package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.EmailVerificationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fix 4: Listens to {@link UserRegisteredEvent} AFTER the registration transaction commits and
 * sends the email verification email.
 *
 * <p>By moving this call out of the main {@link
 * com.uk.certifynow.certify_now.service.auth.RegistrationService} transaction, a transient SMTP
 * failure no longer rolls back user creation. The user + profile + consent records commit
 * independently of email delivery.
 *
 * <p>This listener runs on the {@code authEventsExecutor} thread pool so it does not block the HTTP
 * response thread.
 */
@Component
public class EmailVerificationEventListener {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationEventListener.class);

  private final EmailVerificationService emailVerificationService;
  private final UserRepository userRepository;

  public EmailVerificationEventListener(
      final EmailVerificationService emailVerificationService,
      final UserRepository userRepository) {
    this.emailVerificationService = emailVerificationService;
    this.userRepository = userRepository;
  }

  @Async("authEventsExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRegistered(final UserRegisteredEvent event) {
    final UUID userId = event.getUserId();
    final User user =
        userRepository
            .findById(userId)
            .orElseGet(
                () -> {
                  log.error(
                      "EmailVerificationEventListener: User {} not found after registration commit"
                          + " — cannot send verification email",
                      userId);
                  return null;
                });

    if (user == null) {
      return;
    }

    try {
      emailVerificationService.sendVerificationEmail(user);
    } catch (final Exception ex) {
      // SMTP failure must not propagate — the user is already persisted.
      // The user can request a new verification email via the resend endpoint.
      log.error(
          "Failed to send verification email to userId={} email={}", userId, user.getEmail(), ex);
    }
  }
}

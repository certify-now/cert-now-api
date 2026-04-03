package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Async listener for duplicate registration attempts.
 *
 * <p>Fires AFTER_COMMIT to ensure the existing user record is visible to the email service. SMTP
 * failures here do NOT affect the registration transaction — the user creation has already
 * committed.
 */
@Component
public class RegistrationNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(RegistrationNotificationListener.class);

  private final EmailService emailService;

  public RegistrationNotificationListener(final EmailService emailService) {
    this.emailService = emailService;
  }

  @Async("authEventsExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDuplicateRegistrationAttempt(final DuplicateRegistrationAttemptEvent event) {
    log.warn(
        "SECURITY: Duplicate registration attempt on {} for email={}, ip={}",
        event.getCollisionType(),
        event.getTargetEmail(),
        event.getIpAddress());
    try {
      emailService.sendDuplicateRegistrationNotification(
          event.getTargetEmail(), event.getIpAddress());
    } catch (final Exception ex) {
      log.error(
          "Failed to send duplicate-registration notification to {}", event.getTargetEmail(), ex);
    }
  }
}

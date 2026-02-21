package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.UserLoggedInEvent;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles authentication logic for email/password login.
 *
 * <p>Responsibilities: - Validate credentials - Check account status (active, suspended,
 * deactivated) - Update last login timestamp - Publish UserLoggedInEvent
 *
 * <p>This service is focused solely on authentication and does NOT handle token issuance - that is
 * the responsibility of SessionService.
 */
@Service
public class AuthenticationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public AuthenticationService(
      final UserRepository userRepository,
      final PasswordEncoder passwordEncoder,
      final ApplicationEventPublisher eventPublisher,
      final Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Authenticates a user with email and password.
   *
   * <p>This method validates credentials and account status, updates the last login timestamp, and
   * publishes a login event. It does NOT issue tokens - that is handled by SessionService.
   *
   * @param email user's email address
   * @param password user's plaintext password
   * @param deviceInfo device information for audit trail
   * @return authenticated User entity
   * @throws BusinessException if credentials are invalid or account is not in good standing
   */
  @Transactional
  public User authenticate(final String email, final String password, final String deviceInfo) {
    final User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Invalid email or password"));

    // Validate password
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    // Validate account status
    validateAccountStatus(user);

    // Update last login timestamp using Clock for testability
    user.updateLastLogin(clock);
    userRepository.save(user);

    // Publish domain event for downstream processing (e.g., fraud detection,
    // analytics)
    eventPublisher.publishEvent(new UserLoggedInEvent(user.getId(), user.getEmail(), deviceInfo));

    return user;
  }

  /**
   * Validates that the user account is in a state that allows authentication.
   *
   * @throws BusinessException if account is deactivated or suspended
   */
  private void validateAccountStatus(final User user) {
    if (user.getStatus() == UserStatus.DEACTIVATED) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DEACTIVATED",
          "Your account has been deactivated. Please contact support.");
    }

    if (user.getStatus() == UserStatus.SUSPENDED) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ACCOUNT_SUSPENDED",
          "Your account has been suspended. Please contact support.");
    }

    // PENDING_VERIFICATION users can still log in (they just can't access certain
    // features)
    // This is handled by authorization logic, not authentication
  }
}

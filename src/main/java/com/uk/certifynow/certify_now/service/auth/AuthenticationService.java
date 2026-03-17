package com.uk.certifynow.certify_now.service.auth;

import static com.uk.certifynow.certify_now.util.AuthUtils.maskEmail;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.LoginFailedEvent;
import com.uk.certifynow.certify_now.events.UserLoggedInEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EmailNotVerifiedException;
import com.uk.certifynow.certify_now.repos.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles authentication logic for email/password login. */
@Service
public class AuthenticationService {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

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

  @Transactional
  public User authenticate(
      final String email, final String password, final String deviceInfo, final String ipAddress) {

    log.debug("Authentication attempt for email: {}", maskEmail(email));

    final User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

    if (user == null) {
      log.warn("Authentication failed: user not found | email={}", maskEmail(email));
      eventPublisher.publishEvent(
          new LoginFailedEvent(email, "INVALID_CREDENTIALS", 1, ipAddress, null, null));
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    if (!user.getAuthProvider().requiresPassword()) {
      log.warn(
          "Authentication failed: OAuth user attempted password login | userId={}", user.getId());
      eventPublisher.publishEvent(
          new LoginFailedEvent(email, "INVALID_CREDENTIALS", 1, ipAddress, null, null));
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      log.warn(
          "Authentication failed: invalid password | userId={} | email={}",
          user.getId(),
          maskEmail(email));
      eventPublisher.publishEvent(
          new LoginFailedEvent(email, "INVALID_CREDENTIALS", 1, ipAddress, null, null));
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    try {
      user.getStatus().assertCanAuthenticate();
    } catch (BusinessException ex) {
      log.warn(
          "Authentication failed: account status check | userId={} | status={} | reason={}",
          user.getId(),
          user.getStatus(),
          ex.getErrorCode());
      eventPublisher.publishEvent(
          new LoginFailedEvent(email, ex.getErrorCode(), 1, ipAddress, null, null));
      throw ex;
    }

    if (!Boolean.TRUE.equals(user.getEmailVerified())) {
      log.warn("Authentication failed: email not verified | userId={}", user.getId());
      eventPublisher.publishEvent(
          new LoginFailedEvent(email, "EMAIL_NOT_VERIFIED", 1, ipAddress, null, null));
      throw new EmailNotVerifiedException("Please verify your email address before logging in.");
    }

    final OffsetDateTime previousLoginAt = user.getLastLoginAt();

    user.updateLastLogin(clock);
    userRepository.save(user);

    log.info("Authentication successful | userId={} | role={}", user.getId(), user.getRole());

    eventPublisher.publishEvent(
        new UserLoggedInEvent(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            deviceInfo,
            ipAddress,
            previousLoginAt));

    return user;
  }
}

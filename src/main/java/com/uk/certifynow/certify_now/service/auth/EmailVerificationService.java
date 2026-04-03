package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.EmailVerifiedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.enums.UserStatus;
import com.uk.certifynow.certify_now.service.notification.EmailService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for email verification operations.
 *
 * <p>Handles: - Generating and sending verification codes - Verifying email addresses with codes -
 * Token expiry and single-use enforcement - Cooldown enforcement for resend requests
 *
 * <p>Security features: - Cryptographically secure random 6-digit codes - SHA-256 hashing (codes
 * never stored in plaintext) - 24-hour expiry window - Single-use enforcement - Configurable
 * cooldown between resend requests
 */
@Service
public class EmailVerificationService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository tokenRepository;
  private final EmailService emailService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Value("${app.email-verification.token-expiry-hours:24}")
  private int tokenExpiryHours;

  @Value("${app.email-verification.resend-cooldown-seconds:60}")
  private int resendCooldownSeconds;

  public EmailVerificationService(
      final UserRepository userRepository,
      final EmailVerificationTokenRepository tokenRepository,
      final EmailService emailService,
      final ApplicationEventPublisher eventPublisher,
      final Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.emailService = emailService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Generate and send email verification code.
   *
   * <p>Called automatically after registration or when user requests a new verification email.
   *
   * @param user user to send verification email to
   * @return raw verification code
   */
  @Transactional
  public String sendVerificationEmail(final User user) {
    // Delete any existing unused tokens for this user
    tokenRepository.deleteUnusedTokensByUserId(user.getId());

    // Generate cryptographically secure random code
    final String verificationCode = generateVerificationCode();
    final String tokenHash = hashToken(verificationCode);

    // Create token entity
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(OffsetDateTime.now(clock).plusHours(tokenExpiryHours));
    token.setCreatedAt(OffsetDateTime.now(clock));

    tokenRepository.save(token);

    // Send email
    emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationCode);

    return verificationCode;
  }

  /**
   * Verify email using code.
   *
   * <p>Validates token, marks it as used, and updates user's email_verified status. If user was
   * PENDING_VERIFICATION, activates them.
   *
   * @param rawCode raw code from verification email
   * @return the activated {@link User} so the caller can immediately issue fresh tokens
   */
  @Transactional
  public User verifyEmail(final String rawCode) {
    final String tokenHash = hashToken(rawCode);

    final EmailVerificationToken token =
        tokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_TOKEN",
                        "Invalid or expired verification token"));

    // Check if already used
    if (token.isUsed()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Token has already been used");
    }

    // Check if expired
    if (token.isExpired(clock)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Token has expired");
    }

    // Mark token as used
    token.markAsUsed(clock);
    tokenRepository.save(token);

    // Update user
    final User user = token.getUser();
    user.setEmailVerified(true);

    // If user was PENDING_VERIFICATION, activate them
    if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
      user.setStatus(UserStatus.ACTIVE);
    }

    userRepository.save(user);

    final Long secondsSinceRegistration =
        user.getCreatedAt() != null
            ? ChronoUnit.SECONDS.between(user.getCreatedAt(), OffsetDateTime.now(clock))
            : null;
    eventPublisher.publishEvent(
        new EmailVerifiedEvent(user.getId(), user.getEmail(), secondsSinceRegistration));

    return user;
  }

  /**
   * Resend verification email to user by user ID.
   *
   * <p>Enforces a cooldown period between resend requests to prevent abuse.
   *
   * @param userId user ID
   * @return raw verification code
   */
  @Transactional
  public String resendVerificationEmail(final UUID userId) {
    final User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

    if (user.getEmailVerified()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "ALREADY_VERIFIED", "Email is already verified");
    }

    enforceCooldown(user);

    return sendVerificationEmail(user);
  }

  /**
   * Resend verification email by email address.
   *
   * <p>Designed for unauthenticated access. Returns silently for unknown or already-verified emails
   * to prevent email enumeration attacks.
   *
   * @param email email address to resend verification to
   */
  @Transactional
  public void resendVerificationEmailByEmail(final String email) {
    final Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email);

    // If user not found, return silently to prevent email enumeration
    if (optionalUser.isEmpty()) {
      return;
    }

    final User user = optionalUser.get();

    // If already verified, return silently to prevent leaking state
    if (user.getEmailVerified()) {
      return;
    }

    enforceCooldown(user);

    sendVerificationEmail(user);
  }

  /**
   * Update the email address of an unverified user and resend the verification code.
   *
   * <p>This allows a user who made a typo during registration to correct their email before
   * verification. Throws if the user is already verified or if the new email is taken by another
   * user.
   *
   * @param userId authenticated user's ID
   * @param newEmail corrected email address
   */
  @Transactional
  public void updateEmailForUnverifiedUser(final UUID userId, final String newEmail) {
    final User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

    if (user.getEmailVerified()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "ALREADY_VERIFIED", "Cannot change email after verification");
    }

    // Check the new email isn't already taken by a different user
    userRepository
        .findByEmailIgnoreCase(newEmail)
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(userId)) {
                throw new BusinessException(
                    HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already in use");
              }
            });

    user.setEmail(newEmail.toLowerCase(Locale.ROOT));
    userRepository.save(user);

    // Enforce cooldown to prevent abuse
    enforceCooldown(user);

    // Delete old unused verification tokens
    tokenRepository.deleteUnusedTokensByUserId(userId);

    // Send a new verification email to the corrected address
    sendVerificationEmail(user);
  }

  /**
   * Enforce cooldown period between verification email resend requests.
   *
   * @param user user to check cooldown for
   * @throws BusinessException with TOO_MANY_REQUESTS if within cooldown period
   */
  private void enforceCooldown(final User user) {
    tokenRepository
        .findTopByUserOrderByCreatedAtDesc(user)
        .ifPresent(
            lastToken -> {
              final OffsetDateTime now = OffsetDateTime.now(clock);
              final Duration elapsed = Duration.between(lastToken.getCreatedAt(), now);

              if (elapsed.getSeconds() < resendCooldownSeconds) {
                final long remainingSeconds = resendCooldownSeconds - elapsed.getSeconds();
                throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RESEND_COOLDOWN",
                    "Please wait "
                        + remainingSeconds
                        + " seconds before requesting another verification email");
              }
            });
  }

  /**
   * Generate cryptographically secure 6-digit verification code.
   *
   * @return zero-padded 6-digit code
   */
  private String generateVerificationCode() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  /**
   * Hash token using SHA-256.
   *
   * @param rawToken raw token/code string
   * @return SHA-256 hash
   */
  private String hashToken(final String rawToken) {
    return DigestUtils.sha256Hex(rawToken);
  }
}

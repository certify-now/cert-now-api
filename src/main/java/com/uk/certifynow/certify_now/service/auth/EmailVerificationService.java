package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.EmailService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for email verification operations.
 *
 * <p>Handles: - Generating and sending verification codes - Verifying email addresses with codes -
 * Token expiry and single-use enforcement
 *
 * <p>Security features: - Cryptographically secure random 6-digit codes - SHA-256 hashing (codes
 * never stored in plaintext) - 24-hour expiry window - Single-use enforcement
 */
@Service
public class EmailVerificationService {

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository tokenRepository;
  private final EmailService emailService;
  private final Clock clock;

  @Value("${app.email-verification.token-expiry-hours:24}")
  private int tokenExpiryHours;

  public EmailVerificationService(
      final UserRepository userRepository,
      final EmailVerificationTokenRepository tokenRepository,
      final EmailService emailService,
      final Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.emailService = emailService;
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
   */
  @Transactional
  public void verifyEmail(final String rawCode) {
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
  }

  /**
   * Resend verification email to user.
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

    return sendVerificationEmail(user);
  }

  /**
   * Generate cryptographically secure 6-digit verification code.
   *
   * @return zero-padded 6-digit code
   */
  private String generateVerificationCode() {
    try {
      final SecureRandom secureRandom = SecureRandom.getInstanceStrong();
      return String.format("%06d", secureRandom.nextInt(1_000_000));
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate verification code", e);
    }
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

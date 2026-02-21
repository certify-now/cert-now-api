package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import com.uk.certifynow.certify_now.shared.service.EmailService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for email verification operations.
 *
 * <p>Handles: - Generating and sending verification tokens - Verifying email addresses with tokens
 * - Token expiry and single-use enforcement
 *
 * <p>Security features: - Cryptographically secure random tokens (64-char hex) - SHA-256 hashing
 * (tokens never stored in plaintext) - 24-hour expiry window - Single-use enforcement
 */
@Service
public class EmailVerificationService {

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository tokenRepository;
  private final EmailService emailService;
  private final Clock clock;

  @Value("${app.email-verification.token-expiry-hours:24}")
  private int tokenExpiryHours;

  @Value("${app.frontend.base-url}")
  private String frontendBaseUrl;

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
   * Generate and send email verification token.
   *
   * <p>Called automatically after registration or when user requests a new verification email.
   *
   * @param user user to send verification email to
   * @return verification link (for email template)
   */
  @Transactional
  public String sendVerificationEmail(final User user) {
    // Delete any existing unused tokens for this user
    tokenRepository.deleteUnusedTokensByUserId(user.getId());

    // Generate cryptographically secure random token
    final String rawToken = generateSecureToken();
    final String tokenHash = hashToken(rawToken);

    // Create token entity
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(OffsetDateTime.now(clock).plusHours(tokenExpiryHours));
    token.setCreatedAt(OffsetDateTime.now(clock));

    tokenRepository.save(token);

    // Build verification link
    final String verificationLink = buildVerificationLink(rawToken);

    // Send email
    emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);

    return verificationLink;
  }

  /**
   * Verify email using token from link.
   *
   * <p>Validates token, marks it as used, and updates user's email_verified status. If user was
   * PENDING_VERIFICATION, activates them.
   *
   * @param rawToken raw token from verification link
   */
  @Transactional
  public void verifyEmail(final String rawToken) {
    final String tokenHash = hashToken(rawToken);

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
   * @return verification link
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
   * Generate cryptographically secure random token.
   *
   * @return 64-character hex string
   */
  private String generateSecureToken() {
    try {
      final SecureRandom secureRandom = SecureRandom.getInstanceStrong();
      return secureRandom
          .ints(32, 0, 16)
          .mapToObj(Integer::toHexString)
          .collect(Collectors.joining());
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate secure token", e);
    }
  }

  /**
   * Hash token using SHA-256.
   *
   * @param rawToken raw token string
   * @return SHA-256 hash
   */
  private String hashToken(final String rawToken) {
    return DigestUtils.sha256Hex(rawToken);
  }

  /**
   * Build verification link for email.
   *
   * @param rawToken raw token
   * @return full verification URL
   */
  private String buildVerificationLink(final String rawToken) {
    return String.format("%s/verify-email?token=%s", frontendBaseUrl, rawToken);
  }
}

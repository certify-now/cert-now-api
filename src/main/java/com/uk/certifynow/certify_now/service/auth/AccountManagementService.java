package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.enums.UserStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles sensitive account mutations that require the user to confirm their current password:
 * changing password and changing email address.
 */
@Service
public class AccountManagementService {

  private static final Logger log = LoggerFactory.getLogger(AccountManagementService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService emailVerificationService;
  private final Clock clock;

  public AccountManagementService(
      final UserRepository userRepository,
      final PasswordEncoder passwordEncoder,
      final EmailVerificationService emailVerificationService,
      final Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailVerificationService = emailVerificationService;
    this.clock = clock;
  }

  /**
   * Changes the authenticated user's password after verifying their current password.
   *
   * @param userId authenticated user's ID
   * @param currentPassword the user's current password for verification
   * @param newPassword the desired new password (min 8 chars enforced at controller layer)
   */
  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void changePassword(
      final UUID userId, final String currentPassword, final String newPassword) {
    final User user = loadUser(userId);
    requirePasswordAuth(user);
    verifyCurrentPassword(user, currentPassword);

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    userRepository.save(user);

    log.info("User {} changed their password", userId);
  }

  /**
   * Changes the authenticated user's email address after verifying their current password.
   *
   * <p>The email is updated immediately and the account is placed back into {@code
   * PENDING_VERIFICATION} status. A verification email is dispatched to the new address. The user
   * must verify the new email before they can log in again.
   *
   * @param userId authenticated user's ID
   * @param currentPassword the user's current password for verification
   * @param newEmail the desired new email address
   */
  @Transactional
  @CacheEvict(
      value = {"users", "users_all", "users_email", "users_me"},
      allEntries = true)
  public void changeEmail(final UUID userId, final String currentPassword, final String newEmail) {
    final User user = loadUser(userId);
    requirePasswordAuth(user);
    verifyCurrentPassword(user, currentPassword);

    final String normalized = newEmail.toLowerCase(Locale.ROOT).trim();

    if (normalized.equalsIgnoreCase(user.getEmail())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EMAIL_UNCHANGED",
          "New email must be different from the current email");
    }

    userRepository
        .findByEmailIgnoreCase(normalized)
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(userId)) {
                throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "EMAIL_TAKEN",
                    "Email is already in use by another account");
              }
            });

    user.setEmail(normalized);
    user.setEmailVerified(false);
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setUpdatedAt(OffsetDateTime.now(clock));
    userRepository.save(user);

    emailVerificationService.sendVerificationEmail(user);

    log.info("User {} changed their email address", userId);
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private User loadUser(final UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
  }

  private void requirePasswordAuth(final User user) {
    if (!user.getAuthProvider().requiresPassword()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "OAUTH_ACCOUNT",
          "This operation is not available for accounts linked via a third-party provider");
    }
  }

  private void verifyCurrentPassword(final User user, final String rawPassword) {
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Current password is incorrect");
    }
  }
}

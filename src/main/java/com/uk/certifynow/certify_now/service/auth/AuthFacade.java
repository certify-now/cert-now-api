package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.service.mappers.AuthMapper;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade for authentication operations.
 *
 * <p>This is the orchestration layer that coordinates RegistrationService, AuthenticationService,
 * and SessionService. It provides the public API used by controllers and contains NO business logic
 * itself.
 *
 * <p>Responsibilities: - Orchestrate application services - Delegate to AuthMapper for DTO
 * construction - Define clean public API
 *
 * <p>This facade ensures controllers don't need to know about the internal service structure and
 * provides a stable API even if internal services change.
 */
@Service
public class AuthFacade {

  private final RegistrationService registrationService;
  private final AuthenticationService authenticationService;
  private final SessionService sessionService;
  private final AuthMapper authMapper;
  private final EmailVerificationService emailVerificationService;
  private final AccountManagementService accountManagementService;

  public AuthFacade(
      final RegistrationService registrationService,
      final AuthenticationService authenticationService,
      final SessionService sessionService,
      final AuthMapper authMapper,
      final EmailVerificationService emailVerificationService,
      final AccountManagementService accountManagementService) {
    this.registrationService = registrationService;
    this.authenticationService = authenticationService;
    this.sessionService = sessionService;
    this.authMapper = authMapper;
    this.emailVerificationService = emailVerificationService;
    this.accountManagementService = accountManagementService;
  }

  /**
   * Registers a new user and immediately issues tokens on success.
   *
   * <p>If the email/phone already exists, registration silently succeeds (no 409) — the existing
   * user receives a "someone tried to register" email notification via an async event listener. An
   * empty Optional from RegistrationService means a silent duplicate was handled.
   *
   * @param request registration details
   * @param deviceInfo device information for audit
   * @param ipAddress IP address for audit
   */
  public AuthResponse register(
      final RegisterRequest request, final String deviceInfo, final String ipAddress) {
    try {
      return registrationService
          .registerUser(request, ipAddress)
          .map(
              user -> {
                final SessionService.TokenPair tokens =
                    sessionService.issueTokens(user, deviceInfo, ipAddress);
                return authMapper.toAuthResponse(user, tokens);
              })
          .orElseGet(authMapper::toGenericRegistrationResponse);
    } catch (DataIntegrityViolationException | UnexpectedRollbackException ex) {
      registrationService.publishDuplicateAttemptIfPresent(
          request.email(), request.phone(), ipAddress);
      return authMapper.toGenericRegistrationResponse();
    }
  }

  /**
   * Authenticates a user and issues tokens.
   *
   * <p>Orchestrates: authentication → token issuance → DTO mapping
   *
   * @param request login credentials
   * @param ipAddress IP address for audit
   * @return authentication response with tokens and user summary
   */
  public AuthResponse login(final LoginRequest request, final String ipAddress) {
    final User user =
        authenticationService.authenticate(
            request.email(), request.password(), request.deviceInfo(), ipAddress);

    final SessionService.TokenPair tokens =
        sessionService.issueTokens(user, request.deviceInfo(), ipAddress);

    return authMapper.toAuthResponse(user, tokens);
  }

  /**
   * Verifies a user's email address and immediately issues fresh tokens.
   *
   * <p>After email verification the user's status transitions from PENDING_VERIFICATION to ACTIVE.
   * The registration-time access token still carries the old status claim, so the app would be
   * blocked on every authenticated endpoint until the token expires. Issuing fresh tokens here
   * solves the problem — the new access token encodes {@code status=ACTIVE} and the user can
   * navigate straight to the home screen without having to log in again.
   *
   * @param rawCode 6-digit verification code from the email
   * @param ipAddress client IP address for audit
   * @return {@link AuthResponse} with fresh access + refresh tokens for the now-active user
   */
  @Transactional
  public AuthResponse verifyEmailAndIssueTokens(final String rawCode, final String ipAddress) {
    final User user = emailVerificationService.verifyEmail(rawCode);
    final SessionService.TokenPair tokens = sessionService.issueTokens(user, null, ipAddress);
    return authMapper.toAuthResponse(user, tokens);
  }

  /**
   * Refreshes tokens using a refresh token.
   *
   * <p>Orchestrates: token rotation → DTO mapping
   *
   * @param request refresh token
   * @param ipAddress IP address for audit
   * @return authentication response with new tokens and user summary
   */
  public AuthResponse refresh(final RefreshRequest request, final String ipAddress) {
    final SessionService.TokenPair tokens =
        sessionService.rotateRefreshToken(request.refreshToken(), ipAddress);
    return authMapper.toAuthResponseFromToken(tokens);
  }

  /**
   * Resends a verification email to the given address (unauthenticated, no-enum safe).
   *
   * @param email address to resend verification to
   */
  public void resendVerificationByEmail(final String email) {
    emailVerificationService.resendVerificationEmailByEmail(email);
  }

  /**
   * Allows an authenticated but unverified user to correct their email address.
   *
   * @param userId authenticated user's ID
   * @param newEmail corrected email address
   */
  @Transactional
  public void updateEmailForUnverifiedUser(final UUID userId, final String newEmail) {
    emailVerificationService.updateEmailForUnverifiedUser(userId, newEmail);
  }

  /**
   * Changes the authenticated user's password after verifying their current password.
   *
   * @param userId authenticated user's ID
   * @param currentPassword current password for verification
   * @param newPassword new password to set
   */
  public void changePassword(
      final UUID userId, final String currentPassword, final String newPassword) {
    accountManagementService.changePassword(userId, currentPassword, newPassword);
  }

  /**
   * Changes the authenticated user's email address after verifying their current password.
   *
   * <p>Sets the account to PENDING_VERIFICATION and sends a verification email to the new address.
   *
   * @param userId authenticated user's ID
   * @param currentPassword current password for verification
   * @param newEmail desired new email address
   */
  public void changeEmail(final UUID userId, final String currentPassword, final String newEmail) {
    accountManagementService.changeEmail(userId, currentPassword, newEmail);
  }

  /**
   * Logs out a user by revoking their refresh token and denylisting the access token jti.
   *
   * @param userId user requesting logout
   * @param refreshToken token to revoke
   * @param accessToken current access token (jti will be added to denylist for immediate
   *     invalidation)
   */
  public void logout(final UUID userId, final String refreshToken, final String accessToken) {
    sessionService.revokeToken(userId, refreshToken, accessToken);
  }

  /**
   * Retrieves the current user's profile.
   *
   * @param userId user ID from JWT
   * @return user profile view with polymorphic profile data
   */
  @Transactional(readOnly = true)
  public AuthResponse.UserSummary getMe(final UUID userId) {
    return authMapper.toUserSummary(userId);
  }
}

package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.service.mappers.AuthMapper;
import com.uk.certifynow.certify_now.shared.exception.AccountNotActiveException;
import com.uk.certifynow.certify_now.shared.exception.EmailNotVerifiedException;
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

  public AuthFacade(
      final RegistrationService registrationService,
      final AuthenticationService authenticationService,
      final SessionService sessionService,
      final AuthMapper authMapper) {
    this.registrationService = registrationService;
    this.authenticationService = authenticationService;
    this.sessionService = sessionService;
    this.authMapper = authMapper;
  }

  /**
   * Registers a new user.
   *
   * <p>Orchestrates: registration only. Token issuance is intentionally deferred until login after
   * email verification.
   *
   * <p>Fix 3: If the email/phone already exists, registration silently succeeds (no 409) — the
   * existing user receives a "someone tried to register" email notification via an async event
   * listener. An empty Optional from RegistrationService means a silent duplicate was handled.
   *
   * @param request registration details
   * @param deviceInfo device information for audit (currently unused)
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
      // Handle rare uniqueness races as silent duplicates too.
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
    // Step 1: Authenticate (validates credentials, updates last login, publishes
    // event)
    final User user =
        authenticationService.authenticate(
            request.email(), request.password(), request.deviceInfo());

    if (!Boolean.TRUE.equals(user.getEmailVerified())) {
      throw new EmailNotVerifiedException("Please verify your email address before logging in.");
    }

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new AccountNotActiveException(
          "Your account is not active. Current status: " + user.getStatus());
    }

    // Step 2: Issue tokens
    final SessionService.TokenPair tokens =
        sessionService.issueTokens(user, request.deviceInfo(), ipAddress);

    // Step 3: Map to DTO
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
    // Step 1: Rotate tokens (validates, revokes old, issues new)
    final SessionService.TokenPair tokens =
        sessionService.rotateRefreshToken(request.refreshToken(), ipAddress);

    // Step 2: Extract user from new access token and map to DTO
    return authMapper.toAuthResponseFromToken(tokens);
  }

  /**
   * Logs out a user by revoking their refresh token and denylisting the access token jti.
   *
   * @param userId user requesting logout
   * @param refreshToken token to revoke
   * @param accessToken current access token (jti will be added to denylist — Fix 1)
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

package com.uk.certifynow.certify_now.auth.application;

import com.uk.certifynow.certify_now.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.auth.mapping.AuthMapper;
import com.uk.certifynow.certify_now.domain.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
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
   * Registers a new user and issues tokens.
   *
   * <p>Orchestrates: registration → token issuance → DTO mapping
   *
   * @param request registration details
   * @param deviceInfo device information for audit
   * @param ipAddress IP address for audit
   * @return authentication response with tokens and user summary
   */
  public AuthResponse register(
      final RegisterRequest request, final String deviceInfo, final String ipAddress) {
    // Step 1: Register user (creates user, profile, consents, publishes event)
    final User user = registrationService.registerUser(request, ipAddress);

    // Step 2: Issue tokens
    final SessionService.TokenPair tokens = sessionService.issueTokens(user, deviceInfo, ipAddress);

    // Step 3: Map to DTO
    return authMapper.toAuthResponse(user, tokens);
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
   * Logs out a user by revoking their refresh token.
   *
   * @param userId user requesting logout
   * @param refreshToken token to revoke
   */
  public void logout(final UUID userId, final String refreshToken) {
    sessionService.revokeToken(userId, refreshToken);
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

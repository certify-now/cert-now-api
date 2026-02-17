package com.uk.certifynow.certify_now.auth.service;

import com.uk.certifynow.certify_now.auth.application.AuthFacade;
import com.uk.certifynow.certify_now.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.auth.dto.RegisterRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy AuthService - now delegates to AuthFacade.
 *
 * <p>This class exists for backward compatibility with existing controllers. All business logic has
 * been moved to the application layer services: - RegistrationService - AuthenticationService -
 * SessionService - AuthFacade (orchestration)
 *
 * <p>This service simply delegates to AuthFacade, which coordinates the application services.
 *
 * @deprecated Use {@link AuthFacade} directly for new code
 */
@Deprecated
@Service
public class AuthService {

  private final AuthFacade authFacade;

  public AuthService(final AuthFacade authFacade) {
    this.authFacade = authFacade;
  }

  /**
   * Registers a new user.
   *
   * @param request registration details
   * @param deviceInfo device information for token tracking
   * @param ipAddress IP address for audit trail
   * @return authentication response with tokens and user summary
   */
  @Transactional
  public AuthResponse register(
      final RegisterRequest request, final String deviceInfo, final String ipAddress) {
    return authFacade.register(request, deviceInfo, ipAddress);
  }

  /**
   * Authenticates a user with email and password.
   *
   * @param request login credentials (includes deviceInfo)
   * @param ipAddress IP address for audit trail
   * @return authentication response with tokens and user summary
   */
  @Transactional
  public AuthResponse login(final LoginRequest request, final String ipAddress) {
    return authFacade.login(request, ipAddress);
  }

  /**
   * Refreshes access token using refresh token.
   *
   * @param request refresh token
   * @param ipAddress IP address for audit trail
   * @return authentication response with new tokens and user summary
   */
  @Transactional
  public AuthResponse refresh(final RefreshRequest request, final String ipAddress) {
    return authFacade.refresh(request, ipAddress);
  }

  /**
   * Logs out a user by revoking their refresh token.
   *
   * @param userId user ID from authentication
   * @param refreshToken refresh token to revoke
   */
  @Transactional
  public void logout(final UUID userId, final String refreshToken) {
    authFacade.logout(userId, refreshToken);
  }

  /**
   * Retrieves the current user's profile information.
   *
   * @param userId user ID
   * @return user summary with profile data
   */
  @Transactional(readOnly = true)
  public AuthResponse.UserSummary getMe(final UUID userId) {
    return authFacade.getMe(userId);
  }
}

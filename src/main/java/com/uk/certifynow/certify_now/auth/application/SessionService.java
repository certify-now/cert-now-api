package com.uk.certifynow.certify_now.auth.application;

import com.uk.certifynow.certify_now.auth.domain.UserStatus;
import com.uk.certifynow.certify_now.auth.service.RefreshTokenService;
import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import com.uk.certifynow.certify_now.shared.security.JwtTokenProvider;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles token lifecycle management.
 *
 * <p>Responsibilities: - Issue access and refresh tokens - Refresh token rotation (atomic revoke +
 * issue) - Revoke tokens on logout - Validate token ownership
 *
 * <p>This service is focused solely on session/token management and does NOT handle authentication
 * - that is the responsibility of AuthenticationService.
 *
 * <p>Token rotation is atomic: the old token is revoked and a new one is issued in the same
 * transaction, preventing token reuse attacks.
 */
@Service
public class SessionService {

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;

  public SessionService(
      final JwtTokenProvider jwtTokenProvider, final RefreshTokenService refreshTokenService) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenService = refreshTokenService;
  }

  /**
   * Issues a new access token and refresh token pair for an authenticated user.
   *
   * @param user authenticated user
   * @param deviceInfo device information for audit trail
   * @param ipAddress IP address for audit trail
   * @return token pair containing access token and refresh token
   */
  @Transactional
  public TokenPair issueTokens(final User user, final String deviceInfo, final String ipAddress) {
    final String accessToken = jwtTokenProvider.generateAccessToken(user);
    final RefreshTokenService.IssuedRefreshToken refreshToken =
        refreshTokenService.issueToken(user, deviceInfo, ipAddress);

    return new TokenPair(accessToken, refreshToken.rawToken());
  }

  /**
   * Rotates a refresh token atomically.
   *
   * <p>This method validates the current token, revokes it, and issues a new one in a single
   * transaction. This prevents token reuse attacks.
   *
   * @param rawRefreshToken the current refresh token
   * @param ipAddress IP address for audit trail
   * @return new token pair
   * @throws BusinessException if token is invalid, expired, or revoked
   */
  @Transactional
  public TokenPair rotateRefreshToken(final String rawRefreshToken, final String ipAddress) {
    // Validate current token (checks expiration, revocation)
    final RefreshToken currentToken = refreshTokenService.validate(rawRefreshToken);
    final User user = currentToken.getUser();

    // Ensure account is still in good standing
    validateAccountStatus(user);

    // Atomic rotation: revoke old, issue new
    refreshTokenService.revoke(currentToken);

    final String accessToken = jwtTokenProvider.generateAccessToken(user);
    final RefreshTokenService.IssuedRefreshToken newRefreshToken =
        refreshTokenService.issueToken(user, currentToken.getDeviceInfo(), ipAddress);

    return new TokenPair(accessToken, newRefreshToken.rawToken());
  }

  /**
   * Revokes a refresh token (logout).
   *
   * <p>Validates that the token belongs to the user before revoking.
   *
   * @param userId the user requesting logout
   * @param rawRefreshToken the refresh token to revoke
   * @throws BusinessException if token doesn't belong to user
   */
  @Transactional
  public void revokeToken(final UUID userId, final String rawRefreshToken) {
    final RefreshToken token = refreshTokenService.validate(rawRefreshToken);

    // Validate ownership
    if (!token.getUser().getId().equals(userId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Token does not belong to user");
    }

    refreshTokenService.revoke(token);
  }

  /**
   * Validates that the user account is in a state that allows token refresh.
   *
   * @throws BusinessException if account is deactivated
   */
  private void validateAccountStatus(final User user) {
    if (user.getStatus() == UserStatus.DEACTIVATED) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DEACTIVATED",
          "Your account has been deactivated. Please contact support.");
    }

    // Suspended users can't refresh tokens (they need to re-authenticate)
    if (user.getStatus() == UserStatus.SUSPENDED) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "ACCOUNT_SUSPENDED",
          "Your account has been suspended. Please contact support.");
    }
  }

  /**
   * Token pair containing access token and refresh token.
   *
   * @param accessToken JWT access token
   * @param refreshToken opaque refresh token
   */
  public record TokenPair(String accessToken, String refreshToken) {}
}

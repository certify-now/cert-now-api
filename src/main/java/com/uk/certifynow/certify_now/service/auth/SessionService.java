package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import com.uk.certifynow.certify_now.shared.security.JwtTokenProvider;
import com.uk.certifynow.certify_now.shared.security.TokenDenylistService;
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
  private final TokenDenylistService tokenDenylistService;

  public SessionService(
      final JwtTokenProvider jwtTokenProvider,
      final RefreshTokenService refreshTokenService,
      final TokenDenylistService tokenDenylistService) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenService = refreshTokenService;
    this.tokenDenylistService = tokenDenylistService;
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
        // null familyId → starts a new token family (Fix 5)
        refreshTokenService.issueToken(user, deviceInfo, ipAddress, null);

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
    // Validate current token (checks expiration, revocation, and family theft
    // detection)
    final RefreshToken currentToken = refreshTokenService.validate(rawRefreshToken);
    final User user = currentToken.getUser();

    // Fix 2: Ensure account is still in good standing before issuing new tokens
    validateAccountStatus(user);

    // Atomic rotation: revoke old, issue new — family continuity maintained (Fix 5)
    refreshTokenService.revoke(currentToken);

    final String accessToken = jwtTokenProvider.generateAccessToken(user);
    final RefreshTokenService.IssuedRefreshToken newRefreshToken =
        refreshTokenService.issueToken(
            user, currentToken.getDeviceInfo(), ipAddress, currentToken.getFamilyId());

    return new TokenPair(accessToken, newRefreshToken.rawToken());
  }

  /**
   * Revokes a refresh token (logout) and denylists the corresponding access token's jti.
   *
   * <p>Validates that the token belongs to the user before revoking. The access token jti is added
   * to the denylist so the short-lived JWT is immediately invalidated rather than waiting for
   * natural expiry.
   *
   * @param userId the user requesting logout
   * @param rawRefreshToken the refresh token to revoke
   * @param accessToken the current access token whose jti should be denylisted (may be null if
   *     unavailable — revocation still proceeds but access token won't be immediately invalidated)
   * @throws BusinessException if token doesn't belong to user
   */
  @Transactional
  public void revokeToken(
      final UUID userId, final String rawRefreshToken, final String accessToken) {
    final RefreshToken token = refreshTokenService.validate(rawRefreshToken);

    // Validate ownership
    if (!token.getUser().getId().equals(userId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Token does not belong to user");
    }

    refreshTokenService.revoke(token);

    // Denylist the access token's jti so it is immediately invalid (Fix 1)
    // If accessToken is null (e.g., caller didn't provide it), the refresh token is
    // still
    // revoked; only the 15-min access token window remains open.
    if (accessToken != null) {
      try {
        final String jti = jwtTokenProvider.getJtiFromToken(accessToken);
        tokenDenylistService.denyToken(jti, jwtTokenProvider.getAccessTokenExpirySeconds());
      } catch (final Exception ex) {
        // Log but don't fail logout if jti extraction fails — refresh token is already
        // revoked
        org.slf4j.LoggerFactory.getLogger(SessionService.class)
            .warn("Could not denylist jti on logout for userId={}", userId, ex);
      }
    }
  }

  /**
   * Validates that the user account is in a state that allows token refresh.
   *
   * <p>Fix 2: This blocks suspended users from obtaining new tokens at refresh time, closing the
   * 15-min access-token window via the refresh flow. The access token itself is blocked via the
   * denylist check in JwtAuthenticationFilter.
   *
   * @throws BusinessException if account is deactivated or suspended
   */
  private void validateAccountStatus(final User user) {
    if (user.getStatus() == UserStatus.DEACTIVATED) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_DEACTIVATED",
          "Your account has been deactivated. Please contact support.");
    }

    // Fix 2: Suspended users can't refresh tokens — they need to re-authenticate
    // and will be blocked again at authentication time.
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

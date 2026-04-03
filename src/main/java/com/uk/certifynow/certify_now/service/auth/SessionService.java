package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.UserLoggedOutEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.service.security.JwtTokenProvider;
import com.uk.certifynow.certify_now.service.security.TokenDenylistService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages token lifecycle for authentication sessions.
 *
 * <p>Handles token issuance, refresh token rotation, and revocation. Token rotation is atomic to
 * prevent reuse attacks.
 */
@Service
public class SessionService {

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;
  private final TokenDenylistService tokenDenylistService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public SessionService(
      final JwtTokenProvider jwtTokenProvider,
      final RefreshTokenService refreshTokenService,
      final TokenDenylistService tokenDenylistService,
      final ApplicationEventPublisher eventPublisher,
      final Clock clock) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.refreshTokenService = refreshTokenService;
    this.tokenDenylistService = tokenDenylistService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Issues new access and refresh token pair for authenticated user.
   *
   * @param user authenticated user
   * @param deviceInfo device information for audit
   * @param ipAddress IP address for audit
   * @return token pair
   */
  @Transactional
  public TokenPair issueTokens(final User user, final String deviceInfo, final String ipAddress) {
    log.debug("Issuing tokens | userId={} | deviceInfo={}", user.getId(), deviceInfo);

    final String accessToken = jwtTokenProvider.generateAccessToken(user);
    final RefreshTokenService.IssuedRefreshToken refreshToken =
        refreshTokenService.issueToken(user, deviceInfo, ipAddress, null);

    log.info(
        "Tokens issued | userId={} | familyId={}",
        user.getId(),
        refreshToken.entity().getFamilyId());

    return new TokenPair(accessToken, refreshToken.rawToken());
  }

  /**
   * Rotates refresh token atomically.
   *
   * <p>Validates current token, revokes it, and issues new one in single transaction to prevent
   * token reuse attacks.
   *
   * @param rawRefreshToken current refresh token
   * @param ipAddress IP address for audit
   * @return new token pair
   * @throws BusinessException if token is invalid, expired, or revoked
   */
  @Transactional
  public TokenPair rotateRefreshToken(final String rawRefreshToken, final String ipAddress) {
    log.debug("Rotating refresh token from IP: {}", maskIpAddress(ipAddress));

    final RefreshToken currentToken = refreshTokenService.validate(rawRefreshToken);
    final User user = currentToken.getUser();

    user.getStatus().assertCanAuthenticate();

    // Atomic operation: revoke old + issue new
    refreshTokenService.revoke(currentToken);

    final String accessToken = jwtTokenProvider.generateAccessToken(user);
    final RefreshTokenService.IssuedRefreshToken newRefreshToken =
        refreshTokenService.issueToken(
            user, currentToken.getDeviceInfo(), ipAddress, currentToken.getFamilyId());

    log.info(
        "Token rotated | userId={} | familyId={} | deviceInfo={}",
        user.getId(),
        currentToken.getFamilyId(),
        currentToken.getDeviceInfo());

    return new TokenPair(accessToken, newRefreshToken.rawToken());
  }

  /**
   * Revokes refresh token and denylists access token (logout).
   *
   * <p>Validates token ownership before revoking. Access token jti is added to denylist for
   * immediate invalidation.
   *
   * @param userId user requesting logout
   * @param rawRefreshToken refresh token to revoke
   * @param accessToken current access token to denylist (nullable)
   * @throws BusinessException if token doesn't belong to user
   */
  @Transactional
  public void revokeToken(
      final UUID userId, final String rawRefreshToken, final String accessToken) {

    log.debug("Logout requested | userId={}", userId);

    final RefreshToken token = refreshTokenService.validate(rawRefreshToken);

    // Validate ownership
    if (!token.getUser().getId().equals(userId)) {
      log.warn(
          "Token ownership mismatch | requestedBy={} | tokenOwner={}",
          userId,
          token.getUser().getId());
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Token does not belong to user");
    }

    final Long sessionDurationSeconds =
        token.getCreatedAt() != null
            ? ChronoUnit.SECONDS.between(token.getCreatedAt(), OffsetDateTime.now(clock))
            : null;

    refreshTokenService.revoke(token);

    // Denylist access token for immediate invalidation
    if (accessToken != null) {
      try {
        final String jti = jwtTokenProvider.getJtiFromToken(accessToken);
        tokenDenylistService.denyToken(jti, jwtTokenProvider.getAccessTokenExpirySeconds());
        log.debug("Access token denylisted | userId={} | jti={}", userId, jti);
      } catch (final Exception ex) {
        // Don't fail logout if denylist fails - refresh token is already revoked
        log.warn("Failed to denylist access token | userId={}", userId, ex);
      }
    } else {
      log.debug("No access token provided for denylisting | userId={}", userId);
    }

    log.info(
        "User logged out | userId={} | sessionDuration={} | deviceInfo={}",
        userId,
        formatDuration(sessionDurationSeconds),
        token.getDeviceInfo());

    eventPublisher.publishEvent(
        new UserLoggedOutEvent(userId, sessionDurationSeconds, token.getDeviceInfo()));
  }

  /**
   * Denylists a single access token by JTI without touching refresh tokens.
   *
   * <p>Used by operations that have already revoked all refresh tokens (e.g. account deletion) and
   * only need to immediately invalidate the caller's current access token.
   *
   * @param rawAccessToken the raw Bearer access token
   */
  public void denyAccessToken(final String rawAccessToken) {
    if (rawAccessToken == null) {
      return;
    }
    try {
      final String jti = jwtTokenProvider.getJtiFromToken(rawAccessToken);
      tokenDenylistService.denyToken(jti, jwtTokenProvider.getAccessTokenExpirySeconds());
      log.debug("Access token denylisted | jti={}", jti);
    } catch (final Exception ex) {
      log.warn("Failed to denylist access token during account deletion", ex);
    }
  }

  /** Masks IP address for privacy-compliant logging. Example: 192.168.1.100 → 192.168.***.*** */
  private String maskIpAddress(final String ipAddress) {
    if (ipAddress == null) {
      return "UNKNOWN";
    }

    // IPv4
    if (ipAddress.contains(".")) {
      final String[] parts = ipAddress.split("\\.");
      if (parts.length == 4) {
        return parts[0] + "." + parts[1] + ".***.***";
      }
    }

    // IPv6 - mask after first 2 segments
    if (ipAddress.contains(":")) {
      final String[] parts = ipAddress.split(":");
      if (parts.length >= 2) {
        return parts[0] + ":" + parts[1] + ":***";
      }
    }

    return "***";
  }

  /** Formats duration in human-readable format. */
  private String formatDuration(final Long seconds) {
    if (seconds == null) {
      return "UNKNOWN";
    }

    if (seconds < 60) {
      return seconds + "s";
    } else if (seconds < 3600) {
      return (seconds / 60) + "m";
    } else if (seconds < 86400) {
      return (seconds / 3600) + "h";
    } else {
      return (seconds / 86400) + "d";
    }
  }

  /**
   * Token pair containing access and refresh tokens.
   *
   * @param accessToken JWT access token (15 min expiry)
   * @param refreshToken opaque refresh token (30 day expiry)
   */
  public record TokenPair(String accessToken, String refreshToken) {}
}

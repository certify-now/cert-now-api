package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service("authRefreshTokenService")
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final int maxRefreshTokensPerUser;
  private final int refreshTokenExpiryDays;
  private final Clock clock;

  public RefreshTokenService(
      final RefreshTokenRepository refreshTokenRepository,
      @Value("${app.security.max-refresh-tokens-per-user:5}") final int maxRefreshTokensPerUser,
      @Value("${app.jwt.refresh-token-expiry-days:30}") final int refreshTokenExpiryDays,
      final Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.maxRefreshTokensPerUser = maxRefreshTokensPerUser;
    this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    this.clock = clock;
  }

  public IssuedRefreshToken issueToken(
      final User user, final String deviceInfo, final String ipAddress) {
    enforceActiveTokenLimit(user.getId());

    final String raw = generateRawToken();
    final RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setTokenHash(hashToken(raw));
    refreshToken.setDeviceInfo(deviceInfo);
    refreshToken.setIpAddress(ipAddress);
    refreshToken.setRevoked(false);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    refreshToken.setCreatedAt(now);
    refreshToken.setExpiresAt(now.plusDays(refreshTokenExpiryDays));
    refreshTokenRepository.save(refreshToken);

    return new IssuedRefreshToken(raw, refreshToken);
  }

  /**
   * Validates a refresh token.
   *
   * <p>Checks that the token exists, is not revoked, and has not expired.
   *
   * @param rawToken the raw refresh token
   * @return the validated RefreshToken entity
   * @throws BusinessException if token is invalid, revoked, or expired
   */
  public RefreshToken validate(final String rawToken) {
    final RefreshToken token =
        findByRawToken(rawToken)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid"));

    if (Boolean.TRUE.equals(token.getRevoked())) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token has been revoked");
    }

    if (token.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token has expired");
    }

    return token;
  }

  public Optional<RefreshToken> findByRawToken(final String rawToken) {
    return refreshTokenRepository.findByTokenHash(hashToken(rawToken));
  }

  public void revoke(final RefreshToken token) {
    token.setRevoked(true);
    token.setRevokedAt(OffsetDateTime.now(clock));
    refreshTokenRepository.save(token);
  }

  public String hashToken(final String rawToken) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (final NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Unable to hash refresh token", ex);
    }
  }

  private String generateRawToken() {
    final byte[] random = new byte[32];
    SECURE_RANDOM.nextBytes(random);
    return HexFormat.of().formatHex(random);
  }

  private void enforceActiveTokenLimit(final UUID userId) {
    final List<RefreshToken> active =
        refreshTokenRepository.findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
            userId, OffsetDateTime.now(clock));
    if (active.size() < maxRefreshTokensPerUser) {
      return;
    }
    final int tokensToRevoke = (active.size() - maxRefreshTokensPerUser) + 1;
    for (int i = 0; i < tokensToRevoke; i++) {
      revoke(active.get(i));
    }
  }

  public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}
}

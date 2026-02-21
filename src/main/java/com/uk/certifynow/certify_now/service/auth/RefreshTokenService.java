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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service("authRefreshTokenService")
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
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

  /**
   * Issues a new refresh token.
   *
   * <p>Fix 5: Accepts an optional {@code familyId}. If {@code null}, a new UUID is generated to
   * start a fresh token family (first login). On rotation, the caller passes the existing {@code
   * familyId} to keep all tokens in the same session family.
   *
   * @param user the authenticated user
   * @param deviceInfo device information for audit trail
   * @param ipAddress IP address for audit trail
   * @param familyId existing family ID to continue (null to start a new family)
   * @return issued token pair containing raw token and entity
   */
  public IssuedRefreshToken issueToken(
      final User user, final String deviceInfo, final String ipAddress, final UUID familyId) {
    enforceActiveTokenLimit(user.getId());

    final String raw = generateRawToken();
    final RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setTokenHash(hashToken(raw));
    refreshToken.setDeviceInfo(deviceInfo);
    refreshToken.setIpAddress(ipAddress);
    refreshToken.setRevoked(false);
    // Fix 5: Generate a new family if this is a fresh session, otherwise carry the
    // existing one
    refreshToken.setFamilyId(familyId != null ? familyId : UUID.randomUUID());
    final OffsetDateTime now = OffsetDateTime.now(clock);
    refreshToken.setCreatedAt(now);
    refreshToken.setExpiresAt(now.plusDays(refreshTokenExpiryDays));
    refreshTokenRepository.save(refreshToken);

    return new IssuedRefreshToken(raw, refreshToken);
  }

  /**
   * Validates a refresh token.
   *
   * <p>Fix 5 — Token reuse detection: If the incoming token is found but already revoked, this
   * indicates a possible token theft scenario (the attacker has a previously rotated token). All
   * tokens in the same family are immediately revoked and a security audit event is logged.
   *
   * <p>Standard checks: token exists, not revoked, not expired.
   *
   * @param rawToken the raw refresh token
   * @return the validated RefreshToken entity
   * @throws BusinessException if token is invalid, revoked, or expired; or TOKEN_REUSE_DETECTED if
   *     a revoked token in an active family is presented
   */
  public RefreshToken validate(final String rawToken) {
    final Optional<RefreshToken> tokenOpt = findByRawToken(rawToken);

    if (tokenOpt.isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }

    final RefreshToken token = tokenOpt.get();

    // Fix 5: Revoked token presented → possible theft — revoke the entire family
    if (Boolean.TRUE.equals(token.getRevoked())) {
      revokeFamily(token.getFamilyId());
      log.warn(
          "SECURITY: TOKEN_REUSE_DETECTED — revoked refresh token re-presented. "
              + "Revoking family={} userId={} ip={}",
          token.getFamilyId(),
          token.getUser().getId(),
          token.getIpAddress());
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "TOKEN_REUSE_DETECTED",
          "A previously revoked token was presented. For your security, all sessions have been"
              + " terminated. Please log in again.");
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

  /**
   * Fix 5: Revokes all tokens in the given family.
   *
   * <p>Called when a revoked token is re-presented (token reuse detection). This ensures an
   * attacker who stole a refresh token cannot continue using any token in the stolen session.
   */
  private void revokeFamily(final UUID familyId) {
    final List<RefreshToken> familyTokens = refreshTokenRepository.findAllByFamilyId(familyId);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    for (final RefreshToken t : familyTokens) {
      if (!Boolean.TRUE.equals(t.getRevoked())) {
        t.setRevoked(true);
        t.setRevokedAt(now);
        refreshTokenRepository.save(t);
      }
    }
  }

  public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}
}

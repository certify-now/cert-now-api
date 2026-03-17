package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service("authRefreshTokenService")
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final int maxRefreshTokensPerUser;
  private final int refreshTokenExpiryDays;
  private final Clock clock;
  private final TransactionTemplate requiresNewTx;

  public RefreshTokenService(
      final RefreshTokenRepository refreshTokenRepository,
      @Value("${app.security.max-refresh-tokens-per-user:5}") final int maxRefreshTokensPerUser,
      @Value("${app.jwt.refresh-token-expiry-days:30}") final int refreshTokenExpiryDays,
      final Clock clock,
      final PlatformTransactionManager transactionManager) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.maxRefreshTokensPerUser = maxRefreshTokensPerUser;
    this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    this.clock = clock;
    this.requiresNewTx = new TransactionTemplate(transactionManager);
    this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Issues a new refresh token.
   *
   * <p>Accepts an optional {@code familyId}. If {@code null}, a new UUID is generated to start a
   * fresh token family (first login). On rotation, the caller passes the existing {@code familyId}
   * to keep all tokens in the same session family, enabling theft detection across rotations.
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
   * <p>Token reuse detection: if the incoming token is found but already revoked, this indicates a
   * possible token theft scenario (an attacker re-presenting a previously rotated token). All
   * tokens in the same family are immediately revoked to prevent further misuse.
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

    if (Boolean.TRUE.equals(token.getRevoked())) {
      revokeFamilyInNewTransaction(token.getFamilyId());
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
    return DigestUtils.sha256Hex(rawToken);
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
   * Revokes all tokens in the given family.
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

  /**
   * Commits family revocation even when caller transaction fails.
   *
   * <p>When a revoked token is replayed we throw TOKEN_REUSE_DETECTED to the caller, which rolls
   * back the surrounding transaction. Family revocation must still persist, so it runs in
   * REQUIRES_NEW.
   */
  private void revokeFamilyInNewTransaction(final UUID familyId) {
    requiresNewTx.executeWithoutResult(status -> revokeFamily(familyId));
  }

  public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}
}

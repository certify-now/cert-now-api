package com.uk.certifynow.certify_now.service.security;

import com.uk.certifynow.certify_now.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private final SecretKey key;
  private final long accessTokenExpiryMs;
  private final Clock clock;

  public JwtTokenProvider(
      @Value(
              "${app.jwt.secret:dev-secret-key-at-least-512-bits-long-for-hs512-algorithm-change-in-production}")
          final String secret,
      @Value("${app.jwt.access-token-expiry-ms:900000}") final long accessTokenExpiryMs,
      final Clock clock) {
    final byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 64) {
      throw new IllegalArgumentException("JWT secret must be at least 512 bits (64 bytes)");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
    this.accessTokenExpiryMs = accessTokenExpiryMs;
    this.clock = clock;
  }

  /**
   * Generates a signed JWT access token for the given user.
   *
   * <p>Claims included:
   *
   * <ul>
   *   <li>{@code sub} — user UUID (principal identifier)
   *   <li>{@code jti} — unique token ID (UUID); used by the denylist for logout/suspension
   *   <li>{@code email}, {@code role}, {@code status} — user details
   *   <li>{@code iat}, {@code exp} — issued-at and expiry timestamps
   * </ul>
   */
  public String generateAccessToken(final User user) {
    final Instant now = Instant.now(clock);
    final Instant expiry = now.plusMillis(accessTokenExpiryMs);
    return Jwts.builder()
        .id(UUID.randomUUID().toString()) // jti claim — unique per issued token
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("status", user.getStatus().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(SignatureAlgorithm.HS512, key)
        .compact();
  }

  public Claims parseClaims(final String token) {
    return Jwts.parser().setSigningKey(key).build().parseClaimsJws(token).getBody();
  }

  /**
   * Extracts the user ID from a JWT token.
   *
   * @param token JWT access token
   * @return user ID
   */
  public UUID getUserIdFromToken(final String token) {
    final Claims claims = parseClaims(token);
    return UUID.fromString(claims.getSubject());
  }

  /**
   * Extracts the jti (JWT ID) from a token.
   *
   * @param token JWT access token
   * @return jti claim value
   */
  public String getJtiFromToken(final String token) {
    return parseClaims(token).getId();
  }

  public long getAccessTokenExpirySeconds() {
    return accessTokenExpiryMs / 1000;
  }
}

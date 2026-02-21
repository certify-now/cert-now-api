package com.uk.certifynow.certify_now.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * Utility methods for parsing and asserting JWT access tokens in integration tests.
 *
 * <p>Uses the same HS512 secret defined in application-integration.yml.
 */
public final class JwtTestUtils {

  /** Must match {@code app.jwt.secret} in application-integration.yml exactly. */
  public static final String TEST_SECRET =
      "test-integration-secret-key-that-is-at-least-512-bits-long-for-hs512-algorithm-test";

  private JwtTestUtils() {}

  private static SecretKey secretKey() {
    return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
  }

  /** Parses and verifies the token signature. Throws if invalid, tampered, or expired. */
  public static Claims parse(final String accessToken) {
    return Jwts.parser()
        .verifyWith(secretKey())
        .build()
        .parseSignedClaims(accessToken)
        .getPayload();
  }

  /** Asserts the token is parseable, not expired, and returns its claims. */
  public static Claims assertValid(final String accessToken) {
    assertThat(accessToken).as("access token must be non-null").isNotNull();
    final Claims claims = parse(accessToken);
    assertThat(claims.getExpiration()).as("token must not be expired").isAfter(new Date());
    assertThat(claims.getId()).as("jti must be present").isNotBlank();
    assertThat(claims.getSubject()).as("sub (userId) must be present").isNotBlank();
    return claims;
  }

  /** Builds a JWT with an expiry in the past (useful for J-03 expired token tests). */
  public static String buildExpiredToken(final String userId, final String role) {
    final long past = System.currentTimeMillis() - 60_000;
    return Jwts.builder()
        .id(java.util.UUID.randomUUID().toString())
        .subject(userId)
        .claim("role", role)
        .claim("status", "ACTIVE")
        .claim("email", "test@example.com")
        .issuedAt(new Date(past - 900_000))
        .expiration(new Date(past))
        .signWith(secretKey())
        .compact();
  }

  /** Builds a JWT with a tampered payload (altered role claim) but keeps valid structure. */
  public static String buildTamperedToken(final String validToken) {
    // Split header.payload.signature and replace payload with base64 of modified
    // JSON.
    // Since we can't re-sign with the real key, just corrupt the signature portion.
    final String[] parts = validToken.split("\\.");
    // Corrupt the last byte of the signature
    final String corruptedSig = parts[2].substring(0, parts[2].length() - 2) + "AA";
    return parts[0] + "." + parts[1] + "." + corruptedSig;
  }

  /** Extracts the jti claim without verifying signature (for logging/assertions after tamper). */
  public static String extractJtiUnsafe(final String accessToken) {
    return parse(accessToken).getId();
  }
}

package com.uk.certifynow.certify_now.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 5 — Token Refresh")
@Sql("/reset.sql")
class Suite5TokenRefreshTest extends IntegrationTestBase {

  @Test
  @DisplayName("T-01: Valid refresh rotates tokens")
  void t01_validRefresh() {
    final String email = TestDataFactory.uniqueEmail();
    final String oldRefresh =
        registerAndGetRefreshToken(TestDataFactory.RegisterPayload.customer(email));

    final String oldFamilyId =
        jdbcTemplate.queryForObject(
            "SELECT family_id FROM refresh_token WHERE token_hash = ?",
            String.class,
            hash(oldRefresh));

    final var response = refresh(oldRefresh);

    response.statusCode(200);
    final String newRefresh = response.extract().jsonPath().getString("data.refresh_token");

    assertThat(newRefresh).isNotEqualTo(oldRefresh);

    // Old token is revoked
    final Boolean oldRevoked =
        jdbcTemplate.queryForObject(
            "SELECT revoked FROM refresh_token WHERE token_hash = ?",
            Boolean.class,
            hash(oldRefresh));
    assertThat(oldRevoked).isTrue();

    // New token is active and has SAME family_id
    final String newFamilyId =
        jdbcTemplate.queryForObject(
            "SELECT family_id FROM refresh_token WHERE token_hash = ?",
            String.class,
            hash(newRefresh));
    assertThat(newFamilyId).isEqualTo(oldFamilyId);
  }

  @Test
  @DisplayName("T-02: Expired refresh token rejected")
  void t02_expiredToken() {
    final String email = TestDataFactory.uniqueEmail();
    final String refreshToken =
        registerAndGetRefreshToken(TestDataFactory.RegisterPayload.customer(email));

    // Manually expire the token in the DB
    jdbcTemplate.update(
        "UPDATE refresh_token SET expires_at = NOW() - INTERVAL '1 day' WHERE token_hash = ?",
        hash(refreshToken));

    refresh(refreshToken).statusCode(401).body("error", equalTo("INVALID_REFRESH_TOKEN"));
  }

  @Test
  @DisplayName("T-03: Already-revoked refresh token triggers family revocation (Fix 5)")
  void t03_reuseDetection() {
    final String email = TestDataFactory.uniqueEmail();
    // A -> B
    final String tokenA =
        registerAndGetRefreshToken(TestDataFactory.RegisterPayload.customer(email));
    refresh(tokenA).statusCode(200);

    // A is now revoked. Let's try to use A again (attacker)
    refresh(tokenA).statusCode(403).body("error", equalTo("TOKEN_REUSE_DETECTED"));

    // Ensure ALL tokens for this user's family are now revoked
    final Integer activeTokens =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token rt JOIN \"user\" u ON rt.user_id = u.id WHERE LOWER(u.email) = LOWER(?) AND rt.revoked = false",
            Integer.class,
            email);

    // Family was revoked!
    assertThat(activeTokens).isEqualTo(0);
  }

  @Test
  @DisplayName("T-05: SUSPENDED user's refresh token rejected")
  void t05_suspendedUserRefreshRejected() {
    final String email = TestDataFactory.uniqueEmail();
    final String refreshToken =
        registerAndGetRefreshToken(TestDataFactory.RegisterPayload.customer(email));

    // Manually suspend
    jdbcTemplate.update(
        "UPDATE \"user\" SET status = 'SUSPENDED' WHERE LOWER(email) = LOWER(?)", email);

    refresh(refreshToken).statusCode(403).body("error", equalTo("ACCOUNT_SUSPENDED"));
  }

  @Test
  @DisplayName("T-06: Max 5 refresh tokens — oldest auto-revoked")
  void t06_maxTokensLimit() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload = TestDataFactory.RegisterPayload.customer(email);
    register(payload).statusCode(201); // login 1

    for (int i = 0; i < 5; i++) {
      login(email, TestDataFactory.VALID_PASSWORD).statusCode(200); // logins 2-6
    }

    // 6 logins total = 6 tokens generated. But max active is 5.
    final Integer activeTokens =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token rt JOIN \"user\" u ON rt.user_id = u.id WHERE LOWER(u.email) = LOWER(?) AND rt.revoked = false",
            Integer.class,
            email);
    assertThat(activeTokens).isEqualTo(5);

    final Integer totalTokens =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token rt JOIN \"user\" u ON rt.user_id = u.id WHERE LOWER(u.email) = LOWER(?)",
            Integer.class,
            email);
    assertThat(totalTokens).isEqualTo(6);
  }

  // --- Helper to hash tokens exactly like RefreshTokenService for DB assertions
  // ---
  private String hash(final String rawToken) {
    return org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawToken);
  }
}

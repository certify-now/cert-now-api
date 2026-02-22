package com.uk.certifynow.certify_now.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 6 — Logout (Denylist)")
@Sql("/reset.sql")
class Suite6LogoutTest extends IntegrationTestBase {

  @Test
  @DisplayName("L-01: Logout revokes refresh token + denylists access token")
  void l01_logoutSuccess() {
    final String email = TestDataFactory.uniqueEmail();
    final var response = register(TestDataFactory.RegisterPayload.customer(email));

    final String access = response.extract().jsonPath().getString("data.access_token");
    final String refresh = response.extract().jsonPath().getString("data.refresh_token");
    final String jti = JwtTestUtils.parse(access).getId();

    logout(access, refresh).statusCode(204);

    // Refresh token is revoked
    final Boolean isRevoked =
        jdbcTemplate.queryForObject(
            "SELECT revoked FROM refresh_token WHERE token_hash = ?",
            Boolean.class,
            org.apache.commons.codec.digest.DigestUtils.sha256Hex(refresh));
    assertThat(isRevoked).isTrue();

    // Access token jti is denylisted in Redis
    final Boolean hasKey = redisTemplate.hasKey("jti:" + jti);
    assertThat(hasKey).isTrue();
    final Long expire = redisTemplate.getExpire("jti:" + jti, TimeUnit.SECONDS);
    assertThat(expire).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(900L);

    // Subsequent use of access token is blocked (401)
    authenticated(access).post("/api/v1/test-protected/privileged").then().statusCode(401);

    // Subsequent use of refresh token is blocked
    refresh(refresh)
        .statusCode(403)
        .body("error", org.hamcrest.Matchers.equalTo("TOKEN_REUSE_DETECTED"));
  }

  @Test
  @DisplayName("L-02: Logout without Bearer token rejected")
  void l02_logoutWithoutBearer() {
    given()
        .contentType(ContentType.JSON)
        .body(java.util.Map.of("refresh_token", "some-token"))
        .when()
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("L-03: Logout with someone else's refresh token rejected")
  void l03_crossUserLogoutRejected() {
    // User A
    final String emailA = TestDataFactory.uniqueEmail();
    final var respA = register(TestDataFactory.RegisterPayload.customer(emailA));
    final String accessA = respA.extract().jsonPath().getString("data.access_token");
    final String refreshA = respA.extract().jsonPath().getString("data.refresh_token");

    // User B
    final String emailB = TestDataFactory.uniqueEmail();
    final var respB = register(TestDataFactory.RegisterPayload.customer(emailB));
    final String refreshB = respB.extract().jsonPath().getString("data.refresh_token");

    // User A attempts to logout User B's refresh token
    logout(accessA, refreshB).statusCode(403);

    // User B's token is NOT revoked
    final Boolean isBRevoked =
        jdbcTemplate.queryForObject(
            "SELECT revoked FROM refresh_token WHERE token_hash = ?",
            Boolean.class,
            org.apache.commons.codec.digest.DigestUtils.sha256Hex(refreshB));
    assertThat(isBRevoked).isFalse();
  }
}

package com.uk.certifynow.certify_now.integration;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Base class for all integration tests.
 *
 * <p>Provisions a shared PostgreSQL 15 and Redis 7 container (started once per JVM, reused across
 * all test classes). Before each test, all tables are truncated via {@link JdbcTestUtils} and all
 * Redis keys are flushed, ensuring a clean state without the overhead of container restarts.
 *
 * <p>Uses {@code @ActiveProfiles("integration")} which loads {@code application-integration.yml}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationTestBase {

  // ─── Spring beans
  // ─────────────────────────────────────────────────────────────

  @LocalServerPort private int port;

  @Autowired protected JdbcTemplate jdbcTemplate;

  @Autowired protected StringRedisTemplate redisTemplate;

  @Autowired protected CaptureEmailService captureEmailService;

  // ─── Setup
  // ────────────────────────────────────────────────────────────────────

  @BeforeEach
  void resetState() {
    // Configure RestAssured
    RestAssured.port = port;
    RestAssured.basePath = "";

    // Truncate all tables — ordering respects FK constraints (child tables first)
    jdbcTemplate.execute("TRUNCATE TABLE email_verification_tokens CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE refresh_token CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE user_consent CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE customer_profile CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE engineer_profile CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE \"user\" CASCADE");

    // Flush all Redis keys (denylist, etc.)
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  // ─── RestAssured helpers
  // ──────────────────────────────────────────────────────

  protected RequestSpecification unauthenticated() {
    return given().contentType(ContentType.JSON).log().ifValidationFails();
  }

  protected RequestSpecification authenticated(final String accessToken) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + accessToken)
        .log()
        .ifValidationFails();
  }

  // ─── Convenience API call helpers
  // ─────────────────────────────────────────────

  /** Registers a user and returns the parsed response body (data object). */
  protected ValidatableResponse register(final TestDataFactory.RegisterPayload payload) {
    return unauthenticated()
        .body(payload)
        .when()
        .post("/api/v1/auth/register")
        .then()
        .log()
        .ifValidationFails();
  }

  protected String registerAndGetAccessToken(final TestDataFactory.RegisterPayload payload) {
    return register(payload).statusCode(201).extract().jsonPath().getString("data.accessToken");
  }

  protected String registerAndGetRefreshToken(final TestDataFactory.RegisterPayload payload) {
    return register(payload).statusCode(201).extract().jsonPath().getString("data.refreshToken");
  }

  protected ValidatableResponse login(final String email, final String password) {
    return unauthenticated()
        .body(new TestDataFactory.LoginPayload(email, password, "test-device"))
        .when()
        .post("/api/v1/auth/login")
        .then()
        .log()
        .ifValidationFails();
  }

  protected ValidatableResponse refresh(final String refreshToken) {
    return unauthenticated()
        .body(new TestDataFactory.RefreshPayload(refreshToken))
        .when()
        .post("/api/v1/auth/refresh")
        .then()
        .log()
        .ifValidationFails();
  }

  protected ValidatableResponse logout(final String accessToken, final String refreshToken) {
    return authenticated(accessToken)
        .body(new TestDataFactory.LogoutPayload(refreshToken))
        .when()
        .post("/api/v1/auth/logout")
        .then()
        .log()
        .ifValidationFails();
  }

  protected ValidatableResponse verifyEmail(final String rawToken) {
    return unauthenticated()
        .body(new TestDataFactory.VerifyEmailPayload(rawToken))
        .when()
        .post("/api/v1/auth/verify-email")
        .then()
        .log()
        .ifValidationFails();
  }

  // ─── DB helpers
  // ───────────────────────────────────────────────────────────────

  protected boolean userExistsByEmail(String email) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"user\" WHERE LOWER(email) = LOWER(?)", Integer.class, email);
    return count != null && count > 0;
  }

  protected String getUserStatus(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM \"user\" WHERE LOWER(email) = LOWER(?)", String.class, email);
  }

  protected boolean getUserEmailVerified(final String email) {
    final Boolean verified =
        jdbcTemplate.queryForObject(
            "SELECT email_verified FROM \"user\" WHERE LOWER(email) = LOWER(?)",
            Boolean.class,
            email);
    return Boolean.TRUE.equals(verified);
  }

  protected void verifyUserEmail(String email) {
    jdbcTemplate.update(
        "UPDATE \"user\" SET email_verified = true, updated_at = NOW() WHERE email = ?", email);
  }

  protected String getRawVerificationToken(final String email) {
    // The service stores the SHA-256 hash; we capture the raw token by grabbing
    // it from email_verification_tokens by joining through users.
    // Since we mock/stub the email service in unit tests but here we need the raw
    // token, we store a hint column OR we use the fact that during integration
    // tests
    // we can intercept the email service call. See CaptureEmailService.
    //
    // For tests that need the raw token, use
    // captureEmailService.getLastVerificationToken()
    // which is injected via @MockitoSpyBean / @SpyBean on StubEmailService.
    throw new UnsupportedOperationException(
        "Use CaptureEmailService.getLastVerificationToken() instead");
  }
}

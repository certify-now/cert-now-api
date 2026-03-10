package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.OffsetDateTime;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the POST /api/v1/auth/resend-verification endpoint. Uses Testcontainers for
 * a real PostgreSQL database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthResendVerificationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify_now_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Use a short cooldown for testing
    registry.add("app.email-verification.resend-cooldown-seconds", () -> "60");
  }

  @LocalServerPort private int port;

  @Autowired private UserRepository userRepository;
  @Autowired private EmailVerificationTokenRepository tokenRepository;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    tokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("Successful resend returns 200 with generic message")
  void successfulResendReturnsGenericMessage() {
    final User user = createUnverifiedUser("resend@test.com");

    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"resend@test.com\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(200)
        .body(
            "data.message",
            equalTo("If that email is registered and unverified, a new code has been sent."))
        .body("meta.request_id", notNullValue());
  }

  @Test
  @DisplayName("Unknown email returns 200 with generic message (anti-enumeration)")
  void unknownEmailReturnsGenericMessage() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"nonexistent@test.com\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(200)
        .body(
            "data.message",
            equalTo("If that email is registered and unverified, a new code has been sent."));
  }

  @Test
  @DisplayName("Already verified email returns 200 with generic message (anti-enumeration)")
  void alreadyVerifiedReturnsGenericMessage() {
    createVerifiedUser("verified@test.com");

    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"verified@test.com\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(200)
        .body(
            "data.message",
            equalTo("If that email is registered and unverified, a new code has been sent."));
  }

  @Test
  @DisplayName("Cooldown violation returns 429")
  void cooldownViolationReturns429() {
    final User user = createUnverifiedUser("cooldown@test.com");
    createRecentToken(user);

    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"cooldown@test.com\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(429);
  }

  @Test
  @DisplayName("Missing email returns 422 validation error")
  void missingEmailReturns422() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(422);
  }

  @Test
  @DisplayName("Invalid email format returns 422 validation error")
  void invalidEmailFormatReturns422() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"email\": \"not-an-email\"}")
        .when()
        .post("/api/v1/auth/resend-verification")
        .then()
        .statusCode(422);
  }

  // ========================================================================
  // HELPERS
  // ========================================================================

  private User createUnverifiedUser(final String email) {
    final User user = new User();
    user.setEmail(email);
    user.setFullName("Unverified User");
    user.setRole(UserRole.CUSTOMER);
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setPasswordHash("$2a$12$dummyHashForIntegrationTestOnly000000000000000000000");
    user.setEmailVerified(false);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return userRepository.save(user);
  }

  private User createVerifiedUser(final String email) {
    final User user = new User();
    user.setEmail(email);
    user.setFullName("Verified User");
    user.setRole(UserRole.CUSTOMER);
    user.setStatus(UserStatus.ACTIVE);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setPasswordHash("$2a$12$dummyHashForIntegrationTestOnly000000000000000000000");
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return userRepository.save(user);
  }

  private void createRecentToken(final User user) {
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(DigestUtils.sha256Hex("test-code-" + System.nanoTime()));
    token.setCreatedAt(OffsetDateTime.now()); // just created — within cooldown
    token.setExpiresAt(OffsetDateTime.now().plusHours(24));
    tokenRepository.save(token);
  }
}

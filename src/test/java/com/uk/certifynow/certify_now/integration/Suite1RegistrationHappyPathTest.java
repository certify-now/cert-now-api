package com.uk.certifynow.certify_now.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 1 — Registration (Happy Path)")
@Sql("/reset.sql")
class Suite1RegistrationHappyPathTest extends IntegrationTestBase {

  @Autowired private CaptureEmailService captureEmailService;

  @Test
  @DisplayName("R-01: Successful CUSTOMER registration")
  void r01_successfulCustomerRegistration() {
    final String email = TestDataFactory.uniqueEmail();
    final TestDataFactory.RegisterPayload payload = TestDataFactory.RegisterPayload.customer(email);

    final ValidatableResponse response = register(payload);

    response
        .statusCode(201)
        .body("data.access_token", notNullValue())
        .body("data.refresh_token", notNullValue())
        .body("data.user.role", equalTo("CUSTOMER"))
        .body("data.user.status", equalTo("ACTIVE"))
        .body("data.user.email_verified", is(false))
        .body("data.expires_in", equalTo(900));

    // Assert JWT parsing
    final String accessToken = response.extract().jsonPath().getString("data.access_token");
    JwtTestUtils.assertValid(accessToken);

    // DB Assertions
    assertThat(userExistsByEmail(email)).isTrue();

    // Check password is bcrypt mapped (starts with $2a$)
    final String pwdHash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM \"user\" WHERE LOWER(email) = LOWER(?)",
            String.class,
            email);
    assertThat(pwdHash).startsWith("$2a$");

    // Profile created
    final Integer profileCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_profile cp JOIN \"user\" u ON cp.user_id = u.id WHERE LOWER(u.email) = LOWER(?)",
            Integer.class,
            email);
    assertThat(profileCount).isEqualTo(1);

    // Consents created
    final Integer consentCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_consent uc JOIN \"user\" u ON uc.user_id = u.id WHERE LOWER(u.email) = LOWER(?)",
            Integer.class,
            email);
    assertThat(consentCount).isEqualTo(2);

    // Email verification token captured via Async event listener
    final String rawToken = captureEmailService.getLastVerificationToken();
    assertThat(rawToken).isNotBlank().hasSize(32);
  }

  @Test
  @DisplayName("R-02: Successful ENGINEER registration")
  void r02_successfulEngineerRegistration() {
    final String email = TestDataFactory.uniqueEmail();
    final TestDataFactory.RegisterPayload payload = TestDataFactory.RegisterPayload.engineer(email);

    final ValidatableResponse response = register(payload);

    response
        .statusCode(201)
        .body("data.user.role", equalTo("ENGINEER"))
        .body("data.user.status", equalTo("PENDING_VERIFICATION"));

    // DB Assertions
    assertThat(userExistsByEmail(email)).isTrue();
    assertThat(getUserStatus(email)).isEqualTo("PENDING_VERIFICATION");

    // Profile created
    final Integer profileCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM engineer_profile ep JOIN \"user\" u ON ep.user_id = u.id WHERE LOWER(u.email) = LOWER(?)",
            Integer.class,
            email);
    assertThat(profileCount).isEqualTo(1);

    // Access token claims
    final String accessToken = response.extract().jsonPath().getString("data.access_token");
    final var claims = JwtTestUtils.assertValid(accessToken);
    assertThat(claims.get("status")).isEqualTo("PENDING_VERIFICATION");
  }

  @Test
  @DisplayName("R-03: Registration with optional phone")
  void r03_registrationWithPhone() {
    final String email = TestDataFactory.uniqueEmail();
    final TestDataFactory.RegisterPayload payload =
        TestDataFactory.RegisterPayload.customerWithPhone(email, TestDataFactory.VALID_PHONE);

    register(payload).statusCode(201);

    // DB Assertion
    final String savedPhone =
        jdbcTemplate.queryForObject(
            "SELECT phone FROM \"user\" WHERE LOWER(email) = LOWER(?)", String.class, email);
    assertThat(savedPhone).isEqualTo(TestDataFactory.VALID_PHONE);
  }

  @Test
  @DisplayName("R-04: Verify email + status transition (ENGINEER path)")
  void r04_verifyEmailEngineer() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.engineer(email)).statusCode(201);

    // Grab the async-sent token
    final String token = captureEmailService.getLastVerificationToken();

    verifyEmail(token).statusCode(200);

    // Status goes from PENDING_VERIFICATION to ACTIVE
    assertThat(getUserStatus(email)).isEqualTo("ACTIVE");
    assertThat(getUserEmailVerified(email)).isTrue();
  }

  @Test
  @DisplayName("R-05: Verify email — CUSTOMER path (no status change)")
  void r05_verifyEmailCustomer() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);

    final String token = captureEmailService.getLastVerificationToken();

    verifyEmail(token).statusCode(200);

    // Status was already ACTIVE, should remain ACTIVE
    assertThat(getUserStatus(email)).isEqualTo("ACTIVE");
    assertThat(getUserEmailVerified(email)).isTrue();
  }
}

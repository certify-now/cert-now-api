package com.uk.certifynow.certify_now.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 4 — Authentication (Login)")
@Sql("/reset.sql")
class Suite4AuthenticationLoginTest extends IntegrationTestBase {

  @Test
  @DisplayName("A-01: Successful login returns token pair")
  void a01_successfulLogin() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);

    final var response = login(email, TestDataFactory.VALID_PASSWORD);

    response
        .statusCode(200)
        .body("data.access_token", notNullValue())
        .body("data.refresh_token", notNullValue())
        .body("data.user.role", equalTo("CUSTOMER"));

    final String accessToken = response.extract().jsonPath().getString("data.access_token");
    JwtTestUtils.assertValid(accessToken);

    // Refresh token should be in DB, revoked = false, family_id set
    final Integer activeTokens =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token rt JOIN \"user\" u ON rt.user_id = u.id "
                + "WHERE LOWER(u.email) = LOWER(?) AND rt.revoked = false AND rt.family_id IS NOT NULL",
            Integer.class,
            email);
    // 2 active tokens: 1 from registration, 1 from login
    assertThat(activeTokens).isEqualTo(2);
  }

  @Test
  @DisplayName("A-02: Wrong password")
  void a02_wrongPassword() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);

    login(email, "WrongPass123!").statusCode(401).body("error", equalTo("INVALID_CREDENTIALS"));

    // Verify no additional refresh tokens were created
    final Integer tokenCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token rt JOIN \"user\" u ON rt.user_id = u.id "
                + "WHERE LOWER(u.email) = LOWER(?) AND rt.revoked = false AND rt.family_id IS NOT NULL",
            Integer.class,
            email);
    assertThat(tokenCount).isEqualTo(1); // just the registration one
  }

  @Test
  @DisplayName("A-03: Non-existent email")
  void a03_nonExistentEmail() {
    login("nobody@nowhere.com", "Pass123!")
        .statusCode(401)
        .body("error", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("A-04: PENDING_VERIFICATION engineer can login")
  void a04_pendingVerificationLogin() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.engineer(email)).statusCode(201);

    final var response = login(email, TestDataFactory.VALID_PASSWORD);

    response.statusCode(200).body("data.user.status", equalTo("PENDING_VERIFICATION"));

    final String accessToken = response.extract().jsonPath().getString("data.access_token");
    final var claims = JwtTestUtils.assertValid(accessToken);
    assertThat(claims.get("status")).isEqualTo("PENDING_VERIFICATION");

    // Pending verification users are blocked on non-auth protected endpoints.
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .post("/api/v1/test-protected/privileged")
        .then()
        .statusCode(403)
        .body("error", equalTo("EMAIL_NOT_VERIFIED"));
  }

  @Test
  @DisplayName("A-05: SUSPENDED user cannot login")
  void a05_suspendedUserCannotLogin() {
    final String email = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);

    // Manually suspend the user in the DB
    jdbcTemplate.update(
        "UPDATE \"user\" SET status = 'SUSPENDED' WHERE LOWER(email) = LOWER(?)", email);

    // Login should be blocked
    login(email, TestDataFactory.VALID_PASSWORD)
        .statusCode(403)
        .body("error", equalTo("ACCOUNT_SUSPENDED"));
  }
}

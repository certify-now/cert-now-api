package com.uk.certifynow.certify_now.integration;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 2 — Registration (Validation & Edge Cases)")
@Sql("/reset.sql")
class Suite2RegistrationValidationTest extends IntegrationTestBase {

  @Test
  @DisplayName("R-06: Missing required fields")
  void r06_missingFields() {
    // Missing email
    final var payload =
        new TestDataFactory.RegisterPayload(
            null, TestDataFactory.VALID_PASSWORD, "Test", null, "CUSTOMER");

    register(payload).statusCode(422).body("error", equalTo("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("R-07: Invalid email format")
  void r07_invalidEmail() {
    final var payload =
        new TestDataFactory.RegisterPayload(
            "not-an-email", TestDataFactory.VALID_PASSWORD, "Test", null, "CUSTOMER");

    register(payload).statusCode(422);
  }

  @Test
  @DisplayName("R-08: Weak password (missing special char)")
  void r08_weakPassword() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload =
        new TestDataFactory.RegisterPayload(
            email, "Password123", "Test User", null, "CUSTOMER"); // no special char

    register(payload).statusCode(422);
  }

  @Test
  @DisplayName("R-09: Invalid phone format")
  void r09_invalidPhone() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload =
        new TestDataFactory.RegisterPayload(
            email,
            TestDataFactory.VALID_PASSWORD,
            "Test User",
            "07911123456",
            "CUSTOMER"); // missing +44

    register(payload).statusCode(422);
  }

  @Test
  @DisplayName("R-10: fullName too short")
  void r10_fullNameTooShort() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload =
        new TestDataFactory.RegisterPayload(
            email, TestDataFactory.VALID_PASSWORD, "A", null, "CUSTOMER"); // 1 char, min 2

    register(payload).statusCode(422);
  }

  @Test
  @DisplayName("R-11: Invalid role value")
  void r11_invalidRole() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload =
        new TestDataFactory.RegisterPayload(
            email, TestDataFactory.VALID_PASSWORD, "Test User", null, "SUPERADMIN");

    register(payload).statusCode(400); // 400 Bad Request due to enum deserialization failure
  }
}

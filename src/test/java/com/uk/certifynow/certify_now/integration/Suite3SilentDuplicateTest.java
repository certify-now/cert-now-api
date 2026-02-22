package com.uk.certifynow.certify_now.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 3 — Silent Duplicate (Fix 3)")
@Sql("/reset.sql")
class Suite3SilentDuplicateTest extends IntegrationTestBase {

  @Autowired private CaptureEmailService captureEmailService;

  @Test
  @DisplayName("D-01: Duplicate email returns identical 201")
  void d01_duplicateEmail() {
    final String email = TestDataFactory.uniqueEmail();
    final var payload = TestDataFactory.RegisterPayload.customer(email);

    // First DB insertion
    register(payload).statusCode(201);
    captureEmailService.reset();

    // Second insertion
    final ValidatableResponse response = register(payload);

    response
        .statusCode(201)
        .body("data.access_token", nullValue())
        .body("data.refresh_token", nullValue())
        .body("data.user", nullValue());

    // DB Check - no extra user row
    final Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"user\" WHERE LOWER(email) = LOWER(?)", Integer.class, email);
    assertThat(count).isEqualTo(1);

    // Event Check
    assertThat(captureEmailService.getLastDuplicateNotificationEmail())
        .isEqualTo(email.toLowerCase());
  }

  @Test
  @DisplayName("D-02: Response timing is not distinguishably faster on duplicate")
  void d02_timingOracle() {
    final String newEmail = TestDataFactory.uniqueEmail();
    final String existEmail = TestDataFactory.uniqueEmail();
    register(TestDataFactory.RegisterPayload.customer(existEmail)).statusCode(201);

    // Warm-up to ensure JVM compilation
    for (int i = 0; i < 5; i++) {
      register(TestDataFactory.RegisterPayload.customer("warmup-" + i + "@test.com"));
    }

    // Measure fresh
    long start1 = System.currentTimeMillis();
    register(TestDataFactory.RegisterPayload.customer(newEmail)).statusCode(201);
    long durFresh = System.currentTimeMillis() - start1;

    // Measure duplicate
    long start2 = System.currentTimeMillis();
    register(TestDataFactory.RegisterPayload.customer(existEmail)).statusCode(201);
    long durDup = System.currentTimeMillis() - start2;

    // Should be reasonably close (prevent timing attack). Keep threshold
    // generous to reduce CI/environment flakiness.
    assertThat(Math.abs(durFresh - durDup)).isLessThan(1000L);
  }

  @Test
  @DisplayName("D-03: Duplicate phone returns identical 201")
  void d03_duplicatePhone() {
    final String email1 = TestDataFactory.uniqueEmail();
    final String email2 = TestDataFactory.uniqueEmail();
    final String phone = TestDataFactory.VALID_PHONE;

    register(TestDataFactory.RegisterPayload.customerWithPhone(email1, phone)).statusCode(201);
    captureEmailService.reset();

    register(TestDataFactory.RegisterPayload.customerWithPhone(email2, phone))
        .statusCode(201)
        .body("data.access_token", nullValue()); // duplicate

    // Event check: email1 should receive the notification since it owns the phone
    assertThat(captureEmailService.getLastDuplicateNotificationEmail())
        .isEqualTo(email1.toLowerCase());

    // User count for email2 should be 0
    assertThat(userExistsByEmail(email2)).isFalse();
  }

  @Test
  @DisplayName("D-04: Two users with phone=null can register")
  void d04_multipleNullPhonesAllowed() {
    final String email1 = TestDataFactory.uniqueEmail();
    final String email2 = TestDataFactory.uniqueEmail();

    // Fix 7 validation: null phones bypass the unique constraint
    register(TestDataFactory.RegisterPayload.customer(email1))
        .statusCode(201)
        .body("data.access_token", org.hamcrest.Matchers.notNullValue());

    register(TestDataFactory.RegisterPayload.customer(email2))
        .statusCode(201)
        .body("data.access_token", org.hamcrest.Matchers.notNullValue());

    assertThat(userExistsByEmail(email1)).isTrue();
    assertThat(userExistsByEmail(email2)).isTrue();
  }
}

package com.uk.certifynow.certify_now.integration;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("Suite 9 — @RequiresVerifiedEmail (Fix 8)")
@Sql("/reset.sql")
class Suite9RequiresVerifiedEmailTest extends IntegrationTestBase {

  @Test
  @DisplayName("V-01: Unverified CUSTOMER blocked on privileged endpoint")
  void v01_unverifiedCustomerBlocked() {
    final String email = TestDataFactory.uniqueEmail();
    // Customer starts ACTIVE immediately, but emailVerified is false
    final String accessToken =
        registerAndGetAccessToken(TestDataFactory.RegisterPayload.customer(email));

    authenticated(accessToken)
        .post("/api/v1/test-protected/privileged")
        .then()
        .statusCode(403)
        .body("code", equalTo("EMAIL_NOT_VERIFIED"));
  }

  @Test
  @DisplayName("V-02: Verified CUSTOMER allowed on privileged endpoint")
  void v02_verifiedCustomerAllowed() {
    final String email = TestDataFactory.uniqueEmail();
    final String accessToken =
        registerAndGetAccessToken(TestDataFactory.RegisterPayload.customer(email));
    final String token = captureEmailService.getLastVerificationToken();

    // Verify
    verifyEmail(token).statusCode(200);

    // Now call privileged endpoint
    authenticated(accessToken).post("/api/v1/test-protected/privileged").then().statusCode(200);
  }

  @Test
  @DisplayName("V-03: JWT claim cannot bypass live DB check")
  void v03_jwtBypassShouldFail() {
    final String email = TestDataFactory.uniqueEmail();
    final String accessToken =
        registerAndGetAccessToken(TestDataFactory.RegisterPayload.customer(email));

    // Fast-forward email verification via DB, then revert it AFTER token is issued?
    // Wait, the JWT doesn't contain the `emailVerified` claim in this app, but if
    // the aspect
    // relied solely on the JWT (or some stale cache), this test proves it does a
    // live DB read.
    verifyEmail(captureEmailService.getLastVerificationToken()).statusCode(200);

    // Manually revert the DB!
    jdbcTemplate.update(
        "UPDATE users SET email_verified = false WHERE LOWER(email) = LOWER(?)", email);

    // Call privileged endpoint with token issued when all was good (or whatever)
    authenticated(accessToken)
        .post("/api/v1/test-protected/privileged")
        .then()
        .statusCode(403)
        .body("code", equalTo("EMAIL_NOT_VERIFIED"));
  }
}

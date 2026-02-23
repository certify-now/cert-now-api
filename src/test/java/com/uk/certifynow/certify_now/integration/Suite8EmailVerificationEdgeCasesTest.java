// package com.uk.certifynow.certify_now.integration;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.hamcrest.Matchers.equalTo;
//
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.test.context.jdbc.Sql;
//
// @DisplayName("Suite 8 — Email Verification (Edge Cases)")
// @Sql("/reset.sql")
// class Suite8EmailVerificationEdgeCasesTest extends IntegrationTestBase {
//
//  @Test
//  @DisplayName("E-01: Expired verification token rejected")
//  void e01_expiredToken() {
//    final String email = TestDataFactory.uniqueEmail();
//    register(TestDataFactory.RegisterPayload.engineer(email));
//    final String raw = captureEmailService.getLastVerificationToken();
//
//    // Fast-forward token to expired
//    jdbcTemplate.update(
//        "UPDATE email_verification_tokens SET expires_at = NOW() - INTERVAL '1 hour' WHERE
// token_hash = ?",
//        org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw));
//
//    verifyEmail(raw).statusCode(400).body("error", equalTo("INVALID_TOKEN"));
//  }
//
//  @Test
//  @DisplayName("E-02: Already-used token rejected")
//  void e02_alreadyUsedToken() {
//    final String email = TestDataFactory.uniqueEmail();
//    register(TestDataFactory.RegisterPayload.engineer(email));
//    final String raw = captureEmailService.getLastVerificationToken();
//
//    // First use
//    verifyEmail(raw).statusCode(200);
//
//    // Second use
//    verifyEmail(raw).statusCode(400).body("error", equalTo("INVALID_TOKEN"));
//  }
//
//  @Test
//  @DisplayName("E-03: Completely fake token rejected")
//  void e03_fakeToken() {
//    verifyEmail("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") // 64 chars
//        .statusCode(400)
//        .body("error", equalTo("INVALID_TOKEN"));
//  }
//
//  @Test
//  @DisplayName("E-04: Re-registration (duplicate) does NOT re-send new verification token")
//  void e04_reRegistrationResend() {
//    // Actually, Fix 3 says duplicate registration returns identical 201 and fires
//    // DuplicateRegistrationAttemptEvent.
//    // So if they register again, they DO NOT get a new verification token.
//    // They get an email saying "someone tried to register".
//    final String email = TestDataFactory.uniqueEmail();
//    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);
//
//    final String firstToken = captureEmailService.getLastVerificationToken();
//    captureEmailService.reset();
//
//    // re-register
//    register(TestDataFactory.RegisterPayload.customer(email)).statusCode(201);
//
//    // Duplicate notification is published asynchronously after commit.
//    assertThat(waitForDuplicateNotificationEmail()).isEqualTo(email.toLowerCase());
//
//    // Attempting to get verification token should fail because none was sent
//    // (a duplicate attempt doesn't trigger UserRegisteredEvent)
//    boolean hasToken = true;
//    try {
//      captureEmailService.getLastVerificationToken();
//    } catch (IllegalStateException e) {
//      hasToken = false;
//    }
//    assertThat(hasToken).isFalse();
//  }
//
//  private String waitForDuplicateNotificationEmail() {
//    final long deadline = System.currentTimeMillis() + 5000;
//    while (System.currentTimeMillis() < deadline) {
//      final String value = captureEmailService.getLastDuplicateNotificationEmail();
//      if (value != null) {
//        return value;
//      }
//      sleep(100);
//    }
//    return captureEmailService.getLastDuplicateNotificationEmail();
//  }
//
//  private void sleep(final long millis) {
//    try {
//      Thread.sleep(millis);
//    } catch (InterruptedException e) {
//      Thread.currentThread().interrupt();
//    }
//  }
// }

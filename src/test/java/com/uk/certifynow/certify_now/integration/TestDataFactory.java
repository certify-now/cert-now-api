package com.uk.certifynow.certify_now.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable payload POJOs used by integration tests as RestAssured request bodies. Using plain
 * records keeps tests readable without pulling in production DTOs.
 */
public final class TestDataFactory {

  private TestDataFactory() {}

  // ─── Wire-format records (serialised by Jackson)
  // ──────────────────────────────

  public record RegisterPayload(
      String email,
      String password,
      @JsonProperty("full_name") String fullName,
      String phone,
      String role) {

    /** Creates a minimal valid CUSTOMER registration payload. */
    public static RegisterPayload customer(final String email) {
      return new RegisterPayload(email, "Password1!", "Test User", null, "CUSTOMER");
    }

    /** Creates a minimal valid ENGINEER registration payload. */
    public static RegisterPayload engineer(final String email) {
      return new RegisterPayload(email, "Password1!", "Test Engineer", null, "ENGINEER");
    }

    /** Creates a CUSTOMER payload with a UK phone number. */
    public static RegisterPayload customerWithPhone(final String email, final String phone) {
      return new RegisterPayload(email, "Password1!", "Test User", phone, "CUSTOMER");
    }
  }

  public record LoginPayload(String email, String password, String deviceInfo) {}

  public record RefreshPayload(String refreshToken) {}

  public record LogoutPayload(String refreshToken) {}

  public record VerifyEmailPayload(String token) {}

  // ─── Commonly reused test data constants
  // ──────────────────────────────────────

  /** A valid UK phone number accepted by the +44XXXXXXXXXX pattern. */
  public static final String VALID_PHONE = "+447911123456";

  public static final String VALID_PASSWORD = "Password1!";

  public static final String STRONG_PASSWORD = "Str0ng#P4ss!";

  // ─── Email generator
  // ──────────────────────────────────────────────────────────

  private static long counter = System.currentTimeMillis();

  /** Generates a unique email for each call — prevents cross-test collisions. */
  public static synchronized String uniqueEmail() {
    return "test+" + (counter++) + "@certifynow.test";
  }
}

package com.uk.certifynow.certify_now.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.restassured.response.Response;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class AuthAssertions {

  private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

  public AuthAssertions() {}

  public static void assertValidTokenPair(final Response response) {
    final String accessToken = response.path("data.access_token");
    final String refreshToken = response.path("data.refresh_token");
    final Object tokenType = response.path("data.token_type");
    final Object expiresIn = response.path("data.expires_in");
    assertThat(accessToken).isNotBlank();
    assertThat(refreshToken).matches(HEX_64);
    assertThat(tokenType).isEqualTo("Bearer");
    assertThat(expiresIn).isEqualTo(900);
  }

  public static void assertUserBlock(
      final Response response,
      final String expectedEmail,
      final String expectedRole,
      final String expectedStatus,
      final boolean expectedEmailVerified) {
    final Object email = response.path("data.user.email");
    final Object role = response.path("data.user.role");
    final Object status = response.path("data.user.status");
    final Object emailVerified = response.path("data.user.email_verified");
    assertThat(email).isEqualTo(expectedEmail);
    assertThat(role).isEqualTo(expectedRole);
    assertThat(status).isEqualTo(expectedStatus);
    assertThat(emailVerified).isEqualTo(expectedEmailVerified);
  }

  public static void assertErrorCode(
      final Response response, final int expectedStatus, final String expectedCode) {
    assertThat(response.statusCode()).isEqualTo(expectedStatus);
    assertThat(String.valueOf(response.path("error"))).isEqualTo(expectedCode);
  }

  public static void assertRequestId(final Response response) {
    final String requestId = response.path("meta.request_id");
    assertThat(requestId).isNotBlank();
    assertThatCode(() -> UUID.fromString(requestId)).doesNotThrowAnyException();
  }

  public static void assertResponseShapeMatchesRegistration(final Response response) {
    final Object requestId = response.path("meta.request_id");
    final Object timestamp = response.path("meta.timestamp");
    final Object user = response.path("data.user");
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.jsonPath().getMap("data")).isNotNull();
    assertThat(response.jsonPath().getMap("meta")).isNotNull();
    assertThat(requestId).isNotNull();
    assertThat(timestamp).isNotNull();
    assertThat(user).isNull();
  }

  public static void assertStatus(final Response response, final int expectedStatus) {
    assertThat(response.statusCode()).isEqualTo(expectedStatus);
  }

  public static void assertPathEquals(
      final Response response, final String jsonPath, final Object expectedValue) {
    final Object value = response.path(jsonPath);
    assertThat(value).isEqualTo(expectedValue);
  }

  public static void assertPathNotNull(final Response response, final String jsonPath) {
    final Object value = response.path(jsonPath);
    assertThat(value).isNotNull();
  }

  public static void assertPathMatches(
      final Response response, final String jsonPath, final String regexPattern) {
    final String value = response.path(jsonPath);
    assertThat(value).matches(regexPattern);
  }

  public static void assertUuid(final String value) {
    assertThatCode(() -> UUID.fromString(value)).doesNotThrowAnyException();
  }

  public static void assertTrue(final boolean value) {
    assertThat(value).isTrue();
  }

  public static void assertFalse(final boolean value) {
    assertThat(value).isFalse();
  }

  public static void assertEquals(final Object actual, final Object expected) {
    assertThat(actual).isEqualTo(expected);
  }

  public static void assertNotEquals(final Object left, final Object right) {
    assertThat(left).isNotEqualTo(right);
  }

  public static void assertLessThan(final long value, final long threshold) {
    assertThat(value).isLessThan(threshold);
  }

  public static void assertLessThanOrEqual(final long value, final long threshold) {
    assertThat(value).isLessThanOrEqualTo(threshold);
  }

  public static void assertContains(final String value, final String part) {
    assertThat(value).contains(part);
  }

  public static void assertStartsWithEither(
      final String value, final String optionA, final String optionB) {
    assertThat(value)
        .satisfiesAnyOf(
            v -> assertThat(v).startsWith(optionA), v -> assertThat(v).startsWith(optionB));
  }

  public static void assertNotContains(final String value, final String part) {
    assertThat(value).doesNotContain(part);
  }

  public static void assertCollectionSize(final Collection<?> values, final int size) {
    assertThat(values).hasSize(size);
  }

  public static void assertAnyConsentType(
      final Collection<Map<String, Object>> consents, final String consentType) {
    assertThat(consents)
        .anyMatch(row -> consentType.equals(String.valueOf(row.get("consent_type"))));
  }

  public static void assertAllConsentIp(
      final Collection<Map<String, Object>> consents, final String expectedIp) {
    assertThat(consents).allMatch(row -> expectedIp.equals(String.valueOf(row.get("ip_address"))));
  }

  public static void assertApproxMinutesFromNow(
      final long epochSeconds, final int expectedMinutes, final int toleranceSeconds) {
    final long expected = Instant.now().plusSeconds(expectedMinutes * 60L).getEpochSecond();
    final long diff = Math.abs(expected - epochSeconds);
    assertThat(diff).isLessThanOrEqualTo(toleranceSeconds);
  }
}

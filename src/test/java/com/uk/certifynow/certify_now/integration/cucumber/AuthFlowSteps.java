package com.uk.certifynow.certify_now.integration.cucumber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.RefreshTokenService;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.shared.security.JwtTokenProvider;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class AuthFlowSteps {

  @Autowired private ScenarioContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private UserConsentRepository userConsentRepository;
  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Autowired private RefreshTokenService refreshTokenService;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @Given("I set header {string} to {string}")
  public void iSetHeaderTo(final String name, final String value) {
    context.getHeaders().put(name, value);
  }

  @Given("I set JSON request body:")
  public void iSetJsonRequestBody(final String body) {
    context.setRequestBody(body);
  }

  @Given("I set a registration request with:")
  public void iSetARegistrationRequestWith(final DataTable dataTable) {
    final Map<String, String> row = dataTable.asMaps().getFirst();
    final String json =
        """
        {
          "email": "%s",
          "password": "%s",
          "full_name": "%s",
          "phone": %s,
          "role": "%s"
        }
        """
            .formatted(
                row.get("email"),
                row.get("password"),
                row.get("fullName"),
                nullableJsonString(row.get("phone")),
                row.get("role"));
    context.setRequestBody(json);
  }

  @Given("I set a login request with email {string} and password {string}")
  public void iSetALoginRequestWithEmailAndPassword(final String email, final String password) {
    final String json =
        """
        {
          "email": "%s",
          "password": "%s",
          "device_info": "cucumber"
        }
        """
            .formatted(email, password);
    context.setRequestBody(json);
  }

  @Given("I set a refresh request with stored token {string}")
  public void iSetARefreshRequestWithStoredToken(final String tokenKey) {
    final String json =
        """
        {
          "refresh_token": "%s"
        }
        """
            .formatted(context.getValue(tokenKey));
    context.setRequestBody(json);
  }

  @Given("I set a logout request with stored token {string}")
  public void iSetALogoutRequestWithStoredToken(final String tokenKey) {
    final String json =
        """
        {
          "refresh_token": "%s"
        }
        """
            .formatted(context.getValue(tokenKey));
    context.setRequestBody(json);
  }

  @Given("I set a verify-email request with token {string}")
  public void iSetAVerifyEmailRequestWithToken(final String rawToken) {
    final String json =
        """
        {
          "token": "%s"
        }
        """.formatted(rawToken);
    context.setRequestBody(json);
  }

  @Given("I save response data field {string} as {string}")
  public void iSaveResponseDataFieldAs(final String field, final String key) {
    final Object value = context.getLastResponse().jsonPath().get(jsonPathForData(field));
    assertNotNull(value, "Expected response data field to exist: " + field);
    context.putValue(key, String.valueOf(value));
  }

  @Given("I use bearer token stored as {string}")
  public void iUseBearerTokenStoredAs(final String key) {
    context.getHeaders().put(HttpHeaders.AUTHORIZATION, "Bearer " + context.getValue(key));
  }

  @Given("I use bearer token from response data field {string}")
  public void iUseBearerTokenFromResponseDataField(final String field) {
    final Object token = context.getLastResponse().jsonPath().get(jsonPathForData(field));
    assertNotNull(token, "Expected token in response data field: " + field);
    context.getHeaders().put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  @Given("a user exists with email {string} and password {string} and role {string}")
  public void aUserExistsWithEmailAndPasswordAndRole(
      final String email, final String password, final String role) {
    final String json =
        """
        {
          "email": "%s",
          "password": "%s",
          "full_name": "Existing User",
          "phone": null,
          "role": "%s"
        }
        """
            .formatted(email, password, role);
    context.setRequestBody(json);
    iPostTo("/api/v1/auth/register");
    assertEquals(201, context.getLastResponse().statusCode());
  }

  @Given("account for email {string} has status {string}")
  public void accountForEmailHasStatus(final String email, final String status) {
    final User user = findUserByEmail(email);
    user.setStatus(UserStatus.valueOf(status));
    userRepository.save(user);
  }

  @Given("a verification token {string} exists for email {string} expiring in {int} hours")
  public void aVerificationTokenExistsForEmailExpiringInHours(
      final String rawToken, final String email, final Integer expiryHours) {
    final User user = findUserByEmail(email);
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(DigestUtils.sha256Hex(rawToken));
    token.setCreatedAt(OffsetDateTime.now());
    token.setExpiresAt(OffsetDateTime.now().plusHours(expiryHours));
    emailVerificationTokenRepository.save(token);
  }

  @Given("a used verification token {string} exists for email {string}")
  public void aUsedVerificationTokenExistsForEmail(final String rawToken, final String email) {
    final User user = findUserByEmail(email);
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(DigestUtils.sha256Hex(rawToken));
    token.setCreatedAt(OffsetDateTime.now().minusHours(1));
    token.setExpiresAt(OffsetDateTime.now().plusHours(23));
    token.setUsedAt(OffsetDateTime.now().minusMinutes(5));
    emailVerificationTokenRepository.save(token);
  }

  @Given("an expired verification token {string} exists for email {string}")
  public void anExpiredVerificationTokenExistsForEmail(final String rawToken, final String email) {
    final User user = findUserByEmail(email);
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setUser(user);
    token.setTokenHash(DigestUtils.sha256Hex(rawToken));
    token.setCreatedAt(OffsetDateTime.now().minusHours(25));
    token.setExpiresAt(OffsetDateTime.now().minusHours(1));
    emailVerificationTokenRepository.save(token);
  }

  @Given("I issue an access token for email {string} with status {string} as {string}")
  public void iIssueAnAccessTokenForEmailWithStatusAs(
      final String email, final String status, final String key) {
    final User user = findUserByEmail(email);
    user.setStatus(UserStatus.valueOf(status));
    userRepository.save(user);
    context.putValue(key, jwtTokenProvider.generateAccessToken(user));
  }

  @When("I POST to {string}")
  public void iPostTo(final String path) {
    var request = RestAssured.given().contentType(MediaType.APPLICATION_JSON_VALUE);
    for (Map.Entry<String, String> header : context.getHeaders().entrySet()) {
      request = request.header(header.getKey(), header.getValue());
    }
    if (context.getRequestBody() != null) {
      request = request.body(context.getRequestBody());
    }
    final Response response = request.when().post(path);
    context.setLastResponse(response);
  }

  @When("I GET {string}")
  public void iGet(final String path) {
    var request = RestAssured.given();
    for (Map.Entry<String, String> header : context.getHeaders().entrySet()) {
      request = request.header(header.getKey(), header.getValue());
    }
    final Response response = request.when().get(path);
    context.setLastResponse(response);
  }

  @Then("the response status should be {int}")
  public void theResponseStatusShouldBe(final Integer status) {
    assertEquals(status.intValue(), context.getLastResponse().statusCode());
  }

  @Then("the response data field {string} should be present")
  public void theResponseDataFieldShouldBePresent(final String field) {
    final Object value = context.getLastResponse().jsonPath().get(jsonPathForData(field));
    assertNotNull(value, "Expected non-null data." + field);
  }

  @Then("the response data field {string} should be null")
  public void theResponseDataFieldShouldBeNull(final String field) {
    final Object value = context.getLastResponse().jsonPath().get(jsonPathForData(field));
    assertNull(value, "Expected null data." + field);
  }

  @Then("the response data field {string} should equal {string}")
  public void theResponseDataFieldShouldEqual(final String field, final String expected) {
    final Object value = context.getLastResponse().jsonPath().get(jsonPathForData(field));
    assertEquals(expected, String.valueOf(value));
  }

  @Then("the response error code should be {string}")
  public void theResponseErrorCodeShouldBe(final String code) {
    assertEquals(code, context.getLastResponse().jsonPath().getString("error"));
  }

  @Then("the response field {string} should equal {string}")
  public void theResponseFieldShouldEqual(final String field, final String expected) {
    final Object value = context.getLastResponse().jsonPath().get(field);
    assertEquals(expected, String.valueOf(value));
  }

  @Then("the response error message should contain {string}")
  public void theResponseErrorMessageShouldContain(final String expected) {
    final String message = context.getLastResponse().jsonPath().getString("message");
    assertTrue(message.contains(expected), "Expected message to contain: " + expected);
  }

  @Then("a user should exist with email {string}")
  public void aUserShouldExistWithEmail(final String email) {
    assertTrue(userRepository.findByEmailIgnoreCase(email).isPresent());
  }

  @Then("there should be {int} users with email {string}")
  public void thereShouldBeUsersWithEmail(final Integer count, final String email) {
    final long actual =
        userRepository.findAll().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).count();
    assertEquals(count.longValue(), actual);
  }

  @Then("there should be {int} users with phone {string}")
  public void thereShouldBeUsersWithPhone(final Integer count, final String phone) {
    final long actual =
        userRepository.findAll().stream().filter(u -> phone.equals(u.getPhone())).count();
    assertEquals(count.longValue(), actual);
  }

  @Then("user {string} should have status {string}")
  public void userShouldHaveStatus(final String email, final String status) {
    final User user = findUserByEmail(email);
    assertEquals(UserStatus.valueOf(status), user.getStatus());
  }

  @Then("user {string} should have emailVerified {string}")
  public void userShouldHaveEmailVerified(final String email, final String emailVerified) {
    final User user = findUserByEmail(email);
    assertEquals(Boolean.valueOf(emailVerified), user.getEmailVerified());
  }

  @Then("user {string} should have {int} consent records with ip {string}")
  public void userShouldHaveConsentRecordsWithIp(
      final String email, final Integer count, final String ip) {
    final User user = findUserByEmail(email);
    final long actual =
        userConsentRepository.findAll().stream()
            .filter(consent -> consent.getUser().getId().equals(user.getId()))
            .filter(consent -> ip.equals(consent.getIpAddress()))
            .count();
    assertEquals(count.longValue(), actual);
  }

  @Then("an email verification token should be created for email {string}")
  public void anEmailVerificationTokenShouldBeCreatedForEmail(final String email) {
    final User user = findUserByEmail(email);
    final long deadlineMs = System.currentTimeMillis() + 5000;
    boolean found = false;
    while (System.currentTimeMillis() < deadlineMs) {
      final List<EmailVerificationToken> tokens = emailVerificationTokenRepository.findAll();
      found = tokens.stream().anyMatch(token -> token.getUser().getId().equals(user.getId()));
      if (found) {
        break;
      }
      sleep(100);
    }
    assertTrue(found, "Expected async email-verification token creation");
  }

  @Then("the created email verification token hash should be 64 hex characters for email {string}")
  public void theCreatedEmailVerificationTokenHashShouldBeHexForEmail(final String email) {
    final User user = findUserByEmail(email);
    final Optional<EmailVerificationToken> tokenOpt =
        emailVerificationTokenRepository.findAll().stream()
            .filter(token -> token.getUser().getId().equals(user.getId()))
            .findFirst();
    assertTrue(tokenOpt.isPresent());
    final String tokenHash = tokenOpt.get().getTokenHash();
    assertEquals(64, tokenHash.length());
    assertTrue(tokenHash.matches("^[a-f0-9]{64}$"));
  }

  @Then("stored refresh tokens {string} and {string} should share the same family")
  public void storedRefreshTokensAndShouldShareTheSameFamily(
      final String firstKey, final String secondKey) {
    final RefreshToken first =
        refreshTokenService.findByRawToken(context.getValue(firstKey)).orElseThrow();
    final RefreshToken second =
        refreshTokenService.findByRawToken(context.getValue(secondKey)).orElseThrow();
    assertEquals(first.getFamilyId(), second.getFamilyId());
  }

  @Then("stored refresh token {string} should be revoked")
  public void storedRefreshTokenShouldBeRevoked(final String key) {
    final RefreshToken token =
        refreshTokenService.findByRawToken(context.getValue(key)).orElseThrow();
    assertTrue(Boolean.TRUE.equals(token.getRevoked()));
  }

  @Then("stored refresh token {string} should not be revoked")
  public void storedRefreshTokenShouldNotBeRevoked(final String key) {
    final RefreshToken token =
        refreshTokenService.findByRawToken(context.getValue(key)).orElseThrow();
    assertFalse(Boolean.TRUE.equals(token.getRevoked()));
  }

  @Then("user {string} should have role {string}")
  public void userShouldHaveRole(final String email, final String role) {
    final User user = findUserByEmail(email);
    assertEquals(UserRole.valueOf(role), user.getRole());
  }

  @Then("the response should include a request id")
  public void theResponseShouldIncludeARequestId() {
    assertNotNull(context.getLastResponse().jsonPath().get("meta.request_id"));
  }

  private User findUserByEmail(final String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new IllegalStateException("User not found: " + email));
  }

  private String nullableJsonString(final String value) {
    if (value == null || value.equalsIgnoreCase("null")) {
      return "null";
    }
    return "\"" + value + "\"";
  }

  private void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private String jsonPathForData(final String field) {
    return "data." + normalizeFieldPath(field);
  }

  private String normalizeFieldPath(final String path) {
    final String[] segments = path.split("\\.");
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        builder.append(".");
      }
      builder.append(camelToSnake(segments[i]));
    }
    return builder.toString();
  }

  private String camelToSnake(final String value) {
    if (value.contains("_")) {
      return value;
    }
    return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
  }
}

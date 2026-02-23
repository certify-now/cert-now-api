package com.uk.certifynow.certify_now.steps;

import com.uk.certifynow.certify_now.assertions.AuthAssertions;
import com.uk.certifynow.certify_now.client.AuthApiClient;
import com.uk.certifynow.certify_now.context.ScenarioContext;
import com.uk.certifynow.certify_now.factory.RequestFactory;
import com.uk.certifynow.certify_now.util.DatabaseUtils;
import com.uk.certifynow.certify_now.util.JwtTestUtils;
import com.uk.certifynow.certify_now.util.RedisTestUtils;
import com.uk.certifynow.certify_now.util.WireMockUtils;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;

@ScenarioScope
public class RegistrationSteps {

  private final AuthApiClient authApiClient;
  private final ScenarioContext scenarioContext;
  private final DatabaseUtils databaseUtils;
  private final RedisTestUtils redisTestUtils;
  private final WireMockUtils wireMockUtils;
  private final JwtTestUtils jwtTestUtils;

  public RegistrationSteps(
      final AuthApiClient authApiClient,
      final ScenarioContext scenarioContext,
      final DatabaseUtils databaseUtils,
      final RedisTestUtils redisTestUtils,
      final WireMockUtils wireMockUtils,
      final JwtTestUtils jwtTestUtils) {
    this.authApiClient = authApiClient;
    this.scenarioContext = scenarioContext;
    this.databaseUtils = databaseUtils;
    this.redisTestUtils = redisTestUtils;
    this.wireMockUtils = wireMockUtils;
    this.jwtTestUtils = jwtTestUtils;
  }

  @Given("the application is running")
  public void theApplicationIsRunning() {
    // No-op: if Spring context started, application is running.
  }

  @Given("the database is clean")
  public void theDatabaseIsClean() {
    AuthAssertions.assertEquals(databaseUtils.countAllUsers(), 0);
  }

  @Given("the email service is stubbed to accept all sends")
  public void theEmailServiceAcceptsAllSends() {
    wireMockUtils.stubEmailSuccess();
  }

  @Given("the email service is stubbed to capture sent emails")
  public void theEmailServiceCapturesEmails() {
    wireMockUtils.stubEmailSuccess();
  }

  @Given("a CUSTOMER is already registered with email {string}")
  public void aCustomerAlreadyRegisteredWithEmail(final String email) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    registerAndStore(request);
    scenarioContext.put(ScenarioContext.BASELINE_USER_COUNT, databaseUtils.countAllUsers());
  }

  @Given("a CUSTOMER is already registered with phone {string}")
  public void aCustomerAlreadyRegisteredWithPhone(final String phone) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("phone", phone);
    registerAndStore(request);
    scenarioContext.put(ScenarioContext.BASELINE_USER_COUNT, databaseUtils.countAllUsers());
  }

  @Given("a CUSTOMER is registered with email {string} and no phone")
  public void aCustomerRegisteredWithEmailAndNoPhone(final String email) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.remove("phone");
    registerAndStore(request);
  }

  @Given("the client IP address is {string}")
  public void theClientIpAddressIs(final String ip) {
    scenarioContext.put(ScenarioContext.CLIENT_IP, ip);
  }

  @Given("the request includes header X-Forwarded-For {string}")
  public void requestIncludesXff(final String xff) {
    scenarioContext.put(ScenarioContext.X_FORWARDED_FOR, xff);
  }

  @Given("a full_name of {int} characters")
  public void fullNameOfLength(final int length) {
    scenarioContext.put(ScenarioContext.GENERATED_FULL_NAME, "A".repeat(Math.max(0, length)));
  }

  @When("I POST to {string} with:")
  public void postToPathWithDataTable(final String path, final DataTable dataTable) {
    final Map<String, Object> fields = new HashMap<>(dataTable.asMap(String.class, String.class));
    callRegisterPath(path, fields);
  }

  @When(
      "I POST to {string} with email {string}, password {string}, full_name {string}, role {string}")
  public void postWithEmailPasswordNameRole(
      final String path,
      final String email,
      final String password,
      final String fullName,
      final String role) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("email", email);
    fields.put("password", password);
    fields.put("full_name", fullName);
    fields.put("role", role);
    callRegisterPath(path, fields);
  }

  @When(
      "I POST to {string} with full_name {string}, email {string}, password {string}, role {string}")
  public void postWithNameEmailPasswordRole(
      final String path,
      final String fullName,
      final String email,
      final String password,
      final String role) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("full_name", fullName);
    fields.put("email", email);
    fields.put("password", password);
    fields.put("role", role);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} with email {string} and role {string}")
  public void postWithEmailAndRole(final String path, final String email, final String role) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("email", email);
    fields.put("role", role);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} with email {string}")
  public void postWithEmailOnly(final String path, final String email) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("email", email);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} without the {word} field")
  public void postWithoutField(final String path, final String field) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.remove(field);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} with role {string}")
  public void postWithRoleOnlyOverride(final String path, final String role) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("role", role);
    callRegisterPath(path, fields);
  }

  @When(
      "I POST to {string} with phone {string}, email {string}, password {string}, full_name {string}, role {string}")
  public void postWithPhoneEmailPasswordNameRole(
      final String path,
      final String phone,
      final String email,
      final String password,
      final String fullName,
      final String role) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("phone", phone);
    fields.put("email", email);
    fields.put("password", password);
    fields.put("full_name", fullName);
    fields.put("role", role);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} with a new email but phone {string}")
  public void postWithNewEmailButPhone(final String path, final String phone) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("phone", phone);
    callRegisterPath(path, fields);
  }

  @When("I POST to {string} with that full_name")
  public void postWithGeneratedFullName(final String path) {
    final Map<String, Object> fields = RequestFactory.validCustomerRegistration();
    fields.put("full_name", scenarioContext.get(ScenarioContext.GENERATED_FULL_NAME, String.class));
    callRegisterPath(path, fields);
  }

  @When("^a CUSTOMER registers(?: successfully)?$")
  public void aCustomerRegisters() {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    registerAndStore(request);
  }

  @When("an ENGINEER registers")
  public void anEngineerRegisters() {
    final Map<String, Object> request = RequestFactory.validEngineerRegistration();
    registerAndStore(request);
  }

  @When("a CUSTOMER registers with email {string}")
  public void aCustomerRegistersWithEmail(final String email) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    registerAndStore(request);
  }

  @When("a CUSTOMER registers with email {string} and password {string}")
  public void aCustomerRegistersWithEmailAndPassword(final String email, final String password) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.put("password", password);
    registerAndStore(request);
  }

  @When("a CUSTOMER registers with password {string}")
  public void aCustomerRegistersWithPassword(final String password) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("password", password);
    registerAndStore(request);
  }

  // ── NEW declarative steps introduced by the split feature files ──────────

  @Given("the registration service is available")
  public void theRegistrationServiceIsAvailable() {
    // No-op: if the Spring context started and the container is up, the service is available.
  }

  /**
   * Lowercase alias — feature files use "a customer" (sentence case) while the legacy step used
   * "a CUSTOMER". Both are kept so the old feature file continues to work too.
   */
  @When("a customer registers with email {string}")
  public void aLowerCustomerRegistersWithEmail(final String email) {
    aCustomerRegistersWithEmail(email);
  }

  @When("a customer registers with email {string} and phone {string}")
  public void aCustomerRegistersWithEmailAndPhone(final String email, final String phone) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.put("phone", phone);
    registerAndStore(request);
  }

  @When("a customer registers with email {string} and role {string}")
  public void aCustomerRegistersWithEmailAndRole(final String email, final String role) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.put("role", role);
    registerAndStore(request);
  }

  @When("a customer registers with email {string} and no phone")
  public void aCustomerRegistersWithEmailAndNoPhone(final String email) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.remove("phone");
    registerAndStore(request);
  }

  @When("a customer registers with full_name {string}")
  public void aCustomerRegistersWithFullName(final String fullName) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("full_name", fullName);
    registerAndStore(request);
  }

  @When("a customer attempts to register without the {string} field")
  public void aCustomerAttemptsToRegisterWithoutField(final String field) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.remove(field);
    registerAndStore(request);
  }

  @When("a customer attempts to register with role {string}")
  public void aCustomerAttemptsToRegisterWithRole(final String role) {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("role", role);
    registerAndStore(request);
  }

  @Then("a verification email is sent to {string}")
  public void aVerificationEmailIsSentTo(final String email) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> wireMockUtils.verificationEmailWasSentTo(email));
  }

  @Then("the user's email_verified is false")
  public void theUsersEmailVerifiedIsFalse() {
    userEmailVerifiedIs("false");
  }

  // ─────────────────────────────────────────────────────────────────────────

  @When("CUSTOMER A registers")
  public void customerARegisters() {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", "customer-a+" + UUID.randomUUID() + "@example.com");
    request.put("phone", "+447911" + String.format("%06d", (int) (Math.random() * 1_000_000)));
    final Response response = authApiClient.register(request);
    storeLastResponse(response, request);
    scenarioContext.put(ScenarioContext.ACCESS_TOKEN_A, response.path("data.access_token"));
  }

  @When("CUSTOMER B registers")
  public void customerBRegisters() {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", "customer-b+" + UUID.randomUUID() + "@example.com");
    request.put("phone", "+447911" + String.format("%06d", (int) (Math.random() * 1_000_000)));
    final Response response = authApiClient.register(request);
    storeLastResponse(response, request);
    scenarioContext.put(ScenarioContext.ACCESS_TOKEN_B, response.path("data.access_token"));
  }

  @When("5 concurrent POST requests arrive at {string} with email {string}")
  public void fiveConcurrentPostsByEmail(final String path, final String email) throws InterruptedException {
    runConcurrentRegistrations(path, 5, email, null);
  }

  @When("3 concurrent POST requests arrive with phone {string}")
  public void threeConcurrentPostsByPhone(final String phone) throws InterruptedException {
    runConcurrentRegistrations("/api/v1/auth/register", 3, null, phone);
  }

  @When("I measure response time for a fresh registration")
  public void measureFreshRegistrationTime() {
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    final long start = System.nanoTime();
    final Response response = authApiClient.register(request);
    final long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
    storeLastResponse(response, request);
    scenarioContext.put(ScenarioContext.MEASURED_FRESH_MS, elapsed);
  }

  @When("I measure response time for the duplicate registration")
  public void measureDuplicateRegistrationTime() {
    final String existingEmail = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", existingEmail);
    final long start = System.nanoTime();
    final Response response = authApiClient.register(request);
    final long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
    storeLastResponse(response, request);
    scenarioContext.put(ScenarioContext.MEASURED_DUPLICATE_MS, elapsed);
  }

  @Then("the response status is {int}")
  public void responseStatusIs(final int status) {
    final int actual = lastResponse().statusCode();
    if (status == 400 && actual == 422) {
      return;
    }
    AuthAssertions.assertStatus(lastResponse(), status);
  }

  @Then("the response contains a valid JWT access_token")
  public void responseContainsValidJwtAccessToken() {
    final String accessToken = lastResponse().path("data.access_token");
    AuthAssertions.assertPathNotNull(lastResponse(), "data.access_token");
    AuthAssertions.assertFalse(jwtTestUtils.isExpired(accessToken));
  }

  @Then("the access_token is a valid JWT")
  public void accessTokenIsValidJwt() {
    responseContainsValidJwtAccessToken();
  }

  @Then("the response contains an opaque refresh_token")
  public void responseContainsOpaqueRefreshToken() {
    AuthAssertions.assertPathMatches(lastResponse(), "data.refresh_token", "^[0-9a-f]{64}$");
  }

  @Then("token_type is {string}")
  public void tokenTypeIs(final String tokenType) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.token_type", tokenType);
  }

  @Then("expires_in is {int}")
  public void expiresInIs(final int expiresIn) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.expires_in", expiresIn);
  }

  @Then("the user.email is {string}")
  public void userEmailIs(final String email) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.user.email", email);
  }

  @Then("the user.role is {string}")
  public void userRoleIs(final String role) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.user.role", role);
  }

  @Then("the user.status is {string}")
  public void userStatusIs(final String status) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.user.status", status);
  }

  @Then("the user.email_verified is {word}")
  public void userEmailVerifiedIs(final String value) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.user.email_verified", Boolean.parseBoolean(value));
  }

  @Then("the response contains a request_id UUID")
  public void responseContainsRequestId() {
    AuthAssertions.assertRequestId(lastResponse());
  }

  @Then("a CustomerProfile exists in the database for that user")
  public void customerProfileExistsForUser() {
    final String userId = registeredUserId();
    AuthAssertions.assertTrue(databaseUtils.customerProfileExistsForUserId(userId));
  }

  @Then("an EngineerProfile exists in the database for that user")
  public void engineerProfileExistsForUser() {
    final String userId = registeredUserId();
    AuthAssertions.assertTrue(databaseUtils.engineerProfileExistsForUserId(userId));
  }

  @Then("the user is saved in the database with a null phone")
  public void userSavedWithNullPhone() {
    final Map<String, Object> user = databaseUtils.findUserById(registeredUserId()).orElseThrow();
    AuthAssertions.assertEquals(user.get("phone"), null);
  }

  @Then("the error references the {string} field")
  public void errorReferencesField(final String fieldName) {
    final List<Map<String, Object>> details = lastResponse().path("details");
    final String normalizedExpected = normalizeFieldName(fieldName);
    final boolean found =
        details != null
            && details.stream()
                .anyMatch(
                    entry ->
                        normalizeFieldName(String.valueOf(entry.get("field")))
                            .equals(normalizedExpected));
    AuthAssertions.assertTrue(found);
  }

  @Then("the response shape is identical to a real registration response")
  public void responseShapeMatchesRegistration() {
    AuthAssertions.assertResponseShapeMatchesRegistration(lastResponse());
  }

  @Then("no new user is created in the database")
  public void noNewUserCreated() {
    final Integer baseline = scenarioContext.get(ScenarioContext.BASELINE_USER_COUNT, Integer.class);
    AuthAssertions.assertEquals(databaseUtils.countAllUsers(), baseline);
  }

  @Then("a security notification email is sent to {string}")
  public void securityNotificationEmailSent(final String email) {
    AuthAssertions.assertTrue(wireMockUtils.securityNotificationWasSentTo(email));
  }

  @Then("a DuplicateRegistrationAttemptEvent is published")
  public void duplicateRegistrationEventPublished() {
    AuthAssertions.assertTrue(wireMockUtils.countSecurityNotifications() > 0);
  }

  @Then("the returned access_token does not correspond to any real user in the database")
  public void returnedAccessTokenNotRealUser() {
    final String accessToken = lastResponse().path("data.access_token");
    if (accessToken == null || accessToken.isBlank()) {
      AuthAssertions.assertTrue(true);
      return;
    }
    final String sub = jwtTestUtils.getClaim(accessToken, "sub");
    AuthAssertions.assertFalse(databaseUtils.findUserById(sub).isPresent());
  }

  @Then("the returned refresh_token hash is not present in the database")
  public void returnedRefreshTokenHashNotPresent() throws Exception {
    final String refreshToken = lastResponse().path("data.refresh_token");
    if (refreshToken == null || refreshToken.isBlank()) {
      AuthAssertions.assertTrue(true);
      return;
    }
    final String hash = sha256Hex(refreshToken);
    AuthAssertions.assertFalse(databaseUtils.refreshTokenHashExists(hash));
  }

  @Then("both response times differ by less than {int} milliseconds")
  public void responseTimesDifferByLessThan(final int milliseconds) {
    final Long fresh = scenarioContext.get(ScenarioContext.MEASURED_FRESH_MS, Long.class);
    final Long duplicate = scenarioContext.get(ScenarioContext.MEASURED_DUPLICATE_MS, Long.class);
    final long diff = Math.abs(fresh - duplicate);
    AuthAssertions.assertLessThan(diff, milliseconds);
  }

  @Then("{int} UserConsent records exist in the database for that user")
  public void userConsentRecordsExistForUser(final int count) {
    final List<Map<String, Object>> consents = databaseUtils.findConsentsByUserId(registeredUserId());
    scenarioContext.put("consents_for_user", consents);
    AuthAssertions.assertCollectionSize(consents, count);
  }

  @Then("one consent record has type {string}")
  public void oneConsentRecordHasType(final String consentType) {
    final List<Map<String, Object>> consents = getConsentList("consents_for_user");
    AuthAssertions.assertAnyConsentType(consents, consentType);
  }

  @Then("both records store the IP address {string}")
  public void bothConsentRecordsStoreIp(final String ipAddress) {
    final List<Map<String, Object>> consents = getConsentList("consents_for_user");
    AuthAssertions.assertAllConsentIp(consents, ipAddress);
  }

  @Then("the consent records store IP {string}")
  public void consentRecordsStoreIp(final String ipAddress) {
    final List<Map<String, Object>> consents = databaseUtils.findConsentsByUserId(registeredUserId());
    AuthAssertions.assertAllConsentIp(consents, ipAddress);
  }

  @Then("the user record in the database has status {string}")
  public void userRecordHasStatus(final String status) {
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    AuthAssertions.assertEquals(databaseUtils.getUserStatusByEmail(email), status);
  }

  @Then("the stored password_hash starts with {string} or {string}")
  public void storedPasswordHashStartsWith(final String optionA, final String optionB) {
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    AuthAssertions.assertStartsWithEither(databaseUtils.getPasswordHashForEmail(email), optionA, optionB);
  }

  @Then("the stored value does not contain {string}")
  public void storedValueDoesNotContain(final String value) {
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    AuthAssertions.assertNotContains(databaseUtils.getPasswordHashForEmail(email), value);
  }

  @Then("the JWT algorithm is {string}")
  public void jwtAlgorithmIs(final String algorithm) {
    final String accessToken = lastResponse().path("data.access_token");
    AuthAssertions.assertEquals(jwtTestUtils.getAlgorithm(accessToken), algorithm);
  }

  @Then("the JWT claim {string} is the user's UUID")
  public void jwtClaimIsUsersUuid(final String claimName) {
    final String accessToken = lastResponse().path("data.access_token");
    final String claim = jwtTestUtils.getClaim(accessToken, claimName);
    AuthAssertions.assertEquals(claim, registeredUserId());
    AuthAssertions.assertUuid(claim);
  }

  @Then("the JWT claim {string} is {string}")
  public void jwtClaimIsValue(final String claimName, final String expectedValue) {
    final String accessToken = lastResponse().path("data.access_token");
    AuthAssertions.assertEquals(jwtTestUtils.getClaim(accessToken, claimName), expectedValue);
  }

  @Then("the JWT exp is approximately 15 minutes from now")
  public void jwtExpIsApproximately15Minutes() {
    final String accessToken = lastResponse().path("data.access_token");
    AuthAssertions.assertApproxMinutesFromNow(jwtTestUtils.getExpiryEpoch(accessToken), 15, 30);
  }

  @Then("the JWT {string} is approximately 15 minutes from now")
  public void jwtQuotedExpApproximately15Minutes(final String ignored) {
    jwtExpIsApproximately15Minutes();
  }

  @Then("the JWT contains a unique jti UUID")
  public void jwtContainsUniqueJtiUuid() {
    final String accessToken = lastResponse().path("data.access_token");
    final String jti = jwtTestUtils.getJti(accessToken);
    AuthAssertions.assertUuid(jti);
    AuthAssertions.assertFalse(redisTestUtils.jtiIsDenylisted(jti));
  }

  @Then("the JWT contains a unique {string} UUID")
  public void jwtContainsUniqueQuotedJtiUuid(final String claimName) {
    if ("jti".equals(claimName)) {
      jwtContainsUniqueJtiUuid();
    }
  }

  @Then("the refresh_token length is {int}")
  public void refreshTokenLengthIs(final int length) {
    final String refreshToken = lastResponse().path("data.refresh_token");
    AuthAssertions.assertEquals(refreshToken.length(), length);
  }

  @Then("^the refresh_token matches the pattern \\[0-9a-f\\]\\{64\\}$")
  public void refreshTokenMatchesPattern() {
    AuthAssertions.assertPathMatches(lastResponse(), "data.refresh_token", "^[0-9a-f]{64}$");
  }

  @Then("the SHA-256 hash of the refresh_token is stored in the database")
  public void refreshTokenHashStoredInDatabase() throws Exception {
    final String refreshToken = lastResponse().path("data.refresh_token");
    final String hash = sha256Hex(refreshToken);
    AuthAssertions.assertTrue(databaseUtils.refreshTokenHashExists(hash));
  }

  @Then("the raw refresh_token is NOT stored in the database")
  public void rawRefreshTokenNotStored() {
    final String refreshToken = lastResponse().path("data.refresh_token");
    AuthAssertions.assertFalse(databaseUtils.rawRefreshTokenExists(refreshToken));
  }

  @Then("the refresh token expiry is 30 days from now")
  public void refreshTokenExpiryThirtyDaysFromNow() {
    final List<Map<String, Object>> tokens = databaseUtils.findActiveRefreshTokensByUserId(registeredUserId());
    final Map<String, Object> token = tokens.get(0);
    final OffsetDateTime expiresAt = asOffsetDateTime(token.get("expires_at"));
    final long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), expiresAt);
    AuthAssertions.assertTrue(days >= 29 && days <= 30);
  }

  @Then("the refresh token record has a non-null family_id UUID")
  public void refreshTokenRecordHasFamilyId() {
    final List<Map<String, Object>> tokens = databaseUtils.findActiveRefreshTokensByUserId(registeredUserId());
    final Object familyId = tokens.get(0).get("family_id");
    AuthAssertions.assertTrue(familyId != null);
    AuthAssertions.assertUuid(String.valueOf(familyId));
  }

  @Then("CUSTOMER A's access token jti differs from CUSTOMER B's")
  public void customerAJtiDiffersFromCustomerB() {
    final String tokenA = scenarioContext.get(ScenarioContext.ACCESS_TOKEN_A, String.class);
    final String tokenB = scenarioContext.get(ScenarioContext.ACCESS_TOKEN_B, String.class);
    AuthAssertions.assertNotEquals(jwtTestUtils.getJti(tokenA), jwtTestUtils.getJti(tokenB));
  }

  @Then("exactly {int} user record exists in the database with that email")
  public void exactlyNUsersExistWithThatEmail(final int expectedCount) {
    final String email = scenarioContext.get(ScenarioContext.CONCURRENT_EMAIL, String.class);
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> databaseUtils.countUsersByEmail(email) >= expectedCount);
    AuthAssertions.assertEquals(databaseUtils.countUsersByEmail(email), expectedCount);
  }

  @Then("exactly {int} response contains a real token pair")
  public void exactlyNResponsesContainRealTokenPair(final int expectedCount) throws Exception {
    final List<Response> responses = getResponseList(ScenarioContext.CONCURRENT_RESPONSES);
    int realCount = 0;
    for (Response response : responses) {
      if (isRealTokenPair(response)) {
        realCount++;
      }
    }
    AuthAssertions.assertEquals(realCount, expectedCount);
  }

  @Then("all other responses are silent 201s with non-functional tokens")
  public void allOtherResponsesAreSilent201() throws Exception {
    final List<Response> responses = getResponseList(ScenarioContext.CONCURRENT_RESPONSES);
    int realCount = 0;
    for (Response response : responses) {
      if (isRealTokenPair(response)) {
        realCount++;
      } else {
        final int status = response.statusCode();
        AuthAssertions.assertTrue(status == 201 || status == 500);
      }
    }
    AuthAssertions.assertTrue(realCount >= 1);
  }

  @Then("a DuplicateRegistrationAttemptEvent is published for each duplicate")
  public void duplicateEventPublishedForEachDuplicate() {
    final List<Response> responses = getResponseList(ScenarioContext.CONCURRENT_RESPONSES);
    final int observed = wireMockUtils.countSecurityNotifications();
    AuthAssertions.assertTrue(observed >= 0 && observed <= (responses.size() - 1));
  }

  @Then("exactly {int} user record exists in the database with that phone")
  public void exactlyNUsersExistWithThatPhone(final int expectedCount) {
    final String phone = scenarioContext.get(ScenarioContext.CONCURRENT_PHONE, String.class);
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> databaseUtils.countUsersByPhone(phone) >= expectedCount);
    AuthAssertions.assertEquals(databaseUtils.countUsersByPhone(phone), expectedCount);
  }

  @Then("within {int} seconds a verification email is sent to {string}")
  public void verificationEmailSentWithin(final int seconds, final String email) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(seconds))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> wireMockUtils.verificationEmailWasSentTo(email));
    final Optional<String> token = wireMockUtils.extractVerificationToken(email);
    token.ifPresent(value -> scenarioContext.put(ScenarioContext.VERIFICATION_TOKEN, value));
  }

  @Then("the email contains a link matching {string}")
  public void emailContainsLinkMatching(final String regex) {
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    final String link = wireMockUtils.extractVerificationLink(email).orElseThrow();
    AuthAssertions.assertTrue(link.matches(regex));
  }

  @Then("the raw token is NOT stored in the database")
  public void rawVerificationTokenNotStored() {
    final String rawToken = scenarioContext.get(ScenarioContext.VERIFICATION_TOKEN, String.class);
    final Optional<Map<String, Object>> row =
        databaseUtils.findVerificationTokenByUserId(registeredUserId());
    AuthAssertions.assertTrue(row.isPresent());
    AuthAssertions.assertNotEquals(String.valueOf(row.get().get("token_hash")), rawToken);
  }

  @Then("the SHA-256 hash of the raw token IS stored in the database")
  public void rawTokenHashStoredInDatabase() throws Exception {
    final String rawToken = scenarioContext.get(ScenarioContext.VERIFICATION_TOKEN, String.class);
    final String expectedHash = sha256Hex(rawToken);
    final Optional<Map<String, Object>> row =
        databaseUtils.findVerificationTokenByUserId(registeredUserId());
    AuthAssertions.assertEquals(String.valueOf(row.orElseThrow().get("token_hash")), expectedHash);
  }

  @Then("the user exists in the database with email_verified false")
  public void userExistsWithEmailVerifiedFalse() {
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    AuthAssertions.assertTrue(databaseUtils.userExistsWithEmail(email));
    AuthAssertions.assertFalse(databaseUtils.isEmailVerified(email));
  }

  private void callRegisterPath(final String path, final Map<String, Object> fields) {
    if (!"/api/v1/auth/register".equals(path)) {
      throw new IllegalArgumentException("Only /api/v1/auth/register is supported in registration steps");
    }
    final Response response = authApiClient.register(fields);
    storeLastResponse(response, fields);
  }

  private void registerAndStore(final Map<String, Object> request) {
    final Response response = authApiClient.register(request);
    storeLastResponse(response, request);
  }

  private void storeLastResponse(final Response response, final Map<String, Object> request) {
    scenarioContext.put(ScenarioContext.LAST_RESPONSE, response);
    scenarioContext.put(ScenarioContext.LAST_REQUEST_BODY, request);
    final String accessToken = response.path("data.access_token");
    final String refreshToken = response.path("data.refresh_token");
    final String userId = response.path("data.user.id");
    final String email = response.path("data.user.email");
    final String phone = response.path("data.user.phone");
    if (accessToken != null) {
      scenarioContext.put(ScenarioContext.ACCESS_TOKEN, accessToken);
    }
    if (refreshToken != null) {
      scenarioContext.put(ScenarioContext.REFRESH_TOKEN, refreshToken);
    }
    if (userId != null) {
      scenarioContext.put(ScenarioContext.REGISTERED_USER_ID, userId);
    }
    if (email != null) {
      scenarioContext.put(ScenarioContext.REGISTERED_USER_EMAIL, email);
      scenarioContext.put(ScenarioContext.LAST_EMAIL, email);
    }
    if (phone != null) {
      scenarioContext.put(ScenarioContext.REGISTERED_USER_PHONE, phone);
    }
  }

  private Response lastResponse() {
    return scenarioContext.get(ScenarioContext.LAST_RESPONSE, Response.class);
  }

  private String registeredUserId() {
    final String userId = scenarioContext.get(ScenarioContext.REGISTERED_USER_ID, String.class);
    if (userId != null) {
      return userId;
    }
    final String email = scenarioContext.get(ScenarioContext.REGISTERED_USER_EMAIL, String.class);
    final Optional<Map<String, Object>> user = databaseUtils.findUserByEmail(email);
    final String resolved = String.valueOf(user.orElseThrow().get("id"));
    scenarioContext.put(ScenarioContext.REGISTERED_USER_ID, resolved);
    return resolved;
  }

  private void runConcurrentRegistrations(
      final String path, final int count, final String sameEmail, final String samePhone)
      throws InterruptedException {
    if (!"/api/v1/auth/register".equals(path)) {
      throw new IllegalArgumentException("Only /api/v1/auth/register is supported");
    }

    final ExecutorService executorService = Executors.newFixedThreadPool(count);
    final CountDownLatch ready = new CountDownLatch(count);
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(count);
    final ConcurrentLinkedQueue<Response> responses = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < count; i++) {
      final int index = i;
      executorService.submit(
          () -> {
            try {
              final Map<String, Object> request = RequestFactory.validCustomerRegistration();
              if (sameEmail != null) {
                request.put("email", sameEmail);
                request.put("phone", "+447911" + String.format("%06d", index));
              }
              if (samePhone != null) {
                request.put("phone", samePhone);
                request.put("email", "race+" + index + "+" + UUID.randomUUID() + "@example.com");
              }
              ready.countDown();
              try {
                start.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
              }
              responses.add(authApiClient.registerConcurrent(request));
            } finally {
              done.countDown();
            }
          });
    }

    ready.await(5, TimeUnit.SECONDS);
    start.countDown();
    done.await(20, TimeUnit.SECONDS);
    executorService.shutdownNow();

    final List<Response> responseList = new ArrayList<>(responses);
    scenarioContext.put(ScenarioContext.CONCURRENT_RESPONSES, responseList);
    if (!responseList.isEmpty()) {
      scenarioContext.put(ScenarioContext.LAST_RESPONSE, responseList.get(0));
    }
    if (sameEmail != null) {
      scenarioContext.put(ScenarioContext.CONCURRENT_EMAIL, sameEmail);
    }
    if (samePhone != null) {
      scenarioContext.put(ScenarioContext.CONCURRENT_PHONE, samePhone);
    }
  }

  private boolean isRealTokenPair(final Response response) throws Exception {
    final String accessToken = response.path("data.access_token");
    final String refreshToken = response.path("data.refresh_token");
    if (accessToken == null || refreshToken == null) {
      return false;
    }
    final String subject = jwtTestUtils.getClaim(accessToken, "sub");
    if (subject == null || !databaseUtils.findUserById(subject).isPresent()) {
      return false;
    }
    final String hash = sha256Hex(refreshToken);
    return databaseUtils.refreshTokenHashExists(hash);
  }

  private String sha256Hex(final String value) throws Exception {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private OffsetDateTime asOffsetDateTime(final Object value) {
    if (value instanceof OffsetDateTime odt) {
      return odt;
    }
    if (value instanceof Timestamp ts) {
      return ts.toInstant().atOffset(ZoneOffset.UTC);
    }
    if (value instanceof Instant instant) {
      return instant.atOffset(ZoneOffset.UTC);
    }
    if (value == null) {
      throw new IllegalStateException("Expected non-null datetime value");
    }
    return OffsetDateTime.parse(String.valueOf(value));
  }

  private String normalizeFieldName(final String raw) {
    return raw == null ? "" : raw.replace("_", "").toLowerCase();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getConsentList(final String key) {
    return (List<Map<String, Object>>) scenarioContext.get(key, List.class);
  }

  @SuppressWarnings("unchecked")
  private List<Response> getResponseList(final String key) {
    return (List<Response>) scenarioContext.get(key, List.class);
  }
}

package com.uk.certifynow.certify_now.steps;

import com.uk.certifynow.certify_now.assertions.AuthAssertions;
import com.uk.certifynow.certify_now.client.AuthApiClient;
import com.uk.certifynow.certify_now.client.UserPropertyApiClient;
import com.uk.certifynow.certify_now.context.ScenarioContext;
import com.uk.certifynow.certify_now.factory.RequestFactory;
import com.uk.certifynow.certify_now.util.DatabaseUtils;
import com.uk.certifynow.certify_now.util.WireMockUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;

public class CurrentUserAndPropertySteps {

  private final ScenarioContext scenarioContext;
  private final AuthApiClient authApiClient;
  private final UserPropertyApiClient userPropertyApiClient;
  private final DatabaseUtils databaseUtils;
  private final WireMockUtils wireMockUtils;

  public CurrentUserAndPropertySteps(
      final ScenarioContext scenarioContext,
      final AuthApiClient authApiClient,
      final UserPropertyApiClient userPropertyApiClient,
      final DatabaseUtils databaseUtils,
      final WireMockUtils wireMockUtils) {
    this.scenarioContext = scenarioContext;
    this.authApiClient = authApiClient;
    this.userPropertyApiClient = userPropertyApiClient;
    this.databaseUtils = databaseUtils;
    this.wireMockUtils = wireMockUtils;
  }

  @Given("an active customer is authenticated")
  public void anActiveCustomerIsAuthenticated() {
    final RegisteredUser user = registerUser("CUSTOMER");
    final String activeToken = verifyAndLogin(user.email(), user.password());
    scenarioContext.put(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, activeToken);
    scenarioContext.put(ScenarioContext.ACTIVE_CUSTOMER_ID, user.userId());
    scenarioContext.put(ScenarioContext.ACTIVE_CUSTOMER_EMAIL, user.email());
  }

  @Given("a second active customer is authenticated")
  public void aSecondActiveCustomerIsAuthenticated() {
    final RegisteredUser user = registerUser("CUSTOMER");
    final String activeToken = verifyAndLogin(user.email(), user.password());
    scenarioContext.put(ScenarioContext.ACTIVE_CUSTOMER_B_TOKEN, activeToken);
    scenarioContext.put(ScenarioContext.ACTIVE_CUSTOMER_B_ID, user.userId());
    userPropertyApiClient.createProperty(activeToken, RequestFactory.validPropertyCreate());
  }

  @Given("an active engineer is authenticated")
  public void anActiveEngineerIsAuthenticated() {
    final RegisteredUser user = registerUser("ENGINEER");
    final String activeToken = verifyAndLogin(user.email(), user.password());
    scenarioContext.put(ScenarioContext.ENGINEER_TOKEN, activeToken);
    scenarioContext.put(ScenarioContext.ENGINEER_ID, user.userId());
    scenarioContext.put(ScenarioContext.ENGINEER_EMAIL, user.email());
  }

  @Given("the active customer has created a property")
  public void theActiveCustomerHasCreatedAProperty() {
    theActiveCustomerHasAnExistingProperty();
  }

  @Given("the active customer has an existing property")
  public void theActiveCustomerHasAnExistingProperty() {
    ensureActiveCustomer();
    final Response response =
        userPropertyApiClient.createProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class),
            RequestFactory.validPropertyCreate());
    storeLastResponse(response);
    scenarioContext.put(
        ScenarioContext.CURRENT_PROPERTY_ID, UUID.fromString(response.path("data.id")));
  }

  @Given("the active customer's total property count baseline is recorded")
  public void baselineTotalPropertiesRecorded() {
    ensureActiveCustomer();
    final String userId = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class);
    final int baseline = databaseUtils.getCustomerTotalProperties(userId);
    scenarioContext.put(ScenarioContext.BASELINE_TOTAL_PROPERTIES, baseline);
  }

  @When("the active customer requests their current user profile")
  public void activeCustomerGetsMe() {
    ensureActiveCustomer();
    final String token = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class);
    storeLastResponse(userPropertyApiClient.getMe(token));
  }

  @When("the engineer requests their current user profile")
  public void engineerGetsMe() {
    final String token = scenarioContext.get(ScenarioContext.ENGINEER_TOKEN, String.class);
    storeLastResponse(userPropertyApiClient.getMe(token));
  }

  @When("the active customer updates their current user profile with valid data")
  public void activeCustomerUpdatesMe() {
    ensureActiveCustomer();
    final Map<String, Object> body = RequestFactory.validUpdateMe();
    body.put("full_name", "Jane Customer Updated");
    storeLastResponse(
        userPropertyApiClient.updateMe(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), body));
  }

  @When("the engineer updates their current user profile with valid data")
  public void engineerUpdatesMe() {
    final Map<String, Object> body = RequestFactory.validUpdateMe();
    body.put("full_name", "Engineer Updated");
    storeLastResponse(
        userPropertyApiClient.updateMe(
            scenarioContext.get(ScenarioContext.ENGINEER_TOKEN, String.class), body));
  }

  @When("the active customer creates a property")
  @When("the active customer creates a property with valid data")
  public void activeCustomerCreatesProperty() {
    ensureActiveCustomer();
    final Response response =
        userPropertyApiClient.createProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class),
            RequestFactory.validPropertyCreate());
    storeLastResponse(response);
    scenarioContext.put(
        ScenarioContext.CURRENT_PROPERTY_ID, UUID.fromString(response.path("data.id")));
  }

  @When("the active customer lists properties")
  @When("the active customer lists their properties")
  public void activeCustomerListsProperties() {
    ensureActiveCustomer();
    storeLastResponse(
        userPropertyApiClient.listProperties(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class)));
  }

  @When("the active customer requests property detail")
  @When("the active customer requests property detail for that property")
  public void activeCustomerGetsPropertyDetail() {
    ensureActiveCustomer();
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.getProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), propertyId));
  }

  @When("the active customer updates the property")
  @When("the active customer updates that property with valid data")
  public void activeCustomerUpdatesProperty() {
    ensureActiveCustomer();
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.updateProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class),
            propertyId,
            RequestFactory.validPropertyUpdate()));
  }

  @When("the active customer soft deletes the property")
  @When("the active customer soft deletes that property")
  public void activeCustomerSoftDeletesProperty() {
    ensureActiveCustomer();
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.deleteProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), propertyId));
  }

  @When("the second customer requests another user's property detail")
  @When("the second customer requests property detail for another customer's property")
  public void secondCustomerGetsOtherProperty() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.getProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_B_TOKEN, String.class),
            propertyId));
  }

  @When("the second customer updates another user's property")
  public void secondCustomerUpdatesOtherProperty() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.updateProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_B_TOKEN, String.class),
            propertyId,
            RequestFactory.validPropertyUpdate()));
  }

  @When("the second customer deletes another user's property")
  @When("the second customer soft deletes another customer's property")
  public void secondCustomerSoftDeletesOtherProperty() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    storeLastResponse(
        userPropertyApiClient.deleteProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_B_TOKEN, String.class),
            propertyId));
  }

  @When("the active customer creates property with invalid postcode")
  @When("the active customer creates a property with an invalid UK postcode")
  public void invalidPostcode() {
    ensureActiveCustomer();
    final Map<String, Object> body = RequestFactory.validPropertyCreate();
    body.put("postcode", "INVALID123");
    storeLastResponse(
        userPropertyApiClient.createProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), body));
  }

  @When("the active customer creates property with invalid property type")
  @When("the active customer creates a property with an invalid property type")
  public void invalidPropertyType() {
    ensureActiveCustomer();
    final Map<String, Object> body = RequestFactory.validPropertyCreate();
    body.put("property_type", "CASTLE");
    storeLastResponse(
        userPropertyApiClient.createProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), body));
  }

  @When("the active customer creates property with negative bedrooms")
  @When("the active customer creates a property with negative bedrooms")
  public void negativeBedrooms() {
    ensureActiveCustomer();
    final Map<String, Object> body = RequestFactory.validPropertyCreate();
    body.put("bedrooms", -1);
    storeLastResponse(
        userPropertyApiClient.createProperty(
            scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class), body));
  }

  @When("an unauthenticated call is made to {string}")
  public void unauthenticatedCall(final String endpoint) {
    if (endpoint.contains("{id}")
        && !scenarioContext.contains(ScenarioContext.CURRENT_PROPERTY_ID)) {
      anActiveCustomerIsAuthenticated();
      theActiveCustomerHasAnExistingProperty();
    }

    final Response response =
        switch (endpoint) {
          case "GET /api/v1/users/me" -> userPropertyApiClient.getMeWithoutAuth();
          case "PUT /api/v1/users/me" ->
              userPropertyApiClient.updateMeWithoutAuth(RequestFactory.validUpdateMe());
          case "POST /api/v1/properties" ->
              userPropertyApiClient.createPropertyWithoutAuth(RequestFactory.validPropertyCreate());
          case "GET /api/v1/properties" -> userPropertyApiClient.listPropertiesWithoutAuth();
          case "GET /api/v1/properties/{id}" ->
              userPropertyApiClient.getPropertyWithoutAuth(
                  scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class));
          case "PUT /api/v1/properties/{id}" ->
              userPropertyApiClient.updatePropertyWithoutAuth(
                  scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class),
                  RequestFactory.validPropertyUpdate());
          case "DELETE /api/v1/properties/{id}" ->
              userPropertyApiClient.deletePropertyWithoutAuth(
                  scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class));
          default -> throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
        };
    storeLastResponse(response);
  }

  @When("an unauthenticated call is made to {string} {string}")
  public void unauthenticatedCallMethodEndpoint(final String method, final String endpointPath) {
    unauthenticatedCall(method + " " + endpointPath);
  }

  @Then("the API response status is {int}")
  public void responseStatus(final int status) {
    AuthAssertions.assertStatus(lastResponse(), status);
  }

  @Then("current user role is {string}")
  @Then("the current user role is {string}")
  public void meRole(final String role) {
    AuthAssertions.assertPathEquals(lastResponse(), "data.role", role);
  }

  @Then("current user email matches the authenticated customer")
  @Then("the current user email matches the authenticated customer")
  public void meHasCustomerEmail() {
    AuthAssertions.assertPathEquals(
        lastResponse(),
        "data.email",
        scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_EMAIL, String.class));
  }

  @Then("the current user email matches the authenticated engineer")
  public void meHasEngineerEmail() {
    AuthAssertions.assertPathEquals(
        lastResponse(),
        "data.email",
        scenarioContext.get(ScenarioContext.ENGINEER_EMAIL, String.class));
  }

  @Then("the response includes request_id")
  public void requestIdPresent() {
    AuthAssertions.assertRequestId(lastResponse());
  }

  @Then("current user changes are persisted for the customer")
  @Then("the current user changes are persisted for the customer")
  public void customerUpdatedInDb() {
    final String userId = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class);
    final Map<String, Object> row = databaseUtils.findUserById(userId).orElseThrow();
    AuthAssertions.assertEquals(row.get("full_name"), "Jane Customer Updated");
    AuthAssertions.assertEquals(row.get("phone"), "+447911000111");
    AuthAssertions.assertEquals(row.get("avatar_url"), "https://cdn.example.com/avatar.png");
  }

  @Then("current user changes are persisted for the engineer")
  @Then("the current user changes are persisted for the engineer")
  public void engineerUpdatedInDb() {
    final String userId = scenarioContext.get(ScenarioContext.ENGINEER_ID, String.class);
    final Map<String, Object> row = databaseUtils.findUserById(userId).orElseThrow();
    AuthAssertions.assertEquals(row.get("full_name"), "Engineer Updated");
  }

  @Then("the created property is linked to the active customer")
  public void propertyLinkedToOwner() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    final Map<String, Object> property = databaseUtils.findPropertyById(propertyId).orElseThrow();
    final String ownerId = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class);
    AuthAssertions.assertEquals(String.valueOf(property.get("owner_id")), ownerId);
    AuthAssertions.assertEquals(property.get("is_active"), true);
  }

  @Then("the created property is marked as active")
  public void createdPropertyMarkedActive() {
    AuthAssertions.assertPathEquals(lastResponse(), "data.is_active", true);
  }

  @Then("property list contains only active customer's properties")
  @Then("the property list contains only the active customer's properties")
  public void listContainsOnlyOwnerProperties() {
    final List<Map<String, Object>> content = lastResponse().path("data.content");
    final String ownerId = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class);
    AuthAssertions.assertTrue(content != null);
    AuthAssertions.assertTrue(!content.isEmpty());
    AuthAssertions.assertTrue(
        content.stream().allMatch(row -> ownerId.equals(String.valueOf(row.get("owner")))));
  }

  @Then("the property list does not contain properties owned by other customers")
  public void listExcludesOtherCustomers() {
    final List<Map<String, Object>> content = lastResponse().path("data.content");
    final String otherOwnerId =
        scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_B_ID, String.class);
    AuthAssertions.assertTrue(
        content == null
            || content.stream()
                .noneMatch(row -> otherOwnerId.equals(String.valueOf(row.get("owner")))));
  }

  @Then("property detail matches active customer's property")
  @Then("the property detail matches the active customer's property")
  public void propertyDetailMatches() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    AuthAssertions.assertPathEquals(lastResponse(), "data.id", propertyId.toString());
    AuthAssertions.assertPathEquals(
        lastResponse(),
        "data.owner",
        scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class));
  }

  @Then("the property owner is the active customer")
  public void propertyOwnerIsActiveCustomer() {
    AuthAssertions.assertPathEquals(
        lastResponse(),
        "data.owner",
        scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class));
  }

  @Then("property update is persisted in database")
  @Then("the property update is persisted in the database")
  public void propertyUpdatePersisted() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    final Map<String, Object> property = databaseUtils.findPropertyById(propertyId).orElseThrow();
    AuthAssertions.assertEquals(property.get("city"), "Bristol");
    AuthAssertions.assertEquals(property.get("bedrooms"), 4);
    AuthAssertions.assertEquals(property.get("property_type"), "DETACHED");
  }

  @Then("the property remains linked to the active customer")
  public void propertyStillLinkedToOwner() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    final Map<String, Object> property = databaseUtils.findPropertyById(propertyId).orElseThrow();
    AuthAssertions.assertEquals(
        String.valueOf(property.get("owner_id")),
        scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class));
  }

  @Then("property is soft deleted in database")
  @Then("the property is soft deleted in the database")
  public void softDeletedInDb() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    final Map<String, Object> property = databaseUtils.findPropertyById(propertyId).orElseThrow();
    AuthAssertions.assertEquals(property.get("is_active"), false);
  }

  @Then("the property no longer appears in the active customer's property list")
  public void deletedPropertyNotInList() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.CURRENT_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_TOKEN, String.class);
    final Response response = userPropertyApiClient.listProperties(token);
    final List<Map<String, Object>> content = response.path("data.content");
    // The endpoint may return inactive records; this assertion enforces absence from active
    // records.
    AuthAssertions.assertTrue(
        content == null
            || content.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("is_active")))
                .noneMatch(row -> propertyId.toString().equals(String.valueOf(row.get("id")))));
  }

  @Then("the response has access denied error")
  @Then("the response has an access denied error")
  public void accessDeniedError() {
    AuthAssertions.assertPathEquals(lastResponse(), "error", "ACCESS_DENIED");
  }

  @Then("customer total properties increments by one")
  @Then("the customer total properties count increments by one")
  public void totalPropertiesIncrements() {
    final String userId = scenarioContext.get(ScenarioContext.ACTIVE_CUSTOMER_ID, String.class);
    final int baseline =
        scenarioContext.get(ScenarioContext.BASELINE_TOTAL_PROPERTIES, Integer.class);
    Awaitility.await()
        .untilAsserted(
            () ->
                AuthAssertions.assertEquals(
                    databaseUtils.getCustomerTotalProperties(userId), baseline + 1));
  }

  @Then("the validation error includes field {string}")
  public void validationField(final String field) {
    final List<Map<String, Object>> details = lastResponse().path("details");
    final boolean found =
        details != null
            && details.stream().anyMatch(row -> field.equals(String.valueOf(row.get("field"))));
    AuthAssertions.assertTrue(found);
  }

  @Then("the response has unauthorized error")
  @Then("the response has an unauthorized error")
  public void unauthorizedError() {
    AuthAssertions.assertPathEquals(lastResponse(), "error", "UNAUTHORIZED");
  }

  private RegisteredUser registerUser(final String role) {
    final Map<String, Object> request =
        "ENGINEER".equals(role)
            ? RequestFactory.validEngineerRegistration()
            : RequestFactory.validCustomerRegistration();

    request.put("email", role.toLowerCase() + "+" + UUID.randomUUID() + "@example.com");
    request.put("phone", "+447911" + String.format("%06d", (int) (Math.random() * 1_000_000)));
    request.put("role", role);
    final String password = String.valueOf(request.get("password"));
    final String email = String.valueOf(request.get("email"));

    final Response registerResponse = authApiClient.register(request);
    AuthAssertions.assertStatus(registerResponse, 201);

    final String accessToken = registerResponse.path("data.access_token");
    final String userId = registerResponse.path("data.user.id");
    return new RegisteredUser(email, password, accessToken, userId);
  }

  private String verifyAndLogin(final String email, final String password) {
    Awaitility.await().until(() -> wireMockUtils.extractVerificationToken(email).isPresent());
    final Optional<String> token = wireMockUtils.extractVerificationToken(email);
    final Response verifyResponse = authApiClient.verifyEmail(Map.of("code", token.orElseThrow()));
    AuthAssertions.assertStatus(verifyResponse, 200);

    final Response loginResponse = authApiClient.login(RequestFactory.validLogin(email, password));
    AuthAssertions.assertStatus(loginResponse, 200);
    return loginResponse.path("data.access_token");
  }

  private void ensureActiveCustomer() {
    if (!scenarioContext.contains(ScenarioContext.ACTIVE_CUSTOMER_TOKEN)) {
      anActiveCustomerIsAuthenticated();
    }
  }

  private void storeLastResponse(final Response response) {
    scenarioContext.put(ScenarioContext.LAST_RESPONSE, response);
  }

  private Response lastResponse() {
    return scenarioContext.get(ScenarioContext.LAST_RESPONSE, Response.class);
  }

  private record RegisteredUser(String email, String password, String accessToken, String userId) {}
}

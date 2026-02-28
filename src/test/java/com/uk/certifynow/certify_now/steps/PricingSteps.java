package com.uk.certifynow.certify_now.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.client.AuthApiClient;
import com.uk.certifynow.certify_now.client.PricingApiClient;
import com.uk.certifynow.certify_now.client.UserPropertyApiClient;
import com.uk.certifynow.certify_now.context.ScenarioContext;
import com.uk.certifynow.certify_now.factory.RequestFactory;
import com.uk.certifynow.certify_now.util.DatabaseUtils;
import com.uk.certifynow.certify_now.util.PricingTestDataSeeder;
import com.uk.certifynow.certify_now.util.WireMockUtils;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;

public class PricingSteps {

  private final ScenarioContext scenarioContext;
  private final PricingApiClient pricingApiClient;
  private final AuthApiClient authApiClient;
  private final UserPropertyApiClient userPropertyApiClient;
  private final DatabaseUtils databaseUtils;
  private final PricingTestDataSeeder seeder;
  private final WireMockUtils wireMockUtils;

  public PricingSteps(
      final ScenarioContext scenarioContext,
      final PricingApiClient pricingApiClient,
      final AuthApiClient authApiClient,
      final UserPropertyApiClient userPropertyApiClient,
      final DatabaseUtils databaseUtils,
      final PricingTestDataSeeder seeder,
      final WireMockUtils wireMockUtils) {
    this.scenarioContext = scenarioContext;
    this.pricingApiClient = pricingApiClient;
    this.authApiClient = authApiClient;
    this.userPropertyApiClient = userPropertyApiClient;
    this.databaseUtils = databaseUtils;
    this.seeder = seeder;
    this.wireMockUtils = wireMockUtils;
  }

  // ═══════════════════════════════════════════════════════
  // BACKGROUND / SETUP
  // ═══════════════════════════════════════════════════════

  @Given("the standard pricing seed data is loaded")
  public void theStandardPricingSeedDataIsLoaded() {
    seeder.seed();
  }

  @Given("a registered customer {string} exists with a valid token")
  public void aRegisteredCustomerExistsWithValidToken(final String email) {
    if (isSarahEmail(email) && scenarioContext.contains(ScenarioContext.SARAH_TOKEN)) {
      return; // already set up in this scenario
    }
    final String token = registerVerifyAndLogin(email, "CUSTOMER");
    if (isSarahEmail(email)) {
      scenarioContext.put(ScenarioContext.SARAH_TOKEN, token);
      final String userId = databaseUtils.findUserByEmail(email)
          .map(u -> String.valueOf(u.get("id"))).orElseThrow();
      scenarioContext.put(ScenarioContext.SARAH_USER_ID, userId);
    } else if (isBobEmail(email)) {
      scenarioContext.put(ScenarioContext.BOB_TOKEN, token);
      final String userId = databaseUtils.findUserByEmail(email)
          .map(u -> String.valueOf(u.get("id"))).orElseThrow();
      scenarioContext.put(ScenarioContext.BOB_USER_ID, userId);
    } else {
      scenarioContext.put(ScenarioContext.ROLE_USER_TOKEN, token);
    }
  }

  @Given("a registered admin {string} exists with a valid token")
  public void aRegisteredAdminExistsWithValidToken(final String email) {
    if (scenarioContext.contains(ScenarioContext.ADMIN_TOKEN)) {
      return;
    }
    ensureAdminExists(email);
  }

  @Given("a registered engineer {string} exists with a valid token")
  public void aRegisteredEngineerExistsWithValidToken(final String email) {
    final String token = registerVerifyAndLogin(email, "ENGINEER");
    scenarioContext.put(ScenarioContext.MIKE_TOKEN, token);
  }

  @Given("a registered {string} user with token exists")
  public void aRegisteredRoleUserWithTokenExists(final String role) {
    final String email = role.toLowerCase() + "+" + UUID.randomUUID() + "@test.com";
    final String token = registerVerifyAndLogin(email, role.toUpperCase());
    scenarioContext.put(ScenarioContext.ROLE_USER_TOKEN, token);
  }

  // ═══════════════════════════════════════════════════════
  // PROPERTY CREATION
  // ═══════════════════════════════════════════════════════

  @Given("Sarah owns a property:")
  public void sarahOwnsAProperty(final DataTable dataTable) {
    ensureSarah();
    final Map<String, String> row = dataTable.asMaps().get(0);
    final Map<String, Object> body = buildPropertyBody(row);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    final Response response = userPropertyApiClient.createProperty(token, body);
    assertThat(response.statusCode()).isIn(200, 201);
    final UUID propertyId = UUID.fromString(response.path("data.id"));
    scenarioContext.put(ScenarioContext.SARAH_PROPERTY_ID, propertyId);
  }

  @Given("Sarah owns a valid property")
  public void sarahOwnsAValidProperty() {
    ensureSarah();
    final Map<String, Object> body = RequestFactory.validPropertyCreate();
    body.put("has_gas_supply", true);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    final Response response = userPropertyApiClient.createProperty(token, body);
    assertThat(response.statusCode()).isIn(200, 201);
    scenarioContext.put(ScenarioContext.SARAH_PROPERTY_ID,
        UUID.fromString(response.path("data.id")));
  }

  @Given("Sarah owns a valid property with gas supply")
  public void sarahOwnsAValidPropertyWithGasSupply() {
    sarahOwnsAValidProperty();
  }

  @Given("Bob owns a property")
  public void bobOwnsAProperty() {
    final String token = scenarioContext.get(ScenarioContext.BOB_TOKEN, String.class);
    final Map<String, Object> body = RequestFactory.validPropertyCreate();
    body.put("has_gas_supply", true);
    final Response response = userPropertyApiClient.createProperty(token, body);
    assertThat(response.statusCode()).isIn(200, 201);
    scenarioContext.put(ScenarioContext.BOB_PROPERTY_ID,
        UUID.fromString(response.path("data.id")));
  }

  // ═══════════════════════════════════════════════════════
  // PRICE CALCULATION — WHEN steps
  // ═══════════════════════════════════════════════════════

  @When("I calculate the price for {string} with urgency {string}")
  public void iCalculatePriceForWithUrgency(final String certType, final String urgency) {
    final UUID propertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(token, propertyId, certType, urgency));
  }

  @When("I calculate the price for property {string} with {string} and {string}")
  public void iCalculatePriceForPropertyWithCertAndUrgency(
      final String propertyId, final String certType, final String urgency) {
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(
        token, UUID.fromString(propertyId), certType, urgency));
  }

  @When("I calculate the price with certificate_type {string}")
  public void iCalculatePriceWithCertificateType(final String certType) {
    final UUID propertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(token, propertyId, certType, "STANDARD"));
  }

  @When("I calculate the price with urgency {string}")
  public void iCalculatePriceWithUrgency(final String urgency) {
    final UUID propertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(token, propertyId, "GAS_SAFETY", urgency));
  }

  @When("I call GET \\/api\\/v1\\/pricing\\/calculate without the certificate_type parameter")
  public void calculateWithoutCertType() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePriceMissingParam(
        token, "?property_id=" + propertyId + "&urgency=STANDARD"));
  }

  @When("I call GET \\/api\\/v1\\/pricing\\/calculate without the urgency parameter")
  public void calculateWithoutUrgency() {
    final UUID propertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePriceMissingParam(
        token, "?property_id=" + propertyId + "&certificate_type=GAS_SAFETY"));
  }

  @When("I call GET \\/api\\/v1\\/pricing\\/calculate without the property_id parameter")
  public void calculateWithoutPropertyId() {
    final String token = scenarioContext.contains(ScenarioContext.SARAH_TOKEN)
        ? scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class)
        : null;
    storeLastResponse(pricingApiClient.calculatePriceMissingParam(
        token, "?certificate_type=GAS_SAFETY&urgency=STANDARD"));
  }

  @When("I call GET \\/api\\/v1\\/pricing\\/calculate without authentication")
  public void calculateWithoutAuthentication() {
    storeLastResponse(pricingApiClient.calculatePriceNoAuth(
        UUID.randomUUID(), "GAS_SAFETY", "STANDARD"));
  }

  @When("Sarah tries to calculate the price for Bob's property")
  public void sarahTriesCalculateForBobProperty() {
    final UUID bobPropertyId = scenarioContext.get(ScenarioContext.BOB_PROPERTY_ID, UUID.class);
    final String token = scenarioContext.get(ScenarioContext.SARAH_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(token, bobPropertyId, "GAS_SAFETY", "STANDARD"));
  }

  @When("Mike tries to calculate the price for Sarah's property")
  public void mikeTriesCalculateForSarahProperty() {
    final UUID sarahPropertyId = scenarioContext.get(ScenarioContext.SARAH_PROPERTY_ID, UUID.class);
    final String mikeToken = scenarioContext.get(ScenarioContext.MIKE_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(mikeToken, sarahPropertyId, "GAS_SAFETY", "STANDARD"));
  }

  @When("the admin calculates the price for Bob's property")
  public void adminCalculatesForBobProperty() {
    final UUID bobPropertyId = scenarioContext.get(ScenarioContext.BOB_PROPERTY_ID, UUID.class);
    final String adminToken = scenarioContext.get(ScenarioContext.ADMIN_TOKEN, String.class);
    storeLastResponse(pricingApiClient.calculatePrice(adminToken, bobPropertyId, "GAS_SAFETY", "STANDARD"));
  }

  @When("that user calls {string} {string}")
  public void thatUserCallsMethodEndpoint(final String method, final String endpoint) {
    final String token = scenarioContext.get(ScenarioContext.ROLE_USER_TOKEN, String.class);
    storeLastResponse(pricingApiClient.call(method, endpoint, token));
  }

  // ═══════════════════════════════════════════════════════
  // ADMIN API CALLS — pricing rules
  // ═══════════════════════════════════════════════════════

  @When("the admin calls GET \\/api\\/v1\\/admin\\/pricing\\/rules")
  public void adminListsRules() {
    storeLastResponse(pricingApiClient.listRules(adminToken(), null));
  }

  @When("the admin calls GET \\/api\\/v1\\/admin\\/pricing\\/rules with active_only {word}")
  public void adminListsRulesWithActiveOnly(final String activeOnly) {
    storeLastResponse(pricingApiClient.listRules(adminToken(), Boolean.parseBoolean(activeOnly)));
  }

  @When("the admin calls GET \\/api\\/v1\\/admin\\/pricing\\/urgency-multipliers")
  public void adminListsMultipliers() {
    storeLastResponse(pricingApiClient.listMultipliers(adminToken()));
  }

  @When("the admin creates a pricing rule:")
  public void adminCreatesPricingRule(final DataTable dataTable) {
    final Map<String, String> row = dataTable.asMaps().get(0);
    final Map<String, Object> body = new HashMap<>();
    body.put("certificate_type", row.get("certificate_type"));
    final String region = row.get("region");
    if (region != null && !region.isBlank()) {
      body.put("region", region);
    }
    body.put("base_price_pence", Integer.parseInt(row.get("base_price_pence")));
    body.put("effective_from", row.get("effective_from"));
    final Response response = pricingApiClient.createRule(adminToken(), body);
    storeLastResponse(response);
    if (response.statusCode() == 201) {
      final String ruleId = response.path("data.id");
      if (ruleId != null) {
        scenarioContext.put(ScenarioContext.CURRENT_RULE_ID, UUID.fromString(ruleId));
      }
    }
  }

  @When("the admin creates a pricing rule with base_price_pence {int}")
  public void adminCreatesPricingRuleWithBasePricePence(final int basePricePence) {
    final Map<String, Object> body = new HashMap<>();
    body.put("certificate_type", "GAS_SAFETY");
    body.put("base_price_pence", basePricePence);
    body.put("effective_from", LocalDate.now().plusDays(10).toString());
    storeLastResponse(pricingApiClient.createRule(adminToken(), body));
  }

  @When("the admin creates a pricing rule with effective_from {string}")
  public void adminCreatesPricingRuleWithEffectiveFrom(final String effectiveFrom) {
    final Map<String, Object> body = new HashMap<>();
    body.put("certificate_type", "GAS_SAFETY");
    body.put("base_price_pence", 8000);
    body.put("effective_from", effectiveFrom);
    storeLastResponse(pricingApiClient.createRule(adminToken(), body));
  }

  @When("the admin creates another GAS_SAFETY rule for LONDON effective 2030-06-01")
  public void adminCreatesAnotherGasSafetyRuleForLondon() {
    final Map<String, Object> body = new HashMap<>();
    body.put("certificate_type", "GAS_SAFETY");
    body.put("region", "LONDON");
    body.put("base_price_pence", 9000);
    body.put("effective_from", "2030-06-01");
    storeLastResponse(pricingApiClient.createRule(adminToken(), body));
  }

  @When("the admin updates the GAS_SAFETY national rule with base_price_pence {int}")
  public void adminUpdatesGasSafetyNationalRuleBasePricePence(final int basePricePence) {
    final UUID ruleId = PricingTestDataSeeder.GAS_SAFETY_RULE_ID;
    storeLastResponse(pricingApiClient.updateRule(adminToken(), ruleId,
        Map.of("base_price_pence", basePricePence)));
    scenarioContext.put(ScenarioContext.CURRENT_RULE_ID, ruleId);
  }

  @When("the admin updates the GAS_SAFETY rule with effective_to {string}")
  public void adminUpdatesGasSafetyRuleEffectiveTo(final String effectiveTo) {
    final UUID ruleId = PricingTestDataSeeder.GAS_SAFETY_RULE_ID;
    storeLastResponse(pricingApiClient.updateRule(adminToken(), ruleId,
        Map.of("effective_to", effectiveTo)));
    scenarioContext.put(ScenarioContext.CURRENT_RULE_ID, ruleId);
  }

  @When("the admin updates the GAS_SAFETY base price to {int}")
  public void adminUpdatesGasSafetyBasePriceTo(final int basePricePence) {
    adminUpdatesGasSafetyNationalRuleBasePricePence(basePricePence);
  }

  // ═══════════════════════════════════════════════════════
  // ADMIN API CALLS — modifiers
  // ═══════════════════════════════════════════════════════

  @When("the admin adds a modifier to the GAS_SAFETY rule:")
  public void adminAddsModifierToGasSafetyRule(final DataTable dataTable) {
    final Map<String, String> row = dataTable.asMaps().get(0);
    final Map<String, Object> body = new HashMap<>();
    body.put("modifier_type", row.get("modifier_type"));
    body.put("modifier_pence", Integer.parseInt(row.get("modifier_pence")));
    final String minStr = row.get("condition_min");
    if (minStr != null && !minStr.isBlank()) {
      body.put("condition_min", new BigDecimal(minStr));
    }
    final String maxStr = row.get("condition_max");
    if (maxStr != null && !maxStr.isBlank()) {
      body.put("condition_max", new BigDecimal(maxStr));
    }
    final Response response = pricingApiClient.addModifier(
        adminToken(), PricingTestDataSeeder.GAS_SAFETY_RULE_ID, body);
    storeLastResponse(response);
    if (response.statusCode() == 201) {
      // find the new modifier id from the returned rule's modifiers
      final List<Map<String, Object>> modifiers = response.path("data.modifiers");
      if (modifiers != null) {
        modifiers.stream()
            .filter(m -> row.get("modifier_type").equals(m.get("modifierType"))
                || row.get("modifier_type").equals(m.get("modifier_type")))
            .map(m -> m.get("id"))
            .filter(id -> id != null)
            .findFirst()
            .ifPresent(id -> scenarioContext.put(
                ScenarioContext.CURRENT_MODIFIER_ID, UUID.fromString(String.valueOf(id))));
      }
    }
  }

  @When("the admin adds a modifier with modifier_type {string}")
  public void adminAddsModifierWithModifierType(final String modifierType) {
    final Map<String, Object> body = Map.of("modifier_type", modifierType, "modifier_pence", 500);
    storeLastResponse(pricingApiClient.addModifier(
        adminToken(), PricingTestDataSeeder.GAS_SAFETY_RULE_ID, body));
  }

  @When("the admin adds a modifier with modifier_pence {int}")
  public void adminAddsModifierWithModifierPence(final int modifierPence) {
    final Map<String, Object> body = Map.of("modifier_type", "BEDROOMS", "modifier_pence", modifierPence);
    storeLastResponse(pricingApiClient.addModifier(
        adminToken(), PricingTestDataSeeder.GAS_SAFETY_RULE_ID, body));
  }

  @When("the admin adds a BEDROOMS modifier with condition_min={int} and condition_max={int}")
  public void adminAddsBedroomsModifierWithConditions(final int min, final int max) {
    final Map<String, Object> body = Map.of(
        "modifier_type", "BEDROOMS",
        "modifier_pence", 1000,
        "condition_min", new BigDecimal(min),
        "condition_max", new BigDecimal(max));
    storeLastResponse(pricingApiClient.addModifier(
        adminToken(), PricingTestDataSeeder.GAS_SAFETY_RULE_ID, body));
  }

  @When("the admin adds a BEDROOMS modifier for {int}-{int} bedrooms at {int} pence to GAS_SAFETY")
  public void adminAddsBedroomsModifierRange(final int min, final int max, final int pence) {
    final Map<String, Object> body = Map.of(
        "modifier_type", "BEDROOMS",
        "modifier_pence", pence,
        "condition_min", new BigDecimal(min),
        "condition_max", new BigDecimal(max));
    storeLastResponse(pricingApiClient.addModifier(
        adminToken(), PricingTestDataSeeder.GAS_SAFETY_RULE_ID, body));
  }

  @When("the admin deletes that modifier")
  public void adminDeletesThatModifier() {
    final UUID modifierId = scenarioContext.get(ScenarioContext.CURRENT_MODIFIER_ID, UUID.class);
    final UUID ruleId = PricingTestDataSeeder.GAS_SAFETY_RULE_ID;
    storeLastResponse(pricingApiClient.removeModifier(adminToken(), ruleId, modifierId));
  }

  @When("the admin removes the BEDROOMS {int}-{int} modifier from the GAS_SAFETY rule")
  public void adminRemovesBedroomsModifierFromGasSafetyRule(final int min, final int max) {
    deleteBedroomsModifierByRange(PricingTestDataSeeder.GAS_SAFETY_RULE_ID, min, max);
  }

  @When("the admin deletes modifier {string} from the GAS_SAFETY rule")
  public void adminDeletesModifierById(final String modifierId) {
    final Response response = pricingApiClient.removeModifier(
        adminToken(),
        PricingTestDataSeeder.GAS_SAFETY_RULE_ID,
        UUID.fromString(modifierId));
    storeLastResponse(response);
  }

  // ═══════════════════════════════════════════════════════
  // ADMIN API CALLS — urgency multipliers
  // ═══════════════════════════════════════════════════════

  @When("the admin updates the PRIORITY multiplier to {bigdecimal}")
  public void adminUpdatesPriorityMultiplier(final BigDecimal multiplier) {
    storeLastResponse(pricingApiClient.updateMultiplier(
        adminToken(),
        PricingTestDataSeeder.PRIORITY_MULTIPLIER_ID,
        Map.of("multiplier", multiplier)));
  }

  @When("the admin updates the EMERGENCY multiplier to {bigdecimal}")
  public void adminUpdatesEmergencyMultiplier(final BigDecimal multiplier) {
    storeLastResponse(pricingApiClient.updateMultiplier(
        adminToken(),
        PricingTestDataSeeder.EMERGENCY_MULTIPLIER_ID,
        Map.of("multiplier", multiplier)));
  }

  // ═══════════════════════════════════════════════════════
  // DATA SETUP — pricing rules and seed state
  // ═══════════════════════════════════════════════════════

  @Given("the EPC pricing rule is deactivated")
  public void theEpcPricingRuleIsDeactivated() {
    databaseUtils.deactivatePricingRule(PricingTestDataSeeder.EPC_RULE_ID.toString());
  }

  @Given("all pricing rules for {string} are deactivated")
  public void allPricingRulesForTypeAreDeactivated(final String certType) {
    databaseUtils.deactivatePricingRulesByType(certType);
  }

  @Given("the PRIORITY urgency multiplier is deactivated")
  public void thePriorityUrgencyMultiplierIsDeactivated() {
    databaseUtils.deactivateUrgencyMultiplier("PRIORITY");
  }

  @Given("the only GAS_SAFETY rule has effective_from of tomorrow")
  public void theGasSafetyRuleHasEffectiveFromTomorrow() {
    final String tomorrow = LocalDate.now().plusDays(1).toString();
    databaseUtils.updatePricingRuleEffectiveFrom(
        PricingTestDataSeeder.GAS_SAFETY_RULE_ID.toString(), tomorrow);
  }

  @Given("the only GAS_SAFETY rule has effective_to of yesterday")
  public void theGasSafetyRuleHasEffectiveToYesterday() {
    final String yesterday = LocalDate.now().minusDays(1).toString();
    databaseUtils.updatePricingRuleEffectiveTo(
        PricingTestDataSeeder.GAS_SAFETY_RULE_ID.toString(), yesterday);
  }

  @Given("a GAS_SAFETY rule for region LONDON already exists effective 2030-06-01 with no end date")
  public void aGasSafetyLondonRuleAlreadyExists() {
    final Map<String, Object> body = new HashMap<>();
    body.put("certificate_type", "GAS_SAFETY");
    body.put("region", "LONDON");
    body.put("base_price_pence", 9000);
    body.put("effective_from", "2030-06-01");
    pricingApiClient.createRule(adminToken(), body);
  }

  @Given("the GAS_SAFETY rule has a BEDROOMS modifier for 3-4 bedrooms")
  public void theGasSafetyRuleHasBedroomsModifier() {
    // It's already seeded — find the modifier id and store it
    findAndStoreBedroomsModifier34();
  }

  @Given("the GAS_SAFETY rule already has a BEDROOMS modifier for condition_min=3 condition_max=4")
  public void gasSafetyRuleAlreadyHasBedroomsModifier() {
    findAndStoreBedroomsModifier34();
  }

  // ═══════════════════════════════════════════════════════
  // THEN — response status / error
  // ═══════════════════════════════════════════════════════

  @Then("the error code is {string}")
  public void theErrorCodeIs(final String expectedCode) {
    final String actual = lastResponse().path("error");
    assertThat(actual).as("Expected error code '%s' but got '%s'. Body: %s",
        expectedCode, actual, lastResponse().getBody().asString()).isEqualTo(expectedCode);
  }

  // ═══════════════════════════════════════════════════════
  // THEN — price breakdown assertions
  // ═══════════════════════════════════════════════════════

  @Then("the price breakdown is:")
  public void thePriceBreakdownIs(final DataTable dataTable) {
    final Response response = lastResponse();
    final Map<String, String> expected = dataTable.asMap(String.class, String.class);
    for (final Map.Entry<String, String> entry : expected.entrySet()) {
      assertBreakdownField(response, entry.getKey(), entry.getValue());
    }
  }

  @Then("the price breakdown has property_modifier_pence of {int}")
  public void thePriceBreakdownHasPropertyModifierPence(final int expected) {
    final int actual = lastResponse().path("data.property_modifier_pence");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("no modifiers are listed in the breakdown")
  public void noModifiersAreListedInTheBreakdown() {
    final List<?> modifiers = lastResponse().path("data.breakdown.modifiers_applied");
    assertThat(modifiers).as("Expected no modifiers").isNullOrEmpty();
  }

  @Then("the breakdown includes a modifier {string} of {int} pence")
  public void theBreakdownIncludesModifier(final String modifierType, final int amountPence) {
    final List<Map<String, Object>> modifiers = lastResponse().path("data.breakdown.modifiers_applied");
    assertThat(modifiers)
        .as("Expected modifier '%s' of %d pence", modifierType, amountPence)
        .isNotNull()
        .anySatisfy(m -> {
          assertThat(String.valueOf(m.get("type"))).isEqualTo(modifierType);
          assertThat(((Number) m.get("amount_pence")).intValue()).isEqualTo(amountPence);
        });
  }

  @Then("the breakdown does not include a modifier containing {string}")
  public void theBreakdownDoesNotIncludeModifierContaining(final String partialType) {
    final List<Map<String, Object>> modifiers = lastResponse().path("data.breakdown.modifiers_applied");
    if (modifiers == null || modifiers.isEmpty()) {
      return;
    }
    assertThat(modifiers)
        .as("Expected no modifier containing '%s'", partialType)
        .noneMatch(m -> String.valueOf(m.get("type")).contains(partialType));
  }

  @Then("the total_price_pence is {int}")
  public void theTotalPricePenceIs(final int expected) {
    final int actual = lastResponse().path("data.total_price_pence");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the urgency_modifier_pence is {int}")
  public void theUrgencyModifierPenceIs(final int expected) {
    final int actual = lastResponse().path("data.urgency_modifier_pence");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the base_price_pence is {int}")
  public void theBasePricePenceIs(final int expected) {
    final int actual = lastResponse().path("data.base_price_pence");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("commission_pence plus engineer_payout_pence equals total_price_pence")
  public void commissionPlusPayout() {
    final int commission = lastResponse().path("data.commission_pence");
    final int payout = lastResponse().path("data.engineer_payout_pence");
    final int total = lastResponse().path("data.total_price_pence");
    assertThat(commission + payout).isEqualTo(total);
  }

  // ═══════════════════════════════════════════════════════
  // THEN — admin pricing rules
  // ═══════════════════════════════════════════════════════

  @Then("the response contains {int} pricing rules")
  public void theResponseContainsNPricingRules(final int expected) {
    final List<?> rules = lastResponse().path("data");
    assertThat(rules).as("Expected %d pricing rules", expected).hasSize(expected);
  }

  @Then("the {string} rule has base_price_pence of {int}")
  public void theRuleHasBasePricePence(final String certType, final int expected) {
    final List<Map<String, Object>> rules = lastResponse().path("data");
    assertThat(rules).anySatisfy(r -> {
      assertThat(String.valueOf(r.get("certificate_type"))).isEqualTo(certType);
      assertThat(((Number) r.get("base_price_pence")).intValue()).isEqualTo(expected);
    });
  }

  @Then("the {string} rule has at least {int} modifiers")
  public void theRuleHasAtLeastNModifiers(final String certType, final int minCount) {
    final List<Map<String, Object>> rules = lastResponse().path("data");
    final Optional<Map<String, Object>> rule = rules.stream()
        .filter(r -> certType.equals(String.valueOf(r.get("certificate_type"))))
        .findFirst();
    assertThat(rule).isPresent();
    final List<?> modifiers = (List<?>) rule.get().get("modifiers");
    assertThat(modifiers).hasSizeGreaterThanOrEqualTo(minCount);
  }

  @Then("the EPC rule has is_active of false")
  public void theEpcRuleHasIsActiveFalse() {
    final List<Map<String, Object>> rules = lastResponse().path("data");
    final Optional<Map<String, Object>> rule = rules.stream()
        .filter(r -> "EPC".equals(String.valueOf(r.get("certificate_type"))))
        .findFirst();
    assertThat(rule).isPresent();
    assertThat(rule.get().get("is_active")).isEqualTo(false);
  }

  @Then("the rule has base_price_pence of {int} and no modifiers")
  public void theRuleHasBasePricePenceAndNoModifiers(final int expected) {
    final int actual = lastResponse().path("data.base_price_pence");
    assertThat(actual).isEqualTo(expected);
    final List<?> modifiers = lastResponse().path("data.modifiers");
    assertThat(modifiers).isNullOrEmpty();
  }

  @Then("the rule has region {string} and base_price_pence {int}")
  public void theRuleHasRegionAndBasePricePence(final String region, final int basePricePence) {
    assertThat((String) lastResponse().path("data.region")).isEqualTo(region);
    final int actual = lastResponse().path("data.base_price_pence");
    assertThat(actual).isEqualTo(basePricePence);
  }

  @Then("the rule now has base_price_pence of {int}")
  public void theRuleNowHasBasePricePenceOf(final int expected) {
    final int actual = lastResponse().path("data.base_price_pence");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the rule now has effective_to of {string}")
  public void theRuleNowHasEffectiveTo(final String expected) {
    final String actual = lastResponse().path("data.effective_to");
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the GAS_SAFETY rule now has a BEDROOMS modifier for 7+ bedrooms of {int} pence")
  public void gasSafetyRuleHasBedroomsModifierFor7Plus(final int pence) {
    final List<Map<String, Object>> modifiers = lastResponse().path("data.modifiers");
    assertThat(modifiers).anySatisfy(m -> {
      assertThat(String.valueOf(m.get("modifier_type"))).isEqualTo("BEDROOMS");
      assertThat(((Number) m.get("modifier_pence")).intValue()).isEqualTo(pence);
    });
  }

  @Then("the GAS_SAFETY rule no longer contains a BEDROOMS modifier for 3-4 bedrooms")
  public void gasSafetyRuleNoLongerContains34Modifier() {
    // Re-fetch the rule list to verify
    final Response rulesResponse = pricingApiClient.listRules(adminToken(), null);
    final List<Map<String, Object>> rules = rulesResponse.path("data");
    final Optional<Map<String, Object>> gasSafetyRule = rules.stream()
        .filter(r -> "GAS_SAFETY".equals(String.valueOf(r.get("certificate_type"))))
        .findFirst();
    assertThat(gasSafetyRule).isPresent();
    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> modifiers = (List<Map<String, Object>>) gasSafetyRule.get().get("modifiers");
    if (modifiers != null) {
      assertThat(modifiers).noneMatch(m -> {
        final Object condMin = m.get("condition_min");
        final Object condMax = m.get("condition_max");
        return "BEDROOMS".equals(String.valueOf(m.get("modifier_type")))
            && condMin != null && new BigDecimal(condMin.toString()).compareTo(new BigDecimal("3")) == 0
            && condMax != null && new BigDecimal(condMax.toString()).compareTo(new BigDecimal("4")) == 0;
      });
    }
  }

  // ═══════════════════════════════════════════════════════
  // THEN — urgency multipliers
  // ═══════════════════════════════════════════════════════

  @Then("the response contains 3 multipliers:")
  public void theResponseContains3Multipliers(final DataTable dataTable) {
    final List<Map<String, Object>> multipliers = lastResponse().path("data");
    assertThat(multipliers).hasSize(3);
    for (final Map<String, String> expectedRow : dataTable.asMaps()) {
      final String urgency = expectedRow.get("urgency");
      final BigDecimal multiplier = new BigDecimal(expectedRow.get("multiplier"));
      final boolean isActive = Boolean.parseBoolean(expectedRow.get("is_active"));
      assertThat(multipliers).anySatisfy(m -> {
        assertThat(String.valueOf(m.get("urgency"))).isEqualTo(urgency);
        assertThat(new BigDecimal(m.get("multiplier").toString()).compareTo(multiplier)).isZero();
        assertThat(m.get("is_active")).isEqualTo(isActive);
      });
    }
  }

  @Then("the multiplier for PRIORITY is now {bigdecimal}")
  public void theMultiplierForPriorityIsNow(final BigDecimal expected) {
    final Object raw = lastResponse().path("data.multiplier");
    final BigDecimal actual = new BigDecimal(raw.toString());
    assertThat(actual.compareTo(expected)).isZero();
  }

  @Then("the multiplier for EMERGENCY is now {bigdecimal}")
  public void theMultiplierForEmergencyIsNow(final BigDecimal expected) {
    final Object raw = lastResponse().path("data.multiplier");
    final BigDecimal actual = new BigDecimal(raw.toString());
    assertThat(actual.compareTo(expected)).isZero();
  }

  // ═══════════════════════════════════════════════════════
  // PRIVATE HELPERS
  // ═══════════════════════════════════════════════════════

  private String registerVerifyAndLogin(final String email, final String role) {
    final Map<String, Object> request = "ENGINEER".equalsIgnoreCase(role)
        ? RequestFactory.validEngineerRegistration()
        : RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.put("role", role.toUpperCase());
    request.put("phone", "+447900" + String.format("%06d", Math.abs(email.hashCode() % 1_000_000)));

    final Response registerResponse = authApiClient.register(request);
    assertThat(registerResponse.statusCode()).as(
        "Registration failed for %s. Body: %s", email, registerResponse.getBody().asString()).isEqualTo(201);

    // Wait for verification email and verify
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> wireMockUtils.extractVerificationToken(email).isPresent());

    final String verificationCode = wireMockUtils.extractVerificationToken(email).orElseThrow();
    final Response verifyResponse = authApiClient.verifyEmail(Map.of("code", verificationCode));
    assertThat(verifyResponse.statusCode()).as(
        "Email verification failed for %s. Body: %s", email, verifyResponse.getBody().asString()).isEqualTo(200);

    // Login
    final Response loginResponse = authApiClient.login(
        RequestFactory.validLogin(email, "Password1!"));
    assertThat(loginResponse.statusCode()).as(
        "Login failed for %s. Body: %s", email, loginResponse.getBody().asString()).isEqualTo(200);

    return loginResponse.path("data.access_token");
  }

  private void ensureAdminExists(final String email) {
    // Register as CUSTOMER, promote to ADMIN via DB, then login
    final Map<String, Object> request = RequestFactory.validCustomerRegistration();
    request.put("email", email);
    request.put("phone", "+447000" + String.format("%06d", Math.abs(email.hashCode() % 1_000_000)));
    request.put("role", "CUSTOMER");

    final Response registerResponse = authApiClient.register(request);
    assertThat(registerResponse.statusCode()).as(
        "Admin registration failed. Body: %s", registerResponse.getBody().asString()).isEqualTo(201);

    // Promote to ADMIN and set email_verified + status directly
    databaseUtils.findUserByEmail(email).ifPresent(u -> {
      final String userId = String.valueOf(u.get("id"));
      // Use JdbcTemplate via DatabaseUtils to promote
      promoteToAdmin(userId);
    });

    // Login to get admin token
    final Response loginResponse = authApiClient.login(
        RequestFactory.validLogin(email, "Password1!"));
    assertThat(loginResponse.statusCode()).as(
        "Admin login failed. Body: %s", loginResponse.getBody().asString()).isEqualTo(200);

    final String adminToken = loginResponse.path("data.access_token");
    final String userId = loginResponse.path("data.user.id");
    scenarioContext.put(ScenarioContext.ADMIN_TOKEN, adminToken);
    scenarioContext.put(ScenarioContext.ADMIN_USER_ID, userId);
  }

  private void promoteToAdmin(final String userId) {
    databaseUtils.setUserStatus(userId, "ACTIVE");
    // We need to update role too — add a helper in DatabaseUtils
    // Using direct SQL via the insertAdminUser workaround: update the row
    databaseUtils.promoteUserToAdmin(userId);
  }

  private void ensureSarah() {
    if (!scenarioContext.contains(ScenarioContext.SARAH_TOKEN)) {
      aRegisteredCustomerExistsWithValidToken("sarah@test.com");
    }
  }

  private String adminToken() {
    return scenarioContext.get(ScenarioContext.ADMIN_TOKEN, String.class);
  }

  private void storeLastResponse(final Response response) {
    scenarioContext.put(ScenarioContext.LAST_RESPONSE, response);
  }

  private Response lastResponse() {
    return scenarioContext.get(ScenarioContext.LAST_RESPONSE, Response.class);
  }

  private boolean isSarahEmail(final String email) {
    return "sarah@test.com".equals(email);
  }

  private boolean isBobEmail(final String email) {
    return "bob@test.com".equals(email);
  }

  private Map<String, Object> buildPropertyBody(final Map<String, String> row) {
    final Map<String, Object> body = RequestFactory.validPropertyCreate();

    final String propertyType = row.get("property_type");
    if (propertyType != null && !propertyType.isBlank()) {
      body.put("property_type", propertyType);
    }

    final String bedrooms = row.get("bedrooms");
    if (bedrooms != null && !"null".equalsIgnoreCase(bedrooms)) {
      body.put("bedrooms", Integer.parseInt(bedrooms));
    } else {
      body.remove("bedrooms");
    }

    final String gasAppliances = row.get("gas_appliance_count");
    if (gasAppliances != null && !"null".equalsIgnoreCase(gasAppliances)) {
      body.put("gas_appliance_count", Integer.parseInt(gasAppliances));
    } else {
      body.put("gas_appliance_count", null);
    }

    final String floorArea = row.get("floor_area_sqm");
    if (floorArea != null && !"null".equalsIgnoreCase(floorArea)) {
      body.put("floor_area_sqm", floorArea);
    } else {
      body.put("floor_area_sqm", null);
    }

    final String hasGasSupply = row.get("has_gas_supply");
    if (hasGasSupply != null) {
      body.put("has_gas_supply", Boolean.parseBoolean(hasGasSupply));
    }

    return body;
  }

  private void assertBreakdownField(
      final Response response, final String field, final String expectedValue) {
    final String jsonPath = "data." + field;
    final Object actual = response.path(jsonPath);
    assertThat(actual)
        .as("Field '%s' expected '%s' but was '%s'. Body: %s",
            field, expectedValue, actual, response.getBody().asString())
        .isNotNull();

    if ("commission_rate".equals(field)) {
      assertThat(new BigDecimal(actual.toString()).compareTo(new BigDecimal(expectedValue))).isZero();
    } else {
      assertThat(((Number) actual).intValue()).isEqualTo(Integer.parseInt(expectedValue));
    }
  }

  private void findAndStoreBedroomsModifier34() {
    final List<Map<String, Object>> modifiers = databaseUtils.findModifiersForRule(
        PricingTestDataSeeder.GAS_SAFETY_RULE_ID.toString());
    modifiers.stream()
        .filter(m -> "BEDROOMS".equals(String.valueOf(m.get("modifier_type"))))
        .filter(m -> {
          final Object min = m.get("condition_min");
          final Object max = m.get("condition_max");
          return min != null
              && new BigDecimal(min.toString()).compareTo(new BigDecimal("3")) == 0
              && max != null
              && new BigDecimal(max.toString()).compareTo(new BigDecimal("4")) == 0;
        })
        .findFirst()
        .ifPresent(m -> scenarioContext.put(
            ScenarioContext.CURRENT_MODIFIER_ID,
            UUID.fromString(String.valueOf(m.get("id")))));
  }

  private void deleteBedroomsModifierByRange(final UUID ruleId, final int min, final int max) {
    final List<Map<String, Object>> modifiers = databaseUtils.findModifiersForRule(ruleId.toString());
    modifiers.stream()
        .filter(m -> "BEDROOMS".equals(String.valueOf(m.get("modifier_type"))))
        .filter(m -> {
          final Object condMin = m.get("condition_min");
          final Object condMax = m.get("condition_max");
          return condMin != null
              && new BigDecimal(condMin.toString()).compareTo(new BigDecimal(min)) == 0
              && condMax != null
              && new BigDecimal(condMax.toString()).compareTo(new BigDecimal(max)) == 0;
        })
        .findFirst()
        .ifPresent(m -> {
          final UUID modifierId = UUID.fromString(String.valueOf(m.get("id")));
          storeLastResponse(pricingApiClient.removeModifier(adminToken(), ruleId, modifierId));
        });
  }

}

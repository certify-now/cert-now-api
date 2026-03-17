package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Integration tests for pricing rules and quote calculation endpoints. */
class PricingIntegrationTest extends BaseIntegrationTest {

  @Test
  void seedPricingRules_thenGetQuote_returnsCalculatedPrice() {
    final TokenPair adminTokens = registerAndLogin("admin-pricing@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("cust-pricing@example.com", "CUSTOMER");

    seedPricingRules(adminTokens.accessToken());

    final UUID propertyId = createProperty(customerTokens.accessToken());

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certificateType": "GAS_SAFETY"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/pricing/quote")
        .then()
        .statusCode(200)
        .body("data.totalPence", notNullValue())
        .body("data.baseRatePence", notNullValue());
  }

  @Test
  void createPricingRule_admin_returnsCreated() {
    final TokenPair adminTokens = registerAndLogin("admin-rule@example.com", "ADMIN");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .body(
            """
            {
              "certificateType": "EPC",
              "region": "NATIONAL",
              "baseRatePence": 8000,
              "commissionPct": 0.10,
              "effectiveFrom": "2026-01-01"
            }
            """)
        .post("/api/v1/admin/pricing/rules")
        .then()
        .statusCode(201);
  }

  @Test
  void createPricingRule_nonAdmin_returns403() {
    final TokenPair customerTokens = registerAndLogin("cust-rule@example.com", "CUSTOMER");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .body(
            """
            {
              "certificateType": "EPC",
              "region": "NATIONAL",
              "baseRatePence": 8000,
              "commissionPct": 0.10,
              "effectiveFrom": "2026-01-01"
            }
            """)
        .post("/api/v1/admin/pricing/rules")
        .then()
        .statusCode(403);
  }

  @Test
  void createPricingRule_then_listRules_returnsList() {
    final TokenPair adminTokens = registerAndLogin("admin-list-rules@example.com", "ADMIN");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .body(
            """
            {
              "certificateType": "EPC",
              "region": "NATIONAL",
              "baseRatePence": 8000,
              "commissionPct": 0.10,
              "effectiveFrom": "2026-01-01"
            }
            """)
        .post("/api/v1/admin/pricing/rules")
        .then()
        .statusCode(201);

    given()
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .get("/api/v1/admin/pricing/rules")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }

  @Test
  void getQuote_noPricingRules_returns422() {
    final TokenPair customerTokens = registerAndLogin("cust-no-rules@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(customerTokens.accessToken());

    // No pricing rules seeded — should return an error
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certificateType": "GAS_SAFETY"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/pricing/quote")
        .then()
        .statusCode(422); // Unprocessable — no pricing rule found
  }
}

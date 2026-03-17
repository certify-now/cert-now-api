package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Integration tests for property lifecycle and compliance API endpoints. */
class PropertyComplianceIntegrationTest extends BaseIntegrationTest {

  @Test
  void createProperty_happyPath_returnsCreatedWithId() {
    final TokenPair customerTokens = registerAndLogin("prop-owner@example.com", "CUSTOMER");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .body(
            """
            {
              "addressLine1": "10 Compliance Street",
              "city": "London",
              "postcode": "SW1A 1AA",
              "country": "GB",
              "propertyType": "FLAT",
              "hasGasSupply": true,
              "hasElectric": true
            }
            """)
        .post("/api/v1/properties")
        .then()
        .statusCode(201)
        .body("data.id", notNullValue());
  }

  @Test
  void getProperty_byOwner_returnsPropertyWithCompliance() {
    final TokenPair customerTokens = registerAndLogin("prop-get@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(customerTokens.accessToken());

    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .get("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(200)
        .body("data.id", equalTo(propertyId.toString()))
        .body("data.compliance", notNullValue());
  }

  @Test
  void getProperty_byOtherCustomer_returns403() {
    final TokenPair owner = registerAndLogin("prop-own@example.com", "CUSTOMER");
    final TokenPair other = registerAndLogin("prop-other@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(owner.accessToken());

    given()
        .header("Authorization", "Bearer " + other.accessToken())
        .get("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(403);
  }

  @Test
  void softDeleteProperty_noActiveJobs_returns204() {
    final TokenPair customerTokens = registerAndLogin("del-prop@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(customerTokens.accessToken());

    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .delete("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(204);
  }

  @Test
  void softDeleteProperty_notOwner_returns403() {
    final TokenPair owner = registerAndLogin("own-del@example.com", "CUSTOMER");
    final TokenPair other = registerAndLogin("other-del@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(owner.accessToken());

    given()
        .header("Authorization", "Bearer " + other.accessToken())
        .delete("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(403);
  }

  @Test
  void restoreProperty_afterSoftDelete_returns200() {
    final TokenPair adminTokens = registerAndLogin("admin-restore-prop@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("cust-restore-prop@example.com", "CUSTOMER");
    final UUID propertyId = createProperty(customerTokens.accessToken());

    // Soft-delete first
    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .delete("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(204);

    // Admin restores
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .post("/api/v1/admin/properties/" + propertyId + "/restore")
        .then()
        .statusCode(200);
  }

  @Test
  void getPropertyHealth_returnsComplianceAggregation() {
    final TokenPair customerTokens = registerAndLogin("health-prop@example.com", "CUSTOMER");
    createProperty(customerTokens.accessToken());

    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .get("/api/v1/properties/health")
        .then()
        .statusCode(200)
        .body("data", notNullValue());
  }
}

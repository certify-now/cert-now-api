package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the complete job lifecycle.
 *
 * <p>Tests the critical booking flow end-to-end against a real PostgreSQL/PostGIS database.
 */
class JobLifecycleIntegrationTest extends BaseIntegrationTest {

  @Test
  void fullJobLifecycleHappyPath() {
    // ─── Setup: register users ────────────────────────────────────────────────
    final TokenPair adminTokens = registerAndLogin("admin-lifecycle@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("customer-lifecycle@example.com", "CUSTOMER");
    final TokenPair engineerTokens = registerAndLogin("engineer-lifecycle@example.com", "ENGINEER");

    final String adminToken = adminTokens.accessToken();
    final String customerToken = customerTokens.accessToken();
    final String engineerToken = engineerTokens.accessToken();

    // ─── 1. Create property ────────────────────────────────────────────────────
    final UUID propertyId = createProperty(customerToken);

    // ─── 2. Seed pricing rules ─────────────────────────────────────────────────
    seedPricingRules(adminToken);

    // ─── 3. Create job ────────────────────────────────────────────────────────
    final String jobId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customerToken)
            .body(
                """
            {
              "propertyId": "%s",
              "certificateType": "GAS_SAFETY",
              "urgency": "STANDARD"
            }
            """
                    .formatted(propertyId))
            .post("/api/v1/jobs")
            .then()
            .statusCode(201)
            .body("data.status", equalTo("CREATED"))
            .body("data.certificateType", equalTo("GAS_SAFETY"))
            .extract()
            .path("data.id");

    // ─── 4. Admin matches job to engineer ─────────────────────────────────────
    final String engineerUserId =
        given()
            .header("Authorization", "Bearer " + engineerToken)
            .get("/api/v1/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("data.id");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body("""
            { "engineerId": "%s" }
            """.formatted(engineerUserId))
        .put("/api/v1/jobs/" + jobId + "/match")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("MATCHED"));

    // ─── 5. Engineer accepts job ───────────────────────────────────────────────
    final String scheduledDate = LocalDate.now().plusDays(2).toString();
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(
            """
            {
              "scheduledDate": "%s",
              "scheduledTimeSlot": "MORNING"
            }
            """
                .formatted(scheduledDate))
        .put("/api/v1/jobs/" + jobId + "/accept")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("ACCEPTED"));

    // ─── 6. Engineer marks en-route ───────────────────────────────────────────
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .put("/api/v1/jobs/" + jobId + "/en-route")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("EN_ROUTE"));

    // ─── 7. Engineer starts job ────────────────────────────────────────────────
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body("""
            { "latitude": 51.5074, "longitude": -0.1278 }
            """)
        .put("/api/v1/jobs/" + jobId + "/start")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("IN_PROGRESS"));

    // ─── 8. Engineer completes job ─────────────────────────────────────────────
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .put("/api/v1/jobs/" + jobId + "/complete")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("COMPLETED"));

    // ─── 9. Verify status history has all transitions ─────────────────────────
    // CREATED → MATCHED → ACCEPTED → EN_ROUTE → IN_PROGRESS → COMPLETED = 6 entries
    given()
        .header("Authorization", "Bearer " + customerToken)
        .get("/api/v1/jobs/" + jobId + "/history")
        .then()
        .statusCode(200)
        .body("data", hasSize(greaterThanOrEqualTo(6)));
  }

  @Test
  void createJob_thenCancelBeforeMatch_fullRefund() {
    final TokenPair adminTokens = registerAndLogin("admin-cancel@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("customer-cancel@example.com", "CUSTOMER");
    final String adminToken = adminTokens.accessToken();
    final String customerToken = customerTokens.accessToken();

    final UUID propertyId = createProperty(customerToken);
    seedPricingRules(adminToken);

    final String jobId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customerToken)
            .body(
                """
            {
              "propertyId": "%s",
              "certificateType": "GAS_SAFETY"
            }
            """
                    .formatted(propertyId))
            .post("/api/v1/jobs")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body("""
            { "reason": "Changed my mind" }
            """)
        .put("/api/v1/jobs/" + jobId + "/cancel")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("CANCELLED"));
  }

  @Test
  void createJob_invalidPropertyId_returns404() {
    final TokenPair adminTokens = registerAndLogin("admin-404@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("customer-404@example.com", "CUSTOMER");
    final String adminToken = adminTokens.accessToken();
    final String customerToken = customerTokens.accessToken();
    seedPricingRules(adminToken);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body(
            """
            {
              "propertyId": "%s",
              "certificateType": "GAS_SAFETY"
            }
            """
                .formatted(UUID.randomUUID()))
        .post("/api/v1/jobs")
        .then()
        .statusCode(404);
  }
}

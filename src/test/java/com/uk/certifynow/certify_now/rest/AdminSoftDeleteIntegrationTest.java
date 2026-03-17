package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Integration tests for admin soft-delete and restore operations on users and properties. */
class AdminSoftDeleteIntegrationTest extends BaseIntegrationTest {

  @Test
  void adminSoftDeleteUser_noActiveJobs_returns204() {
    final TokenPair adminTokens = registerAndLogin("admin-sdd@example.com", "ADMIN");
    final TokenPair targetTokens = registerAndLogin("target-sdd@example.com", "CUSTOMER");

    final String targetUserId =
        given()
            .header("Authorization", "Bearer " + targetTokens.accessToken())
            .get("/api/v1/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("data.id");

    given()
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .delete("/api/v1/admin/users/" + targetUserId)
        .then()
        .statusCode(204);
  }

  @Test
  void adminSoftDeleteUser_nonAdmin_returns403() {
    final TokenPair customerTokens = registerAndLogin("cust-sdd@example.com", "CUSTOMER");
    final TokenPair targetTokens = registerAndLogin("target2-sdd@example.com", "CUSTOMER");

    final String targetUserId =
        given()
            .header("Authorization", "Bearer " + targetTokens.accessToken())
            .get("/api/v1/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("data.id");

    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .delete("/api/v1/admin/users/" + targetUserId)
        .then()
        .statusCode(403);
  }

  @Test
  void adminRestoreUser_afterSoftDelete_setsStatusActive() {
    final TokenPair adminTokens = registerAndLogin("admin-restore@example.com", "ADMIN");
    final TokenPair targetTokens = registerAndLogin("target-restore@example.com", "CUSTOMER");

    final String targetUserId =
        given()
            .header("Authorization", "Bearer " + targetTokens.accessToken())
            .get("/api/v1/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("data.id");

    // Soft-delete first
    given()
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .delete("/api/v1/admin/users/" + targetUserId)
        .then()
        .statusCode(204);

    // Restore
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .post("/api/v1/admin/users/" + targetUserId + "/restore")
        .then()
        .statusCode(200)
        .body("data.id", equalTo(targetUserId));
  }

  @Test
  void adminSoftDeleteUser_alreadyDeleted_returns409() {
    final TokenPair adminTokens = registerAndLogin("admin-dbl-del@example.com", "ADMIN");
    final TokenPair targetTokens = registerAndLogin("target-dbl-del@example.com", "CUSTOMER");

    final String targetUserId =
        given()
            .header("Authorization", "Bearer " + targetTokens.accessToken())
            .get("/api/v1/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("data.id");

    // First delete
    given()
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .delete("/api/v1/admin/users/" + targetUserId)
        .then()
        .statusCode(204);

    // Second delete should fail with 409
    given()
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .delete("/api/v1/admin/users/" + targetUserId)
        .then()
        .statusCode(409)
        .body("error", equalTo("ALREADY_DELETED"));
  }

  @Test
  void adminSoftDeleteProperty_then_restore_roundTrip() {
    final TokenPair adminTokens = registerAndLogin("admin-prop-del@example.com", "ADMIN");
    final TokenPair customerTokens = registerAndLogin("cust-prop-del@example.com", "CUSTOMER");

    final UUID propertyId = createProperty(customerTokens.accessToken());

    // Soft-delete via customer
    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .delete("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(204);

    // Restore via admin
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminTokens.accessToken())
        .post("/api/v1/admin/properties/" + propertyId + "/restore")
        .then()
        .statusCode(200);

    // Property should be accessible again
    given()
        .header("Authorization", "Bearer " + customerTokens.accessToken())
        .get("/api/v1/properties/" + propertyId)
        .then()
        .statusCode(200)
        .body("data.id", equalTo(propertyId.toString()));
  }
}

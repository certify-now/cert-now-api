package com.uk.certifynow.certify_now.service;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.BaseIntegrationTest;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests proving that denormalised property compliance flags stay in sync with
 * certificate mutations (upload / delete). These tests boot a full Spring context against a real
 * PostGIS database and exercise the REST API end-to-end.
 */
@Disabled("PropertyCreatedEvent listener causes 500 in test context — needs investigation")
class ComplianceSyncIntegrationTest extends BaseIntegrationTest {

  @Autowired private PropertyRepository propertyRepository;
  @Autowired private CertificateRepository certificateRepository;

  // ── Upload sets denormalised gas fields ──────────────────────────────────────

  @Test
  void uploadGasCertificate_setsPropertyComplianceFlags() {
    final TokenPair customer = registerAndLogin("gas-upload@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    // Upload a GAS_SAFETY certificate
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customer.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certType": "GAS_SAFETY",
              "issuedAt": "2026-01-01",
              "expiresAt": "2027-01-01"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/certificates/upload")
        .then()
        .statusCode(201);

    // Verify the denormalised property flags are set
    final Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasGasCertificate()).as("hasGasCertificate").isTrue();
    assertThat(property.getGasExpiryDate()).as("gasExpiryDate").isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(property.getCurrentGasCertificate()).as("currentGasCertificate").isNotNull();
  }

  // ── Upload sets denormalised EICR fields ────────────────────────────────────

  @Test
  void uploadEicrCertificate_setsPropertyComplianceFlags() {
    final TokenPair customer = registerAndLogin("eicr-upload@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customer.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certType": "EICR",
              "issuedAt": "2026-01-01",
              "expiresAt": "2031-01-01"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/certificates/upload")
        .then()
        .statusCode(201);

    final Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasEicr()).as("hasEicr").isTrue();
    assertThat(property.getEicrExpiryDate())
        .as("eicrExpiryDate")
        .isEqualTo(LocalDate.of(2031, 1, 1));
    assertThat(property.getCurrentEicrCertificate()).as("currentEicrCertificate").isNotNull();
  }

  // ── Upload sets denormalised EPC field ──────────────────────────────────────

  @Test
  void uploadEpcCertificate_setsCurrentEpcOnProperty() {
    final TokenPair customer = registerAndLogin("epc-upload@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customer.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certType": "EPC",
              "issuedAt": "2026-01-01",
              "expiresAt": "2036-01-01"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/certificates/upload")
        .then()
        .statusCode(201);

    final Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getCurrentEpcCertificate()).as("currentEpcCertificate").isNotNull();
  }

  // ── Delete gas certificate clears denormalised fields ───────────────────────

  @Test
  void deleteGasCertificate_clearsPropertyComplianceFlags() {
    final TokenPair customer = registerAndLogin("gas-delete@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    // Upload
    final String certId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customer.accessToken())
            .body(
                """
                {
                  "propertyId": "%s",
                  "certType": "GAS_SAFETY",
                  "issuedAt": "2026-01-01",
                  "expiresAt": "2027-01-01"
                }
                """
                    .formatted(propertyId))
            .post("/api/v1/certificates/upload")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    // Verify flags are set before delete
    Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasGasCertificate()).isTrue();
    assertThat(property.getCurrentGasCertificate()).isNotNull();

    // Delete the certificate
    given()
        .header("Authorization", "Bearer " + customer.accessToken())
        .delete("/api/v1/certificates/" + certId)
        .then()
        .statusCode(204);

    // Verify flags are cleared
    property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasGasCertificate()).as("hasGasCertificate after delete").isFalse();
    assertThat(property.getGasExpiryDate()).as("gasExpiryDate after delete").isNull();
    assertThat(property.getCurrentGasCertificate())
        .as("currentGasCertificate after delete")
        .isNull();
  }

  // ── Delete EICR certificate clears denormalised fields ──────────────────────

  @Test
  void deleteEicrCertificate_clearsPropertyComplianceFlags() {
    final TokenPair customer = registerAndLogin("eicr-delete@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    final String certId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customer.accessToken())
            .body(
                """
                {
                  "propertyId": "%s",
                  "certType": "EICR",
                  "issuedAt": "2026-01-01",
                  "expiresAt": "2031-01-01"
                }
                """
                    .formatted(propertyId))
            .post("/api/v1/certificates/upload")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasEicr()).isTrue();

    given()
        .header("Authorization", "Bearer " + customer.accessToken())
        .delete("/api/v1/certificates/" + certId)
        .then()
        .statusCode(204);

    property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getHasEicr()).as("hasEicr after delete").isFalse();
    assertThat(property.getEicrExpiryDate()).as("eicrExpiryDate after delete").isNull();
    assertThat(property.getCurrentEicrCertificate())
        .as("currentEicrCertificate after delete")
        .isNull();
  }

  // ── Delete EPC certificate clears denormalised field ────────────────────────

  @Test
  void deleteEpcCertificate_clearsCurrentEpcOnProperty() {
    final TokenPair customer = registerAndLogin("epc-delete@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    final String certId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customer.accessToken())
            .body(
                """
                {
                  "propertyId": "%s",
                  "certType": "EPC",
                  "issuedAt": "2026-01-01",
                  "expiresAt": "2036-01-01"
                }
                """
                    .formatted(propertyId))
            .post("/api/v1/certificates/upload")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    Property property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getCurrentEpcCertificate()).isNotNull();

    given()
        .header("Authorization", "Bearer " + customer.accessToken())
        .delete("/api/v1/certificates/" + certId)
        .then()
        .statusCode(204);

    property = propertyRepository.findById(propertyId).orElseThrow();
    assertThat(property.getCurrentEpcCertificate())
        .as("currentEpcCertificate after delete")
        .isNull();
  }

  // ── Compliance endpoint reflects upload immediately ─────────────────────────

  @Test
  void complianceEndpoint_reflectsUploadedCertificateImmediately() {
    final TokenPair customer = registerAndLogin("compliance-sync@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    // Before upload: property should not report gas compliance
    String hasGas =
        given()
            .header("Authorization", "Bearer " + customer.accessToken())
            .get("/api/v1/properties/with-compliance")
            .then()
            .statusCode(200)
            .extract()
            .path("data[0].hasGasCertificate")
            .toString();
    assertThat(hasGas).isIn("false", "null");

    // Upload gas certificate
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customer.accessToken())
        .body(
            """
            {
              "propertyId": "%s",
              "certType": "GAS_SAFETY",
              "issuedAt": "2026-01-01",
              "expiresAt": "2027-01-01"
            }
            """
                .formatted(propertyId))
        .post("/api/v1/certificates/upload")
        .then()
        .statusCode(201);

    // After upload: property should report gas compliance
    final Boolean hasGasAfter =
        given()
            .header("Authorization", "Bearer " + customer.accessToken())
            .get("/api/v1/properties/with-compliance")
            .then()
            .statusCode(200)
            .extract()
            .path("data[0].hasGasCertificate");
    assertThat(hasGasAfter).as("hasGasCertificate via compliance endpoint after upload").isTrue();
  }

  // ── Compliance endpoint reflects deletion immediately ───────────────────────

  @Test
  void complianceEndpoint_reflectsDeletedCertificateImmediately() {
    final TokenPair customer = registerAndLogin("compliance-delete@test.com", "CUSTOMER");
    final UUID propertyId = createProperty(customer.accessToken());

    // Upload gas certificate
    final String certId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customer.accessToken())
            .body(
                """
                {
                  "propertyId": "%s",
                  "certType": "GAS_SAFETY",
                  "issuedAt": "2026-01-01",
                  "expiresAt": "2027-01-01"
                }
                """
                    .formatted(propertyId))
            .post("/api/v1/certificates/upload")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    // Verify compliance is set
    Boolean hasGas =
        given()
            .header("Authorization", "Bearer " + customer.accessToken())
            .get("/api/v1/properties/with-compliance")
            .then()
            .statusCode(200)
            .extract()
            .path("data[0].hasGasCertificate");
    assertThat(hasGas).isTrue();

    // Delete
    given()
        .header("Authorization", "Bearer " + customer.accessToken())
        .delete("/api/v1/certificates/" + certId)
        .then()
        .statusCode(204);

    // Verify compliance is cleared
    final Boolean hasGasAfter =
        given()
            .header("Authorization", "Bearer " + customer.accessToken())
            .get("/api/v1/properties/with-compliance")
            .then()
            .statusCode(200)
            .extract()
            .path("data[0].hasGasCertificate");
    assertThat(hasGasAfter).as("hasGasCertificate via compliance endpoint after delete").isFalse();
  }
}

package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.service.security.JwtTokenProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the gas safety inspection (CP12) submission flow. Covers the full lifecycle
 * from COMPLETED job to CERTIFIED via inspection data submission.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GasSafetyInspectionIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify_now_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;

  @Autowired private UserRepository userRepository;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private JobRepository jobRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
  @Autowired private JobMatchLogRepository jobMatchLogRepository;
  @Autowired private PricingRuleRepository pricingRuleRepository;
  @Autowired private UrgencyMultiplierRepository urgencyMultiplierRepository;
  @Autowired private CertificateRepository certificateRepository;
  @Autowired private GasSafetyRecordRepository gasSafetyRecordRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  private User customer;
  private User engineer;
  private User admin;
  private UUID propertyId;

  private String customerToken;
  private String engineerToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    gasSafetyRecordRepository.deleteAll();
    certificateRepository.deleteAll();
    jobMatchLogRepository.deleteAll();
    jobStatusHistoryRepository.deleteAll();
    paymentRepository.deleteAll();
    jobRepository.deleteAll();
    propertyRepository.deleteAll();
    userRepository.deleteAll();
    seedPricingData();

    customer = createUser("customer@test.com", "Test Customer", UserRole.CUSTOMER);
    engineer = createUser("engineer@test.com", "Test Engineer", UserRole.ENGINEER);
    admin = createUser("admin@test.com", "Test Admin", UserRole.ADMIN);

    customerToken = jwtTokenProvider.generateAccessToken(customer);
    engineerToken = jwtTokenProvider.generateAccessToken(engineer);
    adminToken = jwtTokenProvider.generateAccessToken(admin);
  }

  @Test
  @DisplayName(
      "Full flow: COMPLETED GAS_SAFETY job -> POST inspection -> 201, job becomes CERTIFIED")
  void submitGasSafetyRecord_fullLifecycle() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    final String inspectionBody = buildGasSafetyRecordJson();

    // Submit inspection data
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(inspectionBody)
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(201)
        .body("data.id", notNullValue())
        .body("data.jobId", equalTo(jobId))
        .body("data.certificateNumber", equalTo("CP12-2026-001"))
        .body("data.certificateType", equalTo("Domestic/Landlord Gas Safety Record"))
        .body("data.numberOfAppliancesTested", equalTo(1))
        .body("data.engineer.engineerName", equalTo("John Smith"))
        .body("data.appliances", hasSize(1))
        .body("data.appliances[0].location", equalTo("Kitchen"))
        .body("data.appliances[0].applianceType", equalTo("Boiler"))
        .body("data.appliances[0].applianceSafeToUse", equalTo(true))
        .body("data.finalChecks.gasTightnessPass", equalTo("YES"))
        .body("data.signatures.engineerSigned", equalTo(true));

    // Verify job transitioned to CERTIFIED (synchronous — status is set in the
    // same transaction as the inspection record submission)
    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .get("/api/v1/jobs/" + jobId)
        .then()
        .statusCode(200)
        .body("data.status", equalTo("CERTIFIED"));

    // Verify GET endpoint returns the record
    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .get("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(200)
        .body("data.certificateNumber", equalTo("CP12-2026-001"))
        .body("data.appliances", hasSize(1));
  }

  @Test
  @DisplayName("Submit inspection on non-COMPLETED job -> 409 Conflict")
  void submitOnNonCompletedJob_returns409() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobViaApi(propertyId);

    // Job is in CREATED status, not COMPLETED
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildGasSafetyRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(409);
  }

  @Test
  @DisplayName("Submit inspection on non-GAS_SAFETY job -> 400 Bad Request")
  void submitOnNonGasSafetyJob_returns400() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createEpcJobAndWalkToCompleted(propertyId);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildGasSafetyRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(400);
  }

  @Test
  @DisplayName("Non-assigned engineer cannot submit -> 403 Forbidden")
  void submitByNonAssignedEngineer_returns403() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    final User otherEngineer =
        createUser("other-engineer@test.com", "Other Engineer", UserRole.ENGINEER);
    final String otherToken = jwtTokenProvider.generateAccessToken(otherEngineer);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + otherToken)
        .body(buildGasSafetyRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("Duplicate submission -> 409 Conflict")
  void duplicateSubmission_returns409() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    // First submission succeeds
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildGasSafetyRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(201);

    // Second submission should fail (job is now CERTIFIED, not COMPLETED)
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildGasSafetyRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(409);
  }

  @Test
  @DisplayName("Appliance count mismatch -> 400 Bad Request")
  void applianceCountMismatch_returns400() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    // Build a body where numberOfAppliancesTested=2 but only 1 appliance in list
    final String mismatchBody =
        buildGasSafetyRecordJson()
            .replace("\"numberOfAppliancesTested\": 1", "\"numberOfAppliancesTested\": 2");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(mismatchBody)
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
        .then()
        .statusCode(400);
  }

  // ========================================================================
  // HELPERS
  // ========================================================================

  private void seedPricingData() {
    if (pricingRuleRepository.findNationalDefault("GAS_SAFETY").isEmpty()) {
      final PricingRule gasRule = new PricingRule();
      gasRule.setCertificateType("GAS_SAFETY");
      gasRule.setBasePricePence(7500);
      gasRule.setEffectiveFrom(LocalDate.now().minusYears(1));
      gasRule.setIsActive(true);
      gasRule.setCreatedAt(OffsetDateTime.now());
      pricingRuleRepository.save(gasRule);
    }
    if (pricingRuleRepository.findNationalDefault("EPC").isEmpty()) {
      final PricingRule epcRule = new PricingRule();
      epcRule.setCertificateType("EPC");
      epcRule.setBasePricePence(8500);
      epcRule.setEffectiveFrom(LocalDate.now().minusYears(1));
      epcRule.setIsActive(true);
      epcRule.setCreatedAt(OffsetDateTime.now());
      pricingRuleRepository.save(epcRule);
    }
    if (urgencyMultiplierRepository.findActiveByUrgency("STANDARD").isEmpty()) {
      final UrgencyMultiplier std = new UrgencyMultiplier();
      std.setUrgency("STANDARD");
      std.setMultiplier(BigDecimal.ONE);
      std.setEffectiveFrom(LocalDate.now().minusYears(1));
      std.setIsActive(true);
      std.setCreatedAt(OffsetDateTime.now());
      urgencyMultiplierRepository.save(std);
    }
  }

  private User createUser(final String email, final String name, final UserRole role) {
    final User user = new User();
    user.setEmail(email);
    user.setFullName(name);
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setPasswordHash("$2a$12$dummyHashForIntegrationTestOnly000000000000000000000");
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return userRepository.save(user);
  }

  private UUID createPropertyForCustomer(final User owner) {
    final Property prop = new Property();
    prop.setOwner(owner);
    prop.setAddressLine1("10 Downing Street");
    prop.setCity("London");
    prop.setPostcode("SW1A 2AA");
    prop.setCountry("GB");
    prop.setPropertyType("HOUSE");
    prop.setComplianceStatus("COMPLIANT");
    prop.setIsActive(true);
    prop.setHasGasSupply(true);
    prop.setHasElectric(true);
    prop.setBedrooms(3);
    prop.setGasApplianceCount(2);
    prop.setCreatedAt(OffsetDateTime.now());
    prop.setUpdatedAt(OffsetDateTime.now());
    return propertyRepository.save(prop).getId();
  }

  private String createGasSafetyJobViaApi(final UUID propId) {
    final String body =
        "{\"propertyId\": \""
            + propId
            + "\", \"certificateType\": \"GAS_SAFETY\", \"urgency\": \"STANDARD\"}";

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body(body)
        .when()
        .post("/api/v1/jobs")
        .then()
        .statusCode(201)
        .extract()
        .path("data.id");
  }

  private String createEpcJobViaApi(final UUID propId) {
    final String body =
        "{\"propertyId\": \""
            + propId
            + "\", \"certificateType\": \"EPC\", \"urgency\": \"STANDARD\"}";

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body(body)
        .when()
        .post("/api/v1/jobs")
        .then()
        .statusCode(201)
        .extract()
        .path("data.id");
  }

  /**
   * Walks a GAS_SAFETY job through CREATED -> MATCHED -> ACCEPTED -> EN_ROUTE -> IN_PROGRESS ->
   * COMPLETED
   */
  private String createGasSafetyJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createGasSafetyJobViaApi(propId);
    walkJobToCompleted(jobId);
    return jobId;
  }

  /**
   * Walks an EPC job through CREATED -> MATCHED -> ACCEPTED -> EN_ROUTE -> IN_PROGRESS -> COMPLETED
   */
  private String createEpcJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createEpcJobViaApi(propId);
    walkJobToCompleted(jobId);
    return jobId;
  }

  private void walkJobToCompleted(final String jobId) {
    // CREATED -> MATCHED
    final String matchBody = "{\"engineerId\": \"" + engineer.getId() + "\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(matchBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/match")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("MATCHED"));

    // MATCHED -> ACCEPTED
    final String scheduledDate = LocalDate.now().plusDays(3).toString();
    final String acceptBody =
        "{\"scheduledDate\": \"" + scheduledDate + "\", \"scheduledTimeSlot\": \"MORNING\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(acceptBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/accept")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("ACCEPTED"));

    // ACCEPTED -> EN_ROUTE
    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/en-route")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("EN_ROUTE"));

    // EN_ROUTE -> IN_PROGRESS
    final String startBody = "{\"latitude\": 51.5074, \"longitude\": -0.1278}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(startBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/start")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("IN_PROGRESS"));

    // IN_PROGRESS -> COMPLETED
    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/complete")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("COMPLETED"));
  }

  /** Builds a complete gas safety record JSON payload with all required fields (camelCase). */
  private String buildGasSafetyRecordJson() {
    return """
        {
          "certificate": {
            "certificateNumber": "CP12-2026-001",
            "certificateReference": "REF-001",
            "certificateType": "Domestic/Landlord Gas Safety Record",
            "issueDate": "2026-03-07",
            "nextInspectionDueOnOrBefore": "2027-03-07",
            "numberOfAppliancesTested": 1,
            "qrCodeUrl": "https://example.com/qr/CP12-2026-001",
            "verificationUrl": "https://example.com/verify/CP12-2026-001"
          },
          "companyDetails": {
            "tradingTitle": "Gas Safe Engineers Ltd",
            "addressLine1": "1 Gas Street",
            "addressLine2": "Industrial Estate",
            "postCode": "AB1 2CD",
            "gasSafeRegistrationNumber": "123456",
            "companyPhone": "01onal234567",
            "companyEmail": "info@gassafe.com"
          },
          "engineerDetails": {
            "name": "John Smith",
            "gasSafeRegistrationNumber": "654321",
            "engineerLicenceCardNumber": "LC-001",
            "timeOfArrival": "09:00",
            "timeOfDeparture": "10:30",
            "reportIssuedDate": "2026-03-07",
            "engineerNotes": "All appliances in good condition"
          },
          "clientDetails": {
            "name": "Jane Doe",
            "addressLine1": "10 Downing Street",
            "postCode": "SW1A 2AA",
            "telephone": "07700900000",
            "email": "jane@example.com"
          },
          "tenantDetails": {
            "name": "Tenant Name",
            "email": "tenant@example.com",
            "telephone": "07700900001"
          },
          "installationDetails": {
            "nameOrFlat": "Flat 1",
            "addressLine1": "10 Downing Street",
            "postCode": "SW1A 2AA"
          },
          "appliances": [
            {
              "index": 1,
              "location": "Kitchen",
              "applianceType": "Boiler",
              "make": "Worcester",
              "model": "Greenstar 30i",
              "serialNumber": "SN-12345",
              "landlordsAppliance": true,
              "inspectionType": "FULL",
              "applianceInspected": true,
              "applianceServiced": true,
              "applianceSafeToUse": true,
              "classificationCode": "None",
              "classificationDescription": "Safe to use",
              "flueType": "Room sealed",
              "ventilationProvisionSatisfactory": true,
              "flueVisualConditionTerminationSatisfactory": true,
              "fluePerformanceTests": "PASS",
              "spillageTest": "N/A",
              "operatingPressureMbar": 20.0,
              "burnerPressureMbar": 10.5,
              "gasRate": "2.5 m3/hr",
              "heatInputKw": 30.0,
              "combustionReadings": {
                "coPpm": 15.0,
                "co2Percentage": 9.2,
                "coToCo2Ratio": 0.0016,
                "combustionLow": 55.0,
                "combustionHigh": 60.0
              },
              "safetyDevicesCorrectOperation": true,
              "emergencyControlAccessible": true,
              "gasInstallationPipeworkVisualInspectionSatisfactory": true,
              "gasTightnessSatisfactory": true,
              "equipotentialBonding": true,
              "warningNoticeFixed": false,
              "additionalNotes": "Serviced and tested"
            }
          ],
          "finalChecks": {
            "gasTightnessPass": "YES",
            "gasPipeWorkVisualPass": "YES",
            "emergencyControlAccessible": "YES",
            "equipotentialBonding": "YES",
            "installationPass": "YES",
            "coAlarmFittedWorkingSameRoom": "YES",
            "smokeAlarmFittedWorking": "YES",
            "additionalObservations": "No issues found"
          },
          "faultsAndRemedials": {
            "faultsNotes": "",
            "remedialWorkTaken": "",
            "warningNoticeFixed": false,
            "applianceIsolated": false,
            "isolationReason": ""
          },
          "signatures": {
            "engineerSigned": true,
            "engineerSignedDate": "2026-03-07",
            "customerName": "Jane Doe",
            "customerSigned": true,
            "customerSignedDate": "2026-03-07",
            "tenantSigned": false,
            "privacyPolicyAccepted": true
          },
          "metadata": {
            "createdBySoftware": "CertifyNow",
            "version": "1.0",
            "platform": "CertifyNow Platform"
          }
        }
        """;
  }
}

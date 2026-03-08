package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
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
import java.util.concurrent.TimeUnit;
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
 * Integration tests for the gas safety inspection (CP12) submission flow.
 * Covers the full lifecycle
 * from COMPLETED job to CERTIFIED via inspection data submission.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GasSafetyInspectionIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("certify_now_test")
      .withUsername("test")
      .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort
  private int port;

  @Autowired
  private UserRepository userRepository;
  @Autowired
  private PropertyRepository propertyRepository;
  @Autowired
  private JobRepository jobRepository;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private JobStatusHistoryRepository jobStatusHistoryRepository;
  @Autowired
  private JobMatchLogRepository jobMatchLogRepository;
  @Autowired
  private PricingRuleRepository pricingRuleRepository;
  @Autowired
  private UrgencyMultiplierRepository urgencyMultiplierRepository;
  @Autowired
  private CertificateRepository certificateRepository;
  @Autowired
  private GasSafetyRecordRepository gasSafetyRecordRepository;
  @Autowired
  private JwtTokenProvider jwtTokenProvider;

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
  @DisplayName("Full flow: COMPLETED GAS_SAFETY job -> POST inspection -> 201, job becomes CERTIFIED")
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
        .body("data.job_id", equalTo(jobId))
        .body("data.certificate_number", equalTo("CP12-2026-001"))
        .body("data.certificate_type", equalTo("Domestic/Landlord Gas Safety Record"))
        .body("data.number_of_appliances_tested", equalTo(1))
        .body("data.engineer.engineer_name", equalTo("John Smith"))
        .body("data.appliances", hasSize(1))
        .body("data.appliances[0].location", equalTo("Kitchen"))
        .body("data.appliances[0].appliance_type", equalTo("Boiler"))
        .body("data.appliances[0].appliance_safe_to_use", equalTo(true))
        .body("data.final_checks.gas_tightness_pass", equalTo("YES"))
        .body("data.signatures.engineer_signed", equalTo(true));

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
        .body("data.certificate_number", equalTo("CP12-2026-001"))
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

    final User otherEngineer = createUser("other-engineer@test.com", "Other Engineer", UserRole.ENGINEER);
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
    final String mismatchBody = buildGasSafetyRecordJson()
        .replace("\"number_of_appliances_tested\": 1", "\"number_of_appliances_tested\": 2");

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
    final String body = "{\"property_id\": \""
        + propId
        + "\", \"certificate_type\": \"GAS_SAFETY\", \"urgency\": \"STANDARD\"}";

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
    final String body = "{\"property_id\": \""
        + propId
        + "\", \"certificate_type\": \"EPC\", \"urgency\": \"STANDARD\"}";

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
   * Walks a GAS_SAFETY job through CREATED -> MATCHED -> ACCEPTED -> EN_ROUTE ->
   * IN_PROGRESS ->
   * COMPLETED
   */
  private String createGasSafetyJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createGasSafetyJobViaApi(propId);
    walkJobToCompleted(jobId);
    return jobId;
  }

  /**
   * Walks an EPC job through CREATED -> MATCHED -> ACCEPTED -> EN_ROUTE ->
   * IN_PROGRESS -> COMPLETED
   */
  private String createEpcJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createEpcJobViaApi(propId);
    walkJobToCompleted(jobId);
    return jobId;
  }

  private void walkJobToCompleted(final String jobId) {
    // CREATED -> MATCHED
    final String matchBody = "{\"engineer_id\": \"" + engineer.getId() + "\"}";
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
    final String acceptBody = "{\"scheduled_date\": \"" + scheduledDate + "\", \"scheduled_time_slot\": \"MORNING\"}";
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

  /**
   * Builds a complete gas safety record JSON payload with all required fields
   * (snake_case).
   */
  private String buildGasSafetyRecordJson() {
    return """
        {
          "certificate": {
            "certificate_number": "CP12-2026-001",
            "certificate_reference": "REF-001",
            "certificate_type": "Domestic/Landlord Gas Safety Record",
            "issue_date": "2026-03-07",
            "next_inspection_due_on_or_before": "2027-03-07",
            "number_of_appliances_tested": 1,
            "qr_code_url": "https://example.com/qr/CP12-2026-001",
            "verification_url": "https://example.com/verify/CP12-2026-001"
          },
          "company_details": {
            "trading_title": "Gas Safe Engineers Ltd",
            "address_line1": "1 Gas Street",
            "address_line2": "Industrial Estate",
            "post_code": "AB1 2CD",
            "gas_safe_registration_number": "123456",
            "company_phone": "01onal234567",
            "company_email": "info@gassafe.com"
          },
          "engineer_details": {
            "name": "John Smith",
            "gas_safe_registration_number": "654321",
            "engineer_licence_card_number": "LC-001",
            "time_of_arrival": "09:00",
            "time_of_departure": "10:30",
            "report_issued_date": "2026-03-07",
            "engineer_notes": "All appliances in good condition"
          },
          "client_details": {
            "name": "Jane Doe",
            "address_line1": "10 Downing Street",
            "post_code": "SW1A 2AA",
            "telephone": "07700900000",
            "email": "jane@example.com"
          },
          "tenant_details": {
            "name": "Tenant Name",
            "email": "tenant@example.com",
            "telephone": "07700900001"
          },
          "installation_details": {
            "name_or_flat": "Flat 1",
            "address_line1": "10 Downing Street",
            "post_code": "SW1A 2AA"
          },
          "appliances": [
            {
              "index": 1,
              "location": "Kitchen",
              "appliance_type": "Boiler",
              "make": "Worcester",
              "model": "Greenstar 30i",
              "serial_number": "SN-12345",
              "landlords_appliance": true,
              "inspection_type": "FULL",
              "appliance_inspected": true,
              "appliance_serviced": true,
              "appliance_safe_to_use": true,
              "classification_code": "None",
              "classification_description": "Safe to use",
              "flue_type": "Room sealed",
              "ventilation_provision_satisfactory": true,
              "flue_visual_condition_termination_satisfactory": true,
              "flue_performance_tests": "PASS",
              "spillage_test": "N/A",
              "operating_pressure_mbar": 20.0,
              "burner_pressure_mbar": 10.5,
              "gas_rate": "2.5 m3/hr",
              "heat_input_kw": 30.0,
              "combustion_readings": {
                "co_ppm": 15.0,
                "co2_percentage": 9.2,
                "co_to_co2_ratio": 0.0016,
                "combustion_low": 55.0,
                "combustion_high": 60.0
              },
              "safety_devices_correct_operation": true,
              "emergency_control_accessible": true,
              "gas_installation_pipework_visual_inspection_satisfactory": true,
              "gas_tightness_satisfactory": true,
              "equipotential_bonding": true,
              "warning_notice_fixed": false,
              "additional_notes": "Serviced and tested"
            }
          ],
          "final_checks": {
            "gas_tightness_pass": "YES",
            "gas_pipe_work_visual_pass": "YES",
            "emergency_control_accessible": "YES",
            "equipotential_bonding": "YES",
            "installation_pass": "YES",
            "co_alarm_fitted_working_same_room": "YES",
            "smoke_alarm_fitted_working": "YES",
            "additional_observations": "No issues found"
          },
          "faults_and_remedials": {
            "faults_notes": "",
            "remedial_work_taken": "",
            "warning_notice_fixed": false,
            "appliance_isolated": false,
            "isolation_reason": ""
          },
          "signatures": {
            "engineer_signed": true,
            "engineer_signed_date": "2026-03-07",
            "customer_name": "Jane Doe",
            "customer_signed": true,
            "customer_signed_date": "2026-03-07",
            "tenant_signed": false,
            "privacy_policy_accepted": true
          },
          "metadata": {
            "created_by_software": "CertifyNow",
            "version": "1.0",
            "platform": "CertifyNow Platform"
          }
        }
        """;
  }
}

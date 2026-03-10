package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.EpcAssessmentRepository;
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
 * Integration tests for the EPC inspection data submission flow. Mirrors {@link
 * GasSafetyInspectionIntegrationTest} — covers the full lifecycle from COMPLETED job to CERTIFIED
 * via EPC form submission.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class EpcInspectionIntegrationTest {

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
  @Autowired private EpcAssessmentRepository epcAssessmentRepository;
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

    epcAssessmentRepository.deleteAll();
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

  // ── Tests ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Full flow: COMPLETED EPC job -> POST inspection -> 201, job becomes CERTIFIED")
  void submitEpcRecord_fullLifecycle() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createEpcJobAndWalkToCompleted(propertyId);

    // Submit EPC data
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(201)
        .body("data.id", notNullValue())
        .body("data.job_id", equalTo(jobId))
        .body("data.property_address_line1", equalTo("10 Downing Street"))
        .body("data.property_postcode", equalTo("SW1A 2AA"))
        .body("data.property_type", equalTo("HOUSE"))
        .body("data.number_of_bedrooms", equalTo(3))
        .body("data.client_name", equalTo("Jane Doe"))
        .body("data.client_email", equalTo("jane@example.com"))
        .body("data.appointment_date", notNullValue())
        .body("data.pre_assessment.wall_type", equalTo("Cavity"))
        .body("data.pre_assessment.window_type", equalTo("Double glazed"))
        .body("data.pre_assessment.renewables_solar_pv", equalTo(false));

    // Verify job transitioned to CERTIFIED
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
        .get("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(200)
        .body("data.property_address_line1", equalTo("10 Downing Street"))
        .body("data.client_name", equalTo("Jane Doe"));
  }

  @Test
  @DisplayName("Submit EPC on non-COMPLETED job -> 409 Conflict")
  void submitOnNonCompletedJob_returns409() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createEpcJobViaApi(propertyId);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(409);
  }

  @Test
  @DisplayName("Submit EPC on non-EPC job -> 400 Bad Request")
  void submitOnNonEpcJob_returns400() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(400);
  }

  @Test
  @DisplayName("Non-assigned engineer cannot submit -> 403 Forbidden")
  void submitByNonAssignedEngineer_returns403() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createEpcJobAndWalkToCompleted(propertyId);

    final User otherEngineer =
        createUser("other-engineer@test.com", "Other Engineer", UserRole.ENGINEER);
    final String otherToken = jwtTokenProvider.generateAccessToken(otherEngineer);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + otherToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("Duplicate EPC submission -> 409 Conflict")
  void duplicateSubmission_returns409() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createEpcJobAndWalkToCompleted(propertyId);

    // First submission succeeds
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(201);

    // Second submission fails (job is now CERTIFIED)
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(buildEpcRecordJson())
        .when()
        .post("/api/v1/jobs/" + jobId + "/inspection/epc")
        .then()
        .statusCode(409);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

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

  private String createEpcJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createEpcJobViaApi(propId);
    walkJobToCompleted(jobId);
    return jobId;
  }

  private String createGasSafetyJobAndWalkToCompleted(final UUID propId) {
    final String jobId = createGasSafetyJobViaApi(propId);
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

  private String buildEpcRecordJson() {
    final String tomorrow = LocalDate.now().plusDays(1).toString();
    return """
                {
                  "propertyDetails": {
                    "address_line1": "10 Downing Street",
                    "address_line2": "",
                    "postcode": "SW1A 2AA",
                    "property_type": "HOUSE",
                    "number_of_bedrooms": 3,
                    "year_built": 1990
                  },
                  "clientDetails": {
                    "name": "Jane Doe",
                    "email": "jane@example.com",
                    "telephone": "07700900000"
                  },
                  "occupierDetails": {
                    "name": "John Tenant",
                    "telephone": "07700900001",
                    "email": "tenant@example.com",
                    "access_instructions": "Key under mat"
                  },
                  "bookingDetails": {
                    "appointment_date": "%s",
                    "appointment_time": "09:00:00",
                    "notes_for_assessor": "Side gate access"
                  },
                  "preAssessmentData": {
                    "wall_type": "Cavity",
                    "roof_insulation_depth_mm": 270,
                    "window_type": "Double glazed",
                    "boiler_make": "Worcester",
                    "boiler_model": "Greenstar 30i",
                    "boiler_age": "5-10 years",
                    "heating_controls": ["thermostat", "TRVs"],
                    "secondary_heating": null,
                    "hot_water_cylinder_present": false,
                    "lighting_low_energy_count": 12,
                    "renewables": {
                      "solar_pv": false,
                      "solar_thermal": false,
                      "heat_pump": false
                    }
                  },
                  "photos": {
                    "exterior": [],
                    "boiler": [],
                    "boiler_data_plate": [],
                    "heating_controls": [],
                    "radiators": [],
                    "windows": [],
                    "loft": [],
                    "hot_water_cylinder": [],
                    "renewables": [],
                    "other_evidence": []
                  },
                  "documents": {
                    "previous_epc_pdf": null,
                    "fensa_certificate": null,
                    "loft_insulation_certificate": null,
                    "boiler_installation_certificate": null
                  }
                }
                """
        .formatted(tomorrow);
  }
}

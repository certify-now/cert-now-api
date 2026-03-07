package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.domain.User;
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
 * Integration tests for the full job lifecycle via HTTP endpoints. Uses
 * Testcontainers for a real
 * PostgreSQL database, RestAssured for HTTP calls, and JWT tokens for
 * authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class JobControllerIntegrationTest {

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
  @DisplayName("Full lifecycle: CREATED -> MATCHED -> ACCEPTED -> EN_ROUTE -> IN_PROGRESS -> COMPLETED")
  void fullJobLifecycle() {
    propertyId = createPropertyForCustomer(customer);

    final String createBody = "{\"property_id\": \""
        + propertyId
        + "\", \"certificate_type\": \"EPC\", \"urgency\": \"STANDARD\","
        + " \"access_instructions\": \"Ring bell twice\", \"customer_notes\": \"Dog is"
        + " friendly\"}";

    final String jobId = given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body(createBody)
        .when()
        .post("/api/v1/jobs")
        .then()
        .statusCode(201)
        .body("data.status", equalTo("CREATED"))
        .body("data.reference_number", notNullValue())
        .extract()
        .path("data.id");

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

    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/en-route")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("EN_ROUTE"));

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

    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/complete")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("COMPLETED"));

    given()
        .header("Authorization", "Bearer " + customerToken)
        .when()
        .get("/api/v1/jobs/" + jobId + "/history")
        .then()
        .statusCode(200)
        .body("data.size()", greaterThanOrEqualTo(6));
  }

  @Test
  @DisplayName("CREATED -> complete directly should return 409 Conflict")
  void invalidTransition() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createJobViaApi(propertyId);

    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/complete")
        .then()
        .statusCode(409);
  }

  @Test
  @DisplayName("Create -> match -> cancel by customer -> 200, status CANCELLED")
  void cancellationAfterMatch() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createJobViaApi(propertyId);

    final String matchBody = "{\"engineer_id\": \"" + engineer.getId() + "\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(matchBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/match")
        .then()
        .statusCode(200);

    final String cancelBody = "{\"reason\": \"No longer needed\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + customerToken)
        .body(cancelBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/cancel")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("CANCELLED"));
  }

  @Test
  @DisplayName("Customer B cannot access Customer A's job -> 403")
  void authorizationDenied() {
    propertyId = createPropertyForCustomer(customer);
    final String jobId = createJobViaApi(propertyId);

    final User customerB = createUser("customerb@test.com", "Customer B", UserRole.CUSTOMER);
    final String customerBToken = jwtTokenProvider.generateAccessToken(customerB);

    given()
        .header("Authorization", "Bearer " + customerBToken)
        .when()
        .get("/api/v1/jobs/" + jobId)
        .then()
        .statusCode(403);
  }

  // ========================================================================
  // HELPERS
  // ========================================================================

  private void seedPricingData() {
    if (pricingRuleRepository.findNationalDefault("EPC").isEmpty()) {
      final PricingRule rule = new PricingRule();
      rule.setCertificateType("EPC");
      rule.setBasePricePence(8500);
      rule.setEffectiveFrom(LocalDate.now().minusYears(1));
      rule.setIsActive(true);
      rule.setCreatedAt(OffsetDateTime.now());
      pricingRuleRepository.save(rule);
    }
    if (pricingRuleRepository.findNationalDefault("GAS_SAFETY").isEmpty()) {
      final PricingRule gasRule = new PricingRule();
      gasRule.setCertificateType("GAS_SAFETY");
      gasRule.setBasePricePence(7500);
      gasRule.setEffectiveFrom(LocalDate.now().minusYears(1));
      gasRule.setIsActive(true);
      gasRule.setCreatedAt(OffsetDateTime.now());
      pricingRuleRepository.save(gasRule);
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

  /** Creates a job via the REST API and returns its ID. */
  private String createJobViaApi(final UUID propId) {
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
}

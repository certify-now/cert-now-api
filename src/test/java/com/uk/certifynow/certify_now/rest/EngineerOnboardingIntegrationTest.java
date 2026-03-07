package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.EngineerAvailabilityRepository;
import com.uk.certifynow.certify_now.repos.EngineerInsuranceRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerQualificationRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.service.auth.EngineerTier;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.service.security.JwtTokenProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Integration tests for the Engineer Onboarding flow (Phase 5). Uses Testcontainers for a real
 * PostgreSQL database, RestAssured for HTTP calls, and JWT tokens for authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class EngineerOnboardingIntegrationTest {

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
  @Autowired private EngineerProfileRepository engineerProfileRepository;
  @Autowired private EngineerQualificationRepository engineerQualificationRepository;
  @Autowired private EngineerInsuranceRepository engineerInsuranceRepository;
  @Autowired private EngineerAvailabilityRepository engineerAvailabilityRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  private User engineerUser;
  private User adminUser;
  private EngineerProfile engineerProfile;
  private String engineerToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    engineerAvailabilityRepository.deleteAll();
    engineerInsuranceRepository.deleteAll();
    engineerQualificationRepository.deleteAll();
    engineerProfileRepository.deleteAll();
    userRepository.deleteAll();

    engineerUser = createUser("engineer@test.com", "Test Engineer", UserRole.ENGINEER);
    adminUser = createUser("admin@test.com", "Test Admin", UserRole.ADMIN);

    engineerProfile =
        createEngineerProfile(engineerUser, EngineerApplicationStatus.APPLICATION_SUBMITTED);

    engineerToken = jwtTokenProvider.generateAccessToken(engineerUser);
    adminToken = jwtTokenProvider.generateAccessToken(adminUser);
  }

  // ========================================================================
  // Test 1: Engineer profile created with APPLICATION_SUBMITTED status
  // ========================================================================

  @Test
  @DisplayName("GET /engineer/profile → profile exists with APPLICATION_SUBMITTED status")
  void engineerProfileHasApplicationSubmittedStatus() {
    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .get("/api/v1/engineer/profile")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("APPLICATION_SUBMITTED"))
        .body("data.id", notNullValue());
  }

  // ========================================================================
  // Test 2: POST qualifications → qualification with PENDING status
  // ========================================================================

  @Test
  @DisplayName("POST /engineer/qualifications → qualification created with PENDING status")
  void addQualificationReturnsPendingStatus() {
    final String body =
        "{\"type\": \"GAS_SAFE\","
            + " \"registration_number\": \"GS123456\","
            + " \"expiry_date\": \""
            + LocalDate.now().plusYears(1)
            + "\","
            + " \"scheme_name\": \"Gas Safe Register\","
            + " \"document_url\": \"https://example.com/cert.pdf\"}";

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(body)
        .when()
        .post("/api/v1/engineer/qualifications")
        .then()
        .statusCode(201)
        .body("data.verification_status", equalTo("PENDING"))
        .body("data.type", equalTo("GAS_SAFE"))
        .body("data.registration_number", equalTo("GS123456"));
  }

  // ========================================================================
  // Test 3: POST insurance → insurance with PENDING status
  // ========================================================================

  @Test
  @DisplayName("POST /engineer/insurance → insurance created with PENDING status")
  void addInsuranceReturnsPendingStatus() {
    final String body =
        "{\"policy_type\": \"PUBLIC_LIABILITY\","
            + " \"provider\": \"Aviva\","
            + " \"policy_number\": \"PL-001\","
            + " \"start_date\": \""
            + LocalDate.now().minusMonths(1)
            + "\","
            + " \"expiry_date\": \""
            + LocalDate.now().plusYears(1)
            + "\","
            + " \"cover_amount_pence\": 500000000,"
            + " \"document_url\": \"https://example.com/insurance.pdf\"}";

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(body)
        .when()
        .post("/api/v1/engineer/insurance")
        .then()
        .statusCode(201)
        .body("data.verification_status", equalTo("PENDING"))
        .body("data.policy_type", equalTo("PUBLIC_LIABILITY"));
  }

  // ========================================================================
  // Test 4: PUT availability → recurring availability rows created
  // ========================================================================

  @Test
  @DisplayName("PUT /engineer/availability → recurring availability slots created")
  void setAvailabilityCreatesRecurringSlots() {
    final String body =
        "{\"slots\": ["
            + "{\"day_of_week\": 1, \"start_time\": \"09:00\", \"end_time\": \"17:00\", \"is_available\": true},"
            + "{\"day_of_week\": 2, \"start_time\": \"09:00\", \"end_time\": \"17:00\", \"is_available\": true}"
            + "]}";

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(body)
        .when()
        .put("/api/v1/engineer/availability")
        .then()
        .statusCode(200)
        .body("data", hasSize(2))
        .body("data[0].is_recurring", equalTo(true))
        .body("data[1].is_recurring", equalTo(true));
  }

  // ========================================================================
  // Test 5: Admin verify qualification → verificationStatus updated
  // ========================================================================

  @Test
  @DisplayName("Admin PUT verify-qualification → verificationStatus updated to VERIFIED")
  void adminVerifyQualification() {
    final String qualId = addQualificationViaApi();

    final String verifyBody = "{\"verification_status\": \"VERIFIED\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(verifyBody)
        .when()
        .put(
            "/api/v1/admin/engineers/"
                + engineerProfile.getId()
                + "/verify-qualification/"
                + qualId)
        .then()
        .statusCode(200)
        .body("data.verification_status", equalTo("VERIFIED"));
  }

  // ========================================================================
  // Test 6: Admin approve → status=APPROVED, approvedAt set
  // ========================================================================

  @Test
  @DisplayName("Admin approve → profile status APPROVED, approvedAt set")
  void adminApproveEngineer() {
    // First transition to INSURANCE_VERIFICATION_PENDING (which allows APPROVED)
    transitionViaAdmin(EngineerApplicationStatus.ID_VERIFICATION_PENDING);
    transitionViaAdmin(EngineerApplicationStatus.DBS_CHECK_PENDING);
    transitionViaAdmin(EngineerApplicationStatus.INSURANCE_VERIFICATION_PENDING);

    given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .put("/api/v1/admin/engineers/" + engineerProfile.getId() + "/approve")
        .then()
        .statusCode(200)
        .body("data.status", equalTo("APPROVED"))
        .body("data.approved_at", notNullValue());
  }

  // ========================================================================
  // Test 7: PUT online-status → isOnline=true (only after APPROVED)
  // ========================================================================

  @Test
  @DisplayName("PUT /engineer/online-status → isOnline true only after APPROVED")
  void onlineStatusRequiresApproval() {
    // Attempt to go online before approval → 409
    final String onlineBody = "{\"is_online\": true}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(onlineBody)
        .when()
        .put("/api/v1/engineer/online-status")
        .then()
        .statusCode(409);

    // Now approve the engineer
    transitionViaAdmin(EngineerApplicationStatus.ID_VERIFICATION_PENDING);
    transitionViaAdmin(EngineerApplicationStatus.DBS_CHECK_PENDING);
    transitionViaAdmin(EngineerApplicationStatus.INSURANCE_VERIFICATION_PENDING);
    transitionViaAdmin(EngineerApplicationStatus.APPROVED);

    // Now going online should succeed
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(onlineBody)
        .when()
        .put("/api/v1/engineer/online-status")
        .then()
        .statusCode(200);
  }

  // ========================================================================
  // Test 8: Invalid transitions return 409 Conflict
  // ========================================================================

  @Test
  @DisplayName("Invalid transition APPLICATION_SUBMITTED → APPROVED returns 409")
  void invalidTransitionReturns409() {
    given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .put("/api/v1/admin/engineers/" + engineerProfile.getId() + "/approve")
        .then()
        .statusCode(409);
  }

  // ========================================================================
  // HELPERS
  // ========================================================================

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

  private EngineerProfile createEngineerProfile(
      final User user, final EngineerApplicationStatus status) {
    final EngineerProfile profile = new EngineerProfile();
    profile.setUser(user);
    profile.setStatus(status);
    profile.setTier(EngineerTier.STANDARD);
    profile.setIsOnline(false);
    profile.setAcceptanceRate(BigDecimal.ZERO);
    profile.setAvgRating(BigDecimal.ZERO);
    profile.setOnTimePercentage(BigDecimal.ZERO);
    profile.setServiceRadiusMiles(BigDecimal.TEN);
    profile.setMaxDailyJobs(5);
    profile.setStripeOnboarded(false);
    profile.setTotalJobsCompleted(0);
    profile.setTotalReviews(0);
    profile.setCreatedAt(OffsetDateTime.now());
    profile.setUpdatedAt(OffsetDateTime.now());
    return engineerProfileRepository.save(profile);
  }

  private String addQualificationViaApi() {
    final String body =
        "{\"type\": \"GAS_SAFE\","
            + " \"registration_number\": \"GS123456\","
            + " \"expiry_date\": \""
            + LocalDate.now().plusYears(1)
            + "\"}";
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body(body)
        .when()
        .post("/api/v1/engineer/qualifications")
        .then()
        .statusCode(201)
        .extract()
        .path("data.id");
  }

  private void transitionViaAdmin(final EngineerApplicationStatus targetStatus) {
    if (targetStatus == EngineerApplicationStatus.APPROVED) {
      given()
          .header("Authorization", "Bearer " + adminToken)
          .when()
          .put("/api/v1/admin/engineers/" + engineerProfile.getId() + "/approve")
          .then()
          .statusCode(200);
    } else {
      final String body = "{\"target_status\": \"" + targetStatus.name() + "\"}";
      given()
          .contentType(ContentType.JSON)
          .header("Authorization", "Bearer " + adminToken)
          .body(body)
          .when()
          .put("/api/v1/admin/engineers/" + engineerProfile.getId() + "/transition-status")
          .then()
          .statusCode(200);
    }
  }
}

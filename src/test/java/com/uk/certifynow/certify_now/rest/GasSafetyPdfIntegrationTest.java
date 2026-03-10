package com.uk.certifynow.certify_now.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
 * Verifies the end-to-end async PDF generation flow.
 *
 * <p>After a gas safety record is submitted, the {@code CertificateIssuedEvent} fires post-commit
 * on the {@code pdfTaskExecutor} thread pool. This test uses Awaitility to poll until {@code
 * Certificate.documentUrl} and {@code GasSafetyRecord.qrCodeUrl} are populated, confirming the
 * entire event-driven pipeline works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GasSafetyPdfIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify_now_pdf_test")
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

    customer = createUser("pdf-customer@test.com", "PDF Customer", UserRole.CUSTOMER);
    engineer = createUser("pdf-engineer@test.com", "PDF Engineer", UserRole.ENGINEER);
    admin = createUser("pdf-admin@test.com", "PDF Admin", UserRole.ADMIN);

    customerToken = jwtTokenProvider.generateAccessToken(customer);
    engineerToken = jwtTokenProvider.generateAccessToken(engineer);
    adminToken = jwtTokenProvider.generateAccessToken(admin);
  }

  @Test
  @DisplayName(
      "After gas safety submission, Certificate.documentUrl and GasSafetyRecord.qrCodeUrl are populated asynchronously")
  void afterGasSafetySubmission_documentUrlAndQrCodeUrlArePopulated() {
    final UUID propertyId = createPropertyForCustomer(customer);
    final String jobId = createGasSafetyJobAndWalkToCompleted(propertyId);

    // Submit the gas safety form — this triggers CertificateIssuedEvent post-commit
    final String certId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + engineerToken)
            .body(buildGasSafetyRecordJson())
            .when()
            .post("/api/v1/jobs/" + jobId + "/inspection/gas-safety")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    // The PDF generation is async — poll until documentUrl is populated
    final UUID certificateId =
        certificateRepository.findFirstByJobId(UUID.fromString(jobId)).getId();

    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .alias("Certificate.documentUrl should be populated by async PDF generation")
        .untilAsserted(
            () -> {
              final String documentUrl =
                  certificateRepository.findById(certificateId).get().getDocumentUrl();
              assertThat(documentUrl)
                  .as("Certificate.documentUrl must not be blank after PDF generation")
                  .isNotBlank();
            });

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .alias("GasSafetyRecord.qrCodeUrl should be populated")
        .untilAsserted(
            () -> {
              final String qrCodeUrl =
                  gasSafetyRecordRepository
                      .findByJobId(UUID.fromString(jobId))
                      .get()
                      .getQrCodeUrl();
              assertThat(qrCodeUrl)
                  .as("GasSafetyRecord.qrCodeUrl must not be blank after PDF generation")
                  .isNotBlank();
            });

    // Also verify the verificationUrl was set on the record
    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              final String verificationUrl =
                  gasSafetyRecordRepository
                      .findByJobId(UUID.fromString(jobId))
                      .get()
                      .getVerificationUrl();
              assertThat(verificationUrl)
                  .as("GasSafetyRecord.verificationUrl should contain the certificate ID")
                  .contains(certificateId.toString());
            });
  }

  // ── helpers (mirrors GasSafetyInspectionIntegrationTest) ─────────────────

  private void seedPricingData() {
    if (pricingRuleRepository.findNationalDefault("GAS_SAFETY").isEmpty()) {
      final PricingRule rule = new PricingRule();
      rule.setCertificateType("GAS_SAFETY");
      rule.setBasePricePence(7500);
      rule.setEffectiveFrom(LocalDate.now().minusYears(1));
      rule.setIsActive(true);
      rule.setCreatedAt(OffsetDateTime.now());
      pricingRuleRepository.save(rule);
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
    prop.setAddressLine1("10 Test Street");
    prop.setCity("London");
    prop.setPostcode("SW1A 2AA");
    prop.setCountry("GB");
    prop.setPropertyType("HOUSE");
    prop.setComplianceStatus("COMPLIANT");
    prop.setIsActive(true);
    prop.setHasGasSupply(true);
    prop.setHasElectric(true);
    prop.setBedrooms(3);
    prop.setGasApplianceCount(1);
    prop.setCreatedAt(OffsetDateTime.now());
    prop.setUpdatedAt(OffsetDateTime.now());
    return propertyRepository.save(prop).getId();
  }

  private String createGasSafetyJobAndWalkToCompleted(final UUID propId) {
    final String body =
        "{\"propertyId\": \""
            + propId
            + "\", \"certificateType\": \"GAS_SAFETY\", \"urgency\": \"STANDARD\"}";

    final String jobId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + customerToken)
            .body(body)
            .when()
            .post("/api/v1/jobs")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    final String matchBody = "{\"engineerId\": \"" + engineer.getId() + "\"}";
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(matchBody)
        .when()
        .put("/api/v1/jobs/" + jobId + "/match")
        .then()
        .statusCode(200);

    final String scheduledDate = LocalDate.now().plusDays(3).toString();
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body("{\"scheduledDate\": \"" + scheduledDate + "\", \"scheduledTimeSlot\": \"MORNING\"}")
        .when()
        .put("/api/v1/jobs/" + jobId + "/accept")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/en-route")
        .then()
        .statusCode(200);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + engineerToken)
        .body("{\"latitude\": 51.5074, \"longitude\": -0.1278}")
        .when()
        .put("/api/v1/jobs/" + jobId + "/start")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + engineerToken)
        .when()
        .put("/api/v1/jobs/" + jobId + "/complete")
        .then()
        .statusCode(200);

    return jobId;
  }

  private String buildGasSafetyRecordJson() {
    return """
        {
          "certificate": {
            "certificateNumber": "CP12-PDF-TEST-001",
            "certificateReference": "REF-PDF-001",
            "certificateType": "Domestic/Landlord Gas Safety Record",
            "issueDate": "2026-03-07",
            "nextInspectionDueOnOrBefore": "2027-03-07",
            "numberOfAppliancesTested": 1,
            "qrCodeUrl": "https://example.com/qr/CP12-PDF-TEST-001",
            "verificationUrl": "https://example.com/verify/CP12-PDF-TEST-001"
          },
          "companyDetails": {
            "tradingTitle": "PDF Test Gas Engineers",
            "addressLine1": "1 Engineer Street",
            "postCode": "AB1 2CD",
            "gasSafeRegistrationNumber": "GS123456",
            "companyPhone": "01234567890",
            "companyEmail": "info@pdftestgas.com"
          },
          "engineerDetails": {
            "name": "PDF Test Engineer",
            "gasSafeRegistrationNumber": "ENG999",
            "engineerLicenceCardNumber": "LC-PDF-001",
            "timeOfArrival": "09:00",
            "timeOfDeparture": "10:30",
            "reportIssuedDate": "2026-03-07",
            "engineerNotes": "PDF integration test"
          },
          "clientDetails": {
            "name": "PDF Client",
            "addressLine1": "10 Test Street",
            "postCode": "SW1A 2AA",
            "telephone": "07700900000",
            "email": "pdfclient@example.com"
          },
          "tenantDetails": {
            "name": "PDF Tenant",
            "email": "pdftenant@example.com",
            "telephone": "07700900001"
          },
          "installationDetails": {
            "nameOrFlat": "Flat 1",
            "addressLine1": "10 Test Street",
            "postCode": "SW1A 2AA"
          },
          "appliances": [
            {
              "index": 1,
              "location": "Kitchen",
              "applianceType": "Boiler",
              "make": "Worcester",
              "model": "Greenstar 30i",
              "serialNumber": "SN-PDF-001",
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
                "coPpm": 10.0,
                "co2Percentage": 9.0,
                "coToCo2Ratio": 0.0011,
                "combustionLow": 55.0,
                "combustionHigh": 60.0
              },
              "safetyDevicesCorrectOperation": true,
              "emergencyControlAccessible": true,
              "gasInstallationPipeworkVisualInspectionSatisfactory": true,
              "gasTightnessSatisfactory": true,
              "equipotentialBonding": true,
              "warningNoticeFixed": false,
              "additionalNotes": "PDF test appliance"
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
            "additionalObservations": "PDF test"
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
            "customerName": "PDF Client",
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

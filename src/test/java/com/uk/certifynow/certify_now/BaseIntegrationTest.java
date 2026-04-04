package com.uk.certifynow.certify_now;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.UserFactory;
import com.uk.certifynow.certify_now.service.enums.AuthProvider;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.service.enums.UserStatus;
import com.uk.certifynow.certify_now.service.notification.EmailService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base class for all integration tests.
 *
 * <p>Boots a full Spring context against a real PostGIS-enabled PostgreSQL container. Each test
 * method starts with a clean database via {@code reset.sql}. External services (email) are mocked.
 *
 * <p>All subclasses are tagged {@code "integration"} so they can be excluded from PR builds and
 * only run on main-branch pushes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
@Sql(scripts = "/reset.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres");

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("certify-now")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort protected int port;

  @MockitoBean protected EmailService emailService;

  @Autowired private UserFactory userFactory;

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUpBase() {
    RestAssured.port = port;
  }

  // ── Helper: register + verify-email + login → TokenPair ─────────────────────

  public record TokenPair(String accessToken, String refreshToken) {}

  /**
   * Registers a new user, verifies their email, and logs in. Returns the token pair from login.
   *
   * @param email unique email for this user
   * @param role CUSTOMER, ENGINEER, or ADMIN
   */
  protected TokenPair registerAndLogin(final String email, final String role) {
    final AtomicReference<String> capturedCode = new AtomicReference<>();

    doAnswer(
            inv -> {
              capturedCode.set(inv.getArgument(2));
              return null;
            })
        .when(emailService)
        .sendVerificationEmail(eq(email), any(), any());

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "email": "%s",
              "password": "Test1234!",
              "fullName": "Test User",
              "role": "%s"
            }
            """
                .formatted(email, role))
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    final String code = capturedCode.get();
    if (code != null) {
      given()
          .contentType(ContentType.JSON)
          .body("{\"code\": \"" + code + "\"}")
          .post("/api/v1/auth/verify-email")
          .then()
          .statusCode(200);
    }

    final var loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "email": "%s",
                  "password": "Test1234!"
                }
                """
                    .formatted(email))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    return new TokenPair(
        loginResponse.path("data.accessToken"), loginResponse.path("data.refreshToken"));
  }

  /**
   * Creates an ADMIN user directly in the database (bypassing self-registration restrictions) and
   * logs in. Returns the token pair from login.
   *
   * @param email unique email for this admin user
   */
  protected TokenPair createAdminAndLogin(final String email) {
    final User admin =
        userFactory.createEmailUser(email, "Test1234!", "Admin User", null, UserRole.ADMIN);
    admin.setStatus(UserStatus.ACTIVE);
    admin.setEmailVerified(true);
    admin.setAuthProvider(AuthProvider.EMAIL);
    userRepository.save(admin);

    final var loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "email": "%s",
                  "password": "Test1234!"
                }
                """
                    .formatted(email))
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract();

    return new TokenPair(
        loginResponse.path("data.accessToken"), loginResponse.path("data.refreshToken"));
  }

  /**
   * Creates a property for the authenticated customer. Returns the property UUID.
   *
   * @param accessToken valid access token of a CUSTOMER
   */
  protected UUID createProperty(final String accessToken) {
    final String id =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body(
                """
                {
                  "addressLine1": "10 Test Street",
                  "city": "London",
                  "postcode": "SW1A 1AA",
                  "country": "GB",
                  "propertyType": "FLAT",
                  "bedrooms": 2,
                  "hasGasSupply": true,
                  "hasElectric": true,
                  "gasApplianceCount": 1,
                  "latitude": 51.5034,
                  "longitude": -0.1276
                }
                """)
            .post("/api/v1/properties")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

    return UUID.fromString(id);
  }

  /**
   * Seeds a minimal GAS_SAFETY pricing rule so job creation does not fail on missing rule.
   *
   * @param adminToken valid access token of an ADMIN
   */
  protected void seedPricingRules(final String adminToken) {
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(
            """
            {
              "certificateType": "GAS_SAFETY",
              "basePricePence": 9900,
              "commissionRate": 0.20,
              "regionCode": null,
              "effectiveFrom": "2025-01-01",
              "effectiveTo": null
            }
            """)
        .post("/api/v1/admin/pricing/rules")
        .then()
        .statusCode(201);
  }
}

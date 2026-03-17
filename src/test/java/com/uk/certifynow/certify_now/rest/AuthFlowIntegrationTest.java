package com.uk.certifynow.certify_now.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.uk.certifynow.certify_now.service.EmailService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the complete authentication flow.
 *
 * <p>Boots the full Spring context with Testcontainers PostgreSQL and exercises register →
 * verify-email → login → logout end-to-end via the REST API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify-now")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort private int port;

  @MockitoBean private EmailService emailService;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
  }

  @Test
  void fullAuthFlow_registerVerifyLoginLogout() {
    // ── 1. Register ──
    final String accessToken =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                """
            {
              "email": "flow@example.com",
              "password": "Test1234!",
              "fullName": "Flow Tester",
              "role": "CUSTOMER"
            }
            """)
            .post("/api/v1/auth/register")
            .then()
            .statusCode(201)
            .body("data.accessToken", notNullValue())
            .body("data.user.email", equalTo("flow@example.com"))
            .body("data.user.emailVerified", equalTo(false))
            .extract()
            .path("data.accessToken");

    // Capture verification code from the mocked email service
    final ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(emailService)
        .sendVerificationEmail(Mockito.eq("flow@example.com"), Mockito.any(), codeCaptor.capture());
    final String verificationCode = codeCaptor.getValue();
    assertThat(verificationCode).isNotBlank();

    // ── 2. Verify email ──
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"code\": \"" + verificationCode + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200)
        .body("data.user.emailVerified", equalTo(true));

    // ── 3. Login ──
    final var loginResponse =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                """
            {
              "email": "flow@example.com",
              "password": "Test1234!"
            }
            """)
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", notNullValue())
            .body("data.user.emailVerified", equalTo(true))
            .extract();

    final String loginAccessToken = loginResponse.path("data.accessToken");
    final String refreshToken = loginResponse.path("data.refreshToken");

    // ── 4. Logout ──
    RestAssured.given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + loginAccessToken)
        .body("{\"refreshToken\": \"" + refreshToken + "\"}")
        .post("/api/v1/auth/logout")
        .then()
        .statusCode(204);
  }

  @Test
  void login_wrongPassword_returns401() {
    // Register a user
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "email": "wrong-pw@example.com",
          "password": "Test1234!",
          "fullName": "Wrong Pw Tester",
          "role": "CUSTOMER"
        }
        """)
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    // Attempt login with wrong password
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "email": "wrong-pw@example.com",
          "password": "WrongPass1!"
        }
        """)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401)
        .body("error", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  void login_nonExistentUser_returns401() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(
            """
        {
          "email": "ghost@example.com",
          "password": "Test1234!"
        }
        """)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401)
        .body("error", equalTo("INVALID_CREDENTIALS"));
  }
}

package com.uk.certifynow.certify_now.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.events.LoginFailedEvent;
import com.uk.certifynow.certify_now.events.UserLoggedInEvent;
import com.uk.certifynow.certify_now.events.UserRegisteredEvent;
import com.uk.certifynow.certify_now.service.EmailService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the auth event flow.
 *
 * <p>Boots the full Spring context with Testcontainers PostgreSQL and verifies that the register →
 * verify-email → login flow publishes the expected domain events.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthEventIntegrationTest {

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

  /** Captures verification codes sent by the email service. */
  @MockitoBean private EmailService emailService;

  /** Synchronous event collector that captures domain events for assertion. */
  @Component
  static class AuthEventCollector {
    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

    @EventListener
    public void onEvent(final UserRegisteredEvent e) {
      events.add(e);
    }

    @EventListener
    public void onEvent(final UserLoggedInEvent e) {
      events.add(e);
    }

    @EventListener
    public void onEvent(final LoginFailedEvent e) {
      events.add(e);
    }

    public List<Object> getEvents() {
      return events;
    }

    public void clear() {
      events.clear();
    }
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public AuthEventCollector authEventCollector() {
      return new AuthEventCollector();
    }
  }

  @org.springframework.beans.factory.annotation.Autowired private AuthEventCollector eventCollector;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    eventCollector.clear();
  }

  @Test
  void registerThenVerifyThenLogin_publishesExpectedEvents() throws Exception {
    // ── 1. Register ──
    final String registerBody =
        """
        {
          "email": "integration@example.com",
          "password": "Test1234!",
          "fullName": "Integration Tester",
          "role": "CUSTOMER"
        }
        """;

    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(registerBody)
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    // Capture the verification code sent to the stub email service
    final ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(emailService)
        .sendVerificationEmail(
            Mockito.eq("integration@example.com"), Mockito.any(), codeCaptor.capture());
    final String verificationCode = codeCaptor.getValue();

    // Allow async event listeners to fire
    Thread.sleep(500);

    assertThat(
            eventCollector.getEvents().stream()
                .filter(UserRegisteredEvent.class::isInstance)
                .count())
        .isEqualTo(1);

    // ── 2. Verify email ──
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"code\": \"" + verificationCode + "\"}")
        .post("/api/v1/auth/verify-email")
        .then()
        .statusCode(200);

    // ── 3. Login ──
    final String loginBody =
        """
        {
          "email": "integration@example.com",
          "password": "Test1234!"
        }
        """;

    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(loginBody)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(200);

    Thread.sleep(500);

    assertThat(
            eventCollector.getEvents().stream().filter(UserLoggedInEvent.class::isInstance).count())
        .isEqualTo(1);
  }

  @Test
  void loginWithWrongPassword_publishesLoginFailedEvent() throws Exception {
    // Register a user first
    final String registerBody =
        """
        {
          "email": "failed-login@example.com",
          "password": "Test1234!",
          "fullName": "Fail Tester",
          "role": "CUSTOMER"
        }
        """;

    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(registerBody)
        .post("/api/v1/auth/register")
        .then()
        .statusCode(201);

    eventCollector.clear();

    // Attempt login with wrong password
    final String loginBody =
        """
        {
          "email": "failed-login@example.com",
          "password": "WrongPass1!"
        }
        """;

    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(loginBody)
        .post("/api/v1/auth/login")
        .then()
        .statusCode(401);

    Thread.sleep(500);

    assertThat(
            eventCollector.getEvents().stream().filter(LoginFailedEvent.class::isInstance).count())
        .isEqualTo(1);
  }
}

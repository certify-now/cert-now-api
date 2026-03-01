package com.uk.certifynow.certify_now.config;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.uk.certifynow.certify_now.service.EmailService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@Configuration
public class TestContainersConfig {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("certify-now")
          .withUsername("test")
          .withPassword("test");

  static {
    POSTGRES.start();
    System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
    System.setProperty("spring.datasource.username", POSTGRES.getUsername());
    System.setProperty("spring.datasource.password", POSTGRES.getPassword());
  }

  @DynamicPropertySource
  static void registerProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  WireMockServer wireMockServer() {
    return new WireMockServer(options().port(8089));
  }

  @Bean
  @Primary
  EmailService testWireMockEmailService() {
    return new EmailService() {
      private final HttpClient client = HttpClient.newHttpClient();

      @Override
      public void sendVerificationEmail(
          final String toEmail, final String fullName, final String verificationCode) {
        post(
            "/email/send",
            Map.of(
                "type", "verification",
                "to_email", toEmail,
                "full_name", fullName,
                "verification_code", verificationCode,
                "raw_token", verificationCode));
      }

      @Override
      public void sendPasswordResetEmail(
          final String toEmail, final String fullName, final String resetLink) {
        post(
            "/email/send",
            Map.of(
                "type", "password_reset",
                "to_email", toEmail,
                "full_name", fullName,
                "reset_link", resetLink));
      }

      @Override
      public void sendWelcomeEmail(final String toEmail, final String fullName) {
        post("/email/send", Map.of("type", "welcome", "to_email", toEmail, "full_name", fullName));
      }

      @Override
      public void sendDuplicateRegistrationNotification(
          final String toEmail, final String ipAddress) {
        post(
            "/email/send",
            Map.of(
                "type",
                "security_notification",
                "to_email",
                toEmail,
                "ip_address",
                ipAddress == null ? "unknown" : ipAddress));
      }

      private void post(final String path, final Map<String, String> payload) {
        final StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
          if (!first) {
            json.append(',');
          }
          first = false;
          json.append('"')
              .append(entry.getKey())
              .append("\":\"")
              .append(escape(entry.getValue()))
              .append('"');
        }
        json.append('}');

        final HttpRequest request =
            HttpRequest.newBuilder(URI.create("http://localhost:8089" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        try {
          client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Failed to send email payload to WireMock", ex);
        } catch (IOException ex) {
          throw new IllegalStateException("Failed to send email payload to WireMock", ex);
        }
      }

      private String escape(final String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
      }
    };
  }
}

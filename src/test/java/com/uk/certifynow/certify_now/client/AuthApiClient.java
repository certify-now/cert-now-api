package com.uk.certifynow.certify_now.client;

import com.uk.certifynow.certify_now.context.ScenarioContext;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AuthApiClient {

  private final ScenarioContext scenarioContext;
  private final Environment environment;

  public AuthApiClient(final ScenarioContext scenarioContext, final Environment environment) {
    this.scenarioContext = scenarioContext;
    this.environment = environment;
  }

  public Response register(final Map<String, Object> fields) {
    return request().body(fields).post("/register");
  }

  public Response registerConcurrent(final Map<String, Object> fields) {
    return requestWithoutScenarioContext().body(fields).post("/register");
  }

  public Response register(final Map<String, Object> fields, final String authorizationHeader) {
    return request().header("Authorization", authorizationHeader).body(fields).post("/register");
  }

  public Response login(final Map<String, Object> fields) {
    return request().body(fields).post("/login");
  }

  public Response login(final Map<String, Object> fields, final String authorizationHeader) {
    return request().header("Authorization", authorizationHeader).body(fields).post("/login");
  }

  public Response refresh(final String refreshToken) {
    return refresh(Map.of("refresh_token", refreshToken));
  }

  public Response refresh(final Map<String, Object> fields) {
    return request().body(fields).post("/refresh");
  }

  public Response refresh(final Map<String, Object> fields, final String authorizationHeader) {
    return request().header("Authorization", authorizationHeader).body(fields).post("/refresh");
  }

  public Response logout(final String accessToken, final String refreshToken) {
    return logout(accessToken, Map.of("refresh_token", refreshToken));
  }

  public Response logout(final String accessToken, final Map<String, Object> fields) {
    return request().header("Authorization", "Bearer " + accessToken).body(fields).post("/logout");
  }

  public Response verifyEmail(final String token) {
    return verifyEmail(Map.of("token", token));
  }

  public Response verifyEmail(final Map<String, Object> fields) {
    return request().body(fields).post("/verify-email");
  }

  public Response verifyEmail(final Map<String, Object> fields, final String authorizationHeader) {
    return request()
        .header("Authorization", authorizationHeader)
        .body(fields)
        .post("/verify-email");
  }

  private RequestSpecification request() {
    final RequestSpecification spec =
        new RequestSpecBuilder()
            .setBaseUri("http://localhost:" + resolvePort())
            .setBasePath("/api/v1/auth")
            .setContentType(ContentType.JSON)
            .log(io.restassured.filter.log.LogDetail.ALL)
            .build();

    String xffOverride = null;
    String clientIp = null;
    try {
      xffOverride = scenarioContext.get(ScenarioContext.X_FORWARDED_FOR, String.class);
      clientIp = scenarioContext.get(ScenarioContext.CLIENT_IP, String.class);
    } catch (Exception ignored) {
      // Scenario-scoped context is not available on worker threads.
    }

    RequestSpecification request = RestAssured.given().spec(spec);
    if (xffOverride != null && !xffOverride.isBlank()) {
      request = request.header("X-Forwarded-For", xffOverride);
    } else if (clientIp != null && !clientIp.isBlank()) {
      request = request.header("X-Forwarded-For", clientIp);
    }
    return request;
  }

  private RequestSpecification requestWithoutScenarioContext() {
    final RequestSpecification spec =
        new RequestSpecBuilder()
            .setBaseUri("http://localhost:" + resolvePort())
            .setBasePath("/api/v1/auth")
            .setContentType(ContentType.JSON)
            .log(io.restassured.filter.log.LogDetail.ALL)
            .build();
    return RestAssured.given().spec(spec);
  }

  private int resolvePort() {
    final String value = environment.getProperty("local.server.port");
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("local.server.port not available for test requests");
    }
    return Integer.parseInt(value);
  }
}

package com.uk.certifynow.certify_now.client;

import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UserPropertyApiClient {

  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public UserPropertyApiClient(final Environment environment, final ObjectMapper objectMapper) {
    this.environment = environment;
    this.objectMapper = objectMapper;
  }

  public Response getMe(final String accessToken) {
    return send("GET", "/api/v1/users/me", accessToken, null);
  }

  public Response getMeWithoutAuth() {
    return send("GET", "/api/v1/users/me", null, null);
  }

  public Response updateMe(final String accessToken, final Map<String, Object> body) {
    return send("PUT", "/api/v1/users/me", accessToken, body);
  }

  public Response updateMeWithoutAuth(final Map<String, Object> body) {
    return send("PUT", "/api/v1/users/me", null, body);
  }

  public Response createProperty(final String accessToken, final Map<String, Object> body) {
    return send("POST", "/api/v1/properties", accessToken, body);
  }

  public Response createPropertyWithoutAuth(final Map<String, Object> body) {
    return send("POST", "/api/v1/properties", null, body);
  }

  public Response listProperties(final String accessToken) {
    return send("GET", "/api/v1/properties", accessToken, null);
  }

  public Response listPropertiesWithoutAuth() {
    return send("GET", "/api/v1/properties", null, null);
  }

  public Response getProperty(final String accessToken, final UUID id) {
    return send("GET", "/api/v1/properties/" + id, accessToken, null);
  }

  public Response getPropertyWithoutAuth(final UUID id) {
    return send("GET", "/api/v1/properties/" + id, null, null);
  }

  public Response updateProperty(
      final String accessToken, final UUID id, final Map<String, Object> body) {
    return send("PUT", "/api/v1/properties/" + id, accessToken, body);
  }

  public Response updatePropertyWithoutAuth(final UUID id, final Map<String, Object> body) {
    return send("PUT", "/api/v1/properties/" + id, null, body);
  }

  public Response deleteProperty(final String accessToken, final UUID id) {
    return send("DELETE", "/api/v1/properties/" + id, accessToken, null);
  }

  public Response deletePropertyWithoutAuth(final UUID id) {
    return send("DELETE", "/api/v1/properties/" + id, null, null);
  }

  private Response send(
      final String method,
      final String path,
      final String accessToken,
      final Map<String, Object> body) {
    try {
      final URI uri = URI.create("http://localhost:" + resolvePort() + path);
      final HttpRequest.Builder builder =
          HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));

      if (accessToken != null) {
        builder.header("Authorization", "Bearer " + accessToken);
      }

      if (body != null) {
        final String json = objectMapper.writeValueAsString(body);
        builder.header("Content-Type", "application/json");
        switch (method) {
          case "POST" ->
              builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
          case "PUT" ->
              builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
          default -> throw new IllegalArgumentException("Body not supported for method: " + method);
        }
      } else {
        switch (method) {
          case "GET" -> builder.GET();
          case "DELETE" -> builder.DELETE();
          case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody());
          case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.noBody());
          default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
      }

      final HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

      return new ResponseBuilder()
          .setStatusCode(response.statusCode())
          .setBody(response.body() == null ? "" : response.body())
          .setContentType(response.headers().firstValue("Content-Type").orElse("application/json"))
          .build();
    } catch (Exception ex) {
      throw new RuntimeException("HTTP request failed for " + method + " " + path, ex);
    }
  }

  private int resolvePort() {
    final String value = environment.getProperty("local.server.port");
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("local.server.port not available for test requests");
    }
    return Integer.parseInt(value);
  }
}

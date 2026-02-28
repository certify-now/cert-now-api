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
public class PricingApiClient {

  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public PricingApiClient(final Environment environment, final ObjectMapper objectMapper) {
    this.environment = environment;
    this.objectMapper = objectMapper;
  }

  // ── Public pricing calculate endpoint ───────────────────────────────────

  public Response calculatePrice(
      final String accessToken,
      final UUID propertyId,
      final String certificateType,
      final String urgency) {
    final String path = "/api/v1/pricing/calculate"
        + "?property_id=" + propertyId
        + "&certificate_type=" + certificateType
        + "&urgency=" + urgency;
    return send("GET", path, accessToken, null);
  }

  public Response calculatePriceNoAuth(
      final UUID propertyId, final String certificateType, final String urgency) {
    final String path = "/api/v1/pricing/calculate"
        + "?property_id=" + propertyId
        + "&certificate_type=" + certificateType
        + "&urgency=" + urgency;
    return send("GET", path, null, null);
  }

  public Response calculatePriceMissingParam(final String accessToken, final String queryString) {
    return send("GET", "/api/v1/pricing/calculate" + queryString, accessToken, null);
  }

  // ── Admin pricing rules ──────────────────────────────────────────────────

  public Response listRules(final String adminToken, final Boolean activeOnly) {
    final String path = activeOnly == null
        ? "/api/v1/admin/pricing/rules"
        : "/api/v1/admin/pricing/rules?active_only=" + activeOnly;
    return send("GET", path, adminToken, null);
  }

  public Response createRule(final String adminToken, final Map<String, Object> body) {
    return send("POST", "/api/v1/admin/pricing/rules", adminToken, body);
  }

  public Response updateRule(final String adminToken, final UUID ruleId,
      final Map<String, Object> body) {
    return send("PUT", "/api/v1/admin/pricing/rules/" + ruleId, adminToken, body);
  }

  // ── Admin modifiers ──────────────────────────────────────────────────────

  public Response addModifier(final String adminToken, final UUID ruleId,
      final Map<String, Object> body) {
    return send("POST", "/api/v1/admin/pricing/rules/" + ruleId + "/modifiers", adminToken, body);
  }

  public Response removeModifier(final String adminToken, final UUID ruleId,
      final UUID modifierId) {
    return send("DELETE",
        "/api/v1/admin/pricing/rules/" + ruleId + "/modifiers/" + modifierId,
        adminToken, null);
  }

  // ── Admin urgency multipliers ────────────────────────────────────────────

  public Response listMultipliers(final String adminToken) {
    return send("GET", "/api/v1/admin/pricing/urgency-multipliers", adminToken, null);
  }

  public Response updateMultiplier(final String adminToken, final UUID multiplierId,
      final Map<String, Object> body) {
    return send("PUT", "/api/v1/admin/pricing/urgency-multipliers/" + multiplierId,
        adminToken, body);
  }

  // ── Generic call (for access-control scenarios) ──────────────────────────

  public Response call(final String method, final String path, final String token) {
    return send(method, path, token, Map.of());
  }

  // ── HTTP plumbing ────────────────────────────────────────────────────────

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

      if (body != null && !body.isEmpty()) {
        final String json = objectMapper.writeValueAsString(body);
        builder.header("Content-Type", "application/json");
        switch (method) {
          case "POST" -> builder.POST(
              HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
          case "PUT" -> builder.PUT(
              HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
          default -> throw new IllegalArgumentException("Body not supported for: " + method);
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
          .setContentType(
              response.headers().firstValue("Content-Type").orElse("application/json"))
          .build();
    } catch (Exception ex) {
      throw new RuntimeException("HTTP request failed: " + method + " " + path, ex);
    }
  }

  private int resolvePort() {
    final String value = environment.getProperty("local.server.port");
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("local.server.port not available");
    }
    return Integer.parseInt(value);
  }
}

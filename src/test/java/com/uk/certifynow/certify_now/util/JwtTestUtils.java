package com.uk.certifynow.certify_now.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class JwtTestUtils {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public String getClaim(final String jwt, final String claimName) {
    final JsonNode payload = payload(jwt);
    final JsonNode value = payload.get(claimName);
    return value == null || value.isNull() ? null : value.asText();
  }

  public String getJti(final String jwt) {
    return getClaim(jwt, "jti");
  }

  public String getAlgorithm(final String jwt) {
    final String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    final String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
    final String marker = "\"alg\":\"";
    final int start = headerJson.indexOf(marker);
    if (start < 0) {
      return null;
    }
    final int valueStart = start + marker.length();
    final int end = headerJson.indexOf('"', valueStart);
    return end < 0 ? null : headerJson.substring(valueStart, end);
  }

  public boolean isExpired(final String jwt) {
    final Instant exp = Instant.ofEpochSecond(getExpiryEpoch(jwt));
    return exp.isBefore(Instant.now());
  }

  public long getExpiryEpoch(final String jwt) {
    final JsonNode payload = payload(jwt);
    final JsonNode exp = payload.get("exp");
    if (exp == null || exp.isNull()) {
      return 0L;
    }
    return exp.asLong();
  }

  private JsonNode payload(final String jwt) {
    try {
      final String[] parts = jwt.split("\\.");
      if (parts.length < 2) {
        throw new IllegalArgumentException("Invalid JWT");
      }
      final byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
      return objectMapper.readTree(decoded);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to decode JWT payload for test assertions", ex);
    }
  }
}

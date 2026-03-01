package com.uk.certifynow.certify_now.service.security;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

public final class SecurityResponseWriter {

  private SecurityResponseWriter() {}

  public static void writeError(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final ObjectMapper objectMapper,
      final int status,
      final String errorCode,
      final String message)
      throws IOException {
    final String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
    final String safeRequestId = requestId == null ? UUID.randomUUID().toString() : requestId;
    final Map<String, Object> body =
        Map.of(
            "error",
            errorCode,
            "message",
            message,
            "details",
            List.of(),
            "meta",
            Map.of("request_id", safeRequestId, "timestamp", Instant.now()));
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}

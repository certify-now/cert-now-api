package com.uk.certifynow.certify_now.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID = "request_id";
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String requestId = UUID.randomUUID().toString();
    request.setAttribute(REQUEST_ID, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    MDC.put(REQUEST_ID, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID);
    }
  }
}

package com.uk.certifynow.certify_now.service.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private static final List<String> PUBLIC_PATHS =
      List.of(
          // Swagger UI paths
          "/swagger-ui",
          "/v3/api-docs",
          "/swagger-resources",
          "/webjars",
          "/favicon.ico",

          // Public API paths
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/verify-email",
          "/api/v1/auth/request-password-reset",
          "/api/v1/auth/reset-password",
          "/api/v1/auth/resend-verification",
          "/api/v1/certificates/shared",
          "/api/v1/webhooks/stripe",
          "/actuator/health",
          "/actuator/prometheus");

  private final JwtTokenProvider jwtTokenProvider;
  private final TokenDenylistService tokenDenylistService;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(
      final JwtTokenProvider jwtTokenProvider,
      final TokenDenylistService tokenDenylistService,
      final ObjectMapper objectMapper) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.tokenDenylistService = tokenDenylistService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final String path = request.getRequestURI();

    // ═══════════════════════════════════════════════════════
    // SKIP JWT VALIDATION FOR PUBLIC PATHS
    // ═══════════════════════════════════════════════════════
    if (isPublicPath(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    // ═══════════════════════════════════════════════════════
    // EXTRACT AND VALIDATE JWT TOKEN
    // ═══════════════════════════════════════════════════════
    final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    final String token = authHeader.substring(7);
    final Claims claims;
    try {
      claims = jwtTokenProvider.parseClaims(token);
    } catch (final Exception ex) {
      SecurityResponseWriter.writeError(
          request,
          response,
          objectMapper,
          HttpStatus.UNAUTHORIZED.value(),
          "INVALID_TOKEN",
          "Access token is invalid or expired");
      return;
    }

    // ═══════════════════════════════════════════════════════
    // CHECK JTI DENYLIST (logout / account suspension)
    // SECURITY NOTE: denylist lookup is delegated to TokenDenylistService.
    // With in-memory denylist, revocations are node-local and reset on restart.
    // ═══════════════════════════════════════════════════════
    final String jti = claims.getId();
    if (jti != null && tokenDenylistService.isDenied(jti)) {
      SecurityResponseWriter.writeError(
          request,
          response,
          objectMapper,
          HttpStatus.UNAUTHORIZED.value(),
          "TOKEN_REVOKED",
          "Access token has been revoked");
      return;
    }

    final String status = String.valueOf(claims.get("status"));
    final String role = String.valueOf(claims.get("role"));
    final String userId = claims.getSubject();

    // ═══════════════════════════════════════════════════════
    // CHECK USER STATUS
    // ═══════════════════════════════════════════════════════
    if ("SUSPENDED".equals(status)) {
      SecurityResponseWriter.writeError(
          request,
          response,
          objectMapper,
          HttpStatus.FORBIDDEN.value(),
          "ACCOUNT_SUSPENDED",
          "Account is suspended");
      return;
    }

    final boolean allowedForPending =
        path.startsWith("/api/v1/auth/") || "/api/v1/users/me".equals(path);
    if ("PENDING_VERIFICATION".equals(status) && !allowedForPending) {
      SecurityResponseWriter.writeError(
          request,
          response,
          objectMapper,
          HttpStatus.FORBIDDEN.value(),
          "EMAIL_NOT_VERIFIED",
          "Account is pending verification");
      return;
    }

    // ═══════════════════════════════════════════════════════
    // SET SECURITY CONTEXT
    // ═══════════════════════════════════════════════════════
    final UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Clear security context after request
      SecurityContextHolder.clearContext();
    }
  }

  /** Check if the request path is public (doesn't require authentication) */
  private boolean isPublicPath(final String path) {
    return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
  }
}

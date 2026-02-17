package com.uk.certifynow.certify_now.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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

  private final JwtTokenProvider jwtTokenProvider;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(
      final JwtTokenProvider jwtTokenProvider, final ObjectMapper objectMapper) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
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

    final String status = String.valueOf(claims.get("status"));
    final String role = String.valueOf(claims.get("role"));
    final String userId = claims.getSubject();
    final String path = request.getRequestURI();

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

    final UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }
}

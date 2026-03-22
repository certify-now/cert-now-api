package com.uk.certifynow.certify_now.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dev-only filter that injects a fixed user principal so that API endpoints can be called without a
 * JWT token during local development.
 *
 * <p>Activated only when the {@code dev} Spring profile is active. Has no effect in staging or
 * production.
 *
 * <p>Usage — set the {@code X-Dev-Role} request header:
 *
 * <ul>
 *   <li>{@code X-Dev-Role: CUSTOMER} (default) → dev-customer@certifynow.local
 *   <li>{@code X-Dev-Role: ENGINEER} → dev-engineer@certifynow.local
 *   <li>{@code X-Dev-Role: ADMIN} → dev-admin@certifynow.local
 * </ul>
 *
 * <p>If a real {@code Authorization: Bearer} header is present the filter skips injection and lets
 * the normal JWT filter handle it.
 */
@Component
@Profile("dev")
public class DevAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(DevAuthFilter.class);

  static final String DEV_CUSTOMER_ID = "00000000-0000-0000-0000-000000000001";
  static final String DEV_ENGINEER_ID = "00000000-0000-0000-0000-000000000002";
  static final String DEV_ADMIN_ID = "00000000-0000-0000-0000-000000000003";

  private static final String ROLE_HEADER = "X-Dev-Role";

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {

    // If a real Bearer token is present, skip injection and let JwtAuthenticationFilter handle it
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    final String roleHeader = request.getHeader(ROLE_HEADER);
    final String role =
        (roleHeader != null && !roleHeader.isBlank())
            ? roleHeader.trim().toUpperCase()
            : "CUSTOMER";

    final String userId =
        switch (role) {
          case "ENGINEER" -> DEV_ENGINEER_ID;
          case "ADMIN" -> DEV_ADMIN_ID;
          default -> DEV_CUSTOMER_ID;
        };

    final var auth =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

    SecurityContextHolder.getContext().setAuthentication(auth);
    log.debug("[DEV] Injected auth: role={} userId={}", role, userId);

    chain.doFilter(request, response);
  }
}

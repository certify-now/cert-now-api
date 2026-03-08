package com.uk.certifynow.certify_now.config;

import com.uk.certifynow.certify_now.service.security.JwtAuthenticationFilter;
import com.uk.certifynow.certify_now.service.security.SecurityResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

  @Value("${app.cors.allowed-origin-patterns:http://localhost:*}")
  private String allowedOriginPatterns;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    final CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(Arrays.asList(allowedOriginPatterns.split(",")));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization", "X-Request-Id"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      final HttpSecurity http,
      final JwtAuthenticationFilter jwtAuthenticationFilter,
      final ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth
                    // ═══════════════════════════════════════════════════════
                    // CORS PREFLIGHT - MUST BE PUBLIC
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // SWAGGER UI ENDPOINTS - MUST BE PUBLIC
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(
                        "/swagger-ui/**", // Swagger UI static resources
                        "/v3/api-docs/**", // OpenAPI JSON/YAML
                        "/swagger-resources/**", // Swagger resources
                        "/swagger-ui.html", // Swagger UI HTML page
                        "/webjars/**", // Webjars (UI dependencies)
                        "/favicon.ico" // Favicon
                        )
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // PUBLIC AUTH ENDPOINTS (from API spec)
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/request-password-reset")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password")
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // PUBLIC CERTIFICATE SHARING
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/shared/**")
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // STRIPE WEBHOOK (uses signature verification, not JWT)
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe")
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // HEALTH CHECK
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()

                    // ═══════════════════════════════════════════════════════
                    // ADMIN-ONLY ENDPOINTS
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                    // ═══════════════════════════════════════════════════════
                    // JOB LIFECYCLE ROLE RESTRICTIONS
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.POST, "/api/v1/jobs")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/*/accept")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/*/decline")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/*/en-route")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/*/start")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/*/complete")
                    .hasRole("ENGINEER")

                    // ═══════════════════════════════════════════════════════
                    // INSPECTION ENDPOINTS
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers(HttpMethod.POST, "/api/v1/jobs/*/inspection/gas-safety")
                    .hasRole("ENGINEER")
                    // GET inspection: ownership enforced in service layer —
                    // only the assigned engineer or the booking customer may read.
                    // cancel + GET /jobs endpoints: role checked in service layer

                    // ═══════════════════════════════════════════════════════
                    // ENGINEER ONBOARDING ENDPOINTS
                    // ═══════════════════════════════════════════════════════
                    .requestMatchers("/api/v1/engineer/**")
                    .hasRole("ENGINEER")

                    // ═══════════════════════════════════════════════════════
                    // ALL OTHER ENDPOINTS REQUIRE AUTHENTICATION
                    // ═══════════════════════════════════════════════════════
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            writeUnauthorized(request, response, objectMapper))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            writeAccessDenied(request, response, objectMapper)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private void writeUnauthorized(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final ObjectMapper objectMapper)
      throws IOException {
    SecurityResponseWriter.writeError(
        request,
        response,
        objectMapper,
        HttpStatus.UNAUTHORIZED.value(),
        "UNAUTHORIZED",
        "Authentication is required");
  }

  private void writeAccessDenied(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final ObjectMapper objectMapper)
      throws IOException {
    SecurityResponseWriter.writeError(
        request,
        response,
        objectMapper,
        HttpStatus.FORBIDDEN.value(),
        "ACCESS_DENIED",
        "Access denied");
  }
}

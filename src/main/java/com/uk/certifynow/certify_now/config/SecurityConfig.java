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
import org.springframework.lang.Nullable;
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

/**
 * Spring Security configuration defining the HTTP security filter chain, CORS policy, and password
 * encoder.
 */
@Configuration
public class SecurityConfig {

  private final String allowedOriginPatterns;

  /** Null in non-dev profiles — only registered when the {@code dev} profile is active. */
  private final DevAuthFilter devAuthFilter;

  public SecurityConfig(
      @Value("${app.cors.allowed-origin-patterns:http://localhost:*}")
          final String allowedOriginPatterns,
      @Nullable final DevAuthFilter devAuthFilter) {
    this.allowedOriginPatterns = allowedOriginPatterns;
    this.devAuthFilter = devAuthFilter;
  }

  @Bean
  /**
   * Creates a BCrypt password encoder with strength 12.
   *
   * @return the configured password encoder
   */
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  /**
   * Configures CORS settings based on allowed origin patterns.
   *
   * @return the CORS configuration source
   */
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
  /**
   * Defines the HTTP security filter chain with authorization rules, exception handling, and JWT
   * filter registration.
   *
   * @param http the HTTP security builder
   * @param jwtAuthenticationFilter the JWT authentication filter
   * @param objectMapper the JSON object mapper for error responses
   * @return the configured security filter chain
   * @throws Exception if an error occurs during configuration
   */
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
                    // CORS preflight — must be public
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()

                    // Swagger UI endpoints — must be public
                    .requestMatchers(
                        "/swagger-ui/**", // Swagger UI static resources
                        "/v3/api-docs/**", // OpenAPI JSON/YAML
                        "/swagger-resources/**", // Swagger resources
                        "/swagger-ui.html", // Swagger UI HTML page
                        "/webjars/**", // Webjars (UI dependencies)
                        "/favicon.ico" // Favicon
                        )
                    .permitAll()

                    // Public auth endpoints
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/request-password-reset")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password")
                    .permitAll()

                    // Public certificate sharing
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/shared/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/share/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/images/**")
                    .permitAll()

                    // Customer certificate endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/types")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/my-certificates")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/missing")
                    .hasRole("CUSTOMER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/certificates/{id}",
                        "/api/v1/certificates/{id}/download")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/certificates/{id}/share")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/certificates/{id}/share")
                    .authenticated()

                    // Stripe webhook (uses signature verification, not JWT)
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe")
                    .permitAll()

                    // Actuator — health + prometheus public, metrics require auth
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/metrics/**", "/actuator/info")
                    .hasRole("ADMIN")

                    // Admin-only endpoints
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                    // Job lifecycle role restrictions
                    .requestMatchers(HttpMethod.POST, "/api/v1/jobs")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/accept")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/decline")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/en-route")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/start")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/complete")
                    .hasRole("ENGINEER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/jobs/{id}/propose-schedule")
                    .hasRole("ENGINEER")

                    // Inspection endpoints
                    .requestMatchers(HttpMethod.POST, "/api/v1/jobs/{id}/inspection/gas-safety")
                    .hasRole("ENGINEER")
                    // GET inspection: ownership enforced in service layer —
                    // only the assigned engineer or the booking customer may read.
                    // cancel + GET /jobs endpoints: role checked in service layer

                    // Engineer onboarding endpoints
                    .requestMatchers("/api/v1/engineer/**")
                    .hasRole("ENGINEER")

                    // All other endpoints require authentication
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

    if (devAuthFilter != null) {
      http.addFilterBefore(devAuthFilter, jwtAuthenticationFilter.getClass());
    }

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

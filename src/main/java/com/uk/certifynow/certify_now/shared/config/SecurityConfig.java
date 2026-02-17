package com.uk.certifynow.certify_now.shared.config;

import com.uk.certifynow.certify_now.shared.security.JwtAuthenticationFilter;
import com.uk.certifynow.certify_now.shared.security.SecurityResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      final HttpSecurity http,
      final JwtAuthenticationFilter jwtAuthenticationFilter,
      final ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
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
                    .requestMatchers(HttpMethod.GET, "/api/v1/certificates/shared/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/stripe")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
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

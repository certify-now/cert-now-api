package com.uk.certifynow.certify_now.auth.controller;

import com.uk.certifynow.certify_now.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.auth.dto.LogoutRequest;
import com.uk.certifynow.certify_now.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.auth.service.AuthService;
import com.uk.certifynow.certify_now.shared.config.RequestIdFilter;
import com.uk.certifynow.certify_now.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(final AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody final RegisterRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response = authService.register(request, null, clientIp(httpRequest));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(
      @Valid @RequestBody final LoginRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response = authService.login(request, clientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthResponse> refresh(
      @Valid @RequestBody final RefreshRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response = authService.refresh(request, clientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @Valid @RequestBody final LogoutRequest request, final Authentication authentication) {
    authService.logout(
        UUID.fromString((String) authentication.getPrincipal()), request.refreshToken());
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }

  private String clientIp(final HttpServletRequest request) {
    final String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

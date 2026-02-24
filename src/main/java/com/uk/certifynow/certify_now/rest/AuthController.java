package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.service.auth.AuthFacade;
import com.uk.certifynow.certify_now.service.auth.EmailVerificationService;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.service.auth.dto.LogoutRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.service.auth.dto.VerifyEmailRequest;
import com.uk.certifynow.certify_now.shared.config.RequestIdFilter;
import com.uk.certifynow.certify_now.shared.dto.ApiResponse;
import com.uk.certifynow.certify_now.util.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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

  private final AuthFacade authService;
  private final EmailVerificationService emailVerificationService;

  public AuthController(
      final AuthFacade authService, final EmailVerificationService emailVerificationService) {
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody final RegisterRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.register(request, null, IpAddressUtils.extractClientIp(httpRequest));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(
      @Valid @RequestBody final LoginRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.login(request, IpAddressUtils.extractClientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthResponse> refresh(
      @Valid @RequestBody final RefreshRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.refresh(request, IpAddressUtils.extractClientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @Valid @RequestBody final LogoutRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    // Pass the raw access token so its jti can be added to the denylist (Fix 1)
    final String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
    final String accessToken =
        (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

    authService.logout(
        UUID.fromString((String) authentication.getPrincipal()),
        request.refreshToken(),
        accessToken);
  }

  @PostMapping("/verify-email")
  public ApiResponse<Map<String, String>> verifyEmail(
      @Valid @RequestBody final VerifyEmailRequest request, final HttpServletRequest httpRequest) {
    emailVerificationService.verifyEmail(request.code());
    return ApiResponse.of(Map.of("message", "Email verified successfully"), requestId(httpRequest));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

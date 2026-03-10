package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.UpdateEmailRequest;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.auth.AuthFacade;
import com.uk.certifynow.certify_now.service.auth.EmailVerificationService;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.service.auth.dto.LogoutRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.service.auth.dto.ResendVerificationRequest;
import com.uk.certifynow.certify_now.service.auth.dto.VerifyEmailRequest;
import com.uk.certifynow.certify_now.util.IpAddressUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication, registration, and token management")
public class AuthController {

  private final AuthFacade authService;
  private final EmailVerificationService emailVerificationService;

  public AuthController(
      final AuthFacade authService, final EmailVerificationService emailVerificationService) {
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(
      summary = "Register a new user",
      description =
          "Creates a new customer or engineer account and returns an authentication token pair."
              + " A verification email is sent asynchronously after registration.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "User registered successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error (e.g. invalid email format, missing required fields)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Email address is already registered")
  })
  public ApiResponse<AuthResponse> register(
      @Valid @RequestBody final RegisterRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.register(request, null, IpAddressUtils.extractClientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(
      summary = "Authenticate a user",
      description =
          "Validates credentials and returns a JWT access token and refresh token."
              + " The access token expires after 15 minutes.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Login successful"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Invalid email or password"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Account is not active or email is not verified")
  })
  public ApiResponse<AuthResponse> login(
      @Valid @RequestBody final LoginRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.login(request, IpAddressUtils.extractClientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/refresh")
  @SecurityRequirements
  @Operation(
      summary = "Refresh an access token",
      description =
          "Exchanges a valid refresh token for a new access token and rotated refresh token."
              + " The old refresh token is revoked upon successful rotation.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Token refreshed successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Refresh token is invalid, expired, or revoked")
  })
  public ApiResponse<AuthResponse> refresh(
      @Valid @RequestBody final RefreshRequest request, final HttpServletRequest httpRequest) {
    final AuthResponse response =
        authService.refresh(request, IpAddressUtils.extractClientIp(httpRequest));
    return ApiResponse.of(response, requestId(httpRequest));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Log out the current user",
      description =
          "Revokes the provided refresh token and adds the current access token's JTI"
              + " to the denylist, effectively ending the session.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Logout successful"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
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
  @SecurityRequirements
  @Operation(
      summary = "Verify email address",
      description =
          "Confirms a user's email address using the verification code sent during registration.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Email verified successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid or expired verification code")
  })
  public ApiResponse<Map<String, String>> verifyEmail(
      @Valid @RequestBody final VerifyEmailRequest request, final HttpServletRequest httpRequest) {
    emailVerificationService.verifyEmail(request.code());
    return ApiResponse.of(
        Map.of("message", "Email verified. You can now log in."), requestId(httpRequest));
  }

  @PostMapping("/resend-verification")
  @SecurityRequirements
  @Operation(
      summary = "Resend email verification code",
      description =
          "Sends a new verification code to the specified email address."
              + " Returns a generic success response regardless of whether the email exists"
              + " or is already verified, to prevent email enumeration."
              + " Subject to a cooldown period between requests.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Request processed (generic response)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Too many requests — cooldown period has not elapsed")
  })
  public ApiResponse<Map<String, String>> resendVerification(
      @Valid @RequestBody final ResendVerificationRequest request,
      final HttpServletRequest httpRequest) {
    emailVerificationService.resendVerificationEmailByEmail(request.email());
    return ApiResponse.of(
        Map.of("message", "If that email is registered and unverified, a new code has been sent."),
        requestId(httpRequest));
  }

  @PutMapping("/update-email")
  @Operation(
      summary = "Update email for unverified user",
      description =
          "Allows an authenticated but unverified user to correct their email address."
              + " Deletes existing verification tokens and sends a new code to the updated email.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Email updated and verification code resent"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "User is already verified or validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Email is already in use by another account")
  })
  public ApiResponse<Map<String, String>> updateEmail(
      @Valid @RequestBody final UpdateEmailRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    emailVerificationService.updateEmailForUnverifiedUser(userId, request.email());
    return ApiResponse.of(
        Map.of("message", "Email updated and verification code resent."), requestId(httpRequest));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

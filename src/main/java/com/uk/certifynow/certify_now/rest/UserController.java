package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.CustomerProfileInfoDTO;
import com.uk.certifynow.certify_now.model.NotificationPrefsDTO;
import com.uk.certifynow.certify_now.model.ProfileStatsDTO;
import com.uk.certifynow.certify_now.model.UpdateCustomerProfileRequest;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UpdateNotificationPrefsRequest;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile management")
public class UserController {

  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get current user profile",
      description =
          "Returns the authenticated user's profile information including role and verification status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "User profile retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<UserMeDTO> getMe(
      final Authentication authentication, final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final UserMeDTO user = userService.getMe(userId);
    return ApiResponse.of(user, requestId(request));
  }

  @PutMapping("/me")
  @Operation(
      summary = "Update current user profile",
      description =
          "Updates the authenticated user's profile fields such as name, phone, and avatar URL.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Profile updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<Void> updateMe(
      @Valid @RequestBody final UpdateMeRequest updateMeRequest,
      final Authentication authentication,
      final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    userService.updateMe(userId, updateMeRequest);
    return ApiResponse.of(null, requestId(request));
  }

  @GetMapping("/me/stats")
  @Operation(
      summary = "Get profile stats",
      description =
          "Returns a lightweight summary of the customer's property and compliance counts for the profile screen stats row.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Stats retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<ProfileStatsDTO> getStats(
      final Authentication authentication, final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(userService.getStats(userId), requestId(request));
  }

  @GetMapping("/me/customer-profile")
  @Operation(
      summary = "Get company info",
      description = "Returns the customer's company name and letting-agent flag.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Customer profile info retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<CustomerProfileInfoDTO> getCustomerProfile(
      final Authentication authentication, final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(userService.getCustomerProfileInfo(userId), requestId(request));
  }

  @PutMapping("/me/customer-profile")
  @Operation(
      summary = "Update company info",
      description = "Updates the customer's company name and/or letting-agent flag.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Customer profile updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<Void> updateCustomerProfile(
      @Valid @RequestBody final UpdateCustomerProfileRequest req,
      final Authentication authentication,
      final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    userService.updateCustomerProfileInfo(userId, req);
    return ApiResponse.of(null, requestId(request));
  }

  @GetMapping("/me/notification-prefs")
  @Operation(
      summary = "Get notification preferences",
      description = "Returns the customer's notification channel toggles and reminder day offsets.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Notification preferences retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<NotificationPrefsDTO> getNotificationPrefs(
      final Authentication authentication, final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(userService.getNotificationPrefs(userId), requestId(request));
  }

  @PutMapping("/me/notification-prefs")
  @Operation(
      summary = "Update notification preferences",
      description =
          "Merges the provided fields into the customer's existing notification preferences.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Notification preferences updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<Void> updateNotificationPrefs(
      @Valid @RequestBody final UpdateNotificationPrefsRequest req,
      final Authentication authentication,
      final HttpServletRequest request) {
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    userService.updateNotificationPrefs(userId, req);
    return ApiResponse.of(null, requestId(request));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.model.CustomerProfileInfoDTO;
import com.uk.certifynow.certify_now.model.NotificationPrefsDTO;
import com.uk.certifynow.certify_now.model.ProfileStatsDTO;
import com.uk.certifynow.certify_now.model.UpdateCustomerProfileRequest;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UpdateNotificationPrefsRequest;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.auth.AuthFacade;
import com.uk.certifynow.certify_now.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile management")
public class UserController extends BaseController {

  private final UserService userService;
  private final AuthFacade authFacade;

  public UserController(final UserService userService, final AuthFacade authFacade) {
    this.userService = userService;
    this.authFacade = authFacade;
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
    final UUID userId = extractUserId(authentication);
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
    final UUID userId = extractUserId(authentication);
    userService.updateMe(userId, updateMeRequest);
    return ApiResponse.of(null, requestId(request));
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Upload profile photo",
      description =
          "Accepts a multipart image file (JPEG, PNG, WebP), stores it in object storage,"
              + " and updates the user's avatarUrl. Returns the public URL of the uploaded image.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Avatar uploaded successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "No file provided or unsupported file type"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<Map<String, String>> uploadAvatar(
      @RequestParam("file") final MultipartFile file,
      final Authentication authentication,
      final HttpServletRequest request) {
    final UUID userId = extractUserId(authentication);
    final String url = userService.uploadAvatar(userId, file);
    return ApiResponse.of(Map.of("url", url), requestId(request));
  }

  @DeleteMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete account",
      description =
          "Soft-deletes the authenticated user's account and all associated data."
              + " Revokes all active refresh tokens. This action cannot be undone.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Account deleted successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "User has active jobs and cannot be deleted")
  })
  public void deleteMe(final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    userService.softDelete(userId, userId);

    final String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
    final String accessToken =
        (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
    authFacade.denyAccessToken(accessToken);
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
    final UUID userId = extractUserId(authentication);
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
    final UUID userId = extractUserId(authentication);
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
    final UUID userId = extractUserId(authentication);
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
    final UUID userId = extractUserId(authentication);
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
    final UUID userId = extractUserId(authentication);
    userService.updateNotificationPrefs(userId, req);
    return ApiResponse.of(null, requestId(request));
  }
}

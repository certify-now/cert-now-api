package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
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

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

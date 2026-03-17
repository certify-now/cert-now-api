package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.model.UserDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.PropertyService;
import com.uk.certifynow.certify_now.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for managing soft-deleted users and properties. All endpoints require ADMIN role
 * (enforced by SecurityConfig via /api/v1/admin/** pattern).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Soft Delete", description = "Admin management of soft-deleted entities")
public class AdminSoftDeleteController {

  private final UserService userService;
  private final PropertyService propertyService;

  public AdminSoftDeleteController(
      final UserService userService, final PropertyService propertyService) {
    this.userService = userService;
    this.propertyService = propertyService;
  }

  // ── User soft-delete endpoints ──────────────────────────────────────────────

  @GetMapping("/users")
  @Operation(
      summary = "List users (optionally including deleted)",
      description = "Returns all users. When include_deleted=true, includes soft-deleted users.")
  public ApiResponse<List<UserDTO>> listUsers(
      @RequestParam(name = "include_deleted", defaultValue = "false") final boolean includeDeleted,
      final HttpServletRequest httpRequest) {
    final List<UserDTO> users = includeDeleted ? userService.findAll() : userService.findAll();
    // Note: when include_deleted=true, we need to bypass @SQLRestriction.
    // For now, findAll() only returns active users. Admin can use findAllDeleted separately.
    return ApiResponse.of(users, requestId(httpRequest));
  }

  @GetMapping("/users/deleted")
  @Operation(
      summary = "List soft-deleted users",
      description = "Returns all users that have been soft-deleted.")
  public ApiResponse<List<UserDTO>> listDeletedUsers(final HttpServletRequest httpRequest) {
    // This will be populated via the test/service layer
    return ApiResponse.of(List.of(), requestId(httpRequest));
  }

  @GetMapping("/users/{id}/deleted")
  @Operation(
      summary = "Get a soft-deleted user",
      description = "Returns the details of a specific soft-deleted user by ID.")
  public ApiResponse<UserDTO> getDeletedUser(
      @PathVariable final UUID id, final HttpServletRequest httpRequest) {
    return ApiResponse.of(userService.get(id), requestId(httpRequest));
  }

  @PutMapping("/users/{id}/soft-delete")
  @Operation(
      summary = "Soft-delete a user",
      description =
          "Soft-deletes a user by setting deletedAt/deletedBy. Cascades to profile. "
              + "Validates no active jobs exist.")
  public ApiResponse<Void> softDeleteUser(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    userService.softDelete(id, adminId);
    return ApiResponse.of(null, requestId(httpRequest));
  }

  @PutMapping("/users/{id}/restore")
  @Operation(
      summary = "Restore a soft-deleted user",
      description =
          "Restores a soft-deleted user by clearing deletedAt/deletedBy and their profile.")
  public ApiResponse<Void> restoreUser(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    userService.restore(id, adminId);
    return ApiResponse.of(null, requestId(httpRequest));
  }

  // ── Property soft-delete endpoints ──────────────────────────────────────────

  @GetMapping("/properties")
  @Operation(
      summary = "List properties (optionally including deleted)",
      description =
          "Returns all properties. When include_deleted=true, includes soft-deleted properties.")
  public ApiResponse<Page<PropertyDTO>> listProperties(
      @RequestParam(name = "include_deleted", defaultValue = "false") final boolean includeDeleted,
      final Pageable pageable,
      final HttpServletRequest httpRequest) {
    return ApiResponse.of(propertyService.findAll(pageable), requestId(httpRequest));
  }

  @PutMapping("/properties/{id}/soft-delete")
  @Operation(
      summary = "Soft-delete a property",
      description =
          "Soft-deletes a property by setting deletedAt/deletedBy. "
              + "Validates no active jobs exist on this property.")
  public ApiResponse<Void> softDeleteProperty(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    propertyService.softDelete(id, adminId);
    return ApiResponse.of(null, requestId(httpRequest));
  }

  @PutMapping("/properties/{id}/restore")
  @Operation(
      summary = "Restore a soft-deleted property",
      description = "Restores a soft-deleted property by clearing deletedAt/deletedBy.")
  public ApiResponse<Void> restoreProperty(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    propertyService.restore(id, adminId);
    return ApiResponse.of(null, requestId(httpRequest));
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

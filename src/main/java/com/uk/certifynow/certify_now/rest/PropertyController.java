package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.MyPropertiesResponse;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.property.CreatePropertyRequest;
import com.uk.certifynow.certify_now.rest.dto.property.UpdatePropertyRequest;
import com.uk.certifynow.certify_now.service.PropertyService;
import com.uk.certifynow.certify_now.service.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Property management for customers")
public class PropertyController {

  private final PropertyService propertyService;
  private final SseEmitterRegistry sseEmitterRegistry;

  public PropertyController(
      final PropertyService propertyService, final SseEmitterRegistry sseEmitterRegistry) {
    this.propertyService = propertyService;
    this.sseEmitterRegistry = sseEmitterRegistry;
  }

  @PostMapping
  @Operation(
      summary = "Create a new property",
      description =
          "Registers a new property for the authenticated customer."
              + " The property is automatically linked to the current user.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Property created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Only customers can manage properties")
  })
  public ResponseEntity<ApiResponse<PropertyDTO>> createProperty(
      @Valid @RequestBody final CreatePropertyRequest req,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final PropertyDTO created = propertyService.create(req, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(created, requestId(request)));
  }

  @GetMapping
  @Operation(
      summary = "List my properties",
      description = "Returns a paginated list of properties owned by the authenticated customer.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Properties retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Only customers can manage properties")
  })
  public ApiResponse<Page<PropertyDTO>> getMyProperties(
      final Authentication authentication,
      final Pageable pageable,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(propertyService.getByOwner(userId, pageable), requestId(request));
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "SSE stream for property events",
      description =
          "Opens a long-lived Server-Sent Events connection for the authenticated customer."
              + " Receives an 'epc-enriched' event when async EPC enrichment completes"
              + " for a newly created property.")
  public SseEmitter streamEvents(final Authentication authentication) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return sseEmitterRegistry.register(userId);
  }

  @GetMapping("/with-compliance")
  @Operation(
      summary = "List my properties with compliance health",
      description =
          "Returns all active properties with a comprehensive compliance health score"
              + " for the home screen.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Properties and compliance health retrieved"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Only customers can manage properties")
  })
  public ApiResponse<MyPropertiesResponse> getMyPropertiesWithCompliance(
      final Authentication authentication, final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(
        propertyService.getMyPropertiesWithCompliance(userId), requestId(request));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get a property by ID",
      description =
          "Returns details of a specific property. Only the owner can access their property.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Property retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Not the owner of this property"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Property not found")
  })
  public ApiResponse<PropertyDTO> getProperty(
      @Parameter(description = "Property ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(propertyService.getForOwner(id, userId), requestId(request));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Update a property",
      description = "Updates an existing property. Only the owner can update.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Property updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Not the owner of this property"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Property not found")
  })
  public ApiResponse<PropertyDTO> updateProperty(
      @Parameter(description = "Property ID") @PathVariable final UUID id,
      @Valid @RequestBody final UpdatePropertyRequest req,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(propertyService.update(id, req, userId), requestId(request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Deactivate a property",
      description =
          "Soft-deletes a property by setting it as inactive."
              + " Only the owner can deactivate their property.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Property deactivated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Not the owner of this property"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Property not found")
  })
  public void deactivateProperty(
      @Parameter(description = "Property ID") @PathVariable final UUID id,
      final Authentication authentication) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    propertyService.softDelete(id, userId);
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }

  private void ensureCustomer(final Authentication authentication) {
    final boolean customerRole =
        authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> "ROLE_CUSTOMER".equals(grantedAuthority.getAuthority()));
    if (!customerRole) {
      throw new AccessDeniedException("Only customers can manage properties");
    }
  }
}

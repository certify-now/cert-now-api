package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.MyPropertiesResponse;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Property management for customers")
public class PropertyController {

  private final PropertyService propertyService;

  public PropertyController(final PropertyService propertyService) {
    this.propertyService = propertyService;
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
      @Valid @RequestBody final PropertyDTO propertyDTO,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    propertyDTO.setOwner(userId);
    propertyDTO.setIsActive(true);
    final PropertyDTO createdProperty = propertyService.create(propertyDTO);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(createdProperty, requestId(request)));
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
    final PropertyDTO propertyDTO = propertyService.get(id);
    if (propertyDTO.getOwner() == null || !propertyDTO.getOwner().equals(userId)) {
      throw new AccessDeniedException("You do not own this property");
    }
    return ApiResponse.of(propertyDTO, requestId(request));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Update a property",
      description = "Updates an existing property. Only the owner can update their property.")
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
      @Valid @RequestBody final PropertyDTO propertyDTO,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final PropertyDTO existingProperty = propertyService.get(id);
    if (existingProperty.getOwner() == null || !existingProperty.getOwner().equals(userId)) {
      throw new AccessDeniedException("You do not own this property");
    }
    propertyDTO.setId(id);
    propertyDTO.setOwner(userId);
    return ApiResponse.of(propertyService.update(id, propertyDTO), requestId(request));
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
    final PropertyDTO existingProperty = propertyService.get(id);
    if (existingProperty.getOwner() == null || !existingProperty.getOwner().equals(userId)) {
      throw new AccessDeniedException("You do not own this property");
    }
    propertyService.deactivate(id);
  }

  @GetMapping("/with-compliance")
  @Operation(
      summary = "List my properties with live compliance health",
      description =
          "Returns all active properties for the authenticated customer with live-computed"
              + " compliance statuses, expiry countdowns, next actions and an aggregate health score.")
  public ApiResponse<MyPropertiesResponse> getMyPropertiesWithCompliance(
      final Authentication authentication, final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(propertyService.getMyPropertiesWithCompliance(userId), requestId(request));
  }

  @PostMapping("/{id}/gas-certificate")
  @Operation(
      summary = "Upload / update Gas Safety Certificate",
      description =
          "Stores the Gas Safety Certificate PDF and/or updates expiry metadata for a property."
              + " Useful when the landlord declared cert details during registration but hasn't"
              + " uploaded the PDF yet.")
  public ApiResponse<PropertyDTO> uploadGasCertificate(
      @Parameter(description = "Property ID") @PathVariable final UUID id,
      @RequestParam(required = false) final Boolean hasGasCertificate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate gasExpiryDate,
      @RequestParam(required = false) final MultipartFile gasCertPdf,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final PropertyDTO existing = propertyService.get(id);
    if (!userId.equals(existing.getOwner())) {
      throw new AccessDeniedException("You do not own this property");
    }
    return ApiResponse.of(
        propertyService.uploadGasCertificate(id, hasGasCertificate, gasExpiryDate, gasCertPdf),
        requestId(request));
  }

  @PostMapping("/{id}/eicr-certificate")
  @Operation(
      summary = "Upload / update EICR Certificate",
      description =
          "Stores the EICR PDF and/or updates expiry metadata for a property."
              + " Useful when the landlord declared cert details during registration but hasn't"
              + " uploaded the PDF yet.")
  public ApiResponse<PropertyDTO> uploadEicrCertificate(
      @Parameter(description = "Property ID") @PathVariable final UUID id,
      @RequestParam(required = false) final Boolean hasEicr,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate eicrExpiryDate,
      @RequestParam(required = false) final MultipartFile eicrCertPdf,
      final Authentication authentication,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final PropertyDTO existing = propertyService.get(id);
    if (!userId.equals(existing.getOwner())) {
      throw new AccessDeniedException("You do not own this property");
    }
    return ApiResponse.of(
        propertyService.uploadEicrCertificate(id, hasEicr, eicrExpiryDate, eicrCertPdf),
        requestId(request));
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

package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.service.PropertyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

  private final PropertyService propertyService;

  public PropertyController(final PropertyService propertyService) {
    this.propertyService = propertyService;
  }

  @PostMapping
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
  public ApiResponse<Page<PropertyDTO>> getMyProperties(
      final Authentication authentication,
      final Pageable pageable,
      final HttpServletRequest request) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    return ApiResponse.of(propertyService.getByOwner(userId, pageable), requestId(request));
  }

  @GetMapping("/{id}")
  public ApiResponse<PropertyDTO> getProperty(
      @PathVariable final UUID id,
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
  public ApiResponse<Void> updateProperty(
      @PathVariable final UUID id,
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
    propertyService.update(id, propertyDTO);
    return ApiResponse.of(null, requestId(request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateProperty(@PathVariable final UUID id, final Authentication authentication) {
    ensureCustomer(authentication);
    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final PropertyDTO existingProperty = propertyService.get(id);
    if (existingProperty.getOwner() == null || !existingProperty.getOwner().equals(userId)) {
      throw new AccessDeniedException("You do not own this property");
    }
    propertyService.deactivate(id);
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

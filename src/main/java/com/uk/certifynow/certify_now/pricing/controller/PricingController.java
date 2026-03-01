package com.uk.certifynow.certify_now.pricing.controller;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.pricing.dto.PriceBreakdown;
import com.uk.certifynow.certify_now.pricing.service.PricingService;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
@Validated
public class PricingController {

  private final PricingService pricingService;
  private final PropertyRepository propertyRepository;

  public PricingController(
      final PricingService pricingService, final PropertyRepository propertyRepository) {
    this.pricingService = pricingService;
    this.propertyRepository = propertyRepository;
  }

  @GetMapping("/calculate")
  public ApiResponse<PriceBreakdown> calculate(
      @RequestParam("property_id") @NotNull final UUID propertyId,
      @RequestParam("certificate_type") @NotBlank final String certificateType,
      @RequestParam("urgency") @NotBlank final String urgency,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));

    // Verify ownership unless ADMIN
    final boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    if (!isAdmin) {
      final UUID requesterId = UUID.fromString((String) authentication.getPrincipal());
      if (!property.getOwner().getId().equals(requesterId)) {
        throw new BusinessException(
            HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have access to this property");
      }
    }

    // Validate urgency
    final List<String> validUrgencies = List.of("STANDARD", "PRIORITY", "EMERGENCY");
    if (!validUrgencies.contains(urgency)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_URGENCY", "Invalid urgency value: " + urgency);
    }

    // Validate gas supply for GAS_SAFETY
    if ("GAS_SAFETY".equals(certificateType) && Boolean.FALSE.equals(property.getHasGasSupply())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_GAS_SUPPLY",
          "Property does not have a gas supply — GAS_SAFETY certificate cannot be issued");
    }

    final PriceBreakdown breakdown =
        pricingService.calculatePrice(certificateType, property, urgency);

    return ApiResponse.of(breakdown, (String) httpRequest.getAttribute(RequestIdFilter.REQUEST_ID));
  }
}

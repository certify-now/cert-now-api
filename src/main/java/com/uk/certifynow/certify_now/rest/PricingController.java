package com.uk.certifynow.certify_now.pricing.controller;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Pricing", description = "Public pricing information for certificates")
public class PricingController {

  private final PricingService pricingService;
  private final PropertyRepository propertyRepository;

  public PricingController(
      final PricingService pricingService, final PropertyRepository propertyRepository) {
    this.pricingService = pricingService;
    this.propertyRepository = propertyRepository;
  }

  @GetMapping("/calculate")
  @Operation(
      summary = "Calculate certificate price",
      description =
          "Calculates the price for a certificate based on the property attributes,"
              + " certificate type, and urgency level. Returns a detailed price breakdown"
              + " including base price, modifiers, commission, and engineer payout.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Price calculated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description =
            "Validation error (e.g. invalid urgency value or GAS_SAFETY for property without gas supply)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no access to the specified property"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Property not found")
  })
  public ApiResponse<PriceBreakdown> calculate(
      @Parameter(description = "ID of the property to calculate pricing for")
          @RequestParam("property_id")
          @NotNull
          final UUID propertyId,
      @Parameter(description = "Certificate type (e.g. EPC, GAS_SAFETY)")
          @RequestParam("certificate_type")
          @NotBlank
          final String certificateType,
      @Parameter(description = "Urgency level: STANDARD, PRIORITY, or EMERGENCY")
          @RequestParam("urgency")
          @NotBlank
          final String urgency,
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
        pricingService.calculatePrice(
            certificateType,
            property.getPostcode(),
            property.getPropertyType(),
            property.getBedrooms(),
            property.getGasApplianceCount(),
            property.getFloorAreaSqm(),
            urgency);

    return ApiResponse.of(breakdown, (String) httpRequest.getAttribute(RequestIdFilter.REQUEST_ID));
  }
}

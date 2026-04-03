package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.service.pricing.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
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
public class PricingController extends BaseController {

  private final PricingService pricingService;

  public PricingController(final PricingService pricingService) {
    this.pricingService = pricingService;
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
    final UUID requesterId = extractUserId(authentication);
    final boolean isAdmin = extractRole(authentication) == UserRole.ADMIN;
    return ApiResponse.of(
        pricingService.calculatePriceForProperty(
            propertyId, certificateType, urgency, requesterId, isAdmin),
        requestId(httpRequest));
  }
}

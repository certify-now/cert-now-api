package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypesResponse;
import com.uk.certifynow.certify_now.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Booking", description = "Certificate booking flows")
public class BookingController {

  private final PricingService pricingService;

  public BookingController(final PricingService pricingService) {
    this.pricingService = pricingService;
  }

  @GetMapping("/certificate-types")
  @Operation(
      summary = "List bookable certificate types",
      description =
          "Returns all certificate types that currently have an active pricing rule,"
              + " together with a live price range derived from the base price, the"
              + " maximum applicable modifiers, and the highest active urgency multiplier.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Certificate types retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<CertificateTypesResponse> getCertificateTypes(
      final HttpServletRequest request) {
    return ApiResponse.of(
        pricingService.getCertificateTypes(),
        (String) request.getAttribute(RequestIdFilter.REQUEST_ID));
  }
}

package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypeItem;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypesResponse;
import com.uk.certifynow.certify_now.service.PricingService;
import com.uk.certifynow.certify_now.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Booking", description = "Certificate booking flows")
public class BookingController {

  private final PricingService pricingService;
  private final PropertyService propertyService;

  public BookingController(
      final PricingService pricingService, final PropertyService propertyService) {
    this.pricingService = pricingService;
    this.propertyService = propertyService;
  }

  @GetMapping("/certificate-types")
  @Operation(
      summary = "List bookable certificate types",
      description =
          "Returns all certificate types with active pricing rules. When called by an"
              + " authenticated customer, each item is enriched with overdueCount and"
              + " expiringSoonCount drawn from their property portfolio.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Certificate types retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<CertificateTypesResponse> getCertificateTypes(
      final Authentication authentication, final HttpServletRequest request) {

    final CertificateTypesResponse catalogue = pricingService.getCertificateTypes();

    if (authentication == null) {
      return ApiResponse.of(catalogue, requestId(request));
    }

    final UUID userId = UUID.fromString((String) authentication.getPrincipal());
    final List<PropertyDTO> properties =
        propertyService.getMyPropertiesWithCompliance(userId).getProperties();

    final List<CertificateTypeItem> enriched =
        catalogue.certificateTypes().stream()
            .map(item -> enrichWithCounts(item, properties))
            .toList();

    return ApiResponse.of(new CertificateTypesResponse(enriched), requestId(request));
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static CertificateTypeItem enrichWithCounts(
      final CertificateTypeItem item, final List<PropertyDTO> properties) {
    final int overdue;
    final int expiringSoon;

    switch (item.type()) {
      case "GAS_SAFETY" -> {
        overdue = countStatus(properties, "gasStatus", "EXPIRED");
        expiringSoon = countStatus(properties, "gasStatus", "EXPIRING_SOON");
      }
      case "EICR" -> {
        overdue = countStatus(properties, "eicrStatus", "EXPIRED");
        expiringSoon = countStatus(properties, "eicrStatus", "EXPIRING_SOON");
      }
      default -> {
        overdue = 0;
        expiringSoon = 0;
      }
    }

    if (overdue == item.overdueCount() && expiringSoon == item.expiringSoonCount()) {
      return item;
    }
    return new CertificateTypeItem(
        item.type(), item.name(), item.fromPricePence(), item.priceUnit(), overdue, expiringSoon);
  }

  private static int countStatus(
      final List<PropertyDTO> properties, final String field, final String value) {
    return (int)
        properties.stream()
            .filter(
                p -> {
                  final String status =
                      "gasStatus".equals(field) ? p.getGasStatus() : p.getEicrStatus();
                  return value.equals(status);
                })
            .count();
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

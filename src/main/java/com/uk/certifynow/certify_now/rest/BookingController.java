package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypesResponse;
import com.uk.certifynow.certify_now.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Booking", description = "Certificate booking flows")
public class BookingController extends BaseController {

  private final BookingService bookingService;

  public BookingController(final BookingService bookingService) {
    this.bookingService = bookingService;
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
    if (authentication == null) {
      return ApiResponse.of(bookingService.getCertificateTypes(), requestId(request));
    }
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        bookingService.getCertificateTypesForCustomer(userId), requestId(request));
  }
}

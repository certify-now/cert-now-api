package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.address.AddressSuggestionResponse;
import com.uk.certifynow.certify_now.rest.dto.address.ResolvedAddressResponse;
import com.uk.certifynow.certify_now.service.property.AddressLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/address")
@Tag(
    name = "Address Lookup",
    description = "Address autocomplete and UPRN resolution (proxied from Ideal Postcodes)")
public class AddressController extends BaseController {

  private final AddressLookupService addressLookupService;

  public AddressController(final AddressLookupService addressLookupService) {
    this.addressLookupService = addressLookupService;
  }

  @GetMapping("/autocomplete")
  @Operation(
      summary = "Autocomplete address",
      description =
          "Returns up to 10 address suggestions for the given free-text query. Call after ≥3 characters.")
  public ResponseEntity<ApiResponse<List<AddressSuggestionResponse>>> autocomplete(
      @Parameter(
              description = "Partial address or postcode",
              example = "10 Downing",
              required = true)
          @RequestParam
          final String q,
      final HttpServletRequest request) {

    final List<AddressSuggestionResponse> suggestions = addressLookupService.autocomplete(q.trim());
    return ResponseEntity.ok(ApiResponse.of(suggestions, requestId(request)));
  }

  @GetMapping("/resolve/{id}")
  @Operation(
      summary = "Resolve address by id",
      description =
          "Returns a full structured address including UPRN for a suggestion id returned by the autocomplete endpoint.")
  public ResponseEntity<ApiResponse<ResolvedAddressResponse>> resolve(
      @Parameter(
              description = "Suggestion id from autocomplete (e.g. paf_10093397)",
              required = true)
          @PathVariable
          final String id,
      final HttpServletRequest request) {

    final ResolvedAddressResponse resolved = addressLookupService.resolve(id);
    return ResponseEntity.ok(ApiResponse.of(resolved, requestId(request)));
  }
}

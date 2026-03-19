package com.uk.certifynow.certify_now.rest.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single address autocomplete suggestion")
public record AddressSuggestionResponse(
    @Schema(description = "Opaque address identifier — pass to the resolve endpoint", example = "paf_10093397")
    String id,
    @Schema(description = "Human-readable address line for display in the dropdown", example = "10 Downing Street, London, SW1A")
    String suggestion
) {}

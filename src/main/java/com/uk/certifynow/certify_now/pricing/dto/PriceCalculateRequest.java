package com.uk.certifynow.certify_now.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PriceCalculateRequest(
    @NotNull(message = "property_id is required") UUID propertyId,
    @NotBlank(message = "certificate_type is required") String certificateType,
    @NotBlank(message = "urgency is required") String urgency) {}

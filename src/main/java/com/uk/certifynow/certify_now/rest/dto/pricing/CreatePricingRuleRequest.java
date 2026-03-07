package com.uk.certifynow.certify_now.rest.dto.pricing;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreatePricingRuleRequest(
    @NotBlank(message = "certificate_type is required") String certificateType,
    String region,
    @NotNull(message = "base_price_pence is required")
        @Positive(message = "base_price_pence must be > 0")
        Integer basePricePence,
    @NotNull(message = "effective_from is required")
        @FutureOrPresent(message = "effective_from must be today or in the future")
        LocalDate effectiveFrom,
    LocalDate effectiveTo) {}

package com.uk.certifynow.certify_now.rest.dto.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePricingModifierRequest(
    @NotBlank(message = "modifier_type is required") String modifierType,
    BigDecimal conditionMin,
    BigDecimal conditionMax,
    @NotNull(message = "modifier_pence is required")
        @Positive(message = "modifier_pence must be > 0")
        Integer modifierPence) {}

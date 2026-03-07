package com.uk.certifynow.certify_now.rest.dto.pricing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateUrgencyMultiplierRequest(
    @NotNull(message = "multiplier is required")
        @DecimalMin(value = "1.000", message = "multiplier must be >= 1.000")
        @DecimalMax(value = "3.000", message = "multiplier must be <= 3.000")
        BigDecimal multiplier) {}

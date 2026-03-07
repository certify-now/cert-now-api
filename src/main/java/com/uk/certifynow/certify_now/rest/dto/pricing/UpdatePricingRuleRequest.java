package com.uk.certifynow.certify_now.rest.dto.pricing;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record UpdatePricingRuleRequest(
    @Positive(message = "base_price_pence must be > 0") Integer basePricePence,
    String region,
    LocalDate effectiveTo) {}

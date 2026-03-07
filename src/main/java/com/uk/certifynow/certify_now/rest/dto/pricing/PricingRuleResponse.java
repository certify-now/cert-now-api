package com.uk.certifynow.certify_now.rest.dto.pricing;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PricingRuleResponse(
    UUID id,
    String certificateType,
    String region,
    int basePricePence,
    boolean isActive,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    List<PricingModifierResponse> modifiers) {}

package com.uk.certifynow.certify_now.rest.dto.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingModifierResponse(
    UUID id,
    String modifierType,
    BigDecimal conditionMin,
    BigDecimal conditionMax,
    int modifierPence) {}

package com.uk.certifynow.certify_now.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceBreakdown(
    int basePricePence,
    int propertyModifierPence,
    int urgencyModifierPence,
    int discountPence,
    int totalPricePence,
    BigDecimal commissionRate,
    int commissionPence,
    int engineerPayoutPence,
    Breakdown breakdown) {

  public record Breakdown(
      List<ModifierApplied> modifiersApplied, String urgency, BigDecimal urgencyMultiplier) {}

  public record ModifierApplied(String type, String description, int amountPence) {}
}

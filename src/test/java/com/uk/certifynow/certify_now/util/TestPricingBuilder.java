package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.domain.PricingModifier;
import com.uk.certifynow.certify_now.domain.PricingRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TestPricingBuilder {

  private static final OffsetDateTime NOW = TestConstants.FIXED_NOW;

  private TestPricingBuilder() {}

  public static PricingRule buildGasSafetyRule() {
    return buildRule("GAS_SAFETY", null, 9900);
  }

  public static PricingRule buildEpcRule() {
    return buildRule("EPC", null, 14900);
  }

  public static PricingRule buildRegionalGasSafetyRule(final String region) {
    return buildRule("GAS_SAFETY", region, 11000);
  }

  public static PricingModifier buildBedroomModifier(final PricingRule rule) {
    return buildModifier(rule, "BEDROOMS", new BigDecimal("3"), new BigDecimal("5"), 1000);
  }

  public static PricingModifier buildApplianceModifier(final PricingRule rule) {
    return buildModifier(rule, "APPLIANCES", new BigDecimal("2"), new BigDecimal("4"), 500);
  }

  private static PricingRule buildRule(
      final String certType, final String region, final int basePricePence) {
    final PricingRule r = new PricingRule();
    r.setId(UUID.randomUUID());
    r.setCertificateType(certType);
    r.setRegion(region);
    r.setBasePricePence(basePricePence);
    r.setEffectiveFrom(LocalDate.of(2025, 1, 1));
    r.setEffectiveTo(null);
    r.setIsActive(true);
    r.setCreatedAt(NOW);
    return r;
  }

  private static PricingModifier buildModifier(
      final PricingRule rule,
      final String modifierType,
      final BigDecimal conditionMin,
      final BigDecimal conditionMax,
      final int modifierPence) {
    final PricingModifier m = new PricingModifier();
    m.setId(UUID.randomUUID());
    m.setPricingRule(rule);
    m.setModifierType(modifierType);
    m.setConditionMin(conditionMin);
    m.setConditionMax(conditionMax);
    m.setModifierPence(modifierPence);
    m.setCreatedAt(NOW);
    return m;
  }
}

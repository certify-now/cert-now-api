//package com.uk.certifynow.certify_now.pricing.data;
//
//import com.uk.certifynow.certify_now.domain.PricingModifier;
//import com.uk.certifynow.certify_now.domain.PricingRule;
//import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
//import com.uk.certifynow.certify_now.repos.PricingModifierRepository;
//import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
//import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.OffsetDateTime;
//import java.util.List;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//@Component
//@Profile("!test")
//public class PricingDataInitializer implements ApplicationRunner {
//
//  private static final Logger log = LoggerFactory.getLogger(PricingDataInitializer.class);
//  private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 1, 1);
//
//  private final PricingRuleRepository pricingRuleRepository;
//  private final PricingModifierRepository pricingModifierRepository;
//  private final UrgencyMultiplierRepository urgencyMultiplierRepository;
//
//  public PricingDataInitializer(
//      final PricingRuleRepository pricingRuleRepository,
//      final PricingModifierRepository pricingModifierRepository,
//      final UrgencyMultiplierRepository urgencyMultiplierRepository) {
//    this.pricingRuleRepository = pricingRuleRepository;
//    this.pricingModifierRepository = pricingModifierRepository;
//    this.urgencyMultiplierRepository = urgencyMultiplierRepository;
//  }
//
//  @Override
//  @Transactional
//  public void run(final ApplicationArguments args) {
//    if (pricingRuleRepository.count() > 0) {
//      log.info("Pricing seed data already present — skipping initialisation");
//      return;
//    }
//
//    log.info("Seeding pricing rules, modifiers, and urgency multipliers...");
//
//    final PricingRule gasSafety = createRule("GAS_SAFETY", 7500);
//    final PricingRule epc = createRule("EPC", 7000);
//    final PricingRule eicr = createRule("EICR", 15000);
//
//    pricingRuleRepository.saveAll(List.of(gasSafety, epc, eicr));
//
//    seedGasSafetyModifiers(gasSafety);
//    seedEpcModifiers(epc);
//    seedEicrModifiers(eicr);
//
//    seedUrgencyMultipliers();
//
//    log.info("Pricing seed data committed successfully");
//  }
//
//  private PricingRule createRule(final String certType, final int basePricePence) {
//    final PricingRule rule = new PricingRule();
//    rule.setCertificateType(certType);
//    rule.setRegion(null);
//    rule.setBasePricePence(basePricePence);
//    rule.setIsActive(true);
//    rule.setEffectiveFrom(EFFECTIVE_FROM);
//    rule.setEffectiveTo(null);
//    rule.setCreatedAt(OffsetDateTime.now());
//    return rule;
//  }
//
//  private void seedGasSafetyModifiers(final PricingRule rule) {
//    pricingModifierRepository.saveAll(
//        List.of(
//            modifier(rule, "BEDROOMS", bd(3), bd(4), 1000),
//            modifier(rule, "BEDROOMS", bd(4), bd(5), 2000),
//            modifier(rule, "BEDROOMS", bd(5), null, 3000),
//            modifier(rule, "APPLIANCES", bd(3), bd(4), 1000),
//            modifier(rule, "APPLIANCES", bd(4), bd(5), 2000),
//            modifier(rule, "APPLIANCES", bd(5), null, 3000),
//            modifier(rule, "PROPERTY_TYPE_SEMI_DETACHED", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HOUSE", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_DETACHED", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_BUNGALOW", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HMO", null, null, 3000)));
//  }
//
//  private void seedEpcModifiers(final PricingRule rule) {
//    pricingModifierRepository.saveAll(
//        List.of(
//            modifier(rule, "BEDROOMS", bd(3), bd(4), 1500),
//            modifier(rule, "BEDROOMS", bd(4), bd(5), 2500),
//            modifier(rule, "BEDROOMS", bd(5), null, 4000),
//            modifier(rule, "FLOOR_AREA", bd(50), bd(80), 500),
//            modifier(rule, "FLOOR_AREA", bd(80), bd(120), 1500),
//            modifier(rule, "FLOOR_AREA", bd(120), bd(180), 2500),
//            modifier(rule, "FLOOR_AREA", bd(180), null, 4000),
//            modifier(rule, "PROPERTY_TYPE_SEMI_DETACHED", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HOUSE", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_DETACHED", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_BUNGALOW", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HMO", null, null, 3000)));
//  }
//
//  private void seedEicrModifiers(final PricingRule rule) {
//    pricingModifierRepository.saveAll(
//        List.of(
//            modifier(rule, "BEDROOMS", bd(3), bd(4), 3000),
//            modifier(rule, "BEDROOMS", bd(4), bd(5), 6000),
//            modifier(rule, "BEDROOMS", bd(5), null, 10000),
//            modifier(rule, "PROPERTY_TYPE_SEMI_DETACHED", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HOUSE", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_DETACHED", null, null, 1000),
//            modifier(rule, "PROPERTY_TYPE_BUNGALOW", null, null, 500),
//            modifier(rule, "PROPERTY_TYPE_HMO", null, null, 3000)));
//  }
//
//  private void seedUrgencyMultipliers() {
//    if (urgencyMultiplierRepository.count() > 0) {
//      return;
//    }
//    urgencyMultiplierRepository.saveAll(
//        List.of(
//            urgencyMultiplier("STANDARD", new BigDecimal("1.000")),
//            urgencyMultiplier("PRIORITY", new BigDecimal("1.250")),
//            urgencyMultiplier("EMERGENCY", new BigDecimal("1.500"))));
//  }
//
//  private PricingModifier modifier(
//      final PricingRule rule,
//      final String type,
//      final BigDecimal min,
//      final BigDecimal max,
//      final int pence) {
//    final PricingModifier m = new PricingModifier();
//    m.setPricingRule(rule);
//    m.setModifierType(type);
//    m.setConditionMin(min);
//    m.setConditionMax(max);
//    m.setModifierPence(pence);
//    m.setCreatedAt(OffsetDateTime.now());
//    return m;
//  }
//
//  private UrgencyMultiplier urgencyMultiplier(final String urgency, final BigDecimal multiplier) {
//    final UrgencyMultiplier m = new UrgencyMultiplier();
//    m.setUrgency(urgency);
//    m.setMultiplier(multiplier);
//    m.setIsActive(true);
//    m.setEffectiveFrom(EFFECTIVE_FROM);
//    m.setCreatedAt(OffsetDateTime.now());
//    return m;
//  }
//
//  private BigDecimal bd(final int value) {
//    return new BigDecimal(value);
//  }
//}

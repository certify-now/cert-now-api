package com.uk.certifynow.certify_now.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts standard pricing seed data for each test scenario.
 * Mirrors the data from PricingDataInitializer (which is disabled in the test profile).
 */
@Component
public class PricingTestDataSeeder {

  public static final UUID GAS_SAFETY_RULE_ID =
      UUID.fromString("a0000001-0000-0000-0000-000000000001");
  public static final UUID EPC_RULE_ID =
      UUID.fromString("a0000001-0000-0000-0000-000000000002");
  public static final UUID EICR_RULE_ID =
      UUID.fromString("a0000001-0000-0000-0000-000000000003");

  public static final UUID STANDARD_MULTIPLIER_ID =
      UUID.fromString("b0000001-0000-0000-0000-000000000001");
  public static final UUID PRIORITY_MULTIPLIER_ID =
      UUID.fromString("b0000001-0000-0000-0000-000000000002");
  public static final UUID EMERGENCY_MULTIPLIER_ID =
      UUID.fromString("b0000001-0000-0000-0000-000000000003");

  private final JdbcTemplate jdbcTemplate;

  public PricingTestDataSeeder(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Seeds all standard pricing data (rules, modifiers, urgency multipliers). */
  public void seed() {
    final OffsetDateTime now = OffsetDateTime.now();
    final LocalDate today = LocalDate.now();

    insertRule(GAS_SAFETY_RULE_ID, "GAS_SAFETY", null, 7500, today, null, now);
    insertRule(EPC_RULE_ID, "EPC", null, 7000, today, null, now);
    insertRule(EICR_RULE_ID, "EICR", null, 15000, today, null, now);

    seedGasSafetyModifiers(now);
    seedEpcModifiers(now);
    seedEicrModifiers(now);
    seedUrgencyMultipliers(now, today);
  }

  private void seedGasSafetyModifiers(final OffsetDateTime now) {
    // Bedroom brackets: 3-4 → 1000, 4-5 → 2000, 5+ → 3000
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "BEDROOMS",
        new BigDecimal("3"), new BigDecimal("4"), 1000, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "BEDROOMS",
        new BigDecimal("4"), new BigDecimal("5"), 2000, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "BEDROOMS",
        new BigDecimal("5"), null, 3000, now);

    // Appliance brackets: 3-4 → 1000, 4-5 → 2000, 5+ → 3000
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "APPLIANCES",
        new BigDecimal("3"), new BigDecimal("4"), 1000, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "APPLIANCES",
        new BigDecimal("4"), new BigDecimal("5"), 2000, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "APPLIANCES",
        new BigDecimal("5"), null, 3000, now);

    // Property type modifiers
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "PROPERTY_TYPE_SEMI_DETACHED",
        null, null, 500, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "PROPERTY_TYPE_DETACHED",
        null, null, 1000, now);
    insertModifier(UUID.randomUUID(), GAS_SAFETY_RULE_ID, "PROPERTY_TYPE_HMO",
        null, null, 3000, now);
  }

  private void seedEpcModifiers(final OffsetDateTime now) {
    // Bedroom brackets: 3-4 → 1500, 4-5 → 2500, 5+ → 4000
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "BEDROOMS",
        new BigDecimal("3"), new BigDecimal("4"), 1500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "BEDROOMS",
        new BigDecimal("4"), new BigDecimal("5"), 2500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "BEDROOMS",
        new BigDecimal("5"), null, 4000, now);

    // Floor area brackets: 50-80 → 500, 80-120 → 1500, 120-180 → 2500, 180+ → 4000
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "FLOOR_AREA",
        new BigDecimal("50"), new BigDecimal("80"), 500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "FLOOR_AREA",
        new BigDecimal("80"), new BigDecimal("120"), 1500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "FLOOR_AREA",
        new BigDecimal("120"), new BigDecimal("180"), 2500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "FLOOR_AREA",
        new BigDecimal("180"), null, 4000, now);

    // Property type modifiers
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "PROPERTY_TYPE_SEMI_DETACHED",
        null, null, 500, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "PROPERTY_TYPE_DETACHED",
        null, null, 1000, now);
    insertModifier(UUID.randomUUID(), EPC_RULE_ID, "PROPERTY_TYPE_HMO",
        null, null, 3000, now);
  }

  private void seedEicrModifiers(final OffsetDateTime now) {
    // Bedroom brackets: 3-4 → 3000, 4-5 → 6000, 5+ → 10000
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "BEDROOMS",
        new BigDecimal("3"), new BigDecimal("4"), 3000, now);
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "BEDROOMS",
        new BigDecimal("4"), new BigDecimal("5"), 6000, now);
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "BEDROOMS",
        new BigDecimal("5"), null, 10000, now);

    // Property type modifiers
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "PROPERTY_TYPE_SEMI_DETACHED",
        null, null, 500, now);
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "PROPERTY_TYPE_DETACHED",
        null, null, 1000, now);
    insertModifier(UUID.randomUUID(), EICR_RULE_ID, "PROPERTY_TYPE_HMO",
        null, null, 3000, now);
  }

  private void seedUrgencyMultipliers(final OffsetDateTime now, final LocalDate today) {
    insertMultiplier(STANDARD_MULTIPLIER_ID, "STANDARD", new BigDecimal("1.000"), today, now);
    insertMultiplier(PRIORITY_MULTIPLIER_ID, "PRIORITY", new BigDecimal("1.250"), today, now);
    insertMultiplier(EMERGENCY_MULTIPLIER_ID, "EMERGENCY", new BigDecimal("1.500"), today, now);
  }

  private void insertRule(
      final UUID id,
      final String certificateType,
      final String region,
      final int basePricePence,
      final LocalDate effectiveFrom,
      final LocalDate effectiveTo,
      final OffsetDateTime now) {
    jdbcTemplate.update(
        """
        INSERT INTO pricing_rule
          (id, certificate_type, region, base_price_pence, is_active,
           effective_from, effective_to, created_at, date_created, last_updated)
        VALUES (?::uuid, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)
        """,
        id.toString(), certificateType, region, basePricePence,
        effectiveFrom, effectiveTo, now, now, now);
  }

  private void insertModifier(
      final UUID id,
      final UUID ruleId,
      final String modifierType,
      final BigDecimal conditionMin,
      final BigDecimal conditionMax,
      final int modifierPence,
      final OffsetDateTime now) {
    jdbcTemplate.update(
        """
        INSERT INTO pricing_modifier
          (id, pricing_rule_id, modifier_type, condition_min, condition_max,
           modifier_pence, created_at, date_created, last_updated)
        VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
        """,
        id.toString(), ruleId.toString(), modifierType,
        conditionMin, conditionMax, modifierPence, now, now, now);
  }

  private void insertMultiplier(
      final UUID id,
      final String urgency,
      final BigDecimal multiplier,
      final LocalDate effectiveFrom,
      final OffsetDateTime now) {
    jdbcTemplate.update(
        """
        INSERT INTO urgency_multiplier
          (id, urgency, multiplier, is_active, effective_from,
           created_at, date_created, last_updated)
        VALUES (?::uuid, ?, ?, TRUE, ?, ?, ?, ?)
        """,
        id.toString(), urgency, multiplier, effectiveFrom, now, now, now);
  }

  /**
   * Returns all seeded pricing rule IDs indexed by their certificate type.
   */
  public UUID getRuleIdForType(final String certificateType) {
    return switch (certificateType) {
      case "GAS_SAFETY" -> GAS_SAFETY_RULE_ID;
      case "EPC" -> EPC_RULE_ID;
      case "EICR" -> EICR_RULE_ID;
      default -> throw new IllegalArgumentException("Unknown cert type: " + certificateType);
    };
  }

  /**
   * Returns all seeded urgency multiplier IDs.
   */
  public static List<UUID> allMultiplierIds() {
    return List.of(STANDARD_MULTIPLIER_ID, PRIORITY_MULTIPLIER_ID, EMERGENCY_MULTIPLIER_ID);
  }
}

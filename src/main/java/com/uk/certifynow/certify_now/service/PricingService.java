package com.uk.certifynow.certify_now.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uk.certifynow.certify_now.domain.PricingModifier;
import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.domain.enums.AuditAction;
import com.uk.certifynow.certify_now.domain.enums.AuditEntityType;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.repos.PricingModifierRepository;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypeItem;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypesResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingModifierRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.rest.dto.pricing.PricingModifierResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.PricingRuleResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdateUrgencyMultiplierRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.UrgencyMultiplierResponse;
import com.uk.certifynow.certify_now.service.job.ActorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PricingService {

  private static final List<String> VALID_URGENCIES = List.of("STANDARD", "PRIORITY", "EMERGENCY");

  private final PricingRuleRepository pricingRuleRepository;
  private final PricingModifierRepository pricingModifierRepository;
  private final UrgencyMultiplierRepository urgencyMultiplierRepository;
  private final PropertyRepository propertyRepository;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;
  private final BigDecimal commissionRate;
  private final Clock clock;

  public PricingService(
      final PricingRuleRepository pricingRuleRepository,
      final PricingModifierRepository pricingModifierRepository,
      final UrgencyMultiplierRepository urgencyMultiplierRepository,
      final PropertyRepository propertyRepository,
      final AuditLogRepository auditLogRepository,
      final ObjectMapper objectMapper,
      @Value("${app.pricing.commission-rate:0.15}") final BigDecimal commissionRate,
      final Clock clock) {
    this.pricingRuleRepository = pricingRuleRepository;
    this.pricingModifierRepository = pricingModifierRepository;
    this.urgencyMultiplierRepository = urgencyMultiplierRepository;
    this.propertyRepository = propertyRepository;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
    this.commissionRate = commissionRate;
    this.clock = clock;
  }

  // ═══════════════════════════════════════════════════════
  // CORE CALCULATION
  // ═══════════════════════════════════════════════════════

  /**
   * Validates access and business rules for the given property, then calculates the price. Intended
   * for use by controllers that receive a propertyId instead of raw property attributes.
   */
  @Transactional(readOnly = true)
  public PriceBreakdown calculatePriceForProperty(
      final UUID propertyId,
      final String certificateType,
      final String urgency,
      final UUID requesterId,
      final boolean isAdmin) {
    final Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));

    if (!isAdmin && !property.getOwner().getId().equals(requesterId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have access to this property");
    }

    if (!VALID_URGENCIES.contains(urgency)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_URGENCY", "Invalid urgency value: " + urgency);
    }

    if ("GAS_SAFETY".equals(certificateType) && Boolean.FALSE.equals(property.getHasGasSupply())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_GAS_SUPPLY",
          "Property does not have a gas supply — GAS_SAFETY certificate cannot be issued");
    }

    return calculatePrice(
        certificateType,
        property.getPostcode(),
        property.getPropertyType(),
        property.getBedrooms(),
        property.getGasApplianceCount(),
        property.getFloorAreaSqm(),
        urgency);
  }

  @Transactional(readOnly = true)
  @Cacheable(
      value = "pricing-calc",
      key =
          "#certificateType + ':' + #postcode + ':' + #propertyType + ':'"
              + " + (#bedrooms != null ? #bedrooms : 0) + ':'"
              + " + (#gasApplianceCount != null ? #gasApplianceCount : 0) + ':'"
              + " + (#floorAreaSqm != null ? #floorAreaSqm : 0) + ':'"
              + " + #urgency")
  public PriceBreakdown calculatePrice(
      final String certificateType,
      final String postcode,
      final String propertyType,
      final Integer bedrooms,
      final Integer gasApplianceCount,
      final BigDecimal floorAreaSqm,
      final String urgency) {

    // Step 1: Resolve active pricing rule
    final PricingRule rule = resolveActiveRule(certificateType, postcode);

    // Step 2: Evaluate property modifiers
    final List<PriceBreakdown.ModifierApplied> modifiersApplied = new ArrayList<>();
    final List<PricingModifier> modifiers =
        pricingModifierRepository.findByPricingRuleId(rule.getId());
    int propertyModifierPence = 0;

    for (final PricingModifier mod : modifiers) {
      final boolean matches =
          switch (mod.getModifierType()) {
            case "BEDROOMS" -> evaluateBracket(bedrooms, mod);
            case "APPLIANCES" ->
                "GAS_SAFETY".equals(certificateType) && evaluateBracket(gasApplianceCount, mod);
            case "FLOOR_AREA" ->
                "EPC".equals(certificateType) && evaluateBracket(floorAreaSqm, mod);
            default -> mod.getModifierType().equals("PROPERTY_TYPE_" + propertyType);
          };

      if (matches) {
        propertyModifierPence += mod.getModifierPence();
        modifiersApplied.add(
            new PriceBreakdown.ModifierApplied(
                mod.getModifierType(),
                buildModifierDescription(
                    mod, propertyType, bedrooms, gasApplianceCount, floorAreaSqm),
                mod.getModifierPence()));
      }
    }

    // Step 3: Apply urgency multiplier
    final int basePricePence = rule.getBasePricePence();
    final int subtotal = basePricePence + propertyModifierPence;

    final Optional<UrgencyMultiplier> multiplierOpt =
        urgencyMultiplierRepository.findActiveByUrgency(urgency);

    if (multiplierOpt.isEmpty()) {
      log.warn("No active urgency multiplier found for urgency={}, defaulting to 1.000", urgency);
    }

    final BigDecimal multiplierValue =
        multiplierOpt.map(UrgencyMultiplier::getMultiplier).orElse(BigDecimal.ONE);

    final int totalBeforeDiscount =
        new BigDecimal(subtotal)
            .multiply(multiplierValue)
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    final int urgencyModifierPence = totalBeforeDiscount - subtotal;

    // Step 4: Stub discount
    final int discountPence = 0;
    final int totalPricePence = totalBeforeDiscount - discountPence;

    // Step 5: Commission split
    final int commissionPence =
        new BigDecimal(totalPricePence)
            .multiply(commissionRate)
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    final int engineerPayoutPence = totalPricePence - commissionPence;

    // Step 6: Return breakdown
    return new PriceBreakdown(
        basePricePence,
        propertyModifierPence,
        urgencyModifierPence,
        discountPence,
        totalPricePence,
        commissionRate,
        commissionPence,
        engineerPayoutPence,
        new PriceBreakdown.Breakdown(modifiersApplied, urgency, multiplierValue));
  }

  private PricingRule resolveActiveRule(final String certificateType, final String postcode) {
    // For now, extract a simple region from postcode (first word). Regional rules
    // are optional.
    final String region = postcode != null ? postcode.split(" ")[0] : null;

    if (region != null) {
      final Optional<PricingRule> regional =
          pricingRuleRepository.findActiveByTypeAndRegion(certificateType, region);
      if (regional.isPresent()) {
        return regional.get();
      }
    }

    return pricingRuleRepository
        .findNationalDefault(certificateType)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "NO_PRICING_RULE",
                    "No active pricing rule found for certificate type: " + certificateType));
  }

  private boolean evaluateBracket(final Number value, final PricingModifier mod) {
    if (value == null) {
      return false;
    }
    final BigDecimal val = new BigDecimal(value.toString());
    final boolean aboveMin =
        mod.getConditionMin() == null || val.compareTo(mod.getConditionMin()) >= 0;
    final boolean belowMax =
        mod.getConditionMax() == null || val.compareTo(mod.getConditionMax()) < 0;
    return aboveMin && belowMax;
  }

  private String buildModifierDescription(
      final PricingModifier mod,
      final String propertyType,
      final Integer bedrooms,
      final Integer gasApplianceCount,
      final BigDecimal floorAreaSqm) {
    return switch (mod.getModifierType()) {
      case "BEDROOMS" -> bedrooms + " bedrooms";
      case "APPLIANCES" -> gasApplianceCount + " gas appliances";
      case "FLOOR_AREA" -> (floorAreaSqm != null ? floorAreaSqm : "?") + " sqm";
      default ->
          mod.getModifierType().replace("PROPERTY_TYPE_", "").replace("_", " ").toLowerCase()
              + " property";
    };
  }

  // ═══════════════════════════════════════════════════════
  // ADMIN — READ
  // ═══════════════════════════════════════════════════════

  @Transactional(readOnly = true)
  @Cacheable(value = "pricing-rules")
  public List<PricingRuleResponse> getActivePricingRules(final boolean activeOnly) {
    final List<PricingRule> rules =
        activeOnly ? pricingRuleRepository.findByIsActiveTrue() : pricingRuleRepository.findAll();
    return rules.stream().map(this::toRuleResponse).toList();
  }

  @Transactional(readOnly = true)
  public PricingRuleResponse getPricingRule(final UUID id) {
    final PricingRule rule =
        pricingRuleRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pricing rule not found: " + id));
    return toRuleResponse(rule);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "urgency-multipliers")
  public List<UrgencyMultiplierResponse> getActiveUrgencyMultipliers() {
    return urgencyMultiplierRepository.findByIsActiveTrue().stream()
        .map(this::toMultiplierResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "certificate-types", key = "T(java.time.LocalDate).now().toString()")
  public CertificateTypesResponse getCertificateTypes() {
    final List<PricingRule> activeRules = pricingRuleRepository.findAllActiveNationalForToday();

    // Deduplicate: keep the most recent rule per certificate type
    final Map<String, PricingRule> ruleByType = new LinkedHashMap<>();
    for (final PricingRule rule : activeRules) {
      ruleByType.putIfAbsent(rule.getCertificateType(), rule);
    }

    final List<CertificateTypeItem> items =
        CERTIFICATE_TYPE_ORDER.stream()
            .filter(ruleByType::containsKey)
            .map(type -> toCertificateTypeItem(type, ruleByType.get(type)))
            .toList();

    return new CertificateTypesResponse(items);
  }

  private CertificateTypeItem toCertificateTypeItem(final String type, final PricingRule rule) {
    final CertificateTypeMeta meta = CERTIFICATE_TYPE_META.get(type);
    final String name = meta != null ? meta.name() : type;
    final String priceUnit = "PAT".equals(type) ? "PER_ITEM" : "FLAT";
    return new CertificateTypeItem(type, name, rule.getBasePricePence(), priceUnit, 0, 0);
  }

  // Canonical display order and static metadata for certificate types
  private static final List<String> CERTIFICATE_TYPE_ORDER =
      List.of("GAS_SAFETY", "EICR", "EPC", "PAT", "BOILER_SERVICE");

  private record CertificateTypeMeta(String name, String description) {}

  private static final Map<String, CertificateTypeMeta> CERTIFICATE_TYPE_META =
      Map.of(
          "GAS_SAFETY", new CertificateTypeMeta("Gas Safety", "Annual gas safety inspection"),
          "EICR", new CertificateTypeMeta("EICR", "Electrical safety inspection"),
          "EPC", new CertificateTypeMeta("EPC", "Energy performance certificate"),
          "PAT", new CertificateTypeMeta("PAT", "Portable appliance testing"),
          "BOILER_SERVICE",
              new CertificateTypeMeta("Boiler Service", "Annual boiler service & inspection"));

  // ═══════════════════════════════════════════════════════
  // ADMIN — WRITE (all evict caches)
  // ═══════════════════════════════════════════════════════

  @Transactional
  @CacheEvict(
      value = {"pricing-rules", "pricing-calc", "urgency-multipliers", "certificate-types"},
      allEntries = true)
  public PricingRuleResponse createPricingRule(final CreatePricingRuleRequest request) {
    final LocalDate newFrom = request.effectiveFrom();
    final LocalDate newTo = request.effectiveTo();

    // Find any existing active rules for same cert-type + region
    final List<PricingRule> existing =
        pricingRuleRepository.findByCertificateTypeAndRegion(
            request.certificateType(), request.region());

    for (final PricingRule existingRule : existing) {
      final LocalDate exFrom = existingRule.getEffectiveFrom();
      final LocalDate exTo = existingRule.getEffectiveTo();

      // Rule succession: if the existing rule is open-ended (no effective_to) and
      // the new rule starts strictly after the existing rule, terminate the existing
      // rule at the day before the new one starts.
      if (exTo == null && newFrom.isAfter(exFrom)) {
        existingRule.setEffectiveTo(newFrom.minusDays(1));
        pricingRuleRepository.save(existingRule);
        continue; // succession applied — no longer an overlap
      }

      // For any remaining overlap, reject
      if (datesOverlap(newFrom, newTo, exFrom, exTo)) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "PRICING_RULE_OVERLAP",
            "A pricing rule already exists for this certificate type and region with overlapping dates");
      }
    }

    final PricingRule rule = new PricingRule();
    rule.setCertificateType(request.certificateType());
    rule.setRegion(request.region());
    rule.setBasePricePence(request.basePricePence());
    rule.setEffectiveFrom(newFrom);
    rule.setEffectiveTo(newTo);
    rule.setIsActive(true);
    rule.setCreatedAt(OffsetDateTime.now(clock));

    final PricingRule saved = pricingRuleRepository.save(rule);
    final PricingRuleResponse response = toRuleResponse(saved);
    log.info(
        "Pricing rule created for type={} region={} base={}",
        request.certificateType(),
        request.region(),
        request.basePricePence());

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.PRICING_RULE_CREATED,
            AuditEntityType.PricingRule,
            saved.getId(),
            null,
            toJson(
                Map.of(
                    "certificateType", request.certificateType(),
                    "region", String.valueOf(request.region()),
                    "basePricePence", request.basePricePence()))));

    return response;
  }

  @Transactional
  @CacheEvict(
      value = {"pricing-rules", "pricing-calc", "urgency-multipliers", "certificate-types"},
      allEntries = true)
  public PricingRuleResponse updatePricingRule(
      final UUID id, final UpdatePricingRuleRequest request) {
    final PricingRule rule =
        pricingRuleRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pricing rule not found: " + id));

    final String oldValues =
        toJson(
            Map.of(
                "basePricePence", rule.getBasePricePence(),
                "region", String.valueOf(rule.getRegion()),
                "effectiveTo", String.valueOf(rule.getEffectiveTo())));

    if (request.basePricePence() != null) {
      rule.setBasePricePence(request.basePricePence());
    }
    if (request.region() != null) {
      rule.setRegion(request.region());
    }
    if (request.effectiveTo() != null) {
      rule.setEffectiveTo(request.effectiveTo());
    }

    log.info("Pricing rule {} updated", id);
    final PricingRule saved = pricingRuleRepository.save(rule);

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.PRICING_RULE_UPDATED,
            AuditEntityType.PricingRule,
            id,
            oldValues,
            toJson(
                Map.of(
                    "basePricePence", saved.getBasePricePence(),
                    "region", String.valueOf(saved.getRegion()),
                    "effectiveTo", String.valueOf(saved.getEffectiveTo())))));

    return toRuleResponse(saved);
  }

  @Transactional
  @CacheEvict(
      value = {"pricing-rules", "pricing-calc", "urgency-multipliers", "certificate-types"},
      allEntries = true)
  public PricingRuleResponse addModifier(
      final UUID ruleId, final CreatePricingModifierRequest request) {
    validateModifierType(request.modifierType());
    final PricingRule rule =
        pricingRuleRepository
            .findById(ruleId)
            .orElseThrow(() -> new EntityNotFoundException("Pricing rule not found: " + ruleId));

    // Check bracket overlap for numeric modifier types
    final boolean isBracketType =
        "BEDROOMS".equals(request.modifierType())
            || "APPLIANCES".equals(request.modifierType())
            || "FLOOR_AREA".equals(request.modifierType());
    if (isBracketType) {
      final List<PricingModifier> existing =
          pricingModifierRepository.findByPricingRuleId(ruleId).stream()
              .filter(m -> m.getModifierType().equals(request.modifierType()))
              .toList();
      for (final PricingModifier existing1 : existing) {
        if (bracketsOverlap(
            request.conditionMin(), request.conditionMax(),
            existing1.getConditionMin(), existing1.getConditionMax())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_MODIFIER_OVERLAP",
              "A modifier of type "
                  + request.modifierType()
                  + " already exists with overlapping bracket conditions");
        }
      }
    }

    final PricingModifier modifier = new PricingModifier();
    modifier.setModifierType(request.modifierType());
    modifier.setConditionMin(request.conditionMin());
    modifier.setConditionMax(request.conditionMax());
    modifier.setModifierPence(request.modifierPence());
    modifier.setCreatedAt(OffsetDateTime.now(clock));
    modifier.setPricingRule(rule);

    pricingModifierRepository.save(modifier);
    pricingModifierRepository.flush();

    // Re-fetch modifiers explicitly from the repository to avoid Hibernate
    // first-level cache returning a stale version of the rule's collection.
    final PricingRule reloaded = pricingRuleRepository.findById(ruleId).orElseThrow();
    final List<PricingModifierResponse> modifierResponses =
        pricingModifierRepository.findByPricingRuleId(ruleId).stream()
            .map(
                m ->
                    new PricingModifierResponse(
                        m.getId(),
                        m.getModifierType(),
                        m.getConditionMin(),
                        m.getConditionMax(),
                        m.getModifierPence()))
            .toList();
    return new PricingRuleResponse(
        reloaded.getId(),
        reloaded.getCertificateType(),
        reloaded.getRegion(),
        reloaded.getBasePricePence(),
        Boolean.TRUE.equals(reloaded.getIsActive()),
        reloaded.getEffectiveFrom(),
        reloaded.getEffectiveTo(),
        modifierResponses);
  }

  @Transactional
  @CacheEvict(
      value = {"pricing-rules", "pricing-calc", "urgency-multipliers", "certificate-types"},
      allEntries = true)
  public void removeModifier(final UUID ruleId, final UUID modifierId) {
    final PricingModifier modifier =
        pricingModifierRepository
            .findById(modifierId)
            .orElseThrow(
                () -> new EntityNotFoundException("Pricing modifier not found: " + modifierId));

    if (!modifier.getPricingRule().getId().equals(ruleId)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "MODIFIER_RULE_MISMATCH", "Modifier does not belong to rule");
    }

    pricingModifierRepository.delete(modifier);
    log.info("Modifier {} removed from rule {}", modifierId, ruleId);
  }

  @Transactional
  @CacheEvict(
      value = {"pricing-rules", "pricing-calc", "urgency-multipliers", "certificate-types"},
      allEntries = true)
  public UrgencyMultiplierResponse updateUrgencyMultiplier(
      final UUID id, final UpdateUrgencyMultiplierRequest request) {
    final UrgencyMultiplier multiplier =
        urgencyMultiplierRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Urgency multiplier not found: " + id));

    final String oldValues = toJson(Map.of("multiplier", multiplier.getMultiplier()));

    multiplier.setMultiplier(request.multiplier());

    log.info("Urgency multiplier {} updated to {}", id, request.multiplier());
    final UrgencyMultiplier saved = urgencyMultiplierRepository.save(multiplier);

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.URGENCY_MULTIPLIER_UPDATED,
            AuditEntityType.UrgencyMultiplier,
            id,
            oldValues,
            toJson(Map.of("multiplier", saved.getMultiplier()))));

    return toMultiplierResponse(saved);
  }

  // ═══════════════════════════════════════════════════════
  // AUDIT HELPERS
  // ═══════════════════════════════════════════════════════

  private UUID currentActorId() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof String principal) {
      try {
        return UUID.fromString(principal);
      } catch (final IllegalArgumentException ignored) {
        // principal is not a UUID (e.g. "anonymousUser")
      }
    }
    return null;
  }

  private String toJson(final Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final Exception e) {
      log.warn("Failed to serialize audit values", e);
      return null;
    }
  }

  // ═══════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════

  private boolean datesOverlap(
      final LocalDate start1, final LocalDate end1, final LocalDate start2, final LocalDate end2) {
    final LocalDate effectiveEnd1 = end1 != null ? end1 : LocalDate.of(9999, 12, 31);
    final LocalDate effectiveEnd2 = end2 != null ? end2 : LocalDate.of(9999, 12, 31);
    return !start1.isAfter(effectiveEnd2) && !start2.isAfter(effectiveEnd1);
  }

  private void validateModifierType(final String modifierType) {
    if (modifierType == null) {
      return;
    }
    final boolean valid =
        "BEDROOMS".equals(modifierType)
            || "APPLIANCES".equals(modifierType)
            || "FLOOR_AREA".equals(modifierType)
            || modifierType.startsWith("PROPERTY_TYPE_");
    if (!valid) {
      // 422 Unprocessable Entity: the value is syntactically valid JSON but
      // semantically wrong
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "INVALID_MODIFIER_TYPE",
          "modifier_type must be BEDROOMS, APPLIANCES, FLOOR_AREA, or start with PROPERTY_TYPE_");
    }
  }

  private boolean bracketsOverlap(
      final BigDecimal min1, final BigDecimal max1, final BigDecimal min2, final BigDecimal max2) {
    // Special case: two open-ended brackets [a,∞) and [b,∞).
    // Treat as non-overlapping when the incoming bracket starts at or above the
    // existing one,
    // which allows admins to "refine" the top bracket (e.g. add 7+ alongside
    // existing 5+).
    if (max1 == null && max2 == null) {
      final BigDecimal low1 = min1 != null ? min1 : BigDecimal.ZERO;
      final BigDecimal low2 = min2 != null ? min2 : BigDecimal.ZERO;
      // Only overlap if the new lower bound is strictly below the existing one
      return low1.compareTo(low2) < 0;
    }
    final BigDecimal high1 = max1 != null ? max1 : new BigDecimal("999999");
    final BigDecimal high2 = max2 != null ? max2 : new BigDecimal("999999");
    final BigDecimal low1 = min1 != null ? min1 : BigDecimal.ZERO;
    final BigDecimal low2 = min2 != null ? min2 : BigDecimal.ZERO;
    return low1.compareTo(high2) < 0 && low2.compareTo(high1) < 0;
  }

  private PricingRuleResponse toRuleResponse(final PricingRule rule) {
    final List<PricingModifierResponse> modifierResponses =
        rule.getPricingRulePricingModifiers().stream()
            .map(
                m ->
                    new PricingModifierResponse(
                        m.getId(),
                        m.getModifierType(),
                        m.getConditionMin(),
                        m.getConditionMax(),
                        m.getModifierPence()))
            .toList();

    return new PricingRuleResponse(
        rule.getId(),
        rule.getCertificateType(),
        rule.getRegion(),
        rule.getBasePricePence(),
        Boolean.TRUE.equals(rule.getIsActive()),
        rule.getEffectiveFrom(),
        rule.getEffectiveTo(),
        modifierResponses);
  }

  private UrgencyMultiplierResponse toMultiplierResponse(final UrgencyMultiplier m) {
    return new UrgencyMultiplierResponse(
        m.getId(),
        m.getUrgency(),
        m.getMultiplier(),
        Boolean.TRUE.equals(m.getIsActive()),
        m.getEffectiveFrom());
  }
}

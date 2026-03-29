package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.PricingModifier;
import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.PricingModifierRepository;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestPricingBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private PricingRuleRepository pricingRuleRepository;
  @Mock private PricingModifierRepository pricingModifierRepository;
  @Mock private UrgencyMultiplierRepository urgencyMultiplierRepository;
  @Mock private PropertyRepository propertyRepository;

  private PricingService service;

  @BeforeEach
  void setUp() {
    service =
        new PricingService(
            pricingRuleRepository,
            pricingModifierRepository,
            urgencyMultiplierRepository,
            propertyRepository,
            new BigDecimal("0.200"),
            clock);
  }

  @Test
  void calculatePrice_nationalRule_basePriceWithNoModifiers() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    when(pricingRuleRepository.findActiveByTypeAndRegion("GAS_SAFETY", "SW1A"))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("GAS_SAFETY")).thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    final PriceBreakdown result =
        service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD");

    assertThat(result.basePricePence()).isEqualTo(9900);
    assertThat(result.propertyModifierPence()).isEqualTo(0);
    assertThat(result.urgencyModifierPence()).isEqualTo(0);
    assertThat(result.totalPricePence()).isEqualTo(9900);
    assertThat(result.commissionPence()).isEqualTo(1980); // 20% of 9900
    assertThat(result.engineerPayoutPence()).isEqualTo(7920);
  }

  @Test
  void calculatePrice_regionalRuleTakesPrecedenceOverNational() {
    final PricingRule national = TestPricingBuilder.buildGasSafetyRule();
    final PricingRule regional = TestPricingBuilder.buildRegionalGasSafetyRule("SW1A");

    when(pricingRuleRepository.findActiveByTypeAndRegion("GAS_SAFETY", "SW1A"))
        .thenReturn(Optional.of(regional));
    when(pricingModifierRepository.findByPricingRuleId(regional.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    final PriceBreakdown result =
        service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD");

    assertThat(result.basePricePence()).isEqualTo(11000);
  }

  @Test
  void calculatePrice_bedroomModifierApplied() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    final PricingModifier modifier = TestPricingBuilder.buildBedroomModifier(rule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("GAS_SAFETY")).thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of(modifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // 3 bedrooms triggers the modifier (conditionMin=3, conditionMax=5)
    final PriceBreakdown result =
        service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 3, 1, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(1000);
    assertThat(result.totalPricePence()).isEqualTo(9900 + 1000);
  }

  @Test
  void calculatePrice_applianceModifier_onlyForGasSafety() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    final PricingModifier modifier = TestPricingBuilder.buildApplianceModifier(rule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("GAS_SAFETY")).thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of(modifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // 2 appliances triggers the APPLIANCES modifier (conditionMin=2, conditionMax=4)
    final PriceBreakdown result =
        service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 2, 2, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(500);
  }

  @Test
  void calculatePrice_applianceModifier_notAppliedForEpc() {
    final PricingRule epcRule = TestPricingBuilder.buildEpcRule();
    final PricingModifier applianceModifier = TestPricingBuilder.buildApplianceModifier(epcRule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("EPC")).thenReturn(Optional.of(epcRule));
    when(pricingModifierRepository.findByPricingRuleId(epcRule.getId()))
        .thenReturn(List.of(applianceModifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // APPLIANCES modifier should NOT apply for EPC cert type
    final PriceBreakdown result =
        service.calculatePrice("EPC", "SW1A 1AA", "FLAT", 2, 3, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(0);
  }

  @Test
  void calculatePrice_urgencyMultiplierApplied() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("GAS_SAFETY")).thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("URGENT"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("URGENT", new BigDecimal("1.500"))));

    final PriceBreakdown result =
        service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 2, 1, null, "URGENT");

    assertThat(result.urgencyModifierPence()).isEqualTo(4950); // 9900 * 0.5
    assertThat(result.totalPricePence()).isEqualTo(14850); // 9900 * 1.5
  }

  @Test
  void calculatePrice_noActivePricingRule_throwsBadRequest() {
    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault("GAS_SAFETY")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.calculatePrice("GAS_SAFETY", "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createPricingRule_overlappingDates_throwsConflict() {
    final PricingRule existing = TestPricingBuilder.buildGasSafetyRule();
    existing.setEffectiveFrom(LocalDate.of(2025, 1, 1));
    existing.setEffectiveTo(LocalDate.of(2025, 12, 31));

    when(pricingRuleRepository.findByCertificateTypeAndRegion("GAS_SAFETY", null))
        .thenReturn(List.of(existing));

    final CreatePricingRuleRequest req =
        new CreatePricingRuleRequest(
            "GAS_SAFETY", null, 12000, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 8, 31));

    assertThatThrownBy(() -> service.createPricingRule(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("PRICING_RULE_OVERLAP");
  }

  @Test
  void createPricingRule_openEndedExistingRule_terminatedBeforeNewOne() {
    final PricingRule existing = TestPricingBuilder.buildGasSafetyRule();
    existing.setEffectiveFrom(LocalDate.of(2025, 1, 1));
    existing.setEffectiveTo(null); // open-ended

    when(pricingRuleRepository.findByCertificateTypeAndRegion("GAS_SAFETY", null))
        .thenReturn(List.of(existing));
    when(pricingRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreatePricingRuleRequest req =
        new CreatePricingRuleRequest("GAS_SAFETY", null, 12000, LocalDate.of(2026, 1, 1), null);

    service.createPricingRule(req);

    // Existing rule should be terminated at day before new rule starts
    assertThat(existing.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  private UrgencyMultiplier buildUrgencyMultiplier(
      final String urgency, final BigDecimal multiplier) {
    final UrgencyMultiplier um = new UrgencyMultiplier();
    um.setId(UUID.randomUUID());
    um.setUrgency(urgency);
    um.setMultiplier(multiplier);
    um.setIsActive(true);
    um.setCreatedAt(OffsetDateTime.now(clock));
    return um;
  }
}

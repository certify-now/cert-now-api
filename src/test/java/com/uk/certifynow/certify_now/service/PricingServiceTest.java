package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uk.certifynow.certify_now.domain.AuditLog;
import com.uk.certifynow.certify_now.domain.PricingModifier;
import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.repos.PricingModifierRepository;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdateUrgencyMultiplierRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
  @Mock private AuditLogRepository auditLogRepository;
  @Captor private ArgumentCaptor<AuditLog> auditLogCaptor;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private PricingService service;

  @BeforeEach
  void setUp() {
    service =
        new PricingService(
            pricingRuleRepository,
            pricingModifierRepository,
            urgencyMultiplierRepository,
            propertyRepository,
            auditLogRepository,
            objectMapper,
            new BigDecimal("0.200"),
            clock);
  }

  @Test
  void calculatePrice_nationalRule_basePriceWithNoModifiers() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    when(pricingRuleRepository.findActiveByTypeAndRegion(CertificateType.GAS_SAFETY.name(), "SW1A"))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.GAS_SAFETY.name()))
        .thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD");

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

    when(pricingRuleRepository.findActiveByTypeAndRegion(CertificateType.GAS_SAFETY.name(), "SW1A"))
        .thenReturn(Optional.of(regional));
    when(pricingModifierRepository.findByPricingRuleId(regional.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD");

    assertThat(result.basePricePence()).isEqualTo(11000);
  }

  @Test
  void calculatePrice_bedroomModifierApplied() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    final PricingModifier modifier = TestPricingBuilder.buildBedroomModifier(rule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.GAS_SAFETY.name()))
        .thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of(modifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // 3 bedrooms triggers the modifier (conditionMin=3, conditionMax=5)
    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 3, 1, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(1000);
    assertThat(result.totalPricePence()).isEqualTo(9900 + 1000);
  }

  @Test
  void calculatePrice_applianceModifier_onlyForGasSafety() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();
    final PricingModifier modifier = TestPricingBuilder.buildApplianceModifier(rule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.GAS_SAFETY.name()))
        .thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of(modifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // 2 appliances triggers the APPLIANCES modifier (conditionMin=2, conditionMax=4)
    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 2, 2, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(500);
  }

  @Test
  void calculatePrice_applianceModifier_notAppliedForEpc() {
    final PricingRule epcRule = TestPricingBuilder.buildEpcRule();
    final PricingModifier applianceModifier = TestPricingBuilder.buildApplianceModifier(epcRule);

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.EPC.name()))
        .thenReturn(Optional.of(epcRule));
    when(pricingModifierRepository.findByPricingRuleId(epcRule.getId()))
        .thenReturn(List.of(applianceModifier));
    when(urgencyMultiplierRepository.findActiveByUrgency("STANDARD"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"))));

    // APPLIANCES modifier should NOT apply for EPC cert type
    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.EPC.name(), "SW1A 1AA", "FLAT", 2, 3, null, "STANDARD");

    assertThat(result.propertyModifierPence()).isEqualTo(0);
  }

  @Test
  void calculatePrice_urgencyMultiplierApplied() {
    final PricingRule rule = TestPricingBuilder.buildGasSafetyRule();

    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.GAS_SAFETY.name()))
        .thenReturn(Optional.of(rule));
    when(pricingModifierRepository.findByPricingRuleId(rule.getId())).thenReturn(List.of());
    when(urgencyMultiplierRepository.findActiveByUrgency("URGENT"))
        .thenReturn(Optional.of(buildUrgencyMultiplier("URGENT", new BigDecimal("1.500"))));

    final PriceBreakdown result =
        service.calculatePrice(
            CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 2, 1, null, "URGENT");

    assertThat(result.urgencyModifierPence()).isEqualTo(4950); // 9900 * 0.5
    assertThat(result.totalPricePence()).isEqualTo(14850); // 9900 * 1.5
  }

  @Test
  void calculatePrice_noActivePricingRule_throwsBadRequest() {
    when(pricingRuleRepository.findActiveByTypeAndRegion(anyString(), any()))
        .thenReturn(Optional.empty());
    when(pricingRuleRepository.findNationalDefault(CertificateType.GAS_SAFETY.name()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.calculatePrice(
                    CertificateType.GAS_SAFETY.name(), "SW1A 1AA", "FLAT", 2, 1, null, "STANDARD"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createPricingRule_overlappingDates_throwsConflict() {
    final PricingRule existing = TestPricingBuilder.buildGasSafetyRule();
    existing.setEffectiveFrom(LocalDate.of(2025, 1, 1));
    existing.setEffectiveTo(LocalDate.of(2025, 12, 31));

    when(pricingRuleRepository.findByCertificateTypeAndRegion(
            CertificateType.GAS_SAFETY.name(), null))
        .thenReturn(List.of(existing));

    final CreatePricingRuleRequest req =
        new CreatePricingRuleRequest(
            CertificateType.GAS_SAFETY.name(),
            null,
            12000,
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 8, 31));

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

    when(pricingRuleRepository.findByCertificateTypeAndRegion(
            CertificateType.GAS_SAFETY.name(), null))
        .thenReturn(List.of(existing));
    when(pricingRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreatePricingRuleRequest req =
        new CreatePricingRuleRequest(
            CertificateType.GAS_SAFETY.name(), null, 12000, LocalDate.of(2026, 1, 1), null);

    service.createPricingRule(req);

    // Existing rule should be terminated at day before new rule starts
    assertThat(existing.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  void createPricingRule_savesAuditLog() {
    when(pricingRuleRepository.findByCertificateTypeAndRegion(
            CertificateType.GAS_SAFETY.name(), null))
        .thenReturn(List.of());
    when(pricingRuleRepository.save(any()))
        .thenAnswer(
            inv -> {
              final PricingRule r = inv.getArgument(0);
              if (r.getId() == null) {
                r.setId(UUID.randomUUID());
              }
              return r;
            });

    final CreatePricingRuleRequest req =
        new CreatePricingRuleRequest(
            CertificateType.GAS_SAFETY.name(), null, 9900, LocalDate.of(2026, 1, 1), null);

    service.createPricingRule(req);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("PRICING_RULE_CREATED");
    assertThat(log.getEntityType()).isEqualTo("PricingRule");
    assertThat(log.getActorType()).isEqualTo("ADMIN");
    assertThat(log.getNewValues()).contains("GAS_SAFETY");
    assertThat(log.getOldValues()).isNull();
  }

  @Test
  void updatePricingRule_capturesOldAndNewValues() {
    final PricingRule existing = TestPricingBuilder.buildGasSafetyRule();
    when(pricingRuleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(pricingRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final UpdatePricingRuleRequest req = new UpdatePricingRuleRequest(12000, null, null);
    service.updatePricingRule(existing.getId(), req);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("PRICING_RULE_UPDATED");
    assertThat(log.getEntityType()).isEqualTo("PricingRule");
    assertThat(log.getOldValues()).contains("9900");
    assertThat(log.getNewValues()).contains("12000");
  }

  @Test
  void updateUrgencyMultiplier_capturesOldAndNewValues() {
    final UrgencyMultiplier multiplier =
        buildUrgencyMultiplier("STANDARD", new BigDecimal("1.000"));
    when(urgencyMultiplierRepository.findById(multiplier.getId()))
        .thenReturn(Optional.of(multiplier));
    when(urgencyMultiplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final UpdateUrgencyMultiplierRequest req =
        new UpdateUrgencyMultiplierRequest(new BigDecimal("1.500"));
    service.updateUrgencyMultiplier(multiplier.getId(), req);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("URGENCY_MULTIPLIER_UPDATED");
    assertThat(log.getEntityType()).isEqualTo("UrgencyMultiplier");
    assertThat(log.getOldValues()).contains("1.000");
    assertThat(log.getNewValues()).contains("1.500");
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

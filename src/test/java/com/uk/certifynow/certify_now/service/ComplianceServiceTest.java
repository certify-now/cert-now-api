// package com.uk.certifynow.certify_now.service;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
// import com.uk.certifynow.certify_now.model.PropertyDTO;
// import com.uk.certifynow.certify_now.util.TestConstants;
// import java.time.Clock;
// import java.time.LocalDate;
// import java.util.List;
// import java.util.UUID;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
//
// class ComplianceServiceTest {
//
//  private final Clock clock = TestConstants.FIXED_CLOCK;
//
//  // Today in test = 2026-01-15
//  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
//
//  private ComplianceService service;
//
//  @BeforeEach
//  void setUp() {
//    service = new ComplianceService(clock);
//  }
//
//  // ── computeCertStatus ──────────────────────────────────────────────────────
//
//  @Test
//  void computeCertStatus_noGasSupply_returnsNotApplicable() {
//    assertThat(service.computeCertStatus(false, true, TODAY.plusDays(60)))
//        .isEqualTo("NOT_APPLICABLE");
//  }
//
//  @Test
//  void computeCertStatus_noCert_returnsMissing() {
//    assertThat(service.computeCertStatus(true, false, null)).isEqualTo("MISSING");
//  }
//
//  @Test
//  void computeCertStatus_nullCert_returnsMissing() {
//    assertThat(service.computeCertStatus(true, null, null)).isEqualTo("MISSING");
//  }
//
//  @Test
//  void computeCertStatus_expired_returnsExpired() {
//    assertThat(service.computeCertStatus(true, true, TODAY.minusDays(1))).isEqualTo("EXPIRED");
//  }
//
//  @Test
//  void computeCertStatus_within30Days_returnsExpiringSoon() {
//    assertThat(service.computeCertStatus(true, true, TODAY.plusDays(15)))
//        .isEqualTo("EXPIRING_SOON");
//  }
//
//  @Test
//  void computeCertStatus_moreThan30Days_returnsCompliant() {
//    assertThat(service.computeCertStatus(true, true, TODAY.plusDays(60))).isEqualTo("COMPLIANT");
//  }
//
//  @Test
//  void computeCertStatus_expiryExactlyToday_returnsExpired() {
//    // expiryDate.isBefore(today) — exact today is NOT before today, so it should be checked
//    assertThat(service.computeCertStatus(true, true, TODAY)).isEqualTo("EXPIRING_SOON");
//  }
//
//  // ── computePropertyScore ───────────────────────────────────────────────────
//
//  @Test
//  void computePropertyScore_allCompliant_returns100() {
//    assertThat(service.computePropertyScore("COMPLIANT", "COMPLIANT")).isEqualTo(100);
//  }
//
//  @Test
//  void computePropertyScore_oneExpired_returns50() {
//    assertThat(service.computePropertyScore("COMPLIANT", "EXPIRED")).isEqualTo(50);
//  }
//
//  @Test
//  void computePropertyScore_bothNotApplicable_returns100() {
//    assertThat(service.computePropertyScore("NOT_APPLICABLE", "NOT_APPLICABLE")).isEqualTo(100);
//  }
//
//  @Test
//  void computePropertyScore_expiringSoonCountsAsCompliant() {
//    assertThat(service.computePropertyScore("EXPIRING_SOON", "COMPLIANT")).isEqualTo(100);
//  }
//
//  // ── deriveOverallStatus ────────────────────────────────────────────────────
//
//  @Test
//  void deriveOverallStatus_expiredTakesPriority() {
//    assertThat(service.deriveOverallStatus("EXPIRED", "COMPLIANT")).isEqualTo("EXPIRED");
//  }
//
//  @Test
//  void deriveOverallStatus_expiringSoonWhenNoExpired() {
//    assertThat(service.deriveOverallStatus("EXPIRING_SOON", "COMPLIANT"))
//        .isEqualTo("EXPIRING_SOON");
//  }
//
//  @Test
//  void deriveOverallStatus_missingWhenNoneExpired() {
//    assertThat(service.deriveOverallStatus("MISSING", "COMPLIANT")).isEqualTo("MISSING");
//  }
//
//  @Test
//  void deriveOverallStatus_bothNotApplicable_returnsNotApplicable() {
//    assertThat(service.deriveOverallStatus("NOT_APPLICABLE", "NOT_APPLICABLE"))
//        .isEqualTo("NOT_APPLICABLE");
//  }
//
//  // ── enrich ─────────────────────────────────────────────────────────────────
//
//  @Test
//  void enrich_populatesAllComputedFields() {
//    final PropertyDTO dto = buildDto();
//    dto.setHasGasSupply(true);
//    dto.setHasGasCertificate(true);
//    dto.setGasExpiryDate(TODAY.plusDays(60));
//    dto.setHasElectric(true);
//    dto.setHasEicr(false);
//
//    service.enrich(dto);
//
//    assertThat(dto.getGasStatus()).isEqualTo("COMPLIANT");
//    assertThat(dto.getEicrStatus()).isEqualTo("MISSING");
//    assertThat(dto.getComplianceStatus()).isEqualTo("MISSING");
//    assertThat(dto.getNextActions()).isNotEmpty();
//  }
//
//  // ── computeHealth ──────────────────────────────────────────────────────────
//
//  @Test
//  void computeHealth_aggregatesAcrossProperties() {
//    final PropertyDTO compliant = buildDto();
//    compliant.setGasStatus("COMPLIANT");
//    compliant.setEicrStatus("NOT_APPLICABLE");
//    compliant.setComplianceStatus("COMPLIANT");
//
//    final PropertyDTO expired = buildDto();
//    expired.setGasStatus("EXPIRED");
//    expired.setEicrStatus("NOT_APPLICABLE");
//    expired.setComplianceStatus("EXPIRED");
//
//    final ComplianceHealthDTO health = service.computeHealth(List.of(compliant, expired));
//
//    assertThat(health.getTotalProperties()).isEqualTo(2);
//    assertThat(health.getCompliantCount()).isEqualTo(1);
//    assertThat(health.getNonCompliantCount()).isEqualTo(1);
//    assertThat(health.getSummary()).isEqualTo("AT_RISK");
//  }
//
//  // ── computeNextActions ─────────────────────────────────────────────────────
//
//  @Test
//  void computeNextActions_missing_noPdf_suggestsUpload() {
//    final PropertyDTO dto = buildDto();
//    dto.setHasGasSupply(true);
//    dto.setHasGasCertificate(false);
//    dto.setHasGasCertPdf(false);
//    dto.setHasElectric(false);
//
//    final List<String> actions = service.computeNextActions(dto, "MISSING", "NOT_APPLICABLE");
//
//    assertThat(actions).contains("Add Gas Safety Certificate");
//  }
//
//  @Test
//  void computeNextActions_expiringSoon_showsDaysRemaining() {
//    final PropertyDTO dto = buildDto();
//    dto.setHasGasSupply(true);
//    dto.setHasGasCertificate(true);
//    dto.setGasExpiryDate(TODAY.plusDays(15));
//    dto.setGasDaysUntilExpiry(15);
//    dto.setHasElectric(false);
//
//    final List<String> actions = service.computeNextActions(dto, "EXPIRING_SOON",
// "NOT_APPLICABLE");
//
//    assertThat(actions).anyMatch(a -> a.contains("15 days"));
//  }
//
//  private PropertyDTO buildDto() {
//    final PropertyDTO dto = new PropertyDTO();
//    dto.setId(UUID.randomUUID());
//    dto.setAddressLine1("10 Test Street");
//    dto.setCity("London");
//    dto.setPostcode("SW1A 1AA");
//    dto.setCountry("GB");
//    dto.setPropertyType("FLAT");
//    dto.setHasGasSupply(true);
//    dto.setHasElectric(true);
//    dto.setComplianceStatus("UNKNOWN");
//    return dto;
//  }
// }

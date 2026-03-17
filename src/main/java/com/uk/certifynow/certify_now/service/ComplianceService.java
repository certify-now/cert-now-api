package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
import com.uk.certifynow.certify_now.model.PropertyComplianceItemDTO;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes per-property and aggregate compliance status from certificate expiry data. All business
 * logic for compliance lives here — not in the controller or frontend.
 */
@Transactional(readOnly = true)
@Service
public class ComplianceService {

  /** Certs expiring within this many days are flagged EXPIRING_SOON. */
  private static final int EXPIRY_WARNING_DAYS = 30;

  /**
   * Describes a single certificate category — captures inputs and computed status. Used to replace
   * repetitive gas/EICR branches with a uniform loop.
   */
  record CertCheck(
      String type,
      Boolean hasSupply,
      Boolean hasCert,
      LocalDate expiryDate,
      Integer daysUntilExpiry,
      Boolean hasCertPdf,
      String addAction,
      String uploadAction,
      String renewLabel,
      String overdueLabel,
      String status) {

    String nextAction() {
      return switch (status) {
        case "MISSING" -> {
          if (Boolean.TRUE.equals(hasCert)
              && expiryDate != null
              && !Boolean.TRUE.equals(hasCertPdf)) {
            yield uploadAction;
          }
          yield addAction;
        }
        case "EXPIRING_SOON" -> renewLabel + daysUntilExpiry + " days";
        case "EXPIRED" -> overdueLabel;
        default -> null;
      };
    }
  }

  private final Clock clock;

  public ComplianceService(final Clock clock) {
    this.clock = clock;
  }

  // ── Per-certificate status ─────────────────────────────────────────────────

  /**
   * Derives the compliance status for a single certificate category.
   *
   * @param hasSupply whether this utility (gas/electric) is present at the property
   * @param hasCert whether the landlord has declared they hold a valid certificate
   * @param expiryDate the certificate's expiry date
   */
  public String computeCertStatus(Boolean hasSupply, Boolean hasCert, LocalDate expiryDate) {
    if (!Boolean.TRUE.equals(hasSupply)) {
      return "NOT_APPLICABLE";
    }
    if (!Boolean.TRUE.equals(hasCert) || expiryDate == null) {
      return "MISSING";
    }
    LocalDate today = LocalDate.now(clock);
    if (expiryDate.isBefore(today)) {
      return "EXPIRED";
    }
    if (expiryDate.isBefore(today.plusDays(EXPIRY_WARNING_DAYS))) {
      return "EXPIRING_SOON";
    }
    return "COMPLIANT";
  }

  /** Computes days until the certificate expires, or null if not applicable / already expired. */
  public Integer computeDaysUntilExpiry(String certStatus, LocalDate expiryDate) {
    if (!"COMPLIANT".equals(certStatus) && !"EXPIRING_SOON".equals(certStatus)) {
      return null;
    }
    return (int) ChronoUnit.DAYS.between(LocalDate.now(clock), expiryDate);
  }

  // ── Per-property score ─────────────────────────────────────────────────────

  /**
   * Assigns a 0-100 compliance score for a single property based on its cert statuses.
   * NOT_APPLICABLE certs are excluded from the average.
   */
  public int computePropertyScore(String gasStatus, String eicrStatus) {
    List<Integer> scores = new ArrayList<>();
    if (gasStatus != null && !"NOT_APPLICABLE".equals(gasStatus)) {
      scores.add(scoreForStatus(gasStatus));
    }
    if (eicrStatus != null && !"NOT_APPLICABLE".equals(eicrStatus)) {
      scores.add(scoreForStatus(eicrStatus));
    }
    if (scores.isEmpty()) {
      return 100;
    }
    return scores.stream().mapToInt(Integer::intValue).sum() / scores.size();
  }

  private int scoreForStatus(String status) {
    return switch (status) {
      case "COMPLIANT", "EXPIRING_SOON" -> 100;
      default -> 0;
    };
  }

  // ── Overall property compliance status ────────────────────────────────────

  /**
   * Derives a single overall compliance status for the property from the individual cert statuses.
   * Values: COMPLIANT | EXPIRING_SOON | EXPIRED | MISSING
   */
  public String deriveOverallStatus(String gasStatus, String eicrStatus) {
    Set<String> relevant = new java.util.HashSet<>();
    if (gasStatus != null && !"NOT_APPLICABLE".equals(gasStatus)) {
      relevant.add(gasStatus);
    }
    if (eicrStatus != null && !"NOT_APPLICABLE".equals(eicrStatus)) {
      relevant.add(eicrStatus);
    }
    if (relevant.isEmpty()) {
      return "NOT_APPLICABLE";
    }
    if (relevant.contains("EXPIRED")) return "EXPIRED";
    if (relevant.contains("EXPIRING_SOON")) return "EXPIRING_SOON";
    if (relevant.contains("MISSING")) return "MISSING";
    return "COMPLIANT";
  }

  // ── Next actions ───────────────────────────────────────────────────────────

  /**
   * Builds a prioritised list of actionable next steps for the landlord. Business rule: if the
   * landlord declared a cert + expiry date but hasn't uploaded a PDF, we prompt them to upload
   * rather than re-register.
   */
  public List<String> computeNextActions(PropertyDTO dto, String gasStatus, String eicrStatus) {
    final List<CertCheck> checks =
        List.of(
            new CertCheck(
                "GAS",
                dto.getHasGasSupply(),
                dto.getHasGasCertificate(),
                dto.getGasExpiryDate(),
                dto.getGasDaysUntilExpiry(),
                dto.getHasGasCertPdf(),
                "Add Gas Safety Certificate",
                "Upload Gas Safety Certificate",
                "Renew Gas Safety - ",
                "Gas Safety Certificate Overdue",
                gasStatus),
            new CertCheck(
                "EICR",
                dto.getHasElectric(),
                dto.getHasEicr(),
                dto.getEicrExpiryDate(),
                dto.getEicrDaysUntilExpiry(),
                dto.getHasEicrCertPdf(),
                "Add EICR",
                "Upload EICR",
                "Renew EICR - ",
                "EICR Overdue",
                eicrStatus));

    final List<String> actions = new ArrayList<>();
    for (final CertCheck check : checks) {
      final String action = check.nextAction();
      if (action != null) {
        actions.add(action);
      }
    }
    return actions;
  }

  // ── Enrich a DTO ──────────────────────────────────────────────────────────

  /**
   * Populates all computed compliance fields on the DTO in-place. Call this after mapping the
   * entity to DTO.
   */
  public void enrich(final PropertyDTO dto) {
    record EnrichSpec(
        Boolean hasSupply,
        Boolean hasCert,
        LocalDate expiryDate,
        BiConsumer<PropertyDTO, String> statusSetter,
        BiConsumer<PropertyDTO, Integer> daysSetter) {}

    final List<EnrichSpec> specs =
        List.of(
            new EnrichSpec(
                dto.getHasGasSupply(),
                dto.getHasGasCertificate(),
                dto.getGasExpiryDate(),
                PropertyDTO::setGasStatus,
                PropertyDTO::setGasDaysUntilExpiry),
            new EnrichSpec(
                dto.getHasElectric(),
                dto.getHasEicr(),
                dto.getEicrExpiryDate(),
                PropertyDTO::setEicrStatus,
                PropertyDTO::setEicrDaysUntilExpiry));

    for (final EnrichSpec spec : specs) {
      final String status = computeCertStatus(spec.hasSupply(), spec.hasCert(), spec.expiryDate());
      spec.statusSetter().accept(dto, status);
      spec.daysSetter().accept(dto, computeDaysUntilExpiry(status, spec.expiryDate()));
    }

    dto.setComplianceStatus(deriveOverallStatus(dto.getGasStatus(), dto.getEicrStatus()));
    dto.setNextActions(computeNextActions(dto, dto.getGasStatus(), dto.getEicrStatus()));
  }

  // ── Aggregate health ──────────────────────────────────────────────────────

  /**
   * Computes the aggregate ComplianceHealthDTO from an already-enriched list of properties. Expects
   * enrich() to have been called on each DTO first.
   *
   * <p>overallScore = average of all property cert scores (COMPLIANT/EXPIRING_SOON = 100,
   * EXPIRED/MISSING = 0, NOT_APPLICABLE excluded). This answers "what % of my certificate
   * obligations are legally valid right now?"
   */
  public ComplianceHealthDTO computeHealth(List<PropertyDTO> properties) {
    int total = properties.size();
    int compliantCount = 0;
    int expiringSoonCount = 0;
    int nonCompliantCount = 0;
    int totalScore = 0;
    List<PropertyComplianceItemDTO> items = new ArrayList<>();

    for (PropertyDTO p : properties) {
      int score = computePropertyScore(p.getGasStatus(), p.getEicrStatus());
      totalScore += score;

      String status = p.getComplianceStatus();
      if ("COMPLIANT".equals(status)) {
        compliantCount++;
      } else if ("EXPIRING_SOON".equals(status)) {
        expiringSoonCount++;
      } else if ("EXPIRED".equals(status) || "MISSING".equals(status)) {
        nonCompliantCount++;
      }

      items.add(
          new PropertyComplianceItemDTO(
              p.getId(),
              p.getAddressLine1(),
              p.getPostcode(),
              p.getGasStatus(),
              p.getEicrStatus(),
              score));
    }

    int overallScore = total > 0 ? totalScore / total : 100;
    return new ComplianceHealthDTO(
        overallScore,
        total,
        compliantCount,
        expiringSoonCount,
        nonCompliantCount,
        computeSummaryLabel(nonCompliantCount, expiringSoonCount),
        items);
  }

  private String computeSummaryLabel(int nonCompliantCount, int expiringSoonCount) {
    if (nonCompliantCount > 0) return "AT_RISK";
    if (expiringSoonCount > 0) return "ATTENTION_NEEDED";
    return "ON_TRACK";
  }
}

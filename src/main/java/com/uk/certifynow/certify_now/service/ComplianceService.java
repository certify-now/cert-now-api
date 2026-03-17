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
import org.springframework.stereotype.Service;

/**
 * Computes per-property and aggregate compliance status from certificate expiry data. All business
 * logic for compliance lives here — not in the controller or frontend.
 */
@Service
public class ComplianceService {

  /** Certs expiring within this many days are flagged EXPIRING_SOON. */
  private static final int EXPIRY_WARNING_DAYS = 30;

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
    List<String> actions = new ArrayList<>();

    if ("MISSING".equals(gasStatus)) {
      if (Boolean.TRUE.equals(dto.getHasGasCertificate())
          && dto.getGasExpiryDate() != null
          && !Boolean.TRUE.equals(dto.getHasGasCertPdf())) {
        actions.add("Upload Gas Safety Certificate");
      } else {
        actions.add("Add Gas Safety Certificate");
      }
    } else if ("EXPIRING_SOON".equals(gasStatus)) {
      actions.add("Renew Gas Safety - " + dto.getGasDaysUntilExpiry() + " days");
    } else if ("EXPIRED".equals(gasStatus)) {
      actions.add("Gas Safety Certificate Overdue");
    }

    if ("MISSING".equals(eicrStatus)) {
      if (Boolean.TRUE.equals(dto.getHasEicr())
          && dto.getEicrExpiryDate() != null
          && !Boolean.TRUE.equals(dto.getHasEicrCertPdf())) {
        actions.add("Upload EICR");
      } else {
        actions.add("Add EICR");
      }
    } else if ("EXPIRING_SOON".equals(eicrStatus)) {
      actions.add("Renew EICR - " + dto.getEicrDaysUntilExpiry() + " days");
    } else if ("EXPIRED".equals(eicrStatus)) {
      actions.add("EICR Overdue");
    }

    return actions;
  }

  // ── Enrich a DTO ──────────────────────────────────────────────────────────

  /**
   * Populates all computed compliance fields on the DTO in-place. Call this after mapping the
   * entity to DTO.
   */
  public void enrich(PropertyDTO dto) {
    String gasStatus =
        computeCertStatus(
            dto.getHasGasSupply(), dto.getHasGasCertificate(), dto.getGasExpiryDate());
    dto.setGasStatus(gasStatus);
    dto.setGasDaysUntilExpiry(computeDaysUntilExpiry(gasStatus, dto.getGasExpiryDate()));

    String eicrStatus =
        computeCertStatus(dto.getHasElectric(), dto.getHasEicr(), dto.getEicrExpiryDate());
    dto.setEicrStatus(eicrStatus);
    dto.setEicrDaysUntilExpiry(computeDaysUntilExpiry(eicrStatus, dto.getEicrExpiryDate()));

    dto.setComplianceStatus(deriveOverallStatus(gasStatus, eicrStatus));
    dto.setNextActions(computeNextActions(dto, gasStatus, eicrStatus));
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

package com.uk.certifynow.certify_now.service.certificate;

import com.uk.certifynow.certify_now.model.ComplianceHealthDTO;
import com.uk.certifynow.certify_now.model.PropertyComplianceItemDTO;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.service.enums.ComplianceStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes per-property and aggregate compliance status from certificate expiry data. All business
 * logic for compliance lives here — not in the controller or frontend.
 *
 * <p>Statuses are expressed as {@link ComplianceStatus} values serialised to their name strings in
 * the API response. Raw string literals are never used in this class.
 */
@Transactional(readOnly = true)
@Service
public class ComplianceService {

  private final Clock clock;

  public ComplianceService(final Clock clock) {
    this.clock = clock;
  }

  // ── Per-certificate status ─────────────────────────────────────────────────

  /**
   * Derives the compliance status for a single obligation backed by a supply flag. Used for Gas
   * Safety and EICR where the property may not have the relevant utility.
   *
   * @param hasSupply whether this utility (gas/electric) is present at the property
   * @param hasCert whether a valid certificate is on record
   * @param expiryDate the certificate's expiry date
   */
  public ComplianceStatus computeCertStatus(
      final Boolean hasSupply,
      final Boolean hasCert,
      final LocalDate expiryDate,
      final int expiringSoonDays) {
    if (!Boolean.TRUE.equals(hasSupply)) {
      return ComplianceStatus.NOT_APPLICABLE;
    }
    return computeExpiryStatus(hasCert, expiryDate, expiringSoonDays);
  }

  /**
   * Derives the compliance status for an obligation that always applies (e.g. EPC).
   *
   * @param hasCert whether a certificate is on record
   * @param expiryDate the certificate's expiry date
   */
  public ComplianceStatus computeEpcStatus(
      final Boolean hasCert, final LocalDate expiryDate, final int expiringSoonDays) {
    return computeExpiryStatus(hasCert, expiryDate, expiringSoonDays);
  }

  private ComplianceStatus computeExpiryStatus(
      final Boolean hasCert, final LocalDate expiryDate, final int expiringSoonDays) {
    if (!Boolean.TRUE.equals(hasCert) || expiryDate == null) {
      return ComplianceStatus.MISSING;
    }
    final LocalDate today = LocalDate.now(clock);
    if (expiryDate.isBefore(today)) {
      return ComplianceStatus.EXPIRED;
    }
    if (expiryDate.isBefore(today.plusDays(expiringSoonDays))) {
      return ComplianceStatus.EXPIRING_SOON;
    }
    return ComplianceStatus.COMPLIANT;
  }

  /** Computes days until the certificate expires, or null if not applicable / already expired. */
  public Integer computeDaysUntilExpiry(
      final ComplianceStatus certStatus, final LocalDate expiryDate) {
    if (certStatus != ComplianceStatus.COMPLIANT && certStatus != ComplianceStatus.EXPIRING_SOON) {
      return null;
    }
    return (int) ChronoUnit.DAYS.between(LocalDate.now(clock), expiryDate);
  }

  // ── Per-property score ─────────────────────────────────────────────────────

  /**
   * Assigns a 0–100 compliance score for a single property based on its cert statuses.
   * NOT_APPLICABLE certs are excluded from the average.
   */
  public int computePropertyScore(
      final ComplianceStatus gasStatus,
      final ComplianceStatus eicrStatus,
      final ComplianceStatus epcStatus) {
    final List<Integer> scores = new ArrayList<>();
    if (gasStatus != null && gasStatus != ComplianceStatus.NOT_APPLICABLE) {
      scores.add(scoreForStatus(gasStatus));
    }
    if (eicrStatus != null && eicrStatus != ComplianceStatus.NOT_APPLICABLE) {
      scores.add(scoreForStatus(eicrStatus));
    }
    if (epcStatus != null && epcStatus != ComplianceStatus.NOT_APPLICABLE) {
      scores.add(scoreForStatus(epcStatus));
    }
    if (scores.isEmpty()) {
      return 100;
    }
    return scores.stream().mapToInt(Integer::intValue).sum() / scores.size();
  }

  private int scoreForStatus(final ComplianceStatus status) {
    return switch (status) {
      case COMPLIANT, EXPIRING_SOON -> 100;
      default -> 0;
    };
  }

  // ── Overall property compliance status ────────────────────────────────────

  /**
   * Derives a single overall compliance status for the property from the individual cert statuses.
   * Priority order: EXPIRED > EXPIRING_SOON > MISSING > COMPLIANT.
   */
  public ComplianceStatus deriveOverallStatus(
      final ComplianceStatus gasStatus,
      final ComplianceStatus eicrStatus,
      final ComplianceStatus epcStatus) {
    final Set<ComplianceStatus> relevant = new java.util.HashSet<>();
    if (gasStatus != null && gasStatus != ComplianceStatus.NOT_APPLICABLE) {
      relevant.add(gasStatus);
    }
    if (eicrStatus != null && eicrStatus != ComplianceStatus.NOT_APPLICABLE) {
      relevant.add(eicrStatus);
    }
    if (epcStatus != null && epcStatus != ComplianceStatus.NOT_APPLICABLE) {
      relevant.add(epcStatus);
    }
    if (relevant.isEmpty()) {
      return ComplianceStatus.NOT_APPLICABLE;
    }
    if (relevant.contains(ComplianceStatus.EXPIRED)) return ComplianceStatus.EXPIRED;
    if (relevant.contains(ComplianceStatus.EXPIRING_SOON)) return ComplianceStatus.EXPIRING_SOON;
    if (relevant.contains(ComplianceStatus.MISSING)) return ComplianceStatus.MISSING;
    return ComplianceStatus.COMPLIANT;
  }

  // ── Enrich a DTO ──────────────────────────────────────────────────────────

  /**
   * Populates all computed compliance fields on the DTO in-place. Call this after mapping the
   * entity to DTO.
   *
   * <p>Gas and EICR are gated by supply flags (NOT_APPLICABLE when the utility is absent). EPC
   * always applies — all UK rental properties require one.
   */
  public void enrich(final PropertyDTO dto, final int expiringSoonDays) {
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

    ComplianceStatus gasStatus = null;
    ComplianceStatus eicrStatus = null;

    for (final EnrichSpec spec : specs) {
      final ComplianceStatus status =
          computeCertStatus(spec.hasSupply(), spec.hasCert(), spec.expiryDate(), expiringSoonDays);
      spec.statusSetter().accept(dto, status.name());
      spec.daysSetter().accept(dto, computeDaysUntilExpiry(status, spec.expiryDate()));
      if (spec.statusSetter() == (BiConsumer<PropertyDTO, String>) PropertyDTO::setGasStatus) {
        gasStatus = status;
      } else {
        eicrStatus = status;
      }
    }

    // Capture gas/EICR statuses for overall derivation
    gasStatus = ComplianceStatus.valueOf(dto.getGasStatus());
    eicrStatus = ComplianceStatus.valueOf(dto.getEicrStatus());

    // EPC — always applicable, derived from the current EPC certificate FK.
    // Unlike gas/EICR there is no "missing" state: EPC is sourced entirely from the government
    // registry. The government API does not distinguish between "never lodged" and "last one
    // expired and not renewed" — it simply returns nothing. Both cases mean no valid EPC exists,
    // so we treat them identically as EXPIRED (non-compliant).
    // A null epcExpiryDate on an existing cert means the registry lookup found nothing.
    final boolean hasEpcCert = dto.getCurrentEpcCertificateId() != null;
    final ComplianceStatus epcStatus =
        (!hasEpcCert || dto.getEpcExpiryDate() == null)
            ? ComplianceStatus.EXPIRED
            : computeEpcStatus(true, dto.getEpcExpiryDate(), expiringSoonDays);
    dto.setEpcStatus(epcStatus.name());
    dto.setEpcDaysUntilExpiry(computeDaysUntilExpiry(epcStatus, dto.getEpcExpiryDate()));

    dto.setComplianceStatus(deriveOverallStatus(gasStatus, eicrStatus, epcStatus).name());
    dto.setNextActions(Collections.emptyList());
  }

  // ── Aggregate health ──────────────────────────────────────────────────────

  /**
   * Computes the aggregate ComplianceHealthDTO from an already-enriched list of properties. Expects
   * {@link #enrich(PropertyDTO, int)} to have been called on each DTO first.
   */
  public ComplianceHealthDTO computeHealth(final List<PropertyDTO> properties) {
    int total = properties.size();
    int compliantCount = 0;
    int expiringSoonCount = 0;
    int nonCompliantCount = 0;
    int totalScore = 0;
    final List<PropertyComplianceItemDTO> items = new ArrayList<>();

    for (final PropertyDTO p : properties) {
      final ComplianceStatus gas = p.getGasStatus() != null ? safeValueOf(p.getGasStatus()) : null;
      final ComplianceStatus eicr =
          p.getEicrStatus() != null ? safeValueOf(p.getEicrStatus()) : null;
      final ComplianceStatus epc = p.getEpcStatus() != null ? safeValueOf(p.getEpcStatus()) : null;

      final int score = computePropertyScore(gas, eicr, epc);
      totalScore += score;

      final String status = p.getComplianceStatus();
      if (ComplianceStatus.COMPLIANT.name().equals(status)) {
        compliantCount++;
      } else if (ComplianceStatus.EXPIRING_SOON.name().equals(status)) {
        expiringSoonCount++;
      } else if (ComplianceStatus.EXPIRED.name().equals(status)
          || ComplianceStatus.MISSING.name().equals(status)) {
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

    final int overallScore = total > 0 ? totalScore / total : 100;
    return new ComplianceHealthDTO(
        overallScore,
        total,
        compliantCount,
        expiringSoonCount,
        nonCompliantCount,
        computeSummaryLabel(nonCompliantCount, expiringSoonCount),
        items);
  }

  private String computeSummaryLabel(final int nonCompliantCount, final int expiringSoonCount) {
    if (nonCompliantCount > 0) return "AT_RISK";
    if (expiringSoonCount > 0) return "ATTENTION_NEEDED";
    return "ON_TRACK";
  }

  private ComplianceStatus safeValueOf(final String name) {
    try {
      return ComplianceStatus.valueOf(name);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}

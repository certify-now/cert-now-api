package com.uk.certifynow.certify_now.domain.enums;

/**
 * Lifecycle state of a {@code Certificate} record in the database.
 *
 * <p>Distinct from {@link com.uk.certifynow.certify_now.service.ComplianceStatus}, which represents
 * the UI-facing compliance health of a property obligation (e.g. COMPLIANT, MISSING).
 *
 * <ul>
 *   <li>{@code ACTIVE} — the certificate is valid and in use
 *   <li>{@code EXPIRED} — the certificate's expiry date has passed
 *   <li>{@code SUPERSEDED} — replaced by a newer certificate of the same type
 *   <li>{@code VALID} — dynamically calculated: certificate has not expired
 *   <li>{@code EXPIRING_SOON} — dynamically calculated: certificate expires within threshold
 *   <li>{@code MISSING} — no certificate on record for this obligation
 * </ul>
 */
public enum CertificateStatus {
  ACTIVE,
  EXPIRED,
  SUPERSEDED,
  VALID,
  EXPIRING_SOON,
  MISSING;

  public static CertificateStatus fromString(final String status) {
    return valueOf(status.toUpperCase());
  }
}

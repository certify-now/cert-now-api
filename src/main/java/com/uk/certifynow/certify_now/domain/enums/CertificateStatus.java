package com.uk.certifynow.certify_now.domain.enums;

/**
 * Lifecycle state of a {@code Certificate} record in the database.
 *
 * <p>Distinct from {@link com.uk.certifynow.certify_now.service.ComplianceStatus}, which
 * represents the UI-facing compliance health of a property obligation (e.g. COMPLIANT, MISSING).
 *
 * <ul>
 *   <li>{@code ACTIVE}     — the certificate is valid and in use</li>
 *   <li>{@code EXPIRED}    — the certificate's expiry date has passed</li>
 *   <li>{@code SUPERSEDED} — replaced by a newer certificate of the same type</li>
 * </ul>
 */
public enum CertificateStatus {
  ACTIVE,
  EXPIRED,
  SUPERSEDED
}

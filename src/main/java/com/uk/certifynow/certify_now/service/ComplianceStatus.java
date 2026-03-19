package com.uk.certifynow.certify_now.service;

/**
 * UI-facing compliance health of a single property obligation (gas, EICR, EPC).
 *
 * <p>Distinct from {@link com.uk.certifynow.certify_now.domain.enums.CertificateStatus}, which
 * represents the lifecycle state of a {@code Certificate} database record.
 *
 * <p>These values are returned in {@code PropertyDTO} fields such as {@code gasStatus},
 * {@code eicrStatus}, and {@code epcStatus}, and drive the colour-coding and action prompts
 * in the frontend.
 *
 * <ul>
 *   <li>{@code COMPLIANT}      — certificate is valid and not due soon</li>
 *   <li>{@code EXPIRING_SOON}  — certificate expires within 30 days</li>
 *   <li>{@code EXPIRED}        — certificate has passed its expiry date</li>
 *   <li>{@code MISSING}        — no certificate on record for this obligation</li>
 *   <li>{@code NOT_APPLICABLE} — this obligation does not apply to the property
 *                                (e.g. gas cert when there is no gas supply)</li>
 * </ul>
 */
public enum ComplianceStatus {
  COMPLIANT,
  EXPIRING_SOON,
  EXPIRED,
  MISSING,
  NOT_APPLICABLE
}

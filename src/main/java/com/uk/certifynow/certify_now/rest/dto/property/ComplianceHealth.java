package com.uk.certifynow.certify_now.rest.dto.property;

import java.util.List;

/**
 * Comprehensive compliance health score for the home screen.
 *
 * @param overallScore percentage 0-100 across all properties
 * @param totalProperties total properties owned
 * @param compliantCount properties fully compliant
 * @param actionRequiredCount properties needing attention
 * @param expiredCount properties with expired certificates
 * @param summary human-readable summary label (e.g. "Good", "Action Required")
 * @param items per-property breakdown
 */
public record ComplianceHealth(
    int overallScore,
    int totalProperties,
    int compliantCount,
    int actionRequiredCount,
    int expiredCount,
    String summary,
    List<PropertyComplianceItem> items) {

  /**
   * Per-property compliance breakdown.
   *
   * @param propertyId UUID as string
   * @param addressLine1 first line of address
   * @param postcode property postcode
   * @param gasStatus one of COMPLIANT, EXPIRING_SOON, EXPIRED, MISSING, NOT_APPLICABLE
   * @param eicrStatus one of COMPLIANT, EXPIRING_SOON, EXPIRED, MISSING, NOT_APPLICABLE
   * @param propertyScore 0-100 for this property
   */
  public record PropertyComplianceItem(
      String propertyId,
      String addressLine1,
      String postcode,
      String gasStatus,
      String eicrStatus,
      int propertyScore) {}
}

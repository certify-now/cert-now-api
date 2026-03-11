package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.rest.dto.property.ComplianceHealth;
import com.uk.certifynow.certify_now.rest.dto.property.ComplianceHealth.PropertyComplianceItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Calculates a comprehensive compliance health score across a customer's properties. Each property
 * is scored on its Gas Safety and EICR certificate status.
 */
@Service
public class ComplianceHealthService {

  private static final int EXPIRING_SOON_DAYS = 30;

  /**
   * Build a {@link ComplianceHealth} snapshot for the given properties.
   *
   * @param properties all active properties belonging to the customer
   * @return aggregated compliance health
   */
  public ComplianceHealth calculate(List<Property> properties) {
    if (properties.isEmpty()) {
      return new ComplianceHealth(100, 0, 0, 0, 0, "No properties", List.of());
    }

    final LocalDate today = LocalDate.now();
    final List<PropertyComplianceItem> items = new ArrayList<>();
    int compliant = 0;
    int actionRequired = 0;
    int expired = 0;
    int totalScore = 0;

    for (Property property : properties) {
      String gasStatus = resolveGasStatus(property, today);
      String eicrStatus = resolveEicrStatus(property, today);
      int propertyScore = computePropertyScore(gasStatus, eicrStatus, property);

      items.add(
          new PropertyComplianceItem(
              property.getId().toString(),
              property.getAddressLine1(),
              property.getPostcode(),
              gasStatus,
              eicrStatus,
              propertyScore));

      totalScore += propertyScore;

      if (propertyScore == 100) {
        compliant++;
      } else if ("EXPIRED".equals(gasStatus) || "EXPIRED".equals(eicrStatus)) {
        expired++;
      } else {
        actionRequired++;
      }
    }

    int overallScore = totalScore / properties.size();
    String summary = deriveSummary(overallScore);

    return new ComplianceHealth(
        overallScore, properties.size(), compliant, actionRequired, expired, summary, items);
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  private String resolveGasStatus(Property property, LocalDate today) {
    if (!Boolean.TRUE.equals(property.getHasGasSupply())) {
      return "NOT_APPLICABLE";
    }
    if (!Boolean.TRUE.equals(property.getHasGasCertificate())) {
      return "MISSING";
    }
    if (property.getGasExpiryDate() == null) {
      return "MISSING";
    }
    if (property.getGasExpiryDate().isBefore(today)) {
      return "EXPIRED";
    }
    if (property.getGasExpiryDate().isBefore(today.plusDays(EXPIRING_SOON_DAYS))) {
      return "EXPIRING_SOON";
    }
    return "COMPLIANT";
  }

  private String resolveEicrStatus(Property property, LocalDate today) {
    if (!Boolean.TRUE.equals(property.getHasElectric())) {
      return "NOT_APPLICABLE";
    }
    if (!Boolean.TRUE.equals(property.getHasEicr())) {
      return "MISSING";
    }
    if (property.getEicrExpiryDate() == null) {
      return "MISSING";
    }
    if (property.getEicrExpiryDate().isBefore(today)) {
      return "EXPIRED";
    }
    if (property.getEicrExpiryDate().isBefore(today.plusDays(EXPIRING_SOON_DAYS))) {
      return "EXPIRING_SOON";
    }
    return "COMPLIANT";
  }

  /**
   * Each applicable certificate category contributes equally. COMPLIANT = 100, EXPIRING_SOON = 60,
   * MISSING = 20, EXPIRED = 0, NOT_APPLICABLE = excluded.
   */
  private int computePropertyScore(String gasStatus, String eicrStatus, Property property) {
    int total = 0;
    int count = 0;

    if (!"NOT_APPLICABLE".equals(gasStatus)) {
      total += statusToPoints(gasStatus);
      count++;
    }
    if (!"NOT_APPLICABLE".equals(eicrStatus)) {
      total += statusToPoints(eicrStatus);
      count++;
    }

    // If no applicable categories, property is fully compliant by default
    if (count == 0) {
      return 100;
    }
    return total / count;
  }

  private int statusToPoints(String status) {
    return switch (status) {
      case "COMPLIANT" -> 100;
      case "EXPIRING_SOON" -> 60;
      case "MISSING" -> 20;
      case "EXPIRED" -> 0;
      default -> 0;
    };
  }

  private String deriveSummary(int overallScore) {
    if (overallScore >= 90) {
      return "Excellent";
    }
    if (overallScore >= 70) {
      return "Good";
    }
    if (overallScore >= 40) {
      return "Action Required";
    }
    return "Critical";
  }
}

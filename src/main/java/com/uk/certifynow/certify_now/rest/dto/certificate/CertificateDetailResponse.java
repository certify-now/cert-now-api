package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CertificateDetailResponse(
    UUID id,
    String certificateNumber,
    String certificateType,
    PropertySummaryResponse property,
    String status,
    String result,
    LocalDate issuedAt,
    LocalDate expiresAt,
    Long daysUntilExpiry,
    String documentUrl,
    String shareToken,
    String shareUrl,
    boolean canDownload,
    boolean canShare,
    boolean canRenew,
    EngineerSummaryResponse issuedBy,
    UUID jobId,
    GasInspectionSummary gasInspection,
    EicrInspectionSummary eicrInspection,
    EpcAssessmentSummary epcAssessment
) {

  public record GasInspectionSummary(
      int applianceCount,
      List<ApplianceSummary> appliances
  ) {}

  public record ApplianceSummary(
      String applianceType,
      String location,
      String make,
      String model,
      String safetyStatus
  ) {}

  public record EicrInspectionSummary(
      int c1Count,
      int c2Count,
      int c3Count,
      int fiCount,
      LocalDate inspectionDate,
      LocalDate nextInspectionDate,
      String overallResult
  ) {}

  public record EpcAssessmentSummary(
      String epcRating,
      Integer epcScore,
      Integer potentialRating,
      Integer potentialScore
  ) {}
}

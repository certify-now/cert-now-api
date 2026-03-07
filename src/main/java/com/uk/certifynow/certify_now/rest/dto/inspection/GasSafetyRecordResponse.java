package com.uk.certifynow.certify_now.rest.dto.inspection;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GasSafetyRecordResponse(
    UUID id,
    UUID jobId,
    String certificateNumber,
    String certificateReference,
    String certificateType,
    LocalDate issueDate,
    LocalDate nextInspectionDueOnOrBefore,
    Integer numberOfAppliancesTested,
    String qrCodeUrl,
    String verificationUrl,
    EngineerSummary engineer,
    CompanySummary companyDetails,
    ClientSummary clientDetails,
    TenantSummary tenantDetails,
    InstallationSummary installationDetails,
    List<ApplianceSummary> appliances,
    FinalChecksSummary finalChecks,
    FaultsAndRemedialsSummary faultsAndRemedials,
    SignaturesSummary signatures,
    MetadataSummary metadata,
    OffsetDateTime createdAt) {

  public record EngineerSummary(
      String engineerName,
      String engineerGasSafeNumber,
      String engineerLicenceCardNumber,
      String timeOfArrival,
      String timeOfDeparture,
      LocalDate reportIssuedDate,
      String engineerNotes) {}

  public record CompanySummary(
      String tradingTitle,
      String addressLine1,
      String addressLine2,
      String addressLine3,
      String postCode,
      String gasSafeRegistrationNumber,
      String companyPhone,
      String companyEmail) {}

  public record ClientSummary(
      String name,
      String addressLine1,
      String addressLine2,
      String addressLine3,
      String postCode,
      String telephone,
      String email) {}

  public record TenantSummary(String name, String email, String telephone) {}

  public record InstallationSummary(
      String nameOrFlat,
      String addressLine1,
      String addressLine2,
      String addressLine3,
      String postCode,
      String telephone,
      String email) {}

  public record ApplianceSummary(
      UUID id,
      Integer index,
      String location,
      String applianceType,
      String make,
      String model,
      String serialNumber,
      Boolean landlordsAppliance,
      String inspectionType,
      Boolean applianceInspected,
      Boolean applianceServiced,
      Boolean applianceSafeToUse,
      String classificationCode,
      String classificationDescription,
      String flueType,
      Boolean ventilationProvisionSatisfactory,
      Boolean flueVisualConditionTerminationSatisfactory,
      String fluePerformanceTests,
      String spillageTest,
      java.math.BigDecimal operatingPressureMbar,
      java.math.BigDecimal burnerPressureMbar,
      String gasRate,
      java.math.BigDecimal heatInputKw,
      Boolean safetyDevicesCorrectOperation,
      Boolean emergencyControlAccessible,
      Boolean gasInstallationPipeworkVisualInspectionSatisfactory,
      Boolean gasTightnessSatisfactory,
      Boolean equipotentialBonding,
      Boolean warningNoticeFixed,
      String additionalNotes,
      CombustionReadingsSummary combustionReadings) {}

  public record CombustionReadingsSummary(
      java.math.BigDecimal coPpm,
      java.math.BigDecimal co2Percentage,
      java.math.BigDecimal coToCo2Ratio,
      java.math.BigDecimal combustionLow,
      java.math.BigDecimal combustionHigh) {}

  public record FinalChecksSummary(
      String gasTightnessPass,
      String gasPipeWorkVisualPass,
      String emergencyControlAccessible,
      String equipotentialBonding,
      String installationPass,
      String coAlarmFittedWorkingSameRoom,
      String smokeAlarmFittedWorking,
      String additionalObservations) {}

  public record FaultsAndRemedialsSummary(
      String faultsNotes,
      String remedialWorkTaken,
      Boolean warningNoticeFixed,
      Boolean applianceIsolated,
      String isolationReason) {}

  public record SignaturesSummary(
      Boolean engineerSigned,
      LocalDate engineerSignedDate,
      String customerName,
      Boolean customerSigned,
      LocalDate customerSignedDate,
      Boolean tenantSigned,
      LocalDate tenantSignedDate,
      Boolean privacyPolicyAccepted) {}

  public record MetadataSummary(
      String createdBySoftware, String version, OffsetDateTime generatedAt, String platform) {}
}

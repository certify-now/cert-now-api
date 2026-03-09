package com.uk.certifynow.certify_now.service.pdf;

import java.util.List;

/**
 * Flat view model for the gas safety certificate PDF template.
 *
 * <p>All dates are pre-formatted as {@code dd/MM/yyyy} strings and all nullable values are
 * represented as empty strings so the Thymeleaf template never receives {@code null}.
 */
public record GasSafetyCertificatePdfModel(
    CertificateMeta meta,
    CompanyInfo company,
    ClientInfo client,
    TenantInfo tenant,
    InstallationAddress installation,
    EngineerInfo engineer,
    List<ApplianceRow> appliances,
    FinalChecks finalChecks,
    FaultsAndRemedials faults,
    Signatures signatures,
    String verificationUrl,
    String qrCodeDataUri) {

  public record CertificateMeta(
      String certificateNumber,
      String certificateType,
      String issueDateFormatted,
      String nextDueFormatted,
      int applianceCount) {}

  public record CompanyInfo(
      String tradingTitle, String address, String gasSafeRegNumber, String phone, String email) {}

  public record ClientInfo(String name, String address, String telephone, String email) {}

  public record TenantInfo(String name, String telephone, String email) {}

  public record InstallationAddress(String nameOrFlat, String address) {}

  public record EngineerInfo(
      String name,
      String gasSafeNumber,
      String licenceCardNumber,
      String arrivalTime,
      String departureTime,
      String reportIssuedDateFormatted) {}

  public record ApplianceRow(
      int index,
      String location,
      String type,
      String make,
      String model,
      String serialNumber,
      String classCode,
      boolean landlordsAppliance,
      String flueType,
      String fluePerformance,
      String operatingPressure,
      String burnerPressure,
      String heatInputKw,
      String coPpm,
      String co2Percentage,
      String coToCo2Ratio,
      String safetyDevices,
      boolean safeToUse,
      boolean serviced,
      String additionalNotes) {}

  public record FinalChecks(
      String gasTightness,
      String pipeworkVisual,
      String emergencyControl,
      String bonding,
      String installationPass,
      String coAlarm,
      String smokeAlarm,
      String additionalObservations) {}

  public record FaultsAndRemedials(
      String faultsNotes,
      String remedialWork,
      String warningNoticeFixed,
      String applianceIsolated,
      String isolationReason) {}

  public record Signatures(
      String engineerName,
      String engineerSignedDateFormatted,
      String gasSafeNumber,
      String customerName,
      String customerSignedDateFormatted,
      String tenantSignedDateFormatted,
      String engineerNotes) {}
}

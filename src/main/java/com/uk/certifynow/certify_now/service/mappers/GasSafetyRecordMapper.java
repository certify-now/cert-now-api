package com.uk.certifynow.certify_now.service.mappers;

import com.uk.certifynow.certify_now.domain.GasSafetyAppliance;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.CombustionReadings;
import com.uk.certifynow.certify_now.domain.embeddable.CompanyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.InstallationDetails;
import com.uk.certifynow.certify_now.domain.embeddable.TenantDetails;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyApplianceRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.ApplianceSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.ClientSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.CombustionReadingsSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.CompanySummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.EngineerSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.FaultsAndRemedialsSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.FinalChecksSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.InstallationSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.MetadataSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.SignaturesSummary;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse.TenantSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct compile-time mapper for {@link GasSafetyRecord}. Replaces the ~400-line manual mapping
 * methods in {@link com.uk.certifynow.certify_now.service.inspection.GasSafetyRecordService}.
 */
@Mapper
public interface GasSafetyRecordMapper {

  // ── Request → Entity ─────────────────────────────────────────────────────────

  @Mapping(source = "certificate.certificateNumber", target = "certificateNumber")
  @Mapping(source = "certificate.certificateReference", target = "certificateReference")
  @Mapping(source = "certificate.certificateType", target = "certificateType")
  @Mapping(source = "certificate.issueDate", target = "issueDate")
  @Mapping(
      source = "certificate.nextInspectionDueOnOrBefore",
      target = "nextInspectionDueOnOrBefore")
  @Mapping(source = "certificate.numberOfAppliancesTested", target = "numberOfAppliancesTested")
  @Mapping(source = "certificate.qrCodeUrl", target = "qrCodeUrl")
  @Mapping(source = "certificate.verificationUrl", target = "verificationUrl")
  @Mapping(source = "engineerDetails.name", target = "engineerName")
  @Mapping(
      source = "engineerDetails.gasSafeRegistrationNumber",
      target = "engineerGasSafeNumber")
  @Mapping(
      source = "engineerDetails.engineerLicenceCardNumber",
      target = "engineerLicenceCardNumber")
  @Mapping(source = "engineerDetails.timeOfArrival", target = "timeOfArrival")
  @Mapping(source = "engineerDetails.timeOfDeparture", target = "timeOfDeparture")
  @Mapping(source = "engineerDetails.reportIssuedDate", target = "reportIssuedDate")
  @Mapping(source = "engineerDetails.engineerNotes", target = "engineerNotes")
  @Mapping(source = "finalChecks.gasTightnessPass", target = "gasTightnessPass")
  @Mapping(source = "finalChecks.gasPipeWorkVisualPass", target = "gasPipeWorkVisualPass")
  @Mapping(source = "finalChecks.emergencyControlAccessible", target = "emergencyControlAccessible")
  @Mapping(source = "finalChecks.equipotentialBonding", target = "equipotentialBonding")
  @Mapping(source = "finalChecks.installationPass", target = "installationPass")
  @Mapping(
      source = "finalChecks.coAlarmFittedWorkingSameRoom",
      target = "coAlarmFittedWorkingSameRoom")
  @Mapping(source = "finalChecks.smokeAlarmFittedWorking", target = "smokeAlarmFittedWorking")
  @Mapping(source = "finalChecks.additionalObservations", target = "additionalObservations")
  @Mapping(source = "faultsAndRemedials.faultsNotes", target = "faultsNotes")
  @Mapping(source = "faultsAndRemedials.remedialWorkTaken", target = "remedialWorkTaken")
  @Mapping(source = "faultsAndRemedials.warningNoticeFixed", target = "warningNoticeFixed")
  @Mapping(source = "faultsAndRemedials.applianceIsolated", target = "applianceIsolated")
  @Mapping(source = "faultsAndRemedials.isolationReason", target = "isolationReason")
  @Mapping(source = "signatures.engineerSigned", target = "engineerSigned")
  @Mapping(source = "signatures.engineerSignedDate", target = "engineerSignedDate")
  @Mapping(source = "signatures.customerName", target = "customerName")
  @Mapping(source = "signatures.customerSigned", target = "customerSigned")
  @Mapping(source = "signatures.customerSignedDate", target = "customerSignedDate")
  @Mapping(source = "signatures.tenantSigned", target = "tenantSigned")
  @Mapping(source = "signatures.tenantSignedDate", target = "tenantSignedDate")
  @Mapping(source = "signatures.privacyPolicyAccepted", target = "privacyPolicyAccepted")
  @Mapping(source = "metadata.createdBySoftware", target = "createdBySoftware")
  @Mapping(source = "metadata.version", target = "version")
  @Mapping(source = "metadata.generatedAt", target = "generatedAt")
  @Mapping(source = "metadata.platform", target = "platform")
  @Mapping(target = "job", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "dateCreated", ignore = true)
  @Mapping(target = "appliances", ignore = true)
  GasSafetyRecord toEntity(GasSafetyRecordRequest request);

  @Mapping(source = "index", target = "applianceIndex")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "gasSafetyRecord", ignore = true)
  GasSafetyAppliance toAppliance(GasSafetyApplianceRequest request);

  CompanyDetails toCompanyDetails(
      com.uk.certifynow.certify_now.rest.dto.inspection.CompanyDetailsRequest request);

  ClientDetails toClientDetails(
      com.uk.certifynow.certify_now.rest.dto.inspection.ClientDetailsRequest request);

  TenantDetails toTenantDetails(
      com.uk.certifynow.certify_now.rest.dto.inspection.TenantDetailsRequest request);

  InstallationDetails toInstallationDetails(
      com.uk.certifynow.certify_now.rest.dto.inspection.InstallationDetailsRequest request);

  CombustionReadings toCombustionReadings(
      com.uk.certifynow.certify_now.rest.dto.inspection.CombustionReadingsRequest request);

  // ── Entity → Response ────────────────────────────────────────────────────────

  @Mapping(source = "job.id", target = "jobId")
  @Mapping(source = "dateCreated", target = "createdAt")
  @Mapping(
      expression =
          "java(toEngineerSummary(record))",
      target = "engineer")
  @Mapping(source = "companyDetails", target = "companyDetails")
  @Mapping(source = "clientDetails", target = "clientDetails")
  @Mapping(source = "tenantDetails", target = "tenantDetails")
  @Mapping(source = "installationDetails", target = "installationDetails")
  @Mapping(
      expression =
          "java(toFinalChecksSummary(record))",
      target = "finalChecks")
  @Mapping(
      expression =
          "java(toFaultsSummary(record))",
      target = "faultsAndRemedials")
  @Mapping(
      expression =
          "java(toSignaturesSummary(record))",
      target = "signatures")
  @Mapping(
      expression =
          "java(toMetadataSummary(record))",
      target = "metadata")
  GasSafetyRecordResponse toResponse(GasSafetyRecord record);

  @Mapping(source = "applianceIndex", target = "index")
  @Mapping(source = "combustionReadings", target = "combustionReadings")
  ApplianceSummary toApplianceSummary(GasSafetyAppliance appliance);

  CombustionReadingsSummary toCombustionReadingsSummary(CombustionReadings readings);

  CompanySummary toCompanySummary(CompanyDetails cd);

  ClientSummary toClientSummary(ClientDetails cl);

  TenantSummary toTenantSummary(TenantDetails td);

  InstallationSummary toInstallationSummary(InstallationDetails inst);

  default EngineerSummary toEngineerSummary(final GasSafetyRecord record) {
    return new EngineerSummary(
        record.getEngineerName(),
        record.getEngineerGasSafeNumber(),
        record.getEngineerLicenceCardNumber(),
        record.getTimeOfArrival(),
        record.getTimeOfDeparture(),
        record.getReportIssuedDate(),
        record.getEngineerNotes());
  }

  default FinalChecksSummary toFinalChecksSummary(final GasSafetyRecord record) {
    return new FinalChecksSummary(
        record.getGasTightnessPass(),
        record.getGasPipeWorkVisualPass(),
        record.getEmergencyControlAccessible(),
        record.getEquipotentialBonding(),
        record.getInstallationPass(),
        record.getCoAlarmFittedWorkingSameRoom(),
        record.getSmokeAlarmFittedWorking(),
        record.getAdditionalObservations());
  }

  default FaultsAndRemedialsSummary toFaultsSummary(final GasSafetyRecord record) {
    return new FaultsAndRemedialsSummary(
        record.getFaultsNotes(),
        record.getRemedialWorkTaken(),
        record.getWarningNoticeFixed(),
        record.getApplianceIsolated(),
        record.getIsolationReason());
  }

  default SignaturesSummary toSignaturesSummary(final GasSafetyRecord record) {
    return new SignaturesSummary(
        record.getEngineerSigned(),
        record.getEngineerSignedDate(),
        record.getCustomerName(),
        record.getCustomerSigned(),
        record.getCustomerSignedDate(),
        record.getTenantSigned(),
        record.getTenantSignedDate(),
        record.getPrivacyPolicyAccepted());
  }

  default MetadataSummary toMetadataSummary(final GasSafetyRecord record) {
    return new MetadataSummary(
        record.getCreatedBySoftware(),
        record.getVersion(),
        record.getGeneratedAt(),
        record.getPlatform());
  }
}

package com.uk.certifynow.certify_now.service.inspection;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.GasSafetyAppliance;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.CombustionReadings;
import com.uk.certifynow.certify_now.domain.embeddable.CompanyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.InstallationDetails;
import com.uk.certifynow.certify_now.domain.embeddable.TenantDetails;
import com.uk.certifynow.certify_now.events.CertificateIssuedEvent;
import com.uk.certifynow.certify_now.events.job.JobStatusChangedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.rest.dto.inspection.CertificateDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.CombustionReadingsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EngineerDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.FaultsAndRemedialsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.FinalChecksRequest;
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
import com.uk.certifynow.certify_now.rest.dto.inspection.MetadataRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.SignaturesRequest;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GasSafetyRecordService {

  private final GasSafetyRecordRepository gasSafetyRecordRepository;
  private final JobRepository jobRepository;
  private final CertificateRepository certificateRepository;
  private final JobStatusHistoryRepository historyRepository;
  private final ApplicationEventPublisher publisher;

  public GasSafetyRecordService(
      final GasSafetyRecordRepository gasSafetyRecordRepository,
      final JobRepository jobRepository,
      final CertificateRepository certificateRepository,
      final JobStatusHistoryRepository historyRepository,
      final ApplicationEventPublisher publisher) {
    this.gasSafetyRecordRepository = gasSafetyRecordRepository;
    this.jobRepository = jobRepository;
    this.certificateRepository = certificateRepository;
    this.historyRepository = historyRepository;
    this.publisher = publisher;
  }

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public GasSafetyRecordResponse submitGasSafetyRecord(
      final UUID jobId, final UUID engineerId, final GasSafetyRecordRequest request) {

    // 1. Load job
    final Job job = jobRepository
        .findById(jobId)
        .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

    // 2. Validate job is in COMPLETED status
    final JobStatus currentStatus = JobStatus.fromString(job.getStatus());
    if (currentStatus != JobStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "JOB_NOT_COMPLETED",
          "Job must be in COMPLETED status to submit inspection data. Current status: "
              + job.getStatus());
    }

    // 3. Validate certificate type is GAS_SAFETY
    if (!"GAS_SAFETY".equals(job.getCertificateType())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_CERTIFICATE_TYPE",
          "This endpoint only accepts GAS_SAFETY jobs. Job type: " + job.getCertificateType());
    }

    // 4. Validate the authenticated user is the assigned engineer
    if (job.getEngineer() == null || !job.getEngineer().getId().equals(engineerId)) {
      throw new AccessDeniedException("Only the assigned engineer can submit inspection data");
    }

    // 5. Validate no existing gas safety record for this job
    if (gasSafetyRecordRepository.existsByJobId(jobId)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "DUPLICATE_SUBMISSION",
          "A gas safety record already exists for this job");
    }

    // 6. Validate appliance count matches
    final CertificateDetailsRequest cert = request.certificate();
    if (cert.numberOfAppliancesTested() != request.appliances().size()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "APPLIANCE_COUNT_MISMATCH",
          "numberOfAppliancesTested ("
              + cert.numberOfAppliancesTested()
              + ") does not match the number of appliances provided ("
              + request.appliances().size()
              + ")");
    }

    // 7. Map request to entity
    final GasSafetyRecord record = mapToEntity(job, request);
    final GasSafetyRecord saved = gasSafetyRecordRepository.save(record);

    // 8. Create Certificate entity
    final Certificate certificate = issueCertificate(job, saved);

    // 9. Supersede any existing active certificate for same property + type
    supersedePreviousCertificates(job, certificate);

    // 10. Transition job to CERTIFIED status (within this transaction for
    // atomicity).
    // The status change, history record, and events are all committed as one unit —
    // no post-commit listener is needed for the job status.
    job.setStatus("CERTIFIED");
    job.setCertifiedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    jobRepository.save(job);

    // 11. Record COMPLETED → CERTIFIED status history entry.
    recordHistory(job, "COMPLETED", "CERTIFIED", engineerId, "SYSTEM");

    // 12. Publish status-changed event (for audit/notification listeners)
    // and CertificateIssuedEvent (downstream: property compliance etc.).
    publisher.publishEvent(
        new JobStatusChangedEvent(jobId, "COMPLETED", "CERTIFIED", engineerId, "SYSTEM"));
    publisher.publishEvent(
        new CertificateIssuedEvent(
            jobId, certificate.getId(), job.getProperty().getId(), "GAS_SAFETY"));

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public GasSafetyRecordResponse getGasSafetyRecord(final UUID jobId, final UUID callerId) {
    final GasSafetyRecord record = gasSafetyRecordRepository
        .findByJobId(jobId)
        .orElseThrow(
            () -> new EntityNotFoundException("No gas safety record found for job: " + jobId));

    // Enforce ownership: only the assigned engineer, the customer who booked the
    // job, or an ADMIN may read inspection data.
    final Job job = record.getJob();
    final boolean isEngineer = job.getEngineer() != null && job.getEngineer().getId().equals(callerId);
    final boolean isCustomer = job.getCustomer() != null && job.getCustomer().getId().equals(callerId);
    if (!isEngineer && !isCustomer) {
      throw new AccessDeniedException(
          "You do not have permission to view this gas safety record");
    }

    return toResponse(record);
  }

  private Certificate issueCertificate(final Job job, final GasSafetyRecord record) {
    final Certificate certificate = new Certificate();
    certificate.setCertificateNumber(record.getCertificateNumber());
    certificate.setCertificateType("GAS_SAFETY");
    certificate.setIssuedAt(record.getIssueDate());
    certificate.setExpiryAt(record.getNextInspectionDueOnOrBefore());
    certificate.setStatus("ACTIVE");
    certificate.setResult("PASS");
    certificate.setJob(job);
    certificate.setProperty(job.getProperty());
    certificate.setIssuedByEngineer(job.getEngineer());
    certificate.setCreatedAt(OffsetDateTime.now());
    certificate.setUpdatedAt(OffsetDateTime.now());
    return certificateRepository.save(certificate);
  }

  private void supersedePreviousCertificates(final Job job, final Certificate newCertificate) {
    final UUID propertyId = job.getProperty().getId();
    final List<Certificate> activeCerts = certificateRepository.findByPropertyIdAndCertificateTypeAndStatus(
        propertyId, "GAS_SAFETY", "ACTIVE");
    for (final Certificate existing : activeCerts) {
      if (!existing.getId().equals(newCertificate.getId())) {
        existing.setStatus("SUPERSEDED");
        existing.setSupersededBy(newCertificate);
        existing.setUpdatedAt(OffsetDateTime.now());
        certificateRepository.save(existing);
      }
    }
  }

  // ---- Mapping: Request to Entity ----

  private GasSafetyRecord mapToEntity(final Job job, final GasSafetyRecordRequest request) {
    final GasSafetyRecord record = new GasSafetyRecord();
    record.setJob(job);

    // Certificate
    final CertificateDetailsRequest cert = request.certificate();
    record.setCertificateNumber(cert.certificateNumber());
    record.setCertificateReference(cert.certificateReference());
    record.setCertificateType(cert.certificateType());
    record.setIssueDate(cert.issueDate());
    record.setNextInspectionDueOnOrBefore(cert.nextInspectionDueOnOrBefore());
    record.setNumberOfAppliancesTested(cert.numberOfAppliancesTested());
    record.setQrCodeUrl(cert.qrCodeUrl());
    record.setVerificationUrl(cert.verificationUrl());

    // Engineer
    final EngineerDetailsRequest eng = request.engineerDetails();
    record.setEngineerName(eng.name());
    record.setEngineerGasSafeNumber(eng.gasSafeRegistrationNumber());
    record.setEngineerLicenceCardNumber(eng.engineerLicenceCardNumber());
    record.setTimeOfArrival(eng.timeOfArrival());
    record.setTimeOfDeparture(eng.timeOfDeparture());
    record.setReportIssuedDate(eng.reportIssuedDate());
    record.setEngineerNotes(eng.engineerNotes());

    // Company details
    if (request.companyDetails() != null) {
      final CompanyDetails cd = new CompanyDetails();
      cd.setTradingTitle(request.companyDetails().tradingTitle());
      cd.setAddressLine1(request.companyDetails().addressLine1());
      cd.setAddressLine2(request.companyDetails().addressLine2());
      cd.setAddressLine3(request.companyDetails().addressLine3());
      cd.setPostCode(request.companyDetails().postCode());
      cd.setGasSafeRegistrationNumber(request.companyDetails().gasSafeRegistrationNumber());
      cd.setCompanyPhone(request.companyDetails().companyPhone());
      cd.setCompanyEmail(request.companyDetails().companyEmail());
      record.setCompanyDetails(cd);
    }

    // Client details
    if (request.clientDetails() != null) {
      final ClientDetails cld = new ClientDetails();
      cld.setName(request.clientDetails().name());
      cld.setAddressLine1(request.clientDetails().addressLine1());
      cld.setAddressLine2(request.clientDetails().addressLine2());
      cld.setAddressLine3(request.clientDetails().addressLine3());
      cld.setPostCode(request.clientDetails().postCode());
      cld.setTelephone(request.clientDetails().telephone());
      cld.setEmail(request.clientDetails().email());
      record.setClientDetails(cld);
    }

    // Tenant details
    if (request.tenantDetails() != null) {
      final TenantDetails ten = new TenantDetails();
      ten.setName(request.tenantDetails().name());
      ten.setEmail(request.tenantDetails().email());
      ten.setTelephone(request.tenantDetails().telephone());
      record.setTenantDetails(ten);
    }

    // Installation details
    if (request.installationDetails() != null) {
      final InstallationDetails instd = new InstallationDetails();
      instd.setNameOrFlat(request.installationDetails().nameOrFlat());
      instd.setAddressLine1(request.installationDetails().addressLine1());
      instd.setAddressLine2(request.installationDetails().addressLine2());
      instd.setAddressLine3(request.installationDetails().addressLine3());
      instd.setPostCode(request.installationDetails().postCode());
      instd.setTelephone(request.installationDetails().telephone());
      instd.setEmail(request.installationDetails().email());
      record.setInstallationDetails(instd);
    }

    // Final checks
    if (request.finalChecks() != null) {
      final FinalChecksRequest fc = request.finalChecks();
      record.setGasTightnessPass(fc.gasTightnessPass());
      record.setGasPipeWorkVisualPass(fc.gasPipeWorkVisualPass());
      record.setEmergencyControlAccessible(fc.emergencyControlAccessible());
      record.setEquipotentialBonding(fc.equipotentialBonding());
      record.setInstallationPass(fc.installationPass());
      record.setCoAlarmFittedWorkingSameRoom(fc.coAlarmFittedWorkingSameRoom());
      record.setSmokeAlarmFittedWorking(fc.smokeAlarmFittedWorking());
      record.setAdditionalObservations(fc.additionalObservations());
    }

    // Faults and remedials
    if (request.faultsAndRemedials() != null) {
      final FaultsAndRemedialsRequest fr = request.faultsAndRemedials();
      record.setFaultsNotes(fr.faultsNotes());
      record.setRemedialWorkTaken(fr.remedialWorkTaken());
      record.setWarningNoticeFixed(fr.warningNoticeFixed());
      record.setApplianceIsolated(fr.applianceIsolated());
      record.setIsolationReason(fr.isolationReason());
    }

    // Signatures
    if (request.signatures() != null) {
      final SignaturesRequest sig = request.signatures();
      record.setEngineerSigned(sig.engineerSigned());
      record.setEngineerSignedDate(sig.engineerSignedDate());
      record.setCustomerName(sig.customerName());
      record.setCustomerSigned(sig.customerSigned());
      record.setCustomerSignedDate(sig.customerSignedDate());
      record.setTenantSigned(sig.tenantSigned());
      record.setTenantSignedDate(sig.tenantSignedDate());
      record.setPrivacyPolicyAccepted(sig.privacyPolicyAccepted());
    }

    // Metadata
    if (request.metadata() != null) {
      final MetadataRequest meta = request.metadata();
      record.setCreatedBySoftware(meta.createdBySoftware());
      record.setVersion(meta.version());
      record.setGeneratedAt(meta.generatedAt());
      record.setPlatform(meta.platform());
    }

    // Appliances
    final List<GasSafetyAppliance> appliances = new ArrayList<>();
    for (final GasSafetyApplianceRequest appReq : request.appliances()) {
      final GasSafetyAppliance appliance = mapApplianceToEntity(appReq, record);
      appliances.add(appliance);
    }
    record.setAppliances(appliances);

    return record;
  }

  private GasSafetyAppliance mapApplianceToEntity(
      final GasSafetyApplianceRequest req, final GasSafetyRecord record) {
    final GasSafetyAppliance a = new GasSafetyAppliance();
    a.setGasSafetyRecord(record);
    a.setApplianceIndex(req.index());
    a.setLocation(req.location());
    a.setApplianceType(req.applianceType());
    a.setMake(req.make());
    a.setModel(req.model());
    a.setSerialNumber(req.serialNumber());
    a.setLandlordsAppliance(req.landlordsAppliance());
    a.setInspectionType(req.inspectionType());
    a.setApplianceInspected(req.applianceInspected());
    a.setApplianceServiced(req.applianceServiced());
    a.setApplianceSafeToUse(req.applianceSafeToUse());
    a.setClassificationCode(req.classificationCode());
    a.setClassificationDescription(req.classificationDescription());
    a.setFlueType(req.flueType());
    a.setVentilationProvisionSatisfactory(req.ventilationProvisionSatisfactory());
    a.setFlueVisualConditionTerminationSatisfactory(
        req.flueVisualConditionTerminationSatisfactory());
    a.setFluePerformanceTests(req.fluePerformanceTests());
    a.setSpillageTest(req.spillageTest());
    a.setOperatingPressureMbar(req.operatingPressureMbar());
    a.setBurnerPressureMbar(req.burnerPressureMbar());
    a.setGasRate(req.gasRate());
    a.setHeatInputKw(req.heatInputKw());
    a.setSafetyDevicesCorrectOperation(req.safetyDevicesCorrectOperation());
    a.setEmergencyControlAccessible(req.emergencyControlAccessible());
    a.setGasInstallationPipeworkVisualInspectionSatisfactory(
        req.gasInstallationPipeworkVisualInspectionSatisfactory());
    a.setGasTightnessSatisfactory(req.gasTightnessSatisfactory());
    a.setEquipotentialBonding(req.equipotentialBonding());
    a.setWarningNoticeFixed(req.warningNoticeFixed());
    a.setAdditionalNotes(req.additionalNotes());

    if (req.combustionReadings() != null) {
      final CombustionReadingsRequest cr = req.combustionReadings();
      final CombustionReadings readings = new CombustionReadings();
      readings.setCoPpm(cr.coPpm());
      readings.setCo2Percentage(cr.co2Percentage());
      readings.setCoToCo2Ratio(cr.coToCo2Ratio());
      readings.setCombustionLow(cr.combustionLow());
      readings.setCombustionHigh(cr.combustionHigh());
      a.setCombustionReadings(readings);
    }

    return a;
  }

  // ---- Mapping: Entity to Response ----

  private GasSafetyRecordResponse toResponse(final GasSafetyRecord record) {
    final EngineerSummary engineer = new EngineerSummary(
        record.getEngineerName(),
        record.getEngineerGasSafeNumber(),
        record.getEngineerLicenceCardNumber(),
        record.getTimeOfArrival(),
        record.getTimeOfDeparture(),
        record.getReportIssuedDate(),
        record.getEngineerNotes());

    final CompanySummary company = mapCompanySummary(record.getCompanyDetails());
    final ClientSummary client = mapClientSummary(record.getClientDetails());
    final TenantSummary tenant = mapTenantSummary(record.getTenantDetails());
    final InstallationSummary installation = mapInstallationSummary(record.getInstallationDetails());

    final List<ApplianceSummary> appliances = record.getAppliances().stream().map(this::mapApplianceSummary).toList();

    final FinalChecksSummary finalChecks = new FinalChecksSummary(
        record.getGasTightnessPass(),
        record.getGasPipeWorkVisualPass(),
        record.getEmergencyControlAccessible(),
        record.getEquipotentialBonding(),
        record.getInstallationPass(),
        record.getCoAlarmFittedWorkingSameRoom(),
        record.getSmokeAlarmFittedWorking(),
        record.getAdditionalObservations());

    final FaultsAndRemedialsSummary faults = new FaultsAndRemedialsSummary(
        record.getFaultsNotes(),
        record.getRemedialWorkTaken(),
        record.getWarningNoticeFixed(),
        record.getApplianceIsolated(),
        record.getIsolationReason());

    final SignaturesSummary signatures = new SignaturesSummary(
        record.getEngineerSigned(),
        record.getEngineerSignedDate(),
        record.getCustomerName(),
        record.getCustomerSigned(),
        record.getCustomerSignedDate(),
        record.getTenantSigned(),
        record.getTenantSignedDate(),
        record.getPrivacyPolicyAccepted());

    final MetadataSummary metadata = new MetadataSummary(
        record.getCreatedBySoftware(),
        record.getVersion(),
        record.getGeneratedAt(),
        record.getPlatform());

    return new GasSafetyRecordResponse(
        record.getId(),
        record.getJob().getId(),
        record.getCertificateNumber(),
        record.getCertificateReference(),
        record.getCertificateType(),
        record.getIssueDate(),
        record.getNextInspectionDueOnOrBefore(),
        record.getNumberOfAppliancesTested(),
        record.getQrCodeUrl(),
        record.getVerificationUrl(),
        engineer,
        company,
        client,
        tenant,
        installation,
        appliances,
        finalChecks,
        faults,
        signatures,
        metadata,
        record.getDateCreated());
  }

  private CompanySummary mapCompanySummary(final CompanyDetails cd) {
    if (cd == null) {
      return null;
    }
    return new CompanySummary(
        cd.getTradingTitle(),
        cd.getAddressLine1(),
        cd.getAddressLine2(),
        cd.getAddressLine3(),
        cd.getPostCode(),
        cd.getGasSafeRegistrationNumber(),
        cd.getCompanyPhone(),
        cd.getCompanyEmail());
  }

  private ClientSummary mapClientSummary(final ClientDetails cl) {
    if (cl == null) {
      return null;
    }
    return new ClientSummary(
        cl.getName(),
        cl.getAddressLine1(),
        cl.getAddressLine2(),
        cl.getAddressLine3(),
        cl.getPostCode(),
        cl.getTelephone(),
        cl.getEmail());
  }

  private TenantSummary mapTenantSummary(final TenantDetails td) {
    if (td == null) {
      return null;
    }
    return new TenantSummary(td.getName(), td.getEmail(), td.getTelephone());
  }

  private InstallationSummary mapInstallationSummary(final InstallationDetails inst) {
    if (inst == null) {
      return null;
    }
    return new InstallationSummary(
        inst.getNameOrFlat(),
        inst.getAddressLine1(),
        inst.getAddressLine2(),
        inst.getAddressLine3(),
        inst.getPostCode(),
        inst.getTelephone(),
        inst.getEmail());
  }

  private ApplianceSummary mapApplianceSummary(final GasSafetyAppliance a) {
    CombustionReadingsSummary cr = null;
    if (a.getCombustionReadings() != null) {
      final CombustionReadings readings = a.getCombustionReadings();
      cr = new CombustionReadingsSummary(
          readings.getCoPpm(),
          readings.getCo2Percentage(),
          readings.getCoToCo2Ratio(),
          readings.getCombustionLow(),
          readings.getCombustionHigh());
    }
    return new ApplianceSummary(
        a.getId(),
        a.getApplianceIndex(),
        a.getLocation(),
        a.getApplianceType(),
        a.getMake(),
        a.getModel(),
        a.getSerialNumber(),
        a.getLandlordsAppliance(),
        a.getInspectionType(),
        a.getApplianceInspected(),
        a.getApplianceServiced(),
        a.getApplianceSafeToUse(),
        a.getClassificationCode(),
        a.getClassificationDescription(),
        a.getFlueType(),
        a.getVentilationProvisionSatisfactory(),
        a.getFlueVisualConditionTerminationSatisfactory(),
        a.getFluePerformanceTests(),
        a.getSpillageTest(),
        a.getOperatingPressureMbar(),
        a.getBurnerPressureMbar(),
        a.getGasRate(),
        a.getHeatInputKw(),
        a.getSafetyDevicesCorrectOperation(),
        a.getEmergencyControlAccessible(),
        a.getGasInstallationPipeworkVisualInspectionSatisfactory(),
        a.getGasTightnessSatisfactory(),
        a.getEquipotentialBonding(),
        a.getWarningNoticeFixed(),
        a.getAdditionalNotes(),
        cr);
  }

  private void recordHistory(
      final Job job,
      final String fromStatus,
      final String toStatus,
      final UUID actorId,
      final String actorType) {
    final com.uk.certifynow.certify_now.domain.JobStatusHistory h = new com.uk.certifynow.certify_now.domain.JobStatusHistory();
    h.setJob(job);
    h.setFromStatus(fromStatus);
    h.setToStatus(toStatus);
    h.setActorId(actorId);
    h.setActorType(actorType);
    h.setCreatedAt(java.time.OffsetDateTime.now());
    historyRepository.save(h);
  }
}

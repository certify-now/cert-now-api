package com.uk.certifynow.certify_now.service.inspection;

import com.uk.certifynow.certify_now.domain.Certificate;
import com.uk.certifynow.certify_now.domain.GasSafetyAppliance;
import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.enums.CertificateResult;
import com.uk.certifynow.certify_now.domain.enums.CertificateStatus;
import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.events.CertificateIssuedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse;
import com.uk.certifynow.certify_now.service.job.JobService;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import com.uk.certifynow.certify_now.service.mappers.GasSafetyRecordMapper;
import java.time.Clock;
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
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final JobService jobService;
  private final GasSafetyRecordMapper gasSafetyRecordMapper;

  public GasSafetyRecordService(
      final GasSafetyRecordRepository gasSafetyRecordRepository,
      final JobRepository jobRepository,
      final CertificateRepository certificateRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock,
      final JobService jobService,
      final GasSafetyRecordMapper gasSafetyRecordMapper) {
    this.gasSafetyRecordRepository = gasSafetyRecordRepository;
    this.jobRepository = jobRepository;
    this.certificateRepository = certificateRepository;
    this.publisher = publisher;
    this.clock = clock;
    this.jobService = jobService;
    this.gasSafetyRecordMapper = gasSafetyRecordMapper;
  }

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public GasSafetyRecordResponse submitGasSafetyRecord(
      final UUID jobId, final UUID engineerId, final GasSafetyRecordRequest request) {

    // 1. Load job
    final Job job =
        jobRepository
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
    if (!CertificateType.GAS_SAFETY.name().equals(job.getCertificateType())) {
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
    final int expectedCount = request.certificate().numberOfAppliancesTested();
    if (expectedCount != request.appliances().size()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "APPLIANCE_COUNT_MISMATCH",
          "numberOfAppliancesTested ("
              + expectedCount
              + ") does not match the number of appliances provided ("
              + request.appliances().size()
              + ")");
    }

    // 7. Map request to entity via MapStruct
    final GasSafetyRecord record = gasSafetyRecordMapper.toEntity(request);
    record.setJob(job);
    record.setCompanyDetails(gasSafetyRecordMapper.toCompanyDetails(request.companyDetails()));
    record.setClientDetails(gasSafetyRecordMapper.toClientDetails(request.clientDetails()));
    record.setTenantDetails(gasSafetyRecordMapper.toTenantDetails(request.tenantDetails()));
    record.setInstallationDetails(
        gasSafetyRecordMapper.toInstallationDetails(request.installationDetails()));
    final List<GasSafetyAppliance> appliances = new ArrayList<>();
    for (final var appReq : request.appliances()) {
      final GasSafetyAppliance appliance = gasSafetyRecordMapper.toAppliance(appReq);
      appliance.setGasSafetyRecord(record);
      if (appReq.combustionReadings() != null) {
        appliance.setCombustionReadings(
            gasSafetyRecordMapper.toCombustionReadings(appReq.combustionReadings()));
      }
      appliances.add(appliance);
    }
    record.setAppliances(appliances);
    final GasSafetyRecord saved = gasSafetyRecordRepository.save(record);

    // 8. Create Certificate entity
    final Certificate certificate = issueCertificate(job, saved);

    // 9. Supersede any existing active certificate for same property + type
    supersedePreviousCertificates(job, certificate);

    // 10. Delegate job certification to JobService (validates transition, records history,
    // publishes events). Joins the current transaction via default REQUIRED propagation.
    jobService.certifyJob(jobId);

    // 11. Publish CertificateIssuedEvent (downstream: property compliance etc.)
    publisher.publishEvent(
        new CertificateIssuedEvent(
            jobId, certificate.getId(), job.getProperty().getId(), "GAS_SAFETY"));

    return gasSafetyRecordMapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public GasSafetyRecordResponse getGasSafetyRecord(final UUID jobId, final UUID callerId) {
    final GasSafetyRecord record =
        gasSafetyRecordRepository
            .findByJobId(jobId)
            .orElseThrow(
                () -> new EntityNotFoundException("No gas safety record found for job: " + jobId));

    // Enforce ownership: only the assigned engineer, the customer who booked the
    // job, or an ADMIN may read inspection data.
    final Job job = record.getJob();
    final boolean isEngineer =
        job.getEngineer() != null && job.getEngineer().getId().equals(callerId);
    final boolean isCustomer =
        job.getCustomer() != null && job.getCustomer().getId().equals(callerId);
    if (!isEngineer && !isCustomer) {
      throw new AccessDeniedException("You do not have permission to view this gas safety record");
    }

    return gasSafetyRecordMapper.toResponse(record);
  }

  private Certificate issueCertificate(final Job job, final GasSafetyRecord record) {
    final Certificate certificate = new Certificate();
    certificate.setCertificateNumber(record.getCertificateNumber());
    certificate.setCertificateType(CertificateType.GAS_SAFETY.name());
    certificate.setIssuedAt(record.getIssueDate());
    certificate.setExpiryAt(record.getNextInspectionDueOnOrBefore());
    certificate.setStatus(CertificateStatus.ACTIVE.name());
    certificate.setResult(CertificateResult.PASS.name());
    certificate.setJob(job);
    certificate.setProperty(job.getProperty());
    certificate.setIssuedByEngineer(job.getEngineer());
    certificate.setCreatedAt(OffsetDateTime.now(clock));
    certificate.setUpdatedAt(OffsetDateTime.now(clock));
    return certificateRepository.save(certificate);
  }

  private void supersedePreviousCertificates(final Job job, final Certificate newCertificate) {
    final UUID propertyId = job.getProperty().getId();
    final List<Certificate> activeCerts =
        certificateRepository.findByPropertyIdAndCertificateTypeAndStatus(
            propertyId, CertificateType.GAS_SAFETY.name(), CertificateStatus.ACTIVE.name());
    for (final Certificate existing : activeCerts) {
      if (!existing.getId().equals(newCertificate.getId())) {
        existing.setStatus(CertificateStatus.SUPERSEDED.name());
        existing.setSupersededBy(newCertificate);
        existing.setUpdatedAt(OffsetDateTime.now(clock));
        certificateRepository.save(existing);
      }
    }
  }
}

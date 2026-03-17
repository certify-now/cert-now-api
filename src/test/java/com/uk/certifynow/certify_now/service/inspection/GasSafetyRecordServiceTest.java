package com.uk.certifynow.certify_now.service.inspection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.GasSafetyRecord;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.CertificateRepository;
import com.uk.certifynow.certify_now.repos.GasSafetyRecordRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.rest.dto.inspection.CertificateDetailsRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyApplianceRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest;
import com.uk.certifynow.certify_now.service.job.JobService;
import com.uk.certifynow.certify_now.service.mappers.GasSafetyRecordMapper;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class GasSafetyRecordServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private GasSafetyRecordRepository gasSafetyRecordRepository;
  @Mock private JobRepository jobRepository;
  @Mock private CertificateRepository certificateRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobService jobService;
  @Mock private GasSafetyRecordMapper gasSafetyRecordMapper;

  private GasSafetyRecordService service;

  @BeforeEach
  void setUp() {
    service =
        new GasSafetyRecordService(
            gasSafetyRecordRepository,
            jobRepository,
            certificateRepository,
            publisher,
            clock,
            jobService,
            gasSafetyRecordMapper);
  }

  @Test
  void submitGasSafetyRecord_jobNotCompleted_throwsConflict() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildInProgress(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(
            () ->
                service.submitGasSafetyRecord(
                    job.getId(), engineer.getId(), buildRequest(1, List.of(buildAppliance()))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("JOB_NOT_COMPLETED");
  }

  @Test
  void submitGasSafetyRecord_wrongCertType_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);
    job.setCertificateType("EPC");

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(
            () ->
                service.submitGasSafetyRecord(
                    job.getId(), engineer.getId(), buildRequest(1, List.of(buildAppliance()))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("INVALID_CERTIFICATE_TYPE");
  }

  @Test
  void submitGasSafetyRecord_wrongEngineer_throwsAccessDenied() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User other = TestUserBuilder.buildActiveEngineer(UUID.randomUUID(), "other@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(
            () ->
                service.submitGasSafetyRecord(
                    job.getId(), other.getId(), buildRequest(1, List.of(buildAppliance()))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void submitGasSafetyRecord_duplicateSubmission_throwsConflict() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(gasSafetyRecordRepository.existsByJobId(job.getId())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.submitGasSafetyRecord(
                    job.getId(), engineer.getId(), buildRequest(1, List.of(buildAppliance()))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("DUPLICATE_SUBMISSION");
  }

  @Test
  void submitGasSafetyRecord_applianceCountMismatch_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(gasSafetyRecordRepository.existsByJobId(job.getId())).thenReturn(false);

    // Declares 2 but provides only 1
    assertThatThrownBy(
            () ->
                service.submitGasSafetyRecord(
                    job.getId(), engineer.getId(), buildRequest(2, List.of(buildAppliance()))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("APPLIANCE_COUNT_MISMATCH");
  }

  @Test
  void getGasSafetyRecord_unrelatedUser_throwsAccessDenied() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User unrelated = TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "x@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);

    final GasSafetyRecord record = new GasSafetyRecord();
    record.setJob(job);

    when(gasSafetyRecordRepository.findByJobId(job.getId())).thenReturn(Optional.of(record));

    assertThatThrownBy(() -> service.getGasSafetyRecord(job.getId(), unrelated.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  private GasSafetyRecordRequest buildRequest(
      final int numberOfAppliancesTested, final List<GasSafetyApplianceRequest> appliances) {
    final CertificateDetailsRequest cert =
        new CertificateDetailsRequest(
            "CERT-001",
            null,
            "CP12",
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2027, 1, 15),
            numberOfAppliancesTested,
            null,
            null);

    return new GasSafetyRecordRequest(
        cert, null, null, null, null, null, appliances, null, null, null, null);
  }

  private GasSafetyApplianceRequest buildAppliance() {
    return new GasSafetyApplianceRequest(
        0,
        "Kitchen",
        "Boiler",
        "Worcester",
        "Greenstar",
        "SN123",
        true,
        "SERVICE_AND_SAFETY",
        true,
        false,
        true,
        "CF",
        "Currently Safe",
        "OPEN_FLUED",
        true,
        true,
        "PASS",
        "PASS",
        null,
        null,
        null,
        null,
        true,
        true,
        true,
        true,
        true,
        false,
        null,
        null);
  }
}

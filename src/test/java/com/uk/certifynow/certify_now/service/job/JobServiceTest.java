package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobHistoryService jobHistoryService;
  @Mock private JobCancellationService jobCancellationService;

  private JobService jobService;
  private JobResponseMapper jobResponseMapper;

  @BeforeEach
  void setUp() {
    jobResponseMapper = new JobResponseMapper(new ObjectMapper());
    jobService =
        new JobService(
            jobRepository,
            paymentRepository,
            matchLogRepository,
            publisher,
            clock,
            jobResponseMapper,
            jobHistoryService,
            jobCancellationService);
  }

  // ─── STATE TRANSITIONS ────────────────────────────────────────────────────────

  @Test
  void acceptJob_matchedToAccepted_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
    when(matchLogRepository.findByJobIdAndEngineerId(job.getId(), engineer.getId()))
        .thenReturn(Optional.empty());

    final LocalDate scheduledDate = LocalDate.now(clock).plusDays(2);
    final AcceptJobRequest req = new AcceptJobRequest(scheduledDate, "MORNING");

    final JobResponse response = jobService.acceptJob(job.getId(), engineer.getId(), req);

    assertThat(response.status()).isEqualTo(JobStatus.ACCEPTED.name());
    assertThat(response.scheduledDate()).isEqualTo(scheduledDate);
  }

  @Test
  void acceptJob_scheduledDateTooFar_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    final AcceptJobRequest req = new AcceptJobRequest(LocalDate.now(clock).plusDays(20), "MORNING");

    assertThatThrownBy(() -> jobService.acceptJob(job.getId(), engineer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo("INVALID_SCHEDULE_DATE"));
  }

  @Test
  void acceptJob_scheduledDateInPast_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    final AcceptJobRequest req = new AcceptJobRequest(LocalDate.now(clock).minusDays(1), "MORNING");

    assertThatThrownBy(() -> jobService.acceptJob(job.getId(), engineer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo("INVALID_SCHEDULE_DATE"));
  }

  @Test
  void markEnRoute_acceptedToEnRoute_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildAccepted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final JobResponse response = jobService.markEnRoute(job.getId(), engineer.getId());

    assertThat(response.status()).isEqualTo(JobStatus.EN_ROUTE.name());
  }

  @Test
  void startJob_enRouteToInProgress_withinGpsRange_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildEnRoute(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final StartJobRequest req =
        new StartJobRequest(new BigDecimal("51.5074"), new BigDecimal("-0.1278"));
    final JobResponse response = jobService.startJob(job.getId(), engineer.getId(), req);

    assertThat(response.status()).isEqualTo(JobStatus.IN_PROGRESS.name());
  }

  @Test
  void startJob_enRouteToInProgress_noCoordinates_skipsGpsCheck() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    // coordinates are null by default — GPS check should be skipped gracefully
    final Job job = TestJobBuilder.buildEnRoute(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final StartJobRequest req =
        new StartJobRequest(new BigDecimal("51.5074"), new BigDecimal("-0.1278"));
    final JobResponse response = jobService.startJob(job.getId(), engineer.getId(), req);

    assertThat(response.status()).isEqualTo(JobStatus.IN_PROGRESS.name());
  }

  @Test
  void startJob_gpsTooFar_throwsBusinessException() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    // Property at London (51.5074, -0.1278)
    property.setCoordinates(makePoint(-0.1278, 51.5074));
    final Job job = TestJobBuilder.buildEnRoute(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    // Engineer at Manchester (~260km away)
    final StartJobRequest req =
        new StartJobRequest(new BigDecimal("53.4808"), new BigDecimal("-2.2426"));

    assertThatThrownBy(() -> jobService.startJob(job.getId(), engineer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo("GPS_TOO_FAR"));
  }

  @Test
  void completeJob_inProgressToCompleted_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildInProgress(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final JobResponse response = jobService.completeJob(job.getId(), engineer.getId());

    assertThat(response.status()).isEqualTo(JobStatus.COMPLETED.name());
  }

  @Test
  void certifyJob_completedToCertified_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCompleted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    jobService.certifyJob(job.getId());

    verify(jobRepository).save(any());
    verify(publisher).publishEvent(any(Object.class));
  }

  // ─── INVALID TRANSITIONS ──────────────────────────────────────────────────────

  @Test
  void acceptJob_fromCreatedStatus_throwsInvalidTransition() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    job.setEngineer(engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    final AcceptJobRequest req = new AcceptJobRequest(LocalDate.now(clock).plusDays(1), "MORNING");

    assertThatThrownBy(() -> jobService.acceptJob(job.getId(), engineer.getId(), req))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void completeJob_fromAcceptedStatus_throwsInvalidTransition() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildAccepted(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> jobService.completeJob(job.getId(), engineer.getId()))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  // ─── AUTHORISATION ────────────────────────────────────────────────────────────

  @Test
  void getById_customerCanReadOwnJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final JobResponse response =
        jobService.getById(job.getId(), customer.getId(), UserRole.CUSTOMER);

    assertThat(response.id()).isEqualTo(job.getId());
  }

  @Test
  void getById_engineerCanReadAssignedJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final JobResponse response =
        jobService.getById(job.getId(), engineer.getId(), UserRole.ENGINEER);

    assertThat(response.id()).isEqualTo(job.getId());
  }

  @Test
  void getById_unrelatedUser_throwsAccessDenied() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User unrelated = TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "x@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> jobService.getById(job.getId(), unrelated.getId(), UserRole.CUSTOMER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void getById_adminCanReadAnyJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User admin = TestUserBuilder.buildAdmin();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final JobResponse response = jobService.getById(job.getId(), admin.getId(), UserRole.ADMIN);

    assertThat(response.id()).isEqualTo(job.getId());
  }

  // ─── LISTING ──────────────────────────────────────────────────────────────────

  @Test
  void listJobs_customer_returnsOwnJobs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final Page<Job> page = new PageImpl<>(List.of(job));

    when(jobRepository.findByCustomerWithFilters(
            eq(customer.getId()), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(page);

    final var result =
        jobService.listJobs(customer.getId(), UserRole.CUSTOMER, null, null, Pageable.unpaged());

    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listJobs_engineer_returnsAssignedJobs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);
    final Page<Job> page = new PageImpl<>(List.of(job));

    when(jobRepository.findByEngineerWithFilters(
            eq(engineer.getId()), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(page);

    final var result =
        jobService.listJobs(engineer.getId(), UserRole.ENGINEER, null, null, Pageable.unpaged());

    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listJobs_admin_returnsAllJobs() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final Page<Job> page = new PageImpl<>(List.of(job));

    when(jobRepository.findAllWithFilters(isNull(), isNull(), any(Pageable.class)))
        .thenReturn(page);

    final var result =
        jobService.listJobs(UUID.randomUUID(), UserRole.ADMIN, null, null, Pageable.unpaged());

    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listJobs_withStatusFilter_filtersCorrectly() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Page<Job> emptyPage = Page.empty();

    when(jobRepository.findByCustomerWithFilters(
            any(), eq(List.of(JobStatus.CREATED.name())), isNull(), any(Pageable.class)))
        .thenReturn(emptyPage);

    final var result =
        jobService.listJobs(
            customer.getId(),
            UserRole.CUSTOMER,
            JobStatus.CREATED.name(),
            null,
            Pageable.unpaged());

    assertThat(result.getTotalElements()).isZero();
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private static Point makePoint(final double lng, final double lat) {
    return new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(lng, lat));
  }
}

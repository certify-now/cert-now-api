package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.job.JobCreatedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.MatchJobRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.auth.UserRole;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private UserRepository userRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private PricingCalculator pricingCalculator;
  @Mock private ReferenceNumberGenerator referenceNumberGenerator;
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
            userRepository,
            propertyRepository,
            paymentRepository,
            matchLogRepository,
            pricingCalculator,
            referenceNumberGenerator,
            publisher,
            new ObjectMapper(),
            clock,
            jobResponseMapper,
            jobHistoryService,
            jobCancellationService);
  }

  // ─── CREATE JOB ──────────────────────────────────────────────────────────────

  @Test
  void createJob_happyPath_returnsCreatedJob() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final PriceBreakdown price = buildPrice();

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(pricingCalculator.calculate("GAS_SAFETY", property.getId(), "STANDARD")).thenReturn(price);
    when(referenceNumberGenerator.generate()).thenReturn("CN-00000001");
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", "STANDARD", null, null, null);

    final JobResponse response = jobService.createJob(customer.getId(), req);

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("CREATED");
    assertThat(response.certificateType()).isEqualTo("GAS_SAFETY");

    final ArgumentCaptor<JobCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCreatedEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(customer.getId());
  }

  @Test
  void createJob_propertyNotOwned_throwsAccessDenied() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User otherCustomer =
        TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "other@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(otherCustomer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, null);

    assertThatThrownBy(() -> jobService.createJob(customer.getId(), req))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void createJob_inactiveProperty_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildInactive(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, null);

    assertThatThrownBy(() -> jobService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("inactive");
  }

  @Test
  void createJob_gasSafety_noGasSupply_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithElectric(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, null);

    assertThatThrownBy(() -> jobService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("gas supply");
  }

  @Test
  void createJob_invalidPreferredDay_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final var badDay =
        new com.uk.certifynow.certify_now.rest.dto.job.DayAvailability(
            "FUNDAY", List.of("MORNING"));
    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, List.of(badDay));

    assertThatThrownBy(() -> jobService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("INVALID_PREFERRED_DAY");
  }

  @Test
  void createJob_invalidPreferredTimeSlot_throwsBadRequest() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    final var badSlot =
        new com.uk.certifynow.certify_now.rest.dto.job.DayAvailability("MON", List.of("MIDNIGHT"));
    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, List.of(badSlot));

    assertThatThrownBy(() -> jobService.createJob(customer.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("INVALID_PREFERRED_TIME_SLOT");
  }

  @Test
  void createJob_referenceNumberCollision_retriesUpTo3Times() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final PriceBreakdown price = buildPrice();

    when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(pricingCalculator.calculate(anyString(), any(), anyString())).thenReturn(price);
    when(referenceNumberGenerator.generate()).thenReturn("REF");
    // First two attempts throw; third succeeds
    when(jobRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenThrow(new DataIntegrityViolationException("dup"))
        .thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final CreateJobRequest req =
        new CreateJobRequest(property.getId(), "GAS_SAFETY", null, null, null, null);

    final JobResponse response = jobService.createJob(customer.getId(), req);

    assertThat(response).isNotNull();
    verify(jobRepository, times(3)).save(any());
  }

  // ─── STATE TRANSITIONS ────────────────────────────────────────────────────────

  @Test
  void matchJob_createdToMatched_success() {
    final User admin = TestUserBuilder.buildAdmin();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(userRepository.findById(engineer.getId())).thenReturn(Optional.of(engineer));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final MatchJobRequest req = new MatchJobRequest(engineer.getId());
    final JobResponse response = jobService.matchJob(job.getId(), admin.getId(), req);

    assertThat(response.status()).isEqualTo("MATCHED");
  }

  @Test
  void matchJob_nonEngineerUser_throwsBadRequest() {
    final User admin = TestUserBuilder.buildAdmin();
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User notEngineer =
        TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "c2@example.com");
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(userRepository.findById(notEngineer.getId())).thenReturn(Optional.of(notEngineer));

    final MatchJobRequest req = new MatchJobRequest(notEngineer.getId());

    assertThatThrownBy(() -> jobService.matchJob(job.getId(), admin.getId(), req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("engineer");
  }

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

    assertThat(response.status()).isEqualTo("ACCEPTED");
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
        .hasMessageContaining("INVALID_SCHEDULE_DATE");
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
        .hasMessageContaining("INVALID_SCHEDULE_DATE");
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

    assertThat(response.status()).isEqualTo("EN_ROUTE");
  }

  @Test
  void startJob_enRouteToInProgress_success() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildEnRoute(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

    final var req =
        new com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest(
            new BigDecimal("51.5074"), new BigDecimal("-0.1278"));
    final JobResponse response = jobService.startJob(job.getId(), engineer.getId(), req);

    assertThat(response.status()).isEqualTo("IN_PROGRESS");
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

    assertThat(response.status()).isEqualTo("COMPLETED");
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
    verify(publisher).publishEvent(any());
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
            eq(customer.getId()), anyList(), isNull(), any(Pageable.class)))
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
            eq(engineer.getId()), anyList(), isNull(), any(Pageable.class)))
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

    when(jobRepository.findAllWithFilters(anyList(), isNull(), any(Pageable.class)))
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
            any(), eq(List.of("CREATED")), isNull(), any(Pageable.class)))
        .thenReturn(emptyPage);

    final var result =
        jobService.listJobs(
            customer.getId(), UserRole.CUSTOMER, "CREATED", null, Pageable.unpaged());

    assertThat(result.getTotalElements()).isZero();
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private PriceBreakdown buildPrice() {
    return new PriceBreakdown(
        9900,
        0,
        0,
        0,
        9900,
        new BigDecimal("0.200"),
        1980,
        7920,
        new PriceBreakdown.Breakdown(List.of(), "STANDARD", BigDecimal.ONE));
  }
}

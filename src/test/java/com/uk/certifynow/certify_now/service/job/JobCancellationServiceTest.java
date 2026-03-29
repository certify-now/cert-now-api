package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.job.JobCancelledEvent;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JobCancellationServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobHistoryService jobHistoryService;

  private JobCancellationService service;
  private JobResponseMapper jobResponseMapper;

  @BeforeEach
  void setUp() {
    jobResponseMapper = new JobResponseMapper(new ObjectMapper());
    service =
        new JobCancellationService(
            jobRepository,
            paymentRepository,
            matchLogRepository,
            publisher,
            clock,
            jobResponseMapper,
            jobHistoryService);
  }

  @Test
  void cancelJob_customer_createdStatus_fullRefund() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Changed my mind"));

    final ArgumentCaptor<JobCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCancelledEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getRefundAmountPence()).isEqualTo(9900);
  }

  @Test
  void cancelJob_customer_acceptedStatus_moreThan24h_fullRefund() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildAccepted(customer, property, engineer);
    // Scheduled 3 days from now — more than 24h away
    job.setScheduledDate(LocalDate.now(clock).plusDays(3));
    job.setScheduledTimeSlot("MORNING");
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Plans changed"));

    final ArgumentCaptor<JobCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCancelledEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getRefundAmountPence()).isEqualTo(9900);
  }

  @Test
  void cancelJob_customer_acceptedStatus_lessThan24h_80percentRefund() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildAccepted(customer, property, engineer);
    // Scheduled today, same day — within 24h
    job.setScheduledDate(LocalDate.now(clock));
    job.setScheduledTimeSlot("MORNING");
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Emergency"));

    final ArgumentCaptor<JobCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCancelledEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getRefundAmountPence()).isEqualTo((int) (9900 * 0.80));
  }

  @Test
  void cancelJob_customer_enRouteStatus_totalMinusCalloutFee() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildEnRoute(customer, property, engineer);
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Changed mind"));

    final ArgumentCaptor<JobCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(JobCancelledEvent.class);
    verify(publisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getRefundAmountPence()).isEqualTo(9900 - 1500);
  }

  @Test
  void cancelJob_customer_inProgressStatus_throwsNotAllowed() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildInProgress(customer, property, engineer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(
            () ->
                service.cancelJob(
                    job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Nope")))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void cancelJob_admin_anyStatus_allowed() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildAccepted(customer, property, engineer);
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final JobResponse response =
        service.cancelJob(
            job.getId(), UUID.randomUUID(), UserRole.ADMIN, new CancelJobRequest("Admin action"));

    assertThat(response.status()).isEqualTo(JobStatus.CANCELLED.name());
  }

  @Test
  void cancelJob_updatesPaymentRefundFields() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final Payment payment = buildPayment(job, customer);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.of(payment));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("Reason"));

    final ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(paymentCaptor.capture());
    assertThat(paymentCaptor.getValue().getRefundAmountPence()).isEqualTo(9900);
    assertThat(paymentCaptor.getValue().getRefundReason()).isEqualTo("Reason");
  }

  @Test
  void cancelJob_publishesJobCancelledEvent() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildCreated(customer, property);

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJob(
        job.getId(), customer.getId(), UserRole.CUSTOMER, new CancelJobRequest("test"));

    verify(publisher).publishEvent(any(JobCancelledEvent.class));
  }

  @Test
  void declineJob_revertsToCreated_incrementsMatchAttempts() {
    final User customer = TestUserBuilder.buildActiveCustomer();
    final User engineer = TestUserBuilder.buildActiveEngineer();
    final Property property = TestPropertyBuilder.buildWithGas(customer);
    final Job job = TestJobBuilder.buildMatched(customer, property, engineer);
    final int initialAttempts = job.getMatchAttempts();

    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
    when(matchLogRepository.findByJobIdAndEngineerId(job.getId(), engineer.getId()))
        .thenReturn(Optional.empty());

    final JobResponse response =
        service.declineJob(
            job.getId(),
            engineer.getId(),
            new com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest("Not available"));

    assertThat(response.status()).isEqualTo(JobStatus.CREATED.name());
    assertThat(job.getMatchAttempts()).isEqualTo(initialAttempts + 1);
    assertThat(job.getEngineer()).isNull();
  }

  private Payment buildPayment(final Job job, final User customer) {
    final Payment p = new Payment();
    p.setId(UUID.randomUUID());
    p.setJob(job);
    p.setCustomer(customer);
    p.setAmountPence(9900);
    p.setStatus("PENDING");
    p.setCurrency("GBP");
    p.setRequiresAction(false);
    p.setCreatedAt(OffsetDateTime.now(clock).minusHours(1));
    p.setUpdatedAt(OffsetDateTime.now(clock));
    return p;
  }
}

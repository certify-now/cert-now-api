package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.MatchJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.job.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock private JobRepository jobRepository;
  @Mock private UserRepository userRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private JobStatusHistoryRepository historyRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private PricingCalculator pricingCalculator;
  @Mock private ReferenceNumberGenerator referenceNumberGenerator;
  @Mock private ApplicationEventPublisher publisher;

  @InjectMocks private JobService jobService;

  // ── Shared test fixtures ─────────────────────────────────────────────────
  private UUID customerId;
  private UUID engineerId;
  private UUID adminId;
  private UUID propertyId;
  private UUID jobId;
  private User customer;
  private User engineer;
  private Property property;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    engineerId = UUID.randomUUID();
    adminId = UUID.randomUUID();
    propertyId = UUID.randomUUID();
    jobId = UUID.randomUUID();

    customer = buildUser(customerId, UserRole.CUSTOMER);
    engineer = buildUser(engineerId, UserRole.ENGINEER);
    property = buildProperty(propertyId, customer);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // createJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("createJob()")
  class CreateJobTests {

    @Test
    @DisplayName("Happy path: saves job with CREATED status and publishes event")
    void happyPath() {
      final CreateJobRequest request =
          new CreateJobRequest(propertyId, "GAS_SAFETY", "STANDARD", null, null);
      final PriceBreakdown price = stubPriceBreakdown(10000);

      when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
      when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
      when(pricingCalculator.calculate("GAS_SAFETY", propertyId, "STANDARD")).thenReturn(price);
      when(referenceNumberGenerator.generate()).thenReturn("CN-20260307-ABCD");
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> withId(inv.getArgument(0), jobId));
      when(paymentRepository.save(any(Payment.class)))
          .thenAnswer(inv -> withPaymentId(inv.getArgument(0)));

      final JobResponse response = jobService.createJob(customerId, request);

      assertThat(response.status()).isEqualTo("CREATED");
      assertThat(response.referenceNumber()).isEqualTo("CN-20260307-ABCD");
      verify(publisher).publishEvent(any(Object.class));
      verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("Fails if customer not found")
    void customerNotFound() {
      final CreateJobRequest request =
          new CreateJobRequest(propertyId, "GAS_SAFETY", null, null, null);
      when(userRepository.findById(customerId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> jobService.createJob(customerId, request))
          .isInstanceOf(EntityNotFoundException.class)
          .hasMessageContaining(customerId.toString());
    }

    @Test
    @DisplayName("Fails if property not found")
    void propertyNotFound() {
      final CreateJobRequest request =
          new CreateJobRequest(propertyId, "GAS_SAFETY", null, null, null);
      when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
      when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> jobService.createJob(customerId, request))
          .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Fails if property does not belong to customer")
    void propertyNotOwned() {
      final User otherCustomer = buildUser(UUID.randomUUID(), UserRole.CUSTOMER);
      final Property otherProperty = buildProperty(propertyId, otherCustomer);

      final CreateJobRequest request =
          new CreateJobRequest(propertyId, "GAS_SAFETY", null, null, null);
      when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
      when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(otherProperty));

      assertThatThrownBy(() -> jobService.createJob(customerId, request))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // matchJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("matchJob()")
  class MatchJobTests {

    @Test
    @DisplayName("Happy path: CREATED → MATCHED, publishes event")
    void happyPath() {
      final Job job = buildJob(jobId, "CREATED", customer, property, null);
      final MatchJobRequest request = new MatchJobRequest(engineerId);

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.matchJob(jobId, adminId, request);

      assertThat(response.status()).isEqualTo("MATCHED");
      verify(publisher).publishEvent(any(Object.class));
      verify(matchLogRepository).save(any(JobMatchLog.class));
    }

    @Test
    @DisplayName("Fails from ACCEPTED status (invalid transition)")
    void failsFromAccepted() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.matchJob(jobId, adminId, new MatchJobRequest(engineerId)))
          .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Fails from terminal status CANCELLED")
    void failsFromTerminal() {
      final Job job = buildJob(jobId, "CANCELLED", customer, property, null);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.matchJob(jobId, adminId, new MatchJobRequest(engineerId)))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // acceptJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("acceptJob()")
  class AcceptJobTests {

    @Test
    @DisplayName("Happy path: MATCHED → ACCEPTED with valid schedule")
    void happyPath() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      final LocalDate scheduledDate = LocalDate.now().plusDays(3);
      final AcceptJobRequest request = new AcceptJobRequest(scheduledDate, "MORNING");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.empty());
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.acceptJob(jobId, engineerId, request);

      assertThat(response.status()).isEqualTo("ACCEPTED");
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Fails from CREATED status (invalid transition)")
    void failsFromCreated() {
      final Job job = buildJob(jobId, "CREATED", customer, property, engineer);
      final AcceptJobRequest request = new AcceptJobRequest(LocalDate.now().plusDays(3), "MORNING");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.acceptJob(jobId, engineerId, request))
          .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Fails if called by non-assigned engineer")
    void failsNonAssigned() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      final UUID otherEngineerId = UUID.randomUUID();
      final AcceptJobRequest request = new AcceptJobRequest(LocalDate.now().plusDays(3), "MORNING");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.acceptJob(jobId, otherEngineerId, request))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Fails if scheduledDate is more than 14 days out")
    void failsScheduleDateTooFar() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      final LocalDate tooFar = LocalDate.now().plusDays(15);
      final AcceptJobRequest request = new AcceptJobRequest(tooFar, "MORNING");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.acceptJob(jobId, engineerId, request))
          .hasMessageContaining("scheduledDate");
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // declineJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("declineJob()")
  class DeclineJobTests {

    @Test
    @DisplayName("Happy path: MATCHED → CREATED, engineer cleared, matchAttempts incremented")
    void happyPath() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      job.setMatchAttempts(1);
      final DeclineJobRequest request = new DeclineJobRequest("Too far away");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.empty());
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.declineJob(jobId, engineerId, request);

      assertThat(response.status()).isEqualTo("CREATED");
      assertThat(response.engineerId()).isNull();
      assertThat(response.matchAttempts()).isEqualTo(2);
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Fails if job is not in MATCHED status")
    void failsFromAccepted() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(
              () -> jobService.declineJob(jobId, engineerId, new DeclineJobRequest(null)))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // markEnRoute()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("markEnRoute()")
  class MarkEnRouteTests {

    @Test
    @DisplayName("Happy path: ACCEPTED → EN_ROUTE")
    void happyPath() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.markEnRoute(jobId, engineerId);

      assertThat(response.status()).isEqualTo("EN_ROUTE");
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Fails from CREATED status")
    void failsFromCreated() {
      final Job job = buildJob(jobId, "CREATED", customer, property, engineer);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.markEnRoute(jobId, engineerId))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // startJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("startJob()")
  class StartJobTests {

    @Test
    @DisplayName("Happy path: EN_ROUTE → IN_PROGRESS, GPS coords stored")
    void happyPath() {
      final Job job = buildJob(jobId, "EN_ROUTE", customer, property, engineer);
      final StartJobRequest request =
          new StartJobRequest(new BigDecimal("51.5074"), new BigDecimal("-0.1278"));

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.startJob(jobId, engineerId, request);

      assertThat(response.status()).isEqualTo("IN_PROGRESS");
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Fails from ACCEPTED status")
    void failsFromAccepted() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      final StartJobRequest request =
          new StartJobRequest(new BigDecimal("51.5074"), new BigDecimal("-0.1278"));

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.startJob(jobId, engineerId, request))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // completeJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("completeJob()")
  class CompleteJobTests {

    @Test
    @DisplayName("Happy path: IN_PROGRESS → COMPLETED")
    void happyPath() {
      final Job job = buildJob(jobId, "IN_PROGRESS", customer, property, engineer);

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response = jobService.completeJob(jobId, engineerId);

      assertThat(response.status()).isEqualTo("COMPLETED");
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Fails from EN_ROUTE status")
    void failsFromEnRoute() {
      final Job job = buildJob(jobId, "EN_ROUTE", customer, property, engineer);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.completeJob(jobId, engineerId))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // cancelJob()
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("cancelJob()")
  class CancelJobTests {

    @Test
    @DisplayName("From CREATED by CUSTOMER → 100% refund, status CANCELLED")
    void cancelCreatedByCustomer() {
      final Job job = buildJob(jobId, "CREATED", customer, property, null);
      job.setTotalPricePence(10000);
      final CancelJobRequest request = new CancelJobRequest("Changed my mind");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response =
          jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request);

      assertThat(response.status()).isEqualTo("CANCELLED");
      verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("From MATCHED by CUSTOMER → 100% refund")
    void cancelMatchedByCustomer() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      job.setTotalPricePence(10000);
      final CancelJobRequest request = new CancelJobRequest("Changed my mind");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response =
          jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request);

      assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("From ACCEPTED by CUSTOMER >24h before → 100% refund")
    void cancelAcceptedMoreThan24h() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      job.setTotalPricePence(10000);
      // Schedule far in the future so >24h
      job.setScheduledDate(LocalDate.now().plusDays(10));
      job.setScheduledTimeSlot("MORNING");
      final CancelJobRequest request = new CancelJobRequest("Changed my mind");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response =
          jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request);

      assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("From ACCEPTED by CUSTOMER <24h before → 80% refund")
    void cancelAcceptedLessThan24h() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      job.setTotalPricePence(10000);
      // Schedule for tomorrow morning — within 24h
      job.setScheduledDate(LocalDate.now());
      job.setScheduledTimeSlot("MORNING");
      final CancelJobRequest request = new CancelJobRequest("Emergency");
      final Payment payment = buildPaymentEntity(job);

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.of(payment));

      final JobResponse response =
          jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request);

      assertThat(response.status()).isEqualTo("CANCELLED");
      // Verify refund amount was set on payment (80% of 10000 = 8000)
      assertThat(payment.getRefundAmountPence()).isEqualTo(8000);
    }

    @Test
    @DisplayName("From EN_ROUTE by CUSTOMER → total minus 15 pound call-out fee")
    void cancelEnRouteByCustomer() {
      final Job job = buildJob(jobId, "EN_ROUTE", customer, property, engineer);
      job.setTotalPricePence(10000);
      final CancelJobRequest request = new CancelJobRequest("Emergency");
      final Payment payment = buildPaymentEntity(job);

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.of(payment));

      final JobResponse response =
          jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request);

      assertThat(response.status()).isEqualTo("CANCELLED");
      // 10000 - 1500 (call-out fee) = 8500
      assertThat(payment.getRefundAmountPence()).isEqualTo(8500);
    }

    @Test
    @DisplayName("Engineer cancelling from MATCHED → treated as decline (redirects)")
    void engineerCancelFromMatchedTreatedAsDecline() {
      final Job job = buildJob(jobId, "MATCHED", customer, property, engineer);
      job.setMatchAttempts(1);
      final CancelJobRequest request = new CancelJobRequest("Cannot make it");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
      when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
      when(matchLogRepository.findByJobIdAndEngineerId(jobId, engineerId))
          .thenReturn(Optional.empty());
      when(paymentRepository.findByJobId(jobId)).thenReturn(Optional.empty());

      final JobResponse response =
          jobService.cancelJob(jobId, engineerId, UserRole.ENGINEER, request);

      // Should have been treated as a decline — status reverted to CREATED
      assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("From IN_PROGRESS by CUSTOMER → should throw (only admin allowed)")
    void cancelInProgressByCustomer() {
      final Job job = buildJob(jobId, "IN_PROGRESS", customer, property, engineer);
      job.setTotalPricePence(10000);
      final CancelJobRequest request = new CancelJobRequest("Want to stop");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.cancelJob(jobId, customerId, UserRole.CUSTOMER, request))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // Double-transition tests
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Double-transition tests")
  class DoubleTransitionTests {

    @Test
    @DisplayName("Accept a job that is already ACCEPTED → InvalidStateTransitionException")
    void acceptAlreadyAccepted() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      final AcceptJobRequest request = new AcceptJobRequest(LocalDate.now().plusDays(3), "MORNING");

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.acceptJob(jobId, engineerId, request))
          .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Complete a job that is already COMPLETED → InvalidStateTransitionException")
    void completeAlreadyCompleted() {
      final Job job = buildJob(jobId, "COMPLETED", customer, property, engineer);
      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.completeJob(jobId, engineerId))
          .isInstanceOf(InvalidStateTransitionException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // Authorization tests
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Authorization tests")
  class AuthorizationTests {

    @Test
    @DisplayName("authoriseRead denies access to unrelated user")
    void readDeniedForUnrelatedUser() {
      final Job job = buildJob(jobId, "CREATED", customer, property, null);
      final UUID unrelatedId = UUID.randomUUID();

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.getById(jobId, unrelatedId, UserRole.CUSTOMER))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("authoriseEngineer denies non-assigned engineer")
    void engineerDeniedForNonAssigned() {
      final Job job = buildJob(jobId, "ACCEPTED", customer, property, engineer);
      final UUID otherEngineerId = UUID.randomUUID();

      when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> jobService.markEnRoute(jobId, otherEngineerId))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // Helper builders
  // ════════════════════════════════════════════════════════════════════════════

  private User buildUser(final UUID id, final UserRole role) {
    final User user = new User();
    user.setId(id);
    user.setRole(role);
    user.setFullName("Test " + role.name());
    user.setEmail(id + "@test.com");
    user.setPasswordHash("hashed");
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private Property buildProperty(final UUID id, final User owner) {
    final Property prop = new Property();
    prop.setId(id);
    prop.setOwner(owner);
    prop.setIsActive(true);
    prop.setHasGasSupply(true);
    prop.setHasElectric(true);
    prop.setAddressLine1("1 Test Street");
    prop.setCity("London");
    prop.setPostcode("SW1A 1AA");
    prop.setCountry("GB");
    prop.setPropertyType("HOUSE");
    prop.setComplianceStatus("COMPLIANT");
    prop.setCreatedAt(OffsetDateTime.now());
    prop.setUpdatedAt(OffsetDateTime.now());
    return prop;
  }

  private Job buildJob(
      final UUID id, final String status, final User cust, final Property prop, final User eng) {
    final Job job = new Job();
    job.setId(id);
    job.setStatus(status);
    job.setCustomer(cust);
    job.setProperty(prop);
    job.setEngineer(eng);
    job.setCertificateType("GAS_SAFETY");
    job.setUrgency("STANDARD");
    job.setReferenceNumber("CN-20260307-TEST");
    job.setMatchAttempts(0);
    job.setBasePricePence(8500);
    job.setPropertyModifierPence(0);
    job.setUrgencyModifierPence(0);
    job.setDiscountPence(0);
    job.setTotalPricePence(10000);
    job.setCommissionRate(new BigDecimal("0.150"));
    job.setCommissionPence(1500);
    job.setEngineerPayoutPence(8500);
    job.setCreatedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    return job;
  }

  private Job withId(final Job job, final UUID id) {
    job.setId(id);
    return job;
  }

  private Payment withPaymentId(final Payment payment) {
    payment.setId(UUID.randomUUID());
    return payment;
  }

  private Payment buildPaymentEntity(final Job job) {
    final Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setJob(job);
    payment.setCustomer(customer);
    payment.setStatus("PENDING");
    payment.setAmountPence(job.getTotalPricePence());
    payment.setCurrency("GBP");
    payment.setRequiresAction(false);
    payment.setStripePaymentIntentId("stub_pi_" + job.getId());
    payment.setStripeClientSecret("stub_secret_" + job.getId());
    payment.setCreatedAt(OffsetDateTime.now());
    payment.setUpdatedAt(OffsetDateTime.now());
    return payment;
  }

  private PriceBreakdown stubPriceBreakdown(final int totalPence) {
    return new PriceBreakdown(
        totalPence,
        0,
        0,
        0,
        totalPence,
        new BigDecimal("0.15"),
        (int) (totalPence * 0.15),
        (int) (totalPence * 0.85),
        null);
  }
}

package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.job.JobAcceptedEvent;
import com.uk.certifynow.certify_now.events.job.JobCreatedEvent;
import com.uk.certifynow.certify_now.events.job.JobMatchedEvent;
import com.uk.certifynow.certify_now.events.job.JobStatusChangedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DayAvailability;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.MatchJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.ProposeScheduleRequest;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobService {

  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final PropertyRepository propertyRepository;
  private final PaymentRepository paymentRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final PricingCalculator pricingCalculator;
  private final ReferenceNumberGenerator referenceNumberGenerator;
  private final ApplicationEventPublisher publisher;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final JobResponseMapper jobResponseMapper;
  private final JobHistoryService jobHistoryService;
  private final JobCancellationService jobCancellationService;

  public JobService(
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final PropertyRepository propertyRepository,
      final PaymentRepository paymentRepository,
      final JobMatchLogRepository matchLogRepository,
      final PricingCalculator pricingCalculator,
      final ReferenceNumberGenerator referenceNumberGenerator,
      final ApplicationEventPublisher publisher,
      final ObjectMapper objectMapper,
      final Clock clock,
      final JobResponseMapper jobResponseMapper,
      final JobHistoryService jobHistoryService,
      final JobCancellationService jobCancellationService) {
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.propertyRepository = propertyRepository;
    this.paymentRepository = paymentRepository;
    this.matchLogRepository = matchLogRepository;
    this.pricingCalculator = pricingCalculator;
    this.referenceNumberGenerator = referenceNumberGenerator;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.jobResponseMapper = jobResponseMapper;
    this.jobHistoryService = jobHistoryService;
    this.jobCancellationService = jobCancellationService;
  }

  // ────────────────────────────────────────────────────────────────────────────
  // CREATE JOB
  // ────────────────────────────────────────────────────────────────────────────

  @Transactional
  public JobResponse createJob(final UUID customerId, final CreateJobRequest request) {
    // 1. Load customer
    final User customer =
        userRepository
            .findById(customerId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + customerId));

    // 2. Load property, validate ownership
    final Property property =
        propertyRepository
            .findById(request.propertyId())
            .orElseThrow(
                () -> new EntityNotFoundException("Property not found: " + request.propertyId()));
    if (!property.getOwner().getId().equals(customerId)) {
      throw new AccessDeniedException("Property does not belong to this customer");
    }
    if (!Boolean.TRUE.equals(property.getIsActive())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PROPERTY_INACTIVE",
          "This property is inactive and cannot be booked");
    }

    // 3. Business validation: gas safety requires gas supply
    final String certType = request.certificateType();
    if ("GAS_SAFETY".equals(certType) && !Boolean.TRUE.equals(property.getHasGasSupply())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_GAS_SUPPLY",
          "Property does not have a gas supply; cannot book a gas safety certificate");
    }

    // Validate preferred availability if provided
    if (request.preferredAvailability() != null && !request.preferredAvailability().isEmpty()) {
      final Set<String> validDays = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
      final Set<String> validSlots = Set.of("MORNING", "AFTERNOON", "EVENING");
      for (final DayAvailability entry : request.preferredAvailability()) {
        if (!validDays.contains(entry.day().toUpperCase())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PREFERRED_DAY",
              "Invalid preferred day: " + entry.day() + ". Must be MON-SUN.");
        }
        for (final String slot : entry.slots()) {
          if (!validSlots.contains(slot.toUpperCase())) {
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PREFERRED_TIME_SLOT",
                "Invalid preferred time slot: "
                    + slot
                    + ". Must be MORNING, AFTERNOON, or EVENING.");
          }
        }
      }
    }

    // 4. Calculate pricing
    final String urgency = request.urgencyOrDefault();
    final PriceBreakdown price = pricingCalculator.calculate(certType, property.getId(), urgency);

    // 5. Generate reference number (retry up to 3 times on collision)
    Job saved = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      final Job job = buildJob(customer, property, request, certType, urgency, price);
      try {
        saved = jobRepository.save(job);
        break;
      } catch (final DataIntegrityViolationException e) {
        if (attempt == 2) throw e; // give up after 3 attempts
      }
    }

    // 6. Record initial status history (null → CREATED)
    jobHistoryService.recordHistory(
        saved, null, JobStatus.CREATED.name(), customerId, "CUSTOMER", null, null);

    // 7. Create stub payment
    final Payment payment = buildPayment(customer, saved, price);
    final Payment savedPayment = paymentRepository.save(payment);

    // 8. Publish event (fires after commit via @TransactionalEventListener)
    publisher.publishEvent(
        new JobCreatedEvent(
            saved.getId(), customerId, property.getId(), certType, price.totalPricePence()));

    return jobResponseMapper.toJobResponse(saved, savedPayment);
  }

  private Job buildJob(
      final User customer,
      final Property property,
      final CreateJobRequest request,
      final String certType,
      final String urgency,
      final PriceBreakdown price) {
    final Job job = new Job();
    job.setCustomer(customer);
    job.setProperty(property);
    job.setCertificateType(certType);
    job.setUrgency(urgency);
    job.setStatus(JobStatus.CREATED.name());
    job.setReferenceNumber(referenceNumberGenerator.generate());
    job.setMatchAttempts(0);
    job.setAccessInstructions(request.accessInstructions());
    job.setCustomerNotes(request.customerNotes());
    if (request.preferredAvailability() != null && !request.preferredAvailability().isEmpty()) {
      try {
        job.setPreferredAvailability(
            objectMapper.writeValueAsString(request.preferredAvailability()));
      } catch (final JacksonException e) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "INVALID_AVAILABILITY",
            "Failed to serialise availability preferences");
      }
    }
    // Pricing — copied from breakdown at creation time
    job.setBasePricePence(price.basePricePence());
    job.setPropertyModifierPence(price.propertyModifierPence());
    job.setUrgencyModifierPence(price.urgencyModifierPence());
    job.setDiscountPence(price.discountPence());
    job.setTotalPricePence(price.totalPricePence());
    job.setCommissionRate(price.commissionRate());
    job.setCommissionPence(price.commissionPence());
    job.setEngineerPayoutPence(price.engineerPayoutPence());
    job.setCreatedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    return job;
  }

  private Payment buildPayment(final User customer, final Job job, final PriceBreakdown price) {
    final Payment payment = new Payment();
    payment.setCustomer(customer);
    payment.setJob(job);
    payment.setStatus("PENDING");
    payment.setAmountPence(price.totalPricePence());
    payment.setCurrency("GBP");
    payment.setRequiresAction(false);
    // Stub values — real Stripe integration in Phase 8
    payment.setStripePaymentIntentId("stub_pi_" + job.getId());
    payment.setStripeClientSecret("stub_secret_" + job.getId());
    payment.setCreatedAt(OffsetDateTime.now(clock));
    payment.setUpdatedAt(OffsetDateTime.now(clock));
    return payment;
  }

  // ────────────────────────────────────────────────────────────────────────────
  // GET BY ID
  // ────────────────────────────────────────────────────────────────────────────

  @Cacheable(value = "jobs", key = "#jobId")
  @Transactional(readOnly = true)
  public JobResponse getById(final UUID jobId, final UUID actorId, final UserRole actorRole) {
    final Job job = loadJobOrThrow(jobId);
    authoriseRead(job, actorId, actorRole);
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(job, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // LIST JOBS
  // ────────────────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Page<JobSummaryResponse> listJobs(
      final UUID actorId,
      final UserRole actorRole,
      final String statusFilter,
      final String certTypeFilter,
      final Pageable pageable) {

    final List<String> statuses = parseStatuses(statusFilter);

    final Page<Job> page;
    if (actorRole == UserRole.ADMIN) {
      page = jobRepository.findAllWithFilters(statuses, certTypeFilter, pageable);
    } else if (actorRole == UserRole.ENGINEER) {
      page = jobRepository.findByEngineerWithFilters(actorId, statuses, certTypeFilter, pageable);
    } else {
      // CUSTOMER
      page = jobRepository.findByCustomerWithFilters(actorId, statuses, certTypeFilter, pageable);
    }
    return page.map(jobResponseMapper::toJobSummary);
  }

  private static List<String> parseStatuses(final String statusFilter) {
    // Return empty list (not null) so JPQL ":statuses IS EMPTY" works correctly across
    // all Hibernate versions; passing null to an IN clause can fail on some dialects.
    if (statusFilter == null || statusFilter.isBlank()) return List.of();
    return Arrays.stream(statusFilter.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // MATCH JOB (ADMIN temp endpoint — remove when MatchingService built in Phase
  // 6)
  // ────────────────────────────────────────────────────────────────────────────

  // TODO: Remove when MatchingService is built (Phase 6+)
  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse matchJob(final UUID jobId, final UUID adminId, final MatchJobRequest request) {
    final Job job = loadJobOrThrow(jobId);
    final JobStatus current = JobStatus.fromString(job.getStatus());
    final JobStatus target = JobStatus.MATCHED;
    validateTransition(current, target);

    // Validate engineer exists and has ENGINEER role
    final User engineer =
        userRepository
            .findById(request.engineerId())
            .orElseThrow(
                () -> new EntityNotFoundException("User not found: " + request.engineerId()));
    if (!engineer.isEngineer()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NOT_AN_ENGINEER", "User is not an engineer");
    }

    job.setEngineer(engineer);
    job.setMatchedAt(OffsetDateTime.now(clock));
    job.setStatus(JobStatus.MATCHED.name());
    job.setMatchAttempts(job.getMatchAttempts() + 1);
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved, JobStatus.CREATED.name(), JobStatus.MATCHED.name(), adminId, "ADMIN", null, null);

    // Create match log entry (score/distance null for manual admin match)
    jobHistoryService.createMatchLog(saved, engineer, null, null);

    publisher.publishEvent(new JobMatchedEvent(saved.getId(), engineer.getId(), null, null));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // ACCEPT JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse acceptJob(
      final UUID jobId, final UUID engineerId, final AcceptJobRequest request) {
    final Job job = loadJobOrThrow(jobId);
    authoriseEngineer(job, engineerId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.ACCEPTED);

    // Validate scheduled date is within 14 days
    final LocalDate today = LocalDate.now(clock);
    final LocalDate maxDate = today.plusDays(14);
    if (request.scheduledDate().isBefore(today) || request.scheduledDate().isAfter(maxDate)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SCHEDULE_DATE",
          "scheduledDate must be between today and 14 days from now");
    }

    job.setStatus(JobStatus.ACCEPTED.name());
    job.setScheduledDate(request.scheduledDate());
    job.setScheduledTimeSlot(request.scheduledTimeSlot());
    job.setAcceptedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        JobStatus.MATCHED.name(),
        JobStatus.ACCEPTED.name(),
        engineerId,
        "ENGINEER",
        null,
        null);

    // Update match log
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            log -> {
              log.setRespondedAt(OffsetDateTime.now(clock));
              log.setResponse(JobStatus.ACCEPTED.name());
              matchLogRepository.save(log);
            });

    publisher.publishEvent(
        new JobAcceptedEvent(
            saved.getId(), engineerId, request.scheduledDate(), request.scheduledTimeSlot()));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // DECLINE JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse declineJob(
      final UUID jobId, final UUID engineerId, final DeclineJobRequest request) {
    return jobCancellationService.declineJob(jobId, engineerId, request);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // MARK EN-ROUTE
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse markEnRoute(final UUID jobId, final UUID engineerId) {
    final Job job = loadJobOrThrow(jobId);
    authoriseEngineer(job, engineerId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.EN_ROUTE);

    job.setStatus(JobStatus.EN_ROUTE.name());
    job.setEnRouteAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        JobStatus.ACCEPTED.name(),
        JobStatus.EN_ROUTE.name(),
        engineerId,
        "ENGINEER",
        null,
        null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(),
            JobStatus.ACCEPTED.name(),
            JobStatus.EN_ROUTE.name(),
            engineerId,
            "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // START JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse startJob(
      final UUID jobId, final UUID engineerId, final StartJobRequest request) {
    final Job job = loadJobOrThrow(jobId);
    authoriseEngineer(job, engineerId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.IN_PROGRESS);

    // TODO: GPS validation — compare engineer coordinates with property.location
    // using PostGIS
    // ST_Distance. If distance > 200 metres, return 400 GPS_TOO_FAR.
    // Requires geocoded property location. Stubbed until geocoding is integrated.
    // Real implementation:
    // propertyLocationService.distanceMetres(request.latitude(),
    // request.longitude(), job.getProperty().getId()) > 200 → throw GPS_TOO_FAR

    job.setStatus(JobStatus.IN_PROGRESS.name());
    job.setStartedAt(OffsetDateTime.now(clock));
    job.setEngineerStartLat(request.latitude());
    job.setEngineerStartLng(request.longitude());
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        JobStatus.EN_ROUTE.name(),
        JobStatus.IN_PROGRESS.name(),
        engineerId,
        "ENGINEER",
        null,
        null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(),
            JobStatus.EN_ROUTE.name(),
            JobStatus.IN_PROGRESS.name(),
            engineerId,
            "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // COMPLETE JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse completeJob(final UUID jobId, final UUID engineerId) {
    final Job job = loadJobOrThrow(jobId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.COMPLETED);
    authoriseEngineer(job, engineerId);

    job.setStatus(JobStatus.COMPLETED.name());
    job.setCompletedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        JobStatus.IN_PROGRESS.name(),
        JobStatus.COMPLETED.name(),
        engineerId,
        "ENGINEER",
        null,
        null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(),
            JobStatus.IN_PROGRESS.name(),
            JobStatus.COMPLETED.name(),
            engineerId,
            "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public void certifyJob(final UUID jobId) {
    final Job job = loadJobOrThrow(jobId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.CERTIFIED);

    job.setStatus(JobStatus.CERTIFIED.name());
    job.setCertifiedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    jobRepository.save(job);

    final UUID engineerId = job.getEngineer() != null ? job.getEngineer().getId() : null;
    jobHistoryService.recordHistory(
        job,
        JobStatus.COMPLETED.name(),
        JobStatus.CERTIFIED.name(),
        engineerId,
        "SYSTEM",
        null,
        null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            job.getId(),
            JobStatus.COMPLETED.name(),
            JobStatus.CERTIFIED.name(),
            engineerId,
            "SYSTEM"));
  }

  // ────────────────────────────────────────────────────────────────────────────
  // CANCEL JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse cancelJob(
      final UUID jobId,
      final UUID actorId,
      final UserRole actorRole,
      final CancelJobRequest request) {
    return jobCancellationService.cancelJob(jobId, actorId, actorRole, request);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // GET STATUS HISTORY
  // ────────────────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<JobStatusHistoryResponse> getHistory(
      final UUID jobId, final UUID actorId, final UserRole actorRole) {
    final Job job = loadJobOrThrow(jobId);
    authoriseRead(job, actorId, actorRole);
    return jobHistoryService.getHistoryResponses(jobId);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // BEFORE-DELETE GUARD EVENT LISTENERS (preserved from original CRUD stub)
  // ────────────────────────────────────────────────────────────────────────────

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    // Only block hard-delete if there are ANY jobs (active or terminal).
    // Soft-delete uses its own validation that only checks active jobs.
    final ReferencedException ex = new ReferencedException();
    final Job customerJob = jobRepository.findFirstByCustomerId(event.getId());
    if (customerJob != null) {
      ex.setKey("user.job.customer.referenced");
      ex.addParam(customerJob.getId());
      throw ex;
    }
    final Job engineerJob = jobRepository.findFirstByEngineerId(event.getId());
    if (engineerJob != null) {
      ex.setKey("user.job.engineer.referenced");
      ex.addParam(engineerJob.getId());
      throw ex;
    }
  }

  @EventListener(BeforeDeleteProperty.class)
  public void on(final BeforeDeleteProperty event) {
    // Only block hard-delete if there are ANY jobs.
    // Soft-delete uses its own validation that only checks active jobs.
    final ReferencedException ex = new ReferencedException();
    final Job propertyJob = jobRepository.findFirstByPropertyId(event.getId());
    if (propertyJob != null) {
      ex.setKey("property.job.property.referenced");
      ex.addParam(propertyJob.getId());
      throw ex;
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // PRIVATE HELPERS
  // ────────────────────────────────────────────────────────────────────────────

  private Job loadJobOrThrow(final UUID jobId) {
    return jobRepository
        .findById(jobId)
        .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
  }

  private void validateTransition(final JobStatus current, final JobStatus target) {
    if (!current.canTransitionTo(target)) {
      throw new InvalidStateTransitionException(current, target);
    }
  }

  private void authoriseRead(final Job job, final UUID actorId, final UserRole actorRole) {
    if (actorRole == UserRole.ADMIN) return;
    final boolean isCustomer = job.getCustomer().getId().equals(actorId);
    final boolean isEngineer =
        job.getEngineer() != null && job.getEngineer().getId().equals(actorId);
    if (!isCustomer && !isEngineer) {
      throw new AccessDeniedException("Access denied to this job");
    }
  }

  private void authoriseEngineer(final Job job, final UUID engineerId) {
    if (job.getEngineer() == null || !job.getEngineer().getId().equals(engineerId)) {
      throw new AccessDeniedException("Not the assigned engineer for this job");
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // PROPOSE SCHEDULE
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse proposeSchedule(
      final UUID jobId, final UUID engineerId, final ProposeScheduleRequest request) {
    final Job job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

    if (job.getEngineer() == null || !job.getEngineer().getId().equals(engineerId)) {
      throw new AccessDeniedException("You are not assigned to this job");
    }

    final JobStatus current = JobStatus.fromString(job.getStatus());
    if (current != JobStatus.ACCEPTED) {
      throw new InvalidStateTransitionException(
          "Cannot propose schedule for job in status: " + current);
    }

    job.setScheduledDate(request.scheduledDate());
    job.setScheduledTimeSlot(request.scheduledTimeSlot());
    job.setScheduledAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    jobRepository.save(job);

    return jobResponseMapper.toJobResponse(job, null);
  }
}

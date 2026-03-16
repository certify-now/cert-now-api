package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.job.JobAcceptedEvent;
import com.uk.certifynow.certify_now.events.job.JobCancelledEvent;
import com.uk.certifynow.certify_now.events.job.JobCreatedEvent;
import com.uk.certifynow.certify_now.events.job.JobMatchedEvent;
import com.uk.certifynow.certify_now.events.job.JobStatusChangedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobService {

  // Terminal statuses — no further transitions allowed
  private static final List<String> TERMINAL_STATUSES =
      List.of("COMPLETED", "CERTIFIED", "CANCELLED", "FAILED");

  // Call-out fee in pence (£15.00) charged when customer cancels after engineer
  // is en-route
  private static final int CALLOUT_FEE_PENCE = 1500;

  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final PropertyRepository propertyRepository;
  private final PaymentRepository paymentRepository;
  private final JobStatusHistoryRepository historyRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final PricingCalculator pricingCalculator;
  private final ReferenceNumberGenerator referenceNumberGenerator;
  private final ApplicationEventPublisher publisher;
  private final ObjectMapper objectMapper;

  public JobService(
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final PropertyRepository propertyRepository,
      final PaymentRepository paymentRepository,
      final JobStatusHistoryRepository historyRepository,
      final JobMatchLogRepository matchLogRepository,
      final PricingCalculator pricingCalculator,
      final ReferenceNumberGenerator referenceNumberGenerator,
      final ApplicationEventPublisher publisher,
      final ObjectMapper objectMapper) {
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.propertyRepository = propertyRepository;
    this.paymentRepository = paymentRepository;
    this.historyRepository = historyRepository;
    this.matchLogRepository = matchLogRepository;
    this.pricingCalculator = pricingCalculator;
    this.referenceNumberGenerator = referenceNumberGenerator;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
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
    recordHistory(saved, null, "CREATED", customerId, "CUSTOMER", null, null);

    // 7. Create stub payment
    final Payment payment = buildPayment(customer, saved, price);
    final Payment savedPayment = paymentRepository.save(payment);

    // 8. Publish event (fires after commit via @TransactionalEventListener)
    publisher.publishEvent(
        new JobCreatedEvent(
            saved.getId(), customerId, property.getId(), certType, price.totalPricePence()));

    return toJobResponse(saved, savedPayment);
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
    job.setStatus("CREATED");
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
    job.setCreatedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
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
    payment.setCreatedAt(OffsetDateTime.now());
    payment.setUpdatedAt(OffsetDateTime.now());
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
    return toJobResponse(job, payment);
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

    final Page<Job> page;
    if (actorRole == UserRole.ADMIN) {
      page = jobRepository.findAllWithFilters(statusFilter, certTypeFilter, pageable);
    } else if (actorRole == UserRole.ENGINEER) {
      if (statusFilter != null && certTypeFilter != null) {
        page =
            jobRepository.findByEngineerIdAndStatusAndCertificateTypeOrderByCreatedAtDesc(
                actorId, statusFilter, certTypeFilter, pageable);
      } else if (statusFilter != null) {
        page =
            jobRepository.findByEngineerIdAndStatusOrderByCreatedAtDesc(
                actorId, statusFilter, pageable);
      } else if (certTypeFilter != null) {
        page =
            jobRepository.findByEngineerIdAndCertificateTypeOrderByCreatedAtDesc(
                actorId, certTypeFilter, pageable);
      } else {
        page = jobRepository.findByEngineerIdOrderByCreatedAtDesc(actorId, pageable);
      }
    } else {
      // CUSTOMER
      if (statusFilter != null && certTypeFilter != null) {
        page =
            jobRepository.findByCustomerIdAndStatusAndCertificateTypeOrderByCreatedAtDesc(
                actorId, statusFilter, certTypeFilter, pageable);
      } else if (statusFilter != null) {
        page =
            jobRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(
                actorId, statusFilter, pageable);
      } else if (certTypeFilter != null) {
        page =
            jobRepository.findByCustomerIdAndCertificateTypeOrderByCreatedAtDesc(
                actorId, certTypeFilter, pageable);
      } else {
        page = jobRepository.findByCustomerIdOrderByCreatedAtDesc(actorId, pageable);
      }
    }
    return page.map(this::toJobSummary);
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
    job.setMatchedAt(OffsetDateTime.now());
    job.setStatus("MATCHED");
    job.setMatchAttempts(job.getMatchAttempts() + 1);
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, "CREATED", "MATCHED", adminId, "ADMIN", null, null);

    // Create match log entry (score/distance null for manual admin match)
    createMatchLog(saved, engineer, null, null);

    publisher.publishEvent(new JobMatchedEvent(saved.getId(), engineer.getId(), null, null));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
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
    final LocalDate today = LocalDate.now();
    final LocalDate maxDate = today.plusDays(14);
    if (request.scheduledDate().isBefore(today) || request.scheduledDate().isAfter(maxDate)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SCHEDULE_DATE",
          "scheduledDate must be between today and 14 days from now");
    }

    job.setStatus("ACCEPTED");
    job.setScheduledDate(request.scheduledDate());
    job.setScheduledTimeSlot(request.scheduledTimeSlot());
    job.setAcceptedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, "MATCHED", "ACCEPTED", engineerId, "ENGINEER", null, null);

    // Update match log
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            log -> {
              log.setRespondedAt(OffsetDateTime.now());
              log.setResponse("ACCEPTED");
              matchLogRepository.save(log);
            });

    publisher.publishEvent(
        new JobAcceptedEvent(
            saved.getId(), engineerId, request.scheduledDate(), request.scheduledTimeSlot()));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // DECLINE JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse declineJob(
      final UUID jobId, final UUID engineerId, final DeclineJobRequest request) {
    final Job job = loadJobOrThrow(jobId);
    authoriseEngineer(job, engineerId);
    // Decline is MATCHED → CREATED (revert)
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.CREATED);

    final String prevStatus = job.getStatus();
    job.setStatus("CREATED");
    job.setEngineer(null);
    job.setMatchedAt(null);
    job.setMatchAttempts(job.getMatchAttempts() + 1);
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, prevStatus, "CREATED", engineerId, "ENGINEER", request.reason(), null);

    // Update match log
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            log -> {
              log.setRespondedAt(OffsetDateTime.now());
              log.setResponse("DECLINED");
              log.setDeclineReason(request.reason());
              matchLogRepository.save(log);
            });

    publisher.publishEvent(
        new JobStatusChangedEvent(saved.getId(), prevStatus, "CREATED", engineerId, "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
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

    job.setStatus("EN_ROUTE");
    job.setEnRouteAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, "ACCEPTED", "EN_ROUTE", engineerId, "ENGINEER", null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(saved.getId(), "ACCEPTED", "EN_ROUTE", engineerId, "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
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

    job.setStatus("IN_PROGRESS");
    job.setStartedAt(OffsetDateTime.now());
    job.setEngineerStartLat(request.latitude());
    job.setEngineerStartLng(request.longitude());
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, "EN_ROUTE", "IN_PROGRESS", engineerId, "ENGINEER", null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(), "EN_ROUTE", "IN_PROGRESS", engineerId, "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
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

    job.setStatus("COMPLETED");
    job.setCompletedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    recordHistory(saved, "IN_PROGRESS", "COMPLETED", engineerId, "ENGINEER", null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(), "IN_PROGRESS", "COMPLETED", engineerId, "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void certifyJob(final UUID jobId) {
    final Job job = loadJobOrThrow(jobId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.CERTIFIED);

    job.setStatus("CERTIFIED");
    job.setCertifiedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    jobRepository.save(job);

    final UUID engineerId = job.getEngineer() != null ? job.getEngineer().getId() : null;
    recordHistory(job, "COMPLETED", "CERTIFIED", engineerId, "SYSTEM", null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(job.getId(), "COMPLETED", "CERTIFIED", engineerId, "SYSTEM"));
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
    final Job job = loadJobOrThrow(jobId);
    final CancellationActor actor = determineCancellationActor(job, actorId, actorRole);
    final JobStatus current = JobStatus.fromString(job.getStatus());

    // Special case: engineer cancelling from MATCHED state is treated as a decline
    if (actor == CancellationActor.ENGINEER && current == JobStatus.MATCHED) {
      return declineJob(jobId, actorId, new DeclineJobRequest(request.reason()));
    }

    // Validate cancellation is allowed for this actor + status
    validateCancellationPermission(actor, current);
    validateTransition(current, JobStatus.CANCELLED);

    // Calculate refund
    final int refundPence = calculateRefund(job, actor, current);

    final String prevStatus = job.getStatus();
    job.setStatus("CANCELLED");
    job.setCancelledAt(OffsetDateTime.now());
    job.setCancelledBy(actor.name());
    job.setCancellationReason(request.reason());
    job.setUpdatedAt(OffsetDateTime.now());
    final Job saved = jobRepository.save(job);

    // Update payment refund fields (actual Stripe refund happens in Phase 8)
    paymentRepository
        .findByJobId(jobId)
        .ifPresent(
            payment -> {
              payment.setRefundAmountPence(refundPence);
              payment.setRefundReason(request.reason());
              paymentRepository.save(payment);
            });

    final String metadataJson = "{\"refund_amount_pence\":" + refundPence + "}";
    recordHistory(
        saved, prevStatus, "CANCELLED", actorId, actor.name(), request.reason(), metadataJson);

    publisher.publishEvent(
        new JobCancelledEvent(saved.getId(), actor.name(), request.reason(), refundPence));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // GET STATUS HISTORY
  // ────────────────────────────────────────────────────────────────────────────

  public List<JobStatusHistoryResponse> getHistory(
      final UUID jobId, final UUID actorId, final UserRole actorRole) {
    final Job job = loadJobOrThrow(jobId);
    authoriseRead(job, actorId, actorRole);
    return historyRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
        .map(
            h ->
                new JobStatusHistoryResponse(
                    h.getId(),
                    h.getFromStatus(),
                    h.getToStatus(),
                    h.getActorId(),
                    h.getActorType(),
                    h.getReason(),
                    h.getMetadata(),
                    h.getCreatedAt()))
        .toList();
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

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    // Cascade is handled by FK ON DELETE CASCADE in the database — no action
    // required here.
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

  private CancellationActor determineCancellationActor(
      final Job job, final UUID actorId, final UserRole role) {
    if (role == UserRole.ADMIN) return CancellationActor.ADMIN;
    if (job.getCustomer().getId().equals(actorId)) return CancellationActor.CUSTOMER;
    if (job.getEngineer() != null && job.getEngineer().getId().equals(actorId)) {
      return CancellationActor.ENGINEER;
    }
    throw new AccessDeniedException("Access denied to cancel this job");
  }

  private void validateCancellationPermission(
      final CancellationActor actor, final JobStatus status) {
    final boolean allowed =
        switch (status) {
          case CREATED, AWAITING_ACCEPTANCE, ESCALATED ->
              actor == CancellationActor.CUSTOMER || actor == CancellationActor.ADMIN;
          case MATCHED -> true; // customer, engineer (handled as decline above), admin
          case ACCEPTED ->
              actor == CancellationActor.CUSTOMER
                  || actor == CancellationActor.ENGINEER
                  || actor == CancellationActor.ADMIN;
          case EN_ROUTE -> actor == CancellationActor.CUSTOMER || actor == CancellationActor.ADMIN;
          default -> actor == CancellationActor.ADMIN; // IN_PROGRESS, etc. — only admin
        };
    if (!allowed) {
      throw new InvalidStateTransitionException(
          "Cancellation not allowed for " + actor + " when job is in state " + status);
    }
  }

  private int calculateRefund(
      final Job job, final CancellationActor actor, final JobStatus status) {
    final int total = job.getTotalPricePence();
    return switch (status) {
      case CREATED, AWAITING_ACCEPTANCE, MATCHED, ESCALATED -> total; // 100% refund
      case ACCEPTED -> {
        // >24h before scheduled start: 100%, <24h: 80%
        final OffsetDateTime scheduledStart = scheduledStart(job);
        if (scheduledStart != null && OffsetDateTime.now().plusHours(24).isAfter(scheduledStart)) {
          yield (int) (total * 0.80); // 80% refund
        }
        yield total; // 100% refund
      }
      case EN_ROUTE -> Math.max(0, total - CALLOUT_FEE_PENCE); // minus £15 call-out fee
      default -> 0;
    };
  }

  private OffsetDateTime scheduledStart(final Job job) {
    if (job.getScheduledDate() == null || job.getScheduledTimeSlot() == null) return null;
    final LocalTime slotTime =
        switch (job.getScheduledTimeSlot()) {
          case "MORNING" -> LocalTime.of(8, 0);
          case "AFTERNOON" -> LocalTime.of(12, 0);
          case "EVENING" -> LocalTime.of(17, 0);
          default -> LocalTime.NOON;
        };
    return job.getScheduledDate().atTime(slotTime).atOffset(ZoneOffset.UTC);
  }

  private void recordHistory(
      final Job job,
      final String fromStatus,
      final String toStatus,
      final UUID actorId,
      final String actorType,
      final String reason,
      final String metadata) {
    final JobStatusHistory history = new JobStatusHistory();
    history.setJob(job);
    history.setFromStatus(fromStatus);
    history.setToStatus(toStatus);
    history.setActorId(actorId);
    history.setActorType(actorType);
    history.setReason(reason);
    history.setMetadata(metadata);
    history.setCreatedAt(OffsetDateTime.now());
    historyRepository.save(history);
  }

  private void createMatchLog(
      final Job job, final User engineer, final BigDecimal score, final BigDecimal distance) {
    final JobMatchLog log = new JobMatchLog();
    log.setJob(job);
    log.setEngineer(engineer);
    log.setMatchScore(score);
    log.setDistanceMiles(distance);
    log.setNotifiedAt(OffsetDateTime.now());
    log.setOfferedAt(OffsetDateTime.now());
    log.setCreatedAt(OffsetDateTime.now());
    matchLogRepository.save(log);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // MAPPERS
  // ────────────────────────────────────────────────────────────────────────────

  private JobResponse toJobResponse(final Job job, final Payment payment) {
    final Property prop = job.getProperty();
    final String propSummary =
        prop.getAddressLine1() + ", " + prop.getCity() + " " + prop.getPostcode();
    final User eng = job.getEngineer();
    final String engName = eng == null ? null : eng.getFullName();
    final UUID engId = eng == null ? null : eng.getId();

    final JobResponse.Pricing pricing =
        new JobResponse.Pricing(
            job.getBasePricePence(),
            job.getPropertyModifierPence(),
            job.getUrgencyModifierPence(),
            job.getDiscountPence(),
            job.getTotalPricePence(),
            job.getCommissionRate() == null ? 0.15 : job.getCommissionRate().doubleValue(),
            job.getCommissionPence(),
            job.getEngineerPayoutPence());

    final JobResponse.Payment paymentSummary =
        payment == null
            ? null
            : new JobResponse.Payment(
                payment.getId(),
                payment.getStatus(),
                payment.getStripeClientSecret(),
                payment.getAmountPence());

    final JobResponse.Timestamps timestamps =
        new JobResponse.Timestamps(
            job.getCreatedAt(),
            job.getMatchedAt(),
            job.getAcceptedAt(),
            job.getEnRouteAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCertifiedAt(),
            job.getCancelledAt());

    return new JobResponse(
        job.getId(),
        job.getReferenceNumber(),
        job.getCustomer().getId(),
        prop.getId(),
        propSummary,
        engId,
        engName,
        job.getCertificateType(),
        job.getStatus(),
        job.getUrgency(),
        job.getScheduledTimeSlot(),
        job.getScheduledDate(),
        job.getMatchAttempts(),
        job.getAccessInstructions(),
        job.getCustomerNotes(),
        parseAvailability(job.getPreferredAvailability()),
        job.getCancelledBy(),
        job.getCancellationReason(),
        pricing,
        paymentSummary,
        timestamps);
  }

  private JobSummaryResponse toJobSummary(final Job job) {
    final Property prop = job.getProperty();
    final String propSummary =
        prop == null
            ? null
            : prop.getAddressLine1() + ", " + prop.getCity() + " " + prop.getPostcode();
    final User eng = job.getEngineer();
    return new JobSummaryResponse(
        job.getId(),
        job.getReferenceNumber(),
        job.getCertificateType(),
        job.getStatus(),
        job.getUrgency(),
        job.getTotalPricePence(),
        job.getScheduledDate(),
        job.getScheduledTimeSlot(),
        propSummary,
        eng == null ? null : eng.getFullName(),
        job.getCreatedAt(),
        parseAvailability(job.getPreferredAvailability()));
  }

  private List<DayAvailability> parseAvailability(final String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<DayAvailability>>() {});
    } catch (final JacksonException e) {
      return List.of();
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
    job.setScheduledAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    jobRepository.save(job);

    return toJobResponse(job, null);
  }
}

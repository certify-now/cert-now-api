package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.job.JobAcceptedEvent;
import com.uk.certifynow.certify_now.events.job.JobStatusChangedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.ProposeScheduleRequest;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

  private static final Logger log = LoggerFactory.getLogger(JobService.class);

  /** Maximum allowed distance (in metres) between engineer GPS and property coordinates. */
  private static final double GPS_MAX_DISTANCE_METRES = 200.0;

  private final JobRepository jobRepository;
  private final PaymentRepository paymentRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final JobResponseMapper jobResponseMapper;
  private final JobHistoryService jobHistoryService;
  private final JobCancellationService jobCancellationService;

  public JobService(
      final JobRepository jobRepository,
      final PaymentRepository paymentRepository,
      final JobMatchLogRepository matchLogRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock,
      final JobResponseMapper jobResponseMapper,
      final JobHistoryService jobHistoryService,
      final JobCancellationService jobCancellationService) {
    this.jobRepository = jobRepository;
    this.paymentRepository = paymentRepository;
    this.matchLogRepository = matchLogRepository;
    this.publisher = publisher;
    this.clock = clock;
    this.jobResponseMapper = jobResponseMapper;
    this.jobHistoryService = jobHistoryService;
    this.jobCancellationService = jobCancellationService;
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
    // Return null when no filter so JPQL ":statuses IS NULL" short-circuits the IN clause.
    // Hibernate 7 rejects the same named parameter used twice in the same query (IS EMPTY + IN),
    // so we use IS NULL instead and pass null to signal "no filter".
    if (statusFilter == null || statusFilter.isBlank()) return null;
    final List<String> result =
        Arrays.stream(statusFilter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    return result.isEmpty() ? null : result;
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
        ActorType.ENGINEER.name(),
        null,
        null);

    // Update match log
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            matchLog -> {
              matchLog.setRespondedAt(OffsetDateTime.now(clock));
              matchLog.setResponse(JobStatus.ACCEPTED.name());
              matchLogRepository.save(matchLog);
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
    return transitionAndPublish(
        jobId,
        engineerId,
        ActorType.ENGINEER.name(),
        JobStatus.EN_ROUTE,
        job -> job.setEnRouteAt(OffsetDateTime.now(clock)));
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

    // GPS proximity validation
    validateGpsProximity(job, request);

    final String prev = job.getStatus();
    job.setStatus(JobStatus.IN_PROGRESS.name());
    job.setStartedAt(OffsetDateTime.now(clock));
    job.setEngineerStartLat(request.latitude());
    job.setEngineerStartLng(request.longitude());
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        prev,
        JobStatus.IN_PROGRESS.name(),
        engineerId,
        ActorType.ENGINEER.name(),
        null,
        null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            saved.getId(),
            prev,
            JobStatus.IN_PROGRESS.name(),
            engineerId,
            ActorType.ENGINEER.name()));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // COMPLETE JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse completeJob(final UUID jobId, final UUID engineerId) {
    return transitionAndPublish(
        jobId,
        engineerId,
        ActorType.ENGINEER.name(),
        JobStatus.COMPLETED,
        job -> job.setCompletedAt(OffsetDateTime.now(clock)));
  }

  // ────────────────────────────────────────────────────────────────────────────
  // CERTIFY JOB
  // ────────────────────────────────────────────────────────────────────────────

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public void certifyJob(final UUID jobId) {
    final Job job = loadJobOrThrow(jobId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.CERTIFIED);

    final String prev = job.getStatus();
    job.setStatus(JobStatus.CERTIFIED.name());
    job.setCertifiedAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    jobRepository.save(job);

    final UUID engineerId = job.getEngineer() != null ? job.getEngineer().getId() : null;
    jobHistoryService.recordHistory(
        job, prev, JobStatus.CERTIFIED.name(), engineerId, ActorType.SYSTEM.name(), null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(
            job.getId(), prev, JobStatus.CERTIFIED.name(), engineerId, ActorType.SYSTEM.name()));
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

  // ────────────────────────────────────────────────────────────────────────────
  // PRIVATE HELPERS
  // ────────────────────────────────────────────────────────────────────────────

  private JobResponse transitionAndPublish(
      final UUID jobId,
      final UUID actorId,
      final String actorType,
      final JobStatus target,
      final Consumer<Job> mutator) {
    final Job job = loadJobOrThrow(jobId);
    if (ActorType.ENGINEER.name().equals(actorType)) {
      authoriseEngineer(job, actorId);
    }
    final String prev = job.getStatus();
    validateTransition(JobStatus.fromString(prev), target);
    mutator.accept(job);
    job.setStatus(target.name());
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);
    jobHistoryService.recordHistory(saved, prev, target.name(), actorId, actorType, null, null);
    publisher.publishEvent(
        new JobStatusChangedEvent(saved.getId(), prev, target.name(), actorId, actorType));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  private void validateGpsProximity(final Job job, final StartJobRequest request) {
    final Property property = job.getProperty();
    final Point coordinates = property.getCoordinates();

    if (coordinates == null) {
      log.warn(
          "Property {} has no geocoded coordinates; skipping GPS proximity check for job {}",
          property.getId(),
          job.getId());
      return;
    }

    final double propertyLat = coordinates.getY();
    final double propertyLng = coordinates.getX();
    final double engineerLat = request.latitude().doubleValue();
    final double engineerLng = request.longitude().doubleValue();

    final double distanceMetres =
        haversineMetres(propertyLat, propertyLng, engineerLat, engineerLng);

    if (distanceMetres > GPS_MAX_DISTANCE_METRES) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "GPS_TOO_FAR",
          String.format(
              "Engineer is %.0fm from the property; must be within %.0fm to start the job",
              distanceMetres, GPS_MAX_DISTANCE_METRES));
    }
  }

  /** Haversine formula — returns the great-circle distance in metres between two WGS84 points. */
  static double haversineMetres(
      final double lat1, final double lng1, final double lat2, final double lng2) {
    final double earthRadiusMetres = 6_371_000.0;
    final double dLat = Math.toRadians(lat2 - lat1);
    final double dLng = Math.toRadians(lng2 - lng1);
    final double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return earthRadiusMetres * c;
  }

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
}

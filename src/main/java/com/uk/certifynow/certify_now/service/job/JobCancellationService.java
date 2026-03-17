package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Payment;
import com.uk.certifynow.certify_now.events.job.JobCancelledEvent;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PaymentRepository;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles all job cancellation and engineer-decline flows extracted from {@link JobService}. */
@Service
public class JobCancellationService {

  private static final int CALLOUT_FEE_PENCE = 1500;

  private final JobRepository jobRepository;
  private final PaymentRepository paymentRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final JobResponseMapper jobResponseMapper;
  private final JobHistoryService jobHistoryService;

  public JobCancellationService(
      final JobRepository jobRepository,
      final PaymentRepository paymentRepository,
      final JobMatchLogRepository matchLogRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock,
      final JobResponseMapper jobResponseMapper,
      final JobHistoryService jobHistoryService) {
    this.jobRepository = jobRepository;
    this.paymentRepository = paymentRepository;
    this.matchLogRepository = matchLogRepository;
    this.publisher = publisher;
    this.clock = clock;
    this.jobResponseMapper = jobResponseMapper;
    this.jobHistoryService = jobHistoryService;
  }

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

    if (actor == CancellationActor.ENGINEER && current == JobStatus.MATCHED) {
      return declineJob(jobId, actorId, new DeclineJobRequest(request.reason()));
    }

    validateCancellationPermission(actor, current);
    validateTransition(current, JobStatus.CANCELLED);

    final int refundPence = calculateRefund(job, actor, current);

    final String prevStatus = job.getStatus();
    job.setStatus(JobStatus.CANCELLED.name());
    job.setCancelledAt(OffsetDateTime.now(clock));
    job.setCancelledBy(actor.name());
    job.setCancellationReason(request.reason());
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    final Optional<Payment> paymentOpt = paymentRepository.findByJobId(jobId);
    paymentOpt.ifPresent(
        payment -> {
          payment.setRefundAmountPence(refundPence);
          payment.setRefundReason(request.reason());
          paymentRepository.save(payment);
        });

    final String metadataJson = "{\"refund_amount_pence\":" + refundPence + "}";
    jobHistoryService.recordHistory(
        saved,
        prevStatus,
        JobStatus.CANCELLED.name(),
        actorId,
        actor.name(),
        request.reason(),
        metadataJson);

    publisher.publishEvent(
        new JobCancelledEvent(saved.getId(), actor.name(), request.reason(), refundPence));
    return jobResponseMapper.toJobResponse(saved, paymentOpt.orElse(null));
  }

  @CacheEvict(value = "jobs", key = "#jobId")
  @Transactional
  public JobResponse declineJob(
      final UUID jobId, final UUID engineerId, final DeclineJobRequest request) {
    final Job job = loadJobOrThrow(jobId);
    authoriseEngineer(job, engineerId);
    validateTransition(JobStatus.fromString(job.getStatus()), JobStatus.CREATED);

    final String prevStatus = job.getStatus();
    job.setStatus(JobStatus.CREATED.name());
    job.setEngineer(null);
    job.setMatchedAt(null);
    job.setMatchAttempts(job.getMatchAttempts() + 1);
    job.setUpdatedAt(OffsetDateTime.now(clock));
    final Job saved = jobRepository.save(job);

    jobHistoryService.recordHistory(
        saved,
        prevStatus,
        JobStatus.CREATED.name(),
        engineerId,
        "ENGINEER",
        request.reason(),
        null);

    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            log -> {
              log.setRespondedAt(OffsetDateTime.now(clock));
              log.setResponse("DECLINED");
              log.setDeclineReason(request.reason());
              matchLogRepository.save(log);
            });

    publisher.publishEvent(
        new com.uk.certifynow.certify_now.events.job.JobStatusChangedEvent(
            saved.getId(), prevStatus, JobStatus.CREATED.name(), engineerId, "ENGINEER"));
    final Payment payment = paymentRepository.findByJobId(jobId).orElse(null);
    return jobResponseMapper.toJobResponse(saved, payment);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

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
          case MATCHED -> true;
          case ACCEPTED ->
              actor == CancellationActor.CUSTOMER
                  || actor == CancellationActor.ENGINEER
                  || actor == CancellationActor.ADMIN;
          case EN_ROUTE -> actor == CancellationActor.CUSTOMER || actor == CancellationActor.ADMIN;
          default -> actor == CancellationActor.ADMIN;
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
      case CREATED, AWAITING_ACCEPTANCE, MATCHED, ESCALATED -> total;
      case ACCEPTED -> {
        final OffsetDateTime scheduledStart = scheduledStart(job);
        if (scheduledStart != null
            && OffsetDateTime.now(clock).plusHours(24).isAfter(scheduledStart)) {
          yield (int) (total * 0.80);
        }
        yield total;
      }
      case EN_ROUTE -> Math.max(0, total - CALLOUT_FEE_PENCE);
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
}

package com.uk.certifynow.certify_now.service.matching;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.job.JobMatchedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.enums.JobStatus;
import com.uk.certifynow.certify_now.service.enums.MatchLogResponse;
import com.uk.certifynow.certify_now.service.job.JobResponseMapper;
import com.uk.certifynow.certify_now.service.notification.AdminAlertService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matching engine implementing the broadcast model. When a job is created, all eligible engineers
 * are notified simultaneously. The first engineer to accept (claim) wins the job atomically.
 */
@Service
public class MatchingService {

  private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

  private final JobRepository jobRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final JobMatchLogRepository matchLogRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final JobResponseMapper jobResponseMapper;
  private final AdminAlertService adminAlertService;

  @Value("${certifynow.matching.broadcast-expiry-minutes:15}")
  private int broadcastExpiryMinutes;

  public MatchingService(
      final JobRepository jobRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final JobMatchLogRepository matchLogRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock,
      final JobResponseMapper jobResponseMapper,
      final AdminAlertService adminAlertService) {
    this.jobRepository = jobRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.matchLogRepository = matchLogRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
    this.clock = clock;
    this.jobResponseMapper = jobResponseMapper;
    this.adminAlertService = adminAlertService;
  }

  // ────────────────────────────────────────────────────────────────────────────
  // FIND CANDIDATES
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Finds all eligible engineers for a job: approved, within service radius, available, and under
   * daily job cap. Qualification filtering is a TODO (stub) until qualification-to-cert-type
   * mapping is defined.
   */
  @Transactional(readOnly = true)
  public List<EngineerProfile> findCandidates(final Job job) {
    final Property property = job.getProperty();

    // Defensive guard — PropertyService now prevents null coordinates at creation,
    // but if a property somehow has no coordinates, fail fast instead of loading all engineers.
    if (property.getCoordinates() == null) {
      log.error("Property {} has no coordinates — cannot match engineers", property.getId());
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "GEOCODING_REQUIRED",
          "Property must have coordinates for engineer matching");
    }

    // JTS Point stores (longitude, latitude) in x/y respectively
    final double lat = property.getCoordinates().getY();
    final double lng = property.getCoordinates().getX();

    // PostGIS spatial query: approved engineers within their own service radius
    final List<EngineerProfile> nearby = engineerProfileRepository.findNearbyApproved(lat, lng);

    // Filter: daily job cap check — batch query to avoid N+1
    final OffsetDateTime startOfDay = LocalDate.now(clock).atStartOfDay().atOffset(ZoneOffset.UTC);
    final List<UUID> nearbyIds = nearby.stream().map(ep -> ep.getUser().getId()).toList();
    final Map<UUID, Long> jobCountByEngineerId =
        jobRepository.countEngineerJobsTodayBatch(nearbyIds, startOfDay).stream()
            .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

    final List<EngineerProfile> eligible =
        nearby.stream()
            .filter(
                ep -> {
                  final long todayCount =
                      jobCountByEngineerId.getOrDefault(ep.getUser().getId(), 0L);
                  return todayCount < ep.getMaxDailyJobs();
                })
            .toList();

    // TODO: Qualification filtering — once cert-type-to-qualification mapping is
    // defined,
    // filter engineers who hold the required qualification for
    // job.getCertificateType().

    log.debug(
        "findCandidates: job={}, nearby={}, eligible (after cap check)={}",
        job.getId(),
        nearby.size(),
        eligible.size());

    return eligible;
  }

  // ────────────────────────────────────────────────────────────────────────────
  // BROADCAST TO ELIGIBLE
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Broadcasts a job to all eligible engineers. Updates job status to AWAITING_ACCEPTANCE and
   * creates match log entries for each notified engineer.
   *
   * <p>The {@code jobRef} parameter may come from a detached / proxy-only entity (e.g. loaded by
   * the scheduler outside a transaction). We therefore re-load the job with its {@code property}
   * eagerly joined so that {@link #findCandidates} can access {@code property.location} safely
   * within this transaction.
   */
  @Transactional
  public void broadcastToEligible(final Job jobRef) {
    final Job job =
        jobRepository
            .findByIdWithProperty(jobRef.getId())
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Job not found during broadcast: " + jobRef.getId()));

    final JobStatus current = JobStatus.fromString(job.getStatus());
    if (current != JobStatus.CREATED) {
      log.warn(
          "broadcastToEligible called for job {} in status {} — skipping", job.getId(), current);
      return;
    }

    final List<EngineerProfile> candidates = findCandidates(job);

    if (candidates.isEmpty()) {
      log.warn("No eligible engineers found for job {} — escalating immediately", job.getId());
      escalateJob(job);
      return;
    }

    // Update job status to AWAITING_ACCEPTANCE
    job.setStatus(JobStatus.AWAITING_ACCEPTANCE.name());
    job.setBroadcastAt(OffsetDateTime.now(clock));
    job.setUpdatedAt(OffsetDateTime.now(clock));
    jobRepository.save(job);

    // Create match log entries and "notify" each engineer
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final List<JobMatchLog> matchLogs = new java.util.ArrayList<>(candidates.size());
    for (final EngineerProfile ep : candidates) {
      final JobMatchLog matchLog = new JobMatchLog();
      matchLog.setJob(job);
      matchLog.setEngineer(ep.getUser());
      matchLog.setNotifiedAt(now);
      matchLog.setCreatedAt(now);
      matchLog.setResponse(MatchLogResponse.PENDING.name());
      // Distance calculation would require PostGIS — left as null for now
      matchLogs.add(matchLog);
    }
    matchLogRepository.saveAll(matchLogs);

    log.info(
        "Job {} broadcast to {} eligible engineers: {}",
        job.getId(),
        candidates.size(),
        candidates.stream().map(ep -> ep.getUser().getId().toString()).toList());

    log.info("Job {} status=AWAITING_ACCEPTANCE", job.getId());
  }

  // ────────────────────────────────────────────────────────────────────────────
  // CLAIM JOB (atomic first-to-accept)
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Atomic first-to-accept logic. Uses a conditional UPDATE to ensure only one engineer can claim a
   * job. Returns the updated job response on success. Throws 409 Conflict if already claimed.
   */
  @Transactional
  public JobResponse claimJob(final UUID jobId, final UUID engineerId) {
    // Validate engineer exists
    final User engineer =
        userRepository
            .findById(engineerId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + engineerId));
    if (!engineer.isEngineer()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "NOT_AN_ENGINEER", "User is not an engineer");
    }

    // Verify the engineer was actually notified about this job
    final JobMatchLog matchLog =
        matchLogRepository
            .findByJobIdAndEngineerId(jobId, engineerId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "NOT_NOTIFIED",
                        "Engineer was not notified about this job"));

    // Atomic conditional update — only succeeds if job is still AWAITING_ACCEPTANCE
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final int updatedRows = jobRepository.claimJob(jobId, engineer, now);

    if (updatedRows == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "JOB_ALREADY_CLAIMED",
          "This job has already been claimed by another engineer");
    }

    // Update match log for the claiming engineer
    matchLog.setRespondedAt(now);
    matchLog.setResponse(MatchLogResponse.ACCEPTED.name());
    matchLogRepository.save(matchLog);

    // Expire all other pending match log entries
    matchLogRepository.expireAllPendingForJob(jobId);

    // Reload the job for the response
    final Job job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

    // Publish matched event
    publisher.publishEvent(new JobMatchedEvent(jobId, engineerId, null, null));

    log.info("Job {} claimed by engineer {}", jobId, engineerId);

    return jobResponseMapper.toJobResponse(job, null);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // ESCALATE JOB
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Called when nobody accepts within the timeout. Updates job status to ESCALATED and expires all
   * pending match log entries.
   */
  @Transactional
  public void escalateJob(final Job job) {
    log.warn("Escalating job {} — no engineer accepted within timeout", job.getId());

    final OffsetDateTime now = OffsetDateTime.now(clock);
    job.setStatus(JobStatus.ESCALATED.name());
    job.setEscalatedAt(now);
    job.setLastAdminAlertAt(now);
    job.setAdminAlertCount(1);
    job.setUpdatedAt(now);
    jobRepository.save(job);

    // Expire all pending match log entries
    matchLogRepository.expireAllPendingForJob(job.getId());

    log.warn(
        "Job {} escalated — admin attention required. Was broadcast at {}",
        job.getId(),
        job.getBroadcastAt());

    // Fire email + Slack alerts asynchronously so the matching transaction is not blocked.
    adminAlertService.sendJobEscalationAlert(job);
  }

  /**
   * Called by the reminder scheduler for jobs that remain in ESCALATED status longer than the
   * configured reminder interval. Re-loads the job inside a transaction to guard against concurrent
   * resolution, increments the alert counter, persists the new {@code lastAdminAlertAt} timestamp,
   * then fires the async reminder notifications.
   */
  @Transactional
  public void sendEscalationReminderAndRecord(final Job jobRef) {
    final Job job =
        jobRepository
            .findById(jobRef.getId())
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobRef.getId()));

    if (JobStatus.fromString(job.getStatus()) != JobStatus.ESCALATED) {
      log.info(
          "sendEscalationReminderAndRecord: job {} is no longer ESCALATED (status={}) — skipping",
          job.getId(),
          job.getStatus());
      return;
    }

    final OffsetDateTime now = OffsetDateTime.now(clock);

    if (job.getAdminAlertCount() == null) {
      log.warn("adminAlertCount is null for escalated job {} — defaulting to 1", job.getId());
    }
    final int currentCount = job.getAdminAlertCount() != null ? job.getAdminAlertCount() : 1;
    final int newCount = currentCount + 1;

    final long minutesEscalated =
        job.getEscalatedAt() != null ? Duration.between(job.getEscalatedAt(), now).toMinutes() : 0;

    job.setAdminAlertCount(newCount);
    job.setLastAdminAlertAt(now);
    job.setUpdatedAt(now);
    jobRepository.save(job);

    log.warn(
        "Job {} still ESCALATED after {}min — dispatching reminder #{}",
        job.getId(),
        minutesEscalated,
        newCount);

    adminAlertService.sendJobEscalationReminder(job, newCount, minutesEscalated);
  }

  // ────────────────────────────────────────────────────────────────────────────
  // PRIVATE HELPERS
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Parses a location string into [lat, lng]. Supports formats: "POINT(lng lat)",
   * "SRID=4326;POINT(lng lat)", or "lat,lng".
   */
}

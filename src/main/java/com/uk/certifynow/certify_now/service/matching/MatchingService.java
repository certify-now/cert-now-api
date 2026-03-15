package com.uk.certifynow.certify_now.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.uk.certifynow.certify_now.rest.dto.job.DayAvailability;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.job.JobStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
  private final ObjectMapper objectMapper;

  @Value("${certifynow.matching.broadcast-expiry-minutes:15}")
  private int broadcastExpiryMinutes;

  public MatchingService(
      final JobRepository jobRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final JobMatchLogRepository matchLogRepository,
      final UserRepository userRepository,
      final ApplicationEventPublisher publisher,
      final ObjectMapper objectMapper) {
    this.jobRepository = jobRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.matchLogRepository = matchLogRepository;
    this.userRepository = userRepository;
    this.publisher = publisher;
    this.objectMapper = objectMapper;
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
    final String locationStr = property.getLocation();

    // If property has no geocoded location, fall back to all approved engineers
    if (locationStr == null || locationStr.isBlank()) {
      log.warn(
          "Property {} has no geocoded location — falling back to all APPROVED engineers",
          property.getId());
      return engineerProfileRepository.findByStatus(
          com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus.APPROVED);
    }

    // Parse location — expected format: "POINT(lng lat)" or "lng,lat"
    final double[] coords = parseLocation(locationStr);
    final double lat = coords[0];
    final double lng = coords[1];

    // PostGIS spatial query: approved engineers within their own service radius
    final List<EngineerProfile> nearby = engineerProfileRepository.findNearbyApproved(lat, lng);

    // Filter: daily job cap check
    final OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
    final List<EngineerProfile> eligible =
        nearby.stream()
            .filter(
                ep -> {
                  final long todayCount =
                      jobRepository.countEngineerJobsToday(ep.getUser().getId(), startOfDay);
                  return todayCount < ep.getMaxDailyJobs();
                })
            .toList();

    // TODO: Qualification filtering — once cert-type-to-qualification mapping is
    // defined,
    // filter engineers who hold the required qualification for
    // job.getCertificateType().

    log.info(
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
    job.setStatus("AWAITING_ACCEPTANCE");
    job.setBroadcastAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    jobRepository.save(job);

    // Create match log entries and "notify" each engineer
    final OffsetDateTime now = OffsetDateTime.now();
    for (final EngineerProfile ep : candidates) {
      final JobMatchLog matchLog = new JobMatchLog();
      matchLog.setJob(job);
      matchLog.setEngineer(ep.getUser());
      matchLog.setNotifiedAt(now);
      matchLog.setCreatedAt(now);
      matchLog.setResponse("PENDING");
      // Distance calculation would require PostGIS — left as null for now
      matchLogRepository.save(matchLog);

      // Placeholder for push notification — log for now
      log.info(
          "Job {} broadcast to engineer {} (user {})",
          job.getId(),
          ep.getId(),
          ep.getUser().getId());
    }

    log.info(
        "Job {} broadcast to {} eligible engineers, status=AWAITING_ACCEPTANCE",
        job.getId(),
        candidates.size());
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
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "NOT_NOTIFIED",
                    "Engineer was not notified about this job"));

    // Atomic conditional update — only succeeds if job is still AWAITING_ACCEPTANCE
    final OffsetDateTime now = OffsetDateTime.now();
    final int updatedRows = jobRepository.claimJob(jobId, engineer, now);

    if (updatedRows == 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "JOB_ALREADY_CLAIMED",
          "This job has already been claimed by another engineer");
    }

    // Update match log for the claiming engineer
    matchLogRepository
        .findByJobIdAndEngineerId(jobId, engineerId)
        .ifPresent(
            matchLog -> {
              matchLog.setRespondedAt(now);
              matchLog.setResponse("ACCEPTED");
              matchLogRepository.save(matchLog);
            });

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

    return toClaimResponse(job);
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

    job.setStatus("ESCALATED");
    job.setEscalatedAt(OffsetDateTime.now());
    job.setUpdatedAt(OffsetDateTime.now());
    jobRepository.save(job);

    // Expire all pending match log entries
    matchLogRepository.expireAllPendingForJob(job.getId());

    // TODO: Wire to admin notification system when built
    log.warn(
        "Job {} escalated — admin attention required. Was broadcast at {}",
        job.getId(),
        job.getBroadcastAt());
  }

  // ────────────────────────────────────────────────────────────────────────────
  // HELPERS
  // ────────────────────────────────────────────────────────────────────────────

  /**
   * Parses a location string into [lat, lng]. Supports formats: "POINT(lng lat)",
   * "SRID=4326;POINT(lng lat)", or "lng,lat".
   */
  private double[] parseLocation(final String location) {
    try {
      final String cleaned = location.trim();
      if (cleaned.toUpperCase().contains("POINT")) {
        // Extract coordinates from POINT(lng lat) format
        final int start = cleaned.indexOf('(');
        final int end = cleaned.indexOf(')');
        final String coords = cleaned.substring(start + 1, end).trim();
        final String[] parts = coords.split("\\s+");
        final double lng = Double.parseDouble(parts[0]);
        final double lat = Double.parseDouble(parts[1]);
        return new double[] {lat, lng};
      } else if (cleaned.contains(",")) {
        final String[] parts = cleaned.split(",");
        final double lng = Double.parseDouble(parts[0].trim());
        final double lat = Double.parseDouble(parts[1].trim());
        return new double[] {lat, lng};
      }
    } catch (final Exception e) {
      log.error("Failed to parse location string: {}", location, e);
    }
    // Default to London coordinates as fallback
    return new double[] {51.5074, -0.1278};
  }

  /**
   * Minimal job response for the claim endpoint. Returns key fields needed by the engineer after
   * claiming.
   */
  private JobResponse toClaimResponse(final Job job) {
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
        null, // payment not needed in claim response
        timestamps);
  }

  private List<DayAvailability> parseAvailability(final String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<DayAvailability>>() {});
    } catch (final JsonProcessingException e) {
      return List.of();
    }
  }
}

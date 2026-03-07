package com.uk.certifynow.certify_now.scheduler;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for the matching engine:
 *
 * <ul>
 *   <li>{@link #processUnmatchedJobs()} — safety net for jobs stuck in CREATED (missed events)
 *   <li>{@link #processExpiredBroadcasts()} — escalates jobs where nobody claimed within timeout
 * </ul>
 */
@Component
public class MatchingScheduler {

  private static final Logger log = LoggerFactory.getLogger(MatchingScheduler.class);

  private final JobRepository jobRepository;
  private final MatchingService matchingService;

  @Value("${certifynow.matching.broadcast-expiry-minutes:15}")
  private int broadcastExpiryMinutes;

  public MatchingScheduler(
      final JobRepository jobRepository, final MatchingService matchingService) {
    this.jobRepository = jobRepository;
    this.matchingService = matchingService;
  }

  /**
   * Safety net: finds jobs in CREATED status that haven't been broadcast yet (broadcastAt is null).
   * This handles edge cases where the event listener failed or didn't fire.
   */
  @Scheduled(fixedRateString = "${certifynow.matching.unmatched-job-check-interval-ms:30000}")
  public void processUnmatchedJobs() {
    final List<Job> unmatchedJobs = jobRepository.findByStatusAndBroadcastAtIsNull("CREATED");

    if (unmatchedJobs.isEmpty()) {
      return;
    }

    log.info(
        "processUnmatchedJobs: found {} unbroadcast jobs in CREATED status", unmatchedJobs.size());

    for (final Job job : unmatchedJobs) {
      try {
        matchingService.broadcastToEligible(job);
      } catch (final Exception e) {
        log.error("processUnmatchedJobs: failed to broadcast job {}", job.getId(), e);
      }
    }
  }

  /**
   * Finds jobs in AWAITING_ACCEPTANCE status where the broadcast was sent more than the configured
   * timeout ago and nobody has claimed them. Escalates these jobs.
   */
  @Scheduled(fixedRateString = "${certifynow.matching.expired-broadcast-check-interval-ms:30000}")
  public void processExpiredBroadcasts() {
    final OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(broadcastExpiryMinutes);

    final List<Job> expiredJobs =
        jobRepository.findByStatusAndBroadcastAtBefore("AWAITING_ACCEPTANCE", cutoff);

    if (expiredJobs.isEmpty()) {
      return;
    }

    log.info(
        "processExpiredBroadcasts: found {} jobs past {}min broadcast window",
        expiredJobs.size(),
        broadcastExpiryMinutes);

    for (final Job job : expiredJobs) {
      try {
        matchingService.escalateJob(job);
      } catch (final Exception e) {
        log.error("processExpiredBroadcasts: failed to escalate job {}", job.getId(), e);
      }
    }
  }
}

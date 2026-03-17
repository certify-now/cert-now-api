package com.uk.certifynow.certify_now.scheduler;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
  private static final int BATCH_SIZE = 50;

  private final JobRepository jobRepository;
  private final MatchingService matchingService;
  private final Clock clock;

  @Value("${certifynow.matching.broadcast-expiry-minutes:15}")
  private int broadcastExpiryMinutes;

  public MatchingScheduler(
      final JobRepository jobRepository, final MatchingService matchingService, final Clock clock) {
    this.jobRepository = jobRepository;
    this.matchingService = matchingService;
    this.clock = clock;
  }

  /**
   * Safety net: finds jobs in CREATED status that haven't been broadcast yet (broadcastAt is null).
   * This handles edge cases where the event listener failed or didn't fire.
   */
  @Scheduled(fixedRateString = "${certifynow.matching.unmatched-job-check-interval-ms:30000}")
  public void processUnmatchedJobs() {
    // Always fetch page 0: broadcastToEligible transitions jobs out of CREATED,
    // so processed items fall off the result set naturally.
    // Guard: if every job in a batch throws, none are removed from page 0 — break to avoid
    // spinning indefinitely when the whole batch is persistently failing.
    Page<Job> page;
    do {
      page =
          jobRepository.findByStatusAndBroadcastAtIsNull("CREATED", PageRequest.of(0, BATCH_SIZE));
      if (page.isEmpty()) {
        return;
      }
      log.info(
          "processUnmatchedJobs: processing batch of {} unbroadcast jobs",
          page.getNumberOfElements());
      int successCount = 0;
      for (final Job job : page.getContent()) {
        try {
          matchingService.broadcastToEligible(job);
          successCount++;
        } catch (final Exception e) {
          log.error("processUnmatchedJobs: failed to broadcast job {}", job.getId(), e);
        }
      }
      if (successCount == 0) {
        log.warn("processUnmatchedJobs: no jobs transitioned in this batch — stopping to avoid loop");
        return;
      }
    } while (page.hasNext());
  }

  /**
   * Finds jobs in AWAITING_ACCEPTANCE status where the broadcast was sent more than the configured
   * timeout ago and nobody has claimed them. Escalates these jobs.
   */
  @Scheduled(fixedRateString = "${certifynow.matching.expired-broadcast-check-interval-ms:30000}")
  public void processExpiredBroadcasts() {
    final OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMinutes(broadcastExpiryMinutes);
    // Always fetch page 0: escalateJob transitions jobs out of AWAITING_ACCEPTANCE,
    // so processed items fall off the result set naturally.
    // Guard: break if no jobs transition to avoid infinite loop on persistent failures.
    Page<Job> page;
    do {
      page =
          jobRepository.findByStatusAndBroadcastAtBefore(
              "AWAITING_ACCEPTANCE", cutoff, PageRequest.of(0, BATCH_SIZE));
      if (page.isEmpty()) {
        return;
      }
      log.info(
          "processExpiredBroadcasts: processing batch of {} jobs past {}min broadcast window",
          page.getNumberOfElements(),
          broadcastExpiryMinutes);
      int successCount = 0;
      for (final Job job : page.getContent()) {
        try {
          matchingService.escalateJob(job);
          successCount++;
        } catch (final Exception e) {
          log.error("processExpiredBroadcasts: failed to escalate job {}", job.getId(), e);
        }
      }
      if (successCount == 0) {
        log.warn("processExpiredBroadcasts: no jobs transitioned in this batch — stopping to avoid loop");
        return;
      }
    } while (page.hasNext());
  }
}

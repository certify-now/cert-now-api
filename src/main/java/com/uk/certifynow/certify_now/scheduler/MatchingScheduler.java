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
      final JobRepository jobRepository,
      final MatchingService matchingService,
      final Clock clock) {
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
    int pageNumber = 0;
    Page<Job> page;
    do {
      page = jobRepository.findByStatusAndBroadcastAtIsNull(
          "CREATED", PageRequest.of(pageNumber, BATCH_SIZE));
      if (page.isEmpty()) {
        return;
      }
      log.info(
          "processUnmatchedJobs: processing page {} ({} jobs)",
          pageNumber,
          page.getNumberOfElements());
      for (final Job job : page.getContent()) {
        try {
          matchingService.broadcastToEligible(job);
        } catch (final Exception e) {
          log.error("processUnmatchedJobs: failed to broadcast job {}", job.getId(), e);
        }
      }
      pageNumber++;
    } while (page.hasNext());
  }

  /**
   * Finds jobs in AWAITING_ACCEPTANCE status where the broadcast was sent more than the configured
   * timeout ago and nobody has claimed them. Escalates these jobs.
   */
  @Scheduled(fixedRateString = "${certifynow.matching.expired-broadcast-check-interval-ms:30000}")
  public void processExpiredBroadcasts() {
    final OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMinutes(broadcastExpiryMinutes);
    int pageNumber = 0;
    Page<Job> page;
    do {
      page = jobRepository.findByStatusAndBroadcastAtBefore(
          "AWAITING_ACCEPTANCE", cutoff, PageRequest.of(pageNumber, BATCH_SIZE));
      if (page.isEmpty()) {
        return;
      }
      log.info(
          "processExpiredBroadcasts: processing page {} ({} jobs past {}min broadcast window)",
          pageNumber,
          page.getNumberOfElements(),
          broadcastExpiryMinutes);
      for (final Job job : page.getContent()) {
        try {
          matchingService.escalateJob(job);
        } catch (final Exception e) {
          log.error("processExpiredBroadcasts: failed to escalate job {}", job.getId(), e);
        }
      }
      pageNumber++;
    } while (page.hasNext());
  }
}

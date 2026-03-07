package com.uk.certifynow.certify_now.events.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link JobCreatedEvent} and triggers the matching engine to broadcast the job to all
 * eligible engineers. Replaces the stub logging in {@link JobEventLogger#onJobCreated}.
 */
@Component
public class MatchingJobListener {

  private static final Logger log = LoggerFactory.getLogger(MatchingJobListener.class);

  private final MatchingService matchingService;
  private final JobRepository jobRepository;

  public MatchingJobListener(
      final MatchingService matchingService, final JobRepository jobRepository) {
    this.matchingService = matchingService;
    this.jobRepository = jobRepository;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onJobCreated(final JobCreatedEvent event) {
    log.info(
        "MatchingJobListener: job {} created — initiating broadcast to eligible engineers",
        event.getJobId());

    final Job job =
        jobRepository
            .findById(event.getJobId())
            .orElseThrow(
                () -> {
                  log.error(
                      "MatchingJobListener: job {} not found after creation event",
                      event.getJobId());
                  return new IllegalStateException(
                      "Job not found: " + event.getJobId() + " — this should not happen");
                });

    try {
      matchingService.broadcastToEligible(job);
    } catch (final Exception e) {
      log.error(
          "MatchingJobListener: failed to broadcast job {} — will be picked up by scheduler",
          event.getJobId(),
          e);
    }
  }
}

package com.uk.certifynow.certify_now.events.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stub event listeners that log all job domain events.
 *
 * <p>
 * These will be replaced with real notification/matching logic in later phases:
 * <ul>
 * <li>Phase 6: MatchingJobListener replaces onJobCreated</li>
 * <li>Phase 9: NotificationJobListener replaces all of these</li>
 * </ul>
 */
@Component
public class JobEventLogger {

    private static final Logger log = LoggerFactory.getLogger(JobEventLogger.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(final JobCreatedEvent event) {
        log.info(
                "JOB_CREATED: jobId={}, customerId={}, type={}, totalPricePence={}",
                event.getJobId(),
                event.getCustomerId(),
                event.getCertificateType(),
                event.getTotalPricePence());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobMatched(final JobMatchedEvent event) {
        log.info(
                "JOB_MATCHED: jobId={}, engineerId={}, score={}, distanceMiles={}",
                event.getJobId(),
                event.getEngineerId(),
                event.getMatchScore(),
                event.getDistanceMiles());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobAccepted(final JobAcceptedEvent event) {
        log.info(
                "JOB_ACCEPTED: jobId={}, engineerId={}, scheduledDate={}, slot={}",
                event.getJobId(),
                event.getEngineerId(),
                event.getScheduledDate(),
                event.getScheduledTimeSlot());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobStatusChanged(final JobStatusChangedEvent event) {
        log.info(
                "JOB_STATUS_CHANGED: jobId={}, {} → {}, actorType={}",
                event.getJobId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActorType());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCancelled(final JobCancelledEvent event) {
        log.info(
                "JOB_CANCELLED: jobId={}, by={}, refundPence={}",
                event.getJobId(),
                event.getCancelledBy(),
                event.getRefundAmountPence());
    }
}

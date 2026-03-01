package com.uk.certifynow.certify_now.events.job;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when an engineer is matched to a job.
 * Listeners: JobEventLogger (Phase 4), NotificationJobListener (Phase 9).
 */
public class JobMatchedEvent {

    private final UUID jobId;
    private final UUID engineerId;
    private final BigDecimal matchScore;
    private final BigDecimal distanceMiles;

    public JobMatchedEvent(
            final UUID jobId,
            final UUID engineerId,
            final BigDecimal matchScore,
            final BigDecimal distanceMiles) {
        this.jobId = jobId;
        this.engineerId = engineerId;
        this.matchScore = matchScore;
        this.distanceMiles = distanceMiles;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getEngineerId() {
        return engineerId;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public BigDecimal getDistanceMiles() {
        return distanceMiles;
    }
}

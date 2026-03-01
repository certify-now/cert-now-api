package com.uk.certifynow.certify_now.events.job;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when an engineer accepts a job and sets the schedule.
 */
public class JobAcceptedEvent {

    private final UUID jobId;
    private final UUID engineerId;
    private final LocalDate scheduledDate;
    private final String scheduledTimeSlot;

    public JobAcceptedEvent(
            final UUID jobId,
            final UUID engineerId,
            final LocalDate scheduledDate,
            final String scheduledTimeSlot) {
        this.jobId = jobId;
        this.engineerId = engineerId;
        this.scheduledDate = scheduledDate;
        this.scheduledTimeSlot = scheduledTimeSlot;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getEngineerId() {
        return engineerId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public String getScheduledTimeSlot() {
        return scheduledTimeSlot;
    }
}

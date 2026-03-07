package com.uk.certifynow.certify_now.events.job;

import java.util.UUID;

/**
 * Generic status change event used for EN_ROUTE, IN_PROGRESS, COMPLETED, and DECLINE (revert to
 * CREATED).
 */
public class JobStatusChangedEvent {

  private final UUID jobId;
  private final String fromStatus;
  private final String toStatus;
  private final UUID actorId;
  private final String actorType;

  public JobStatusChangedEvent(
      final UUID jobId,
      final String fromStatus,
      final String toStatus,
      final UUID actorId,
      final String actorType) {
    this.jobId = jobId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.actorId = actorId;
    this.actorType = actorType;
  }

  public UUID getJobId() {
    return jobId;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }
}

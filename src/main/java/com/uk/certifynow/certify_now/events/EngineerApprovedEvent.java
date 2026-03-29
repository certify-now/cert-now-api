package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.job.ActorType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an engineer's application is approved by an admin. Listeners: EngineerEventLogger
 * (Phase 5 stub), NotificationJobListener (Phase 9).
 */
public class EngineerApprovedEvent extends DomainEvent {

  private final UUID engineerUserId;
  private final UUID engineerProfileId;
  private final UUID approvedBy;
  private final OffsetDateTime approvedAt;

  public EngineerApprovedEvent(
      final UUID engineerUserId,
      final UUID engineerProfileId,
      final UUID approvedBy,
      final OffsetDateTime approvedAt) {
    super(approvedBy, ActorType.ADMIN);
    this.engineerUserId = engineerUserId;
    this.engineerProfileId = engineerProfileId;
    this.approvedBy = approvedBy;
    this.approvedAt = approvedAt;
  }

  public UUID getEngineerUserId() {
    return engineerUserId;
  }

  public UUID getEngineerProfileId() {
    return engineerProfileId;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public OffsetDateTime getApprovedAt() {
    return approvedAt;
  }
}

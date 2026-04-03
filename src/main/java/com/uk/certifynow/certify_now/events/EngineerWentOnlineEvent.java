package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;

/**
 * Published when an approved engineer sets their status to online. Listeners: EngineerEventLogger
 * (Phase 5 stub), NotificationJobListener (Phase 9).
 */
public class EngineerWentOnlineEvent extends DomainEvent {

  private final UUID engineerUserId;
  private final UUID engineerProfileId;

  public EngineerWentOnlineEvent(final UUID engineerUserId, final UUID engineerProfileId) {
    super(engineerUserId, ActorType.ENGINEER);
    this.engineerUserId = engineerUserId;
    this.engineerProfileId = engineerProfileId;
  }

  public UUID getEngineerUserId() {
    return engineerUserId;
  }

  public UUID getEngineerProfileId() {
    return engineerProfileId;
  }
}

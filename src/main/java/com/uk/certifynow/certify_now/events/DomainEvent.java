package com.uk.certifynow.certify_now.events;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {

  private final UUID eventId;
  private final Instant occurredAt;
  private final UUID actorId;
  private final String actorType;

  protected DomainEvent(final UUID actorId, final String actorType) {
    this.eventId = UUID.randomUUID();
    this.occurredAt = Instant.now();
    this.actorId = actorId;
    this.actorType = actorType;
  }

  public UUID getEventId() {
    return eventId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }
}

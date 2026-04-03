package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PropertyRestoredEvent extends DomainEvent {

  private final UUID propertyId;
  private final UUID restoredBy;

  public PropertyRestoredEvent(
      final UUID propertyId, final UUID restoredBy, final ActorType actorType) {
    super(restoredBy, actorType);
    this.propertyId = propertyId;
    this.restoredBy = restoredBy;
  }

  public PropertyRestoredEvent(final UUID propertyId, final UUID restoredBy) {
    this(propertyId, restoredBy, ActorType.ADMIN);
  }
}

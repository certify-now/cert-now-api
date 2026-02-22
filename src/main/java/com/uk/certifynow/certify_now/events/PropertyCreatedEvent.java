package com.uk.certifynow.certify_now.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class PropertyCreatedEvent {

  private final UUID propertyId;
  private final UUID ownerId;

  public PropertyCreatedEvent(final UUID propertyId, final UUID ownerId) {
    this.propertyId = propertyId;
    this.ownerId = ownerId;
  }
}

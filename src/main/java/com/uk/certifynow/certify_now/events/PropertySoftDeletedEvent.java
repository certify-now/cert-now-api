package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.job.ActorType;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PropertySoftDeletedEvent extends DomainEvent {

  private final UUID propertyId;
  private final UUID deletedBy;

  public PropertySoftDeletedEvent(
      final UUID propertyId, final UUID deletedBy, final ActorType actorType) {
    super(deletedBy, actorType);
    this.propertyId = propertyId;
    this.deletedBy = deletedBy;
  }

  public PropertySoftDeletedEvent(final UUID propertyId, final UUID deletedBy) {
    this(propertyId, deletedBy, ActorType.ADMIN);
  }
}

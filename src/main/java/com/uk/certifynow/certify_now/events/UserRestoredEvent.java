package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;
import lombok.Getter;

@Getter
public class UserRestoredEvent extends DomainEvent {

  private final UUID userId;
  private final UUID restoredBy;

  public UserRestoredEvent(final UUID userId, final UUID restoredBy, final ActorType actorType) {
    super(restoredBy, actorType);
    this.userId = userId;
    this.restoredBy = restoredBy;
  }

  public UserRestoredEvent(final UUID userId, final UUID restoredBy) {
    this(userId, restoredBy, ActorType.ADMIN);
  }
}

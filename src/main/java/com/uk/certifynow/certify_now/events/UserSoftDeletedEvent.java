package com.uk.certifynow.certify_now.events;

import java.util.UUID;
import lombok.Getter;

@Getter
public class UserSoftDeletedEvent extends DomainEvent {

  private final UUID userId;
  private final UUID deletedBy;

  public UserSoftDeletedEvent(final UUID userId, final UUID deletedBy, final String actorType) {
    super(deletedBy, actorType);
    this.userId = userId;
    this.deletedBy = deletedBy;
  }

  public UserSoftDeletedEvent(final UUID userId, final UUID deletedBy) {
    this(userId, deletedBy, "ADMIN");
  }
}

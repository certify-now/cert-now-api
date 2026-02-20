package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.shared.event.DomainEvent;
import java.util.UUID;

public class UserRegisteredEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String role;

  public UserRegisteredEvent(final UUID userId, final String email, final String role) {
    super(userId, "USER");
    this.userId = userId;
    this.email = email;
    this.role = role;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }
}

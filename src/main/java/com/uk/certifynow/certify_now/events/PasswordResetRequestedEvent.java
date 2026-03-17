package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class PasswordResetRequestedEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String ipAddress;

  public PasswordResetRequestedEvent(
      final UUID userId, final String email, final String ipAddress) {
    super(userId, "USER");
    this.userId = userId;
    this.email = email;
    this.ipAddress = ipAddress;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getIpAddress() {
    return ipAddress;
  }
}

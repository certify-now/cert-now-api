package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class UserLoggedInEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String deviceInfo;

  public UserLoggedInEvent(final UUID userId, final String email, final String deviceInfo) {
    super(userId, "USER");
    this.userId = userId;
    this.email = email;
    this.deviceInfo = deviceInfo;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getDeviceInfo() {
    return deviceInfo;
  }
}

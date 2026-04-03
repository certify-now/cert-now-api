package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;

public class UserLoggedOutEvent extends DomainEvent {

  private final UUID userId;
  private final Long sessionDurationSeconds;
  private final String deviceInfo;

  public UserLoggedOutEvent(
      final UUID userId, final Long sessionDurationSeconds, final String deviceInfo) {
    super(userId, ActorType.CUSTOMER);
    this.userId = userId;
    this.sessionDurationSeconds = sessionDurationSeconds;
    this.deviceInfo = deviceInfo;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getSessionDurationSeconds() {
    return sessionDurationSeconds;
  }

  public String getDeviceInfo() {
    return deviceInfo;
  }
}

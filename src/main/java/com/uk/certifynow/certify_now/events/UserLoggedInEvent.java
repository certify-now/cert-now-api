package com.uk.certifynow.certify_now.events;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class UserLoggedInEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String role;
  private final String deviceInfo;
  private final String ipAddress;
  private final OffsetDateTime lastLoginAt;
  private final Long daysSinceLastLogin;

  public UserLoggedInEvent(
      final UUID userId,
      final String email,
      final String role,
      final String deviceInfo,
      final String ipAddress,
      final OffsetDateTime lastLoginAt) {
    super(userId, "USER");
    this.userId = userId;
    this.email = email;
    this.role = role;
    this.deviceInfo = deviceInfo;
    this.ipAddress = ipAddress;
    this.lastLoginAt = lastLoginAt;
    this.daysSinceLastLogin =
        lastLoginAt != null ? ChronoUnit.DAYS.between(lastLoginAt, OffsetDateTime.now()) : null;
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

  public String getDeviceInfo() {
    return deviceInfo;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public OffsetDateTime getLastLoginAt() {
    return lastLoginAt;
  }

  public Long getDaysSinceLastLogin() {
    return daysSinceLastLogin;
  }
}

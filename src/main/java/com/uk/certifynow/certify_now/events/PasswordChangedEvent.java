package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class PasswordChangedEvent extends DomainEvent {

  private final UUID userId;
  private final String ipAddress;
  private final boolean passwordReset;
  private final boolean userInitiated;
  private final String method;

  public PasswordChangedEvent(
      final UUID userId,
      final String ipAddress,
      final boolean passwordReset,
      final boolean userInitiated,
      final String method) {
    super(userId, "USER");
    this.userId = userId;
    this.ipAddress = ipAddress;
    this.passwordReset = passwordReset;
    this.userInitiated = userInitiated;
    this.method = method;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public boolean isPasswordReset() {
    return passwordReset;
  }

  public boolean isUserInitiated() {
    return userInitiated;
  }

  public String getMethod() {
    return method;
  }
}

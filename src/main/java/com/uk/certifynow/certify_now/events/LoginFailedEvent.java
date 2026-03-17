package com.uk.certifynow.certify_now.events;

public class LoginFailedEvent {

  private final String email;
  private final String reason;
  private final int attemptCount;
  private final String ipAddress;
  private final String userAgent;
  private final String suspensionReason;

  public LoginFailedEvent(
      final String email,
      final String reason,
      final int attemptCount,
      final String ipAddress,
      final String userAgent,
      final String suspensionReason) {
    this.email = email;
    this.reason = reason;
    this.attemptCount = attemptCount;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.suspensionReason = suspensionReason;
  }

  public String getEmail() {
    return email;
  }

  public String getReason() {
    return reason;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getSuspensionReason() {
    return suspensionReason;
  }
}

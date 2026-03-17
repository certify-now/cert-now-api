package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class AccountSuspendedEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String reason;
  private final UUID suspendedByAdminId;
  private final String suspensionDuration;

  public AccountSuspendedEvent(
      final UUID userId,
      final String email,
      final String reason,
      final UUID suspendedByAdminId,
      final String suspensionDuration) {
    super(userId, "ADMIN");
    this.userId = userId;
    this.email = email;
    this.reason = reason;
    this.suspendedByAdminId = suspendedByAdminId;
    this.suspensionDuration = suspensionDuration;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getReason() {
    return reason;
  }

  public UUID getSuspendedByAdminId() {
    return suspendedByAdminId;
  }

  public String getSuspensionDuration() {
    return suspensionDuration;
  }
}

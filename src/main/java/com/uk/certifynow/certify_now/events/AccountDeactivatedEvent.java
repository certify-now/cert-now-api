package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.job.ActorType;
import java.util.UUID;

public class AccountDeactivatedEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String reason;
  private final String initiatedBy;
  private final Long accountAgeInDays;
  private final Long totalJobsCompleted;

  public AccountDeactivatedEvent(
      final UUID userId,
      final String email,
      final String reason,
      final String initiatedBy,
      final Long accountAgeInDays,
      final Long totalJobsCompleted) {
    super(userId, ActorType.CUSTOMER);
    this.userId = userId;
    this.email = email;
    this.reason = reason;
    this.initiatedBy = initiatedBy;
    this.accountAgeInDays = accountAgeInDays;
    this.totalJobsCompleted = totalJobsCompleted;
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

  public String getInitiatedBy() {
    return initiatedBy;
  }

  public Long getAccountAgeInDays() {
    return accountAgeInDays;
  }

  public Long getTotalJobsCompleted() {
    return totalJobsCompleted;
  }
}

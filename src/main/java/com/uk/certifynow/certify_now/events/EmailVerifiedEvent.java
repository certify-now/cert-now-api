package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;

public class EmailVerifiedEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final Long secondsSinceRegistration;

  public EmailVerifiedEvent(
      final UUID userId, final String email, final Long secondsSinceRegistration) {
    super(userId, ActorType.CUSTOMER);
    this.userId = userId;
    this.email = email;
    this.secondsSinceRegistration = secondsSinceRegistration;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public Long getSecondsSinceRegistration() {
    return secondsSinceRegistration;
  }
}

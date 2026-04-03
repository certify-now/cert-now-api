package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.enums.ActorType;
import java.util.UUID;

/**
 * Published when a registration attempt is made for an email or phone that already exists.
 *
 * <p>Instead of throwing a 409 CONFLICT (which would let attackers enumerate registered emails),
 * RegistrationService publishes this event and returns a silent 201 success. An async listener
 * sends a "someone tried to register with your account" notification to the existing user.
 */
public class DuplicateRegistrationAttemptEvent extends DomainEvent {

  private final String targetEmail;
  private final String collisionType; // "EMAIL" or "PHONE"
  private final String ipAddress;

  public DuplicateRegistrationAttemptEvent(
      final UUID existingUserId,
      final String targetEmail,
      final String collisionType,
      final String ipAddress) {
    super(existingUserId, ActorType.CUSTOMER);
    this.targetEmail = targetEmail;
    this.collisionType = collisionType;
    this.ipAddress = ipAddress;
  }

  public String getTargetEmail() {
    return targetEmail;
  }

  public String getCollisionType() {
    return collisionType;
  }

  public String getIpAddress() {
    return ipAddress;
  }
}

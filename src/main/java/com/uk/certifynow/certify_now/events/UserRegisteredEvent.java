package com.uk.certifynow.certify_now.events;

import java.util.UUID;

public class UserRegisteredEvent extends DomainEvent {

  private final UUID userId;
  private final String email;
  private final String role;
  private final String authProvider;
  private final boolean emailVerified;

  public UserRegisteredEvent(
      final UUID userId,
      final String email,
      final String role,
      final String authProvider,
      final boolean emailVerified) {
    super(userId, "USER");
    this.userId = userId;
    this.email = email;
    this.role = role;
    this.authProvider = authProvider;
    this.emailVerified = emailVerified;
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

  public String getAuthProvider() {
    return authProvider;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }
}

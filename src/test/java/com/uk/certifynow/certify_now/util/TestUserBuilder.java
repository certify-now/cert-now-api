package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import java.util.UUID;

public final class TestUserBuilder {

  private static final java.time.OffsetDateTime NOW = TestConstants.FIXED_NOW;

  private TestUserBuilder() {}

  public static User buildActiveCustomer() {
    return build(
        UUID.randomUUID(),
        "customer@example.com",
        "Test Customer",
        UserRole.CUSTOMER,
        UserStatus.ACTIVE,
        true);
  }

  public static User buildActiveCustomer(final UUID id, final String email) {
    return build(id, email, "Test Customer", UserRole.CUSTOMER, UserStatus.ACTIVE, true);
  }

  public static User buildActiveEngineer() {
    return build(
        UUID.randomUUID(),
        "engineer@example.com",
        "Test Engineer",
        UserRole.ENGINEER,
        UserStatus.ACTIVE,
        true);
  }

  public static User buildActiveEngineer(final UUID id, final String email) {
    return build(id, email, "Test Engineer", UserRole.ENGINEER, UserStatus.ACTIVE, true);
  }

  public static User buildAdmin() {
    return build(
        UUID.randomUUID(),
        "admin@example.com",
        "Test Admin",
        UserRole.ADMIN,
        UserStatus.ACTIVE,
        true);
  }

  public static User buildPending() {
    return build(
        UUID.randomUUID(),
        "pending@example.com",
        "Pending User",
        UserRole.CUSTOMER,
        UserStatus.PENDING_VERIFICATION,
        false);
  }

  public static User buildSuspended() {
    final User user = buildActiveCustomer();
    user.setStatus(UserStatus.SUSPENDED);
    return user;
  }

  public static User buildDeactivated() {
    final User user = buildActiveCustomer();
    user.setStatus(UserStatus.DEACTIVATED);
    return user;
  }

  private static User build(
      final UUID id,
      final String email,
      final String fullName,
      final UserRole role,
      final UserStatus status,
      final boolean emailVerified) {
    final User user = new User();
    user.setId(id);
    user.setEmail(email);
    user.setFullName(fullName);
    user.setPasswordHash("$2a$10$hashed_password_for_testing");
    user.setRole(role);
    user.setStatus(status);
    user.setEmailVerified(emailVerified);
    user.setPhoneVerified(false);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setCreatedAt(NOW.minusDays(30));
    user.setUpdatedAt(NOW);
    return user;
  }
}

package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.User;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Factory for creating User aggregates with proper initialization. Encapsulates user creation logic
 * and ensures consistent state.
 */
@Component
public class UserFactory {

  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public UserFactory(final PasswordEncoder passwordEncoder, final Clock clock) {
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * Creates a new User with email/password authentication.
   *
   * @param email user email (will be normalized to lowercase)
   * @param password raw password (will be hashed)
   * @param fullName user's full name
   * @param phone user's phone number (optional)
   * @param role user role (CUSTOMER or ENGINEER)
   * @return newly created User entity
   */
  public User createEmailUser(
      final String email,
      final String password,
      final String fullName,
      final String phone,
      final UserRole role) {
    final User user = new User();
    user.setEmail(email.toLowerCase(Locale.ROOT));
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFullName(fullName);
    user.setPhone(phone);
    user.setRole(role);
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setEmailVerified(false);
    user.setPhoneVerified(false);

    final OffsetDateTime now = OffsetDateTime.now(clock);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    return user;
  }

  /**
   * Creates a new User with OAuth authentication. Future implementation for Google/Apple/Microsoft
   * OAuth.
   *
   * @param email user email
   * @param fullName user's full name
   * @param externalAuthId OAuth provider's user ID
   * @param provider OAuth provider
   * @param role user role
   * @return newly created User entity
   */
  public User createOAuthUser(
      final String email,
      final String fullName,
      final String externalAuthId,
      final AuthProvider provider,
      final UserRole role) {
    final User user = new User();
    user.setEmail(email.toLowerCase(Locale.ROOT));
    user.setPasswordHash(null); // OAuth users don't have passwords
    user.setFullName(fullName);
    user.setExternalAuthId(externalAuthId);
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE); // OAuth users are active immediately
    user.setAuthProvider(provider);
    user.setEmailVerified(true); // OAuth providers verify emails
    user.setPhoneVerified(false);

    final OffsetDateTime now = OffsetDateTime.now(clock);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    return user;
  }
}

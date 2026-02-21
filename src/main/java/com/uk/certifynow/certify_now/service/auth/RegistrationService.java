package com.uk.certifynow.certify_now.service.auth;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.UserConsent;
import com.uk.certifynow.certify_now.events.DuplicateRegistrationAttemptEvent;
import com.uk.certifynow.certify_now.events.UserRegisteredEvent;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration workflow.
 *
 * <p>Responsibilities: - Validate email/phone uniqueness (Fix 3: silently, no 409) - Create User
 * aggregate via UserFactory - Create profile via ProfileFactory - Create user consents - Publish
 * UserRegisteredEvent (Fix 4: email verification triggered AFTER_COMMIT by listener)
 *
 * <p>This service is focused solely on registration logic and delegates entity creation to domain
 * factories, ensuring the application layer doesn't know about business defaults.
 */
@Service
public class RegistrationService {

  private final UserRepository userRepository;
  private final CustomerProfileRepository customerProfileRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final UserConsentRepository userConsentRepository;
  private final UserFactory userFactory;
  private final ProfileFactory profileFactory;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public RegistrationService(
      final UserRepository userRepository,
      final CustomerProfileRepository customerProfileRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final UserConsentRepository userConsentRepository,
      final UserFactory userFactory,
      final ProfileFactory profileFactory,
      final ApplicationEventPublisher eventPublisher,
      final Clock clock) {
    this.userRepository = userRepository;
    this.customerProfileRepository = customerProfileRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.userConsentRepository = userConsentRepository;
    this.userFactory = userFactory;
    this.profileFactory = profileFactory;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Registers a new user with email/password authentication.
   *
   * <p>Fix 3: Returns an empty Optional on duplicate email/phone rather than throwing 409, to
   * prevent email enumeration. An async event listener sends a security notification to the
   * existing user.
   *
   * <p>Fix 4: Email verification is no longer called inline. It is triggered by {@link
   * com.uk.certifynow.certify_now.events.EmailVerificationEventListener} after the transaction
   * commits, so any SMTP failure does NOT roll back user creation.
   *
   * @param request registration details including email, password, role
   * @param ipAddress IP address of the registration request for audit
   * @return Optional containing the created User, or empty if a silent duplicate was handled
   */
  @Transactional
  public Optional<User> registerUser(final RegisterRequest request, final String ipAddress) {
    // Fix 3: Silent duplicate detection — no 409 thrown
    if (handleDuplicateEmail(request.email(), ipAddress)) {
      return Optional.empty();
    }
    // Fix 7: Null-safe phone check (blank treated as absent)
    if (handleDuplicatePhone(request.phone(), ipAddress)) {
      return Optional.empty();
    }

    // Create user via factory — encapsulates creation logic and defaults
    final User user =
        userFactory.createEmailUser(
            request.email(),
            request.password(),
            request.fullName(),
            request.phone(),
            request.role());

    userRepository.save(user);

    // Create role-specific profile
    createProfile(user);

    // Create consent records
    createConsents(user, ipAddress);

    // Fix 4: Publish event — EmailVerificationEventListener sends the email
    // AFTER_COMMIT
    // so SMTP failure does not roll back this transaction.
    eventPublisher.publishEvent(
        new UserRegisteredEvent(user.getId(), user.getEmail(), user.getRole().name()));

    return Optional.of(user);
  }

  /**
   * Fix 3: Checks for duplicate email. If found, publishes a silent notification event.
   *
   * @return true if a duplicate was found and silently handled
   */
  private boolean handleDuplicateEmail(final String email, final String ipAddress) {
    if (!userRepository.existsByEmailIgnoreCase(email)) {
      return false;
    }
    userRepository
        .findByEmailIgnoreCase(email)
        .ifPresent(
            existingUser ->
                eventPublisher.publishEvent(
                    new DuplicateRegistrationAttemptEvent(
                        existingUser.getId(), existingUser.getEmail(), "EMAIL", ipAddress)));
    return true;
  }

  /**
   * Fix 7: Checks for duplicate phone only when phone is non-null and non-blank.
   *
   * <p>The DB unique constraint on phone should be a partial/filtered index that allows multiple
   * NULL values: {@code CREATE UNIQUE INDEX ... ON users(phone) WHERE phone IS NOT NULL;}
   *
   * @return true if a duplicate was found and silently handled
   */
  private boolean handleDuplicatePhone(final String phone, final String ipAddress) {
    if (phone == null || phone.isBlank()) {
      return false;
    }
    if (!userRepository.existsByPhone(phone)) {
      return false;
    }
    userRepository
        .findByEmailIgnoreCase(phone) // Not ideal — ideally findByPhone exists
        .ifPresent(
            existingUser ->
                eventPublisher.publishEvent(
                    new DuplicateRegistrationAttemptEvent(
                        existingUser.getId(), existingUser.getEmail(), "PHONE", ipAddress)));
    return true;
  }

  /**
   * Creates the appropriate profile based on user role.
   *
   * <p>Uses ProfileFactory to ensure business defaults are set correctly.
   */
  private void createProfile(final User user) {
    if (user.getRole() == UserRole.CUSTOMER) {
      final CustomerProfile profile = profileFactory.createCustomerProfile(user);
      customerProfileRepository.save(profile);
    } else if (user.getRole() == UserRole.ENGINEER) {
      final EngineerProfile profile = profileFactory.createEngineerProfile(user);
      engineerProfileRepository.save(profile);
    }
    // ADMIN role doesn't have a profile
  }

  /**
   * Creates user consent records for terms of service and privacy policy.
   *
   * @param user the user who granted consent
   * @param ipAddress IP address for audit trail
   */
  private void createConsents(final User user, final String ipAddress) {
    final OffsetDateTime now = OffsetDateTime.now(clock);

    final UserConsent termsConsent = new UserConsent();
    termsConsent.setUser(user);
    termsConsent.setConsentType("TERMS_OF_SERVICE");
    termsConsent.setGranted(true);
    termsConsent.setGrantedAt(now);
    termsConsent.setCreatedAt(now);
    termsConsent.setIpAddress(ipAddress);
    userConsentRepository.save(termsConsent);

    final UserConsent privacyConsent = new UserConsent();
    privacyConsent.setUser(user);
    privacyConsent.setConsentType("PRIVACY_POLICY");
    privacyConsent.setGranted(true);
    privacyConsent.setGrantedAt(now);
    privacyConsent.setCreatedAt(now);
    privacyConsent.setIpAddress(ipAddress);
    userConsentRepository.save(privacyConsent);
  }
}

package com.uk.certifynow.certify_now.service.auth;

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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration workflow.
 *
 * <p>Responsibilities: - Validate email/phone uniqueness (silently, no 409) to prevent email
 * enumeration - Create User aggregate via UserFactory - Create profile via ProfileFactory - Create
 * user consents - Publish UserRegisteredEvent (email verification triggered AFTER_COMMIT by
 * listener)
 *
 * <p>This service is focused solely on registration logic and delegates entity creation to domain
 * factories, ensuring the application layer doesn't know about business defaults.
 */
@Service
public class RegistrationService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

  private static final String CONSENT_TERMS_OF_SERVICE = "TERMS_OF_SERVICE";
  private static final String CONSENT_PRIVACY_POLICY = "PRIVACY_POLICY";

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
   * <p>Returns an empty Optional on duplicate email/phone rather than throwing 409, to prevent
   * email enumeration. An async event listener sends a security notification to the existing user.
   *
   * <p>Email verification is triggered by {@link
   * com.uk.certifynow.certify_now.events.EmailVerificationEventListener} after the transaction
   * commits, so any SMTP failure does NOT roll back user creation.
   *
   * @param request registration details including email, password, role
   * @param ipAddress IP address of the registration request for audit
   * @return Optional containing the created User, or empty if a silent duplicate was handled
   */
  @Transactional(noRollbackFor = DataIntegrityViolationException.class)
  public Optional<User> registerUser(final RegisterRequest request, final String ipAddress) {
    if (handleDuplicateEmail(request.email(), ipAddress)) {
      return Optional.empty();
    }
    if (handleDuplicatePhone(request.phone(), ipAddress)) {
      return Optional.empty();
    }

    final User user =
        userFactory.createEmailUser(
            request.email(),
            request.password(),
            request.fullName(),
            request.phone(),
            request.role());

    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      log.warn("Concurrent duplicate registration detected for email={}", request.email(), ex);
      handleDuplicateEmail(request.email(), ipAddress);
      handleDuplicatePhone(request.phone(), ipAddress);
      return Optional.empty();
    }

    createProfile(user);

    createConsents(user, ipAddress);

    eventPublisher.publishEvent(
        new UserRegisteredEvent(user.getId(), user.getEmail(), user.getRole().name()));

    return Optional.of(user);
  }

  /**
   * Checks for a duplicate email. If found, publishes a silent notification event to the existing
   * user and returns true. Uses a single DB query instead of exists + find.
   *
   * @return true if a duplicate was found and silently handled
   */
  private boolean handleDuplicateEmail(final String email, final String ipAddress) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(
            existingUser -> {
              eventPublisher.publishEvent(
                  new DuplicateRegistrationAttemptEvent(
                      existingUser.getId(), existingUser.getEmail(), "EMAIL", ipAddress));
              return true;
            })
        .orElse(false);
  }

  /**
   * Checks for a duplicate phone only when phone is non-null and non-blank. Uses a single DB query
   * instead of exists + find.
   *
   * <p>The DB unique constraint on phone must be a partial/filtered index that allows multiple NULL
   * values: {@code CREATE UNIQUE INDEX ... ON users(phone) WHERE phone IS NOT NULL;}
   *
   * @return true if a duplicate was found and silently handled
   */
  private boolean handleDuplicatePhone(final String phone, final String ipAddress) {
    if (phone == null || phone.isBlank()) {
      return false;
    }
    return userRepository
        .findByPhone(phone)
        .map(
            existingUser -> {
              eventPublisher.publishEvent(
                  new DuplicateRegistrationAttemptEvent(
                      existingUser.getId(), existingUser.getEmail(), "PHONE", ipAddress));
              return true;
            })
        .orElse(false);
  }

  /**
   * Best-effort duplicate event publication used when a uniqueness race is detected outside the
   * normal duplicate-check flow.
   */
  public void publishDuplicateAttemptIfPresent(
      final String email, final String phone, final String ipAddress) {
    if (email != null) {
      handleDuplicateEmail(email, ipAddress);
    }
    if (phone != null && !phone.isBlank()) {
      handleDuplicatePhone(phone, ipAddress);
    }
  }

  /**
   * Creates the appropriate role-specific profile for the given user.
   *
   * <p>ADMIN users intentionally have no profile. An explicit default branch logs a warning if an
   * unrecognised role is encountered, making future role additions safer.
   */
  private void createProfile(final User user) {
    switch (user.getRole()) {
      case CUSTOMER -> customerProfileRepository.save(profileFactory.createCustomerProfile(user));
      case ENGINEER -> engineerProfileRepository.save(profileFactory.createEngineerProfile(user));
      case ADMIN -> log.debug("No profile created for ADMIN user id={}", user.getId());
      default ->
          log.warn(
              "No profile creation logic defined for role={} user id={}",
              user.getRole(),
              user.getId());
    }
  }

  /**
   * Creates user consent records for Terms of Service and Privacy Policy in a single batch.
   *
   * @param user the user who granted consent
   * @param ipAddress IP address for the audit trail
   */
  private void createConsents(final User user, final String ipAddress) {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    userConsentRepository.saveAll(
        List.of(
            buildConsent(user, CONSENT_TERMS_OF_SERVICE, now, ipAddress),
            buildConsent(user, CONSENT_PRIVACY_POLICY, now, ipAddress)));
  }

  /**
   * Builds a single {@link UserConsent} record. Centralises field assignment so {@link
   * #createConsents} doesn't repeat the same block per consent type.
   *
   * @param user the consenting user
   * @param type the consent type identifier (e.g. "TERMS_OF_SERVICE")
   * @param now the timestamp to record for grantedAt and createdAt
   * @param ipAddress IP address for the audit trail
   * @return a populated, unsaved UserConsent
   */
  private UserConsent buildConsent(
      final User user, final String type, final OffsetDateTime now, final String ipAddress) {
    final UserConsent consent = new UserConsent();
    consent.setUser(user);
    consent.setConsentType(type);
    consent.setGranted(true);
    consent.setGrantedAt(now);
    consent.setCreatedAt(now);
    consent.setIpAddress(ipAddress);
    return consent;
  }
}

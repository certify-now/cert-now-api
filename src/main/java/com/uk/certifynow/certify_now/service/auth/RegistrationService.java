package com.uk.certifynow.certify_now.service.auth;

import static com.uk.certifynow.certify_now.util.AuthUtils.maskEmail;

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
 * <p>Creates user accounts with email/password authentication, sets up role-specific profiles, and
 * records consent. Prevents email enumeration by silently handling duplicates instead of returning
 * 409 errors.
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
   * <p>Returns empty Optional for duplicate email/phone to prevent enumeration attacks. Email
   * verification is triggered asynchronously by event listener.
   *
   * @param request registration details
   * @param ipAddress requester's IP for audit trail
   * @return created user, or empty if duplicate was silently handled
   */
  @Transactional(noRollbackFor = DataIntegrityViolationException.class)
  public Optional<User> registerUser(final RegisterRequest request, final String ipAddress) {

    log.debug("Registration attempt for email: {}", maskEmail(request.email()));

    // Silent duplicate detection (prevents enumeration)
    if (handleDuplicateEmail(request.email(), ipAddress)) {
      log.info("Registration blocked: duplicate email | email={}", maskEmail(request.email()));
      return Optional.empty();
    }
    if (handleDuplicatePhone(request.phone(), ipAddress)) {
      log.info("Registration blocked: duplicate phone");
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
      log.info("User created successfully | userId={} | role={}", user.getId(), user.getRole());
    } catch (DataIntegrityViolationException ex) {
      log.warn("Concurrent duplicate registration | email={}", maskEmail(request.email()));
      handleDuplicateEmail(request.email(), ipAddress);
      handleDuplicatePhone(request.phone(), ipAddress);
      return Optional.empty();
    }

    try {
      createProfile(user);
      createConsents(user, ipAddress);
    } catch (Exception ex) {
      log.error("Failed to create profile or consents | userId={}", user.getId(), ex);
      throw ex; // Transaction will rollback
    }

    eventPublisher.publishEvent(
        new UserRegisteredEvent(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getAuthProvider() != null ? user.getAuthProvider().name() : null,
            Boolean.TRUE.equals(user.getEmailVerified())));

    return Optional.of(user);
  }

  /**
   * Checks for duplicate email and publishes security notification to existing user if found.
   *
   * @return true if duplicate exists
   */
  private boolean handleDuplicateEmail(final String email, final String ipAddress) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(
            existingUser -> {
              log.debug("Duplicate email detected | userId={}", existingUser.getId());
              eventPublisher.publishEvent(
                  new DuplicateRegistrationAttemptEvent(
                      existingUser.getId(), existingUser.getEmail(), "EMAIL", ipAddress));
              return true;
            })
        .orElse(false);
  }

  /**
   * Checks for duplicate phone and publishes security notification to existing user if found.
   *
   * @return true if duplicate exists
   */
  private boolean handleDuplicatePhone(final String phone, final String ipAddress) {
    if (phone == null || phone.isBlank()) {
      return false;
    }
    return userRepository
        .findByPhone(phone)
        .map(
            existingUser -> {
              log.debug("Duplicate phone detected | userId={}", existingUser.getId());
              eventPublisher.publishEvent(
                  new DuplicateRegistrationAttemptEvent(
                      existingUser.getId(), existingUser.getEmail(), "PHONE", ipAddress));
              return true;
            })
        .orElse(false);
  }

  /**
   * Publishes duplicate attempt events for race condition scenarios where duplicate check was
   * bypassed.
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

  /** Creates role-specific profile (Customer or Engineer). ADMIN users have no profile. */
  private void createProfile(final User user) {
    switch (user.getRole()) {
      case CUSTOMER -> {
        customerProfileRepository.save(profileFactory.createCustomerProfile(user));
        log.debug("Customer profile created | userId={}", user.getId());
      }
      case ENGINEER -> {
        engineerProfileRepository.save(profileFactory.createEngineerProfile(user));
        log.debug("Engineer profile created | userId={}", user.getId());
      }
      case ADMIN -> log.debug("No profile needed for ADMIN | userId={}", user.getId());
      default -> {
        log.error("Unknown role type | role={} | userId={}", user.getRole(), user.getId());
        throw new IllegalStateException("Unsupported role: " + user.getRole());
      }
    }
  }

  /** Creates consent records for Terms of Service and Privacy Policy. */
  private void createConsents(final User user, final String ipAddress) {
    final OffsetDateTime now = OffsetDateTime.now(clock);
    userConsentRepository.saveAll(
        List.of(
            buildConsent(user, CONSENT_TERMS_OF_SERVICE, now, ipAddress),
            buildConsent(user, CONSENT_PRIVACY_POLICY, now, ipAddress)));

    log.debug(
        "Consents recorded | userId={} | types={},{}",
        user.getId(),
        CONSENT_TERMS_OF_SERVICE,
        CONSENT_PRIVACY_POLICY);
  }

  /** Builds a single UserConsent entity. */
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

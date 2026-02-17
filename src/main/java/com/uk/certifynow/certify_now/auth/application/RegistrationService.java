package com.uk.certifynow.certify_now.auth.application;

import com.uk.certifynow.certify_now.auth.domain.UserRole;
import com.uk.certifynow.certify_now.auth.domain.factories.ProfileFactory;
import com.uk.certifynow.certify_now.auth.domain.factories.UserFactory;
import com.uk.certifynow.certify_now.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.auth.event.UserRegisteredEvent;
import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.domain.UserConsent;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration workflow.
 *
 * <p>Responsibilities: - Validate email/phone uniqueness - Create User aggregate via UserFactory -
 * Create profile via ProfileFactory - Create user consents - Publish UserRegisteredEvent
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
   * @param request registration details including email, password, role
   * @param ipAddress IP address of the registration request for audit
   * @return the created User aggregate
   * @throws BusinessException if email or phone already exists
   */
  @Transactional
  public User registerUser(final RegisterRequest request, final String ipAddress) {
    validateUniqueness(request.email(), request.phone());

    // Create user via factory - encapsulates creation logic and defaults
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

    // Publish domain event for downstream processing (e.g., send welcome email)
    eventPublisher.publishEvent(
        new UserRegisteredEvent(user.getId(), user.getEmail(), user.getRole().name()));

    return user;
  }

  /**
   * Validates that email and phone are unique across the system.
   *
   * @throws BusinessException if email or phone already exists
   */
  private void validateUniqueness(final String email, final String phone) {
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email address is already registered");
    }

    if (phone != null && userRepository.existsByPhone(phone)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PHONE_EXISTS", "Phone number is already registered");
    }
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
    termsConsent.setIpAddress(ipAddress);
    userConsentRepository.save(termsConsent);

    final UserConsent privacyConsent = new UserConsent();
    privacyConsent.setUser(user);
    privacyConsent.setConsentType("PRIVACY_POLICY");
    privacyConsent.setGranted(true);
    privacyConsent.setGrantedAt(now);
    privacyConsent.setIpAddress(ipAddress);
    userConsentRepository.save(privacyConsent);
  }
}

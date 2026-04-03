package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.DuplicateRegistrationAttemptEvent;
import com.uk.certifynow.certify_now.events.UserRegisteredEvent;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private UserRepository userRepository;
  @Mock private CustomerProfileRepository customerProfileRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private UserConsentRepository userConsentRepository;
  @Mock private UserFactory userFactory;
  @Mock private ProfileFactory profileFactory;
  @Mock private ApplicationEventPublisher eventPublisher;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new RegistrationService(
            userRepository,
            customerProfileRepository,
            engineerProfileRepository,
            userConsentRepository,
            userFactory,
            profileFactory,
            eventPublisher,
            clock);
  }

  @Test
  void registerUser_happyPath_customerProfile_publishesEvent() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setEmailVerified(false);

    when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
    when(userFactory.createEmailUser(any(), any(), any(), any(), any())).thenReturn(user);
    when(userRepository.saveAndFlush(user)).thenReturn(user);
    when(userConsentRepository.saveAll(any())).thenReturn(null);
    when(customerProfileRepository.save(any())).thenReturn(null);
    when(profileFactory.createCustomerProfile(user)).thenReturn(null);

    final var req =
        new RegisterRequest("new@example.com", "Test1234!", "New User", null, UserRole.CUSTOMER);
    final var result = service.registerUser(req, "1.2.3.4");

    assertThat(result).isPresent();
    verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
  }

  @Test
  void registerUser_duplicateEmail_returnsEmpty_publishesDuplicateEvent() {
    final User existing = TestUserBuilder.buildActiveCustomer();

    when(userRepository.findByEmailIgnoreCase("dup@example.com")).thenReturn(Optional.of(existing));

    final var req =
        new RegisterRequest("dup@example.com", "Test1234!", "Dup User", null, UserRole.CUSTOMER);
    final var result = service.registerUser(req, "1.2.3.4");

    assertThat(result).isEmpty();
    verify(eventPublisher).publishEvent(any(DuplicateRegistrationAttemptEvent.class));
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerUser_duplicatePhone_returnsEmpty() {
    final User existing = TestUserBuilder.buildActiveCustomer();

    when(userRepository.findByEmailIgnoreCase("notdup@example.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhone("+447700900000")).thenReturn(Optional.of(existing));

    final var req =
        new RegisterRequest(
            "notdup@example.com", "Test1234!", "New User", "+447700900000", UserRole.CUSTOMER);
    final var result = service.registerUser(req, "1.2.3.4");

    assertThat(result).isEmpty();
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerUser_nullPhone_notCheckedForDuplicate() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setEmailVerified(false);

    when(userRepository.findByEmailIgnoreCase("ok@example.com")).thenReturn(Optional.empty());
    when(userFactory.createEmailUser(any(), any(), any(), any(), any())).thenReturn(user);
    when(userRepository.saveAndFlush(user)).thenReturn(user);
    when(userConsentRepository.saveAll(any())).thenReturn(null);
    when(customerProfileRepository.save(any())).thenReturn(null);
    when(profileFactory.createCustomerProfile(user)).thenReturn(null);

    final var req =
        new RegisterRequest("ok@example.com", "Test1234!", "User", null, UserRole.CUSTOMER);
    final var result = service.registerUser(req, "1.2.3.4");

    assertThat(result).isPresent();
    verify(userRepository, never()).findByPhone(any());
  }
}

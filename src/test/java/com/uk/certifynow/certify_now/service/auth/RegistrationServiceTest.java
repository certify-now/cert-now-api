package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.CustomerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.DuplicateRegistrationAttemptEvent;
import com.uk.certifynow.certify_now.events.UserRegisteredEvent;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserConsentRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationService — Fixes 3, 4, 7")
class RegistrationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CustomerProfileRepository customerProfileRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private UserConsentRepository userConsentRepository;
  @Mock private UserFactory userFactory;
  @Mock private ProfileFactory profileFactory;
  @Mock private ApplicationEventPublisher eventPublisher;

  private Clock clock;
  private RegistrationService service;

  private static final Instant FIXED_INSTANT = Instant.parse("2026-02-21T00:00:00Z");
  private static final String EMAIL = "new@example.com";
  private static final String PASSWORD = "Password123!";
  private static final String FULL_NAME = "New User";
  private static final String PHONE = "+441234567890";
  private static final String IP = "1.2.3.4";

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
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

  @Nested
  @DisplayName("Fix 3 — Email enumeration prevention")
  class EmailEnumerationPrevention {

    @Test
    @DisplayName("should return empty Optional silently on duplicate email (no exception)")
    void shouldReturnEmptyOnDuplicateEmail() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
      final User existingUser = createUser();
      when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existingUser));

      final RegisterRequest request = registerRequest(EMAIL, PHONE);
      final Optional<User> result = service.registerUser(request, IP);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should publish DuplicateRegistrationAttemptEvent on duplicate email")
    void shouldPublishEventOnDuplicateEmail() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
      final User existingUser = createUser();
      when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existingUser));

      service.registerUser(registerRequest(EMAIL, PHONE), IP);

      final ArgumentCaptor<DuplicateRegistrationAttemptEvent> captor =
          ArgumentCaptor.forClass(DuplicateRegistrationAttemptEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue().getTargetEmail()).isEqualTo(existingUser.getEmail());
      assertThat(captor.getValue().getCollisionType()).isEqualTo("EMAIL");
      assertThat(captor.getValue().getIpAddress()).isEqualTo(IP);
    }

    @Test
    @DisplayName("should NOT save user when duplicate email detected")
    void shouldNotSaveUserOnDuplicateEmail() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
      when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(createUser()));

      service.registerUser(registerRequest(EMAIL, PHONE), IP);

      verify(userRepository, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("Fix 4 — AFTER_COMMIT email verification")
  class AfterCommitEmailVerification {

    @Test
    @DisplayName("should publish UserRegisteredEvent (not call sendVerificationEmail directly)")
    void shouldPublishUserRegisteredEventNotCallEmailInline() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
      final User user = createUser();
      when(userFactory.createEmailUser(any(), any(), any(), any(), any())).thenReturn(user);
      when(userRepository.save(any())).thenReturn(user);
      when(profileFactory.createCustomerProfile(any())).thenReturn(new CustomerProfile());
      when(userConsentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      service.registerUser(registerRequest(EMAIL, null), IP);

      verify(eventPublisher)
          .publishEvent(argThat((Object event) -> event instanceof UserRegisteredEvent));
    }
  }

  @Nested
  @DisplayName("Fix 7 — Null-safe phone check")
  class NullSafePhoneCheck {

    @Test
    @DisplayName("should not query phone uniqueness when phone is null")
    void shouldSkipPhoneCheckWhenNull() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
      final User user = createUser();
      when(userFactory.createEmailUser(any(), any(), any(), any(), any())).thenReturn(user);
      when(userRepository.save(any())).thenReturn(user);
      when(profileFactory.createCustomerProfile(any())).thenReturn(new CustomerProfile());
      when(userConsentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      // Should not throw and should succeed
      final Optional<User> result = service.registerUser(registerRequest(EMAIL, null), IP);

      assertThat(result).isPresent();
      // existsByPhone should never be called
      verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    @DisplayName("should not query phone uniqueness when phone is blank")
    void shouldSkipPhoneCheckWhenBlank() {
      when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
      final User user = createUser();
      when(userFactory.createEmailUser(any(), any(), any(), any(), any())).thenReturn(user);
      when(userRepository.save(any())).thenReturn(user);
      when(profileFactory.createCustomerProfile(any())).thenReturn(new CustomerProfile());
      when(userConsentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      final Optional<User> result = service.registerUser(registerRequest(EMAIL, "  "), IP);

      assertThat(result).isPresent();
      verify(userRepository, never()).existsByPhone(any());
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private RegisterRequest registerRequest(final String email, final String phone) {
    return new RegisterRequest(email, PASSWORD, FULL_NAME, phone, UserRole.CUSTOMER);
  }

  private User createUser() {
    final User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(EMAIL);
    user.setRole(UserRole.CUSTOMER);
    user.setStatus(UserStatus.ACTIVE);
    user.setCreatedAt(OffsetDateTime.now(clock));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    return user;
  }
}

package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.events.UserLoggedInEvent;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService")
class AuthenticationServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private ApplicationEventPublisher eventPublisher;

  private Clock clock;

  @InjectMocks private AuthenticationService authenticationService;

  private static final String TEST_EMAIL = "test@example.com";
  private static final String TEST_PASSWORD = "password123";
  private static final String TEST_PASSWORD_HASH = "$2a$10$encodedPasswordHash";
  private static final String TEST_DEVICE_INFO = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
  private static final Instant FIXED_INSTANT = Instant.parse("2026-02-17T10:00:00Z");

  private User testUser;

  @BeforeEach
  void setUp() {
    // Use a fixed clock for deterministic testing
    clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    authenticationService = new AuthenticationService(userRepository, passwordEncoder, eventPublisher, clock);

    testUser = createTestUser(UserStatus.ACTIVE);
  }

  @Nested
  @DisplayName("authenticate()")
  class Authenticate {

    @Test
    @DisplayName("should successfully authenticate user with valid credentials")
    void shouldAuthenticateWithValidCredentials() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      User result = authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);

      // Verify user was saved with updated last login
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(userCaptor.capture());
      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getLastLoginAt()).isNotNull();

      // Verify event was published
      ArgumentCaptor<UserLoggedInEvent> eventCaptor =
          ArgumentCaptor.forClass(UserLoggedInEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      UserLoggedInEvent event = eventCaptor.getValue();
      assertThat(event.getUserId()).isEqualTo(testUser.getId());
      assertThat(event.getEmail()).isEqualTo(TEST_EMAIL);
      assertThat(event.getDeviceInfo()).isEqualTo(TEST_DEVICE_INFO);
    }

    @Test
    @DisplayName("should throw BusinessException when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Invalid email or password")
          .satisfies(
              exception -> {
                BusinessException businessException = (BusinessException) exception;
                assertThat(businessException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(businessException.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
              });

      // Verify no save or event publication occurred
      verify(userRepository, never()).save(any(User.class));
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should throw BusinessException when password is incorrect")
    void shouldThrowExceptionWhenPasswordIncorrect() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Invalid email or password")
          .satisfies(
              exception -> {
                BusinessException businessException = (BusinessException) exception;
                assertThat(businessException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(businessException.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
              });

      // Verify no save or event publication occurred
      verify(userRepository, never()).save(any(User.class));
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should handle case-insensitive email lookup")
    void shouldHandleCaseInsensitiveEmail() {
      // Arrange
      String mixedCaseEmail = "TeSt@ExAmPlE.cOm";
      when(userRepository.findByEmailIgnoreCase(mixedCaseEmail)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      User result =
          authenticationService.authenticate(mixedCaseEmail, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      assertThat(result).isNotNull();
      verify(userRepository).findByEmailIgnoreCase(mixedCaseEmail);
    }

    @Test
    @DisplayName("should update last login timestamp using provided clock")
    void shouldUpdateLastLoginWithClock() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      OffsetDateTime expectedTime = OffsetDateTime.now(clock);

      // Act
      authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(userCaptor.capture());
      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getLastLoginAt()).isEqualTo(expectedTime);
      assertThat(savedUser.getUpdatedAt()).isEqualTo(expectedTime);
    }
  }

  @Nested
  @DisplayName("Account Status Validation")
  class AccountStatusValidation {

    @Test
    @DisplayName("should allow authentication for ACTIVE user")
    void shouldAllowActiveUser() {
      // Arrange
      testUser.setStatus(UserStatus.ACTIVE);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      User result = authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      assertThat(result).isNotNull();
      verify(eventPublisher).publishEvent(any(UserLoggedInEvent.class));
    }

    @Test
    @DisplayName("should allow authentication for PENDING_VERIFICATION user")
    void shouldAllowPendingVerificationUser() {
      // Arrange
      testUser.setStatus(UserStatus.PENDING_VERIFICATION);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      User result = authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      assertThat(result).isNotNull();
      verify(eventPublisher).publishEvent(any(UserLoggedInEvent.class));
    }

    @Test
    @DisplayName("should throw BusinessException for DEACTIVATED user")
    void shouldRejectDeactivatedUser() {
      // Arrange
      testUser.setStatus(UserStatus.DEACTIVATED);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("account has been deactivated")
          .satisfies(
              exception -> {
                BusinessException businessException = (BusinessException) exception;
                assertThat(businessException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(businessException.getErrorCode()).isEqualTo("ACCOUNT_DEACTIVATED");
              });

      // Verify no save or event publication occurred
      verify(userRepository, never()).save(any(User.class));
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should throw BusinessException for SUSPENDED user")
    void shouldRejectSuspendedUser() {
      // Arrange
      testUser.setStatus(UserStatus.SUSPENDED);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("account has been suspended")
          .satisfies(
              exception -> {
                BusinessException businessException = (BusinessException) exception;
                assertThat(businessException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(businessException.getErrorCode()).isEqualTo("ACCOUNT_SUSPENDED");
              });

      // Verify no save or event publication occurred
      verify(userRepository, never()).save(any(User.class));
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should validate account status before updating last login")
    void shouldValidateStatusBeforeUpdatingLastLogin() {
      // Arrange
      testUser.setStatus(UserStatus.SUSPENDED);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class);

      // Verify user was never saved (last login not updated)
      verify(userRepository, never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("Event Publishing")
  class EventPublishing {

    @Test
    @DisplayName("should publish UserLoggedInEvent with correct details")
    void shouldPublishEventWithCorrectDetails() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert
      ArgumentCaptor<UserLoggedInEvent> eventCaptor =
          ArgumentCaptor.forClass(UserLoggedInEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      UserLoggedInEvent event = eventCaptor.getValue();
      assertThat(event.getUserId()).isEqualTo(testUser.getId());
      assertThat(event.getEmail()).isEqualTo(TEST_EMAIL);
      assertThat(event.getDeviceInfo()).isEqualTo(TEST_DEVICE_INFO);
    }

    @Test
    @DisplayName("should publish event after updating last login")
    void shouldPublishEventAfterUpdatingLastLogin() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
      when(userRepository.save(any(User.class))).thenReturn(testUser);

      // Act
      authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      // Assert - verify order: save happens before event publication
      var inOrder = org.mockito.Mockito.inOrder(userRepository, eventPublisher);
      inOrder.verify(userRepository).save(any(User.class));
      inOrder.verify(eventPublisher).publishEvent(any(UserLoggedInEvent.class));
    }

    @Test
    @DisplayName("should not publish event when authentication fails")
    void shouldNotPublishEventOnAuthenticationFailure() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class);

      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("should not publish event when account is not in good standing")
    void shouldNotPublishEventForInvalidAccountStatus() {
      // Arrange
      testUser.setStatus(UserStatus.DEACTIVATED);
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class);

      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("Security Considerations")
  class SecurityConsiderations {

    @Test
    @DisplayName("should use same error message for non-existent user and wrong password")
    void shouldUseSameErrorMessageForSecurityReasons() {
      // Test non-existent user
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());

      String messageForNonExistentUser = null;
      try {
        authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      } catch (BusinessException e) {
        messageForNonExistentUser = e.getMessage();
      }

      // Test wrong password
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

      String messageForWrongPassword = null;
      try {
        authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      } catch (BusinessException e) {
        messageForWrongPassword = e.getMessage();
      }

      // Assert both messages are identical (to prevent user enumeration)
      assertThat(messageForNonExistentUser).isEqualTo(messageForWrongPassword);
    }

    @Test
    @DisplayName("should not expose password hash in exceptions")
    void shouldNotExposePasswordHashInExceptions() {
      // Arrange
      when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
      when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

      // Act & Assert
      assertThatThrownBy(
              () -> authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              exception -> {
                String exceptionMessage = exception.getMessage();
                assertThat(exceptionMessage).doesNotContain(TEST_PASSWORD_HASH);
                assertThat(exceptionMessage).doesNotContain(TEST_PASSWORD);
              });
    }
  }

  // Helper methods

  private User createTestUser(UserStatus status) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(TEST_EMAIL);
    user.setFullName("Test User");
    user.setPasswordHash(TEST_PASSWORD_HASH);
    user.setStatus(status);
    user.setRole(UserRole.CUSTOMER);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.now(clock));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    return user;
  }
}

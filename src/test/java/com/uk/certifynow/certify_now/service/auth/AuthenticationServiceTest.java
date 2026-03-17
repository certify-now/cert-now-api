package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.LoginFailedEvent;
import com.uk.certifynow.certify_now.events.UserLoggedInEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.TestConstants;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock = TestConstants.FIXED_CLOCK;

  private AuthenticationService authenticationService;

  @BeforeEach
  void setUp() {
    authenticationService =
        new AuthenticationService(userRepository, passwordEncoder, eventPublisher, clock);
  }

  @Test
  void authenticate_success_publishesUserLoggedInEvent() {
    final User user = buildActiveUser();
    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(userRepository.save(any(User.class))).thenReturn(user);

    final User result =
        authenticationService.authenticate(
            "test@example.com", "password123", "Chrome/Desktop", "192.168.1.1");

    assertThat(result.getId()).isEqualTo(user.getId());

    final ArgumentCaptor<UserLoggedInEvent> captor =
        ArgumentCaptor.forClass(UserLoggedInEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    final UserLoggedInEvent event = captor.getValue();
    assertThat(event.getUserId()).isEqualTo(user.getId());
    assertThat(event.getEmail()).isEqualTo("test@example.com");
    assertThat(event.getRole()).isEqualTo("CUSTOMER");
    assertThat(event.getDeviceInfo()).isEqualTo("Chrome/Desktop");
    assertThat(event.getIpAddress()).isEqualTo("192.168.1.1");
  }

  @Test
  void authenticate_unknownEmail_publishesLoginFailedEventAndThrows() {
    when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authenticationService.authenticate(
                    "unknown@example.com", "password123", "Chrome/Desktop", "10.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid email or password");

    final ArgumentCaptor<LoginFailedEvent> captor = ArgumentCaptor.forClass(LoginFailedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    final LoginFailedEvent event = captor.getValue();
    assertThat(event.getEmail()).isEqualTo("unknown@example.com");
    assertThat(event.getReason()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
  }

  @Test
  void authenticate_wrongPassword_publishesLoginFailedEventAndThrows() {
    final User user = buildActiveUser();
    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

    assertThatThrownBy(
            () ->
                authenticationService.authenticate(
                    "test@example.com", "wrong_password", "Chrome/Desktop", "10.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid email or password");

    final ArgumentCaptor<LoginFailedEvent> captor = ArgumentCaptor.forClass(LoginFailedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    assertThat(captor.getValue().getReason()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void authenticate_deactivatedAccount_publishesLoginFailedEventWithErrorCode() {
    final User user = buildActiveUser();
    user.setStatus(UserStatus.DEACTIVATED);
    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);

    assertThatThrownBy(
            () ->
                authenticationService.authenticate(
                    "test@example.com", "password123", "Chrome/Desktop", "10.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("deactivated");

    final ArgumentCaptor<LoginFailedEvent> captor = ArgumentCaptor.forClass(LoginFailedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    assertThat(captor.getValue().getReason()).isEqualTo("ACCOUNT_DEACTIVATED");
  }

  @Test
  void authenticate_suspendedAccount_publishesLoginFailedEventWithErrorCode() {
    final User user = buildActiveUser();
    user.setStatus(UserStatus.SUSPENDED);
    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);

    assertThatThrownBy(
            () ->
                authenticationService.authenticate(
                    "test@example.com", "password123", "Chrome/Desktop", "10.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("suspended");

    final ArgumentCaptor<LoginFailedEvent> captor = ArgumentCaptor.forClass(LoginFailedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    assertThat(captor.getValue().getReason()).isEqualTo("ACCOUNT_SUSPENDED");
  }

  @Test
  void authenticate_success_capturesPreviousLoginAt() {
    final User user = buildActiveUser();
    final OffsetDateTime previousLogin = OffsetDateTime.now(clock).minusDays(5);
    user.setLastLoginAt(previousLogin);

    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(userRepository.save(any(User.class))).thenReturn(user);

    authenticationService.authenticate(
        "test@example.com", "password123", "Chrome/Desktop", "192.168.1.1");

    final ArgumentCaptor<UserLoggedInEvent> captor =
        ArgumentCaptor.forClass(UserLoggedInEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    assertThat(captor.getValue().getLastLoginAt()).isEqualTo(previousLogin);
  }

  @Test
  void authenticate_success_updatesLastLogin() {
    final User user = buildActiveUser();
    when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(userRepository.save(any(User.class))).thenReturn(user);

    authenticationService.authenticate(
        "test@example.com", "password123", "Chrome/Desktop", "192.168.1.1");

    verify(userRepository).save(user);
    assertThat(user.getLastLoginAt()).isNotNull();
  }

  @Test
  void authenticate_unknownEmail_doesNotSaveUser() {
    when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authenticationService.authenticate(
                    "unknown@example.com", "password123", null, null))
        .isInstanceOf(BusinessException.class);

    verify(userRepository, never()).save(any());
  }

  private User buildActiveUser() {
    final User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("test@example.com");
    user.setPasswordHash("hashed_password");
    user.setRole(UserRole.CUSTOMER);
    user.setStatus(UserStatus.ACTIVE);
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setCreatedAt(OffsetDateTime.now(clock).minusDays(30));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    return user;
  }
}

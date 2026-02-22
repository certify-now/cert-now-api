package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.model.UserMeDTO;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.AuthProvider;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, eventPublisher);
  }

  @Test
  @DisplayName("updateMe updates only allowed profile fields")
  void updateMeUpdatesOnlyAllowedFields() {
    final UUID userId = UUID.randomUUID();
    final OffsetDateTime before = OffsetDateTime.parse("2026-02-21T00:00:00Z");
    final User user = testUser(userId);
    user.setUpdatedAt(before);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    final UpdateMeRequest request = new UpdateMeRequest();
    request.setFullName("Updated Name");
    request.setPhone("+447700900099");
    request.setAvatarUrl("https://example.com/avatar.png");

    userService.updateMe(userId, request);

    verify(userRepository).save(user);
    assertThat(user.getFullName()).isEqualTo("Updated Name");
    assertThat(user.getPhone()).isEqualTo("+447700900099");
    assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getPasswordHash()).isEqualTo("$2a$12$existingHash");
    assertThat(user.getUpdatedAt()).isAfter(before);
  }

  @Test
  @DisplayName("updateMe rejects duplicate phone number")
  void updateMeRejectsDuplicatePhone() {
    final UUID userId = UUID.randomUUID();
    final User user = testUser(userId);
    final User anotherUser = testUser(UUID.randomUUID());

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.findByPhone("+447700900099")).thenReturn(Optional.of(anotherUser));

    final UpdateMeRequest request = new UpdateMeRequest();
    request.setPhone("+447700900099");

    assertThatThrownBy(() -> userService.updateMe(userId, request))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Phone number is already in use");
    verify(userRepository, never()).save(user);
  }

  @Test
  @DisplayName("UserMeDTO does not expose password hash")
  void userMeDtoDoesNotExposePasswordHash() {
    assertThat(java.util.Arrays.stream(UserMeDTO.class.getDeclaredFields()).map(Field::getName))
        .doesNotContain("passwordHash");
  }

  private User testUser(final UUID userId) {
    final User user = new User();
    user.setId(userId);
    user.setEmail("user@example.com");
    user.setFullName("Original Name");
    user.setPhone("+447700900001");
    user.setPasswordHash("$2a$12$existingHash");
    user.setAvatarUrl("https://example.com/old.png");
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setRole(UserRole.CUSTOMER);
    user.setStatus(UserStatus.ACTIVE);
    user.setEmailVerified(true);
    user.setPhoneVerified(false);
    user.setCreatedAt(OffsetDateTime.parse("2026-02-20T00:00:00Z"));
    user.setUpdatedAt(OffsetDateTime.parse("2026-02-20T00:00:00Z"));
    return user;
  }
}

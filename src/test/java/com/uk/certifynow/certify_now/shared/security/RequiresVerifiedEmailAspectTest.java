package com.uk.certifynow.certify_now.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequiresVerifiedEmailAspect — Fix 8")
class RequiresVerifiedEmailAspectTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private RequiresVerifiedEmailAspect aspect;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setupSecurityContext() {
    final UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("Verified user")
  class VerifiedUser {

    @Test
    @DisplayName("should not throw when emailVerified is true")
    void shouldPassWhenEmailVerified() {
      final User user = createUser(true);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      // Should not throw
      aspect.checkEmailVerified();
    }
  }

  @Nested
  @DisplayName("Unverified user")
  class UnverifiedUser {

    @Test
    @DisplayName("should throw EMAIL_NOT_VERIFIED when emailVerified is false")
    void shouldThrowWhenEmailNotVerified() {
      final User user = createUser(false);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> aspect.checkEmailVerified())
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException be = (BusinessException) ex;
                Assertions.assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                Assertions.assertThat(be.getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
              });
    }

    @Test
    @DisplayName("should throw EMAIL_NOT_VERIFIED when emailVerified is null")
    void shouldThrowWhenEmailVerifiedIsNull() {
      final User user = createUser(null);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> aspect.checkEmailVerified())
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  Assertions.assertThat(((BusinessException) ex).getErrorCode())
                      .isEqualTo("EMAIL_NOT_VERIFIED"));
    }
  }

  @Nested
  @DisplayName("Missing authentication")
  class MissingAuthentication {

    @Test
    @DisplayName("should throw UNAUTHORIZED when no security context")
    void shouldThrowWhenNoSecurityContext() {
      SecurityContextHolder.clearContext();

      assertThatThrownBy(() -> aspect.checkEmailVerified())
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException be = (BusinessException) ex;
                Assertions.assertThat(be.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                Assertions.assertThat(be.getErrorCode()).isEqualTo("UNAUTHORIZED");
              });
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED when user not found in DB")
    void shouldThrowWhenUserNotFound() {
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> aspect.checkEmailVerified())
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  Assertions.assertThat(((BusinessException) ex).getErrorCode())
                      .isEqualTo("UNAUTHORIZED"));
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private User createUser(final Boolean emailVerified) {
    final User user = new User();
    user.setId(userId);
    user.setEmail("test@example.com");
    user.setEmailVerified(emailVerified);
    return user;
  }
}

package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.EmailService;
import java.lang.reflect.Field;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationTokenRepository tokenRepository;
  @Mock private EmailService emailService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);

  @InjectMocks private EmailVerificationService service;

  private User unverifiedUser;
  private User verifiedUser;

  @BeforeEach
  void setUp() throws Exception {
    // Manually inject the fixed clock since @InjectMocks uses no-arg or matching constructor
    service = new EmailVerificationService(userRepository, tokenRepository, emailService, clock);

    // Set @Value fields via reflection
    setField(service, "tokenExpiryHours", 24);
    setField(service, "resendCooldownSeconds", 60);

    unverifiedUser = buildUser(false);
    verifiedUser = buildUser(true);
  }

  // ════════════════════════════════════════════════════════════════════════════
  // resendVerificationEmail(UUID) — cooldown tests
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("resendVerificationEmail(UUID) — cooldown enforcement")
  class ResendByIdCooldownTests {

    @Test
    @DisplayName("Succeeds when no previous token exists")
    void succeedsWhenNoPreviousToken() {
      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(tokenRepository.findTopByUserOrderByCreatedAtDesc(unverifiedUser))
          .thenReturn(Optional.empty());
      when(tokenRepository.save(any(EmailVerificationToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> service.resendVerificationEmail(unverifiedUser.getId()))
          .doesNotThrowAnyException();

      verify(emailService)
          .sendVerificationEmail(
              eq(unverifiedUser.getEmail()), eq(unverifiedUser.getFullName()), anyString());
    }

    @Test
    @DisplayName("Succeeds when cooldown has elapsed")
    void succeedsWhenCooldownElapsed() {
      final EmailVerificationToken oldToken = buildToken(unverifiedUser, 120); // 120 seconds ago

      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(tokenRepository.findTopByUserOrderByCreatedAtDesc(unverifiedUser))
          .thenReturn(Optional.of(oldToken));
      when(tokenRepository.save(any(EmailVerificationToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> service.resendVerificationEmail(unverifiedUser.getId()))
          .doesNotThrowAnyException();

      verify(emailService)
          .sendVerificationEmail(
              eq(unverifiedUser.getEmail()), eq(unverifiedUser.getFullName()), anyString());
    }

    @Test
    @DisplayName("Throws TOO_MANY_REQUESTS when within cooldown period")
    void throwsWhenWithinCooldown() {
      final EmailVerificationToken recentToken = buildToken(unverifiedUser, 30); // 30 seconds ago

      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(tokenRepository.findTopByUserOrderByCreatedAtDesc(unverifiedUser))
          .thenReturn(Optional.of(recentToken));

      assertThatThrownBy(() -> service.resendVerificationEmail(unverifiedUser.getId()))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException bex = (BusinessException) ex;
                assert bex.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
                assert "RESEND_COOLDOWN".equals(bex.getErrorCode());
              })
          .hasMessageContaining("Please wait")
          .hasMessageContaining("seconds");

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // resendVerificationEmailByEmail(String) — anti-enumeration tests
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("resendVerificationEmailByEmail(String) — anti-enumeration")
  class ResendByEmailTests {

    @Test
    @DisplayName("Returns successfully when email is not found (anti-enumeration)")
    void returnsSuccessfullyWhenEmailNotFound() {
      when(userRepository.findByEmailIgnoreCase("unknown@example.com"))
          .thenReturn(Optional.empty());

      assertThatCode(() -> service.resendVerificationEmailByEmail("unknown@example.com"))
          .doesNotThrowAnyException();

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
      verify(tokenRepository, never()).findTopByUserOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("Returns successfully when email is already verified (anti-enumeration)")
    void returnsSuccessfullyWhenAlreadyVerified() {
      when(userRepository.findByEmailIgnoreCase(verifiedUser.getEmail()))
          .thenReturn(Optional.of(verifiedUser));

      assertThatCode(() -> service.resendVerificationEmailByEmail(verifiedUser.getEmail()))
          .doesNotThrowAnyException();

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
      verify(tokenRepository, never()).findTopByUserOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("Sends verification email for valid unverified user")
    void sendsEmailForUnverifiedUser() {
      when(userRepository.findByEmailIgnoreCase(unverifiedUser.getEmail()))
          .thenReturn(Optional.of(unverifiedUser));
      when(tokenRepository.findTopByUserOrderByCreatedAtDesc(unverifiedUser))
          .thenReturn(Optional.empty());
      when(tokenRepository.save(any(EmailVerificationToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> service.resendVerificationEmailByEmail(unverifiedUser.getEmail()))
          .doesNotThrowAnyException();

      verify(emailService)
          .sendVerificationEmail(
              eq(unverifiedUser.getEmail()), eq(unverifiedUser.getFullName()), anyString());
    }

    @Test
    @DisplayName("Throws TOO_MANY_REQUESTS for unverified user within cooldown")
    void throwsCooldownForUnverifiedUser() {
      final EmailVerificationToken recentToken = buildToken(unverifiedUser, 10);

      when(userRepository.findByEmailIgnoreCase(unverifiedUser.getEmail()))
          .thenReturn(Optional.of(unverifiedUser));
      when(tokenRepository.findTopByUserOrderByCreatedAtDesc(unverifiedUser))
          .thenReturn(Optional.of(recentToken));

      assertThatThrownBy(() -> service.resendVerificationEmailByEmail(unverifiedUser.getEmail()))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException bex = (BusinessException) ex;
                assert bex.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
                assert "RESEND_COOLDOWN".equals(bex.getErrorCode());
              });

      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // updateEmailForUnverifiedUser(UUID, String) — email update tests
  // ════════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("updateEmailForUnverifiedUser(UUID, String)")
  class UpdateEmailTests {

    @Test
    @DisplayName("Successfully updates email for an unverified user")
    void successfullyUpdatesEmailForUnverifiedUser() {
      final String newEmail = "corrected@example.com";

      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(userRepository.findByEmailIgnoreCase(newEmail)).thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(tokenRepository.save(any(EmailVerificationToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> service.updateEmailForUnverifiedUser(unverifiedUser.getId(), newEmail))
          .doesNotThrowAnyException();

      verify(userRepository).save(unverifiedUser);
      assert newEmail.equals(unverifiedUser.getEmail());
      // Called once explicitly in updateEmailForUnverifiedUser and once inside
      // sendVerificationEmail
      verify(tokenRepository, times(2)).deleteUnusedTokensByUserId(unverifiedUser.getId());
      verify(emailService).sendVerificationEmail(eq(newEmail), anyString(), anyString());
    }

    @Test
    @DisplayName("Throws when user is already email-verified")
    void throwsWhenAlreadyVerified() {
      when(userRepository.findById(verifiedUser.getId())).thenReturn(Optional.of(verifiedUser));

      assertThatThrownBy(
              () -> service.updateEmailForUnverifiedUser(verifiedUser.getId(), "new@example.com"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException bex = (BusinessException) ex;
                assert bex.getStatus() == HttpStatus.BAD_REQUEST;
                assert "ALREADY_VERIFIED".equals(bex.getErrorCode());
              })
          .hasMessageContaining("Cannot change email after verification");

      verify(userRepository, never()).save(any());
      verify(tokenRepository, never()).deleteUnusedTokensByUserId(any());
      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Throws when new email is already taken by another user")
    void throwsWhenEmailTakenByAnotherUser() {
      final User otherUser = buildUser(false);
      final String takenEmail = otherUser.getEmail();

      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(userRepository.findByEmailIgnoreCase(takenEmail)).thenReturn(Optional.of(otherUser));

      assertThatThrownBy(
              () -> service.updateEmailForUnverifiedUser(unverifiedUser.getId(), takenEmail))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException bex = (BusinessException) ex;
                assert bex.getStatus() == HttpStatus.CONFLICT;
                assert "EMAIL_TAKEN".equals(bex.getErrorCode());
              })
          .hasMessageContaining("Email is already in use");

      verify(userRepository, never()).save(any());
      verify(tokenRepository, never()).deleteUnusedTokensByUserId(any());
      verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Allows updating to the same email the user already has")
    void allowsSameEmail() {
      final String sameEmail = unverifiedUser.getEmail();

      when(userRepository.findById(unverifiedUser.getId())).thenReturn(Optional.of(unverifiedUser));
      when(userRepository.findByEmailIgnoreCase(sameEmail)).thenReturn(Optional.of(unverifiedUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(tokenRepository.save(any(EmailVerificationToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      assertThatCode(() -> service.updateEmailForUnverifiedUser(unverifiedUser.getId(), sameEmail))
          .doesNotThrowAnyException();

      // Called once explicitly in updateEmailForUnverifiedUser and once inside
      // sendVerificationEmail
      verify(tokenRepository, times(2)).deleteUnusedTokensByUserId(unverifiedUser.getId());
      verify(emailService).sendVerificationEmail(eq(sameEmail), anyString(), anyString());
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════════════════════════════════════════

  private User buildUser(final boolean emailVerified) {
    final User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(emailVerified ? "verified@example.com" : "unverified@example.com");
    user.setFullName("Test User");
    user.setEmailVerified(emailVerified);
    user.setPhoneVerified(false);
    user.setStatus(emailVerified ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION);
    user.setRole(UserRole.CUSTOMER);
    user.setAuthProvider(AuthProvider.EMAIL);
    user.setPasswordHash("$2a$12$dummyHash");
    user.setCreatedAt(OffsetDateTime.now(clock));
    user.setUpdatedAt(OffsetDateTime.now(clock));
    return user;
  }

  private EmailVerificationToken buildToken(final User user, final long secondsAgo) {
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setTokenHash("hash-" + UUID.randomUUID());
    token.setCreatedAt(OffsetDateTime.now(clock).minusSeconds(secondsAgo));
    token.setExpiresAt(OffsetDateTime.now(clock).plusHours(24));
    return token;
  }

  private static void setField(final Object target, final String fieldName, final Object value)
      throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}

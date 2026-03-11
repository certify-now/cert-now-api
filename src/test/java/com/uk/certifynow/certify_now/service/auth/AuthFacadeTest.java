package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.mappers.AuthMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFacade")
class AuthFacadeTest {

  @Mock private RegistrationService registrationService;
  @Mock private AuthenticationService authenticationService;
  @Mock private SessionService sessionService;
  @Mock private AuthMapper authMapper;
  @Mock private EmailVerificationService emailVerificationService;

  @InjectMocks private AuthFacade facade;

  private User activeUser;
  private SessionService.TokenPair tokenPair;
  private AuthResponse authResponse;

  @BeforeEach
  void setUp() {
    activeUser = new User();
    activeUser.setId(UUID.randomUUID());
    activeUser.setEmail("verified@example.com");
    activeUser.setFullName("Verified User");
    activeUser.setEmailVerified(true);
    activeUser.setStatus(UserStatus.ACTIVE);
    activeUser.setRole(UserRole.CUSTOMER);
    activeUser.setAuthProvider(AuthProvider.EMAIL);
    activeUser.setPasswordHash("$2a$12$dummyHash");
    activeUser.setCreatedAt(OffsetDateTime.now());
    activeUser.setUpdatedAt(OffsetDateTime.now());

    tokenPair = new SessionService.TokenPair("new-access-token", "new-refresh-token");
    authResponse =
        new AuthResponse(
            "new-access-token",
            "new-refresh-token",
            "Bearer",
            900L,
            new AuthResponse.UserSummary(
                activeUser.getId(),
                activeUser.getEmail(),
                activeUser.getFullName(),
                null,
                "CUSTOMER",
                "ACTIVE",
                true,
                false,
                null,
                activeUser.getCreatedAt(),
                null,
                null));
  }

  // ══════════════════════════════════════════════════════════════════════
  // verifyEmailAndIssueTokens — fresh token issuance after verification
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("verifyEmailAndIssueTokens")
  class VerifyEmailAndIssueTokensTests {

    @Test
    @DisplayName("Verifies email, issues tokens and returns AuthResponse")
    void issuesTokensForActivatedUser() {
      when(emailVerificationService.verifyEmail("123456")).thenReturn(activeUser);
      when(sessionService.issueTokens(eq(activeUser), isNull(), anyString())).thenReturn(tokenPair);
      when(authMapper.toAuthResponse(activeUser, tokenPair)).thenReturn(authResponse);

      final AuthResponse result = facade.verifyEmailAndIssueTokens("123456", "127.0.0.1");

      assertThat(result.accessToken()).isEqualTo("new-access-token");
      assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
      assertThat(result.user()).isNotNull();
      assertThat(result.user().email()).isEqualTo("verified@example.com");

      verify(emailVerificationService).verifyEmail("123456");
      verify(sessionService).issueTokens(eq(activeUser), isNull(), eq("127.0.0.1"));
      verify(authMapper).toAuthResponse(activeUser, tokenPair);
    }

    @Test
    @DisplayName("Propagates exception from EmailVerificationService on invalid code")
    void propagatesExceptionOnInvalidCode() {
      when(emailVerificationService.verifyEmail("000000"))
          .thenThrow(
              new com.uk.certifynow.certify_now.exception.BusinessException(
                  org.springframework.http.HttpStatus.BAD_REQUEST,
                  "INVALID_TOKEN",
                  "Invalid or expired verification token"));

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> facade.verifyEmailAndIssueTokens("000000", "127.0.0.1"))
          .isInstanceOf(com.uk.certifynow.certify_now.exception.BusinessException.class);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  // resendVerificationByEmail — thin delegate
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("resendVerificationByEmail")
  class ResendVerificationByEmailTests {

    @Test
    @DisplayName("Delegates to EmailVerificationService.resendVerificationEmailByEmail")
    void delegatesToService() {
      facade.resendVerificationByEmail("user@example.com");
      verify(emailVerificationService).resendVerificationEmailByEmail("user@example.com");
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  // updateEmailForUnverifiedUser — thin delegate
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("updateEmailForUnverifiedUser")
  class UpdateEmailForUnverifiedUserTests {

    @Test
    @DisplayName("Delegates to EmailVerificationService.updateEmailForUnverifiedUser")
    void delegatesToService() {
      final UUID userId = activeUser.getId();
      facade.updateEmailForUnverifiedUser(userId, "new@example.com");
      verify(emailVerificationService).updateEmailForUnverifiedUser(userId, "new@example.com");
    }
  }
}

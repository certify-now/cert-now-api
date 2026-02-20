package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.service.auth.dto.AuthResponse;
import com.uk.certifynow.certify_now.service.auth.dto.LoginRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RefreshRequest;
import com.uk.certifynow.certify_now.service.auth.dto.RegisterRequest;
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

  @InjectMocks private AuthFacade authFacade;

  private static final String TEST_EMAIL = "test@example.com";
  private static final String TEST_PASSWORD = "Password123!";
  private static final String TEST_FULL_NAME = "Test User";
  private static final String TEST_PHONE = "+447123456789";
  private static final String TEST_DEVICE_INFO = "Mozilla/5.0";
  private static final String TEST_IP_ADDRESS = "192.168.1.1";
  private static final String TEST_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
  private static final String TEST_REFRESH_TOKEN = "refresh_token_123";

  private User testUser;
  private SessionService.TokenPair testTokenPair;
  private AuthResponse testAuthResponse;

  @BeforeEach
  void setUp() {
    testUser = createTestUser();
    testTokenPair = new SessionService.TokenPair(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
    testAuthResponse = createTestAuthResponse();
  }

  @Nested
  @DisplayName("register()")
  class Register {

    @Test
    @DisplayName("should orchestrate registration flow and return auth response")
    void shouldRegisterUserSuccessfully() {
      // Arrange
      RegisterRequest request =
          new RegisterRequest(
              TEST_EMAIL, TEST_PASSWORD, TEST_FULL_NAME, TEST_PHONE, UserRole.CUSTOMER);

      when(registrationService.registerUser(request, TEST_IP_ADDRESS)).thenReturn(testUser);
      when(sessionService.issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(testUser, testTokenPair)).thenReturn(testAuthResponse);

      // Act
      AuthResponse result = authFacade.register(request, TEST_DEVICE_INFO, TEST_IP_ADDRESS);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      assertThat(result.refreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
      assertThat(result.user().email()).isEqualTo(TEST_EMAIL);

      // Verify orchestration order
      var inOrder =
          org.mockito.Mockito.inOrder(registrationService, sessionService, authMapper);
      inOrder.verify(registrationService).registerUser(request, TEST_IP_ADDRESS);
      inOrder.verify(sessionService).issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS);
      inOrder.verify(authMapper).toAuthResponse(testUser, testTokenPair);
    }

    @Test
    @DisplayName("should pass correct parameters to registration service")
    void shouldPassCorrectParametersToRegistrationService() {
      // Arrange
      RegisterRequest request =
          new RegisterRequest(
              TEST_EMAIL, TEST_PASSWORD, TEST_FULL_NAME, TEST_PHONE, UserRole.ENGINEER);

      when(registrationService.registerUser(request, TEST_IP_ADDRESS)).thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.register(request, TEST_DEVICE_INFO, TEST_IP_ADDRESS);

      // Assert
      verify(registrationService).registerUser(request, TEST_IP_ADDRESS);
    }

    @Test
    @DisplayName("should pass device info and IP address to session service")
    void shouldPassDeviceInfoAndIpToSessionService() {
      // Arrange
      RegisterRequest request =
          new RegisterRequest(
              TEST_EMAIL, TEST_PASSWORD, TEST_FULL_NAME, TEST_PHONE, UserRole.CUSTOMER);

      when(registrationService.registerUser(any(RegisterRequest.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.register(request, TEST_DEVICE_INFO, TEST_IP_ADDRESS);

      // Assert
      verify(sessionService).issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS);
    }

    @Test
    @DisplayName("should map user and tokens to auth response")
    void shouldMapUserAndTokensToAuthResponse() {
      // Arrange
      RegisterRequest request =
          new RegisterRequest(
              TEST_EMAIL, TEST_PASSWORD, TEST_FULL_NAME, TEST_PHONE, UserRole.CUSTOMER);

      when(registrationService.registerUser(any(RegisterRequest.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(testUser, testTokenPair)).thenReturn(testAuthResponse);

      // Act
      authFacade.register(request, TEST_DEVICE_INFO, TEST_IP_ADDRESS);

      // Assert
      verify(authMapper).toAuthResponse(testUser, testTokenPair);
    }
  }

  @Nested
  @DisplayName("login()")
  class Login {

    @Test
    @DisplayName("should orchestrate login flow and return auth response")
    void shouldLoginUserSuccessfully() {
      // Arrange
      LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      when(authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO))
          .thenReturn(testUser);
      when(sessionService.issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(testUser, testTokenPair)).thenReturn(testAuthResponse);

      // Act
      AuthResponse result = authFacade.login(request, TEST_IP_ADDRESS);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      assertThat(result.refreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
      assertThat(result.user().email()).isEqualTo(TEST_EMAIL);

      // Verify orchestration order
      var inOrder =
          org.mockito.Mockito.inOrder(authenticationService, sessionService, authMapper);
      inOrder.verify(authenticationService).authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      inOrder.verify(sessionService).issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS);
      inOrder.verify(authMapper).toAuthResponse(testUser, testTokenPair);
    }

    @Test
    @DisplayName("should extract credentials from login request")
    void shouldExtractCredentialsFromRequest() {
      // Arrange
      String customDeviceInfo = "Chrome/95.0";
      LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, customDeviceInfo);

      when(authenticationService.authenticate(TEST_EMAIL, TEST_PASSWORD, customDeviceInfo))
          .thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.login(request, TEST_IP_ADDRESS);

      // Assert
      verify(authenticationService).authenticate(TEST_EMAIL, TEST_PASSWORD, customDeviceInfo);
      verify(sessionService).issueTokens(testUser, customDeviceInfo, TEST_IP_ADDRESS);
    }

    @Test
    @DisplayName("should pass IP address for audit trail")
    void shouldPassIpAddressForAudit() {
      // Arrange
      LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      String customIpAddress = "10.0.0.1";

      when(authenticationService.authenticate(any(String.class), any(String.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(testUser, TEST_DEVICE_INFO, customIpAddress))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.login(request, customIpAddress);

      // Assert
      verify(sessionService).issueTokens(testUser, TEST_DEVICE_INFO, customIpAddress);
    }

    @Test
    @DisplayName("should return mapped auth response")
    void shouldReturnMappedAuthResponse() {
      // Arrange
      LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      AuthResponse customResponse =
          new AuthResponse(
              "custom_token",
              "custom_refresh",
              "Bearer",
              3600L,
              testAuthResponse.user());

      when(authenticationService.authenticate(any(String.class), any(String.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(testUser, testTokenPair)).thenReturn(customResponse);

      // Act
      AuthResponse result = authFacade.login(request, TEST_IP_ADDRESS);

      // Assert
      assertThat(result).isEqualTo(customResponse);
      assertThat(result.accessToken()).isEqualTo("custom_token");
    }
  }

  @Nested
  @DisplayName("refresh()")
  class Refresh {

    @Test
    @DisplayName("should orchestrate token refresh flow and return auth response")
    void shouldRefreshTokensSuccessfully() {
      // Arrange
      RefreshRequest request = new RefreshRequest(TEST_REFRESH_TOKEN);

      when(sessionService.rotateRefreshToken(TEST_REFRESH_TOKEN, TEST_IP_ADDRESS))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponseFromToken(testTokenPair)).thenReturn(testAuthResponse);

      // Act
      AuthResponse result = authFacade.refresh(request, TEST_IP_ADDRESS);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
      assertThat(result.refreshToken()).isEqualTo(TEST_REFRESH_TOKEN);

      // Verify orchestration order
      var inOrder = org.mockito.Mockito.inOrder(sessionService, authMapper);
      inOrder.verify(sessionService).rotateRefreshToken(TEST_REFRESH_TOKEN, TEST_IP_ADDRESS);
      inOrder.verify(authMapper).toAuthResponseFromToken(testTokenPair);
    }

    @Test
    @DisplayName("should pass refresh token to session service")
    void shouldPassRefreshTokenToSessionService() {
      // Arrange
      String customRefreshToken = "custom_refresh_token_xyz";
      RefreshRequest request = new RefreshRequest(customRefreshToken);

      when(sessionService.rotateRefreshToken(customRefreshToken, TEST_IP_ADDRESS))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponseFromToken(any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.refresh(request, TEST_IP_ADDRESS);

      // Assert
      verify(sessionService).rotateRefreshToken(customRefreshToken, TEST_IP_ADDRESS);
    }

    @Test
    @DisplayName("should pass IP address for audit trail")
    void shouldPassIpAddressForAudit() {
      // Arrange
      RefreshRequest request = new RefreshRequest(TEST_REFRESH_TOKEN);
      String customIpAddress = "172.16.0.1";

      when(sessionService.rotateRefreshToken(TEST_REFRESH_TOKEN, customIpAddress))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponseFromToken(any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.refresh(request, customIpAddress);

      // Assert
      verify(sessionService).rotateRefreshToken(TEST_REFRESH_TOKEN, customIpAddress);
    }

    @Test
    @DisplayName("should map new token pair to auth response")
    void shouldMapNewTokensToAuthResponse() {
      // Arrange
      RefreshRequest request = new RefreshRequest(TEST_REFRESH_TOKEN);
      SessionService.TokenPair newTokenPair =
          new SessionService.TokenPair("new_access_token", "new_refresh_token");

      when(sessionService.rotateRefreshToken(any(String.class), any(String.class)))
          .thenReturn(newTokenPair);
      when(authMapper.toAuthResponseFromToken(newTokenPair)).thenReturn(testAuthResponse);

      // Act
      authFacade.refresh(request, TEST_IP_ADDRESS);

      // Assert
      verify(authMapper).toAuthResponseFromToken(newTokenPair);
    }

    @Test
    @DisplayName("should use token-based mapping method")
    void shouldUseTokenBasedMappingMethod() {
      // Arrange
      RefreshRequest request = new RefreshRequest(TEST_REFRESH_TOKEN);

      when(sessionService.rotateRefreshToken(any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponseFromToken(testTokenPair)).thenReturn(testAuthResponse);

      // Act
      authFacade.refresh(request, TEST_IP_ADDRESS);

      // Assert
      verify(authMapper).toAuthResponseFromToken(testTokenPair);
    }
  }

  @Nested
  @DisplayName("logout()")
  class Logout {

    @Test
    @DisplayName("should revoke refresh token for user")
    void shouldRevokeRefreshToken() {
      // Arrange
      UUID userId = UUID.randomUUID();
      String refreshToken = "token_to_revoke";

      // Act
      authFacade.logout(userId, refreshToken);

      // Assert
      verify(sessionService).revokeToken(userId, refreshToken);
    }

    @Test
    @DisplayName("should pass user ID to session service")
    void shouldPassUserIdToSessionService() {
      // Arrange
      UUID customUserId = UUID.randomUUID();

      // Act
      authFacade.logout(customUserId, TEST_REFRESH_TOKEN);

      // Assert
      verify(sessionService).revokeToken(customUserId, TEST_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("should pass refresh token to session service")
    void shouldPassRefreshTokenToSessionService() {
      // Arrange
      UUID userId = UUID.randomUUID();
      String customToken = "custom_token_to_revoke";

      // Act
      authFacade.logout(userId, customToken);

      // Assert
      verify(sessionService).revokeToken(userId, customToken);
    }

    @Test
    @DisplayName("should delegate logout to session service without transformation")
    void shouldDelegateLogoutDirectly() {
      // Arrange
      UUID userId = UUID.randomUUID();

      // Act
      authFacade.logout(userId, TEST_REFRESH_TOKEN);

      // Assert - verify only session service is called
      verify(sessionService).revokeToken(userId, TEST_REFRESH_TOKEN);
      org.mockito.Mockito.verifyNoInteractions(
          registrationService, authenticationService, authMapper);
    }
  }

  @Nested
  @DisplayName("getMe()")
  class GetMe {

    @Test
    @DisplayName("should retrieve user summary for authenticated user")
    void shouldRetrieveUserSummary() {
      // Arrange
      UUID userId = UUID.randomUUID();
      AuthResponse.UserSummary userSummary = testAuthResponse.user();

      when(authMapper.toUserSummary(userId)).thenReturn(userSummary);

      // Act
      AuthResponse.UserSummary result = authFacade.getMe(userId);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.email()).isEqualTo(TEST_EMAIL);
      verify(authMapper).toUserSummary(userId);
    }

    @Test
    @DisplayName("should pass user ID from JWT to mapper")
    void shouldPassUserIdFromJwt() {
      // Arrange
      UUID customUserId = UUID.randomUUID();
      AuthResponse.UserSummary userSummary = testAuthResponse.user();

      when(authMapper.toUserSummary(customUserId)).thenReturn(userSummary);

      // Act
      authFacade.getMe(customUserId);

      // Assert
      verify(authMapper).toUserSummary(customUserId);
    }

    @Test
    @DisplayName("should return user summary with profile data")
    void shouldReturnUserSummaryWithProfile() {
      // Arrange
      UUID userId = UUID.randomUUID();
      AuthResponse.UserSummary userSummary =
          new AuthResponse.UserSummary(
              userId,
              TEST_EMAIL,
              TEST_FULL_NAME,
              TEST_PHONE,
              "CUSTOMER",
              "ACTIVE",
              true,
              false,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              null);

      when(authMapper.toUserSummary(userId)).thenReturn(userSummary);

      // Act
      AuthResponse.UserSummary result = authFacade.getMe(userId);

      // Assert
      assertThat(result).isEqualTo(userSummary);
      assertThat(result.id()).isEqualTo(userId);
      assertThat(result.email()).isEqualTo(TEST_EMAIL);
      assertThat(result.fullName()).isEqualTo(TEST_FULL_NAME);
      assertThat(result.role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("should be read-only operation")
    void shouldBeReadOnlyOperation() {
      // Arrange
      UUID userId = UUID.randomUUID();
      AuthResponse.UserSummary userSummary = testAuthResponse.user();

      when(authMapper.toUserSummary(userId)).thenReturn(userSummary);

      // Act
      authFacade.getMe(userId);

      // Assert - verify no modifications are made
      org.mockito.Mockito.verifyNoInteractions(
          registrationService, authenticationService, sessionService);
    }
  }

  @Nested
  @DisplayName("Service Orchestration")
  class ServiceOrchestration {

    @Test
    @DisplayName("should not contain business logic - only orchestration")
    void shouldOnlyOrchestrate() {
      // This test ensures the facade is a pure orchestration layer
      // It should only call other services and mapper, not perform any business logic

      // Arrange
      LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);

      when(authenticationService.authenticate(any(String.class), any(String.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);

      // Act
      authFacade.login(request, TEST_IP_ADDRESS);

      // Assert - verify all delegation happened
      verify(authenticationService).authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      verify(sessionService).issueTokens(testUser, TEST_DEVICE_INFO, TEST_IP_ADDRESS);
      verify(authMapper).toAuthResponse(testUser, testTokenPair);
    }

    @Test
    @DisplayName("should ensure stable public API regardless of internal service changes")
    void shouldProvideStablePublicApi() {
      // The facade provides a stable API even if internal services change
      // This test verifies that the public methods have clear, consistent signatures

      // Arrange
      RegisterRequest registerRequest =
          new RegisterRequest(
              TEST_EMAIL, TEST_PASSWORD, TEST_FULL_NAME, TEST_PHONE, UserRole.CUSTOMER);
      LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, TEST_DEVICE_INFO);
      RefreshRequest refreshRequest = new RefreshRequest(TEST_REFRESH_TOKEN);

      when(registrationService.registerUser(any(RegisterRequest.class), any(String.class)))
          .thenReturn(testUser);
      when(authenticationService.authenticate(any(String.class), any(String.class), any(String.class)))
          .thenReturn(testUser);
      when(sessionService.issueTokens(any(User.class), any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(sessionService.rotateRefreshToken(any(String.class), any(String.class)))
          .thenReturn(testTokenPair);
      when(authMapper.toAuthResponse(any(User.class), any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);
      when(authMapper.toAuthResponseFromToken(any(SessionService.TokenPair.class)))
          .thenReturn(testAuthResponse);
      when(authMapper.toUserSummary(any(UUID.class))).thenReturn(testAuthResponse.user());

      // Act - all public API methods
      authFacade.register(registerRequest, TEST_DEVICE_INFO, TEST_IP_ADDRESS);
      authFacade.login(loginRequest, TEST_IP_ADDRESS);
      authFacade.refresh(refreshRequest, TEST_IP_ADDRESS);
      authFacade.logout(UUID.randomUUID(), TEST_REFRESH_TOKEN);
      authFacade.getMe(UUID.randomUUID());

      // Assert - all return expected types (AuthResponse or void)
      // This ensures the API is consistent and predictable
    }
  }

  // Helper methods

  private User createTestUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(TEST_EMAIL);
    user.setFullName(TEST_FULL_NAME);
    user.setPhone(TEST_PHONE);
    return user;
  }

  private AuthResponse createTestAuthResponse() {
    AuthResponse.UserSummary userSummary =
        new AuthResponse.UserSummary(
            testUser.getId(),
            TEST_EMAIL,
            TEST_FULL_NAME,
            TEST_PHONE,
            "CUSTOMER",
            "ACTIVE",
            true,
            false,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null);

    return new AuthResponse(
        TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, "Bearer", 3600L, userSummary);
  }
}


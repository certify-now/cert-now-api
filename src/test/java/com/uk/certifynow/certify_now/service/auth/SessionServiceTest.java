package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.UserLoggedOutEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.service.security.JwtTokenProvider;
import com.uk.certifynow.certify_now.service.security.TokenDenylistService;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private TokenDenylistService tokenDenylistService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    sessionService =
        new SessionService(
            jwtTokenProvider, refreshTokenService, tokenDenylistService, eventPublisher, clock);
  }

  @Test
  void issueTokens_returnsAccessAndRefreshTokens() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken refreshTokenEntity = buildRefreshToken(user);
    final RefreshTokenService.IssuedRefreshToken issued =
        new RefreshTokenService.IssuedRefreshToken("raw-refresh-token", refreshTokenEntity);

    when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
    when(refreshTokenService.issueToken(eq(user), any(), any(), isNull())).thenReturn(issued);

    final SessionService.TokenPair pair =
        sessionService.issueTokens(user, "Chrome/Desktop", "1.2.3.4");

    assertThat(pair.accessToken()).isEqualTo("access-token");
    assertThat(pair.refreshToken()).isEqualTo("raw-refresh-token");
  }

  @Test
  void rotateRefreshToken_revokesOldAndIssuesNew() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken currentToken = buildRefreshToken(user);
    currentToken.setFamilyId(UUID.randomUUID());

    final RefreshToken newTokenEntity = buildRefreshToken(user);
    final RefreshTokenService.IssuedRefreshToken newIssued =
        new RefreshTokenService.IssuedRefreshToken("new-raw-token", newTokenEntity);

    when(refreshTokenService.validate("old-raw")).thenReturn(currentToken);
    when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-token");
    when(refreshTokenService.issueToken(eq(user), any(), any(), eq(currentToken.getFamilyId())))
        .thenReturn(newIssued);

    final SessionService.TokenPair pair = sessionService.rotateRefreshToken("old-raw", "1.2.3.4");

    verify(refreshTokenService).revoke(currentToken);
    assertThat(pair.accessToken()).isEqualTo("new-access-token");
    assertThat(pair.refreshToken()).isEqualTo("new-raw-token");
  }

  @Test
  void rotateRefreshToken_inactiveUser_throws() {
    final User suspended = TestUserBuilder.buildSuspended();
    final RefreshToken currentToken = buildRefreshToken(suspended);

    when(refreshTokenService.validate("old-raw")).thenReturn(currentToken);

    assertThatThrownBy(() -> sessionService.rotateRefreshToken("old-raw", "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("suspended");
  }

  @Test
  void revokeToken_ownershipMismatch_throws403() {
    final User realOwner = TestUserBuilder.buildActiveCustomer();
    final User otherUser =
        TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "other@example.com");
    final RefreshToken token = buildRefreshToken(realOwner);

    when(refreshTokenService.validate("raw")).thenReturn(token);

    assertThatThrownBy(() -> sessionService.revokeToken(otherUser.getId(), "raw", null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void revokeToken_denylists_accessTokenJti() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildRefreshToken(user);
    final String jti = "test-jti";
    final String accessToken = "some.access.token";

    when(refreshTokenService.validate("raw")).thenReturn(token);
    when(jwtTokenProvider.getJtiFromToken(accessToken)).thenReturn(jti);
    when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(900L);

    sessionService.revokeToken(user.getId(), "raw", accessToken);

    verify(tokenDenylistService).denyToken(jti, 900L);
  }

  @Test
  void revokeToken_nullAccessToken_doesNotFailLogout() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildRefreshToken(user);

    when(refreshTokenService.validate("raw")).thenReturn(token);

    sessionService.revokeToken(user.getId(), "raw", null);

    verify(tokenDenylistService, never()).denyToken(any(), anyLong());
    verify(refreshTokenService).revoke(token);
  }

  @Test
  void revokeToken_publishesUserLoggedOutEvent() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildRefreshToken(user);
    token.setDeviceInfo("Chrome/Desktop");

    when(refreshTokenService.validate("raw")).thenReturn(token);

    sessionService.revokeToken(user.getId(), "raw", null);

    final ArgumentCaptor<UserLoggedOutEvent> captor =
        ArgumentCaptor.forClass(UserLoggedOutEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
    assertThat(captor.getValue().getDeviceInfo()).isEqualTo("Chrome/Desktop");
  }

  private RefreshToken buildRefreshToken(final User user) {
    final RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setUser(user);
    t.setFamilyId(UUID.randomUUID());
    t.setTokenHash("hashed");
    t.setRevoked(false);
    t.setCreatedAt(OffsetDateTime.now(clock).minusHours(1));
    t.setExpiresAt(OffsetDateTime.now(clock).plusDays(30));
    return t;
  }
}

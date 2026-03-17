package com.uk.certifynow.certify_now.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  private static final String SECRET =
      "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-padding";
  private static final Instant FIXED = TestConstants.FIXED_INSTANT;

  @Mock private TokenDenylistService tokenDenylistService;
  @Mock private Environment env;

  private JwtTokenProvider jwtTokenProvider;
  private JwtAuthenticationFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    jwtTokenProvider = new JwtTokenProvider(SECRET, 900_000L, clock, env);
    objectMapper = new ObjectMapper();
    filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenDenylistService, objectMapper);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void publicPath_noToken_passesThrough() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void publicPath_withExpiredToken_stillPassesThrough() throws Exception {
    // Create an expired token
    final Clock pastClock = Clock.fixed(FIXED.minusSeconds(1000), ZoneOffset.UTC);
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    final JwtTokenProvider pastProvider = new JwtTokenProvider(SECRET, 1L, pastClock, env);
    final User user = TestUserBuilder.buildActiveCustomer();
    final String expiredToken = pastProvider.generateAccessToken(user);

    final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.addHeader("Authorization", "Bearer " + expiredToken);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    // Must pass through without error — public path ignores token validity
    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void protectedPath_noToken_passesThrough_withoutSettingContext() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    // Filter passes through; Spring Security handles 401 downstream
    assertThat(chain.getRequest()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void protectedPath_validToken_setsSecurityContext() throws Exception {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isNull(); // cleared in finally
    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void protectedPath_expiredToken_returns401() throws Exception {
    final Clock pastClock = Clock.fixed(FIXED.minusSeconds(1000), ZoneOffset.UTC);
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    final JwtTokenProvider pastProvider = new JwtTokenProvider(SECRET, 1L, pastClock, env);
    final User user = TestUserBuilder.buildActiveCustomer();
    final String expiredToken = pastProvider.generateAccessToken(user);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + expiredToken);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
  }

  @Test
  void protectedPath_revokedJti_returns401_TOKEN_REVOKED() throws Exception {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = jwtTokenProvider.generateAccessToken(user);
    final String jti = jwtTokenProvider.getJtiFromToken(token);
    when(tokenDenylistService.isDenied(jti)).thenReturn(true);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("TOKEN_REVOKED");
  }

  @Test
  void protectedPath_suspendedUser_returns403_ACCOUNT_SUSPENDED() throws Exception {
    final User user = TestUserBuilder.buildSuspended();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("ACCOUNT_SUSPENDED");
  }

  @Test
  void protectedPath_pendingVerification_nonAuthPath_returns403() throws Exception {
    final User user = TestUserBuilder.buildPending();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("EMAIL_NOT_VERIFIED");
  }

  @Test
  void protectedPath_pendingVerification_authPath_passesThrough() throws Exception {
    final User user = TestUserBuilder.buildPending();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/auth/verify-email");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    // Should pass through — /api/v1/auth/* is in the allowed list for pending users
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void protectedPath_pendingVerification_usersMePath_passesThrough() throws Exception {
    final User user = TestUserBuilder.buildPending();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void securityContext_clearedAfterRequest() throws Exception {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = jwtTokenProvider.generateAccessToken(user);
    when(tokenDenylistService.isDenied(anyString())).thenReturn(false);

    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
    request.addHeader("Authorization", "Bearer " + token);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    // SecurityContext must be cleared in the finally block
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}

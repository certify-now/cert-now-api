package com.uk.certifynow.certify_now.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenProviderTest {

  private static final String TEST_SECRET =
      "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-padding";
  private static final long EXPIRY_MS = 900_000L;
  private static final Instant FIXED = TestConstants.FIXED_INSTANT;

  @Mock private Environment env;

  private Clock clock;
  private JwtTokenProvider provider;

  @BeforeEach
  void setUp() {
    clock = TestConstants.FIXED_CLOCK;
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    provider = new JwtTokenProvider(TEST_SECRET, EXPIRY_MS, clock, env);
  }

  @Test
  void generateAccessToken_containsExpectedClaims() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    final Claims claims = provider.parseClaims(token);

    assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
    assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
    assertThat(claims.getId()).isNotBlank();
    assertThat(claims.getIssuedAt()).isNotNull();
    assertThat(claims.getExpiration()).isNotNull();
  }

  @Test
  void generateAccessToken_usesClockForTimestamps() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    final Claims claims = provider.parseClaims(token);

    final Date expectedIat = Date.from(FIXED);
    final Date expectedExp = Date.from(FIXED.plusMillis(EXPIRY_MS));

    assertThat(claims.getIssuedAt()).isEqualTo(expectedIat);
    assertThat(claims.getExpiration()).isEqualTo(expectedExp);
  }

  @Test
  void parseClaims_validToken_returnsClaims() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    final Claims claims = provider.parseClaims(token);

    assertThat(claims).isNotNull();
    assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
  }

  @Test
  void parseClaims_expiredToken_throwsException() {
    // Create provider with clock set in the past so token is already expired
    final Clock pastClock = Clock.fixed(FIXED.minusSeconds(EXPIRY_MS / 1000 + 10), ZoneOffset.UTC);
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    final JwtTokenProvider pastProvider =
        new JwtTokenProvider(TEST_SECRET, EXPIRY_MS, pastClock, env);

    final User user = TestUserBuilder.buildActiveCustomer();
    final String expiredToken = pastProvider.generateAccessToken(user);

    assertThatThrownBy(() -> provider.parseClaims(expiredToken))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void parseClaims_tamperedToken_throwsException() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    final String tampered = token.substring(0, token.length() - 5) + "XXXXX";

    assertThatThrownBy(() -> provider.parseClaims(tampered)).isInstanceOf(Exception.class);
  }

  @Test
  void parseClaims_wrongSigningKey_throwsException() {
    final String otherSecret =
        "other-secret-key-at-least-64-bytes-long-for-hs512-algorithm-padding-x";
    when(env.getActiveProfiles()).thenReturn(new String[] {"test"});
    final JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, EXPIRY_MS, clock, env);

    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = otherProvider.generateAccessToken(user);

    assertThatThrownBy(() -> provider.parseClaims(token)).isInstanceOf(Exception.class);
  }

  @Test
  void validateSecret_prodProfileWithDevSecret_throwsException() {
    when(env.getActiveProfiles()).thenReturn(new String[] {"prod"});
    final JwtTokenProvider p =
        new JwtTokenProvider(
            "dev-secret-key-at-least-512-bits-long-for-hs512-algorithm-change-in-production-pad",
            EXPIRY_MS,
            clock,
            env);
    assertThatThrownBy(p::validateSecret)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Production profile detected");
  }

  @Test
  void validateSecret_prodProfileWithRealSecret_succeeds() {
    when(env.getActiveProfiles()).thenReturn(new String[] {"prod"});
    final JwtTokenProvider prod = new JwtTokenProvider(TEST_SECRET, EXPIRY_MS, clock, env);
    assertThatCode(prod::validateSecret).doesNotThrowAnyException();
  }

  @Test
  void getUserIdFromToken_returnsCorrectId() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    assertThat(provider.getUserIdFromToken(token)).isEqualTo(user.getId());
  }

  @Test
  void getJtiFromToken_returnsNonBlankJti() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final String token = provider.generateAccessToken(user);

    assertThat(provider.getJtiFromToken(token)).isNotBlank();
  }

  @Test
  void getAccessTokenExpirySeconds_returnsCorrectValue() {
    assertThat(provider.getAccessTokenExpirySeconds()).isEqualTo(EXPIRY_MS / 1000);
  }
}

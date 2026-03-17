package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private TransactionStatus txStatus;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    service = new RefreshTokenService(refreshTokenRepository, 5, 30, clock, transactionManager);
  }

  @Test
  void issueToken_createsHashedTokenWithFamilyId() {
    final User user = TestUserBuilder.buildActiveCustomer();
    when(refreshTokenRepository.findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
            any(), any()))
        .thenReturn(List.of());
    when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final UUID familyId = UUID.randomUUID();
    final RefreshTokenService.IssuedRefreshToken issued =
        service.issueToken(user, "Chrome", "1.2.3.4", familyId);

    assertThat(issued.rawToken()).isNotBlank();
    assertThat(issued.entity().getFamilyId()).isEqualTo(familyId);
    assertThat(issued.entity().getTokenHash()).isEqualTo(service.hashToken(issued.rawToken()));
    assertThat(issued.entity().getRevoked()).isFalse();
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void issueToken_nullFamilyId_generatesNewFamily() {
    final User user = TestUserBuilder.buildActiveCustomer();
    when(refreshTokenRepository.findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
            any(), any()))
        .thenReturn(List.of());
    when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final RefreshTokenService.IssuedRefreshToken issued =
        service.issueToken(user, "Chrome", "1.2.3.4", null);

    assertThat(issued.entity().getFamilyId()).isNotNull();
  }

  @Test
  void validate_validToken_returnsEntity() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildActiveToken(user, clock);
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    final RefreshToken result = service.validate("raw-token");

    assertThat(result).isEqualTo(token);
  }

  @Test
  void validate_unknownToken_throws401() {
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.validate("unknown-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void validate_revokedToken_revokesEntireFamily_throws403() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildActiveToken(user, clock);
    token.setRevoked(true);
    final UUID familyId = token.getFamilyId();

    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    when(refreshTokenRepository.findAllByFamilyId(familyId)).thenReturn(List.of(token));

    assertThatThrownBy(() -> service.validate("revoked-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("TOKEN_REUSE_DETECTED");
  }

  @Test
  void validate_expiredToken_throws401() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final RefreshToken token = buildActiveToken(user, clock);
    token.setExpiresAt(OffsetDateTime.now(clock).minusHours(1));

    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.validate("expired-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void enforceActiveTokenLimit_revokesOldestWhenOverLimit() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final List<RefreshToken> activeTokens = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      activeTokens.add(buildActiveToken(user, clock));
    }

    when(refreshTokenRepository.findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(
            eq(user.getId()), any()))
        .thenReturn(activeTokens);
    when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // Issuing a 6th token — oldest (index 0) should be revoked to make room
    service.issueToken(user, "Chrome", "1.2.3.4", null);

    verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // 1 revoke + 1 new
    assertThat(activeTokens.get(0).getRevoked()).isTrue();
  }

  private RefreshToken buildActiveToken(final User user, final Clock clock) {
    final RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setUser(user);
    t.setFamilyId(UUID.randomUUID());
    t.setTokenHash("hashed-" + UUID.randomUUID());
    t.setRevoked(false);
    t.setCreatedAt(OffsetDateTime.now(clock).minusHours(1));
    t.setExpiresAt(OffsetDateTime.now(clock).plusDays(30));
    return t;
  }
}

package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.RefreshToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.shared.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService — Fix 5 (Token Family Tracking)")
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private TransactionStatus transactionStatus;

  private Clock clock;
  private RefreshTokenService service;

  private static final Instant FIXED_INSTANT = Instant.parse("2026-02-21T00:00:00Z");

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    service = new RefreshTokenService(refreshTokenRepository, 5, 30, clock, transactionManager);
  }

  @Nested
  @DisplayName("issueToken()")
  class IssueToken {

    @Test
    @DisplayName("should generate new familyId when null is passed (fresh session)")
    void shouldGenerateNewFamilyIdWhenNull() {
      final User user = createUser();
      when(refreshTokenRepository
              .findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(any(), any()))
          .thenReturn(List.of());
      when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      final RefreshTokenService.IssuedRefreshToken result =
          service.issueToken(user, "device", "1.2.3.4", null);

      assertThat(result.entity().getFamilyId()).isNotNull();
      assertThat(result.rawToken()).isNotBlank();
    }

    @Test
    @DisplayName("should carry existing familyId on rotation")
    void shouldCarryExistingFamilyIdOnRotation() {
      final User user = createUser();
      final UUID existingFamilyId = UUID.randomUUID();
      when(refreshTokenRepository
              .findAllByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtAsc(any(), any()))
          .thenReturn(List.of());
      when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      final RefreshTokenService.IssuedRefreshToken result =
          service.issueToken(user, "device", "1.2.3.4", existingFamilyId);

      assertThat(result.entity().getFamilyId()).isEqualTo(existingFamilyId);
    }
  }

  @Nested
  @DisplayName("validate() — token reuse detection (Fix 5)")
  class ValidateWithFamilyTracking {

    @Test
    @DisplayName("should throw TOKEN_REUSE_DETECTED and revoke family when revoked token presented")
    void shouldRevokeEntireFamilyOnReuseDetection() {
      final UUID familyId = UUID.randomUUID();
      final RefreshToken revokedToken = createRevokedToken(familyId);
      final RefreshToken activeToken = createActiveToken(familyId);

      when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));
      when(refreshTokenRepository.findAllByFamilyId(familyId))
          .thenReturn(List.of(revokedToken, activeToken));
      when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      assertThatThrownBy(() -> service.validate("some-raw-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException be = (BusinessException) ex;
                assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(be.getErrorCode()).isEqualTo("TOKEN_REUSE_DETECTED");
              });

      // Verify the active sibling token was also revoked
      final ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenRepository).save(captor.capture());
      assertThat(captor.getValue().getRevoked()).isTrue();
    }

    @Test
    @DisplayName("should throw INVALID_REFRESH_TOKEN when token not found")
    void shouldThrowWhenTokenNotFound() {
      when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.validate("unknown-token"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                final BusinessException be = (BusinessException) ex;
                assertThat(be.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(be.getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
              });
    }

    @Test
    @DisplayName("should return valid non-revoked, non-expired token")
    void shouldReturnValidToken() {
      final UUID familyId = UUID.randomUUID();
      final RefreshToken activeToken = createActiveToken(familyId);
      when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(activeToken));

      final RefreshToken result = service.validate("some-raw-token");

      assertThat(result).isEqualTo(activeToken);
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private User createUser() {
    final User user = new User();
    user.setId(UUID.randomUUID());
    return user;
  }

  private RefreshToken createRevokedToken(final UUID familyId) {
    final RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setRevoked(true);
    t.setFamilyId(familyId);
    t.setExpiresAt(OffsetDateTime.now(clock).plusDays(30));
    t.setCreatedAt(OffsetDateTime.now(clock));
    t.setIpAddress("1.2.3.4");
    final User user = createUser();
    t.setUser(user);
    return t;
  }

  private RefreshToken createActiveToken(final UUID familyId) {
    final RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setRevoked(false);
    t.setFamilyId(familyId);
    t.setExpiresAt(OffsetDateTime.now(clock).plusDays(30));
    t.setCreatedAt(OffsetDateTime.now(clock));
    t.setIpAddress("1.2.3.4");
    final User user = createUser();
    t.setUser(user);
    return t;
  }
}

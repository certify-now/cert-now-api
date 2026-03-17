package com.uk.certifynow.certify_now.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.EmailVerificationToken;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.EmailVerifiedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.repos.EmailVerificationTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.EmailService;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationTokenRepository tokenRepository;
  @Mock private EmailService emailService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailVerificationService(
            userRepository, tokenRepository, emailService, eventPublisher, clock);
    ReflectionTestUtils.setField(service, "tokenExpiryHours", 24);
    ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60);
  }

  @Test
  void verifyEmail_validToken_updatesUserAndPublishesEvent() {
    final User user = TestUserBuilder.buildPending();
    final String rawCode = "123456";

    final EmailVerificationToken token = new EmailVerificationToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setExpiresAt(OffsetDateTime.now(clock).plusHours(24));
    token.setCreatedAt(OffsetDateTime.now(clock).minusMinutes(5));

    when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final User result = service.verifyEmail(rawCode);

    assertThat(result.getEmailVerified()).isTrue();
    assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(token.isUsed()).isTrue();
    verify(eventPublisher).publishEvent(any(EmailVerifiedEvent.class));
  }

  @Test
  void verifyEmail_unknownToken_throwsBadRequest() {
    when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifyEmail("000000"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void verifyEmail_expiredToken_throwsBadRequest() {
    final User user = TestUserBuilder.buildPending();
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setExpiresAt(OffsetDateTime.now(clock).minusHours(1)); // expired
    token.setCreatedAt(OffsetDateTime.now(clock).minusHours(25));

    when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.verifyEmail("000000"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void verifyEmail_alreadyUsedToken_throwsBadRequest() {
    final User user = TestUserBuilder.buildPending();
    final EmailVerificationToken token = new EmailVerificationToken();
    token.setId(UUID.randomUUID());
    token.setUser(user);
    token.setExpiresAt(OffsetDateTime.now(clock).plusHours(24));
    token.setCreatedAt(OffsetDateTime.now(clock).minusMinutes(5));
    token.markAsUsed(clock); // already used

    when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.verifyEmail("000000")).isInstanceOf(BusinessException.class);
  }

  @Test
  void resendVerificationEmail_cooldownActive_throwsBusinessException() {
    final User user = TestUserBuilder.buildPending();

    final EmailVerificationToken recentToken = new EmailVerificationToken();
    recentToken.setId(UUID.randomUUID());
    recentToken.setUser(user);
    recentToken.setCreatedAt(OffsetDateTime.now(clock).minusSeconds(30)); // within 60s cooldown
    recentToken.setExpiresAt(OffsetDateTime.now(clock).plusHours(24));

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(tokenRepository.findTopByUserOrderByCreatedAtDesc(user))
        .thenReturn(Optional.of(recentToken));

    assertThatThrownBy(() -> service.resendVerificationEmail(user.getId()))
        .isInstanceOf(BusinessException.class);
  }
}

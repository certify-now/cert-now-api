package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.UserSoftDeletedEvent;
import com.uk.certifynow.certify_now.exception.BusinessException;
import com.uk.certifynow.certify_now.model.UpdateMeRequest;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.RefreshTokenRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.service.mappers.UserMapper;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private UserRepository userRepository;
  @Mock private JobRepository jobRepository;
  @Mock private CustomerProfileRepository customerProfileRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private UserMapper userMapper;
  @Mock private PropertyService propertyService;
  @Mock private ObjectMapper objectMapper;

  private UserService service;

  @BeforeEach
  void setUp() {
    service =
        new UserService(
            userRepository,
            jobRepository,
            customerProfileRepository,
            engineerProfileRepository,
            refreshTokenRepository,
            publisher,
            userMapper,
            clock,
            propertyService,
            objectMapper);
  }

  @Test
  void updateMe_blankPhone_setsToNull() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final UpdateMeRequest req = new UpdateMeRequest();
    req.setPhone("  ");

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.updateMe(user.getId(), req);

    assertThat(user.getPhone()).isNull();
  }

  @Test
  void updateMe_existingPhone_fromOtherUser_throws409() {
    final User user = TestUserBuilder.buildActiveCustomer();
    final User otherUser =
        TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "other@example.com");
    otherUser.setPhone("+447700900123");

    final UpdateMeRequest req = new UpdateMeRequest();
    req.setPhone("+447700900123");

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(userRepository.findByPhone("+447700900123")).thenReturn(Optional.of(otherUser));

    assertThatThrownBy(() -> service.updateMe(user.getId(), req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void updateMe_sameUserSamePhone_noConflict() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setPhone("+447700900123");

    final UpdateMeRequest req = new UpdateMeRequest();
    req.setPhone("+447700900123");

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(userRepository.findByPhone("+447700900123")).thenReturn(Optional.of(user));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.updateMe(user.getId(), req);

    assertThat(user.getPhone()).isEqualTo("+447700900123");
  }

  @Test
  void softDelete_withActiveCustomerJobs_throwsConflict() {
    final User user = TestUserBuilder.buildActiveCustomer();

    when(userRepository.findByIdIncludingDeleted(user.getId())).thenReturn(Optional.of(user));
    when(jobRepository.existsActiveJobsByCustomerId(eq(user.getId()), any())).thenReturn(true);

    assertThatThrownBy(() -> service.softDelete(user.getId(), user.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("ACTIVE_JOBS_EXIST");
  }

  @Test
  void softDelete_noActiveJobs_setsDeletedAt_deactivatesStatus() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setCreatedAt(OffsetDateTime.now(clock).minusDays(30));

    when(userRepository.findByIdIncludingDeleted(user.getId())).thenReturn(Optional.of(user));
    when(jobRepository.existsActiveJobsByCustomerId(eq(user.getId()), any())).thenReturn(false);
    when(jobRepository.existsActiveJobsByEngineerId(eq(user.getId()), any())).thenReturn(false);
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(customerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.empty());

    service.softDelete(user.getId(), user.getId());

    assertThat(user.getDeletedAt()).isNotNull();
    assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    verify(refreshTokenRepository).deleteAllByUserId(user.getId());
    verify(publisher).publishEvent(any(UserSoftDeletedEvent.class));
  }

  @Test
  void softDelete_alreadyDeleted_throwsConflict() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setDeletedAt(OffsetDateTime.now(clock).minusDays(1));

    when(userRepository.findByIdIncludingDeleted(user.getId())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.softDelete(user.getId(), UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("ALREADY_DELETED");
  }

  @Test
  void restore_notDeleted_throwsConflict() {
    final User user = TestUserBuilder.buildActiveCustomer();

    when(userRepository.findByIdIncludingDeleted(user.getId())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.restore(user.getId(), UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo("NOT_DELETED");
  }

  @Test
  void restore_deletedUser_clearsDeletedAt_setsActive() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setDeletedAt(OffsetDateTime.now(clock).minusDays(1));
    user.setStatus(UserStatus.DEACTIVATED);

    when(userRepository.findByIdIncludingDeleted(user.getId())).thenReturn(Optional.of(user));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(customerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.empty());

    service.restore(user.getId(), UUID.randomUUID());

    assertThat(user.getDeletedAt()).isNull();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(publisher).publishEvent(any(Object.class));
  }
}

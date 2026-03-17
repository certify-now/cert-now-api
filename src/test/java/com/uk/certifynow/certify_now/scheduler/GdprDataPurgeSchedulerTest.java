package com.uk.certifynow.certify_now.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.CustomerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GdprDataPurgeSchedulerTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private UserRepository userRepository;
  @Mock private CustomerProfileRepository customerProfileRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;

  private GdprDataPurgeScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new GdprDataPurgeScheduler(
            userRepository, customerProfileRepository, engineerProfileRepository, 30, clock);
  }

  @Test
  void purgeExpiredSoftDeletedUsers_noUsersToAnonymize_doesNothing() {
    when(userRepository.findAllDeletedBefore(any())).thenReturn(List.of());

    scheduler.purgeExpiredSoftDeletedUsers();

    verify(userRepository, never()).save(any());
  }

  @Test
  void purgeExpiredSoftDeletedUsers_anonymizesAllPiiFields() {
    final User user = TestUserBuilder.buildActiveCustomer();
    user.setPhone("+447700900123");
    user.setAvatarUrl("https://example.com/avatar.jpg");
    user.setDeletedAt(OffsetDateTime.now(clock).minusDays(31));

    when(userRepository.findAllDeletedBefore(any())).thenReturn(List.of(user));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(customerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.empty());
    when(engineerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.empty());

    scheduler.purgeExpiredSoftDeletedUsers();

    assertThat(user.getFullName()).isEqualTo("Deleted User");
    assertThat(user.getEmail()).contains("@deleted.certifynow.co.uk");
    assertThat(user.getPhone()).isNull();
    assertThat(user.getAvatarUrl()).isNull();
    assertThat(user.getPasswordHash()).isEqualTo("ANONYMIZED");
  }

  @Test
  void purgeExpiredSoftDeletedUsers_engineerProfile_clearedBioAndLocation() {
    final User user = TestUserBuilder.buildActiveEngineer();
    user.setDeletedAt(OffsetDateTime.now(clock).minusDays(31));

    final EngineerProfile profile = new EngineerProfile();
    profile.setId(UUID.randomUUID());
    profile.setUser(user);
    profile.setBio("Experienced engineer");
    profile.setLocation("POINT(-0.1278 51.5074)");
    profile.setIsOnline(true);
    profile.setAcceptanceRate(new BigDecimal("0.90"));
    profile.setAvgRating(new BigDecimal("4.80"));
    profile.setOnTimePercentage(new BigDecimal("95.0"));
    profile.setServiceRadiusMiles(new BigDecimal("10.0"));
    profile.setStripeOnboarded(false);
    profile.setTotalJobsCompleted(0);
    profile.setTotalReviews(0);
    profile.setCreatedAt(OffsetDateTime.now(clock).minusDays(60));
    profile.setUpdatedAt(OffsetDateTime.now(clock).minusDays(31));

    when(userRepository.findAllDeletedBefore(any())).thenReturn(List.of(user));
    when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(customerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.empty());
    when(engineerProfileRepository.findByUserIdIncludingDeleted(user.getId()))
        .thenReturn(Optional.of(profile));
    when(engineerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    scheduler.purgeExpiredSoftDeletedUsers();

    assertThat(profile.getBio()).isNull();
    assertThat(profile.getLocation()).isNull();
    assertThat(profile.getIsOnline()).isFalse();
  }

  @Test
  void anonymizeUser_oneUserFails_othersContinue() {
    final User user1 = TestUserBuilder.buildActiveCustomer();
    user1.setDeletedAt(OffsetDateTime.now(clock).minusDays(31));

    final User user2 = TestUserBuilder.buildActiveCustomer(UUID.randomUUID(), "user2@example.com");
    user2.setDeletedAt(OffsetDateTime.now(clock).minusDays(35));

    when(userRepository.findAllDeletedBefore(any())).thenReturn(List.of(user1, user2));
    // user1 throws an exception during save
    when(userRepository.save(user1)).thenThrow(new RuntimeException("DB error"));
    when(userRepository.save(user2)).thenAnswer(inv -> inv.getArgument(0));
    when(customerProfileRepository.findByUserIdIncludingDeleted(user2.getId()))
        .thenReturn(Optional.empty());
    when(engineerProfileRepository.findByUserIdIncludingDeleted(user2.getId()))
        .thenReturn(Optional.empty());

    // Should not throw — errors are logged, not propagated
    scheduler.purgeExpiredSoftDeletedUsers();

    verify(userRepository).save(user1);
    verify(userRepository).save(user2);
  }
}

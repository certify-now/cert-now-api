package com.uk.certifynow.certify_now.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.service.notification.AdminAlertService;
import com.uk.certifynow.certify_now.service.job.JobResponseMapper;
import com.uk.certifynow.certify_now.service.enums.JobStatus;
import com.uk.certifynow.certify_now.util.TestConstants;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link MatchingService} focusing on escalation and reminder behaviour.
 *
 * <p>Kept in a separate file from {@code MatchingServiceTest} to avoid growing that class further —
 * escalation is a distinct concern from candidate-finding and job-claiming.
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceEscalationTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private JobRepository jobRepository;
  @Mock private EngineerProfileRepository engineerProfileRepository;
  @Mock private JobMatchLogRepository matchLogRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private JobResponseMapper jobResponseMapper;
  @Mock private AdminAlertService adminAlertService;

  private MatchingService matchingService;
  private User customer;
  private Property property;

  @BeforeEach
  void setUp() {
    matchingService =
        new MatchingService(
            jobRepository,
            engineerProfileRepository,
            matchLogRepository,
            userRepository,
            publisher,
            clock,
            jobResponseMapper,
            adminAlertService);

    customer = TestUserBuilder.buildActiveCustomer();
    property = TestPropertyBuilder.buildWithGas(customer);
  }

  // ─── escalateJob ──────────────────────────────────────────────────────────

  @Test
  void escalateJob_setsStatusToEscalated() {
    final Job job = TestJobBuilder.buildCreated(customer, property);

    matchingService.escalateJob(job);

    assertThat(job.getStatus()).isEqualTo(JobStatus.ESCALATED.name());
  }

  @Test
  void escalateJob_setsEscalatedAtAndLastAdminAlertAtAndAdminAlertCountToOne() {
    final Job job = TestJobBuilder.buildCreated(customer, property);
    final OffsetDateTime expectedNow =
        OffsetDateTime.ofInstant(TestConstants.FIXED_INSTANT, java.time.ZoneOffset.UTC);

    matchingService.escalateJob(job);

    assertThat(job.getEscalatedAt()).isEqualTo(expectedNow);
    assertThat(job.getLastAdminAlertAt()).isEqualTo(expectedNow);
    assertThat(job.getAdminAlertCount()).isEqualTo(1);
  }

  @Test
  void escalateJob_callsAdminAlertServiceSendJobEscalationAlert() {
    final Job job = TestJobBuilder.buildCreated(customer, property);

    matchingService.escalateJob(job);

    verify(adminAlertService).sendJobEscalationAlert(job);
  }

  @Test
  void escalateJob_expiresAllPendingMatchLogs() {
    final Job job = TestJobBuilder.buildCreated(customer, property);

    matchingService.escalateJob(job);

    verify(matchLogRepository).expireAllPendingForJob(job.getId());
  }

  // ─── sendEscalationReminderAndRecord ─────────────────────────────────────

  @Test
  void sendEscalationReminderAndRecord_skipsWhenJobIsNoLongerEscalated() {
    final Job job = TestJobBuilder.buildCreated(customer, property);
    job.setStatus(JobStatus.MATCHED.name());
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    matchingService.sendEscalationReminderAndRecord(job);

    verify(adminAlertService, never())
        .sendJobEscalationReminder(any(), any(int.class), any(long.class));
    verify(jobRepository, never()).save(any());
  }

  @Test
  void sendEscalationReminderAndRecord_incrementsAdminAlertCountAndPersists() {
    final Job job = buildEscalatedJob(3);
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    matchingService.sendEscalationReminderAndRecord(job);

    final ArgumentCaptor<Job> savedJobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(savedJobCaptor.capture());
    assertThat(savedJobCaptor.getValue().getAdminAlertCount()).isEqualTo(4);
  }

  @Test
  void sendEscalationReminderAndRecord_updatesLastAdminAlertAt() {
    final Job job = buildEscalatedJob(1);
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    final OffsetDateTime expectedNow =
        OffsetDateTime.ofInstant(TestConstants.FIXED_INSTANT, java.time.ZoneOffset.UTC);

    matchingService.sendEscalationReminderAndRecord(job);

    final ArgumentCaptor<Job> savedJobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(savedJobCaptor.capture());
    assertThat(savedJobCaptor.getValue().getLastAdminAlertAt()).isEqualTo(expectedNow);
  }

  @Test
  void sendEscalationReminderAndRecord_callsAdminAlertServiceWithCorrectCountAndDuration() {
    // Escalated 30 minutes before the fixed clock instant.
    final Job job = buildEscalatedJob(2);
    final OffsetDateTime fixedNow =
        OffsetDateTime.ofInstant(TestConstants.FIXED_INSTANT, java.time.ZoneOffset.UTC);
    job.setEscalatedAt(fixedNow.minusMinutes(30));
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    matchingService.sendEscalationReminderAndRecord(job);

    // currentCount = 2, newCount = 3; escalated 30 min ago.
    verify(adminAlertService).sendJobEscalationReminder(job, 3, 30L);
  }

  @Test
  void sendEscalationReminderAndRecord_defaultsToOneWhenAdminAlertCountIsNull() {
    final Job job = buildEscalatedJob(null);
    when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

    matchingService.sendEscalationReminderAndRecord(job);

    // null → defaults to 1 → newCount = 2
    verify(adminAlertService).sendJobEscalationReminder(eq(job), eq(2), any(long.class));
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private Job buildEscalatedJob(final Integer adminAlertCount) {
    final Job job = TestJobBuilder.buildCreated(customer, property);
    job.setStatus(JobStatus.ESCALATED.name());
    final OffsetDateTime fixedNow =
        OffsetDateTime.ofInstant(TestConstants.FIXED_INSTANT, java.time.ZoneOffset.UTC);
    job.setEscalatedAt(fixedNow.minusHours(1));
    job.setLastAdminAlertAt(fixedNow.minusHours(1));
    job.setAdminAlertCount(adminAlertCount);
    return job;
  }
}

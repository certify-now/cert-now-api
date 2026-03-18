package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.util.TestJobBuilder;
import com.uk.certifynow.certify_now.util.TestPropertyBuilder;
import com.uk.certifynow.certify_now.util.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AdminAlertService}.
 *
 * <p>Both methods are {@code @Async} in production, but tests call them directly on the service
 * instance (bypassing the proxy), so they execute synchronously. This is the correct approach for
 * unit tests — async behaviour is an infrastructure concern, not a business-logic concern.
 */
@ExtendWith(MockitoExtension.class)
class AdminAlertServiceTest {

  private static final String ADMIN_EMAIL = "admin@certnow.co.uk";

  @Mock private EmailService emailService;
  @Mock private SlackNotificationService slackService;

  private AdminAlertService adminAlertService;
  private Job job;

  @BeforeEach
  void setUp() {
    adminAlertService = new AdminAlertService(emailService, slackService);
    ReflectionTestUtils.setField(adminAlertService, "adminEmail", ADMIN_EMAIL);

    final var customer = TestUserBuilder.buildActiveCustomer();
    final var property = TestPropertyBuilder.buildWithGas(customer);
    job = TestJobBuilder.buildCreated(customer, property);
  }

  // ─── sendJobEscalationAlert ───────────────────────────────────────────────

  @Test
  void sendJobEscalationAlert_callsEmailServiceWithCorrectArgs() {
    adminAlertService.sendJobEscalationAlert(job);

    verify(emailService)
        .sendJobEscalationAlert(
            ADMIN_EMAIL,
            job.getId().toString(),
            job.getReferenceNumber(),
            job.getCertificateType(),
            job.getUrgency(),
            job.getTotalPricePence());
  }

  @Test
  void sendJobEscalationAlert_callsSlackServiceWithCorrectArgs() {
    adminAlertService.sendJobEscalationAlert(job);

    verify(slackService)
        .sendJobEscalationAlert(
            job.getId().toString(),
            job.getReferenceNumber(),
            job.getCertificateType(),
            job.getUrgency(),
            job.getTotalPricePence());
  }

  @Test
  void sendJobEscalationAlert_throwsWhenJobIsNull() {
    assertThatThrownBy(() -> adminAlertService.sendJobEscalationAlert(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job must not be null");
  }

  // ─── sendJobEscalationReminder ────────────────────────────────────────────

  @Test
  void sendJobEscalationReminder_callsBothChannelsWithCorrectReminderCountAndDuration() {
    final int reminderCount = 3;
    final long minutesEscalated = 45L;

    adminAlertService.sendJobEscalationReminder(job, reminderCount, minutesEscalated);

    verify(emailService)
        .sendJobEscalationReminder(
            ADMIN_EMAIL,
            job.getId().toString(),
            job.getReferenceNumber(),
            job.getCertificateType(),
            job.getUrgency(),
            job.getTotalPricePence(),
            reminderCount,
            minutesEscalated);

    verify(slackService)
        .sendJobEscalationReminder(
            job.getId().toString(),
            job.getReferenceNumber(),
            job.getCertificateType(),
            job.getUrgency(),
            job.getTotalPricePence(),
            reminderCount,
            minutesEscalated);
  }

  @Test
  void sendJobEscalationReminder_throwsWhenJobIsNull() {
    assertThatThrownBy(() -> adminAlertService.sendJobEscalationReminder(null, 2, 30L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job must not be null");
  }
}

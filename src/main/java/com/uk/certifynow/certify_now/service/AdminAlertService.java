package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Orchestrates admin notifications across all channels (email, Slack).
 *
 * <p>Callers invoke a single method here; this class sequences each notification channel in turn so
 * adding future channels (PagerDuty, Teams, etc.) requires no changes to callers. Channels run
 * sequentially on the {@code matchingTaskExecutor} thread — not concurrently. This is intentional:
 * the overhead is acceptable for low-frequency escalation events, and it avoids spawning additional
 * threads per alert. If a channel becomes slow enough to matter, promote it to its own executor at
 * that point.
 */
@Service
public class AdminAlertService {

  private static final Logger log = LoggerFactory.getLogger(AdminAlertService.class);

  private final EmailService emailService;
  private final SlackNotificationService slackService;

  @Value("${app.admin.email}")
  private String adminEmail;

  public AdminAlertService(
      final EmailService emailService, final SlackNotificationService slackService) {
    this.emailService = emailService;
    this.slackService = slackService;
  }

  /**
   * Sends a job escalation alert to all configured admin channels (email, then Slack), sequentially
   * on {@code matchingTaskExecutor}.
   *
   * <p>This method is the single async boundary: the matching engine returns immediately and all
   * channel I/O happens on a background thread. Individual channel methods are intentionally
   * <em>not</em> {@code @Async} — the async contract is explicit and centralised here so that
   * adding a new channel cannot accidentally block the caller.
   *
   * @param job the escalated job (must not be null; lazy relations are not accessed)
   */
  @Async("matchingTaskExecutor")
  public void sendJobEscalationAlert(final Job job) {
    Assert.notNull(job, "job must not be null");

    final String jobId = job.getId().toString();
    final String ref = job.getReferenceNumber();
    final String certType = job.getCertificateType();
    final String urgency = job.getUrgency();
    final int pricePence = job.getTotalPricePence();

    log.info("Dispatching escalation alert for job {} ({}) via email and Slack", ref, jobId);

    emailService.sendJobEscalationAlert(adminEmail, jobId, ref, certType, urgency, pricePence);
    slackService.sendJobEscalationAlert(jobId, ref, certType, urgency, pricePence);
  }

  /**
   * Sends a follow-up reminder for a job that remains in ESCALATED status. Runs on {@code
   * matchingTaskExecutor} and sequences email then Slack, consistent with the initial alert.
   *
   * @param job the still-escalated job
   * @param reminderCount total number of admin alerts sent so far (2 = first reminder)
   * @param minutesEscalated how long the job has been in ESCALATED status
   */
  @Async("matchingTaskExecutor")
  public void sendJobEscalationReminder(
      final Job job, final int reminderCount, final long minutesEscalated) {
    Assert.notNull(job, "job must not be null");

    final String jobId = job.getId().toString();
    final String ref = job.getReferenceNumber();
    final String certType = job.getCertificateType();
    final String urgency = job.getUrgency();
    final int pricePence = job.getTotalPricePence();

    log.warn(
        "Dispatching escalation reminder #{} for job {} ({}) — escalated for {}min",
        reminderCount,
        ref,
        jobId,
        minutesEscalated);

    emailService.sendJobEscalationReminder(
        adminEmail, jobId, ref, certType, urgency, pricePence, reminderCount, minutesEscalated);
    slackService.sendJobEscalationReminder(
        jobId, ref, certType, urgency, pricePence, reminderCount, minutesEscalated);
  }
}

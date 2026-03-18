package com.uk.certifynow.certify_now.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends operational alerts to Slack via Incoming Webhooks.
 *
 * <p>Configure {@code app.slack.webhook-url} per environment profile. If the property is blank or
 * absent the service logs a debug message and returns silently, so local/CI runs without a real
 * webhook never fail.
 *
 * <p>This class is intentionally <em>not</em> {@code @Async}. The async boundary lives in {@link
 * AdminAlertService}, which sequences all channels on a single dedicated executor thread. Keeping
 * async out of individual channel classes makes the threading contract explicit and prevents
 * accidental executor fragmentation.
 */
@Service
public class SlackNotificationService {

  private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);
  private static final DateTimeFormatter TIMESTAMP_FMT =
      DateTimeFormatter.ofPattern("HH:mm 'UTC,' dd MMM yyyy");

  private final RestClient restClient;

  @Value("${app.slack.webhook-url:}")
  private String webhookUrl;

  public SlackNotificationService(final RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  /**
   * Posts a rich escalation alert to the configured Slack channel. No-ops silently when {@code
   * app.slack.webhook-url} is not set.
   */
  public void sendJobEscalationAlert(
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final int totalPricePence) {

    if (webhookUrl == null || webhookUrl.isBlank()) {
      log.debug("Slack webhook not configured — skipping escalation alert for job {}", jobId);
      return;
    }

    final String formattedPrice = String.format("£%.2f", totalPricePence / 100.0);
    final String escalatedAt = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
    final String payload =
        buildPayload(jobId, referenceNumber, certificateType, urgency, formattedPrice, escalatedAt);

    try {
      restClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
      log.info("Slack escalation alert sent for job {} ({})", referenceNumber, jobId);
    } catch (final Exception ex) {
      // Non-fatal — admin will still receive the email alert.
      log.error(
          "Failed to send Slack escalation alert for job {} — email alert was still dispatched",
          jobId,
          ex);
    }
  }

  private String buildPayload(
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final String formattedPrice,
      final String escalatedAt) {

    // Slack Block Kit payload — renders a structured alert card in the channel.
    return """
        {
          "text": ":rotating_light: *Job Escalated — Admin Action Required* | %s",
          "blocks": [
            {
              "type": "header",
              "text": {
                "type": "plain_text",
                "text": "🚨  Job Escalated — No Engineer Accepted",
                "emoji": true
              }
            },
            {
              "type": "section",
              "fields": [
                { "type": "mrkdwn", "text": "*Job Ref:*\\n%s" },
                { "type": "mrkdwn", "text": "*Certificate Type:*\\n%s" },
                { "type": "mrkdwn", "text": "*Urgency:*\\n%s" },
                { "type": "mrkdwn", "text": "*Price:*\\n%s" },
                { "type": "mrkdwn", "text": "*Escalated At:*\\n%s" }
              ]
            },
            {
              "type": "section",
              "text": {
                "type": "mrkdwn",
                "text": "*Job ID:* `%s`"
              }
            },
            {
              "type": "context",
              "elements": [
                {
                  "type": "mrkdwn",
                  "text": ":warning:  No eligible engineers were found or none accepted within the broadcast window. Manual assignment required."
                }
              ]
            },
            { "type": "divider" }
          ]
        }
        """
        .formatted(
            escapeJson(referenceNumber),
            escapeJson(referenceNumber),
            escapeJson(certificateType),
            escapeJson(urgency),
            escapeJson(formattedPrice),
            escapeJson(escalatedAt),
            escapeJson(jobId));
  }

  /**
   * Posts a follow-up reminder to the configured Slack channel for a job that remains unresolved.
   * No-ops silently when {@code app.slack.webhook-url} is not set.
   */
  public void sendJobEscalationReminder(
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final int totalPricePence,
      final int reminderCount,
      final long minutesEscalated) {

    if (webhookUrl == null || webhookUrl.isBlank()) {
      log.debug("Slack webhook not configured — skipping reminder for job {}", jobId);
      return;
    }

    final String formattedPrice = String.format("£%.2f", totalPricePence / 100.0);
    final String age = formatAge(minutesEscalated);
    final String payload =
        buildReminderPayload(
            jobId, referenceNumber, certificateType, urgency, formattedPrice, reminderCount, age);

    try {
      restClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
      log.info(
          "Slack escalation reminder #{} sent for job {} ({})",
          reminderCount,
          referenceNumber,
          jobId);
    } catch (final Exception ex) {
      log.error(
          "Failed to send Slack escalation reminder for job {} — email reminder was still dispatched",
          jobId,
          ex);
    }
  }

  private String buildReminderPayload(
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final String formattedPrice,
      final int reminderCount,
      final String age) {

    return """
        {
          "text": ":rotating_light: *Reminder #%d — Job Still Unresolved* | %s (%s elapsed)",
          "blocks": [
            {
              "type": "header",
              "text": {
                "type": "plain_text",
                "text": "🔴  Reminder #%d — Job Still Awaiting Action",
                "emoji": true
              }
            },
            {
              "type": "section",
              "fields": [
                { "type": "mrkdwn", "text": "*Job Ref:*\\n%s" },
                { "type": "mrkdwn", "text": "*Certificate Type:*\\n%s" },
                { "type": "mrkdwn", "text": "*Urgency:*\\n%s" },
                { "type": "mrkdwn", "text": "*Price:*\\n%s" },
                { "type": "mrkdwn", "text": "*Escalated For:*\\n:timer_clock: %s" }
              ]
            },
            {
              "type": "section",
              "text": {
                "type": "mrkdwn",
                "text": "*Job ID:* `%s`"
              }
            },
            {
              "type": "context",
              "elements": [
                {
                  "type": "mrkdwn",
                  "text": ":warning:  This job has not been resolved. Reminders will continue until it is assigned or cancelled."
                }
              ]
            },
            { "type": "divider" }
          ]
        }
        """
        .formatted(
            reminderCount,
            escapeJson(referenceNumber),
            escapeJson(age),
            reminderCount,
            escapeJson(referenceNumber),
            escapeJson(certificateType),
            escapeJson(urgency),
            escapeJson(formattedPrice),
            escapeJson(age),
            escapeJson(jobId));
  }

  private String formatAge(final long minutes) {
    if (minutes < 60) return minutes + "min";
    final long hours = minutes / 60;
    final long remainder = minutes % 60;
    return remainder == 0 ? hours + "h" : hours + "h " + remainder + "min";
  }

  /** Escapes characters that would break inline JSON strings. */
  private String escapeJson(final String value) {
    if (value == null) return "";
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}

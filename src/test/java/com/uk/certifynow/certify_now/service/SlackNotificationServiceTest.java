package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link SlackNotificationService}.
 *
 * <p>The {@code RestClient} used for HTTP calls is mocked so tests never make real network
 * requests. The {@code webhookUrl} field is set via {@link ReflectionTestUtils} to simulate the
 * effect of Spring's {@code @Value} injection without loading an application context.
 */
@ExtendWith(MockitoExtension.class)
class SlackNotificationServiceTest {

  private static final String WEBHOOK_URL = "https://hooks.slack.com/services/test/url";
  private static final String JOB_ID = "462c218e-63f4-4d7f-972e-da67a613872b";
  private static final String REF = "CN-0042";
  private static final String CERT_TYPE = CertificateType.GAS_SAFETY.name();
  private static final String URGENCY = "STANDARD";
  private static final int PRICE_PENCE = 7500;

  @Mock private RestClient restClient;
  @Mock private RestClient.Builder restClientBuilder;
  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RestClient.RequestBodySpec requestBodySpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private SlackNotificationService slackService;

  @BeforeEach
  void setUp() {
    when(restClientBuilder.build()).thenReturn(restClient);
    slackService = new SlackNotificationService(restClientBuilder);
  }

  // ─── sendJobEscalationAlert — no-op paths ────────────────────────────────

  @Test
  void sendJobEscalationAlert_noOpsWhenWebhookUrlIsBlank() {
    setWebhookUrl("");

    slackService.sendJobEscalationAlert(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE);

    verify(restClient, never()).post();
  }

  @Test
  void sendJobEscalationAlert_noOpsWhenWebhookUrlIsNull() {
    setWebhookUrl(null);

    slackService.sendJobEscalationAlert(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE);

    verify(restClient, never()).post();
  }

  // ─── sendJobEscalationAlert — error handling ─────────────────────────────

  @Test
  void sendJobEscalationAlert_logsErrorAndDoesNotRethrowWhenRestClientThrows() {
    setWebhookUrl(WEBHOOK_URL);
    stubPostChainThrowing(new RuntimeException("Slack unavailable"));

    // Should not throw — exceptions are caught and logged as non-fatal.
    slackService.sendJobEscalationAlert(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE);

    verify(restClient).post();
  }

  // ─── sendJobEscalationReminder — no-op paths ─────────────────────────────

  @Test
  void sendJobEscalationReminder_noOpsWhenWebhookUrlIsBlank() {
    setWebhookUrl("");

    slackService.sendJobEscalationReminder(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE, 2, 30L);

    verify(restClient, never()).post();
  }

  @Test
  void sendJobEscalationReminder_noOpsWhenWebhookUrlIsNull() {
    setWebhookUrl(null);

    slackService.sendJobEscalationReminder(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE, 2, 30L);

    verify(restClient, never()).post();
  }

  // ─── formatAge — tested via sendJobEscalationReminder payload ────────────

  @Test
  void sendJobEscalationReminder_payloadContainsSubHourAge() {
    setWebhookUrl(WEBHOOK_URL);
    final ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    stubPostChainSucceeding(payloadCaptor);

    slackService.sendJobEscalationReminder(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE, 2, 45L);

    assertThat(payloadCaptor.getValue()).contains("45min");
  }

  @Test
  void sendJobEscalationReminder_payloadContainsExactHourAge() {
    setWebhookUrl(WEBHOOK_URL);
    final ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    stubPostChainSucceeding(payloadCaptor);

    slackService.sendJobEscalationReminder(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE, 2, 60L);

    assertThat(payloadCaptor.getValue()).contains("1h");
    assertThat(payloadCaptor.getValue()).doesNotContain("1h 0min");
  }

  @Test
  void sendJobEscalationReminder_payloadContainsMixedHoursAndMinutesAge() {
    setWebhookUrl(WEBHOOK_URL);
    final ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    stubPostChainSucceeding(payloadCaptor);

    slackService.sendJobEscalationReminder(JOB_ID, REF, CERT_TYPE, URGENCY, PRICE_PENCE, 2, 90L);

    assertThat(payloadCaptor.getValue()).contains("1h 30min");
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private void setWebhookUrl(final String url) {
    ReflectionTestUtils.setField(slackService, "webhookUrl", url);
  }

  private void stubPostChainThrowing(final RuntimeException ex) {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenThrow(ex);
  }

  private void stubPostChainSucceeding(final ArgumentCaptor<String> bodyCaptor) {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.body(bodyCaptor.capture())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(null);
  }
}

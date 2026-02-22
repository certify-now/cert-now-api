package com.uk.certifynow.certify_now.integration;

import com.uk.certifynow.certify_now.shared.service.EmailService;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test-only EmailService that captures the last verification token and duplicate notification so
 * integration tests can retrieve the raw token without needing a real SMTP server.
 *
 * <p>Marked {@code @Primary} so it overrides the production {@code StubEmailService} when the
 * {@code integration} profile is active.
 */
@Service
@Primary
@Profile("integration")
public class CaptureEmailService implements EmailService {

  private final AtomicReference<CapturedVerificationEmail> lastVerification =
      new AtomicReference<>();
  private final AtomicReference<String> lastDuplicateEmail = new AtomicReference<>();

  @Override
  public void sendVerificationEmail(
      final String toEmail, final String fullName, final String verificationLink) {
    // Extract raw token from link: <baseUrl>/verify-email?token=<rawToken>
    final String rawToken =
        verificationLink.contains("?token=")
            ? verificationLink.substring(verificationLink.lastIndexOf("?token=") + 7)
            : verificationLink;
    lastVerification.set(
        new CapturedVerificationEmail(toEmail, fullName, verificationLink, rawToken));
  }

  @Override
  public void sendPasswordResetEmail(
      final String toEmail, final String fullName, final String resetLink) {
    // not needed for current test suites — no-op
  }

  @Override
  public void sendDuplicateRegistrationNotification(final String toEmail, final String fullName) {
    lastDuplicateEmail.set(toEmail);
  }

  @Override
  public void sendWelcomeEmail(final String toEmail, final String fullName) {
    // no-op for tests
  }

  // ─── Accessors
  // ────────────────────────────────────────────────────────────────

  /** Returns the raw verification token from the last sendVerificationEmail() call. */
  public String getLastVerificationToken() {
    final CapturedVerificationEmail captured = lastVerification.get();
    if (captured == null) {
      throw new IllegalStateException(
          "No verification email has been captured yet. "
              + "Did the registration complete and the AFTER_COMMIT listener fire?");
    }
    return captured.rawToken();
  }

  /** Returns the email address of the last duplicate registration notification. */
  public String getLastDuplicateNotificationEmail() {
    return lastDuplicateEmail.get();
  }

  /** Resets captured state (called between tests if needed). */
  public void reset() {
    lastVerification.set(null);
    lastDuplicateEmail.set(null);
  }

  public record CapturedVerificationEmail(
      String toEmail, String fullName, String verificationLink, String rawToken) {}
}

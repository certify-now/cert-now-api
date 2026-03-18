package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
  private static final String LOGO_CONTENT_ID = "certifynow-logo";
  private static final String LOGO_PATH = "static/images/logo2.png";
  private static final int OTP_EXPIRY_MINUTES = 10;
  private static final DateTimeFormatter TIMESTAMP_FMT =
      DateTimeFormatter.ofPattern("HH:mm 'UTC,' dd MMM yyyy");

  private final JavaMailSender mailSender;
  private final String fromAddress;
  private final String replyToAddress;
  private final String supportEmail;

  public SmtpEmailService(
      final JavaMailSender mailSender,
      @Value("${app.email.from}") final String fromAddress,
      @Value("${app.email.reply-to:${app.email.from}}") final String replyToAddress,
      @Value("${app.email.support:${app.email.from}}") final String supportEmail) {
    Assert.hasText(fromAddress, "app.email.from must not be blank");
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    this.replyToAddress = replyToAddress;
    this.supportEmail = supportEmail;
  }

  @Async("emailTaskExecutor")
  @Override
  public void sendVerificationEmail(
      final String toEmail, final String fullName, final String verificationCode) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(fullName, "fullName must not be blank");
    Assert.hasText(verificationCode, "verificationCode must not be blank");

    final String subject = "Your CertifyNow verification code: " + verificationCode;
    final String requestedAt = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);

    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello %s,</p>
        <p style="margin:0 0 24px 0;">
          To complete your <strong>CertifyNow</strong> sign-up, enter the verification
          code below. It expires in <strong>%d minutes</strong>.
        </p>

        <!-- OTP Code Box -->
        <table role="presentation" cellspacing="0" cellpadding="0" width="100%%" style="margin:0 0 12px 0;">
          <tr>
            <td style="background:#F4F6F9;border:1px solid #E5E7EB;border-radius:8px;
                       padding:24px 40px;text-align:center;">
              <span style="font-size:36px;font-weight:700;letter-spacing:8px;
                           color:#1B6CA8;font-family:monospace;">%s</span>
            </td>
          </tr>
        </table>

        <p style="margin:0 0 28px 0;font-size:12px;color:#9CA3AF;text-align:center;">
          Never share this code with anyone. CertifyNow will never ask for it.
        </p>

        <p style="margin:0 0 20px 0;font-size:13px;color:#6B7280;">
          <strong>Requested at:</strong> %s
        </p>

        <p style="margin:0 0 24px 0;font-size:13px;color:#6B7280;">
          <strong>Didn't sign up?</strong> Your email address may have been entered by mistake.
          No action is needed — your account has not been created and this code cannot be used
          without access to your inbox.
        </p>
        <p style="margin:0;font-size:13px;color:#6B7280;">
          Need help? Reply to this email or contact us at
          <a href="mailto:%s" style="color:#1B6CA8;text-decoration:none;">%s</a>.
        </p>
        """
            .formatted(
                escapeHtml(fullName),
                OTP_EXPIRY_MINUTES,
                escapeHtml(verificationCode),
                escapeHtml(requestedAt),
                escapeHtml(supportEmail),
                escapeHtml(supportEmail));

    sendHtmlEmail(toEmail, subject, body);
  }

  @Async("emailTaskExecutor")
  @Override
  public void sendPasswordResetEmail(
      final String toEmail, final String fullName, final String resetLink) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(fullName, "fullName must not be blank");
    Assert.hasText(resetLink, "resetLink must not be blank");

    final String subject = "Reset your CertifyNow password";
    final String requestedAt = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);

    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello %s,</p>
        <p style="margin:0 0 24px 0;">
          We received a request to reset your <strong>CertifyNow</strong> password.
          Click the button below to choose a new one. This link expires in
          <strong>%d minutes</strong>.
        </p>
        <p style="margin:0 0 32px 0;">
          <a href="%s"
             style="display:inline-block;padding:12px 28px;background:#0D1B2A;
                    color:#fff;text-decoration:none;border-radius:6px;
                    font-weight:600;font-size:15px;">
            Reset Password
          </a>
        </p>
        <p style="margin:0 0 8px 0;font-size:13px;color:#6B7280;">
          If the button doesn't work, copy and paste this link into your browser:
        </p>
        <p style="margin:0 0 24px 0;font-size:13px;word-break:break-all;">
          <a href="%s" style="color:#1B6CA8;">%s</a>
        </p>
        <p style="margin:0 0 12px 0;font-size:13px;color:#6B7280;">
          <strong>Requested at:</strong> %s
        </p>
        <p style="margin:0;font-size:13px;color:#6B7280;">
          If you didn't request a password reset, you can safely ignore this email.
          Need help? Contact us at
          <a href="mailto:%s" style="color:#1B6CA8;text-decoration:none;">%s</a>.
        </p>
        """
            .formatted(
                escapeHtml(fullName),
                OTP_EXPIRY_MINUTES,
                resetLink,
                resetLink,
                escapeHtml(resetLink),
                escapeHtml(requestedAt),
                escapeHtml(supportEmail),
                escapeHtml(supportEmail));

    sendHtmlEmail(toEmail, subject, body);
  }

  @Async("emailTaskExecutor")
  @Override
  public void sendWelcomeEmail(final String toEmail, final String fullName) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(fullName, "fullName must not be blank");

    final String subject = "Welcome to CertifyNow — you're all set";
    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello %s,</p>
        <p style="margin:0 0 16px 0;">
          Welcome to <strong>CertifyNow</strong>! Your account is now active and you can
          start managing your property compliance straight away.
        </p>
        <p style="margin:0 0 24px 0;">
          If you have any questions, our team is always happy to help.
        </p>
        <p style="margin:0;font-size:13px;color:#6B7280;">
          Need help? Contact us at
          <a href="mailto:%s" style="color:#1B6CA8;text-decoration:none;">%s</a>.
        </p>
        """
            .formatted(escapeHtml(fullName), escapeHtml(supportEmail), escapeHtml(supportEmail));

    sendHtmlEmail(toEmail, subject, body);
  }

  @Async("emailTaskExecutor")
  @Override
  public void sendDuplicateRegistrationNotification(final String toEmail, final String ipAddress) {
    Assert.hasText(toEmail, "toEmail must not be blank");

    final String subject = "Security notice: sign-up attempt on your account";
    final String maskedIp = maskIp(ipAddress);
    final String attemptedAt = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);

    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello,</p>
        <p style="margin:0 0 16px 0;">
          Someone attempted to register a new CertifyNow account using your email address
          or phone number. No new account has been created.
        </p>
        <p style="margin:0 0 8px 0;font-size:13px;color:#6B7280;">
          <strong>Request origin:</strong> %s
        </p>
        <p style="margin:0 0 24px 0;font-size:13px;color:#6B7280;">
          <strong>Attempted at:</strong> %s
        </p>
        <p style="margin:0;font-size:13px;color:#6B7280;">
          If this was you, you can safely ignore this email.
          If you're concerned about your account security, please contact us immediately at
          <a href="mailto:%s" style="color:#1B6CA8;text-decoration:none;">%s</a>.
        </p>
        """
            .formatted(
                maskedIp,
                escapeHtml(attemptedAt),
                escapeHtml(supportEmail),
                escapeHtml(supportEmail));

    sendHtmlEmail(toEmail, subject, body);
  }

  @Override
  public void sendJobEscalationAlert(
      final String toEmail,
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final int totalPricePence) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(jobId, "jobId must not be blank");

    final String formattedPrice = String.format("£%.2f", totalPricePence / 100.0);
    final String subject = "⚠️ Job Escalated — " + referenceNumber + " requires admin attention";
    final String escalatedAt = ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);

    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello Admin,</p>
        <p style="margin:0 0 20px 0;">
          A job has been <strong style="color:#DC2626;">escalated</strong> because no eligible
          engineer was found or no engineer accepted within the broadcast window.
          Immediate attention is required.
        </p>

        <!-- Job details table -->
        <table role="presentation" cellspacing="0" cellpadding="0" width="100%%"
               style="margin:0 0 24px 0;border:1px solid #E5E7EB;border-radius:8px;overflow:hidden;">
          <tr style="background:#F9FAFB;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;width:40%%;">Job Reference</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;font-weight:700;">%s</td>
          </tr>
          <tr>
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Certificate Type</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr style="background:#F9FAFB;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Urgency</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr>
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Total Price</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr style="background:#F9FAFB;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;">Escalated At</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;">%s</td>
          </tr>
        </table>

        <p style="margin:0 0 20px 0;font-size:13px;color:#6B7280;">
          <strong>Job ID:</strong> <code style="font-size:12px;background:#F3F4F6;
          padding:2px 6px;border-radius:4px;">%s</code>
        </p>

        <p style="margin:0 0 8px 0;font-size:14px;font-weight:600;color:#DC2626;">
          Action required:
        </p>
        <ul style="margin:0 0 24px 0;padding-left:20px;font-size:14px;color:#374151;line-height:1.8;">
          <li>Review the job in the admin dashboard</li>
          <li>Manually assign an available engineer, or</li>
          <li>Contact the customer to rebook</li>
        </ul>

        <p style="margin:0;font-size:13px;color:#6B7280;">
          Need help? Contact the engineering team or reply to this email.
        </p>
        """
            .formatted(
                escapeHtml(referenceNumber),
                escapeHtml(certificateType),
                escapeHtml(urgency),
                escapeHtml(formattedPrice),
                escapeHtml(escalatedAt),
                escapeHtml(jobId));

    sendHtmlEmail(toEmail, subject, body);
  }

  @Override
  public void sendJobEscalationReminder(
      final String toEmail,
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final int totalPricePence,
      final int reminderCount,
      final long minutesEscalated) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(jobId, "jobId must not be blank");

    final String formattedPrice = String.format("£%.2f", totalPricePence / 100.0);
    final String formattedAge = formatEscalatedAge(minutesEscalated);
    final String subject =
        "🔴 Reminder #"
            + reminderCount
            + " — "
            + referenceNumber
            + " still needs attention ("
            + formattedAge
            + ")";

    final String body =
        """
        <p style="margin:0 0 16px 0;">Hello Admin,</p>
        <p style="margin:0 0 20px 0;">
          This is <strong>reminder #%d</strong>. The job below has been
          <strong style="color:#DC2626;">escalated for %s</strong> and has not yet been resolved.
          Please take action as soon as possible.
        </p>

        <!-- Job details table -->
        <table role="presentation" cellspacing="0" cellpadding="0" width="100%%"
               style="margin:0 0 24px 0;border:1px solid #E5E7EB;border-radius:8px;overflow:hidden;">
          <tr style="background:#FEF2F2;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;width:40%%;">Job Reference</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;font-weight:700;">%s</td>
          </tr>
          <tr>
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Certificate Type</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr style="background:#FEF2F2;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Urgency</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr>
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;
                       border-bottom:1px solid #E5E7EB;">Total Price</td>
            <td style="padding:10px 16px;font-size:13px;color:#1F2937;
                       border-bottom:1px solid #E5E7EB;">%s</td>
          </tr>
          <tr style="background:#FEF2F2;">
            <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#374151;">Time Escalated</td>
            <td style="padding:10px 16px;font-size:13px;color:#DC2626;font-weight:600;">%s</td>
          </tr>
        </table>

        <p style="margin:0 0 20px 0;font-size:13px;color:#6B7280;">
          <strong>Job ID:</strong> <code style="font-size:12px;background:#F3F4F6;
          padding:2px 6px;border-radius:4px;">%s</code>
        </p>

        <p style="margin:0 0 8px 0;font-size:14px;font-weight:600;color:#DC2626;">
          Immediate action required:
        </p>
        <ul style="margin:0 0 24px 0;padding-left:20px;font-size:14px;color:#374151;line-height:1.8;">
          <li>Review the job in the admin dashboard</li>
          <li>Manually assign an available engineer, or</li>
          <li>Contact the customer to rebook</li>
        </ul>

        <p style="margin:0;font-size:13px;color:#6B7280;">
          You will continue to receive reminders until this job is resolved.
        </p>
        """
            .formatted(
                reminderCount,
                escapeHtml(formattedAge),
                escapeHtml(referenceNumber),
                escapeHtml(certificateType),
                escapeHtml(urgency),
                escapeHtml(formattedPrice),
                escapeHtml(formattedAge),
                escapeHtml(jobId));

    sendHtmlEmail(toEmail, subject, body);
  }

  private void sendHtmlEmail(final String toEmail, final String subject, final String htmlBody) {
    Assert.hasText(toEmail, "toEmail must not be blank");
    Assert.hasText(subject, "subject must not be blank");
    Assert.hasText(htmlBody, "htmlBody must not be blank");

    final String domain = extractDomain(toEmail);
    final long startMs = System.currentTimeMillis();

    log.debug("Attempting to send email [subject='{}'] to domain [{}]", subject, domain);

    try {
      final MimeMessage message = mailSender.createMimeMessage();
      final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(fromAddress);
      helper.setReplyTo(replyToAddress);
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(wrapInLayout(subject, htmlBody), true);

      final ClassPathResource logo = new ClassPathResource(LOGO_PATH);
      if (logo.exists()) {
        helper.addInline(LOGO_CONTENT_ID, logo);
      } else {
        log.warn(
            "Logo resource not found at path [{}] — email will be sent without logo. "
                + "Check LOGO_PATH constant and that the resource is on the classpath.",
            LOGO_PATH);
      }

      mailSender.send(message);

      log.info(
          "Email sent successfully to {} in {}ms", toEmail, System.currentTimeMillis() - startMs);

    } catch (MessagingException ex) {
      log.error(
          "MIME construction failed for email [subject='{}'] to domain [{}] — "
              + "check MimeMessageHelper configuration",
          subject,
          domain,
          ex);
      throw new EmailDeliveryException("Failed to build email: " + subject, ex);

    } catch (MailException ex) {
      log.error(
          "SMTP transport failure for email [subject='{}'] to domain [{}] after {}ms — "
              + "check Outlook SMTP config and connectivity",
          subject,
          domain,
          System.currentTimeMillis() - startMs,
          ex);
      throw new EmailDeliveryException("Failed to send email: " + subject, ex);
    }
  }

  /** Wraps the email body fragment in a minimal, consistent HTML layout with inline logo. */
  private String wrapInLayout(final String title, final String bodyContent) {
    return """
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>%s</title>
          <!-- Preheader: hidden inbox preview text -->
          <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
            CertifyNow — Property Compliance. Simplified. This email was sent because an action was taken on your account.
          </div>
        </head>
        <body style="margin:0;padding:0;background-color:#F4F6F9;
                     font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;
                     color:#1F2937;">

          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                 style="padding:40px 0;background-color:#F4F6F9;">
            <tr>
              <td align="center">

                <table role="presentation" width="600" cellspacing="0" cellpadding="0"
                       style="background:#ffffff;border-radius:12px;overflow:hidden;
                              box-shadow:0 4px 20px rgba(0,0,0,0.05);">

                  <!-- Brand accent bar -->
                  <tr>
                    <td style="height:6px;background:#0D1B2A;"></td>
                  </tr>

                  <!-- Logo -->
                  <tr>
                    <td style="padding:32px 24px 16px 24px;text-align:center;">
                      <img src="cid:%s"
                           alt="CertifyNow"
                           style="height:120px;width:auto;display:block;margin:0 auto;" />
                    </td>
                  </tr>

                  <!-- Body -->
                  <tr>
                    <td style="padding:24px 40px 32px 40px;font-size:15px;
                               line-height:1.7;color:#1F2937;">
                      %s
                    </td>
                  </tr>

                  <!-- Divider -->
                  <tr>
                    <td style="padding:0 40px;">
                      <hr style="border:none;border-top:1px solid #E5E7EB;margin:0;" />
                    </td>
                  </tr>

                  <!-- Footer -->
                  <tr>
                    <td style="padding:24px 40px;text-align:center;">
                      <p style="font-size:12px;color:#9CA3AF;margin:0 0 6px 0;">
                        © 2026 CertifyNow Ltd
                      </p>
                      <p style="font-size:12px;color:#9CA3AF;margin:0 0 6px 0;">
                        Property Compliance. Simplified.
                      </p>
                      <p style="font-size:12px;color:#9CA3AF;margin:0;">
                        You are receiving this email because an action was taken on your
                        CertifyNow account. Please do not reply directly to this email —
                        contact us at
                        <a href="mailto:%s"
                           style="color:#9CA3AF;text-decoration:underline;">%s</a>.
                      </p>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>

        </body>
      </html>
      """
        .formatted(
            escapeHtml(title),
            LOGO_CONTENT_ID,
            bodyContent,
            escapeHtml(supportEmail),
            escapeHtml(supportEmail));
  }

  /** Extracts the domain portion of an email address for safe logging. */
  private String extractDomain(final String email) {
    if (email == null || !email.contains("@")) return "unknown";
    return email.substring(email.indexOf('@') + 1);
  }

  /** Masks all but the first octet of an IPv4 address. e.g. "192.168.1.100" → "192.x.x.x" */
  private String maskIp(final String ip) {
    if (ip == null || ip.isBlank()) return "unknown";
    final String[] parts = ip.split("\\.", 2);
    return parts.length > 0 ? parts[0] + ".x.x.x" : "unknown";
  }

  /** Minimal HTML escaping to prevent XSS when embedding user-supplied values in HTML emails. */
  private String escapeHtml(final String input) {
    if (input == null) return "";
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;");
  }

  /** Converts minutes into a human-readable string, e.g. "45 minutes" or "2 hours 30 minutes". */
  private String formatEscalatedAge(final long minutes) {
    if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s");
    final long hours = minutes / 60;
    final long remainder = minutes % 60;
    final String hourPart = hours + " hour" + (hours == 1 ? "" : "s");
    return remainder == 0 ? hourPart : hourPart + " " + remainder + " min";
  }
}

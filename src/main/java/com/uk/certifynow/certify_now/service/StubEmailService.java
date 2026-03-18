package com.uk.certifynow.certify_now.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub email service implementation for development.
 *
 * <p>Logs email content instead of actually sending emails. Replace with SendGridEmailService for
 * production.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "stub", matchIfMissing = true)
public class StubEmailService implements EmailService {

  private static final Logger logger = LoggerFactory.getLogger(StubEmailService.class);

  @Override
  public void sendVerificationEmail(
      final String toEmail, final String fullName, final String verificationCode) {
    logger.info(
        "📧 [STUB] Sending verification email to: {}\n" + "   Name: {}\n" + "   Code: {}",
        toEmail,
        fullName,
        verificationCode);
  }

  @Override
  public void sendPasswordResetEmail(
      final String toEmail, final String fullName, final String resetLink) {
    logger.info(
        "📧 [STUB] Sending password reset email to: {}\n" + "   Name: {}\n" + "   Link: {}",
        toEmail,
        fullName,
        resetLink);
  }

  @Override
  public void sendWelcomeEmail(final String toEmail, final String fullName) {
    logger.info("📧 [STUB] Sending welcome email to: {}\n" + "   Name: {}", toEmail, fullName);
  }

  @Override
  public void sendDuplicateRegistrationNotification(final String toEmail, final String ipAddress) {
    logger.warn(
        "📧 [STUB] Sending duplicate-registration security notice to: {}\n   From IP: {}",
        toEmail,
        ipAddress);
  }

  @Override
  public void sendJobEscalationAlert(
      final String toEmail,
      final String jobId,
      final String referenceNumber,
      final String certificateType,
      final String urgency,
      final int totalPricePence) {
    logger.warn(
        "📧 [STUB] Job escalation alert → to={}, ref={}, type={}, urgency={}, price={}p",
        toEmail,
        referenceNumber,
        certificateType,
        urgency,
        totalPricePence);
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
    logger.warn(
        "📧 [STUB] Job escalation reminder #{} → to={}, ref={}, escalated for {}min",
        reminderCount,
        toEmail,
        referenceNumber,
        minutesEscalated);
  }
}

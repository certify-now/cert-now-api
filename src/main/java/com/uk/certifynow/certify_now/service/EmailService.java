package com.uk.certifynow.certify_now.service;

/**
 * Email service interface for sending transactional emails.
 *
 * <p>Implementation will use SendGrid or similar email provider. This interface allows for easy
 * testing and swapping of email providers.
 */
public interface EmailService {

  /**
   * Send email verification code to user.
   *
   * @param toEmail recipient email address
   * @param fullName recipient's full name
   * @param verificationCode verification code
   */
  void sendVerificationEmail(String toEmail, String fullName, String verificationCode);

  /**
   * Send password reset link to user.
   *
   * @param toEmail recipient email address
   * @param fullName recipient's full name
   * @param resetLink password reset URL
   */
  void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);

  /**
   * Send welcome email after successful email verification.
   *
   * @param toEmail recipient email address
   * @param fullName recipient's full name
   */
  void sendWelcomeEmail(String toEmail, String fullName);

  /**
   * Send a security notification when someone tries to register with an already-registered email or
   * phone. Registration silently succeeds (no 409) to prevent email enumeration — this notification
   * lets the real account holder know a registration attempt occurred.
   *
   * @param toEmail the existing user's email address
   * @param ipAddress the IP address of the duplicate attempt (for the recipient's awareness)
   */
  void sendDuplicateRegistrationNotification(String toEmail, String ipAddress);

  /**
   * Send an escalation alert to an admin when a job cannot be matched to any engineer and requires
   * manual intervention.
   *
   * @param toEmail admin email address
   * @param jobId UUID of the escalated job
   * @param referenceNumber human-readable job reference (e.g. CN-0042)
   * @param certificateType type of certificate required (e.g. GAS_SAFETY)
   * @param urgency urgency level (e.g. STANDARD, URGENT)
   * @param totalPricePence total job price in pence
   */
  void sendJobEscalationAlert(
      String toEmail,
      String jobId,
      String referenceNumber,
      String certificateType,
      String urgency,
      int totalPricePence);

  /**
   * Send a follow-up reminder to an admin for a job that has remained escalated without being
   * resolved.
   *
   * @param toEmail admin email address
   * @param jobId UUID of the escalated job
   * @param referenceNumber human-readable job reference (e.g. CN-0042)
   * @param certificateType type of certificate required (e.g. GAS_SAFETY)
   * @param urgency urgency level (e.g. STANDARD, URGENT)
   * @param totalPricePence total job price in pence
   * @param reminderCount how many admin alerts have been sent so far (2 = first reminder)
   * @param minutesEscalated how long the job has been in ESCALATED status
   */
  void sendJobEscalationReminder(
      String toEmail,
      String jobId,
      String referenceNumber,
      String certificateType,
      String urgency,
      int totalPricePence,
      int reminderCount,
      long minutesEscalated);
}

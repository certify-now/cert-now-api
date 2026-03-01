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
   * phone (Fix 3: prevents email enumeration by silently handling duplicates).
   *
   * @param toEmail the existing user's email address
   * @param ipAddress the IP address of the duplicate attempt (for the recipient's awareness)
   */
  void sendDuplicateRegistrationNotification(String toEmail, String ipAddress);
}

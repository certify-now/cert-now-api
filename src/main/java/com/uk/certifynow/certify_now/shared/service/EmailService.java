package com.uk.certifynow.certify_now.shared.service;

/**
 * Email service interface for sending transactional emails.
 *
 * <p>
 * Implementation will use SendGrid or similar email provider.
 * This interface allows for easy testing and swapping of email providers.
 */
public interface EmailService {

    /**
     * Send email verification link to user.
     *
     * @param toEmail          recipient email address
     * @param fullName         recipient's full name
     * @param verificationLink verification URL
     */
    void sendVerificationEmail(String toEmail, String fullName, String verificationLink);

    /**
     * Send password reset link to user.
     *
     * @param toEmail   recipient email address
     * @param fullName  recipient's full name
     * @param resetLink password reset URL
     */
    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);

    /**
     * Send welcome email after successful email verification.
     *
     * @param toEmail  recipient email address
     * @param fullName recipient's full name
     */
    void sendWelcomeEmail(String toEmail, String fullName);
}

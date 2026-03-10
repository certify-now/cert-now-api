package com.uk.certifynow.certify_now.service.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request to resend email verification code. */
public record ResendVerificationRequest(
    @NotBlank(message = "email is required") @Email(message = "must be a valid email address")
        String email) {}

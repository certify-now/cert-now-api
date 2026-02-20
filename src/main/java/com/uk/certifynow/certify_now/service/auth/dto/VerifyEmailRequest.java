package com.uk.certifynow.certify_now.service.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to verify email address.
 */
public record VerifyEmailRequest(@NotBlank(message = "token is required") String token) {
}

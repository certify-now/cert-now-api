package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequest(
    @NotBlank(message = "Email is required") @Email(message = "Must be a valid email address")
        String email) {}

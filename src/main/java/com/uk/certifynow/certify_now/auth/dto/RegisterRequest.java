package com.uk.certifynow.certify_now.auth.dto;

import com.uk.certifynow.certify_now.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "email is required") @Email(message = "must be a valid email address")
        String email,
    @NotBlank(message = "password is required")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$",
            message =
                "must be at least 8 characters with uppercase, lowercase, number and special character")
        String password,
    @NotBlank(message = "full_name is required")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String fullName,
    @Pattern(regexp = "^\\+44\\d{10}$", message = "must be a valid UK phone number") String phone,
    @NotNull(message = "role is required") UserRole role) {}

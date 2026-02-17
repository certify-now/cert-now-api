package com.uk.certifynow.certify_now.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank(message = "refresh_token is required") String refreshToken) {}

package com.uk.certifynow.certify_now.auth.dto;

import com.uk.certifynow.certify_now.auth.api.response.ProfileView;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, UserSummary user) {
  public record UserSummary(
      UUID id,
      String email,
      String fullName,
      String phone,
      String role,
      String status,
      Boolean emailVerified,
      Boolean phoneVerified,
      String avatarUrl,
      OffsetDateTime createdAt,
      OffsetDateTime lastLoginAt,
      ProfileView profile) {}
}

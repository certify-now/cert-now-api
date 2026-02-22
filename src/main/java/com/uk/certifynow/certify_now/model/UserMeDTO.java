package com.uk.certifynow.certify_now.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserMeDTO {

  private UUID id;

  private String email;

  private String fullName;

  private String phone;

  private String avatarUrl;

  private String role;

  private String status;

  private Boolean emailVerified;

  private Boolean phoneVerified;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  private OffsetDateTime lastLoginAt;
}

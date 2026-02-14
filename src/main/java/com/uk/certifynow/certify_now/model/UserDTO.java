package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UserDTO {

    private UUID id;

    @NotNull
    private Boolean emailVerified;

    @NotNull
    private Boolean phoneVerified;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime lastLoginAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @Size(max = 20)
    private String phone;

    @NotNull
    @Size(max = 50)
    private String authProvider;

    @Size(max = 512)
    private String avatarUrl;

    @NotNull
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String externalAuthId;

    @NotNull
    @Size(max = 255)
    private String fullName;

    @NotNull
    @Size(max = 255)
    private String passwordHash;

    @NotNull
    @Size(max = 255)
    private String role;

    @NotNull
    @Size(max = 255)
    private String status;

}

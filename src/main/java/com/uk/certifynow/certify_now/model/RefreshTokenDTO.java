package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class RefreshTokenDTO {

    private UUID id;

    @NotNull
    private Boolean revoked;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    @Size(max = 255)
    private String deviceInfo;

    @Size(max = 255)
    private String ipAddress;

    @NotNull
    @Size(max = 255)
    private String tokenHash;

    @NotNull
    private UUID user;

}

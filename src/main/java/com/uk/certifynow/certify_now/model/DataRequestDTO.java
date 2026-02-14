package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class DataRequestDTO {

    private UUID id;

    private OffsetDateTime completedAt;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime downloadExpiresAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    @Size(max = 20)
    private String requestType;

    @NotNull
    @Size(max = 20)
    private String status;

    @Size(max = 512)
    private String downloadUrl;

    private String notes;

    @NotNull
    private UUID user;

}

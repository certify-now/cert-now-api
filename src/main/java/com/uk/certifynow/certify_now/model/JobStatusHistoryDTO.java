package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class JobStatusHistoryDTO {

    private UUID id;

    @NotNull
    private OffsetDateTime createdAt;

    private UUID actorId;

    @NotNull
    @Size(max = 20)
    private String actorType;

    @Size(max = 255)
    private String fromStatus;

    private String reason;

    @NotNull
    @Size(max = 255)
    private String toStatus;

    private String metadata;

    @NotNull
    private UUID job;

}

package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PayoutDTO {

    private UUID id;

    @NotNull
    private Integer amountPence;

    @NotNull
    private Integer commissionPence;

    @NotNull
    @Size(max = 3)
    private String currency;

    private Integer instantFeePence;

    @NotNull
    @JsonProperty("isInstant")
    private Boolean isInstant;

    @NotNull
    private Integer netPence;

    private LocalDate scheduledFor;

    private OffsetDateTime completedAt;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    private String failureReason;

    @NotNull
    @Size(max = 255)
    private String status;

    @Size(max = 255)
    private String stripePayoutId;

    @Size(max = 255)
    private String stripeTransferId;

    @NotNull
    private UUID engineer;

    private UUID job;

    private UUID payment;

}

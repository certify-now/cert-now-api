package com.uk.certifynow.certify_now.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class RenewalReminderDTO {

    private UUID id;

    @NotNull
    private Integer daysBefore;

    @NotNull
    private LocalDate expiryDate;

    @NotNull
    private LocalDate scheduledFor;

    @NotNull
    private Boolean sent;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime sentAt;

    @NotNull
    @Size(max = 255)
    private String certificateType;

    @NotNull
    private UUID certificate;

    @NotNull
    private UUID customer;

    private UUID notification;

    @NotNull
    private UUID property;

}

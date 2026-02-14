package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class EngineerAvailabilityDTO {

    private UUID id;

    @NotNull
    private Integer dayOfWeek;

    @NotNull
    @Schema(type = "string", example = "18:30")
    private LocalTime endTime;

    @NotNull
    @JsonProperty("isAvailable")
    private Boolean isAvailable;

    @NotNull
    @JsonProperty("isRecurring")
    private Boolean isRecurring;

    private LocalDate overrideDate;

    @NotNull
    @Schema(type = "string", example = "18:30")
    private LocalTime startTime;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    private UUID engineerProfile;

}

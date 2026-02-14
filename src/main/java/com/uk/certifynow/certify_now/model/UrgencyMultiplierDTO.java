package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UrgencyMultiplierDTO {

    private UUID id;

    @NotNull
    private LocalDate effectiveFrom;

    @NotNull
    @JsonProperty("isActive")
    private Boolean isActive;

    @NotNull
    @Digits(integer = 4, fraction = 3)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "5.008")
    private BigDecimal multiplier;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    @Size(max = 255)
    private String urgency;

}

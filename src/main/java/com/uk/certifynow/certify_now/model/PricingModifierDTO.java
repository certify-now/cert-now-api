package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PricingModifierDTO {

    private UUID id;

    @Digits(integer = 10, fraction = 2)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "15.08")
    private BigDecimal conditionMax;

    @Digits(integer = 10, fraction = 2)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", example = "77.08")
    private BigDecimal conditionMin;

    @NotNull
    private Integer modifierPence;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    @Size(max = 50)
    private String modifierType;

    @NotNull
    private UUID pricingRule;

}

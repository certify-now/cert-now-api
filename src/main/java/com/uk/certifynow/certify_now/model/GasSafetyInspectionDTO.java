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
public class GasSafetyInspectionDTO {

    private UUID id;

    @NotNull
    private LocalDate inspectionDate;

    @NotNull
    private LocalDate nextInspectionDate;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    @Size(max = 20)
    private String inspectorGasSafeId;

    @Size(max = 255)
    private String defectSeverity;

    private String defectsText;

    private String landlordAddress;

    @Size(max = 255)
    private String landlordName;

    @NotNull
    @Size(max = 255)
    private String overallResult;

    @NotNull
    private UUID job;

}

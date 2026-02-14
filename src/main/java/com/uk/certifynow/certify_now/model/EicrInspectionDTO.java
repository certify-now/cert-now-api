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
public class EicrInspectionDTO {

    private UUID id;

    @NotNull
    private Integer c1Count;

    @NotNull
    private Integer c2Count;

    @NotNull
    private Integer c3Count;

    private Integer consumerUnitAgeYears;

    @NotNull
    private Integer fiCount;

    @NotNull
    private LocalDate inspectionDate;

    private Integer installationYear;

    @NotNull
    private LocalDate nextInspectionDate;

    private Integer numberOfCircuits;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @Size(max = 50)
    private String earthingArrangement;

    @NotNull
    @Size(max = 50)
    private String inspectorAccreditation;

    @Size(max = 100)
    private String consumerUnitType;

    @Size(max = 100)
    private String schemeName;

    @NotNull
    @Size(max = 255)
    private String overallResult;

    private String observationsDetail;

    @NotNull
    private UUID job;

}

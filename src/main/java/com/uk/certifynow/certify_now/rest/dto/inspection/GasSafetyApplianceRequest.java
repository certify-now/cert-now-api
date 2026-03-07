package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GasSafetyApplianceRequest(
    @NotNull Integer index,
    @NotBlank String location,
    @NotBlank @Size(max = 100) String applianceType,
    @Size(max = 100) String make,
    @Size(max = 100) String model,
    @Size(max = 100) String serialNumber,
    @NotNull Boolean landlordsAppliance,
    @NotBlank @Size(max = 50) String inspectionType,
    @NotNull Boolean applianceInspected,
    Boolean applianceServiced,
    @NotNull Boolean applianceSafeToUse,
    @Size(max = 10) String classificationCode,
    String classificationDescription,
    @Size(max = 50) String flueType,
    @NotNull Boolean ventilationProvisionSatisfactory,
    @NotNull Boolean flueVisualConditionTerminationSatisfactory,
    @NotBlank String fluePerformanceTests,
    @NotBlank String spillageTest,
    BigDecimal operatingPressureMbar,
    BigDecimal burnerPressureMbar,
    String gasRate,
    BigDecimal heatInputKw,
    @NotNull Boolean safetyDevicesCorrectOperation,
    @NotNull Boolean emergencyControlAccessible,
    @NotNull Boolean gasInstallationPipeworkVisualInspectionSatisfactory,
    @NotNull Boolean gasTightnessSatisfactory,
    @NotNull Boolean equipotentialBonding,
    Boolean warningNoticeFixed,
    String additionalNotes,
    @Valid CombustionReadingsRequest combustionReadings) {}

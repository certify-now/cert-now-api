package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotBlank;

public record FinalChecksRequest(
    @NotBlank String gasTightnessPass,
    @NotBlank String gasPipeWorkVisualPass,
    @NotBlank String emergencyControlAccessible,
    @NotBlank String equipotentialBonding,
    @NotBlank String installationPass,
    @NotBlank String coAlarmFittedWorkingSameRoom,
    String smokeAlarmFittedWorking,
    String additionalObservations) {}

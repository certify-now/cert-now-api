package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record EpcBookingDetailsRequest(
        @NotNull @JsonProperty("appointment_date") LocalDate appointmentDate,
        @NotNull @JsonProperty("appointment_time") LocalTime appointmentTime,
        @JsonProperty("notes_for_assessor") String notesForAssessor) {
}

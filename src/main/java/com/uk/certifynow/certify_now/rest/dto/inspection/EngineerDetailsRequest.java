package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EngineerDetailsRequest(
    @NotBlank String name,
    @NotBlank @Size(max = 20) String gasSafeRegistrationNumber,
    @Size(max = 20) String engineerLicenceCardNumber,
    String timeOfArrival,
    String timeOfDeparture,
    @NotNull LocalDate reportIssuedDate,
    String engineerNotes) {}

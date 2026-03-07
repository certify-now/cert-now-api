package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CertificateDetailsRequest(
    @NotBlank @Size(max = 100) String certificateNumber,
    @Size(max = 100) String certificateReference,
    @Size(max = 50) String certificateType,
    @NotNull LocalDate issueDate,
    @NotNull LocalDate nextInspectionDueOnOrBefore,
    @NotNull Integer numberOfAppliancesTested,
    @Size(max = 512) String qrCodeUrl,
    @Size(max = 512) String verificationUrl) {}

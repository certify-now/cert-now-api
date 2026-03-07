package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SignaturesRequest(
    @NotNull Boolean engineerSigned,
    @NotNull LocalDate engineerSignedDate,
    String customerName,
    Boolean customerSigned,
    LocalDate customerSignedDate,
    Boolean tenantSigned,
    LocalDate tenantSignedDate,
    Boolean privacyPolicyAccepted) {}

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
public class EngineerInsuranceDTO {

    private UUID id;

    @NotNull
    private LocalDate expiryDate;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private Long coverAmountPence;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    @Size(max = 50)
    private String policyType;

    @Size(max = 100)
    private String policyNumber;

    @Size(max = 512)
    private String documentUrl;

    @Size(max = 255)
    private String provider;

    @NotNull
    @Size(max = 255)
    private String verificationStatus;

    @NotNull
    private UUID engineerProfile;

}

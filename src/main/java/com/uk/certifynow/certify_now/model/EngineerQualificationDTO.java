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
public class EngineerQualificationDTO {

    private UUID id;

    @NotNull
    private LocalDate expiryDate;

    @NotNull
    private Boolean externalVerified;

    private LocalDate issueDate;

    @NotNull
    private OffsetDateTime createdAt;

    private OffsetDateTime lastApiCheckAt;

    @NotNull
    private OffsetDateTime updatedAt;

    private OffsetDateTime verifiedAt;

    private UUID verifiedBy;

    @NotNull
    @Size(max = 100)
    private String registrationNumber;

    @Size(max = 512)
    private String documentUrl;

    @Size(max = 255)
    private String schemeName;

    @NotNull
    @Size(max = 255)
    private String type;

    @NotNull
    @Size(max = 255)
    private String verificationStatus;

    private String metadata;

    @NotNull
    private UUID engineerProfile;

}

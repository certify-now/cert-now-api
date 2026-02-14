package com.uk.certifynow.certify_now.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class FeatureFlagDTO {

    private UUID id;

    @NotNull
    @JsonProperty("isEnabled")
    private Boolean isEnabled;

    @NotNull
    private Integer rolloutPct;

    @NotNull
    private OffsetDateTime createdAt;

    @NotNull
    private OffsetDateTime updatedAt;

    @NotNull
    @Size(max = 100)
    private String flagKey;

    private String description;

    private String metadata;

}

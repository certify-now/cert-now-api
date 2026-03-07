package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record MetadataRequest(
    @Size(max = 100) String createdBySoftware,
    @Size(max = 20) String version,
    OffsetDateTime generatedAt,
    @Size(max = 50) String platform) {}

package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.time.LocalDate;
import java.util.UUID;

public record CertificateListItemResponse(
    UUID id,
    String certificateNumber,
    String certificateType,
    PropertySummaryResponse property,
    String status,
    String result,
    LocalDate issuedAt,
    LocalDate expiresAt,
    Long daysUntilExpiry,
    String documentUrl,
    String shareToken,
    String shareUrl,
    boolean canDownload,
    boolean canShare,
    boolean canRenew,
    EngineerSummaryResponse issuedBy) {}

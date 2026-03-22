package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code POST /api/v1/certificates/upload}.
 *
 * <p>Received as individual {@code multipart/form-data} fields so that the optional file attachment
 * can be streamed alongside the metadata in a single request.
 */
public record UploadCertificateRequest(
    UUID propertyId,
    String certType,
    LocalDate issuedAt,
    LocalDate expiresAt,
    String notes,
    String customTypeName) {}

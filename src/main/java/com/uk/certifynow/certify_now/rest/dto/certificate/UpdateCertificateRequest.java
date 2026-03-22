package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.time.LocalDate;

public record UpdateCertificateRequest(
    LocalDate issuedAt,
    LocalDate expiresAt,
    /** Combined "Provider: X\nNotes text" string, same format as upload. Null = no change. */
    String notes,
    /** Only meaningful for CUSTOM type certificates. */
    String customTypeName) {}

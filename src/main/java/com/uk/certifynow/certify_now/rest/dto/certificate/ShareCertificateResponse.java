package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.time.OffsetDateTime;

public record ShareCertificateResponse(String shareUrl, OffsetDateTime createdAt) {}

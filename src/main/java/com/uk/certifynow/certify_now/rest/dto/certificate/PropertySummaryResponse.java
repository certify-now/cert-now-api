package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.UUID;

public record PropertySummaryResponse(
    UUID id, String addressLine1, String addressLine2, String city, String postcode) {}

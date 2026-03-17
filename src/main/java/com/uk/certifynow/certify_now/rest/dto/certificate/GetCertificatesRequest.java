package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.UUID;

public record GetCertificatesRequest(String type, String status, UUID propertyId, String sort) {}

package com.uk.certifynow.certify_now.rest.dto.certificate;

public record CertificateTypeResponse(
    String type, String name, String description, boolean expiryRequired) {}

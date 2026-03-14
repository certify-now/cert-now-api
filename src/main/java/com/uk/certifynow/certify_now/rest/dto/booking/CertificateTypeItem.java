package com.uk.certifynow.certify_now.rest.dto.booking;

public record CertificateTypeItem(
    String type,
    String name,
    String description,
    int priceFromPence,
    int priceToPence,
    String priceUnit) {}

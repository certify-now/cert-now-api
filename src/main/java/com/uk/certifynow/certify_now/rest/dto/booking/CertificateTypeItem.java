package com.uk.certifynow.certify_now.rest.dto.booking;

public record CertificateTypeItem(
    String type,
    String name,
    int fromPricePence,
    String priceUnit,
    int overdueCount,
    int expiringSoonCount) {}

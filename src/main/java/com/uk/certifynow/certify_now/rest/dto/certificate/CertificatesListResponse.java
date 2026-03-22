package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.List;
import java.util.Map;

public record CertificatesListResponse(List<CertificateListItemResponse> data, Meta meta) {

  public record Meta(int totalCertificates, Breakdown breakdown, Map<String, Integer> byType) {}

  public record Breakdown(int valid, int expired, int expiringSoon, int missing, int superseded) {}
}

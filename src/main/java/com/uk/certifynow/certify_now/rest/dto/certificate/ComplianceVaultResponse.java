package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.List;
import java.util.UUID;

public record ComplianceVaultResponse(
    List<PropertyWithCertificates> properties, CertificatesListResponse.Meta meta) {

  public record PropertyWithCertificates(
      UUID id,
      String addressLine1,
      String city,
      String postcode,
      Boolean hasGasSupply,
      Boolean hasElectric,
      List<CertificateListItemResponse> certificates) {}
}

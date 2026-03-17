package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.UUID;

public record MissingCertificateResponse(
    PropertySummaryResponse property,
    String certificateType,
    String status,
    String reason,
    String urgency,
    ActionResponse action
) {

  public record ActionResponse(String text, String url) {}

  public static MissingCertificateResponse of(
      UUID propertyId,
      String addressLine1,
      String addressLine2,
      String city,
      String postcode,
      String certificateType,
      String reason,
      String urgency) {
    final String bookUrl =
        "/jobs/create?property_id=" + propertyId + "&type=" + certificateType;
    final String actionText = "Book " + friendlyTypeName(certificateType) + " Certificate";
    return new MissingCertificateResponse(
        new PropertySummaryResponse(propertyId, addressLine1, addressLine2, city, postcode),
        certificateType,
        "MISSING",
        reason,
        urgency,
        new ActionResponse(actionText, bookUrl));
  }

  private static String friendlyTypeName(final String type) {
    return switch (type) {
      case "GAS_SAFETY" -> "Gas Safety";
      case "EICR" -> "EICR";
      case "EPC" -> "EPC";
      case "PAT" -> "PAT";
      default -> type;
    };
  }
}

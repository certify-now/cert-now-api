package com.uk.certifynow.certify_now.rest.dto.certificate;

import java.util.UUID;

/**
 * Query filters for the customer certificate list endpoint.
 *
 * @param type          optional certificate type filter (GAS_SAFETY, EICR, EPC, PAT)
 * @param status        optional status filter applied after dynamic status calculation
 * @param propertyId    optional property UUID filter
 * @param sort          optional sort key: expiry_asc, expiry_desc, issued_desc
 * @param includeHistory when true, SUPERSEDED certificates are included so the customer can
 *                       see their full compliance history; defaults to false
 */
public record GetCertificatesRequest(
    String type,
    String status,
    UUID propertyId,
    String sort,
    boolean includeHistory) {}

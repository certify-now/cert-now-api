package com.uk.certifynow.certify_now.rest.dto.inspection;

public record FaultsAndRemedialsRequest(
    String faultsNotes,
    String remedialWorkTaken,
    Boolean warningNoticeFixed,
    Boolean applianceIsolated,
    String isolationReason) {}

package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EpcDocumentsRequest(
    @JsonProperty("previous_epc_pdf") String previousEpcPdf,
    @JsonProperty("fensa_certificate") String fensaCertificate,
    @JsonProperty("loft_insulation_certificate") String loftInsulationCertificate,
    @JsonProperty("boiler_installation_certificate") String boilerInstallationCertificate) {}

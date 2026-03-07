package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GasSafetyRecordRequest(
    @NotNull @Valid CertificateDetailsRequest certificate,
    @Valid CompanyDetailsRequest companyDetails,
    @NotNull @Valid EngineerDetailsRequest engineerDetails,
    @Valid ClientDetailsRequest clientDetails,
    @Valid TenantDetailsRequest tenantDetails,
    @Valid InstallationDetailsRequest installationDetails,
    @NotEmpty @Valid List<GasSafetyApplianceRequest> appliances,
    @Valid FinalChecksRequest finalChecks,
    @Valid FaultsAndRemedialsRequest faultsAndRemedials,
    @Valid SignaturesRequest signatures,
    @Valid MetadataRequest metadata) {}

package com.uk.certifynow.certify_now.rest.dto.inspection;

import jakarta.validation.constraints.Size;

public record TenantDetailsRequest(String name, String email, @Size(max = 20) String telephone) {}

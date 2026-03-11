package com.uk.certifynow.certify_now.rest.dto.property;

import com.uk.certifynow.certify_now.model.PropertyDTO;
import java.util.List;

/**
 * Wraps the property list and compliance health for the getMyProperties endpoint.
 *
 * @param properties list of property DTOs
 * @param complianceHealth aggregated compliance health score
 */
public record MyPropertiesResponse(
    List<PropertyDTO> properties, ComplianceHealth complianceHealth) {}

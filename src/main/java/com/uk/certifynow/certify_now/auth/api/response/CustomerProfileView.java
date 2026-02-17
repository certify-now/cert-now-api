package com.uk.certifynow.certify_now.auth.api.response;

import java.math.BigDecimal;

/**
 * Customer profile view for API responses.
 *
 * @param totalProperties number of properties owned
 * @param complianceScore compliance score (0-100)
 * @param isLettingAgent whether user is a letting agent
 * @param companyName company name if letting agent
 */
public record CustomerProfileView(
    Integer totalProperties, BigDecimal complianceScore, Boolean isLettingAgent, String companyName)
    implements ProfileView {}

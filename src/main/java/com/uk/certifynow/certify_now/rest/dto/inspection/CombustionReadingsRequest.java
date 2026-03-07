package com.uk.certifynow.certify_now.rest.dto.inspection;

import java.math.BigDecimal;

public record CombustionReadingsRequest(
    BigDecimal coPpm,
    BigDecimal co2Percentage,
    BigDecimal coToCo2Ratio,
    BigDecimal combustionLow,
    BigDecimal combustionHigh) {}

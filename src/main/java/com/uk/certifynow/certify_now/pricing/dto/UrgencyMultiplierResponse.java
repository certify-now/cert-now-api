package com.uk.certifynow.certify_now.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UrgencyMultiplierResponse(
    UUID id, String urgency, BigDecimal multiplier, boolean isActive, LocalDate effectiveFrom) {}

package com.uk.certifynow.certify_now.interfaces;

import com.uk.certifynow.certify_now.pricing.dto.PriceBreakdown;
import java.util.UUID;

public interface PricingCalculator {

  PriceBreakdown calculate(String certificateType, UUID propertyId, String urgency);
}

package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.interfaces.PricingCalculator;
import com.uk.certifynow.certify_now.rest.dto.pricing.PriceBreakdown;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PricingCalculatorImpl implements PricingCalculator {

  private final PricingService pricingService;
  private final PropertyRepository propertyRepository;

  public PricingCalculatorImpl(
      final PricingService pricingService, final PropertyRepository propertyRepository) {
    this.pricingService = pricingService;
    this.propertyRepository = propertyRepository;
  }

  @Override
  public PriceBreakdown calculate(
      final String certificateType, final UUID propertyId, final String urgency) {
    final Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));
    return pricingService.calculatePrice(certificateType, property, urgency);
  }
}

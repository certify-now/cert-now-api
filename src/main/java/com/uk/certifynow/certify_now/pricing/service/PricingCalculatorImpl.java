package com.uk.certifynow.certify_now.pricing.service;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.pricing.dto.PriceBreakdown;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.shared.exception.EntityNotFoundException;
import com.uk.certifynow.certify_now.shared.interfaces.PricingCalculator;
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
            .orElseThrow(
                () -> new EntityNotFoundException("Property not found: " + propertyId));
    return pricingService.calculatePrice(certificateType, property, urgency);
  }
}

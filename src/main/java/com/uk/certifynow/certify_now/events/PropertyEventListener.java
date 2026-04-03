package com.uk.certifynow.certify_now.events;

import com.uk.certifynow.certify_now.service.customer.CustomerProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PropertyEventListener {

  private final CustomerProfileService customerProfileService;

  public PropertyEventListener(CustomerProfileService customerProfileService) {
    this.customerProfileService = customerProfileService;
  }

  @EventListener
  public void onPropertyCreated(PropertyCreatedEvent event) {
    customerProfileService.incrementPropertyCount(event.getOwnerId());
  }
}

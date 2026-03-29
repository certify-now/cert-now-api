package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.enums.CertificateType;
import com.uk.certifynow.certify_now.model.PropertyDTO;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypeItem;
import com.uk.certifynow.certify_now.rest.dto.booking.CertificateTypesResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  private final PricingService pricingService;
  private final PropertyService propertyService;

  public BookingService(
      final PricingService pricingService, final PropertyService propertyService) {
    this.pricingService = pricingService;
    this.propertyService = propertyService;
  }

  @Transactional(readOnly = true)
  public CertificateTypesResponse getCertificateTypes() {
    return pricingService.getCertificateTypes();
  }

  @Transactional(readOnly = true)
  public CertificateTypesResponse getCertificateTypesForCustomer(final UUID customerId) {
    final CertificateTypesResponse catalogue = pricingService.getCertificateTypes();
    final List<PropertyDTO> properties =
        propertyService.getMyPropertiesWithCompliance(customerId).getProperties();
    final List<CertificateTypeItem> enriched =
        catalogue.certificateTypes().stream()
            .map(item -> enrichWithCounts(item, properties))
            .toList();
    return new CertificateTypesResponse(enriched);
  }

  private static CertificateTypeItem enrichWithCounts(
      final CertificateTypeItem item, final List<PropertyDTO> properties) {
    final int overdue;
    final int expiringSoon;

    try {
      switch (CertificateType.valueOf(item.type())) {
        case GAS_SAFETY -> {
          overdue =
              countStatus(properties, "gasStatus", ComplianceStatus.EXPIRED.name())
                  + countStatus(properties, "gasStatus", ComplianceStatus.MISSING.name());
          expiringSoon =
              countStatus(properties, "gasStatus", ComplianceStatus.EXPIRING_SOON.name());
        }
        case EICR -> {
          overdue =
              countStatus(properties, "eicrStatus", ComplianceStatus.EXPIRED.name())
                  + countStatus(properties, "eicrStatus", ComplianceStatus.MISSING.name());
          expiringSoon =
              countStatus(properties, "eicrStatus", ComplianceStatus.EXPIRING_SOON.name());
        }
        case EPC -> {
          overdue =
              countStatus(properties, "epcStatus", ComplianceStatus.EXPIRED.name())
                  + countStatus(properties, "epcStatus", ComplianceStatus.MISSING.name());
          expiringSoon =
              countStatus(properties, "epcStatus", ComplianceStatus.EXPIRING_SOON.name());
        }
        default -> {
          overdue = 0;
          expiringSoon = 0;
        }
      }
    } catch (final IllegalArgumentException e) {
      return item;
    }

    if (overdue == item.overdueCount() && expiringSoon == item.expiringSoonCount()) {
      return item;
    }
    return new CertificateTypeItem(
        item.type(), item.name(), item.fromPricePence(), item.priceUnit(), overdue, expiringSoon);
  }

  private static int countStatus(
      final List<PropertyDTO> properties, final String field, final String value) {
    return (int)
        properties.stream()
            .filter(
                p -> {
                  final String status =
                      switch (field) {
                        case "gasStatus" -> p.getGasStatus();
                        case "eicrStatus" -> p.getEicrStatus();
                        case "epcStatus" -> p.getEpcStatus();
                        default -> null;
                      };
                  return value.equals(status);
                })
            .count();
  }
}

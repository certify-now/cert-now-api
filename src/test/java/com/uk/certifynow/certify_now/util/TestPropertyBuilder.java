package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TestPropertyBuilder {

  private static final OffsetDateTime NOW = TestConstants.FIXED_NOW;

  private TestPropertyBuilder() {}

  public static Property buildWithGas(final User owner) {
    final Property p = base(owner);
    p.setHasGasSupply(true);
    p.setGasApplianceCount(1);
    p.setHasElectric(false);
    return p;
  }

  public static Property buildWithElectric(final User owner) {
    final Property p = base(owner);
    p.setHasGasSupply(false);
    p.setGasApplianceCount(0);
    p.setHasElectric(true);
    return p;
  }

  public static Property buildFull(final User owner) {
    final Property p = base(owner);
    p.setHasGasSupply(true);
    p.setGasApplianceCount(2);
    p.setHasElectric(true);
    p.setFloorAreaSqm(new BigDecimal("75.00"));
    p.setBedrooms(3);
    return p;
  }

  public static Property buildInactive(final User owner) {
    final Property p = buildWithGas(owner);
    p.setIsActive(false);
    return p;
  }

  private static Property base(final User owner) {
    final Property p = new Property();
    p.setId(UUID.randomUUID());
    p.setOwner(owner);
    p.setAddressLine1("10 Test Street");
    p.setCity("London");
    p.setPostcode("SW1A 1AA");
    p.setCountry("GB");
    p.setPropertyType("FLAT");
    p.setBedrooms(2);
    p.setIsActive(true);
    p.setComplianceStatus("UNKNOWN");
    p.setCreatedAt(NOW.minusDays(7));
    p.setUpdatedAt(NOW);
    return p;
  }
}

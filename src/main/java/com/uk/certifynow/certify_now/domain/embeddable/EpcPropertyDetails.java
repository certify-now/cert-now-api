package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class EpcPropertyDetails {

  @Column(name = "prop_address_line1")
  private String addressLine1;

  @Column(name = "prop_address_line2")
  private String addressLine2;

  @Column(name = "prop_address_line3")
  private String addressLine3;

  @Column(name = "prop_postcode", length = 10)
  private String postcode;

  @Column(name = "prop_property_type", length = 50)
  private String propertyType;

  @Column(name = "prop_number_of_bedrooms")
  private Integer numberOfBedrooms;

  @Column(name = "prop_year_built")
  private Integer yearBuilt;

  @Column(name = "prop_floor_level")
  private Integer floorLevel;

  @Column(name = "prop_access_notes", columnDefinition = "text")
  private String accessNotes;
}

package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class InstallationDetails {

  @Column(name = "installation_name_or_flat")
  private String nameOrFlat;

  @Column(name = "installation_address_line1")
  private String addressLine1;

  @Column(name = "installation_address_line2")
  private String addressLine2;

  @Column(name = "installation_address_line3")
  private String addressLine3;

  @Column(name = "installation_post_code", length = 10)
  private String postCode;

  @Column(name = "installation_telephone", length = 20)
  private String telephone;

  @Column(name = "installation_email")
  private String email;
}

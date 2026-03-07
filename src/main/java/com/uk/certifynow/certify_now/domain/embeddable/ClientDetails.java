package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class ClientDetails {

  @Column(name = "client_name")
  private String name;

  @Column(name = "client_address_line1")
  private String addressLine1;

  @Column(name = "client_address_line2")
  private String addressLine2;

  @Column(name = "client_address_line3")
  private String addressLine3;

  @Column(name = "client_post_code", length = 10)
  private String postCode;

  @Column(name = "client_telephone", length = 20)
  private String telephone;

  @Column(name = "client_email")
  private String email;
}

package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class TenantDetails {

  @Column(name = "tenant_name")
  private String name;

  @Column(name = "tenant_email")
  private String email;

  @Column(name = "tenant_telephone", length = 20)
  private String telephone;
}

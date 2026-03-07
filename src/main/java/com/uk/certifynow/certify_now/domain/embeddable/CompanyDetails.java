package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class CompanyDetails {

  @Column(name = "company_trading_title")
  private String tradingTitle;

  @Column(name = "company_address_line1")
  private String addressLine1;

  @Column(name = "company_address_line2")
  private String addressLine2;

  @Column(name = "company_address_line3")
  private String addressLine3;

  @Column(name = "company_post_code", length = 10)
  private String postCode;

  @Column(name = "company_gas_safe_registration_number", length = 20)
  private String gasSafeRegistrationNumber;

  @Column(name = "company_phone", length = 20)
  private String companyPhone;

  @Column(name = "company_email")
  private String companyEmail;
}

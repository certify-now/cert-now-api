package com.uk.certifynow.certify_now.domain.enums;

public enum CertificateType {
  GAS_SAFETY("Gas Safety"),
  EICR("EICR"),
  EPC("EPC"),
  PAT("PAT Testing"),
  FIRE_RISK_ASSESSMENT("Fire Risk Assessment"),
  BOILER_SERVICE("Boiler Service"),
  LEGIONELLA_RISK_ASSESSMENT("Legionella Assessment");

  private final String displayName;

  CertificateType(final String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}

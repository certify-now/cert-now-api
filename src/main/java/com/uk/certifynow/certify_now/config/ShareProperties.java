package com.uk.certifynow.certify_now.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "share")
public class ShareProperties {

  private String baseUrl = "http://localhost:8080";
  private int defaultExpiryDays = 30;
  private int maxExpiryDays = 90;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getDefaultExpiryDays() {
    return defaultExpiryDays;
  }

  public void setDefaultExpiryDays(final int defaultExpiryDays) {
    this.defaultExpiryDays = defaultExpiryDays;
  }

  public int getMaxExpiryDays() {
    return maxExpiryDays;
  }

  public void setMaxExpiryDays(final int maxExpiryDays) {
    this.maxExpiryDays = maxExpiryDays;
  }
}

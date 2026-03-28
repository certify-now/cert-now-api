package com.uk.certifynow.certify_now.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.pagination")
public class PaginationProperties {

  private int maxSize = 50;

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }
}

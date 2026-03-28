package com.uk.certifynow.certify_now.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.certificate-types")
public class CertificateTypeProperties {

  private List<CertificateTypeDefinition> uploadable = new ArrayList<>();

  public List<CertificateTypeDefinition> getUploadable() {
    return uploadable;
  }

  public void setUploadable(final List<CertificateTypeDefinition> uploadable) {
    this.uploadable = uploadable;
  }

  public static class CertificateTypeDefinition {

    private String type;
    private String name;
    private String description;
    private boolean expiryRequired;

    public String getType() {
      return type;
    }

    public void setType(final String type) {
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(final String description) {
      this.description = description;
    }

    public boolean isExpiryRequired() {
      return expiryRequired;
    }

    public void setExpiryRequired(final boolean expiryRequired) {
      this.expiryRequired = expiryRequired;
    }
  }
}

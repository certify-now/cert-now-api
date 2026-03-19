package com.uk.certifynow.certify_now.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

  /** Internal endpoint used by the Java SDK to talk to MinIO (e.g. http://minio:9000). */
  private String endpoint = "http://localhost:9000";

  /**
   * Public-facing endpoint used when constructing object URLs returned to clients. Defaults to
   * {@code endpoint} but can differ when MinIO sits behind a reverse proxy.
   */
  private String publicEndpoint;

  private String accessKey = "minioadmin";

  private String secretKey = "minioadmin";

  /** Bucket that all documents are stored in. Created on startup if it does not exist. */
  private String bucketName = "cert-now-docs";

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getPublicEndpoint() {
    return publicEndpoint != null ? publicEndpoint : endpoint;
  }

  public void setPublicEndpoint(final String publicEndpoint) {
    this.publicEndpoint = publicEndpoint;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(final String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(final String secretKey) {
    this.secretKey = secretKey;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }
}

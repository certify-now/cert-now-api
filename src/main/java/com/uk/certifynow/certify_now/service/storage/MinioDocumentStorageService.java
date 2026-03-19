package com.uk.certifynow.certify_now.service.storage;

import com.uk.certifynow.certify_now.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * MinIO-backed document storage. Active when {@code app.storage.provider=minio}.
 *
 * <p>Object key layout: {@code certificates/{certificateId}/{certificateType}.pdf}
 *
 * <p>On startup the configured bucket is created if it does not already exist and a public-read
 * policy is applied so the returned URLs are directly accessible by clients and mobile apps.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "minio")
public class MinioDocumentStorageService implements DocumentStorageService {

  private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorageService.class);

  private static final String PUBLIC_READ_POLICY =
      """
      {
        "Version": "2012-10-17",
        "Statement": [{
          "Effect": "Allow",
          "Principal": {"AWS": ["*"]},
          "Action": ["s3:GetObject"],
          "Resource": ["arn:aws:s3:::%s/*"]
        }]
      }
      """;

  private final MinioClient minioClient;
  private final MinioProperties properties;

  public MinioDocumentStorageService(
      final MinioClient minioClient, final MinioProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  @PostConstruct
  public void ensureBucketExists() {
    try {
      final boolean exists =
          minioClient.bucketExists(
              BucketExistsArgs.builder().bucket(properties.getBucketName()).build());
      if (!exists) {
        minioClient.makeBucket(
            MakeBucketArgs.builder().bucket(properties.getBucketName()).build());
        log.info("Created MinIO bucket: {}", properties.getBucketName());
      }
      minioClient.setBucketPolicy(
          SetBucketPolicyArgs.builder()
              .bucket(properties.getBucketName())
              .config(PUBLIC_READ_POLICY.formatted(properties.getBucketName()))
              .build());
      log.info(
          "MinIO bucket '{}' is ready (endpoint={})",
          properties.getBucketName(),
          properties.getEndpoint());
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialise MinIO bucket: " + e.getMessage(), e);
    }
  }

  @Override
  public String store(
      final UUID certificateId, final String certificateType, final byte[] content) {
    final String objectKey = buildObjectKey(certificateId, certificateType);
    try {
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(properties.getBucketName())
              .object(objectKey)
              .stream(new ByteArrayInputStream(content), content.length, -1)
              .contentType("application/pdf")
              .build());
      final String url = buildPublicUrl(objectKey);
      log.info(
          "Stored {} certificate PDF: certificateId={} url={}", certificateType, certificateId, url);
      return url;
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to store PDF in MinIO for certificateId=" + certificateId, e);
    }
  }

  @Override
  @Nullable
  public byte[] retrieveByUrl(final String storageUrl) {
    final String objectKey = extractObjectKey(storageUrl);
    try (InputStream stream =
        minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectKey)
                .build())) {
      return stream.readAllBytes();
    } catch (Exception e) {
      // MinIO throws ErrorResponseException with code "NoSuchKey" when the object doesn't exist.
      // We catch broadly here and return null so callers treat it as "not found".
      log.warn("Could not retrieve object from MinIO: url={} error={}", storageUrl, e.getMessage());
      return null;
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private static String buildObjectKey(final UUID certificateId, final String certificateType) {
    return "certificates/" + certificateId + "/" + certificateType + ".pdf";
  }

  private String buildPublicUrl(final String objectKey) {
    return properties.getPublicEndpoint().stripTrailing()
        + "/"
        + properties.getBucketName()
        + "/"
        + objectKey;
  }

  private String extractObjectKey(final String storageUrl) {
    final String prefix =
        properties.getPublicEndpoint().stripTrailing()
            + "/"
            + properties.getBucketName()
            + "/";
    if (!storageUrl.startsWith(prefix)) {
      throw new IllegalArgumentException(
          "storageUrl does not match configured MinIO endpoint/bucket: " + storageUrl);
    }
    return storageUrl.substring(prefix.length());
  }
}

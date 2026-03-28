package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "share_tokens",
    indexes = {
      @Index(name = "idx_share_tokens_token", columnList = "token"),
      @Index(name = "idx_share_tokens_certificate_id", columnList = "certificate_id"),
      @Index(name = "idx_share_tokens_expires_at", columnList = "expires_at")
    })
@Getter
@Setter
public class ShareToken {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false, unique = true, length = 64, updatable = false)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "certificate_id", nullable = false, updatable = false)
  private Certificate certificate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false, updatable = false)
  private User createdBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime expiresAt;

  @Column private OffsetDateTime accessedAt;

  @Column(nullable = false)
  private int accessCount = 0;

  public boolean isExpired(final java.time.Clock clock) {
    return OffsetDateTime.now(clock).isAfter(this.expiresAt);
  }

  /** Generates a cryptographically secure URL-safe Base64 token from 32 random bytes. */
  public static String generateToken() {
    final byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}

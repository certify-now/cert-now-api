package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Email verification token entity.
 *
 * <p>Stores hashed verification tokens sent to users via email. Tokens are single-use,
 * time-limited, and cryptographically secure.
 *
 * <p>Security features: - Tokens are stored as SHA-256 hashes (never in plaintext) - 24-hour expiry
 * window - Single-use enforcement (usedAt timestamp) - Automatic cleanup of expired tokens
 */
@Entity
@Table(
    name = "email_verification_tokens",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_email_verification_token_hash", columnNames = "token_hash"))
public class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash; // SHA-256 hash of raw token

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  // Constructors

  public EmailVerificationToken() {}

  // Business logic methods

  /**
   * Check if token has expired.
   *
   * @param clock injected clock for testability
   * @return true if token is past expiry time
   */
  public boolean isExpired(final Clock clock) {
    return expiresAt.isBefore(OffsetDateTime.now(clock));
  }

  /**
   * Check if token has been used.
   *
   * @return true if token has been used for verification
   */
  public boolean isUsed() {
    return usedAt != null;
  }

  /**
   * Mark token as used.
   *
   * @param clock injected clock for testability
   */
  public void markAsUsed(final Clock clock) {
    this.usedAt = OffsetDateTime.now(clock);
  }

  // Getters and setters

  public UUID getId() {
    return id;
  }

  public void setId(final UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(final User user) {
    this.user = user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(final String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(final OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(final OffsetDateTime usedAt) {
    this.usedAt = usedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}

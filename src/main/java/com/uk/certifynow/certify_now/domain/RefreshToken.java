package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    indexes = {
      @Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
      @Index(name = "idx_refresh_token_token_hash", columnList = "token_hash")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class RefreshToken {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private Boolean revoked;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime expiresAt;

  @Column private OffsetDateTime revokedAt;

  @Column private String deviceInfo;

  @Column private String ipAddress;

  @Column(nullable = false)
  private String tokenHash;

  /**
   * Token family ID — all tokens in the same login session share a familyId.
   *
   * <p>When a refresh token is rotated, the new token inherits the same familyId. If a revoked
   * token in a family is presented, ALL tokens in that family are immediately revoked to mitigate
   * token theft.
   *
   * <p>DB migration: ALTER TABLE refresh_token ADD COLUMN family_id UUID NOT NULL; For existing
   * rows: UPDATE refresh_token SET family_id = id;
   */
  @Column(nullable = false)
  private UUID familyId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

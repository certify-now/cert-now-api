package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class AuditLog {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private UUID actorId;

  @Column(nullable = false)
  private UUID entityId;

  @Column(nullable = false, length = 20)
  private String actorType;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(nullable = false, length = 50)
  private String entityType;

  @Column private String ipAddress;

  @Column(columnDefinition = "text")
  private String userAgent;

  @Column(columnDefinition = "text")
  private String newValues;

  @Column(columnDefinition = "text")
  private String oldValues;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

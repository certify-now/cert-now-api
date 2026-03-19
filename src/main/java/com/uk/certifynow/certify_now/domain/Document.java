package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
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
public class Document {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false, length = 1024)
  private String storageUrl;

  @Column(nullable = false)
  private String fileName;

  @Column(nullable = false, length = 100)
  private String mimeType;

  @Column(nullable = false)
  private Long fileSizeBytes;

  @Column(nullable = false)
  private Boolean isVirusScanned = false;

  @Column
  private Boolean virusScanClean;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by_id", nullable = false)
  private User uploadedBy;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "document")
  private Set<CertificateDocument> certificateDocuments = new HashSet<>();

  @OneToMany(mappedBy = "document")
  private Set<ComplianceDocumentFile> complianceDocumentFiles = new HashSet<>();
}

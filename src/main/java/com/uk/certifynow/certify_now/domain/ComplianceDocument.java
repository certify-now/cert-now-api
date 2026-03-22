package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
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
@Table(name = "compliance_document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ComplianceDocument {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @Column(nullable = false, length = 50)
  private String documentType;

  @Column(length = 100)
  private String customTypeName;

  @Column private LocalDate testDate;

  @Column private LocalDate expiryDate;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(length = 255)
  private String providerName;

  @Column(length = 100)
  private String providerReference;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by_id", nullable = false)
  private User uploadedBy;

  @Column(nullable = false)
  private Boolean reminderEnabled = false;

  @Column private Integer reminderDaysBefore;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "complianceDocument", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<ComplianceDocumentFile> files = new HashSet<>();

  public void addFile(Document document, int displayOrder) {
    ComplianceDocumentFile file = new ComplianceDocumentFile();
    file.setComplianceDocument(this);
    file.setDocument(document);
    file.setDisplayOrder(displayOrder);
    this.files.add(file);
  }
}

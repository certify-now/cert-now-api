package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "compliance_document_file")
@Getter
@Setter
public class ComplianceDocumentFile {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "compliance_document_id", nullable = false)
  private ComplianceDocument complianceDocument;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", nullable = false)
  private Document document;

  @Column(nullable = false)
  private Integer displayOrder = 0;
}

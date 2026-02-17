package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
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
public class EicrInspection {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private Integer c1Count;

  @Column(nullable = false)
  private Integer c2Count;

  @Column(nullable = false)
  private Integer c3Count;

  @Column private Integer consumerUnitAgeYears;

  @Column(nullable = false)
  private Integer fiCount;

  @Column(nullable = false)
  private LocalDate inspectionDate;

  @Column private Integer installationYear;

  @Column(nullable = false)
  private LocalDate nextInspectionDate;

  @Column private Integer numberOfCircuits;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(length = 50)
  private String earthingArrangement;

  @Column(nullable = false, length = 50)
  private String inspectorAccreditation;

  @Column(length = 100)
  private String consumerUnitType;

  @Column(length = 100)
  private String schemeName;

  @Column(nullable = false)
  private String overallResult;

  @Column(columnDefinition = "text")
  private String observationsDetail;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private Job job;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

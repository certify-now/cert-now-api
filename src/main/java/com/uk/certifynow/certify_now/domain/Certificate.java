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
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Certificate {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column private Integer epcScore;

  @Column private LocalDate expiryAt;

  @Column(nullable = false)
  private LocalDate issuedAt;

  @Column private Integer validYears;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private OffsetDateTime shareTokenCreated;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(length = 64)
  private String documentHash;

  @Column(length = 64)
  private String shareToken;

  @Column(length = 100)
  private String certificateNumber;

  @Column(length = 512)
  private String documentUrl;

  @Column(nullable = false)
  private String certificateType;

  @Column private String epcRating;

  @Column private String result;

  @Column(nullable = false)
  private String status;

  @Column(columnDefinition = "text")
  private String metadata;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issued_by_engineer_id")
  private User issuedByEngineer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id")
  private Job job;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "superseded_by_id")
  private Certificate supersededBy;

  @OneToMany(mappedBy = "supersededBy")
  private Set<Certificate> supersededByCertificates = new HashSet<>();

  @OneToMany(mappedBy = "certificate")
  private Set<RenewalReminder> certificateRenewalReminders = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

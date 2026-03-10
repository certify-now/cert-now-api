package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
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
public class Property {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column private Integer bedrooms;

  @Column(nullable = false, length = 2)
  private String country;

  @Column(precision = 8, scale = 2)
  private BigDecimal floorAreaSqm;

  @Column private Integer floors;

  @Column private Integer gasApplianceCount;

  @Column(nullable = false)
  private Boolean hasElectric;

  @Column(nullable = false)
  private Boolean hasGasSupply;

  @Column(nullable = false)
  private Boolean isActive;

  @Column private Integer yearBuilt;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(nullable = false, length = 10)
  private String postcode;

  @Column(length = 20)
  private String uprn;

  @Column(length = 50)
  private String epcRegisterRef;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(length = 100)
  private String county;

  @Column(nullable = false)
  private String addressLine1;

  @Column private String addressLine2;

  @Column(nullable = false)
  private String propertyType;

  @Column(nullable = false, columnDefinition = "text")
  private String complianceStatus;

  @Column private String location;

  // ── Gas Safety certificate fields ──────────────────────────────────────────

  @Column private Boolean hasGasCertificate;

  @Column private LocalDate gasExpiryDate;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "bytea")
  private byte[] gasCertPdf;

  @Column(length = 255)
  private String gasCertPdfName;

  // ── EICR certificate fields ───────────────────────────────────────────────

  @Column private Boolean hasEicr;

  @Column private LocalDate eicrExpiryDate;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "bytea")
  private byte[] eicrCertPdf;

  @Column(length = 255)
  private String eicrCertPdfName;

  @OneToMany(mappedBy = "property")
  private Set<Certificate> propertyCertificates = new HashSet<>();

  @OneToMany(mappedBy = "property")
  private Set<Job> propertyJobs = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @OneToMany(mappedBy = "property")
  private Set<RenewalReminder> propertyRenewalReminders = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

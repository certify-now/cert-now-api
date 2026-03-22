package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;

@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_property_owner_address_postcode",
            columnNames = {"owner_id", "address_line1", "postcode"}))
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class Property implements SoftDeletable {

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

  /**
   * PostGIS geography point (WGS84). Populated from Ideal Postcodes resolve or postcode centroid
   * lookup.
   */
  @Column(columnDefinition = "geography(Point,4326)")
  private Point coordinates;

  // ── Gas Safety certificate fields ──────────────────────────────────────────

  @Column private Boolean hasGasCertificate;

  @Column private LocalDate gasExpiryDate;

  // ── EICR certificate fields ───────────────────────────────────────────────

  @Column private Boolean hasEicr;

  @Column private LocalDate eicrExpiryDate;

  // ── Current certificate FK references (denormalised for fast queries) ─────
  // EAGER: these are always accessed during DTO mapping and compliance enrichment.
  // The certificates are small rows with no collections, so eager loading is appropriate.

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "current_gas_certificate_id")
  private Certificate currentGasCertificate;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "current_eicr_certificate_id")
  private Certificate currentEicrCertificate;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "current_epc_certificate_id")
  private Certificate currentEpcCertificate;

  @OneToMany(mappedBy = "property")
  private Set<Certificate> certificates = new HashSet<>();

  @OneToMany(mappedBy = "property")
  private Set<ComplianceDocument> complianceDocuments = new HashSet<>();

  @OneToMany(mappedBy = "property")
  private Set<Job> propertyJobs = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @OneToMany(mappedBy = "property")
  private Set<RenewalReminder> propertyRenewalReminders = new HashSet<>();

  // ── Soft-delete fields ──────────────────────────────────────────────────

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;
}

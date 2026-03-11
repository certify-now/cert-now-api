package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.CompanyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.InstallationDetails;
import com.uk.certifynow.certify_now.domain.embeddable.TenantDetails;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "gas_safety_record",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_gas_safety_record_job_id", columnNames = "job_id"),
      @UniqueConstraint(
          name = "uq_gas_safety_record_certificate_number",
          columnNames = "certificate_number")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class GasSafetyRecord {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private Job job;

  // Certificate fields
  @Column(nullable = false, length = 100)
  private String certificateNumber;

  @Column(length = 100)
  private String certificateReference;

  @Column(length = 50)
  private String certificateType;

  @Column(nullable = false)
  private LocalDate issueDate;

  @Column(nullable = false)
  private LocalDate nextInspectionDueOnOrBefore;

  @Column(nullable = false)
  private Integer numberOfAppliancesTested;

  @Column(length = 512)
  private String qrCodeUrl;

  @Column(length = 512)
  private String verificationUrl;

  // Engineer fields
  @Column(nullable = false)
  private String engineerName;

  @Column(nullable = false, length = 20)
  private String engineerGasSafeNumber;

  @Column(length = 20)
  private String engineerLicenceCardNumber;

  @Column private String timeOfArrival;

  @Column private String timeOfDeparture;

  @Column private LocalDate reportIssuedDate;

  @Column(columnDefinition = "text")
  private String engineerNotes;

  // Final checks (stored as String: YES/NO/N/A)
  @Column(length = 10)
  private String gasTightnessPass;

  @Column(length = 10)
  private String gasPipeWorkVisualPass;

  @Column(length = 10)
  private String emergencyControlAccessible;

  @Column(length = 10)
  private String equipotentialBonding;

  @Column(length = 10)
  private String installationPass;

  @Column(length = 10)
  private String coAlarmFittedWorkingSameRoom;

  @Column(length = 10)
  private String smokeAlarmFittedWorking;

  @Column(columnDefinition = "text")
  private String additionalObservations;

  // Faults and remedials
  @Column(columnDefinition = "text")
  private String faultsNotes;

  @Column(columnDefinition = "text")
  private String remedialWorkTaken;

  @Column private Boolean warningNoticeFixed;

  @Column private Boolean applianceIsolated;

  @Column(columnDefinition = "text")
  private String isolationReason;

  // Signatures
  @Column private Boolean engineerSigned;

  @Column private LocalDate engineerSignedDate;

  @Column private String customerName;

  @Column private Boolean customerSigned;

  @Column private LocalDate customerSignedDate;

  @Column private Boolean tenantSigned;

  @Column private LocalDate tenantSignedDate;

  @Column private Boolean privacyPolicyAccepted;

  // Metadata
  @Column(length = 100)
  private String createdBySoftware;

  @Column(length = 20)
  private String version;

  @Column private OffsetDateTime generatedAt;

  @Column(length = 50)
  private String platform;

  // Embedded sections
  @Embedded private CompanyDetails companyDetails;

  @Embedded private ClientDetails clientDetails;

  @Embedded private TenantDetails tenantDetails;

  @Embedded private InstallationDetails installationDetails;

  // Appliances (child entities)
  @OneToMany(mappedBy = "gasSafetyRecord", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("applianceIndex ASC")
  private List<GasSafetyAppliance> appliances = new ArrayList<>();

  // Audit
  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.auth.domain.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.auth.domain.EngineerTier;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
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
public class EngineerProfile {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal acceptanceRate;

  @Column(nullable = false, precision = 3, scale = 2)
  private BigDecimal avgRating;

  @Column(nullable = false)
  private Boolean isOnline;

  @Column(nullable = false)
  private Integer maxDailyJobs;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal onTimePercentage;

  @Column(nullable = false, precision = 4, scale = 1)
  private BigDecimal serviceRadiusMiles;

  @Column(nullable = false)
  private Boolean stripeOnboarded;

  @Column(nullable = false)
  private Integer totalJobsCompleted;

  @Column(nullable = false)
  private Integer totalReviews;

  @Column private OffsetDateTime approvedAt;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private OffsetDateTime dbsCheckedAt;

  @Column private OffsetDateTime idVerifiedAt;

  @Column private OffsetDateTime insuranceVerifiedAt;

  @Column private OffsetDateTime locationUpdatedAt;

  @Column private OffsetDateTime trainingCompletedAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(length = 50)
  private String dbsCertificateNumber;

  @Column(length = 50)
  private String dbsStatus;

  @Column(columnDefinition = "text")
  private String bio;

  @Column(nullable = false)
  @Convert(converter = EngineerApplicationStatus.EngineerApplicationStatusConverter.class)
  private EngineerApplicationStatus status;

  @Column private String stripeAccountId;

  @Column(nullable = false)
  @Convert(converter = EngineerTier.EngineerTierConverter.class)
  private EngineerTier tier;

  @Column private String location;

  @Column(columnDefinition = "text")
  private String preferredCertTypes;

  @Column(columnDefinition = "text")
  private String preferredJobTimes;

  @OneToMany(mappedBy = "engineerProfile")
  private Set<EngineerAvailability> engineerProfileEngineerAvailabilities = new HashSet<>();

  @OneToMany(mappedBy = "engineerProfile")
  private Set<EngineerInsurance> engineerProfileEngineerInsurances = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @OneToMany(mappedBy = "engineerProfile")
  private Set<EngineerQualification> engineerProfileEngineerQualifications = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

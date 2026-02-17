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
public class Job {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private Integer basePricePence;

  @Column(nullable = false)
  private Integer commissionPence;

  @Column(nullable = false, precision = 4, scale = 3)
  private BigDecimal commissionRate;

  @Column(nullable = false)
  private Integer discountPence;

  @Column(nullable = false)
  private Integer engineerPayoutPence;

  @Column(precision = 10, scale = 7)
  private BigDecimal engineerStartLat;

  @Column(precision = 10, scale = 7)
  private BigDecimal engineerStartLng;

  @Column(precision = 21, scale = 0)
  private BigDecimal estimatedDuration;

  @Column(nullable = false)
  private Integer matchAttempts;

  @Column(nullable = false)
  private Integer propertyModifierPence;

  @Column private LocalDate scheduledDate;

  @Column(nullable = false)
  private Integer totalPricePence;

  @Column(nullable = false)
  private Integer urgencyModifierPence;

  @Column private OffsetDateTime acceptedAt;

  @Column private OffsetDateTime cancelledAt;

  @Column private OffsetDateTime certifiedAt;

  @Column private OffsetDateTime completedAt;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column private OffsetDateTime enRouteAt;

  @Column private OffsetDateTime escalatedAt;

  @Column private OffsetDateTime matchedAt;

  @Column private OffsetDateTime scheduledAt;

  @Column private OffsetDateTime startedAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Column(nullable = false, length = 20)
  private String referenceNumber;

  @Column(length = 20)
  private String scheduledTimeSlot;

  @Column(columnDefinition = "text")
  private String accessInstructions;

  @Column(columnDefinition = "text")
  private String cancellationReason;

  @Column private String cancelledBy;

  @Column(nullable = false)
  private String certificateType;

  @Column(columnDefinition = "text")
  private String customerNotes;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String urgency;

  @OneToMany(mappedBy = "job")
  private Set<Certificate> jobCertificates = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<EicrInspection> jobEicrInspections = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<EpcAssessment> jobEpcAssessments = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<GasSafetyInspection> jobGasSafetyInspections = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<JobMatchLog> jobJobMatchLogs = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<JobStatusHistory> jobJobStatusHistories = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private User customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "engineer_id")
  private User engineer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @OneToMany(mappedBy = "job")
  private Set<Message> jobMessages = new HashSet<>();

  @OneToMany(mappedBy = "relatedJob")
  private Set<Notification> relatedJobNotifications = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<Payment> jobPayments = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<Payout> jobPayouts = new HashSet<>();

  @OneToMany(mappedBy = "job")
  private Set<Review> jobReviews = new HashSet<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

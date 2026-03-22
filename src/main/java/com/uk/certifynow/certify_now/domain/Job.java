package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
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

  @Column(columnDefinition = "integer not null default 0")
  private Integer adminAlertCount = 0;

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

  @Column private OffsetDateTime broadcastAt;

  @Column private OffsetDateTime escalatedAt;

  @Column private OffsetDateTime lastAdminAlertAt;

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

  @Column(columnDefinition = "text")
  private String preferredAvailability;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String urgency;

  @OneToOne(mappedBy = "job")
  private GasSafetyRecord gasSafetyRecord;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private User customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "engineer_id")
  private User engineer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @Version private Long version;
}

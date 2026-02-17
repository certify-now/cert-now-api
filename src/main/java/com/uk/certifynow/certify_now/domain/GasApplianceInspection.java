package com.uk.certifynow.certify_now.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
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
public class GasApplianceInspection {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(nullable = false)
  private Integer applianceOrder;

  @Column(precision = 8, scale = 5)
  private BigDecimal co2ReadingPercent;

  @Column(precision = 8, scale = 5)
  private BigDecimal coReadingPercent;

  @Column private Boolean flamePicturePass;

  @Column(precision = 6, scale = 2)
  private BigDecimal operatingPressureMbar;

  @Column(precision = 8, scale = 5)
  private BigDecimal ratio;

  @Column private Boolean safetyDeviceCorrect;

  @Column private Boolean spillageTestPass;

  @Column private Boolean ventilationPass;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false, length = 50)
  private String applianceType;

  @Column(length = 50)
  private String flueType;

  @Column(length = 50)
  private String gcNumber;

  @Column(nullable = false, length = 100)
  private String locationInProperty;

  @Column(length = 100)
  private String make;

  @Column(length = 100)
  private String model;

  @Column private String defectSeverity;

  @Column(columnDefinition = "text")
  private String defectsIdentified;

  @Column(nullable = false)
  private String result;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "gas_inspection_id", nullable = false)
  private GasSafetyInspection gasInspection;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

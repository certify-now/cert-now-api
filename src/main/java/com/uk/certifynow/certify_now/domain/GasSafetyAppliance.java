package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.domain.embeddable.CombustionReadings;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "gas_safety_appliance")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class GasSafetyAppliance {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "gas_safety_record_id", nullable = false)
  private GasSafetyRecord gasSafetyRecord;

  @Column(nullable = false)
  private Integer applianceIndex;

  @Column private String location;

  @Column(length = 100)
  private String applianceType;

  @Column(length = 100)
  private String make;

  @Column(length = 100)
  private String model;

  @Column(length = 100)
  private String serialNumber;

  @Column(nullable = false)
  private Boolean landlordsAppliance;

  @Column(nullable = false, length = 50)
  private String inspectionType;

  @Column(nullable = false)
  private Boolean applianceInspected;

  @Column private Boolean applianceServiced;

  @Column(nullable = false)
  private Boolean applianceSafeToUse;

  @Column(length = 10)
  private String classificationCode;

  @Column private String classificationDescription;

  @Column(length = 50)
  private String flueType;

  @Column private Boolean ventilationProvisionSatisfactory;

  @Column private Boolean flueVisualConditionTerminationSatisfactory;

  @Column(length = 20)
  private String fluePerformanceTests;

  @Column(length = 20)
  private String spillageTest;

  @Column(precision = 8, scale = 2)
  private BigDecimal operatingPressureMbar;

  @Column(precision = 8, scale = 2)
  private BigDecimal burnerPressureMbar;

  @Column private String gasRate;

  @Column(precision = 8, scale = 2)
  private BigDecimal heatInputKw;

  @Column private Boolean safetyDevicesCorrectOperation;

  @Column private Boolean emergencyControlAccessible;

  @Column private Boolean gasInstallationPipeworkVisualInspectionSatisfactory;

  @Column private Boolean gasTightnessSatisfactory;

  @Column private Boolean equipotentialBonding;

  @Column private Boolean warningNoticeFixed;

  @Column(columnDefinition = "text")
  private String additionalNotes;

  @Embedded private CombustionReadings combustionReadings;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;
}

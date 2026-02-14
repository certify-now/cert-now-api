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
public class EpcAssessment {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private LocalDate assessmentDate;

    @Column
    private Integer boilerAgeYears;

    @Column(nullable = false)
    private Integer currentScore;

    @Column
    private Integer environmentalImpact;

    @Column
    private Boolean hasInsulatedTank;

    @Column(nullable = false)
    private Boolean hasSolarPv;

    @Column(nullable = false)
    private Boolean hasSolarThermal;

    @Column
    private Integer lowEnergyLightingPct;

    @Column
    private Integer numberOfFloors;

    @Column
    private Integer potentialScore;

    @Column(precision = 8, scale = 2)
    private BigDecimal totalFloorAreaSqm;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime lodgedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(nullable = false, length = 50)
    private String assessorAccreditation;

    @Column(length = 50)
    private String boilerType;

    @Column(length = 50)
    private String builtForm;

    @Column(length = 50)
    private String constructionDateRange;

    @Column(length = 50)
    private String epcRegisterRef;

    @Column(length = 50)
    private String roofInsulation;

    @Column(length = 50)
    private String roofType;

    @Column(length = 50)
    private String wallInsulation;

    @Column(length = 50)
    private String wallType;

    @Column(length = 50)
    private String windowFrame;

    @Column(length = 50)
    private String windowType;

    @Column(length = 100)
    private String heatingControls;

    @Column(length = 100)
    private String hotWaterSystem;

    @Column(length = 100)
    private String mainHeatingType;

    @Column(length = 100)
    private String schemeName;

    @Column(nullable = false)
    private String currentRating;

    @Column
    private String potentialRating;

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

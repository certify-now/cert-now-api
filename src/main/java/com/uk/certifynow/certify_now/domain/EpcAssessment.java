package com.uk.certifynow.certify_now.domain;

import com.uk.certifynow.certify_now.domain.embeddable.ClientDetails;
import com.uk.certifynow.certify_now.domain.embeddable.EpcPropertyDetails;
import com.uk.certifynow.certify_now.domain.embeddable.OccupierDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "epc_assessment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class EpcAssessment {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false, unique = true)
  private Job job;

  // ── Property Details ──────────────────────────────────────────────────────
  @Embedded
  private EpcPropertyDetails propertyDetails;

  // ── Client Details ────────────────────────────────────────────────────────
  // Reuses existing embeddable; adds company field inline
  @Embedded
  private ClientDetails clientDetails;

  @Column(name = "client_company", length = 200)
  private String clientCompany;

  // ── Occupier Details ──────────────────────────────────────────────────────
  @Embedded
  private OccupierDetails occupierDetails;

  // ── Booking Details ───────────────────────────────────────────────────────
  @Column(name = "appointment_date")
  private LocalDate appointmentDate;

  @Column(name = "appointment_time")
  private LocalTime appointmentTime;

  @Column(name = "notes_for_assessor", columnDefinition = "text")
  private String notesForAssessor;

  // ── Pre-Assessment Data ───────────────────────────────────────────────────
  @Column(name = "wall_type", length = 100)
  private String wallType;

  @Column(name = "roof_insulation_depth_mm")
  private Integer roofInsulationDepthMm;

  @Column(name = "window_type", length = 100)
  private String windowType;

  @Column(name = "boiler_make", length = 100)
  private String boilerMake;

  @Column(name = "boiler_model", length = 100)
  private String boilerModel;

  @Column(name = "boiler_age", length = 50)
  private String boilerAge;

  // Stored as JSON array string: ["thermostat","TRVs","programmer"]
  @Column(name = "heating_controls", columnDefinition = "text")
  private String heatingControls;

  @Column(name = "secondary_heating", length = 100)
  private String secondaryHeating;

  @Column(name = "hot_water_cylinder_present")
  private Boolean hotWaterCylinderPresent;

  @Column(name = "cylinder_insulation", length = 100)
  private String cylinderInsulation;

  @Column(name = "lighting_low_energy_count")
  private Integer lightingLowEnergyCount;

  // Renewables
  @Column(name = "renewables_solar_pv")
  private Boolean renewablesSolarPv;

  @Column(name = "renewables_solar_thermal")
  private Boolean renewablesSolarThermal;

  @Column(name = "renewables_heat_pump")
  private Boolean renewablesHeatPump;

  // ── Photos (stored as JSON arrays of URL strings) ─────────────────────────
  @Column(name = "photos_exterior", columnDefinition = "text")
  private String photosExterior;

  @Column(name = "photos_boiler", columnDefinition = "text")
  private String photosBoiler;

  @Column(name = "photos_boiler_data_plate", columnDefinition = "text")
  private String photosBoilerDataPlate;

  @Column(name = "photos_heating_controls", columnDefinition = "text")
  private String photosHeatingControls;

  @Column(name = "photos_radiators", columnDefinition = "text")
  private String photosRadiators;

  @Column(name = "photos_windows", columnDefinition = "text")
  private String photosWindows;

  @Column(name = "photos_loft", columnDefinition = "text")
  private String photosLoft;

  @Column(name = "photos_hot_water_cylinder", columnDefinition = "text")
  private String photosHotWaterCylinder;

  @Column(name = "photos_renewables", columnDefinition = "text")
  private String photosRenewables;

  @Column(name = "photos_other_evidence", columnDefinition = "text")
  private String photosOtherEvidence;

  // ── Documents (URL/path references) ──────────────────────────────────────
  @Column(name = "doc_previous_epc_pdf", length = 512)
  private String docPreviousEpcPdf;

  @Column(name = "doc_fensa_certificate", length = 512)
  private String docFensaCertificate;

  @Column(name = "doc_loft_insulation_certificate", length = 512)
  private String docLoftInsulationCertificate;

  @Column(name = "doc_boiler_installation_certificate", length = 512)
  private String docBoilerInstallationCertificate;

  // ── Audit ─────────────────────────────────────────────────────────────────
  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime dateCreated;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdated;
}

package com.uk.certifynow.certify_now.rest.dto.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EpcRecordResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("job_id") UUID jobId,

    // Property details
    @JsonProperty("property_address_line1") String propertyAddressLine1,
    @JsonProperty("property_address_line2") String propertyAddressLine2,
    @JsonProperty("property_address_line3") String propertyAddressLine3,
    @JsonProperty("property_postcode") String propertyPostcode,
    @JsonProperty("property_type") String propertyType,
    @JsonProperty("number_of_bedrooms") Integer numberOfBedrooms,
    @JsonProperty("year_built") Integer yearBuilt,
    @JsonProperty("floor_level") Integer floorLevel,
    @JsonProperty("access_notes") String accessNotes,

    // Client details
    @JsonProperty("client_name") String clientName,
    @JsonProperty("client_email") String clientEmail,
    @JsonProperty("client_telephone") String clientTelephone,
    @JsonProperty("client_company") String clientCompany,

    // Occupier details
    @JsonProperty("occupier_name") String occupierName,
    @JsonProperty("occupier_telephone") String occupierTelephone,
    @JsonProperty("occupier_email") String occupierEmail,
    @JsonProperty("occupier_access_instructions") String occupierAccessInstructions,

    // Booking details
    @JsonProperty("appointment_date") LocalDate appointmentDate,
    @JsonProperty("appointment_time") LocalTime appointmentTime,
    @JsonProperty("notes_for_assessor") String notesForAssessor,

    // Pre-assessment data
    @JsonProperty("pre_assessment") PreAssessmentSummary preAssessment,

    // Photos
    @JsonProperty("photos") PhotosSummary photos,

    // Documents
    @JsonProperty("documents") DocumentsSummary documents,
    @JsonProperty("date_created") OffsetDateTime dateCreated) {

  public record PreAssessmentSummary(
      @JsonProperty("wall_type") String wallType,
      @JsonProperty("roof_insulation_depth_mm") Integer roofInsulationDepthMm,
      @JsonProperty("window_type") String windowType,
      @JsonProperty("boiler_make") String boilerMake,
      @JsonProperty("boiler_model") String boilerModel,
      @JsonProperty("boiler_age") String boilerAge,
      @JsonProperty("heating_controls") List<String> heatingControls,
      @JsonProperty("secondary_heating") String secondaryHeating,
      @JsonProperty("hot_water_cylinder_present") Boolean hotWaterCylinderPresent,
      @JsonProperty("cylinder_insulation") String cylinderInsulation,
      @JsonProperty("lighting_low_energy_count") Integer lightingLowEnergyCount,
      @JsonProperty("renewables_solar_pv") Boolean renewablesSolarPv,
      @JsonProperty("renewables_solar_thermal") Boolean renewablesSolarThermal,
      @JsonProperty("renewables_heat_pump") Boolean renewablesHeatPump) {}

  public record PhotosSummary(
      @JsonProperty("exterior") List<String> exterior,
      @JsonProperty("boiler") List<String> boiler,
      @JsonProperty("boiler_data_plate") List<String> boilerDataPlate,
      @JsonProperty("heating_controls") List<String> heatingControls,
      @JsonProperty("radiators") List<String> radiators,
      @JsonProperty("windows") List<String> windows,
      @JsonProperty("loft") List<String> loft,
      @JsonProperty("hot_water_cylinder") List<String> hotWaterCylinder,
      @JsonProperty("renewables") List<String> renewables,
      @JsonProperty("other_evidence") List<String> otherEvidence) {}

  public record DocumentsSummary(
      @JsonProperty("previous_epc_pdf") String previousEpcPdf,
      @JsonProperty("fensa_certificate") String fensaCertificate,
      @JsonProperty("loft_insulation_certificate") String loftInsulationCertificate,
      @JsonProperty("boiler_installation_certificate") String boilerInstallationCertificate) {}
}

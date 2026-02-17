package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EpcAssessment;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.model.EpcAssessmentDTO;
import com.uk.certifynow.certify_now.repos.EpcAssessmentRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EpcAssessmentService {

  private final EpcAssessmentRepository epcAssessmentRepository;
  private final JobRepository jobRepository;

  public EpcAssessmentService(
      final EpcAssessmentRepository epcAssessmentRepository, final JobRepository jobRepository) {
    this.epcAssessmentRepository = epcAssessmentRepository;
    this.jobRepository = jobRepository;
  }

  public List<EpcAssessmentDTO> findAll() {
    final List<EpcAssessment> epcAssessments = epcAssessmentRepository.findAll(Sort.by("id"));
    return epcAssessments.stream()
        .map(epcAssessment -> mapToDTO(epcAssessment, new EpcAssessmentDTO()))
        .toList();
  }

  public EpcAssessmentDTO get(final UUID id) {
    return epcAssessmentRepository
        .findById(id)
        .map(epcAssessment -> mapToDTO(epcAssessment, new EpcAssessmentDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EpcAssessmentDTO epcAssessmentDTO) {
    final EpcAssessment epcAssessment = new EpcAssessment();
    mapToEntity(epcAssessmentDTO, epcAssessment);
    return epcAssessmentRepository.save(epcAssessment).getId();
  }

  public void update(final UUID id, final EpcAssessmentDTO epcAssessmentDTO) {
    final EpcAssessment epcAssessment =
        epcAssessmentRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(epcAssessmentDTO, epcAssessment);
    epcAssessmentRepository.save(epcAssessment);
  }

  public void delete(final UUID id) {
    final EpcAssessment epcAssessment =
        epcAssessmentRepository.findById(id).orElseThrow(NotFoundException::new);
    epcAssessmentRepository.delete(epcAssessment);
  }

  private EpcAssessmentDTO mapToDTO(
      final EpcAssessment epcAssessment, final EpcAssessmentDTO epcAssessmentDTO) {
    epcAssessmentDTO.setId(epcAssessment.getId());
    epcAssessmentDTO.setAssessmentDate(epcAssessment.getAssessmentDate());
    epcAssessmentDTO.setBoilerAgeYears(epcAssessment.getBoilerAgeYears());
    epcAssessmentDTO.setCurrentScore(epcAssessment.getCurrentScore());
    epcAssessmentDTO.setEnvironmentalImpact(epcAssessment.getEnvironmentalImpact());
    epcAssessmentDTO.setHasInsulatedTank(epcAssessment.getHasInsulatedTank());
    epcAssessmentDTO.setHasSolarPv(epcAssessment.getHasSolarPv());
    epcAssessmentDTO.setHasSolarThermal(epcAssessment.getHasSolarThermal());
    epcAssessmentDTO.setLowEnergyLightingPct(epcAssessment.getLowEnergyLightingPct());
    epcAssessmentDTO.setNumberOfFloors(epcAssessment.getNumberOfFloors());
    epcAssessmentDTO.setPotentialScore(epcAssessment.getPotentialScore());
    epcAssessmentDTO.setTotalFloorAreaSqm(epcAssessment.getTotalFloorAreaSqm());
    epcAssessmentDTO.setCreatedAt(epcAssessment.getCreatedAt());
    epcAssessmentDTO.setLodgedAt(epcAssessment.getLodgedAt());
    epcAssessmentDTO.setUpdatedAt(epcAssessment.getUpdatedAt());
    epcAssessmentDTO.setAssessorAccreditation(epcAssessment.getAssessorAccreditation());
    epcAssessmentDTO.setBoilerType(epcAssessment.getBoilerType());
    epcAssessmentDTO.setBuiltForm(epcAssessment.getBuiltForm());
    epcAssessmentDTO.setConstructionDateRange(epcAssessment.getConstructionDateRange());
    epcAssessmentDTO.setEpcRegisterRef(epcAssessment.getEpcRegisterRef());
    epcAssessmentDTO.setRoofInsulation(epcAssessment.getRoofInsulation());
    epcAssessmentDTO.setRoofType(epcAssessment.getRoofType());
    epcAssessmentDTO.setWallInsulation(epcAssessment.getWallInsulation());
    epcAssessmentDTO.setWallType(epcAssessment.getWallType());
    epcAssessmentDTO.setWindowFrame(epcAssessment.getWindowFrame());
    epcAssessmentDTO.setWindowType(epcAssessment.getWindowType());
    epcAssessmentDTO.setHeatingControls(epcAssessment.getHeatingControls());
    epcAssessmentDTO.setHotWaterSystem(epcAssessment.getHotWaterSystem());
    epcAssessmentDTO.setMainHeatingType(epcAssessment.getMainHeatingType());
    epcAssessmentDTO.setSchemeName(epcAssessment.getSchemeName());
    epcAssessmentDTO.setCurrentRating(epcAssessment.getCurrentRating());
    epcAssessmentDTO.setPotentialRating(epcAssessment.getPotentialRating());
    epcAssessmentDTO.setJob(epcAssessment.getJob() == null ? null : epcAssessment.getJob().getId());
    return epcAssessmentDTO;
  }

  private EpcAssessment mapToEntity(
      final EpcAssessmentDTO epcAssessmentDTO, final EpcAssessment epcAssessment) {
    epcAssessment.setAssessmentDate(epcAssessmentDTO.getAssessmentDate());
    epcAssessment.setBoilerAgeYears(epcAssessmentDTO.getBoilerAgeYears());
    epcAssessment.setCurrentScore(epcAssessmentDTO.getCurrentScore());
    epcAssessment.setEnvironmentalImpact(epcAssessmentDTO.getEnvironmentalImpact());
    epcAssessment.setHasInsulatedTank(epcAssessmentDTO.getHasInsulatedTank());
    epcAssessment.setHasSolarPv(epcAssessmentDTO.getHasSolarPv());
    epcAssessment.setHasSolarThermal(epcAssessmentDTO.getHasSolarThermal());
    epcAssessment.setLowEnergyLightingPct(epcAssessmentDTO.getLowEnergyLightingPct());
    epcAssessment.setNumberOfFloors(epcAssessmentDTO.getNumberOfFloors());
    epcAssessment.setPotentialScore(epcAssessmentDTO.getPotentialScore());
    epcAssessment.setTotalFloorAreaSqm(epcAssessmentDTO.getTotalFloorAreaSqm());
    epcAssessment.setCreatedAt(epcAssessmentDTO.getCreatedAt());
    epcAssessment.setLodgedAt(epcAssessmentDTO.getLodgedAt());
    epcAssessment.setUpdatedAt(epcAssessmentDTO.getUpdatedAt());
    epcAssessment.setAssessorAccreditation(epcAssessmentDTO.getAssessorAccreditation());
    epcAssessment.setBoilerType(epcAssessmentDTO.getBoilerType());
    epcAssessment.setBuiltForm(epcAssessmentDTO.getBuiltForm());
    epcAssessment.setConstructionDateRange(epcAssessmentDTO.getConstructionDateRange());
    epcAssessment.setEpcRegisterRef(epcAssessmentDTO.getEpcRegisterRef());
    epcAssessment.setRoofInsulation(epcAssessmentDTO.getRoofInsulation());
    epcAssessment.setRoofType(epcAssessmentDTO.getRoofType());
    epcAssessment.setWallInsulation(epcAssessmentDTO.getWallInsulation());
    epcAssessment.setWallType(epcAssessmentDTO.getWallType());
    epcAssessment.setWindowFrame(epcAssessmentDTO.getWindowFrame());
    epcAssessment.setWindowType(epcAssessmentDTO.getWindowType());
    epcAssessment.setHeatingControls(epcAssessmentDTO.getHeatingControls());
    epcAssessment.setHotWaterSystem(epcAssessmentDTO.getHotWaterSystem());
    epcAssessment.setMainHeatingType(epcAssessmentDTO.getMainHeatingType());
    epcAssessment.setSchemeName(epcAssessmentDTO.getSchemeName());
    epcAssessment.setCurrentRating(epcAssessmentDTO.getCurrentRating());
    epcAssessment.setPotentialRating(epcAssessmentDTO.getPotentialRating());
    final Job job =
        epcAssessmentDTO.getJob() == null
            ? null
            : jobRepository
                .findById(epcAssessmentDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    epcAssessment.setJob(job);
    return epcAssessment;
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final EpcAssessment jobEpcAssessment = epcAssessmentRepository.findFirstByJobId(event.getId());
    if (jobEpcAssessment != null) {
      referencedException.setKey("job.epcAssessment.job.referenced");
      referencedException.addParam(jobEpcAssessment.getId());
      throw referencedException;
    }
  }
}

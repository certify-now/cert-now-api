package com.uk.certifynow.certify_now.service.inspection;

import com.uk.certifynow.certify_now.domain.EicrInspection;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.model.EicrInspectionDTO;
import com.uk.certifynow.certify_now.repos.EicrInspectionRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EicrInspectionService {

  private final EicrInspectionRepository eicrInspectionRepository;
  private final JobRepository jobRepository;

  public EicrInspectionService(
      final EicrInspectionRepository eicrInspectionRepository, final JobRepository jobRepository) {
    this.eicrInspectionRepository = eicrInspectionRepository;
    this.jobRepository = jobRepository;
  }

  public List<EicrInspectionDTO> findAll() {
    final List<EicrInspection> eicrInspections = eicrInspectionRepository.findAll(Sort.by("id"));
    return eicrInspections.stream()
        .map(eicrInspection -> mapToDTO(eicrInspection, new EicrInspectionDTO()))
        .toList();
  }

  public EicrInspectionDTO get(final UUID id) {
    return eicrInspectionRepository
        .findById(id)
        .map(eicrInspection -> mapToDTO(eicrInspection, new EicrInspectionDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EicrInspectionDTO eicrInspectionDTO) {
    final EicrInspection eicrInspection = new EicrInspection();
    mapToEntity(eicrInspectionDTO, eicrInspection);
    return eicrInspectionRepository.save(eicrInspection).getId();
  }

  public void update(final UUID id, final EicrInspectionDTO eicrInspectionDTO) {
    final EicrInspection eicrInspection =
        eicrInspectionRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(eicrInspectionDTO, eicrInspection);
    eicrInspectionRepository.save(eicrInspection);
  }

  public void delete(final UUID id) {
    final EicrInspection eicrInspection =
        eicrInspectionRepository.findById(id).orElseThrow(NotFoundException::new);
    eicrInspectionRepository.delete(eicrInspection);
  }

  private EicrInspectionDTO mapToDTO(
      final EicrInspection eicrInspection, final EicrInspectionDTO eicrInspectionDTO) {
    eicrInspectionDTO.setId(eicrInspection.getId());
    eicrInspectionDTO.setC1Count(eicrInspection.getC1Count());
    eicrInspectionDTO.setC2Count(eicrInspection.getC2Count());
    eicrInspectionDTO.setC3Count(eicrInspection.getC3Count());
    eicrInspectionDTO.setConsumerUnitAgeYears(eicrInspection.getConsumerUnitAgeYears());
    eicrInspectionDTO.setFiCount(eicrInspection.getFiCount());
    eicrInspectionDTO.setInspectionDate(eicrInspection.getInspectionDate());
    eicrInspectionDTO.setInstallationYear(eicrInspection.getInstallationYear());
    eicrInspectionDTO.setNextInspectionDate(eicrInspection.getNextInspectionDate());
    eicrInspectionDTO.setNumberOfCircuits(eicrInspection.getNumberOfCircuits());
    eicrInspectionDTO.setCreatedAt(eicrInspection.getCreatedAt());
    eicrInspectionDTO.setUpdatedAt(eicrInspection.getUpdatedAt());
    eicrInspectionDTO.setEarthingArrangement(eicrInspection.getEarthingArrangement());
    eicrInspectionDTO.setInspectorAccreditation(eicrInspection.getInspectorAccreditation());
    eicrInspectionDTO.setConsumerUnitType(eicrInspection.getConsumerUnitType());
    eicrInspectionDTO.setSchemeName(eicrInspection.getSchemeName());
    eicrInspectionDTO.setOverallResult(eicrInspection.getOverallResult());
    eicrInspectionDTO.setObservationsDetail(eicrInspection.getObservationsDetail());
    eicrInspectionDTO.setJob(
        eicrInspection.getJob() == null ? null : eicrInspection.getJob().getId());
    return eicrInspectionDTO;
  }

  private EicrInspection mapToEntity(
      final EicrInspectionDTO eicrInspectionDTO, final EicrInspection eicrInspection) {
    eicrInspection.setC1Count(eicrInspectionDTO.getC1Count());
    eicrInspection.setC2Count(eicrInspectionDTO.getC2Count());
    eicrInspection.setC3Count(eicrInspectionDTO.getC3Count());
    eicrInspection.setConsumerUnitAgeYears(eicrInspectionDTO.getConsumerUnitAgeYears());
    eicrInspection.setFiCount(eicrInspectionDTO.getFiCount());
    eicrInspection.setInspectionDate(eicrInspectionDTO.getInspectionDate());
    eicrInspection.setInstallationYear(eicrInspectionDTO.getInstallationYear());
    eicrInspection.setNextInspectionDate(eicrInspectionDTO.getNextInspectionDate());
    eicrInspection.setNumberOfCircuits(eicrInspectionDTO.getNumberOfCircuits());
    eicrInspection.setCreatedAt(eicrInspectionDTO.getCreatedAt());
    eicrInspection.setUpdatedAt(eicrInspectionDTO.getUpdatedAt());
    eicrInspection.setEarthingArrangement(eicrInspectionDTO.getEarthingArrangement());
    eicrInspection.setInspectorAccreditation(eicrInspectionDTO.getInspectorAccreditation());
    eicrInspection.setConsumerUnitType(eicrInspectionDTO.getConsumerUnitType());
    eicrInspection.setSchemeName(eicrInspectionDTO.getSchemeName());
    eicrInspection.setOverallResult(eicrInspectionDTO.getOverallResult());
    eicrInspection.setObservationsDetail(eicrInspectionDTO.getObservationsDetail());
    final Job job =
        eicrInspectionDTO.getJob() == null
            ? null
            : jobRepository
                .findById(eicrInspectionDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    eicrInspection.setJob(job);
    return eicrInspection;
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final EicrInspection jobEicrInspection =
        eicrInspectionRepository.findFirstByJobId(event.getId());
    if (jobEicrInspection != null) {
      referencedException.setKey("job.eicrInspection.job.referenced");
      referencedException.addParam(jobEicrInspection.getId());
      throw referencedException;
    }
  }
}

package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.GasSafetyInspection;
import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.events.BeforeDeleteGasSafetyInspection;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.model.GasSafetyInspectionDTO;
import com.uk.certifynow.certify_now.repos.GasSafetyInspectionRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class GasSafetyInspectionService {

    private final GasSafetyInspectionRepository gasSafetyInspectionRepository;
    private final JobRepository jobRepository;
    private final ApplicationEventPublisher publisher;

    public GasSafetyInspectionService(
            final GasSafetyInspectionRepository gasSafetyInspectionRepository,
            final JobRepository jobRepository, final ApplicationEventPublisher publisher) {
        this.gasSafetyInspectionRepository = gasSafetyInspectionRepository;
        this.jobRepository = jobRepository;
        this.publisher = publisher;
    }

    public List<GasSafetyInspectionDTO> findAll() {
        final List<GasSafetyInspection> gasSafetyInspections = gasSafetyInspectionRepository.findAll(Sort.by("id"));
        return gasSafetyInspections.stream()
                .map(gasSafetyInspection -> mapToDTO(gasSafetyInspection, new GasSafetyInspectionDTO()))
                .toList();
    }

    public GasSafetyInspectionDTO get(final UUID id) {
        return gasSafetyInspectionRepository.findById(id)
                .map(gasSafetyInspection -> mapToDTO(gasSafetyInspection, new GasSafetyInspectionDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final GasSafetyInspectionDTO gasSafetyInspectionDTO) {
        final GasSafetyInspection gasSafetyInspection = new GasSafetyInspection();
        mapToEntity(gasSafetyInspectionDTO, gasSafetyInspection);
        return gasSafetyInspectionRepository.save(gasSafetyInspection).getId();
    }

    public void update(final UUID id, final GasSafetyInspectionDTO gasSafetyInspectionDTO) {
        final GasSafetyInspection gasSafetyInspection = gasSafetyInspectionRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(gasSafetyInspectionDTO, gasSafetyInspection);
        gasSafetyInspectionRepository.save(gasSafetyInspection);
    }

    public void delete(final UUID id) {
        final GasSafetyInspection gasSafetyInspection = gasSafetyInspectionRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        publisher.publishEvent(new BeforeDeleteGasSafetyInspection(id));
        gasSafetyInspectionRepository.delete(gasSafetyInspection);
    }

    private GasSafetyInspectionDTO mapToDTO(final GasSafetyInspection gasSafetyInspection,
            final GasSafetyInspectionDTO gasSafetyInspectionDTO) {
        gasSafetyInspectionDTO.setId(gasSafetyInspection.getId());
        gasSafetyInspectionDTO.setInspectionDate(gasSafetyInspection.getInspectionDate());
        gasSafetyInspectionDTO.setNextInspectionDate(gasSafetyInspection.getNextInspectionDate());
        gasSafetyInspectionDTO.setCreatedAt(gasSafetyInspection.getCreatedAt());
        gasSafetyInspectionDTO.setUpdatedAt(gasSafetyInspection.getUpdatedAt());
        gasSafetyInspectionDTO.setInspectorGasSafeId(gasSafetyInspection.getInspectorGasSafeId());
        gasSafetyInspectionDTO.setDefectSeverity(gasSafetyInspection.getDefectSeverity());
        gasSafetyInspectionDTO.setDefectsText(gasSafetyInspection.getDefectsText());
        gasSafetyInspectionDTO.setLandlordAddress(gasSafetyInspection.getLandlordAddress());
        gasSafetyInspectionDTO.setLandlordName(gasSafetyInspection.getLandlordName());
        gasSafetyInspectionDTO.setOverallResult(gasSafetyInspection.getOverallResult());
        gasSafetyInspectionDTO.setJob(gasSafetyInspection.getJob() == null ? null : gasSafetyInspection.getJob().getId());
        return gasSafetyInspectionDTO;
    }

    private GasSafetyInspection mapToEntity(final GasSafetyInspectionDTO gasSafetyInspectionDTO,
            final GasSafetyInspection gasSafetyInspection) {
        gasSafetyInspection.setInspectionDate(gasSafetyInspectionDTO.getInspectionDate());
        gasSafetyInspection.setNextInspectionDate(gasSafetyInspectionDTO.getNextInspectionDate());
        gasSafetyInspection.setCreatedAt(gasSafetyInspectionDTO.getCreatedAt());
        gasSafetyInspection.setUpdatedAt(gasSafetyInspectionDTO.getUpdatedAt());
        gasSafetyInspection.setInspectorGasSafeId(gasSafetyInspectionDTO.getInspectorGasSafeId());
        gasSafetyInspection.setDefectSeverity(gasSafetyInspectionDTO.getDefectSeverity());
        gasSafetyInspection.setDefectsText(gasSafetyInspectionDTO.getDefectsText());
        gasSafetyInspection.setLandlordAddress(gasSafetyInspectionDTO.getLandlordAddress());
        gasSafetyInspection.setLandlordName(gasSafetyInspectionDTO.getLandlordName());
        gasSafetyInspection.setOverallResult(gasSafetyInspectionDTO.getOverallResult());
        final Job job = gasSafetyInspectionDTO.getJob() == null ? null : jobRepository.findById(gasSafetyInspectionDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
        gasSafetyInspection.setJob(job);
        return gasSafetyInspection;
    }

    @EventListener(BeforeDeleteJob.class)
    public void on(final BeforeDeleteJob event) {
        final ReferencedException referencedException = new ReferencedException();
        final GasSafetyInspection jobGasSafetyInspection = gasSafetyInspectionRepository.findFirstByJobId(event.getId());
        if (jobGasSafetyInspection != null) {
            referencedException.setKey("job.gasSafetyInspection.job.referenced");
            referencedException.addParam(jobGasSafetyInspection.getId());
            throw referencedException;
        }
    }

}

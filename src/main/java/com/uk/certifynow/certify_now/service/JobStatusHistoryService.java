package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobStatusHistory;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.model.JobStatusHistoryDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.JobStatusHistoryRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class JobStatusHistoryService {

    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final JobRepository jobRepository;

    public JobStatusHistoryService(final JobStatusHistoryRepository jobStatusHistoryRepository,
            final JobRepository jobRepository) {
        this.jobStatusHistoryRepository = jobStatusHistoryRepository;
        this.jobRepository = jobRepository;
    }

    public List<JobStatusHistoryDTO> findAll() {
        final List<JobStatusHistory> jobStatusHistories = jobStatusHistoryRepository.findAll(Sort.by("id"));
        return jobStatusHistories.stream()
                .map(jobStatusHistory -> mapToDTO(jobStatusHistory, new JobStatusHistoryDTO()))
                .toList();
    }

    public JobStatusHistoryDTO get(final UUID id) {
        return jobStatusHistoryRepository.findById(id)
                .map(jobStatusHistory -> mapToDTO(jobStatusHistory, new JobStatusHistoryDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final JobStatusHistoryDTO jobStatusHistoryDTO) {
        final JobStatusHistory jobStatusHistory = new JobStatusHistory();
        mapToEntity(jobStatusHistoryDTO, jobStatusHistory);
        return jobStatusHistoryRepository.save(jobStatusHistory).getId();
    }

    public void update(final UUID id, final JobStatusHistoryDTO jobStatusHistoryDTO) {
        final JobStatusHistory jobStatusHistory = jobStatusHistoryRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(jobStatusHistoryDTO, jobStatusHistory);
        jobStatusHistoryRepository.save(jobStatusHistory);
    }

    public void delete(final UUID id) {
        final JobStatusHistory jobStatusHistory = jobStatusHistoryRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        jobStatusHistoryRepository.delete(jobStatusHistory);
    }

    private JobStatusHistoryDTO mapToDTO(final JobStatusHistory jobStatusHistory,
            final JobStatusHistoryDTO jobStatusHistoryDTO) {
        jobStatusHistoryDTO.setId(jobStatusHistory.getId());
        jobStatusHistoryDTO.setCreatedAt(jobStatusHistory.getCreatedAt());
        jobStatusHistoryDTO.setActorId(jobStatusHistory.getActorId());
        jobStatusHistoryDTO.setActorType(jobStatusHistory.getActorType());
        jobStatusHistoryDTO.setFromStatus(jobStatusHistory.getFromStatus());
        jobStatusHistoryDTO.setReason(jobStatusHistory.getReason());
        jobStatusHistoryDTO.setToStatus(jobStatusHistory.getToStatus());
        jobStatusHistoryDTO.setMetadata(jobStatusHistory.getMetadata());
        jobStatusHistoryDTO.setJob(jobStatusHistory.getJob() == null ? null : jobStatusHistory.getJob().getId());
        return jobStatusHistoryDTO;
    }

    private JobStatusHistory mapToEntity(final JobStatusHistoryDTO jobStatusHistoryDTO,
            final JobStatusHistory jobStatusHistory) {
        jobStatusHistory.setCreatedAt(jobStatusHistoryDTO.getCreatedAt());
        jobStatusHistory.setActorId(jobStatusHistoryDTO.getActorId());
        jobStatusHistory.setActorType(jobStatusHistoryDTO.getActorType());
        jobStatusHistory.setFromStatus(jobStatusHistoryDTO.getFromStatus());
        jobStatusHistory.setReason(jobStatusHistoryDTO.getReason());
        jobStatusHistory.setToStatus(jobStatusHistoryDTO.getToStatus());
        jobStatusHistory.setMetadata(jobStatusHistoryDTO.getMetadata());
        final Job job = jobStatusHistoryDTO.getJob() == null ? null : jobRepository.findById(jobStatusHistoryDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
        jobStatusHistory.setJob(job);
        return jobStatusHistory;
    }

    @EventListener(BeforeDeleteJob.class)
    public void on(final BeforeDeleteJob event) {
        final ReferencedException referencedException = new ReferencedException();
        final JobStatusHistory jobJobStatusHistory = jobStatusHistoryRepository.findFirstByJobId(event.getId());
        if (jobJobStatusHistory != null) {
            referencedException.setKey("job.jobStatusHistory.job.referenced");
            referencedException.addParam(jobJobStatusHistory.getId());
            throw referencedException;
        }
    }

}

package com.uk.certifynow.certify_now.service.job;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.JobMatchLog;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.JobMatchLogDTO;
import com.uk.certifynow.certify_now.repos.JobMatchLogRepository;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class JobMatchLogService {

  private final JobMatchLogRepository jobMatchLogRepository;
  private final UserRepository userRepository;
  private final JobRepository jobRepository;

  public JobMatchLogService(
      final JobMatchLogRepository jobMatchLogRepository,
      final UserRepository userRepository,
      final JobRepository jobRepository) {
    this.jobMatchLogRepository = jobMatchLogRepository;
    this.userRepository = userRepository;
    this.jobRepository = jobRepository;
  }

  public List<JobMatchLogDTO> findAll() {
    final List<JobMatchLog> jobMatchLogs = jobMatchLogRepository.findAll(Sort.by("id"));
    return jobMatchLogs.stream()
        .map(jobMatchLog -> mapToDTO(jobMatchLog, new JobMatchLogDTO()))
        .toList();
  }

  public JobMatchLogDTO get(final UUID id) {
    return jobMatchLogRepository
        .findById(id)
        .map(jobMatchLog -> mapToDTO(jobMatchLog, new JobMatchLogDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final JobMatchLogDTO jobMatchLogDTO) {
    final JobMatchLog jobMatchLog = new JobMatchLog();
    mapToEntity(jobMatchLogDTO, jobMatchLog);
    return jobMatchLogRepository.save(jobMatchLog).getId();
  }

  public void update(final UUID id, final JobMatchLogDTO jobMatchLogDTO) {
    final JobMatchLog jobMatchLog =
        jobMatchLogRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(jobMatchLogDTO, jobMatchLog);
    jobMatchLogRepository.save(jobMatchLog);
  }

  public void delete(final UUID id) {
    final JobMatchLog jobMatchLog =
        jobMatchLogRepository.findById(id).orElseThrow(NotFoundException::new);
    jobMatchLogRepository.delete(jobMatchLog);
  }

  private JobMatchLogDTO mapToDTO(
      final JobMatchLog jobMatchLog, final JobMatchLogDTO jobMatchLogDTO) {
    jobMatchLogDTO.setId(jobMatchLog.getId());
    jobMatchLogDTO.setDistanceMiles(jobMatchLog.getDistanceMiles());
    jobMatchLogDTO.setMatchScore(jobMatchLog.getMatchScore());
    jobMatchLogDTO.setCreatedAt(jobMatchLog.getCreatedAt());
    jobMatchLogDTO.setOfferedAt(jobMatchLog.getOfferedAt());
    jobMatchLogDTO.setRespondedAt(jobMatchLog.getRespondedAt());
    jobMatchLogDTO.setResponse(jobMatchLog.getResponse());
    jobMatchLogDTO.setDeclineReason(jobMatchLog.getDeclineReason());
    jobMatchLogDTO.setEngineer(
        jobMatchLog.getEngineer() == null ? null : jobMatchLog.getEngineer().getId());
    jobMatchLogDTO.setJob(jobMatchLog.getJob() == null ? null : jobMatchLog.getJob().getId());
    return jobMatchLogDTO;
  }

  private JobMatchLog mapToEntity(
      final JobMatchLogDTO jobMatchLogDTO, final JobMatchLog jobMatchLog) {
    jobMatchLog.setDistanceMiles(jobMatchLogDTO.getDistanceMiles());
    jobMatchLog.setMatchScore(jobMatchLogDTO.getMatchScore());
    jobMatchLog.setCreatedAt(jobMatchLogDTO.getCreatedAt());
    jobMatchLog.setOfferedAt(jobMatchLogDTO.getOfferedAt());
    jobMatchLog.setRespondedAt(jobMatchLogDTO.getRespondedAt());
    jobMatchLog.setResponse(jobMatchLogDTO.getResponse());
    jobMatchLog.setDeclineReason(jobMatchLogDTO.getDeclineReason());
    final User engineer =
        jobMatchLogDTO.getEngineer() == null
            ? null
            : userRepository
                .findById(jobMatchLogDTO.getEngineer())
                .orElseThrow(() -> new NotFoundException("engineer not found"));
    jobMatchLog.setEngineer(engineer);
    final Job job =
        jobMatchLogDTO.getJob() == null
            ? null
            : jobRepository
                .findById(jobMatchLogDTO.getJob())
                .orElseThrow(() -> new NotFoundException("job not found"));
    jobMatchLog.setJob(job);
    return jobMatchLog;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final JobMatchLog engineerJobMatchLog =
        jobMatchLogRepository.findFirstByEngineerId(event.getId());
    if (engineerJobMatchLog != null) {
      referencedException.setKey("user.jobMatchLog.engineer.referenced");
      referencedException.addParam(engineerJobMatchLog.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final ReferencedException referencedException = new ReferencedException();
    final JobMatchLog jobJobMatchLog = jobMatchLogRepository.findFirstByJobId(event.getId());
    if (jobJobMatchLog != null) {
      referencedException.setKey("job.jobMatchLog.job.referenced");
      referencedException.addParam(jobJobMatchLog.getId());
      throw referencedException;
    }
  }
}

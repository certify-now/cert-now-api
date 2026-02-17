package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.Job;
import com.uk.certifynow.certify_now.domain.Property;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.events.BeforeDeleteProperty;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.JobDTO;
import com.uk.certifynow.certify_now.repos.JobRepository;
import com.uk.certifynow.certify_now.repos.PropertyRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class JobService {

  private final JobRepository jobRepository;
  private final UserRepository userRepository;
  private final PropertyRepository propertyRepository;
  private final ApplicationEventPublisher publisher;

  public JobService(
      final JobRepository jobRepository,
      final UserRepository userRepository,
      final PropertyRepository propertyRepository,
      final ApplicationEventPublisher publisher) {
    this.jobRepository = jobRepository;
    this.userRepository = userRepository;
    this.propertyRepository = propertyRepository;
    this.publisher = publisher;
  }

  public List<JobDTO> findAll() {
    final List<Job> jobs = jobRepository.findAll(Sort.by("id"));
    return jobs.stream().map(job -> mapToDTO(job, new JobDTO())).toList();
  }

  public JobDTO get(final UUID id) {
    return jobRepository
        .findById(id)
        .map(job -> mapToDTO(job, new JobDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final JobDTO jobDTO) {
    final Job job = new Job();
    mapToEntity(jobDTO, job);
    return jobRepository.save(job).getId();
  }

  public void update(final UUID id, final JobDTO jobDTO) {
    final Job job = jobRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(jobDTO, job);
    jobRepository.save(job);
  }

  public void delete(final UUID id) {
    final Job job = jobRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteJob(id));
    jobRepository.delete(job);
  }

  private JobDTO mapToDTO(final Job job, final JobDTO jobDTO) {
    jobDTO.setId(job.getId());
    jobDTO.setBasePricePence(job.getBasePricePence());
    jobDTO.setCommissionPence(job.getCommissionPence());
    jobDTO.setCommissionRate(job.getCommissionRate());
    jobDTO.setDiscountPence(job.getDiscountPence());
    jobDTO.setEngineerPayoutPence(job.getEngineerPayoutPence());
    jobDTO.setEngineerStartLat(job.getEngineerStartLat());
    jobDTO.setEngineerStartLng(job.getEngineerStartLng());
    jobDTO.setEstimatedDuration(job.getEstimatedDuration());
    jobDTO.setMatchAttempts(job.getMatchAttempts());
    jobDTO.setPropertyModifierPence(job.getPropertyModifierPence());
    jobDTO.setScheduledDate(job.getScheduledDate());
    jobDTO.setTotalPricePence(job.getTotalPricePence());
    jobDTO.setUrgencyModifierPence(job.getUrgencyModifierPence());
    jobDTO.setAcceptedAt(job.getAcceptedAt());
    jobDTO.setCancelledAt(job.getCancelledAt());
    jobDTO.setCertifiedAt(job.getCertifiedAt());
    jobDTO.setCompletedAt(job.getCompletedAt());
    jobDTO.setCreatedAt(job.getCreatedAt());
    jobDTO.setEnRouteAt(job.getEnRouteAt());
    jobDTO.setEscalatedAt(job.getEscalatedAt());
    jobDTO.setMatchedAt(job.getMatchedAt());
    jobDTO.setScheduledAt(job.getScheduledAt());
    jobDTO.setStartedAt(job.getStartedAt());
    jobDTO.setUpdatedAt(job.getUpdatedAt());
    jobDTO.setReferenceNumber(job.getReferenceNumber());
    jobDTO.setScheduledTimeSlot(job.getScheduledTimeSlot());
    jobDTO.setAccessInstructions(job.getAccessInstructions());
    jobDTO.setCancellationReason(job.getCancellationReason());
    jobDTO.setCancelledBy(job.getCancelledBy());
    jobDTO.setCertificateType(job.getCertificateType());
    jobDTO.setCustomerNotes(job.getCustomerNotes());
    jobDTO.setStatus(job.getStatus());
    jobDTO.setUrgency(job.getUrgency());
    jobDTO.setCustomer(job.getCustomer() == null ? null : job.getCustomer().getId());
    jobDTO.setEngineer(job.getEngineer() == null ? null : job.getEngineer().getId());
    jobDTO.setProperty(job.getProperty() == null ? null : job.getProperty().getId());
    return jobDTO;
  }

  private Job mapToEntity(final JobDTO jobDTO, final Job job) {
    job.setBasePricePence(jobDTO.getBasePricePence());
    job.setCommissionPence(jobDTO.getCommissionPence());
    job.setCommissionRate(jobDTO.getCommissionRate());
    job.setDiscountPence(jobDTO.getDiscountPence());
    job.setEngineerPayoutPence(jobDTO.getEngineerPayoutPence());
    job.setEngineerStartLat(jobDTO.getEngineerStartLat());
    job.setEngineerStartLng(jobDTO.getEngineerStartLng());
    job.setEstimatedDuration(jobDTO.getEstimatedDuration());
    job.setMatchAttempts(jobDTO.getMatchAttempts());
    job.setPropertyModifierPence(jobDTO.getPropertyModifierPence());
    job.setScheduledDate(jobDTO.getScheduledDate());
    job.setTotalPricePence(jobDTO.getTotalPricePence());
    job.setUrgencyModifierPence(jobDTO.getUrgencyModifierPence());
    job.setAcceptedAt(jobDTO.getAcceptedAt());
    job.setCancelledAt(jobDTO.getCancelledAt());
    job.setCertifiedAt(jobDTO.getCertifiedAt());
    job.setCompletedAt(jobDTO.getCompletedAt());
    job.setCreatedAt(jobDTO.getCreatedAt());
    job.setEnRouteAt(jobDTO.getEnRouteAt());
    job.setEscalatedAt(jobDTO.getEscalatedAt());
    job.setMatchedAt(jobDTO.getMatchedAt());
    job.setScheduledAt(jobDTO.getScheduledAt());
    job.setStartedAt(jobDTO.getStartedAt());
    job.setUpdatedAt(jobDTO.getUpdatedAt());
    job.setReferenceNumber(jobDTO.getReferenceNumber());
    job.setScheduledTimeSlot(jobDTO.getScheduledTimeSlot());
    job.setAccessInstructions(jobDTO.getAccessInstructions());
    job.setCancellationReason(jobDTO.getCancellationReason());
    job.setCancelledBy(jobDTO.getCancelledBy());
    job.setCertificateType(jobDTO.getCertificateType());
    job.setCustomerNotes(jobDTO.getCustomerNotes());
    job.setStatus(jobDTO.getStatus());
    job.setUrgency(jobDTO.getUrgency());
    final User customer =
        jobDTO.getCustomer() == null
            ? null
            : userRepository
                .findById(jobDTO.getCustomer())
                .orElseThrow(() -> new NotFoundException("customer not found"));
    job.setCustomer(customer);
    final User engineer =
        jobDTO.getEngineer() == null
            ? null
            : userRepository
                .findById(jobDTO.getEngineer())
                .orElseThrow(() -> new NotFoundException("engineer not found"));
    job.setEngineer(engineer);
    final Property property =
        jobDTO.getProperty() == null
            ? null
            : propertyRepository
                .findById(jobDTO.getProperty())
                .orElseThrow(() -> new NotFoundException("property not found"));
    job.setProperty(property);
    return job;
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final Job customerJob = jobRepository.findFirstByCustomerId(event.getId());
    if (customerJob != null) {
      referencedException.setKey("user.job.customer.referenced");
      referencedException.addParam(customerJob.getId());
      throw referencedException;
    }
    final Job engineerJob = jobRepository.findFirstByEngineerId(event.getId());
    if (engineerJob != null) {
      referencedException.setKey("user.job.engineer.referenced");
      referencedException.addParam(engineerJob.getId());
      throw referencedException;
    }
  }

  @EventListener(BeforeDeleteProperty.class)
  public void on(final BeforeDeleteProperty event) {
    final ReferencedException referencedException = new ReferencedException();
    final Job propertyJob = jobRepository.findFirstByPropertyId(event.getId());
    if (propertyJob != null) {
      referencedException.setKey("property.job.property.referenced");
      referencedException.addParam(propertyJob.getId());
      throw referencedException;
    }
  }
}

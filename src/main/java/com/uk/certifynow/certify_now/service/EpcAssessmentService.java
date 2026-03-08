package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EpcAssessment;
import com.uk.certifynow.certify_now.events.BeforeDeleteJob;
import com.uk.certifynow.certify_now.repos.EpcAssessmentRepository;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Lightweight CRUD guard service retained for the BeforeDeleteJob guard only. All inspection
 * business logic lives in {@code EpcInspectionService}.
 */
@Service
public class EpcAssessmentService {

  private final EpcAssessmentRepository epcAssessmentRepository;

  public EpcAssessmentService(final EpcAssessmentRepository epcAssessmentRepository) {
    this.epcAssessmentRepository = epcAssessmentRepository;
  }

  @EventListener(BeforeDeleteJob.class)
  public void on(final BeforeDeleteJob event) {
    final Optional<EpcAssessment> existing = epcAssessmentRepository.findByJobId(event.getId());
    if (existing.isPresent()) {
      final ReferencedException referencedException = new ReferencedException();
      referencedException.setKey("job.epcAssessment.job.referenced");
      referencedException.addParam(existing.get().getId());
      throw referencedException;
    }
  }

  public void delete(final UUID id) {
    epcAssessmentRepository.deleteById(id);
  }
}

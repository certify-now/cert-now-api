package com.uk.certifynow.certify_now.service.engineer;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.EngineerQualification;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerQualificationDTO;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerQualificationRepository;
import com.uk.certifynow.certify_now.rest.dto.engineer.AddQualificationRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.QualificationResponse;
import com.uk.certifynow.certify_now.service.mappers.EngineerQualificationMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EngineerQualificationService {

  private final EngineerQualificationRepository engineerQualificationRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final EngineerQualificationMapper engineerQualificationMapper;
  private final Clock clock;

  public EngineerQualificationService(
      final EngineerQualificationRepository engineerQualificationRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerQualificationMapper engineerQualificationMapper,
      final Clock clock) {
    this.engineerQualificationRepository = engineerQualificationRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerQualificationMapper = engineerQualificationMapper;
    this.clock = clock;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

  public List<EngineerQualificationDTO> findAll() {
    final List<EngineerQualification> engineerQualifications =
        engineerQualificationRepository.findAll(Sort.by("id"));
    return engineerQualifications.stream().map(engineerQualificationMapper::toDTO).toList();
  }

  public EngineerQualificationDTO get(final UUID id) {
    return engineerQualificationRepository
        .findById(id)
        .map(engineerQualificationMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final EngineerQualificationDTO engineerQualificationDTO) {
    final EngineerQualification engineerQualification = new EngineerQualification();
    engineerQualificationMapper.updateEntity(engineerQualificationDTO, engineerQualification);
    resolveReferences(engineerQualificationDTO, engineerQualification);
    final UUID savedId = engineerQualificationRepository.save(engineerQualification).getId();
    log.info("EngineerQualification {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerQualificationDTO engineerQualificationDTO) {
    final EngineerQualification engineerQualification =
        engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerQualificationMapper.updateEntity(engineerQualificationDTO, engineerQualification);
    resolveReferences(engineerQualificationDTO, engineerQualification);
    engineerQualificationRepository.save(engineerQualification);
    log.info("EngineerQualification {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final EngineerQualification engineerQualification =
        engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerQualificationRepository.delete(engineerQualification);
    log.info("EngineerQualification {} deleted", id);
  }

  // -- New business methods (Phase 5) -----------------------------------------

  @Transactional
  public QualificationResponse addQualification(
      final UUID userId, final AddQualificationRequest request) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final EngineerQualification qualification = new EngineerQualification();
    qualification.setEngineerProfile(profile);
    qualification.setType(request.type());
    qualification.setRegistrationNumber(request.registrationNumber());
    qualification.setIssueDate(request.issueDate());
    qualification.setExpiryDate(request.expiryDate());
    qualification.setSchemeName(request.schemeName());
    qualification.setDocumentUrl(request.documentUrl());
    qualification.setVerificationStatus(VerificationStatus.PENDING.name());
    qualification.setExternalVerified(false);
    qualification.setCreatedAt(now);
    qualification.setUpdatedAt(now);
    final QualificationResponse response =
        toResponse(engineerQualificationRepository.save(qualification));
    log.info(
        "Qualification {} added for engineer user {} (type={})",
        response.id(),
        userId,
        request.type());
    return response;
  }

  public List<QualificationResponse> getMyQualifications(final UUID userId) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    return engineerQualificationRepository.findAllByEngineerProfileId(profile.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public QualificationResponse verifyQualification(
      final UUID qualificationId, final UUID adminId, final String newStatus) {
    VerificationStatus.valueOf(newStatus);
    final EngineerQualification qualification =
        engineerQualificationRepository
            .findById(qualificationId)
            .orElseThrow(NotFoundException::new);
    qualification.setVerificationStatus(newStatus);
    qualification.setVerifiedAt(OffsetDateTime.now(clock));
    qualification.setVerifiedBy(adminId);
    qualification.setUpdatedAt(OffsetDateTime.now(clock));
    final QualificationResponse response =
        toResponse(engineerQualificationRepository.save(qualification));
    log.info("Qualification {} verified with status={}", qualificationId, newStatus);
    return response;
  }

  // -- Mapping helpers --------------------------------------------------------

  private EngineerProfile resolveProfileByUserId(final UUID userId) {
    return engineerProfileRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("Engineer profile not found for user"));
  }

  private QualificationResponse toResponse(final EngineerQualification q) {
    return new QualificationResponse(
        q.getId(),
        q.getEngineerProfile() == null ? null : q.getEngineerProfile().getId(),
        q.getType(),
        q.getRegistrationNumber(),
        q.getIssueDate(),
        q.getExpiryDate(),
        q.getSchemeName(),
        q.getDocumentUrl(),
        q.getVerificationStatus(),
        q.getExternalVerified(),
        q.getVerifiedAt(),
        q.getVerifiedBy(),
        q.getCreatedAt(),
        q.getUpdatedAt());
  }

  private void resolveReferences(
      final EngineerQualificationDTO dto, final EngineerQualification entity) {
    final EngineerProfile engineerProfile =
        dto.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(dto.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    entity.setEngineerProfile(engineerProfile);
  }

  @EventListener(BeforeDeleteEngineerProfile.class)
  public void on(final BeforeDeleteEngineerProfile event) {
    final ReferencedException referencedException = new ReferencedException();
    final EngineerQualification engineerProfileEngineerQualification =
        engineerQualificationRepository.findFirstByEngineerProfileId(event.getId());
    if (engineerProfileEngineerQualification != null) {
      referencedException.setKey(
          "engineerProfile.engineerQualification.engineerProfile.referenced");
      referencedException.addParam(engineerProfileEngineerQualification.getId());
      throw referencedException;
    }
  }
}

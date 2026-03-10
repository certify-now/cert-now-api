package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.EngineerQualification;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerQualificationDTO;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerQualificationRepository;
import com.uk.certifynow.certify_now.service.mappers.EngineerQualificationMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
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

  public EngineerQualificationService(
      final EngineerQualificationRepository engineerQualificationRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerQualificationMapper engineerQualificationMapper) {
    this.engineerQualificationRepository = engineerQualificationRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerQualificationMapper = engineerQualificationMapper;
  }

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
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerQualificationDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerQualificationDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerQualification.setEngineerProfile(engineerProfile);
    UUID savedId = engineerQualificationRepository.save(engineerQualification).getId();
    log.info("EngineerQualification {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerQualificationDTO engineerQualificationDTO) {
    final EngineerQualification engineerQualification =
        engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerQualificationMapper.updateEntity(engineerQualificationDTO, engineerQualification);
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerQualificationDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerQualificationDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerQualification.setEngineerProfile(engineerProfile);
    engineerQualificationRepository.save(engineerQualification);
    log.info("EngineerQualification {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerQualificationRepository.deleteById(id);
    log.info("EngineerQualification {} deleted", id);
  }

  @Transactional
  public EngineerQualificationDTO addQualification(
      final UUID engineerProfileId,
      final String qualificationType,
      final String registrationNumber,
      final java.time.LocalDate expiryDate,
      final String documentUrl) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository
            .findById(engineerProfileId)
            .orElseThrow(() -> new NotFoundException("Engineer profile not found"));
    final EngineerQualification qualification = new EngineerQualification();
    qualification.setEngineerProfile(engineerProfile);
    qualification.setQualificationType(qualificationType);
    qualification.setRegistrationNumber(registrationNumber);
    qualification.setExpiryDate(expiryDate);
    qualification.setDocumentUrl(documentUrl);
    qualification.setVerified(false);
    final EngineerQualification saved = engineerQualificationRepository.save(qualification);
    log.info(
        "Qualification {} added for engineer {} (type={})",
        saved.getId(),
        engineerProfileId,
        qualificationType);
    return engineerQualificationMapper.toDTO(saved);
  }

  @Transactional
  public EngineerQualificationDTO verifyQualification(
      final UUID qualificationId, final boolean verified) {
    final EngineerQualification qualification =
        engineerQualificationRepository
            .findById(qualificationId)
            .orElseThrow(() -> new NotFoundException("Qualification not found"));
    qualification.setVerified(verified);
    qualification.setVerifiedAt(verified ? OffsetDateTime.now() : null);
    final EngineerQualification saved = engineerQualificationRepository.save(qualification);
    log.info("Qualification {} verification set to {}", qualificationId, verified);
    return engineerQualificationMapper.toDTO(saved);
  }

  public List<EngineerQualificationDTO> getByEngineerProfile(final UUID engineerProfileId) {
    return engineerQualificationRepository.findAllByEngineerProfileId(engineerProfileId).stream()
        .map(engineerQualificationMapper::toDTO)
        .toList();
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

package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.EngineerQualification;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerQualificationDTO;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerQualificationRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EngineerQualificationService {

  private final EngineerQualificationRepository engineerQualificationRepository;
  private final EngineerProfileRepository engineerProfileRepository;

  public EngineerQualificationService(
      final EngineerQualificationRepository engineerQualificationRepository,
      final EngineerProfileRepository engineerProfileRepository) {
    this.engineerQualificationRepository = engineerQualificationRepository;
    this.engineerProfileRepository = engineerProfileRepository;
  }

  public List<EngineerQualificationDTO> findAll() {
    final List<EngineerQualification> engineerQualifications =
        engineerQualificationRepository.findAll(Sort.by("id"));
    return engineerQualifications.stream()
        .map(
            engineerQualification ->
                mapToDTO(engineerQualification, new EngineerQualificationDTO()))
        .toList();
  }

  public EngineerQualificationDTO get(final UUID id) {
    return engineerQualificationRepository
        .findById(id)
        .map(
            engineerQualification ->
                mapToDTO(engineerQualification, new EngineerQualificationDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EngineerQualificationDTO engineerQualificationDTO) {
    final EngineerQualification engineerQualification = new EngineerQualification();
    mapToEntity(engineerQualificationDTO, engineerQualification);
    return engineerQualificationRepository.save(engineerQualification).getId();
  }

  public void update(final UUID id, final EngineerQualificationDTO engineerQualificationDTO) {
    final EngineerQualification engineerQualification =
        engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(engineerQualificationDTO, engineerQualification);
    engineerQualificationRepository.save(engineerQualification);
  }

  public void delete(final UUID id) {
    final EngineerQualification engineerQualification =
        engineerQualificationRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerQualificationRepository.delete(engineerQualification);
  }

  private EngineerQualificationDTO mapToDTO(
      final EngineerQualification engineerQualification,
      final EngineerQualificationDTO engineerQualificationDTO) {
    engineerQualificationDTO.setId(engineerQualification.getId());
    engineerQualificationDTO.setExpiryDate(engineerQualification.getExpiryDate());
    engineerQualificationDTO.setExternalVerified(engineerQualification.getExternalVerified());
    engineerQualificationDTO.setIssueDate(engineerQualification.getIssueDate());
    engineerQualificationDTO.setCreatedAt(engineerQualification.getCreatedAt());
    engineerQualificationDTO.setLastApiCheckAt(engineerQualification.getLastApiCheckAt());
    engineerQualificationDTO.setUpdatedAt(engineerQualification.getUpdatedAt());
    engineerQualificationDTO.setVerifiedAt(engineerQualification.getVerifiedAt());
    engineerQualificationDTO.setVerifiedBy(engineerQualification.getVerifiedBy());
    engineerQualificationDTO.setRegistrationNumber(engineerQualification.getRegistrationNumber());
    engineerQualificationDTO.setDocumentUrl(engineerQualification.getDocumentUrl());
    engineerQualificationDTO.setSchemeName(engineerQualification.getSchemeName());
    engineerQualificationDTO.setType(engineerQualification.getType());
    engineerQualificationDTO.setVerificationStatus(engineerQualification.getVerificationStatus());
    engineerQualificationDTO.setMetadata(engineerQualification.getMetadata());
    engineerQualificationDTO.setEngineerProfile(
        engineerQualification.getEngineerProfile() == null
            ? null
            : engineerQualification.getEngineerProfile().getId());
    return engineerQualificationDTO;
  }

  private EngineerQualification mapToEntity(
      final EngineerQualificationDTO engineerQualificationDTO,
      final EngineerQualification engineerQualification) {
    engineerQualification.setExpiryDate(engineerQualificationDTO.getExpiryDate());
    engineerQualification.setExternalVerified(engineerQualificationDTO.getExternalVerified());
    engineerQualification.setIssueDate(engineerQualificationDTO.getIssueDate());
    engineerQualification.setCreatedAt(engineerQualificationDTO.getCreatedAt());
    engineerQualification.setLastApiCheckAt(engineerQualificationDTO.getLastApiCheckAt());
    engineerQualification.setUpdatedAt(engineerQualificationDTO.getUpdatedAt());
    engineerQualification.setVerifiedAt(engineerQualificationDTO.getVerifiedAt());
    engineerQualification.setVerifiedBy(engineerQualificationDTO.getVerifiedBy());
    engineerQualification.setRegistrationNumber(engineerQualificationDTO.getRegistrationNumber());
    engineerQualification.setDocumentUrl(engineerQualificationDTO.getDocumentUrl());
    engineerQualification.setSchemeName(engineerQualificationDTO.getSchemeName());
    engineerQualification.setType(engineerQualificationDTO.getType());
    engineerQualification.setVerificationStatus(engineerQualificationDTO.getVerificationStatus());
    engineerQualification.setMetadata(engineerQualificationDTO.getMetadata());
    final EngineerProfile engineerProfile =
        engineerQualificationDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerQualificationDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerQualification.setEngineerProfile(engineerProfile);
    return engineerQualification;
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

package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerInsuranceDTO;
import com.uk.certifynow.certify_now.repos.EngineerInsuranceRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EngineerInsuranceService {

  private final EngineerInsuranceRepository engineerInsuranceRepository;
  private final EngineerProfileRepository engineerProfileRepository;

  public EngineerInsuranceService(
      final EngineerInsuranceRepository engineerInsuranceRepository,
      final EngineerProfileRepository engineerProfileRepository) {
    this.engineerInsuranceRepository = engineerInsuranceRepository;
    this.engineerProfileRepository = engineerProfileRepository;
  }

  public List<EngineerInsuranceDTO> findAll() {
    final List<EngineerInsurance> engineerInsurances =
        engineerInsuranceRepository.findAll(Sort.by("id"));
    return engineerInsurances.stream()
        .map(engineerInsurance -> mapToDTO(engineerInsurance, new EngineerInsuranceDTO()))
        .toList();
  }

  public EngineerInsuranceDTO get(final UUID id) {
    return engineerInsuranceRepository
        .findById(id)
        .map(engineerInsurance -> mapToDTO(engineerInsurance, new EngineerInsuranceDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EngineerInsuranceDTO engineerInsuranceDTO) {
    final EngineerInsurance engineerInsurance = new EngineerInsurance();
    mapToEntity(engineerInsuranceDTO, engineerInsurance);
    return engineerInsuranceRepository.save(engineerInsurance).getId();
  }

  public void update(final UUID id, final EngineerInsuranceDTO engineerInsuranceDTO) {
    final EngineerInsurance engineerInsurance =
        engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(engineerInsuranceDTO, engineerInsurance);
    engineerInsuranceRepository.save(engineerInsurance);
  }

  public void delete(final UUID id) {
    final EngineerInsurance engineerInsurance =
        engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerInsuranceRepository.delete(engineerInsurance);
  }

  private EngineerInsuranceDTO mapToDTO(
      final EngineerInsurance engineerInsurance, final EngineerInsuranceDTO engineerInsuranceDTO) {
    engineerInsuranceDTO.setId(engineerInsurance.getId());
    engineerInsuranceDTO.setExpiryDate(engineerInsurance.getExpiryDate());
    engineerInsuranceDTO.setStartDate(engineerInsurance.getStartDate());
    engineerInsuranceDTO.setCoverAmountPence(engineerInsurance.getCoverAmountPence());
    engineerInsuranceDTO.setCreatedAt(engineerInsurance.getCreatedAt());
    engineerInsuranceDTO.setUpdatedAt(engineerInsurance.getUpdatedAt());
    engineerInsuranceDTO.setPolicyType(engineerInsurance.getPolicyType());
    engineerInsuranceDTO.setPolicyNumber(engineerInsurance.getPolicyNumber());
    engineerInsuranceDTO.setDocumentUrl(engineerInsurance.getDocumentUrl());
    engineerInsuranceDTO.setProvider(engineerInsurance.getProvider());
    engineerInsuranceDTO.setVerificationStatus(engineerInsurance.getVerificationStatus());
    engineerInsuranceDTO.setEngineerProfile(
        engineerInsurance.getEngineerProfile() == null
            ? null
            : engineerInsurance.getEngineerProfile().getId());
    return engineerInsuranceDTO;
  }

  private EngineerInsurance mapToEntity(
      final EngineerInsuranceDTO engineerInsuranceDTO, final EngineerInsurance engineerInsurance) {
    engineerInsurance.setExpiryDate(engineerInsuranceDTO.getExpiryDate());
    engineerInsurance.setStartDate(engineerInsuranceDTO.getStartDate());
    engineerInsurance.setCoverAmountPence(engineerInsuranceDTO.getCoverAmountPence());
    engineerInsurance.setCreatedAt(engineerInsuranceDTO.getCreatedAt());
    engineerInsurance.setUpdatedAt(engineerInsuranceDTO.getUpdatedAt());
    engineerInsurance.setPolicyType(engineerInsuranceDTO.getPolicyType());
    engineerInsurance.setPolicyNumber(engineerInsuranceDTO.getPolicyNumber());
    engineerInsurance.setDocumentUrl(engineerInsuranceDTO.getDocumentUrl());
    engineerInsurance.setProvider(engineerInsuranceDTO.getProvider());
    engineerInsurance.setVerificationStatus(engineerInsuranceDTO.getVerificationStatus());
    final EngineerProfile engineerProfile =
        engineerInsuranceDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerInsuranceDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerInsurance.setEngineerProfile(engineerProfile);
    return engineerInsurance;
  }

  @EventListener(BeforeDeleteEngineerProfile.class)
  public void on(final BeforeDeleteEngineerProfile event) {
    final ReferencedException referencedException = new ReferencedException();
    final EngineerInsurance engineerProfileEngineerInsurance =
        engineerInsuranceRepository.findFirstByEngineerProfileId(event.getId());
    if (engineerProfileEngineerInsurance != null) {
      referencedException.setKey("engineerProfile.engineerInsurance.engineerProfile.referenced");
      referencedException.addParam(engineerProfileEngineerInsurance.getId());
      throw referencedException;
    }
  }
}

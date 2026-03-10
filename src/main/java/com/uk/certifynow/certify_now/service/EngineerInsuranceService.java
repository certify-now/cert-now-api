package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerInsuranceDTO;
import com.uk.certifynow.certify_now.repos.EngineerInsuranceRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.service.mappers.EngineerInsuranceMapper;
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
public class EngineerInsuranceService {

  private final EngineerInsuranceRepository engineerInsuranceRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final EngineerInsuranceMapper engineerInsuranceMapper;

  public EngineerInsuranceService(
      final EngineerInsuranceRepository engineerInsuranceRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerInsuranceMapper engineerInsuranceMapper) {
    this.engineerInsuranceRepository = engineerInsuranceRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerInsuranceMapper = engineerInsuranceMapper;
  }

  public List<EngineerInsuranceDTO> findAll() {
    final List<EngineerInsurance> engineerInsurances =
        engineerInsuranceRepository.findAll(Sort.by("id"));
    return engineerInsurances.stream().map(engineerInsuranceMapper::toDTO).toList();
  }

  public EngineerInsuranceDTO get(final UUID id) {
    return engineerInsuranceRepository
        .findById(id)
        .map(engineerInsuranceMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final EngineerInsuranceDTO engineerInsuranceDTO) {
    final EngineerInsurance engineerInsurance = new EngineerInsurance();
    engineerInsuranceMapper.updateEntity(engineerInsuranceDTO, engineerInsurance);
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerInsuranceDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerInsuranceDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerInsurance.setEngineerProfile(engineerProfile);
    UUID savedId = engineerInsuranceRepository.save(engineerInsurance).getId();
    log.info("EngineerInsurance {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerInsuranceDTO engineerInsuranceDTO) {
    final EngineerInsurance engineerInsurance =
        engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerInsuranceMapper.updateEntity(engineerInsuranceDTO, engineerInsurance);
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerInsuranceDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerInsuranceDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerInsurance.setEngineerProfile(engineerProfile);
    engineerInsuranceRepository.save(engineerInsurance);
    log.info("EngineerInsurance {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerInsuranceRepository.deleteById(id);
    log.info("EngineerInsurance {} deleted", id);
  }

  @Transactional
  public EngineerInsuranceDTO addInsurance(
      final UUID engineerProfileId,
      final String insuranceType,
      final String provider,
      final String policyNumber,
      final java.time.LocalDate expiryDate,
      final String documentUrl) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository
            .findById(engineerProfileId)
            .orElseThrow(() -> new NotFoundException("Engineer profile not found"));
    final EngineerInsurance insurance = new EngineerInsurance();
    insurance.setEngineerProfile(engineerProfile);
    insurance.setInsuranceType(insuranceType);
    insurance.setProvider(provider);
    insurance.setPolicyNumber(policyNumber);
    insurance.setExpiryDate(expiryDate);
    insurance.setDocumentUrl(documentUrl);
    insurance.setVerified(false);
    final EngineerInsurance saved = engineerInsuranceRepository.save(insurance);
    log.info(
        "Insurance {} added for engineer {} (type={})",
        saved.getId(),
        engineerProfileId,
        insuranceType);
    return engineerInsuranceMapper.toDTO(saved);
  }

  @Transactional
  public EngineerInsuranceDTO verifyInsurance(final UUID insuranceId, final boolean verified) {
    final EngineerInsurance insurance =
        engineerInsuranceRepository
            .findById(insuranceId)
            .orElseThrow(() -> new NotFoundException("Insurance not found"));
    insurance.setVerified(verified);
    insurance.setVerifiedAt(verified ? OffsetDateTime.now() : null);
    final EngineerInsurance saved = engineerInsuranceRepository.save(insurance);
    log.info("Insurance {} verification set to {}", insuranceId, verified);
    return engineerInsuranceMapper.toDTO(saved);
  }

  public List<EngineerInsuranceDTO> getByEngineerProfile(final UUID engineerProfileId) {
    return engineerInsuranceRepository.findAllByEngineerProfileId(engineerProfileId).stream()
        .map(engineerInsuranceMapper::toDTO)
        .toList();
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

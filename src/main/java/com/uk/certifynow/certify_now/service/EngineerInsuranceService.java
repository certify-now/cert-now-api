package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerInsurance;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerInsuranceDTO;
import com.uk.certifynow.certify_now.repos.EngineerInsuranceRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.rest.dto.engineer.AddInsuranceRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.InsuranceResponse;
import com.uk.certifynow.certify_now.service.mappers.EngineerInsuranceMapper;
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
public class EngineerInsuranceService {

  private final EngineerInsuranceRepository engineerInsuranceRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final EngineerInsuranceMapper engineerInsuranceMapper;
  private final Clock clock;

  public EngineerInsuranceService(
      final EngineerInsuranceRepository engineerInsuranceRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerInsuranceMapper engineerInsuranceMapper,
      final Clock clock) {
    this.engineerInsuranceRepository = engineerInsuranceRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerInsuranceMapper = engineerInsuranceMapper;
    this.clock = clock;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

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
    resolveReferences(engineerInsuranceDTO, engineerInsurance);
    final UUID savedId = engineerInsuranceRepository.save(engineerInsurance).getId();
    log.info("EngineerInsurance {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerInsuranceDTO engineerInsuranceDTO) {
    final EngineerInsurance engineerInsurance =
        engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerInsuranceMapper.updateEntity(engineerInsuranceDTO, engineerInsurance);
    resolveReferences(engineerInsuranceDTO, engineerInsurance);
    engineerInsuranceRepository.save(engineerInsurance);
    log.info("EngineerInsurance {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final EngineerInsurance engineerInsurance =
        engineerInsuranceRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerInsuranceRepository.delete(engineerInsurance);
    log.info("EngineerInsurance {} deleted", id);
  }

  // -- New business methods (Phase 5) -----------------------------------------

  @Transactional
  public InsuranceResponse addInsurance(final UUID userId, final AddInsuranceRequest request) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final EngineerInsurance insurance = new EngineerInsurance();
    insurance.setEngineerProfile(profile);
    insurance.setPolicyType(request.policyType());
    insurance.setProvider(request.provider());
    insurance.setPolicyNumber(request.policyNumber());
    insurance.setStartDate(request.startDate());
    insurance.setExpiryDate(request.expiryDate());
    insurance.setCoverAmountPence(request.coverAmountPence());
    insurance.setDocumentUrl(request.documentUrl());
    insurance.setVerificationStatus(VerificationStatus.PENDING.name());
    insurance.setCreatedAt(now);
    insurance.setUpdatedAt(now);
    final InsuranceResponse response = toResponse(engineerInsuranceRepository.save(insurance));
    log.info(
        "Insurance {} added for engineer user {} (type={})",
        response.id(),
        userId,
        request.policyType());
    return response;
  }

  public List<InsuranceResponse> getMyInsurance(final UUID userId) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    return engineerInsuranceRepository.findAllByEngineerProfileId(profile.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public InsuranceResponse verifyInsurance(
      final UUID insuranceId, final UUID adminId, final String newStatus) {
    VerificationStatus.valueOf(newStatus);
    final EngineerInsurance insurance =
        engineerInsuranceRepository.findById(insuranceId).orElseThrow(NotFoundException::new);
    insurance.setVerificationStatus(newStatus);
    insurance.setUpdatedAt(OffsetDateTime.now(clock));
    final InsuranceResponse response = toResponse(engineerInsuranceRepository.save(insurance));
    log.info("Insurance {} verified with status={}", insuranceId, newStatus);
    return response;
  }

  // -- Mapping helpers --------------------------------------------------------

  private EngineerProfile resolveProfileByUserId(final UUID userId) {
    return engineerProfileRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("Engineer profile not found for user"));
  }

  private InsuranceResponse toResponse(final EngineerInsurance i) {
    return new InsuranceResponse(
        i.getId(),
        i.getEngineerProfile() == null ? null : i.getEngineerProfile().getId(),
        i.getPolicyType(),
        i.getProvider(),
        i.getPolicyNumber(),
        i.getStartDate(),
        i.getExpiryDate(),
        i.getCoverAmountPence(),
        i.getDocumentUrl(),
        i.getVerificationStatus(),
        i.getCreatedAt(),
        i.getUpdatedAt());
  }

  private void resolveReferences(final EngineerInsuranceDTO dto, final EngineerInsurance entity) {
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
    final EngineerInsurance engineerProfileEngineerInsurance =
        engineerInsuranceRepository.findFirstByEngineerProfileId(event.getId());
    if (engineerProfileEngineerInsurance != null) {
      referencedException.setKey("engineerProfile.engineerInsurance.engineerProfile.referenced");
      referencedException.addParam(engineerProfileEngineerInsurance.getId());
      throw referencedException;
    }
  }
}

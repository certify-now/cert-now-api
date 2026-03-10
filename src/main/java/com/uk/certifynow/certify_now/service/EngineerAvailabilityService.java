package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerAvailabilityDTO;
import com.uk.certifynow.certify_now.repos.EngineerAvailabilityRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.service.mappers.EngineerAvailabilityMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.LocalDate;
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
public class EngineerAvailabilityService {

  private final EngineerAvailabilityRepository engineerAvailabilityRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final EngineerAvailabilityMapper engineerAvailabilityMapper;

  public EngineerAvailabilityService(
      final EngineerAvailabilityRepository engineerAvailabilityRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerAvailabilityMapper engineerAvailabilityMapper) {
    this.engineerAvailabilityRepository = engineerAvailabilityRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerAvailabilityMapper = engineerAvailabilityMapper;
  }

  public List<EngineerAvailabilityDTO> findAll() {
    final List<EngineerAvailability> engineerAvailabilities =
        engineerAvailabilityRepository.findAll(Sort.by("id"));
    return engineerAvailabilities.stream().map(engineerAvailabilityMapper::toDTO).toList();
  }

  public EngineerAvailabilityDTO get(final UUID id) {
    return engineerAvailabilityRepository
        .findById(id)
        .map(engineerAvailabilityMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    final EngineerAvailability engineerAvailability = new EngineerAvailability();
    engineerAvailabilityMapper.updateEntity(engineerAvailabilityDTO, engineerAvailability);
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerAvailabilityDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerAvailabilityDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerAvailability.setEngineerProfile(engineerProfile);
    UUID savedId = engineerAvailabilityRepository.save(engineerAvailability).getId();
    log.info("EngineerAvailability {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    final EngineerAvailability engineerAvailability =
        engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerAvailabilityMapper.updateEntity(engineerAvailabilityDTO, engineerAvailability);
    // Resolve engineer profile reference from UUID
    final EngineerProfile engineerProfile =
        engineerAvailabilityDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerAvailabilityDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerAvailability.setEngineerProfile(engineerProfile);
    engineerAvailabilityRepository.save(engineerAvailability);
    log.info("EngineerAvailability {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerAvailabilityRepository.deleteById(id);
    log.info("EngineerAvailability {} deleted", id);
  }

  @Transactional
  public EngineerAvailabilityDTO setAvailability(
      final UUID engineerProfileId,
      final String dayOfWeek,
      final String startTime,
      final String endTime) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository
            .findById(engineerProfileId)
            .orElseThrow(() -> new NotFoundException("Engineer profile not found"));
    // Look for existing availability for this day
    EngineerAvailability availability =
        engineerAvailabilityRepository
            .findByEngineerProfileIdAndDayOfWeek(engineerProfileId, dayOfWeek)
            .orElseGet(
                () -> {
                  final EngineerAvailability newAvailability = new EngineerAvailability();
                  newAvailability.setEngineerProfile(engineerProfile);
                  newAvailability.setDayOfWeek(dayOfWeek);
                  return newAvailability;
                });
    availability.setStartTime(startTime);
    availability.setEndTime(endTime);
    availability.setIsAvailable(true);
    availability = engineerAvailabilityRepository.save(availability);
    log.info(
        "Availability set for engineer {} on {}: {} - {}",
        engineerProfileId,
        dayOfWeek,
        startTime,
        endTime);
    return engineerAvailabilityMapper.toDTO(availability);
  }

  @Transactional
  public EngineerAvailabilityDTO addOverride(
      final UUID engineerProfileId,
      final LocalDate date,
      final boolean isAvailable,
      final String reason) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository
            .findById(engineerProfileId)
            .orElseThrow(() -> new NotFoundException("Engineer profile not found"));
    final EngineerAvailability override = new EngineerAvailability();
    override.setEngineerProfile(engineerProfile);
    override.setOverrideDate(date);
    override.setIsAvailable(isAvailable);
    override.setOverrideReason(reason);
    override.setDayOfWeek(date.getDayOfWeek().name());
    final EngineerAvailability saved = engineerAvailabilityRepository.save(override);
    log.info(
        "Override added for engineer {} on {}: available={}", engineerProfileId, date, isAvailable);
    return engineerAvailabilityMapper.toDTO(saved);
  }

  public List<EngineerAvailabilityDTO> getWeeklySchedule(final UUID engineerProfileId) {
    return engineerAvailabilityRepository
        .findAllByEngineerProfileIdAndOverrideDateIsNull(engineerProfileId)
        .stream()
        .map(engineerAvailabilityMapper::toDTO)
        .toList();
  }

  public List<EngineerAvailabilityDTO> getOverridesInRange(
      final UUID engineerProfileId, final LocalDate from, final LocalDate to) {
    return engineerAvailabilityRepository
        .findByEngineerProfileIdAndOverrideDateBetween(engineerProfileId, from, to)
        .stream()
        .map(engineerAvailabilityMapper::toDTO)
        .toList();
  }

  public boolean isAvailableOn(final UUID engineerProfileId, final OffsetDateTime dateTime) {
    final LocalDate date = dateTime.toLocalDate();
    // Check overrides first
    final var overrides =
        engineerAvailabilityRepository.findByEngineerProfileIdAndOverrideDateBetween(
            engineerProfileId, date, date);
    if (!overrides.isEmpty()) {
      return overrides.stream().anyMatch(EngineerAvailability::getIsAvailable);
    }
    // Fall back to weekly schedule
    final String dayOfWeek = date.getDayOfWeek().name();
    return engineerAvailabilityRepository
        .findByEngineerProfileIdAndDayOfWeek(engineerProfileId, dayOfWeek)
        .map(EngineerAvailability::getIsAvailable)
        .orElse(false);
  }

  @EventListener(BeforeDeleteEngineerProfile.class)
  public void on(final BeforeDeleteEngineerProfile event) {
    final ReferencedException referencedException = new ReferencedException();
    final EngineerAvailability engineerProfileEngineerAvailability =
        engineerAvailabilityRepository.findFirstByEngineerProfileId(event.getId());
    if (engineerProfileEngineerAvailability != null) {
      referencedException.setKey("engineerProfile.engineerAvailability.engineerProfile.referenced");
      referencedException.addParam(engineerProfileEngineerAvailability.getId());
      throw referencedException;
    }
  }
}

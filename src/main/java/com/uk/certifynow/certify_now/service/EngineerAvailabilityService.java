package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerAvailabilityDTO;
import com.uk.certifynow.certify_now.repos.EngineerAvailabilityRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.rest.dto.engineer.AvailabilityOverrideRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.AvailabilityResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.AvailabilitySlotRequest;
import com.uk.certifynow.certify_now.service.mappers.EngineerAvailabilityMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private final Clock clock;

  public EngineerAvailabilityService(
      final EngineerAvailabilityRepository engineerAvailabilityRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final EngineerAvailabilityMapper engineerAvailabilityMapper,
      final Clock clock) {
    this.engineerAvailabilityRepository = engineerAvailabilityRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.engineerAvailabilityMapper = engineerAvailabilityMapper;
    this.clock = clock;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

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
    resolveReferences(engineerAvailabilityDTO, engineerAvailability);
    final UUID savedId = engineerAvailabilityRepository.save(engineerAvailability).getId();
    log.info("EngineerAvailability {} created", savedId);
    return savedId;
  }

  @Transactional
  public void update(final UUID id, final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    final EngineerAvailability engineerAvailability =
        engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerAvailabilityMapper.updateEntity(engineerAvailabilityDTO, engineerAvailability);
    resolveReferences(engineerAvailabilityDTO, engineerAvailability);
    engineerAvailabilityRepository.save(engineerAvailability);
    log.info("EngineerAvailability {} updated", id);
  }

  @Transactional
  public void delete(final UUID id) {
    final EngineerAvailability engineerAvailability =
        engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerAvailabilityRepository.delete(engineerAvailability);
    log.info("EngineerAvailability {} deleted", id);
  }

  // -- New business methods (Phase 5) -----------------------------------------

  @Transactional
  public List<AvailabilityResponse> setAvailability(
      final UUID userId, final List<AvailabilitySlotRequest> slots) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    engineerAvailabilityRepository.deleteAllByEngineerProfileIdAndIsRecurring(
        profile.getId(), true);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final List<EngineerAvailability> newSlots = new ArrayList<>();
    for (final AvailabilitySlotRequest slot : slots) {
      final EngineerAvailability availability = new EngineerAvailability();
      availability.setEngineerProfile(profile);
      availability.setDayOfWeek(slot.dayOfWeek());
      availability.setStartTime(slot.startTime());
      availability.setEndTime(slot.endTime());
      availability.setIsAvailable(slot.isAvailable());
      availability.setIsRecurring(true);
      availability.setCreatedAt(now);
      availability.setUpdatedAt(now);
      newSlots.add(availability);
    }
    final List<AvailabilityResponse> result =
        engineerAvailabilityRepository.saveAll(newSlots).stream().map(this::toResponse).toList();
    log.info("Availability set for engineer user {} ({} slots)", userId, result.size());
    return result;
  }

  @Transactional
  public AvailabilityResponse addOverride(
      final UUID userId, final AvailabilityOverrideRequest request) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    final EngineerAvailability override = new EngineerAvailability();
    override.setEngineerProfile(profile);
    override.setDayOfWeek(request.overrideDate().getDayOfWeek().getValue());
    override.setStartTime(request.startTime());
    override.setEndTime(request.endTime());
    override.setIsAvailable(request.isAvailable());
    override.setIsRecurring(false);
    override.setOverrideDate(request.overrideDate());
    override.setCreatedAt(now);
    override.setUpdatedAt(now);
    final AvailabilityResponse response = toResponse(engineerAvailabilityRepository.save(override));
    log.info(
        "Availability override {} added for engineer user {} on {}",
        response.id(),
        userId,
        request.overrideDate());
    return response;
  }

  public List<AvailabilityResponse> getAvailableSlots(final UUID profileId, final LocalDate date) {
    final int dayOfWeek = date.getDayOfWeek().getValue();
    final List<EngineerAvailability> recurring =
        engineerAvailabilityRepository
            .findAllByEngineerProfileIdAndIsRecurring(profileId, true)
            .stream()
            .filter(a -> a.getDayOfWeek() == dayOfWeek)
            .toList();
    final List<EngineerAvailability> overrides =
        engineerAvailabilityRepository.findAllByEngineerProfileIdAndOverrideDate(profileId, date);
    // Overlay overrides on recurring: overrides replace recurring slots for matching times
    final Map<String, EngineerAvailability> overrideMap =
        overrides.stream()
            .collect(
                Collectors.toMap(
                    o -> o.getStartTime() + "-" + o.getEndTime(),
                    Function.identity(),
                    (a, b) -> b));
    final List<AvailabilityResponse> result = new ArrayList<>();
    for (final EngineerAvailability slot : recurring) {
      final String key = slot.getStartTime() + "-" + slot.getEndTime();
      if (overrideMap.containsKey(key)) {
        result.add(toResponse(overrideMap.remove(key)));
      } else {
        result.add(toResponse(slot));
      }
    }
    // Add remaining overrides that don't match any recurring slot
    for (final EngineerAvailability o : overrideMap.values()) {
      result.add(toResponse(o));
    }
    return result;
  }

  // -- Mapping helpers --------------------------------------------------------

  private EngineerProfile resolveProfileByUserId(final UUID userId) {
    return engineerProfileRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("Engineer profile not found for user"));
  }

  private AvailabilityResponse toResponse(final EngineerAvailability a) {
    return new AvailabilityResponse(
        a.getId(),
        a.getEngineerProfile() == null ? null : a.getEngineerProfile().getId(),
        a.getDayOfWeek(),
        a.getStartTime(),
        a.getEndTime(),
        a.getIsAvailable(),
        a.getIsRecurring(),
        a.getOverrideDate(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }

  private void resolveReferences(
      final EngineerAvailabilityDTO dto, final EngineerAvailability entity) {
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
    final EngineerAvailability engineerProfileEngineerAvailability =
        engineerAvailabilityRepository.findFirstByEngineerProfileId(event.getId());
    if (engineerProfileEngineerAvailability != null) {
      referencedException.setKey("engineerProfile.engineerAvailability.engineerProfile.referenced");
      referencedException.addParam(engineerProfileEngineerAvailability.getId());
      throw referencedException;
    }
  }
}

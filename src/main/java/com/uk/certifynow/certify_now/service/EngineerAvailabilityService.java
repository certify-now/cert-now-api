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
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineerAvailabilityService {

  private final EngineerAvailabilityRepository engineerAvailabilityRepository;
  private final EngineerProfileRepository engineerProfileRepository;
  private final Clock clock;

  public EngineerAvailabilityService(
      final EngineerAvailabilityRepository engineerAvailabilityRepository,
      final EngineerProfileRepository engineerProfileRepository,
      final Clock clock) {
    this.engineerAvailabilityRepository = engineerAvailabilityRepository;
    this.engineerProfileRepository = engineerProfileRepository;
    this.clock = clock;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

  public List<EngineerAvailabilityDTO> findAll() {
    final List<EngineerAvailability> engineerAvailabilities =
        engineerAvailabilityRepository.findAll(Sort.by("id"));
    return engineerAvailabilities.stream()
        .map(engineerAvailability -> mapToDTO(engineerAvailability, new EngineerAvailabilityDTO()))
        .toList();
  }

  public EngineerAvailabilityDTO get(final UUID id) {
    return engineerAvailabilityRepository
        .findById(id)
        .map(engineerAvailability -> mapToDTO(engineerAvailability, new EngineerAvailabilityDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    final EngineerAvailability engineerAvailability = new EngineerAvailability();
    mapToEntity(engineerAvailabilityDTO, engineerAvailability);
    return engineerAvailabilityRepository.save(engineerAvailability).getId();
  }

  public void update(final UUID id, final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    final EngineerAvailability engineerAvailability =
        engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(engineerAvailabilityDTO, engineerAvailability);
    engineerAvailabilityRepository.save(engineerAvailability);
  }

  public void delete(final UUID id) {
    final EngineerAvailability engineerAvailability =
        engineerAvailabilityRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerAvailabilityRepository.delete(engineerAvailability);
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
    return engineerAvailabilityRepository.saveAll(newSlots).stream().map(this::toResponse).toList();
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
    return toResponse(engineerAvailabilityRepository.save(override));
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

  // -- Existing DTO mapping (kept for backward compat) ------------------------

  private EngineerAvailabilityDTO mapToDTO(
      final EngineerAvailability engineerAvailability,
      final EngineerAvailabilityDTO engineerAvailabilityDTO) {
    engineerAvailabilityDTO.setId(engineerAvailability.getId());
    engineerAvailabilityDTO.setDayOfWeek(engineerAvailability.getDayOfWeek());
    engineerAvailabilityDTO.setEndTime(engineerAvailability.getEndTime());
    engineerAvailabilityDTO.setIsAvailable(engineerAvailability.getIsAvailable());
    engineerAvailabilityDTO.setIsRecurring(engineerAvailability.getIsRecurring());
    engineerAvailabilityDTO.setOverrideDate(engineerAvailability.getOverrideDate());
    engineerAvailabilityDTO.setStartTime(engineerAvailability.getStartTime());
    engineerAvailabilityDTO.setCreatedAt(engineerAvailability.getCreatedAt());
    engineerAvailabilityDTO.setUpdatedAt(engineerAvailability.getUpdatedAt());
    engineerAvailabilityDTO.setEngineerProfile(
        engineerAvailability.getEngineerProfile() == null
            ? null
            : engineerAvailability.getEngineerProfile().getId());
    return engineerAvailabilityDTO;
  }

  private EngineerAvailability mapToEntity(
      final EngineerAvailabilityDTO engineerAvailabilityDTO,
      final EngineerAvailability engineerAvailability) {
    engineerAvailability.setDayOfWeek(engineerAvailabilityDTO.getDayOfWeek());
    engineerAvailability.setEndTime(engineerAvailabilityDTO.getEndTime());
    engineerAvailability.setIsAvailable(engineerAvailabilityDTO.getIsAvailable());
    engineerAvailability.setIsRecurring(engineerAvailabilityDTO.getIsRecurring());
    engineerAvailability.setOverrideDate(engineerAvailabilityDTO.getOverrideDate());
    engineerAvailability.setStartTime(engineerAvailabilityDTO.getStartTime());
    engineerAvailability.setCreatedAt(engineerAvailabilityDTO.getCreatedAt());
    engineerAvailability.setUpdatedAt(engineerAvailabilityDTO.getUpdatedAt());
    final EngineerProfile engineerProfile =
        engineerAvailabilityDTO.getEngineerProfile() == null
            ? null
            : engineerProfileRepository
                .findById(engineerAvailabilityDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
    engineerAvailability.setEngineerProfile(engineerProfile);
    return engineerAvailability;
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

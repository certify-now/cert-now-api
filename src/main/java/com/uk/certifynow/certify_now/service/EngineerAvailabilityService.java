package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerAvailability;
import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.model.EngineerAvailabilityDTO;
import com.uk.certifynow.certify_now.repos.EngineerAvailabilityRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class EngineerAvailabilityService {

    private final EngineerAvailabilityRepository engineerAvailabilityRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    public EngineerAvailabilityService(
            final EngineerAvailabilityRepository engineerAvailabilityRepository,
            final EngineerProfileRepository engineerProfileRepository) {
        this.engineerAvailabilityRepository = engineerAvailabilityRepository;
        this.engineerProfileRepository = engineerProfileRepository;
    }

    public List<EngineerAvailabilityDTO> findAll() {
        final List<EngineerAvailability> engineerAvailabilities = engineerAvailabilityRepository.findAll(Sort.by("id"));
        return engineerAvailabilities.stream()
                .map(engineerAvailability -> mapToDTO(engineerAvailability, new EngineerAvailabilityDTO()))
                .toList();
    }

    public EngineerAvailabilityDTO get(final UUID id) {
        return engineerAvailabilityRepository.findById(id)
                .map(engineerAvailability -> mapToDTO(engineerAvailability, new EngineerAvailabilityDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final EngineerAvailabilityDTO engineerAvailabilityDTO) {
        final EngineerAvailability engineerAvailability = new EngineerAvailability();
        mapToEntity(engineerAvailabilityDTO, engineerAvailability);
        return engineerAvailabilityRepository.save(engineerAvailability).getId();
    }

    public void update(final UUID id, final EngineerAvailabilityDTO engineerAvailabilityDTO) {
        final EngineerAvailability engineerAvailability = engineerAvailabilityRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(engineerAvailabilityDTO, engineerAvailability);
        engineerAvailabilityRepository.save(engineerAvailability);
    }

    public void delete(final UUID id) {
        final EngineerAvailability engineerAvailability = engineerAvailabilityRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        engineerAvailabilityRepository.delete(engineerAvailability);
    }

    private EngineerAvailabilityDTO mapToDTO(final EngineerAvailability engineerAvailability,
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
        engineerAvailabilityDTO.setEngineerProfile(engineerAvailability.getEngineerProfile() == null ? null : engineerAvailability.getEngineerProfile().getId());
        return engineerAvailabilityDTO;
    }

    private EngineerAvailability mapToEntity(final EngineerAvailabilityDTO engineerAvailabilityDTO,
            final EngineerAvailability engineerAvailability) {
        engineerAvailability.setDayOfWeek(engineerAvailabilityDTO.getDayOfWeek());
        engineerAvailability.setEndTime(engineerAvailabilityDTO.getEndTime());
        engineerAvailability.setIsAvailable(engineerAvailabilityDTO.getIsAvailable());
        engineerAvailability.setIsRecurring(engineerAvailabilityDTO.getIsRecurring());
        engineerAvailability.setOverrideDate(engineerAvailabilityDTO.getOverrideDate());
        engineerAvailability.setStartTime(engineerAvailabilityDTO.getStartTime());
        engineerAvailability.setCreatedAt(engineerAvailabilityDTO.getCreatedAt());
        engineerAvailability.setUpdatedAt(engineerAvailabilityDTO.getUpdatedAt());
        final EngineerProfile engineerProfile = engineerAvailabilityDTO.getEngineerProfile() == null ? null : engineerProfileRepository.findById(engineerAvailabilityDTO.getEngineerProfile())
                .orElseThrow(() -> new NotFoundException("engineerProfile not found"));
        engineerAvailability.setEngineerProfile(engineerProfile);
        return engineerAvailability;
    }

    @EventListener(BeforeDeleteEngineerProfile.class)
    public void on(final BeforeDeleteEngineerProfile event) {
        final ReferencedException referencedException = new ReferencedException();
        final EngineerAvailability engineerProfileEngineerAvailability = engineerAvailabilityRepository.findFirstByEngineerProfileId(event.getId());
        if (engineerProfileEngineerAvailability != null) {
            referencedException.setKey("engineerProfile.engineerAvailability.engineerProfile.referenced");
            referencedException.addParam(engineerProfileEngineerAvailability.getId());
            throw referencedException;
        }
    }

}

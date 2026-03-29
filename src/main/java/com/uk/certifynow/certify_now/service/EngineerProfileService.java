package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.events.EngineerApprovedEvent;
import com.uk.certifynow.certify_now.events.EngineerWentOnlineEvent;
import com.uk.certifynow.certify_now.exception.InvalidStateTransitionException;
import com.uk.certifynow.certify_now.model.EngineerProfileDTO;
import com.uk.certifynow.certify_now.repos.EngineerInsuranceRepository;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.EngineerQualificationRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.rest.dto.engineer.EngineerProfileResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.UpdateEngineerProfileRequest;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.service.mappers.EngineerProfileMapper;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EngineerProfileService {

  private final EngineerProfileRepository engineerProfileRepository;
  private final UserRepository userRepository;
  private final EngineerQualificationRepository engineerQualificationRepository;
  private final EngineerInsuranceRepository engineerInsuranceRepository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final EngineerProfileMapper engineerProfileMapper;

  public EngineerProfileService(
      final EngineerProfileRepository engineerProfileRepository,
      final UserRepository userRepository,
      final EngineerQualificationRepository engineerQualificationRepository,
      final EngineerInsuranceRepository engineerInsuranceRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock,
      final EngineerProfileMapper engineerProfileMapper) {
    this.engineerProfileRepository = engineerProfileRepository;
    this.userRepository = userRepository;
    this.engineerQualificationRepository = engineerQualificationRepository;
    this.engineerInsuranceRepository = engineerInsuranceRepository;
    this.publisher = publisher;
    this.clock = clock;
    this.engineerProfileMapper = engineerProfileMapper;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

  public List<EngineerProfileDTO> findAll() {
    final List<EngineerProfile> engineerProfiles = engineerProfileRepository.findAll(Sort.by("id"));
    return engineerProfiles.stream().map(engineerProfileMapper::toDTO).toList();
  }

  @Transactional(readOnly = true)
  public Page<EngineerProfileResponse> findAllPaginated(final Pageable pageable) {
    final Page<EngineerProfile> page = engineerProfileRepository.findAll(pageable);
    final List<UUID> profileIds = page.getContent().stream().map(EngineerProfile::getId).toList();

    final Map<UUID, Integer> qualificationsCounts =
        engineerQualificationRepository.countByEngineerProfileIds(profileIds).stream()
            .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Long) row[1]).intValue()));

    final Map<UUID, Integer> insuranceCounts =
        engineerInsuranceRepository.countByEngineerProfileIds(profileIds).stream()
            .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Long) row[1]).intValue()));

    return page.map(
        profile ->
            toResponse(
                profile,
                qualificationsCounts.getOrDefault(profile.getId(), 0),
                insuranceCounts.getOrDefault(profile.getId(), 0)));
  }

  @Cacheable(value = "engineer-profiles", key = "#profileId")
  @Transactional(readOnly = true)
  public EngineerProfileResponse getProfile(final UUID profileId) {
    final EngineerProfile profile =
        engineerProfileRepository.findById(profileId).orElseThrow(NotFoundException::new);
    return toResponse(profile);
  }

  public EngineerProfileDTO get(final UUID id) {
    return engineerProfileRepository
        .findById(id)
        .map(engineerProfileMapper::toDTO)
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  public UUID create(final EngineerProfileDTO engineerProfileDTO) {
    final EngineerProfile engineerProfile = new EngineerProfile();
    engineerProfileMapper.updateEntity(engineerProfileDTO, engineerProfile);
    // Resolve user reference from UUID
    final User user =
        engineerProfileDTO.getUser() == null
            ? null
            : userRepository
                .findById(engineerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    engineerProfile.setUser(user);
    UUID savedId = engineerProfileRepository.save(engineerProfile).getId();
    log.info("EngineerProfile {} created for user {}", savedId, engineerProfileDTO.getUser());
    return savedId;
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public void update(final UUID id, final EngineerProfileDTO engineerProfileDTO) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    engineerProfileMapper.updateEntity(engineerProfileDTO, engineerProfile);
    // Resolve user reference from UUID
    final User user =
        engineerProfileDTO.getUser() == null
            ? null
            : userRepository
                .findById(engineerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    engineerProfile.setUser(user);
    engineerProfileRepository.save(engineerProfile);
    log.info("EngineerProfile {} updated", id);
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public void delete(final UUID id) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteEngineerProfile(id));
    engineerProfileRepository.delete(engineerProfile);
    log.info("EngineerProfile {} deleted", id);
  }

  // -- New business methods (Phase 5) -----------------------------------------

  @Cacheable(value = "engineer-profiles", key = "'user-' + #userId")
  public EngineerProfileResponse getMyProfile(final UUID userId) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    return toResponse(profile);
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public EngineerProfileResponse updateProfile(
      final UUID userId, final UpdateEngineerProfileRequest request) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    if (request.bio() != null) {
      profile.setBio(request.bio());
    }
    if (request.preferredCertTypes() != null) {
      profile.setPreferredCertTypes(request.preferredCertTypes());
    }
    if (request.preferredJobTimes() != null) {
      profile.setPreferredJobTimes(request.preferredJobTimes());
    }
    if (request.serviceRadiusMiles() != null) {
      profile.setServiceRadiusMiles(request.serviceRadiusMiles());
    }
    if (request.maxDailyJobs() != null) {
      profile.setMaxDailyJobs(request.maxDailyJobs());
    }
    profile.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Engineer profile updated for user {}", userId);
    return toResponse(engineerProfileRepository.save(profile));
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public void updateLocation(final UUID userId, final double latitude, final double longitude) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    profile.setLocation(latitude + "," + longitude);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    profile.setLocationUpdatedAt(now);
    profile.setUpdatedAt(now);
    engineerProfileRepository.save(profile);
    if (log.isDebugEnabled()) {
      log.debug("Location updated for user {} to ({}, {})", userId, latitude, longitude);
    }
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public void setOnlineStatus(final UUID userId, final boolean online) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    if (online && !profile.getStatus().isApproved()) {
      log.warn(
          "User {} attempted to go online but is not approved (status: {})",
          userId,
          profile.getStatus());
      throw new InvalidStateTransitionException(
          "Engineer must be APPROVED before going online, current status: " + profile.getStatus());
    }
    profile.setIsOnline(online);
    profile.setUpdatedAt(OffsetDateTime.now(clock));
    engineerProfileRepository.save(profile);
    log.info("Engineer {} online status set to {}", userId, online);
    if (online) {
      publisher.publishEvent(
          new EngineerWentOnlineEvent(profile.getUser().getId(), profile.getId()));
    }
  }

  @CacheEvict(value = "engineer-profiles", allEntries = true)
  @Transactional
  public EngineerProfileResponse transitionStatus(
      final UUID profileId, final EngineerApplicationStatus targetStatus, final UUID adminId) {
    final EngineerProfile profile =
        engineerProfileRepository.findById(profileId).orElseThrow(NotFoundException::new);
    final EngineerApplicationStatus currentStatus = profile.getStatus();
    if (!currentStatus.canTransitionTo(targetStatus)) {
      log.warn(
          "Invalid status transition for profile {}: {} -> {}",
          profileId,
          currentStatus,
          targetStatus);
      throw new InvalidStateTransitionException(
          "Cannot transition from " + currentStatus + " to " + targetStatus);
    }
    profile.setStatus(targetStatus);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    profile.setUpdatedAt(now);
    if (targetStatus == EngineerApplicationStatus.APPROVED) {
      profile.setApprovedAt(now);
      final User user = profile.getUser();
      user.setStatus(UserStatus.ACTIVE);
      user.setUpdatedAt(now);
      userRepository.save(user);
      log.info("Engineer profile {} approved by admin {}", profileId, adminId);
      publisher.publishEvent(
          new EngineerApprovedEvent(user.getId(), profile.getId(), adminId, now));
    } else {
      log.info(
          "Engineer profile {} transitioned from {} to {}", profileId, currentStatus, targetStatus);
    }
    return toResponse(engineerProfileRepository.save(profile));
  }

  public void recalculateStats(final UUID profileId) {
    log.info("recalculateStats not yet implemented for profileId={}", profileId);
  }

  // -- Mapping helpers --------------------------------------------------------

  private EngineerProfile resolveProfileByUserId(final UUID userId) {
    return engineerProfileRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("Engineer profile not found for user"));
  }

  private EngineerProfileResponse toResponse(final EngineerProfile profile) {
    final int qualificationsCount =
        (int) engineerQualificationRepository.countByEngineerProfileId(profile.getId());
    final int insuranceCount =
        (int) engineerInsuranceRepository.countByEngineerProfileId(profile.getId());
    return toResponse(profile, qualificationsCount, insuranceCount);
  }

  private EngineerProfileResponse toResponse(
      final EngineerProfile profile, final int qualificationsCount, final int insuranceCount) {
    final String availabilitySummary = profile.getIsOnline() ? "Online" : "Offline";
    return new EngineerProfileResponse(
        profile.getId(),
        profile.getUser() == null ? null : profile.getUser().getId(),
        profile.getStatus().name(),
        profile.getTier().name(),
        profile.getBio(),
        profile.getPreferredCertTypes(),
        profile.getPreferredJobTimes(),
        profile.getServiceRadiusMiles(),
        profile.getMaxDailyJobs(),
        profile.getIsOnline(),
        profile.getAcceptanceRate(),
        profile.getAvgRating(),
        profile.getOnTimePercentage(),
        profile.getTotalJobsCompleted(),
        profile.getTotalReviews(),
        profile.getStripeOnboarded(),
        profile.getLocation(),
        profile.getApprovedAt(),
        profile.getLocationUpdatedAt(),
        profile.getCreatedAt(),
        profile.getUpdatedAt(),
        qualificationsCount,
        insuranceCount,
        availabilitySummary);
  }

  @EventListener(BeforeDeleteUser.class)
  public void on(final BeforeDeleteUser event) {
    final ReferencedException referencedException = new ReferencedException();
    final EngineerProfile userEngineerProfile =
        engineerProfileRepository.findFirstByUserId(event.getId());
    if (userEngineerProfile != null) {
      referencedException.setKey("user.engineerProfile.user.referenced");
      referencedException.addParam(userEngineerProfile.getId());
      throw referencedException;
    }
  }
}

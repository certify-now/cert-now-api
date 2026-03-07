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
import com.uk.certifynow.certify_now.service.auth.EngineerTier;
import com.uk.certifynow.certify_now.service.auth.UserStatus;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineerProfileService {

  private static final Logger log = LoggerFactory.getLogger(EngineerProfileService.class);

  private final EngineerProfileRepository engineerProfileRepository;
  private final UserRepository userRepository;
  private final EngineerQualificationRepository engineerQualificationRepository;
  private final EngineerInsuranceRepository engineerInsuranceRepository;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;

  public EngineerProfileService(
      final EngineerProfileRepository engineerProfileRepository,
      final UserRepository userRepository,
      final EngineerQualificationRepository engineerQualificationRepository,
      final EngineerInsuranceRepository engineerInsuranceRepository,
      final ApplicationEventPublisher publisher,
      final Clock clock) {
    this.engineerProfileRepository = engineerProfileRepository;
    this.userRepository = userRepository;
    this.engineerQualificationRepository = engineerQualificationRepository;
    this.engineerInsuranceRepository = engineerInsuranceRepository;
    this.publisher = publisher;
    this.clock = clock;
  }

  // -- Existing generic CRUD methods (kept for backward compat) ---------------

  public List<EngineerProfileDTO> findAll() {
    final List<EngineerProfile> engineerProfiles = engineerProfileRepository.findAll(Sort.by("id"));
    return engineerProfiles.stream()
        .map(engineerProfile -> mapToDTO(engineerProfile, new EngineerProfileDTO()))
        .toList();
  }

  public EngineerProfileDTO get(final UUID id) {
    return engineerProfileRepository
        .findById(id)
        .map(engineerProfile -> mapToDTO(engineerProfile, new EngineerProfileDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final EngineerProfileDTO engineerProfileDTO) {
    final EngineerProfile engineerProfile = new EngineerProfile();
    mapToEntity(engineerProfileDTO, engineerProfile);
    return engineerProfileRepository.save(engineerProfile).getId();
  }

  public void update(final UUID id, final EngineerProfileDTO engineerProfileDTO) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(engineerProfileDTO, engineerProfile);
    engineerProfileRepository.save(engineerProfile);
  }

  public void delete(final UUID id) {
    final EngineerProfile engineerProfile =
        engineerProfileRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeleteEngineerProfile(id));
    engineerProfileRepository.delete(engineerProfile);
  }

  // -- New business methods (Phase 5) -----------------------------------------

  public EngineerProfileResponse getMyProfile(final UUID userId) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    return toResponse(profile);
  }

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
    return toResponse(engineerProfileRepository.save(profile));
  }

  @Transactional
  public void updateLocation(final UUID userId, final double latitude, final double longitude) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    profile.setLocation(latitude + "," + longitude);
    final OffsetDateTime now = OffsetDateTime.now(clock);
    profile.setLocationUpdatedAt(now);
    profile.setUpdatedAt(now);
    engineerProfileRepository.save(profile);
  }

  @Transactional
  public void setOnlineStatus(final UUID userId, final boolean online) {
    final EngineerProfile profile = resolveProfileByUserId(userId);
    if (online && !profile.getStatus().isApproved()) {
      throw new InvalidStateTransitionException(
          "Engineer must be APPROVED before going online, current status: " + profile.getStatus());
    }
    profile.setIsOnline(online);
    profile.setUpdatedAt(OffsetDateTime.now(clock));
    engineerProfileRepository.save(profile);
    if (online) {
      publisher.publishEvent(
          new EngineerWentOnlineEvent(profile.getUser().getId(), profile.getId()));
    }
  }

  @Transactional
  public EngineerProfileResponse transitionStatus(
      final UUID profileId, final EngineerApplicationStatus targetStatus, final UUID adminId) {
    final EngineerProfile profile =
        engineerProfileRepository.findById(profileId).orElseThrow(NotFoundException::new);
    final EngineerApplicationStatus currentStatus = profile.getStatus();
    if (!currentStatus.canTransitionTo(targetStatus)) {
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
      publisher.publishEvent(
          new EngineerApprovedEvent(user.getId(), profile.getId(), adminId, now));
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
        engineerQualificationRepository.findAllByEngineerProfileId(profile.getId()).size();
    final int insuranceCount =
        engineerInsuranceRepository.findAllByEngineerProfileId(profile.getId()).size();
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

  // -- Existing DTO mapping (kept for backward compat) ------------------------

  private EngineerProfileDTO mapToDTO(
      final EngineerProfile engineerProfile, final EngineerProfileDTO engineerProfileDTO) {
    engineerProfileDTO.setId(engineerProfile.getId());
    engineerProfileDTO.setAcceptanceRate(engineerProfile.getAcceptanceRate());
    engineerProfileDTO.setAvgRating(engineerProfile.getAvgRating());
    engineerProfileDTO.setIsOnline(engineerProfile.getIsOnline());
    engineerProfileDTO.setMaxDailyJobs(engineerProfile.getMaxDailyJobs());
    engineerProfileDTO.setOnTimePercentage(engineerProfile.getOnTimePercentage());
    engineerProfileDTO.setServiceRadiusMiles(engineerProfile.getServiceRadiusMiles());
    engineerProfileDTO.setStripeOnboarded(engineerProfile.getStripeOnboarded());
    engineerProfileDTO.setTotalJobsCompleted(engineerProfile.getTotalJobsCompleted());
    engineerProfileDTO.setTotalReviews(engineerProfile.getTotalReviews());
    engineerProfileDTO.setApprovedAt(engineerProfile.getApprovedAt());
    engineerProfileDTO.setCreatedAt(engineerProfile.getCreatedAt());
    engineerProfileDTO.setDbsCheckedAt(engineerProfile.getDbsCheckedAt());
    engineerProfileDTO.setIdVerifiedAt(engineerProfile.getIdVerifiedAt());
    engineerProfileDTO.setInsuranceVerifiedAt(engineerProfile.getInsuranceVerifiedAt());
    engineerProfileDTO.setLocationUpdatedAt(engineerProfile.getLocationUpdatedAt());
    engineerProfileDTO.setTrainingCompletedAt(engineerProfile.getTrainingCompletedAt());
    engineerProfileDTO.setUpdatedAt(engineerProfile.getUpdatedAt());
    engineerProfileDTO.setDbsCertificateNumber(engineerProfile.getDbsCertificateNumber());
    engineerProfileDTO.setDbsStatus(engineerProfile.getDbsStatus());
    engineerProfileDTO.setBio(engineerProfile.getBio());
    engineerProfileDTO.setStatus(engineerProfile.getStatus().name());
    engineerProfileDTO.setStripeAccountId(engineerProfile.getStripeAccountId());
    engineerProfileDTO.setTier(engineerProfile.getTier().name());
    engineerProfileDTO.setLocation(engineerProfile.getLocation());
    engineerProfileDTO.setPreferredCertTypes(engineerProfile.getPreferredCertTypes());
    engineerProfileDTO.setPreferredJobTimes(engineerProfile.getPreferredJobTimes());
    engineerProfileDTO.setUser(
        engineerProfile.getUser() == null ? null : engineerProfile.getUser().getId());
    return engineerProfileDTO;
  }

  private EngineerProfile mapToEntity(
      final EngineerProfileDTO engineerProfileDTO, final EngineerProfile engineerProfile) {
    engineerProfile.setAcceptanceRate(engineerProfileDTO.getAcceptanceRate());
    engineerProfile.setAvgRating(engineerProfileDTO.getAvgRating());
    engineerProfile.setIsOnline(engineerProfileDTO.getIsOnline());
    engineerProfile.setMaxDailyJobs(engineerProfileDTO.getMaxDailyJobs());
    engineerProfile.setOnTimePercentage(engineerProfileDTO.getOnTimePercentage());
    engineerProfile.setServiceRadiusMiles(engineerProfileDTO.getServiceRadiusMiles());
    engineerProfile.setStripeOnboarded(engineerProfileDTO.getStripeOnboarded());
    engineerProfile.setTotalJobsCompleted(engineerProfileDTO.getTotalJobsCompleted());
    engineerProfile.setTotalReviews(engineerProfileDTO.getTotalReviews());
    engineerProfile.setApprovedAt(engineerProfileDTO.getApprovedAt());
    engineerProfile.setCreatedAt(engineerProfileDTO.getCreatedAt());
    engineerProfile.setDbsCheckedAt(engineerProfileDTO.getDbsCheckedAt());
    engineerProfile.setIdVerifiedAt(engineerProfileDTO.getIdVerifiedAt());
    engineerProfile.setInsuranceVerifiedAt(engineerProfileDTO.getInsuranceVerifiedAt());
    engineerProfile.setLocationUpdatedAt(engineerProfileDTO.getLocationUpdatedAt());
    engineerProfile.setTrainingCompletedAt(engineerProfileDTO.getTrainingCompletedAt());
    engineerProfile.setUpdatedAt(engineerProfileDTO.getUpdatedAt());
    engineerProfile.setDbsCertificateNumber(engineerProfileDTO.getDbsCertificateNumber());
    engineerProfile.setDbsStatus(engineerProfileDTO.getDbsStatus());
    engineerProfile.setBio(engineerProfileDTO.getBio());
    engineerProfile.setStatus(EngineerApplicationStatus.valueOf(engineerProfileDTO.getStatus()));
    engineerProfile.setStripeAccountId(engineerProfileDTO.getStripeAccountId());
    engineerProfile.setTier(EngineerTier.valueOf(engineerProfileDTO.getTier()));
    engineerProfile.setLocation(engineerProfileDTO.getLocation());
    engineerProfile.setPreferredCertTypes(engineerProfileDTO.getPreferredCertTypes());
    engineerProfile.setPreferredJobTimes(engineerProfileDTO.getPreferredJobTimes());
    final User user =
        engineerProfileDTO.getUser() == null
            ? null
            : userRepository
                .findById(engineerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
    engineerProfile.setUser(user);
    return engineerProfile;
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

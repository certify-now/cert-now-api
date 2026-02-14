package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.EngineerProfile;
import com.uk.certifynow.certify_now.domain.User;
import com.uk.certifynow.certify_now.events.BeforeDeleteEngineerProfile;
import com.uk.certifynow.certify_now.events.BeforeDeleteUser;
import com.uk.certifynow.certify_now.model.EngineerProfileDTO;
import com.uk.certifynow.certify_now.repos.EngineerProfileRepository;
import com.uk.certifynow.certify_now.repos.UserRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class EngineerProfileService {

    private final EngineerProfileRepository engineerProfileRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher publisher;

    public EngineerProfileService(final EngineerProfileRepository engineerProfileRepository,
            final UserRepository userRepository, final ApplicationEventPublisher publisher) {
        this.engineerProfileRepository = engineerProfileRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
    }

    public List<EngineerProfileDTO> findAll() {
        final List<EngineerProfile> engineerProfiles = engineerProfileRepository.findAll(Sort.by("id"));
        return engineerProfiles.stream()
                .map(engineerProfile -> mapToDTO(engineerProfile, new EngineerProfileDTO()))
                .toList();
    }

    public EngineerProfileDTO get(final UUID id) {
        return engineerProfileRepository.findById(id)
                .map(engineerProfile -> mapToDTO(engineerProfile, new EngineerProfileDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final EngineerProfileDTO engineerProfileDTO) {
        final EngineerProfile engineerProfile = new EngineerProfile();
        mapToEntity(engineerProfileDTO, engineerProfile);
        return engineerProfileRepository.save(engineerProfile).getId();
    }

    public void update(final UUID id, final EngineerProfileDTO engineerProfileDTO) {
        final EngineerProfile engineerProfile = engineerProfileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(engineerProfileDTO, engineerProfile);
        engineerProfileRepository.save(engineerProfile);
    }

    public void delete(final UUID id) {
        final EngineerProfile engineerProfile = engineerProfileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        publisher.publishEvent(new BeforeDeleteEngineerProfile(id));
        engineerProfileRepository.delete(engineerProfile);
    }

    private EngineerProfileDTO mapToDTO(final EngineerProfile engineerProfile,
            final EngineerProfileDTO engineerProfileDTO) {
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
        engineerProfileDTO.setStatus(engineerProfile.getStatus());
        engineerProfileDTO.setStripeAccountId(engineerProfile.getStripeAccountId());
        engineerProfileDTO.setTier(engineerProfile.getTier());
        engineerProfileDTO.setLocation(engineerProfile.getLocation());
        engineerProfileDTO.setPreferredCertTypes(engineerProfile.getPreferredCertTypes());
        engineerProfileDTO.setPreferredJobTimes(engineerProfile.getPreferredJobTimes());
        engineerProfileDTO.setUser(engineerProfile.getUser() == null ? null : engineerProfile.getUser().getId());
        return engineerProfileDTO;
    }

    private EngineerProfile mapToEntity(final EngineerProfileDTO engineerProfileDTO,
            final EngineerProfile engineerProfile) {
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
        engineerProfile.setStatus(engineerProfileDTO.getStatus());
        engineerProfile.setStripeAccountId(engineerProfileDTO.getStripeAccountId());
        engineerProfile.setTier(engineerProfileDTO.getTier());
        engineerProfile.setLocation(engineerProfileDTO.getLocation());
        engineerProfile.setPreferredCertTypes(engineerProfileDTO.getPreferredCertTypes());
        engineerProfile.setPreferredJobTimes(engineerProfileDTO.getPreferredJobTimes());
        final User user = engineerProfileDTO.getUser() == null ? null : userRepository.findById(engineerProfileDTO.getUser())
                .orElseThrow(() -> new NotFoundException("user not found"));
        engineerProfile.setUser(user);
        return engineerProfile;
    }

    @EventListener(BeforeDeleteUser.class)
    public void on(final BeforeDeleteUser event) {
        final ReferencedException referencedException = new ReferencedException();
        final EngineerProfile userEngineerProfile = engineerProfileRepository.findFirstByUserId(event.getId());
        if (userEngineerProfile != null) {
            referencedException.setKey("user.engineerProfile.user.referenced");
            referencedException.addParam(userEngineerProfile.getId());
            throw referencedException;
        }
    }

}

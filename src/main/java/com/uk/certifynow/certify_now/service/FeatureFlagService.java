package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.FeatureFlag;
import com.uk.certifynow.certify_now.model.FeatureFlagDTO;
import com.uk.certifynow.certify_now.repos.FeatureFlagRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    public FeatureFlagService(final FeatureFlagRepository featureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
    }

    public List<FeatureFlagDTO> findAll() {
        final List<FeatureFlag> featureFlags = featureFlagRepository.findAll(Sort.by("id"));
        return featureFlags.stream()
                .map(featureFlag -> mapToDTO(featureFlag, new FeatureFlagDTO()))
                .toList();
    }

    public FeatureFlagDTO get(final UUID id) {
        return featureFlagRepository.findById(id)
                .map(featureFlag -> mapToDTO(featureFlag, new FeatureFlagDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final FeatureFlagDTO featureFlagDTO) {
        final FeatureFlag featureFlag = new FeatureFlag();
        mapToEntity(featureFlagDTO, featureFlag);
        return featureFlagRepository.save(featureFlag).getId();
    }

    public void update(final UUID id, final FeatureFlagDTO featureFlagDTO) {
        final FeatureFlag featureFlag = featureFlagRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(featureFlagDTO, featureFlag);
        featureFlagRepository.save(featureFlag);
    }

    public void delete(final UUID id) {
        final FeatureFlag featureFlag = featureFlagRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        featureFlagRepository.delete(featureFlag);
    }

    private FeatureFlagDTO mapToDTO(final FeatureFlag featureFlag,
            final FeatureFlagDTO featureFlagDTO) {
        featureFlagDTO.setId(featureFlag.getId());
        featureFlagDTO.setIsEnabled(featureFlag.getIsEnabled());
        featureFlagDTO.setRolloutPct(featureFlag.getRolloutPct());
        featureFlagDTO.setCreatedAt(featureFlag.getCreatedAt());
        featureFlagDTO.setUpdatedAt(featureFlag.getUpdatedAt());
        featureFlagDTO.setFlagKey(featureFlag.getFlagKey());
        featureFlagDTO.setDescription(featureFlag.getDescription());
        featureFlagDTO.setMetadata(featureFlag.getMetadata());
        return featureFlagDTO;
    }

    private FeatureFlag mapToEntity(final FeatureFlagDTO featureFlagDTO,
            final FeatureFlag featureFlag) {
        featureFlag.setIsEnabled(featureFlagDTO.getIsEnabled());
        featureFlag.setRolloutPct(featureFlagDTO.getRolloutPct());
        featureFlag.setCreatedAt(featureFlagDTO.getCreatedAt());
        featureFlag.setUpdatedAt(featureFlagDTO.getUpdatedAt());
        featureFlag.setFlagKey(featureFlagDTO.getFlagKey());
        featureFlag.setDescription(featureFlagDTO.getDescription());
        featureFlag.setMetadata(featureFlagDTO.getMetadata());
        return featureFlag;
    }

}

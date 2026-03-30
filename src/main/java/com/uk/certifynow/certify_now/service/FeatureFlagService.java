package com.uk.certifynow.certify_now.service;

import tools.jackson.databind.ObjectMapper;
import com.uk.certifynow.certify_now.domain.FeatureFlag;
import com.uk.certifynow.certify_now.domain.enums.AuditAction;
import com.uk.certifynow.certify_now.domain.enums.AuditEntityType;
import com.uk.certifynow.certify_now.model.FeatureFlagDTO;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.repos.FeatureFlagRepository;
import com.uk.certifynow.certify_now.service.job.ActorType;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {

  private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

  private final FeatureFlagRepository featureFlagRepository;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public FeatureFlagService(
      final FeatureFlagRepository featureFlagRepository,
      final AuditLogRepository auditLogRepository,
      final ObjectMapper objectMapper,
      final Clock clock) {
    this.featureFlagRepository = featureFlagRepository;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Cacheable("feature-flags")
  public List<FeatureFlagDTO> findAll() {
    final List<FeatureFlag> featureFlags = featureFlagRepository.findAll(Sort.by("id"));
    return featureFlags.stream()
        .map(featureFlag -> mapToDTO(featureFlag, new FeatureFlagDTO()))
        .toList();
  }

  @Cacheable(value = "feature-flags", key = "#id")
  public FeatureFlagDTO get(final UUID id) {
    return featureFlagRepository
        .findById(id)
        .map(featureFlag -> mapToDTO(featureFlag, new FeatureFlagDTO()))
        .orElseThrow(NotFoundException::new);
  }

  @Transactional
  @CacheEvict(value = "feature-flags", allEntries = true)
  public UUID create(final FeatureFlagDTO featureFlagDTO) {
    final FeatureFlag featureFlag = new FeatureFlag();
    mapToEntity(featureFlagDTO, featureFlag);
    final FeatureFlag saved = featureFlagRepository.save(featureFlag);

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.FEATURE_FLAG_CREATED,
            AuditEntityType.FeatureFlag,
            saved.getId(),
            null,
            toJson(
                Map.of(
                    "flagKey", saved.getFlagKey(),
                    "isEnabled", saved.getIsEnabled(),
                    "rolloutPct", saved.getRolloutPct()))));

    return saved.getId();
  }

  @Transactional
  @CacheEvict(value = "feature-flags", allEntries = true)
  public void update(final UUID id, final FeatureFlagDTO featureFlagDTO) {
    final FeatureFlag featureFlag =
        featureFlagRepository.findById(id).orElseThrow(NotFoundException::new);

    final String oldValues =
        toJson(
            Map.of(
                "flagKey", featureFlag.getFlagKey(),
                "isEnabled", featureFlag.getIsEnabled(),
                "rolloutPct", featureFlag.getRolloutPct()));

    mapToEntity(featureFlagDTO, featureFlag);
    final FeatureFlag saved = featureFlagRepository.save(featureFlag);

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.FEATURE_FLAG_UPDATED,
            AuditEntityType.FeatureFlag,
            id,
            oldValues,
            toJson(
                Map.of(
                    "flagKey", saved.getFlagKey(),
                    "isEnabled", saved.getIsEnabled(),
                    "rolloutPct", saved.getRolloutPct()))));
  }

  @Transactional
  @CacheEvict(value = "feature-flags", allEntries = true)
  public void delete(final UUID id) {
    final FeatureFlag featureFlag =
        featureFlagRepository.findById(id).orElseThrow(NotFoundException::new);

    auditLogRepository.save(
        AuditHelper.build(
            clock,
            currentActorId(),
            ActorType.ADMIN,
            AuditAction.FEATURE_FLAG_DELETED,
            AuditEntityType.FeatureFlag,
            id,
            toJson(
                Map.of(
                    "flagKey", featureFlag.getFlagKey(),
                    "isEnabled", featureFlag.getIsEnabled(),
                    "rolloutPct", featureFlag.getRolloutPct())),
            null));

    featureFlagRepository.delete(featureFlag);
  }

  private FeatureFlagDTO mapToDTO(
      final FeatureFlag featureFlag, final FeatureFlagDTO featureFlagDTO) {
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

  private FeatureFlag mapToEntity(
      final FeatureFlagDTO featureFlagDTO, final FeatureFlag featureFlag) {
    featureFlag.setIsEnabled(featureFlagDTO.getIsEnabled());
    featureFlag.setRolloutPct(featureFlagDTO.getRolloutPct());
    featureFlag.setCreatedAt(featureFlagDTO.getCreatedAt());
    featureFlag.setUpdatedAt(featureFlagDTO.getUpdatedAt());
    featureFlag.setFlagKey(featureFlagDTO.getFlagKey());
    featureFlag.setDescription(featureFlagDTO.getDescription());
    featureFlag.setMetadata(featureFlagDTO.getMetadata());
    return featureFlag;
  }

  private UUID currentActorId() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof String principal) {
      try {
        return UUID.fromString(principal);
      } catch (final IllegalArgumentException ignored) {
        // principal is not a UUID (e.g. "anonymousUser")
      }
    }
    return null;
  }

  private String toJson(final Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (final Exception e) {
      log.warn("Failed to serialize audit values", e);
      return null;
    }
  }
}

package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.AuditLog;
import com.uk.certifynow.certify_now.domain.FeatureFlag;
import com.uk.certifynow.certify_now.model.FeatureFlagDTO;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.repos.FeatureFlagRepository;
import com.uk.certifynow.certify_now.service.feature.FeatureFlagService;
import com.uk.certifynow.certify_now.util.TestConstants;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private FeatureFlagRepository featureFlagRepository;
  @Mock private AuditLogRepository auditLogRepository;
  @Captor private ArgumentCaptor<AuditLog> auditLogCaptor;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FeatureFlagService service;

  @BeforeEach
  void setUp() {
    service =
        new FeatureFlagService(featureFlagRepository, auditLogRepository, objectMapper, clock);
  }

  @Test
  void create_savesAuditLog() {
    final FeatureFlagDTO dto = buildDTO("test-flag", true, 100);
    final FeatureFlag saved = buildEntity(UUID.randomUUID(), "test-flag", true, 100);
    when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(saved);

    service.create(dto);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("FEATURE_FLAG_CREATED");
    assertThat(log.getEntityType()).isEqualTo("FeatureFlag");
    assertThat(log.getEntityId()).isEqualTo(saved.getId());
    assertThat(log.getActorType()).isEqualTo("ADMIN");
    assertThat(log.getNewValues()).contains("test-flag");
    assertThat(log.getOldValues()).isNull();
  }

  @Test
  void update_capturesOldAndNewValues() {
    final UUID id = UUID.randomUUID();
    final FeatureFlag existing = buildEntity(id, "my-flag", true, 50);
    when(featureFlagRepository.findById(id)).thenReturn(Optional.of(existing));
    when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

    final FeatureFlagDTO dto = buildDTO("my-flag", false, 0);
    service.update(id, dto);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("FEATURE_FLAG_UPDATED");
    assertThat(log.getEntityType()).isEqualTo("FeatureFlag");
    assertThat(log.getEntityId()).isEqualTo(id);
    assertThat(log.getOldValues()).contains("my-flag");
    assertThat(log.getNewValues()).contains("my-flag");
  }

  @Test
  void delete_savesAuditLogBeforeDeleting() {
    final UUID id = UUID.randomUUID();
    final FeatureFlag existing = buildEntity(id, "old-flag", true, 100);
    when(featureFlagRepository.findById(id)).thenReturn(Optional.of(existing));

    service.delete(id);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("FEATURE_FLAG_DELETED");
    assertThat(log.getEntityType()).isEqualTo("FeatureFlag");
    assertThat(log.getEntityId()).isEqualTo(id);
    assertThat(log.getOldValues()).contains("old-flag");
    assertThat(log.getNewValues()).isNull();
    verify(featureFlagRepository).delete(existing);
  }

  private FeatureFlagDTO buildDTO(
      final String flagKey, final boolean isEnabled, final int rolloutPct) {
    final FeatureFlagDTO dto = new FeatureFlagDTO();
    dto.setFlagKey(flagKey);
    dto.setIsEnabled(isEnabled);
    dto.setRolloutPct(rolloutPct);
    dto.setCreatedAt(OffsetDateTime.now(clock));
    dto.setUpdatedAt(OffsetDateTime.now(clock));
    return dto;
  }

  private FeatureFlag buildEntity(
      final UUID id, final String flagKey, final boolean isEnabled, final int rolloutPct) {
    final FeatureFlag flag = new FeatureFlag();
    flag.setId(id);
    flag.setFlagKey(flagKey);
    flag.setIsEnabled(isEnabled);
    flag.setRolloutPct(rolloutPct);
    flag.setCreatedAt(OffsetDateTime.now(clock));
    flag.setUpdatedAt(OffsetDateTime.now(clock));
    return flag;
  }
}

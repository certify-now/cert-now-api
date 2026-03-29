package com.uk.certifynow.certify_now.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uk.certifynow.certify_now.domain.AuditLog;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.service.job.ActorType;
import com.uk.certifynow.certify_now.util.TestConstants;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

  private final Clock clock = TestConstants.FIXED_CLOCK;

  @Mock private AuditLogRepository auditLogRepository;
  @Captor private ArgumentCaptor<AuditLog> auditLogCaptor;

  private AuditEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new AuditEventListener(auditLogRepository, clock);
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void onUserSoftDeleted_savesCorrectAuditLog() {
    final UUID userId = UUID.randomUUID();
    final UUID deletedBy = UUID.randomUUID();
    final UserSoftDeletedEvent event = new UserSoftDeletedEvent(userId, deletedBy, ActorType.ADMIN);

    listener.onUserSoftDeleted(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("USER_SOFT_DELETED");
    assertThat(log.getEntityType()).isEqualTo("User");
    assertThat(log.getEntityId()).isEqualTo(userId);
    assertThat(log.getActorId()).isEqualTo(deletedBy);
    assertThat(log.getActorType()).isEqualTo("ADMIN");
    assertThat(log.getCreatedAt()).isEqualTo(OffsetDateTime.now(clock));
  }

  @Test
  void onUserRestored_savesCorrectAuditLog() {
    final UUID userId = UUID.randomUUID();
    final UUID restoredBy = UUID.randomUUID();
    final UserRestoredEvent event = new UserRestoredEvent(userId, restoredBy, ActorType.ADMIN);

    listener.onUserRestored(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("USER_RESTORED");
    assertThat(log.getEntityType()).isEqualTo("User");
    assertThat(log.getEntityId()).isEqualTo(userId);
    assertThat(log.getActorId()).isEqualTo(restoredBy);
    assertThat(log.getActorType()).isEqualTo("ADMIN");
  }

  @Test
  void onPropertySoftDeleted_savesCorrectAuditLog() {
    final UUID propertyId = UUID.randomUUID();
    final UUID deletedBy = UUID.randomUUID();
    final PropertySoftDeletedEvent event =
        new PropertySoftDeletedEvent(propertyId, deletedBy, ActorType.ADMIN);

    listener.onPropertySoftDeleted(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("PROPERTY_SOFT_DELETED");
    assertThat(log.getEntityType()).isEqualTo("Property");
    assertThat(log.getEntityId()).isEqualTo(propertyId);
    assertThat(log.getActorId()).isEqualTo(deletedBy);
    assertThat(log.getActorType()).isEqualTo("ADMIN");
  }

  @Test
  void onPropertyRestored_savesCorrectAuditLog() {
    final UUID propertyId = UUID.randomUUID();
    final UUID restoredBy = UUID.randomUUID();
    final PropertyRestoredEvent event =
        new PropertyRestoredEvent(propertyId, restoredBy, ActorType.ADMIN);

    listener.onPropertyRestored(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("PROPERTY_RESTORED");
    assertThat(log.getEntityType()).isEqualTo("Property");
    assertThat(log.getEntityId()).isEqualTo(propertyId);
    assertThat(log.getActorId()).isEqualTo(restoredBy);
    assertThat(log.getActorType()).isEqualTo("ADMIN");
  }

  @Test
  void onEngineerApproved_savesCorrectAuditLog() {
    final UUID engineerUserId = UUID.randomUUID();
    final UUID profileId = UUID.randomUUID();
    final UUID approvedBy = UUID.randomUUID();
    final EngineerApprovedEvent event =
        new EngineerApprovedEvent(engineerUserId, profileId, approvedBy, OffsetDateTime.now(clock));

    listener.onEngineerApproved(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    final AuditLog log = auditLogCaptor.getValue();
    assertThat(log.getAction()).isEqualTo("ENGINEER_APPROVED");
    assertThat(log.getEntityType()).isEqualTo("EngineerProfile");
    assertThat(log.getEntityId()).isEqualTo(profileId);
    assertThat(log.getActorId()).isEqualTo(approvedBy);
    assertThat(log.getActorType()).isEqualTo("ADMIN");
  }

  @Test
  void onUserSoftDeleted_defaultActorType_isAdmin() {
    final UUID userId = UUID.randomUUID();
    final UUID deletedBy = UUID.randomUUID();
    final UserSoftDeletedEvent event = new UserSoftDeletedEvent(userId, deletedBy);

    listener.onUserSoftDeleted(event);

    verify(auditLogRepository).save(auditLogCaptor.capture());
    assertThat(auditLogCaptor.getValue().getActorType()).isEqualTo("ADMIN");
  }
}

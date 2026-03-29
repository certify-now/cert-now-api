package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.AuditLog;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Utility for building {@link AuditLog} entities with minimal boilerplate. */
public final class AuditHelper {

  private AuditHelper() {}

  public static AuditLog build(
      final Clock clock,
      final UUID actorId,
      final String actorType,
      final String action,
      final String entityType,
      final UUID entityId,
      final String oldValues,
      final String newValues,
      final String ipAddress,
      final String userAgent) {
    final AuditLog log = new AuditLog();
    log.setCreatedAt(OffsetDateTime.now(clock));
    log.setActorId(actorId);
    log.setActorType(actorType);
    log.setAction(action);
    log.setEntityType(entityType);
    log.setEntityId(entityId);
    log.setOldValues(oldValues);
    log.setNewValues(newValues);
    log.setIpAddress(ipAddress);
    log.setUserAgent(userAgent);
    return log;
  }

  /** Convenience overload without IP/user-agent (for service-layer calls). */
  public static AuditLog build(
      final Clock clock,
      final UUID actorId,
      final String actorType,
      final String action,
      final String entityType,
      final UUID entityId,
      final String oldValues,
      final String newValues) {
    return build(
        clock, actorId, actorType, action, entityType, entityId, oldValues, newValues, null, null);
  }
}

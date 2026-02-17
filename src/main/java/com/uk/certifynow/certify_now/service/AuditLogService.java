package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.AuditLog;
import com.uk.certifynow.certify_now.model.AuditLogDTO;
import com.uk.certifynow.certify_now.repos.AuditLogRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(final AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public List<AuditLogDTO> findAll() {
    final List<AuditLog> auditLogs = auditLogRepository.findAll(Sort.by("id"));
    return auditLogs.stream().map(auditLog -> mapToDTO(auditLog, new AuditLogDTO())).toList();
  }

  public AuditLogDTO get(final UUID id) {
    return auditLogRepository
        .findById(id)
        .map(auditLog -> mapToDTO(auditLog, new AuditLogDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final AuditLogDTO auditLogDTO) {
    final AuditLog auditLog = new AuditLog();
    mapToEntity(auditLogDTO, auditLog);
    return auditLogRepository.save(auditLog).getId();
  }

  public void update(final UUID id, final AuditLogDTO auditLogDTO) {
    final AuditLog auditLog = auditLogRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(auditLogDTO, auditLog);
    auditLogRepository.save(auditLog);
  }

  public void delete(final UUID id) {
    final AuditLog auditLog = auditLogRepository.findById(id).orElseThrow(NotFoundException::new);
    auditLogRepository.delete(auditLog);
  }

  private AuditLogDTO mapToDTO(final AuditLog auditLog, final AuditLogDTO auditLogDTO) {
    auditLogDTO.setId(auditLog.getId());
    auditLogDTO.setCreatedAt(auditLog.getCreatedAt());
    auditLogDTO.setActorId(auditLog.getActorId());
    auditLogDTO.setEntityId(auditLog.getEntityId());
    auditLogDTO.setActorType(auditLog.getActorType());
    auditLogDTO.setAction(auditLog.getAction());
    auditLogDTO.setEntityType(auditLog.getEntityType());
    auditLogDTO.setIpAddress(auditLog.getIpAddress());
    auditLogDTO.setUserAgent(auditLog.getUserAgent());
    auditLogDTO.setNewValues(auditLog.getNewValues());
    auditLogDTO.setOldValues(auditLog.getOldValues());
    return auditLogDTO;
  }

  private AuditLog mapToEntity(final AuditLogDTO auditLogDTO, final AuditLog auditLog) {
    auditLog.setCreatedAt(auditLogDTO.getCreatedAt());
    auditLog.setActorId(auditLogDTO.getActorId());
    auditLog.setEntityId(auditLogDTO.getEntityId());
    auditLog.setActorType(auditLogDTO.getActorType());
    auditLog.setAction(auditLogDTO.getAction());
    auditLog.setEntityType(auditLogDTO.getEntityType());
    auditLog.setIpAddress(auditLogDTO.getIpAddress());
    auditLog.setUserAgent(auditLogDTO.getUserAgent());
    auditLog.setNewValues(auditLogDTO.getNewValues());
    auditLog.setOldValues(auditLogDTO.getOldValues());
    return auditLog;
  }
}

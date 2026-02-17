package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.UrgencyMultiplier;
import com.uk.certifynow.certify_now.model.UrgencyMultiplierDTO;
import com.uk.certifynow.certify_now.repos.UrgencyMultiplierRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UrgencyMultiplierService {

  private final UrgencyMultiplierRepository urgencyMultiplierRepository;

  public UrgencyMultiplierService(final UrgencyMultiplierRepository urgencyMultiplierRepository) {
    this.urgencyMultiplierRepository = urgencyMultiplierRepository;
  }

  public List<UrgencyMultiplierDTO> findAll() {
    final List<UrgencyMultiplier> urgencyMultipliers =
        urgencyMultiplierRepository.findAll(Sort.by("id"));
    return urgencyMultipliers.stream()
        .map(urgencyMultiplier -> mapToDTO(urgencyMultiplier, new UrgencyMultiplierDTO()))
        .toList();
  }

  public UrgencyMultiplierDTO get(final UUID id) {
    return urgencyMultiplierRepository
        .findById(id)
        .map(urgencyMultiplier -> mapToDTO(urgencyMultiplier, new UrgencyMultiplierDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final UrgencyMultiplierDTO urgencyMultiplierDTO) {
    final UrgencyMultiplier urgencyMultiplier = new UrgencyMultiplier();
    mapToEntity(urgencyMultiplierDTO, urgencyMultiplier);
    return urgencyMultiplierRepository.save(urgencyMultiplier).getId();
  }

  public void update(final UUID id, final UrgencyMultiplierDTO urgencyMultiplierDTO) {
    final UrgencyMultiplier urgencyMultiplier =
        urgencyMultiplierRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(urgencyMultiplierDTO, urgencyMultiplier);
    urgencyMultiplierRepository.save(urgencyMultiplier);
  }

  public void delete(final UUID id) {
    final UrgencyMultiplier urgencyMultiplier =
        urgencyMultiplierRepository.findById(id).orElseThrow(NotFoundException::new);
    urgencyMultiplierRepository.delete(urgencyMultiplier);
  }

  private UrgencyMultiplierDTO mapToDTO(
      final UrgencyMultiplier urgencyMultiplier, final UrgencyMultiplierDTO urgencyMultiplierDTO) {
    urgencyMultiplierDTO.setId(urgencyMultiplier.getId());
    urgencyMultiplierDTO.setEffectiveFrom(urgencyMultiplier.getEffectiveFrom());
    urgencyMultiplierDTO.setIsActive(urgencyMultiplier.getIsActive());
    urgencyMultiplierDTO.setMultiplier(urgencyMultiplier.getMultiplier());
    urgencyMultiplierDTO.setCreatedAt(urgencyMultiplier.getCreatedAt());
    urgencyMultiplierDTO.setUrgency(urgencyMultiplier.getUrgency());
    return urgencyMultiplierDTO;
  }

  private UrgencyMultiplier mapToEntity(
      final UrgencyMultiplierDTO urgencyMultiplierDTO, final UrgencyMultiplier urgencyMultiplier) {
    urgencyMultiplier.setEffectiveFrom(urgencyMultiplierDTO.getEffectiveFrom());
    urgencyMultiplier.setIsActive(urgencyMultiplierDTO.getIsActive());
    urgencyMultiplier.setMultiplier(urgencyMultiplierDTO.getMultiplier());
    urgencyMultiplier.setCreatedAt(urgencyMultiplierDTO.getCreatedAt());
    urgencyMultiplier.setUrgency(urgencyMultiplierDTO.getUrgency());
    return urgencyMultiplier;
  }
}

package com.uk.certifynow.certify_now.service.payment;

import com.uk.certifynow.certify_now.domain.StripeWebhookEvent;
import com.uk.certifynow.certify_now.model.StripeWebhookEventDTO;
import com.uk.certifynow.certify_now.repos.StripeWebhookEventRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookEventService {

  private final StripeWebhookEventRepository stripeWebhookEventRepository;

  public StripeWebhookEventService(
      final StripeWebhookEventRepository stripeWebhookEventRepository) {
    this.stripeWebhookEventRepository = stripeWebhookEventRepository;
  }

  public List<StripeWebhookEventDTO> findAll() {
    final List<StripeWebhookEvent> stripeWebhookEvents =
        stripeWebhookEventRepository.findAll(Sort.by("id"));
    return stripeWebhookEvents.stream()
        .map(stripeWebhookEvent -> mapToDTO(stripeWebhookEvent, new StripeWebhookEventDTO()))
        .toList();
  }

  public StripeWebhookEventDTO get(final UUID id) {
    return stripeWebhookEventRepository
        .findById(id)
        .map(stripeWebhookEvent -> mapToDTO(stripeWebhookEvent, new StripeWebhookEventDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final StripeWebhookEventDTO stripeWebhookEventDTO) {
    final StripeWebhookEvent stripeWebhookEvent = new StripeWebhookEvent();
    mapToEntity(stripeWebhookEventDTO, stripeWebhookEvent);
    return stripeWebhookEventRepository.save(stripeWebhookEvent).getId();
  }

  public void update(final UUID id, final StripeWebhookEventDTO stripeWebhookEventDTO) {
    final StripeWebhookEvent stripeWebhookEvent =
        stripeWebhookEventRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(stripeWebhookEventDTO, stripeWebhookEvent);
    stripeWebhookEventRepository.save(stripeWebhookEvent);
  }

  public void delete(final UUID id) {
    final StripeWebhookEvent stripeWebhookEvent =
        stripeWebhookEventRepository.findById(id).orElseThrow(NotFoundException::new);
    stripeWebhookEventRepository.delete(stripeWebhookEvent);
  }

  private StripeWebhookEventDTO mapToDTO(
      final StripeWebhookEvent stripeWebhookEvent,
      final StripeWebhookEventDTO stripeWebhookEventDTO) {
    stripeWebhookEventDTO.setId(stripeWebhookEvent.getId());
    stripeWebhookEventDTO.setProcessed(stripeWebhookEvent.getProcessed());
    stripeWebhookEventDTO.setCreatedAt(stripeWebhookEvent.getCreatedAt());
    stripeWebhookEventDTO.setEventType(stripeWebhookEvent.getEventType());
    stripeWebhookEventDTO.setErrorMessage(stripeWebhookEvent.getErrorMessage());
    stripeWebhookEventDTO.setStripeEventId(stripeWebhookEvent.getStripeEventId());
    stripeWebhookEventDTO.setPayload(stripeWebhookEvent.getPayload());
    return stripeWebhookEventDTO;
  }

  private StripeWebhookEvent mapToEntity(
      final StripeWebhookEventDTO stripeWebhookEventDTO,
      final StripeWebhookEvent stripeWebhookEvent) {
    stripeWebhookEvent.setProcessed(stripeWebhookEventDTO.getProcessed());
    stripeWebhookEvent.setCreatedAt(stripeWebhookEventDTO.getCreatedAt());
    stripeWebhookEvent.setEventType(stripeWebhookEventDTO.getEventType());
    stripeWebhookEvent.setErrorMessage(stripeWebhookEventDTO.getErrorMessage());
    stripeWebhookEvent.setStripeEventId(stripeWebhookEventDTO.getStripeEventId());
    stripeWebhookEvent.setPayload(stripeWebhookEventDTO.getPayload());
    return stripeWebhookEvent;
  }
}

package com.uk.certifynow.certify_now.service.pricing;

import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.events.BeforeDeletePricingRule;
import com.uk.certifynow.certify_now.model.PricingRuleDTO;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PricingRuleService {

  private final PricingRuleRepository pricingRuleRepository;
  private final ApplicationEventPublisher publisher;

  public PricingRuleService(
      final PricingRuleRepository pricingRuleRepository,
      final ApplicationEventPublisher publisher) {
    this.pricingRuleRepository = pricingRuleRepository;
    this.publisher = publisher;
  }

  public List<PricingRuleDTO> findAll() {
    final List<PricingRule> pricingRules = pricingRuleRepository.findAll(Sort.by("id"));
    return pricingRules.stream()
        .map(pricingRule -> mapToDTO(pricingRule, new PricingRuleDTO()))
        .toList();
  }

  public PricingRuleDTO get(final UUID id) {
    return pricingRuleRepository
        .findById(id)
        .map(pricingRule -> mapToDTO(pricingRule, new PricingRuleDTO()))
        .orElseThrow(NotFoundException::new);
  }

  public UUID create(final PricingRuleDTO pricingRuleDTO) {
    final PricingRule pricingRule = new PricingRule();
    mapToEntity(pricingRuleDTO, pricingRule);
    return pricingRuleRepository.save(pricingRule).getId();
  }

  public void update(final UUID id, final PricingRuleDTO pricingRuleDTO) {
    final PricingRule pricingRule =
        pricingRuleRepository.findById(id).orElseThrow(NotFoundException::new);
    mapToEntity(pricingRuleDTO, pricingRule);
    pricingRuleRepository.save(pricingRule);
  }

  public void delete(final UUID id) {
    final PricingRule pricingRule =
        pricingRuleRepository.findById(id).orElseThrow(NotFoundException::new);
    publisher.publishEvent(new BeforeDeletePricingRule(id));
    pricingRuleRepository.delete(pricingRule);
  }

  private PricingRuleDTO mapToDTO(
      final PricingRule pricingRule, final PricingRuleDTO pricingRuleDTO) {
    pricingRuleDTO.setId(pricingRule.getId());
    pricingRuleDTO.setBasePricePence(pricingRule.getBasePricePence());
    pricingRuleDTO.setEffectiveFrom(pricingRule.getEffectiveFrom());
    pricingRuleDTO.setEffectiveTo(pricingRule.getEffectiveTo());
    pricingRuleDTO.setIsActive(pricingRule.getIsActive());
    pricingRuleDTO.setCreatedAt(pricingRule.getCreatedAt());
    pricingRuleDTO.setCreatedBy(pricingRule.getCreatedBy());
    pricingRuleDTO.setRegion(pricingRule.getRegion());
    pricingRuleDTO.setCertificateType(pricingRule.getCertificateType());
    return pricingRuleDTO;
  }

  private PricingRule mapToEntity(
      final PricingRuleDTO pricingRuleDTO, final PricingRule pricingRule) {
    pricingRule.setBasePricePence(pricingRuleDTO.getBasePricePence());
    pricingRule.setEffectiveFrom(pricingRuleDTO.getEffectiveFrom());
    pricingRule.setEffectiveTo(pricingRuleDTO.getEffectiveTo());
    pricingRule.setIsActive(pricingRuleDTO.getIsActive());
    pricingRule.setCreatedAt(pricingRuleDTO.getCreatedAt());
    pricingRule.setCreatedBy(pricingRuleDTO.getCreatedBy());
    pricingRule.setRegion(pricingRuleDTO.getRegion());
    pricingRule.setCertificateType(pricingRuleDTO.getCertificateType());
    return pricingRule;
  }
}

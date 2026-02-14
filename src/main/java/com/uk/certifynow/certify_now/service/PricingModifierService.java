package com.uk.certifynow.certify_now.service;

import com.uk.certifynow.certify_now.domain.PricingModifier;
import com.uk.certifynow.certify_now.domain.PricingRule;
import com.uk.certifynow.certify_now.events.BeforeDeletePricingRule;
import com.uk.certifynow.certify_now.model.PricingModifierDTO;
import com.uk.certifynow.certify_now.repos.PricingModifierRepository;
import com.uk.certifynow.certify_now.repos.PricingRuleRepository;
import com.uk.certifynow.certify_now.util.NotFoundException;
import com.uk.certifynow.certify_now.util.ReferencedException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class PricingModifierService {

    private final PricingModifierRepository pricingModifierRepository;
    private final PricingRuleRepository pricingRuleRepository;

    public PricingModifierService(final PricingModifierRepository pricingModifierRepository,
            final PricingRuleRepository pricingRuleRepository) {
        this.pricingModifierRepository = pricingModifierRepository;
        this.pricingRuleRepository = pricingRuleRepository;
    }

    public List<PricingModifierDTO> findAll() {
        final List<PricingModifier> pricingModifiers = pricingModifierRepository.findAll(Sort.by("id"));
        return pricingModifiers.stream()
                .map(pricingModifier -> mapToDTO(pricingModifier, new PricingModifierDTO()))
                .toList();
    }

    public PricingModifierDTO get(final UUID id) {
        return pricingModifierRepository.findById(id)
                .map(pricingModifier -> mapToDTO(pricingModifier, new PricingModifierDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public UUID create(final PricingModifierDTO pricingModifierDTO) {
        final PricingModifier pricingModifier = new PricingModifier();
        mapToEntity(pricingModifierDTO, pricingModifier);
        return pricingModifierRepository.save(pricingModifier).getId();
    }

    public void update(final UUID id, final PricingModifierDTO pricingModifierDTO) {
        final PricingModifier pricingModifier = pricingModifierRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(pricingModifierDTO, pricingModifier);
        pricingModifierRepository.save(pricingModifier);
    }

    public void delete(final UUID id) {
        final PricingModifier pricingModifier = pricingModifierRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        pricingModifierRepository.delete(pricingModifier);
    }

    private PricingModifierDTO mapToDTO(final PricingModifier pricingModifier,
            final PricingModifierDTO pricingModifierDTO) {
        pricingModifierDTO.setId(pricingModifier.getId());
        pricingModifierDTO.setConditionMax(pricingModifier.getConditionMax());
        pricingModifierDTO.setConditionMin(pricingModifier.getConditionMin());
        pricingModifierDTO.setModifierPence(pricingModifier.getModifierPence());
        pricingModifierDTO.setCreatedAt(pricingModifier.getCreatedAt());
        pricingModifierDTO.setModifierType(pricingModifier.getModifierType());
        pricingModifierDTO.setPricingRule(pricingModifier.getPricingRule() == null ? null : pricingModifier.getPricingRule().getId());
        return pricingModifierDTO;
    }

    private PricingModifier mapToEntity(final PricingModifierDTO pricingModifierDTO,
            final PricingModifier pricingModifier) {
        pricingModifier.setConditionMax(pricingModifierDTO.getConditionMax());
        pricingModifier.setConditionMin(pricingModifierDTO.getConditionMin());
        pricingModifier.setModifierPence(pricingModifierDTO.getModifierPence());
        pricingModifier.setCreatedAt(pricingModifierDTO.getCreatedAt());
        pricingModifier.setModifierType(pricingModifierDTO.getModifierType());
        final PricingRule pricingRule = pricingModifierDTO.getPricingRule() == null ? null : pricingRuleRepository.findById(pricingModifierDTO.getPricingRule())
                .orElseThrow(() -> new NotFoundException("pricingRule not found"));
        pricingModifier.setPricingRule(pricingRule);
        return pricingModifier;
    }

    @EventListener(BeforeDeletePricingRule.class)
    public void on(final BeforeDeletePricingRule event) {
        final ReferencedException referencedException = new ReferencedException();
        final PricingModifier pricingRulePricingModifier = pricingModifierRepository.findFirstByPricingRuleId(event.getId());
        if (pricingRulePricingModifier != null) {
            referencedException.setKey("pricingRule.pricingModifier.pricingRule.referenced");
            referencedException.addParam(pricingRulePricingModifier.getId());
            throw referencedException;
        }
    }

}

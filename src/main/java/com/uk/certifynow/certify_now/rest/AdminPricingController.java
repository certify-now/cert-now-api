package com.uk.certifynow.certify_now.pricing.controller;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingModifierRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.CreatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.PricingRuleResponse;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdatePricingRuleRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.UpdateUrgencyMultiplierRequest;
import com.uk.certifynow.certify_now.rest.dto.pricing.UrgencyMultiplierResponse;
import com.uk.certifynow.certify_now.service.PricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pricing")
public class AdminPricingController {

  private final PricingService pricingService;

  public AdminPricingController(final PricingService pricingService) {
    this.pricingService = pricingService;
  }

  // ═══════════════════════════════════════════════════════
  // PRICING RULES
  // ═══════════════════════════════════════════════════════

  @GetMapping("/rules")
  public ApiResponse<List<PricingRuleResponse>> listRules(
      @RequestParam(value = "active_only", defaultValue = "true") final boolean activeOnly,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.getActivePricingRules(activeOnly), requestId(request));
  }

  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PricingRuleResponse> createRule(
      @Valid @RequestBody final CreatePricingRuleRequest body, final HttpServletRequest request) {
    return ApiResponse.of(pricingService.createPricingRule(body), requestId(request));
  }

  @PutMapping("/rules/{id}")
  public ApiResponse<PricingRuleResponse> updateRule(
      @PathVariable final UUID id,
      @Valid @RequestBody final UpdatePricingRuleRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.updatePricingRule(id, body), requestId(request));
  }

  // ═══════════════════════════════════════════════════════
  // MODIFIERS
  // ═══════════════════════════════════════════════════════

  @PostMapping("/rules/{ruleId}/modifiers")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PricingRuleResponse> addModifier(
      @PathVariable final UUID ruleId,
      @Valid @RequestBody final CreatePricingModifierRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.addModifier(ruleId, body), requestId(request));
  }

  @DeleteMapping("/rules/{ruleId}/modifiers/{modifierId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeModifier(@PathVariable final UUID ruleId, @PathVariable final UUID modifierId) {
    pricingService.removeModifier(ruleId, modifierId);
  }

  // ═══════════════════════════════════════════════════════
  // URGENCY MULTIPLIERS
  // ═══════════════════════════════════════════════════════

  @GetMapping("/urgency-multipliers")
  public ApiResponse<List<UrgencyMultiplierResponse>> listMultipliers(
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.getActiveUrgencyMultipliers(), requestId(request));
  }

  @PutMapping("/urgency-multipliers/{id}")
  public ApiResponse<UrgencyMultiplierResponse> updateMultiplier(
      @PathVariable final UUID id,
      @Valid @RequestBody final UpdateUrgencyMultiplierRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.updateUrgencyMultiplier(id, body), requestId(request));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

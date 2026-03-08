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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin - Pricing", description = "Admin management of pricing configurations")
public class AdminPricingController {

  private final PricingService pricingService;

  public AdminPricingController(final PricingService pricingService) {
    this.pricingService = pricingService;
  }

  // ═══════════════════════════════════════════════════════
  // PRICING RULES
  // ═══════════════════════════════════════════════════════

  @GetMapping("/rules")
  @Operation(
      summary = "List pricing rules",
      description = "Returns all pricing rules. Optionally filter to only active rules.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Pricing rules retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required")
  })
  public ApiResponse<List<PricingRuleResponse>> listRules(
      @Parameter(description = "If true, return only active rules")
          @RequestParam(value = "active_only", defaultValue = "true")
          final boolean activeOnly,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.getActivePricingRules(activeOnly), requestId(request));
  }

  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a pricing rule",
      description =
          "Creates a new pricing rule for a certificate type with a base price,"
              + " optional region, and effective date range.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Pricing rule created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required")
  })
  public ApiResponse<PricingRuleResponse> createRule(
      @Valid @RequestBody final CreatePricingRuleRequest body, final HttpServletRequest request) {
    return ApiResponse.of(pricingService.createPricingRule(body), requestId(request));
  }

  @PutMapping("/rules/{id}")
  @Operation(
      summary = "Update a pricing rule",
      description =
          "Updates an existing pricing rule's base price, region, effective dates, or active status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Pricing rule updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Pricing rule not found")
  })
  public ApiResponse<PricingRuleResponse> updateRule(
      @Parameter(description = "Pricing rule ID") @PathVariable final UUID id,
      @Valid @RequestBody final UpdatePricingRuleRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.updatePricingRule(id, body), requestId(request));
  }

  // ═══════════════════════════════════════════════════════
  // MODIFIERS
  // ═══════════════════════════════════════════════════════

  @PostMapping("/rules/{ruleId}/modifiers")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Add a pricing modifier",
      description =
          "Adds a conditional price modifier (e.g. bedroom bracket, appliance count)"
              + " to an existing pricing rule.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Modifier added successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Pricing rule not found")
  })
  public ApiResponse<PricingRuleResponse> addModifier(
      @Parameter(description = "Pricing rule ID") @PathVariable final UUID ruleId,
      @Valid @RequestBody final CreatePricingModifierRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.addModifier(ruleId, body), requestId(request));
  }

  @DeleteMapping("/rules/{ruleId}/modifiers/{modifierId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remove a pricing modifier",
      description = "Removes a specific modifier from a pricing rule.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Modifier removed successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Pricing rule or modifier not found")
  })
  public void removeModifier(
      @Parameter(description = "Pricing rule ID") @PathVariable final UUID ruleId,
      @Parameter(description = "Modifier ID") @PathVariable final UUID modifierId) {
    pricingService.removeModifier(ruleId, modifierId);
  }

  // ═══════════════════════════════════════════════════════
  // URGENCY MULTIPLIERS
  // ═══════════════════════════════════════════════════════

  @GetMapping("/urgency-multipliers")
  @Operation(
      summary = "List urgency multipliers",
      description = "Returns all active urgency multipliers (e.g. STANDARD, PRIORITY, EMERGENCY).")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Urgency multipliers retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required")
  })
  public ApiResponse<List<UrgencyMultiplierResponse>> listMultipliers(
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.getActiveUrgencyMultipliers(), requestId(request));
  }

  @PutMapping("/urgency-multipliers/{id}")
  @Operation(
      summary = "Update an urgency multiplier",
      description = "Updates the multiplier value for a specific urgency level.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Urgency multiplier updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Urgency multiplier not found")
  })
  public ApiResponse<UrgencyMultiplierResponse> updateMultiplier(
      @Parameter(description = "Urgency multiplier ID") @PathVariable final UUID id,
      @Valid @RequestBody final UpdateUrgencyMultiplierRequest body,
      final HttpServletRequest request) {
    return ApiResponse.of(pricingService.updateUrgencyMultiplier(id, body), requestId(request));
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

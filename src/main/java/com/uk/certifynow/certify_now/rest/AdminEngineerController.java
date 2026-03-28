package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.PaginationProperties;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.EngineerProfileResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.InsuranceResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.QualificationResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.TransitionStatusRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.VerifyInsuranceRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.VerifyQualificationRequest;
import com.uk.certifynow.certify_now.service.EngineerInsuranceService;
import com.uk.certifynow.certify_now.service.EngineerProfileService;
import com.uk.certifynow.certify_now.service.EngineerQualificationService;
import com.uk.certifynow.certify_now.service.auth.EngineerApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/engineers")
@Tag(name = "Admin - Engineers", description = "Admin management of engineers")
public class AdminEngineerController extends BaseController {

  private final EngineerProfileService engineerProfileService;
  private final EngineerQualificationService engineerQualificationService;
  private final EngineerInsuranceService engineerInsuranceService;
  private final PaginationProperties paginationProperties;

  public AdminEngineerController(
      final EngineerProfileService engineerProfileService,
      final EngineerQualificationService engineerQualificationService,
      final EngineerInsuranceService engineerInsuranceService,
      final PaginationProperties paginationProperties) {
    this.engineerProfileService = engineerProfileService;
    this.engineerQualificationService = engineerQualificationService;
    this.engineerInsuranceService = engineerInsuranceService;
    this.paginationProperties = paginationProperties;
  }

  // -- List all engineers (paginated) -----------------------------------------

  @GetMapping
  @Operation(
      summary = "List all engineers",
      description =
          "Returns a paginated list of all engineer profiles." + " Admin access required.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Engineers retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required")
  })
  public ApiResponse<Page<EngineerProfileResponse>> listEngineers(
      @Parameter(description = "Page number (zero-based)") @RequestParam(defaultValue = "0")
          final int page,
      @Parameter(description = "Page size (max 50)") @RequestParam(defaultValue = "20")
          final int size,
      final HttpServletRequest httpRequest) {
    final int cappedSize = Math.min(size, paginationProperties.getMaxSize());
    final var pageable =
        PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    return ApiResponse.of(
        engineerProfileService.findAllPaginated(pageable), requestId(httpRequest));
  }

  // -- Engineer detail --------------------------------------------------------

  @GetMapping("/{id}")
  @Operation(
      summary = "Get engineer detail",
      description = "Returns the full profile of a specific engineer by ID. Admin access required.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Engineer detail retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Engineer not found")
  })
  public ApiResponse<EngineerProfileResponse> getEngineerDetail(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      final HttpServletRequest httpRequest) {
    return ApiResponse.of(engineerProfileService.getProfile(id), requestId(httpRequest));
  }

  // -- Approve ----------------------------------------------------------------

  @PutMapping("/{id}/approve")
  @Operation(
      summary = "Approve an engineer",
      description =
          "Approves an engineer's application, allowing them to go online and accept jobs."
              + " Publishes an EngineerApprovedEvent.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Engineer approved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Engineer not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Invalid status transition")
  })
  public ApiResponse<EngineerProfileResponse> approveEngineer(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerProfileService.transitionStatus(id, EngineerApplicationStatus.APPROVED, adminId),
        requestId(httpRequest));
  }

  // -- Reject -----------------------------------------------------------------

  @PutMapping("/{id}/reject")
  @Operation(
      summary = "Reject an engineer",
      description =
          "Rejects an engineer's application. The engineer will not be able to go online or accept jobs.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Engineer rejected successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Engineer not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Invalid status transition")
  })
  public ApiResponse<EngineerProfileResponse> rejectEngineer(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerProfileService.transitionStatus(id, EngineerApplicationStatus.REJECTED, adminId),
        requestId(httpRequest));
  }

  // -- Generic transition -----------------------------------------------------

  @PutMapping("/{id}/transition-status")
  @Operation(
      summary = "Transition engineer application status",
      description =
          "Transitions an engineer's application to the specified target status."
              + " Supports all valid EngineerApplicationStatus transitions.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Status transitioned successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error or invalid target status"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — admin access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Engineer not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Invalid status transition")
  })
  public ApiResponse<EngineerProfileResponse> transitionStatus(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      @Valid @RequestBody final TransitionStatusRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    final EngineerApplicationStatus targetStatus =
        EngineerApplicationStatus.valueOf(request.targetStatus());
    return ApiResponse.of(
        engineerProfileService.transitionStatus(id, targetStatus, adminId), requestId(httpRequest));
  }

  // -- Verify qualification ---------------------------------------------------

  @PutMapping("/{id}/verify-qualification/{qId}")
  @Operation(
      summary = "Verify an engineer's qualification",
      description = "Admin verifies or rejects an engineer's submitted qualification document.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Qualification verification status updated"),
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
        description = "Engineer or qualification not found")
  })
  public ApiResponse<QualificationResponse> verifyQualification(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      @Parameter(description = "Qualification ID") @PathVariable final UUID qId,
      @Valid @RequestBody final VerifyQualificationRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerQualificationService.verifyQualification(
            qId, adminId, request.verificationStatus()),
        requestId(httpRequest));
  }

  // -- Verify insurance -------------------------------------------------------

  @PutMapping("/{id}/verify-insurance/{iId}")
  @Operation(
      summary = "Verify an engineer's insurance",
      description = "Admin verifies or rejects an engineer's submitted insurance document.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Insurance verification status updated"),
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
        description = "Engineer or insurance record not found")
  })
  public ApiResponse<InsuranceResponse> verifyInsurance(
      @Parameter(description = "Engineer profile ID") @PathVariable final UUID id,
      @Parameter(description = "Insurance record ID") @PathVariable final UUID iId,
      @Valid @RequestBody final VerifyInsuranceRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerInsuranceService.verifyInsurance(iId, adminId, request.verificationStatus()),
        requestId(httpRequest));
  }
}

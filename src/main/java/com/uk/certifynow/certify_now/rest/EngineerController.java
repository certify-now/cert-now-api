package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.AddInsuranceRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.AddQualificationRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.AvailabilityOverrideRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.AvailabilityResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.EngineerProfileResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.InsuranceResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.OnlineStatusRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.QualificationResponse;
import com.uk.certifynow.certify_now.rest.dto.engineer.SetAvailabilityRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.UpdateEngineerProfileRequest;
import com.uk.certifynow.certify_now.rest.dto.engineer.UpdateLocationRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.service.engineer.EngineerAvailabilityService;
import com.uk.certifynow.certify_now.service.engineer.EngineerInsuranceService;
import com.uk.certifynow.certify_now.service.engineer.EngineerProfileService;
import com.uk.certifynow.certify_now.service.engineer.EngineerQualificationService;
import com.uk.certifynow.certify_now.service.matching.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engineer")
@Tag(name = "Engineer", description = "Engineer-specific operations and job management")
public class EngineerController extends BaseController {

  private final EngineerProfileService engineerProfileService;
  private final EngineerQualificationService engineerQualificationService;
  private final EngineerInsuranceService engineerInsuranceService;
  private final EngineerAvailabilityService engineerAvailabilityService;
  private final MatchingService matchingService;

  public EngineerController(
      final EngineerProfileService engineerProfileService,
      final EngineerQualificationService engineerQualificationService,
      final EngineerInsuranceService engineerInsuranceService,
      final EngineerAvailabilityService engineerAvailabilityService,
      final MatchingService matchingService) {
    this.engineerProfileService = engineerProfileService;
    this.engineerQualificationService = engineerQualificationService;
    this.engineerInsuranceService = engineerInsuranceService;
    this.engineerAvailabilityService = engineerAvailabilityService;
    this.matchingService = matchingService;
  }

  // -- Profile ----------------------------------------------------------------

  @GetMapping("/profile")
  @Operation(
      summary = "Get engineer profile",
      description =
          "Returns the authenticated engineer's profile including tier, rating, and online status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Engineer profile retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ApiResponse<EngineerProfileResponse> getMyProfile(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(engineerProfileService.getMyProfile(userId), requestId(httpRequest));
  }

  @PutMapping("/profile")
  @Operation(
      summary = "Update engineer profile",
      description =
          "Updates the authenticated engineer's profile fields such as bio,"
              + " preferred certificate types, service radius, and max daily jobs.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Profile updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ApiResponse<EngineerProfileResponse> updateProfile(
      @Valid @RequestBody final UpdateEngineerProfileRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        engineerProfileService.updateProfile(userId, request), requestId(httpRequest));
  }

  // -- Location ---------------------------------------------------------------

  @PutMapping("/location")
  @Operation(
      summary = "Update engineer location",
      description =
          "Updates the engineer's current GPS coordinates for proximity-based job matching.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Location updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ResponseEntity<ApiResponse<Void>> updateLocation(
      @Valid @RequestBody final UpdateLocationRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    engineerProfileService.updateLocation(userId, request.latitude(), request.longitude());
    return ResponseEntity.ok(ApiResponse.of(null, requestId(httpRequest)));
  }

  // -- Online Status ----------------------------------------------------------

  @PutMapping("/online-status")
  @Operation(
      summary = "Set online status",
      description =
          "Toggles the engineer's online availability." + " Only approved engineers can go online.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Online status updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error or engineer not approved"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ResponseEntity<ApiResponse<Void>> setOnlineStatus(
      @Valid @RequestBody final OnlineStatusRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    engineerProfileService.setOnlineStatus(userId, request.isOnline());
    return ResponseEntity.ok(ApiResponse.of(null, requestId(httpRequest)));
  }

  // -- Qualifications ---------------------------------------------------------

  @PostMapping("/qualifications")
  @Operation(
      summary = "Add a qualification",
      description =
          "Submits a new qualification (e.g. Gas Safe registration) for the engineer."
              + " The qualification will require admin verification before it is active.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Qualification added successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ResponseEntity<ApiResponse<QualificationResponse>> addQualification(
      @Valid @RequestBody final AddQualificationRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    final QualificationResponse response =
        engineerQualificationService.addQualification(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @GetMapping("/qualifications")
  @Operation(
      summary = "List my qualifications",
      description = "Returns all qualifications submitted by the authenticated engineer.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Qualifications retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ApiResponse<List<QualificationResponse>> getMyQualifications(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        engineerQualificationService.getMyQualifications(userId), requestId(httpRequest));
  }

  // -- Insurance --------------------------------------------------------------

  @PostMapping("/insurance")
  @Operation(
      summary = "Add insurance details",
      description =
          "Submits insurance information for the engineer."
              + " Insurance will require admin verification before it is active.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Insurance added successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ResponseEntity<ApiResponse<InsuranceResponse>> addInsurance(
      @Valid @RequestBody final AddInsuranceRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    final InsuranceResponse response = engineerInsuranceService.addInsurance(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @GetMapping("/insurance")
  @Operation(
      summary = "List my insurance records",
      description = "Returns all insurance records submitted by the authenticated engineer.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Insurance records retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ApiResponse<List<InsuranceResponse>> getMyInsurance(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(engineerInsuranceService.getMyInsurance(userId), requestId(httpRequest));
  }

  // -- Availability -----------------------------------------------------------

  @PutMapping("/availability")
  @Operation(
      summary = "Set weekly availability",
      description =
          "Replaces the engineer's recurring weekly availability slots."
              + " Existing slots are overwritten with the provided set.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Availability updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ApiResponse<List<AvailabilityResponse>> setAvailability(
      @Valid @RequestBody final SetAvailabilityRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        engineerAvailabilityService.setAvailability(userId, request.slots()),
        requestId(httpRequest));
  }

  @PostMapping("/availability/override")
  @Operation(
      summary = "Add an availability override",
      description =
          "Creates a one-time availability override for a specific date,"
              + " overriding the recurring weekly schedule.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Availability override created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required")
  })
  public ResponseEntity<ApiResponse<AvailabilityResponse>> addOverride(
      @Valid @RequestBody final AvailabilityOverrideRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    final AvailabilityResponse response = engineerAvailabilityService.addOverride(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  // -- Job Claim (Matching Engine) -------------------------------------------

  /**
   * POST /api/v1/engineer/jobs/{jobId}/claim — Atomic first-to-accept. The first engineer to call
   * this endpoint wins the job. Returns 200 on success, 409 Conflict if already claimed.
   */
  @PostMapping("/jobs/{jobId}/claim")
  @Operation(
      summary = "Claim a job",
      description =
          "Atomic first-to-accept endpoint. The first engineer to call this endpoint"
              + " wins the job. Returns 200 on success, 409 Conflict if already claimed.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job claimed successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Job has already been claimed by another engineer")
  })
  public ApiResponse<JobResponse> claimJob(
      @Parameter(description = "Job ID to claim") @PathVariable final UUID jobId,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(matchingService.claimJob(jobId, engineerId), requestId(httpRequest));
  }
}

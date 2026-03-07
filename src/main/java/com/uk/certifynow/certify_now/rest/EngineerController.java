package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
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
import com.uk.certifynow.certify_now.service.EngineerAvailabilityService;
import com.uk.certifynow.certify_now.service.EngineerInsuranceService;
import com.uk.certifynow.certify_now.service.EngineerProfileService;
import com.uk.certifynow.certify_now.service.EngineerQualificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engineer")
public class EngineerController {

  private final EngineerProfileService engineerProfileService;
  private final EngineerQualificationService engineerQualificationService;
  private final EngineerInsuranceService engineerInsuranceService;
  private final EngineerAvailabilityService engineerAvailabilityService;

  public EngineerController(
      final EngineerProfileService engineerProfileService,
      final EngineerQualificationService engineerQualificationService,
      final EngineerInsuranceService engineerInsuranceService,
      final EngineerAvailabilityService engineerAvailabilityService) {
    this.engineerProfileService = engineerProfileService;
    this.engineerQualificationService = engineerQualificationService;
    this.engineerInsuranceService = engineerInsuranceService;
    this.engineerAvailabilityService = engineerAvailabilityService;
  }

  // -- Profile ----------------------------------------------------------------

  @GetMapping("/profile")
  public ApiResponse<EngineerProfileResponse> getMyProfile(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(engineerProfileService.getMyProfile(userId), requestId(httpRequest));
  }

  @PutMapping("/profile")
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
  public ApiResponse<List<QualificationResponse>> getMyQualifications(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        engineerQualificationService.getMyQualifications(userId), requestId(httpRequest));
  }

  // -- Insurance --------------------------------------------------------------

  @PostMapping("/insurance")
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
  public ApiResponse<List<InsuranceResponse>> getMyInsurance(
      final Authentication authentication, final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(engineerInsuranceService.getMyInsurance(userId), requestId(httpRequest));
  }

  // -- Availability -----------------------------------------------------------

  @PutMapping("/availability")
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
  public ResponseEntity<ApiResponse<AvailabilityResponse>> addOverride(
      @Valid @RequestBody final AvailabilityOverrideRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID userId = extractUserId(authentication);
    final AvailabilityResponse response = engineerAvailabilityService.addOverride(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  // -- Helpers ----------------------------------------------------------------

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

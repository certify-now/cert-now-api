package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class AdminEngineerController {

  private final EngineerProfileService engineerProfileService;
  private final EngineerQualificationService engineerQualificationService;
  private final EngineerInsuranceService engineerInsuranceService;

  public AdminEngineerController(
      final EngineerProfileService engineerProfileService,
      final EngineerQualificationService engineerQualificationService,
      final EngineerInsuranceService engineerInsuranceService) {
    this.engineerProfileService = engineerProfileService;
    this.engineerQualificationService = engineerQualificationService;
    this.engineerInsuranceService = engineerInsuranceService;
  }

  // -- List all engineers (paginated) -----------------------------------------

  @GetMapping
  public ApiResponse<Page<EngineerProfileResponse>> listEngineers(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      final HttpServletRequest httpRequest) {
    final int cappedSize = Math.min(size, 50);
    final Pageable pageable =
        PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    final var allProfiles = engineerProfileService.findAll();
    final int start = (int) pageable.getOffset();
    final int end = Math.min(start + pageable.getPageSize(), allProfiles.size());
    final List<EngineerProfileResponse> pageContent =
        allProfiles.subList(start < allProfiles.size() ? start : allProfiles.size(), end).stream()
            .map(
                dto -> {
                  return engineerProfileService.get(dto.getId());
                })
            .map(
                dto -> {
                  // Use getMyProfile via profile id lookup is not ideal;
                  // build a response from the DTO directly
                  return new EngineerProfileResponse(
                      dto.getId(),
                      dto.getUser(),
                      dto.getStatus(),
                      dto.getTier(),
                      dto.getBio(),
                      dto.getPreferredCertTypes(),
                      dto.getPreferredJobTimes(),
                      dto.getServiceRadiusMiles(),
                      dto.getMaxDailyJobs(),
                      dto.getIsOnline(),
                      dto.getAcceptanceRate(),
                      dto.getAvgRating(),
                      dto.getOnTimePercentage(),
                      dto.getTotalJobsCompleted(),
                      dto.getTotalReviews(),
                      dto.getStripeOnboarded(),
                      dto.getLocation(),
                      dto.getApprovedAt(),
                      dto.getLocationUpdatedAt(),
                      dto.getCreatedAt(),
                      dto.getUpdatedAt(),
                      0,
                      0,
                      null);
                })
            .collect(Collectors.toList());
    final Page<EngineerProfileResponse> resultPage =
        new PageImpl<>(pageContent, pageable, allProfiles.size());
    return ApiResponse.of(resultPage, requestId(httpRequest));
  }

  // -- Engineer detail --------------------------------------------------------

  @GetMapping("/{id}")
  public ApiResponse<EngineerProfileResponse> getEngineerDetail(
      @PathVariable final UUID id, final HttpServletRequest httpRequest) {
    final var dto = engineerProfileService.get(id);
    final EngineerProfileResponse response =
        new EngineerProfileResponse(
            dto.getId(),
            dto.getUser(),
            dto.getStatus(),
            dto.getTier(),
            dto.getBio(),
            dto.getPreferredCertTypes(),
            dto.getPreferredJobTimes(),
            dto.getServiceRadiusMiles(),
            dto.getMaxDailyJobs(),
            dto.getIsOnline(),
            dto.getAcceptanceRate(),
            dto.getAvgRating(),
            dto.getOnTimePercentage(),
            dto.getTotalJobsCompleted(),
            dto.getTotalReviews(),
            dto.getStripeOnboarded(),
            dto.getLocation(),
            dto.getApprovedAt(),
            dto.getLocationUpdatedAt(),
            dto.getCreatedAt(),
            dto.getUpdatedAt(),
            0,
            0,
            null);
    return ApiResponse.of(response, requestId(httpRequest));
  }

  // -- Approve ----------------------------------------------------------------

  @PutMapping("/{id}/approve")
  public ApiResponse<EngineerProfileResponse> approveEngineer(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerProfileService.transitionStatus(id, EngineerApplicationStatus.APPROVED, adminId),
        requestId(httpRequest));
  }

  // -- Reject -----------------------------------------------------------------

  @PutMapping("/{id}/reject")
  public ApiResponse<EngineerProfileResponse> rejectEngineer(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerProfileService.transitionStatus(id, EngineerApplicationStatus.REJECTED, adminId),
        requestId(httpRequest));
  }

  // -- Generic transition -----------------------------------------------------

  @PutMapping("/{id}/transition-status")
  public ApiResponse<EngineerProfileResponse> transitionStatus(
      @PathVariable final UUID id,
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
  public ApiResponse<QualificationResponse> verifyQualification(
      @PathVariable final UUID id,
      @PathVariable final UUID qId,
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
  public ApiResponse<InsuranceResponse> verifyInsurance(
      @PathVariable final UUID id,
      @PathVariable final UUID iId,
      @Valid @RequestBody final VerifyInsuranceRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(
        engineerInsuranceService.verifyInsurance(iId, adminId, request.verificationStatus()),
        requestId(httpRequest));
  }

  // -- Helpers ----------------------------------------------------------------

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse;
import com.uk.certifynow.certify_now.service.inspection.EpcInspectionService;
import com.uk.certifynow.certify_now.service.inspection.GasSafetyRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/inspection")
public class InspectionController {

  private final GasSafetyRecordService gasSafetyRecordService;
  private final EpcInspectionService epcInspectionService;

  public InspectionController(
      final GasSafetyRecordService gasSafetyRecordService,
      final EpcInspectionService epcInspectionService) {
    this.gasSafetyRecordService = gasSafetyRecordService;
    this.epcInspectionService = epcInspectionService;
  }

  // ── Gas Safety ─────────────────────────────────────────────────────────────

  @PostMapping("/gas-safety")
  public ResponseEntity<ApiResponse<GasSafetyRecordResponse>> submitGasSafetyRecord(
      @PathVariable final UUID jobId,
      @Valid @RequestBody final GasSafetyRecordRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    final GasSafetyRecordResponse response =
        gasSafetyRecordService.submitGasSafetyRecord(jobId, engineerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @GetMapping("/gas-safety")
  public ApiResponse<GasSafetyRecordResponse> getGasSafetyRecord(
      @PathVariable final UUID jobId,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID callerId = extractUserId(authentication);
    final GasSafetyRecordResponse response =
        gasSafetyRecordService.getGasSafetyRecord(jobId, callerId);
    return ApiResponse.of(response, requestId(httpRequest));
  }

  // ── EPC ────────────────────────────────────────────────────────────────────

  @PostMapping("/epc")
  public ResponseEntity<ApiResponse<EpcRecordResponse>> submitEpcRecord(
      @PathVariable final UUID jobId,
      @Valid @RequestBody final EpcRecordRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    final EpcRecordResponse response =
        epcInspectionService.submitEpcRecord(jobId, engineerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(response, requestId(httpRequest)));
  }

  @GetMapping("/epc")
  public ApiResponse<EpcRecordResponse> getEpcRecord(
      @PathVariable final UUID jobId,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID callerId = extractUserId(authentication);
    final EpcRecordResponse response = epcInspectionService.getEpcRecord(jobId, callerId);
    return ApiResponse.of(response, requestId(httpRequest));
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

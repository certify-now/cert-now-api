package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordResponse;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest;
import com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordResponse;
import com.uk.certifynow.certify_now.service.inspection.EpcInspectionService;
import com.uk.certifynow.certify_now.service.inspection.GasSafetyRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Inspection", description = "Engineer inspection record submission and retrieval")
public class InspectionController extends BaseController {

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
  @Operation(
      summary = "Submit a Gas Safety inspection record",
      description =
          "Records the Gas Safety inspection data collected by the engineer on-site."
              + " Can only be submitted once the job is IN_PROGRESS. Engineer access required.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Gas Safety record submitted successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required or not assigned to this job"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found")
  })
  public ResponseEntity<ApiResponse<GasSafetyRecordResponse>> submitGasSafetyRecord(
      @Parameter(description = "Job ID") @PathVariable final UUID jobId,
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
  @Operation(
      summary = "Get Gas Safety inspection record",
      description =
          "Returns the Gas Safety inspection record for the specified job."
              + " Accessible by the assigned engineer, property owner, or an admin.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Gas Safety record retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no access to this job's inspection record"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job or inspection record not found")
  })
  public ApiResponse<GasSafetyRecordResponse> getGasSafetyRecord(
      @Parameter(description = "Job ID") @PathVariable final UUID jobId,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID callerId = extractUserId(authentication);
    final GasSafetyRecordResponse response =
        gasSafetyRecordService.getGasSafetyRecord(jobId, callerId);
    return ApiResponse.of(response, requestId(httpRequest));
  }

  // ── EPC ────────────────────────────────────────────────────────────────────

  @PostMapping("/epc")
  @Operation(
      summary = "Submit an EPC inspection record",
      description =
          "Records the Energy Performance Certificate assessment data collected by the engineer."
              + " Can only be submitted once the job is IN_PROGRESS. Engineer access required.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "EPC record submitted successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — engineer access required or not assigned to this job"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found")
  })
  public ResponseEntity<ApiResponse<EpcRecordResponse>> submitEpcRecord(
      @Parameter(description = "Job ID") @PathVariable final UUID jobId,
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
  @Operation(
      summary = "Get EPC inspection record",
      description =
          "Returns the EPC inspection record for the specified job."
              + " Accessible by the assigned engineer, property owner, or an admin.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "EPC record retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no access to this job's inspection record"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job or inspection record not found")
  })
  public ApiResponse<EpcRecordResponse> getEpcRecord(
      @Parameter(description = "Job ID") @PathVariable final UUID jobId,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID callerId = extractUserId(authentication);
    final EpcRecordResponse response = epcInspectionService.getEpcRecord(jobId, callerId);
    return ApiResponse.of(response, requestId(httpRequest));
  }
}

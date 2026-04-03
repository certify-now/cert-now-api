package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.PaginationProperties;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.ProposeScheduleRequest;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.service.enums.UserRole;
import com.uk.certifynow.certify_now.service.job.JobCreationService;
import com.uk.certifynow.certify_now.service.job.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job booking and management")
public class JobController extends BaseController {

  private final JobService jobService;
  private final JobCreationService jobCreationService;
  private final PaginationProperties paginationProperties;

  public JobController(
      final JobService jobService,
      final JobCreationService jobCreationService,
      final PaginationProperties paginationProperties) {
    this.jobService = jobService;
    this.jobCreationService = jobCreationService;
    this.paginationProperties = paginationProperties;
  }

  // ── POST /api/v1/jobs — CUSTOMER only ────────────────────────────────────

  @PostMapping
  @Operation(
      summary = "Create a new job",
      description =
          "Creates a new certification job for the authenticated customer."
              + " The job enters CREATED status and awaits engineer matching.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Job created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — only customers can create jobs")
  })
  public ResponseEntity<ApiResponse<JobResponse>> createJob(
      @Valid @RequestBody final CreateJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID customerId = extractUserId(authentication);
    final JobResponse job = jobCreationService.createJob(customerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(job, requestId(httpRequest)));
  }

  // ── GET /api/v1/jobs — list (role-aware) ─────────────────────────────────

  @GetMapping
  @Operation(
      summary = "List jobs",
      description =
          "Returns a paginated list of jobs. Customers see their own jobs,"
              + " engineers see assigned jobs, and admins see all jobs."
              + " Supports optional filtering by status and certificate type.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Jobs retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated")
  })
  public ApiResponse<Page<JobSummaryResponse>> listJobs(
      @Parameter(description = "Filter by job status (e.g. CREATED, MATCHED, ACCEPTED)")
          @RequestParam(required = false)
          final String status,
      @Parameter(description = "Filter by certificate type (e.g. EPC, GAS_SAFETY)")
          @RequestParam(required = false)
          final String certificateType,
      @Parameter(description = "Page number (zero-based)") @RequestParam(defaultValue = "0")
          final int page,
      @Parameter(description = "Page size (max 50)") @RequestParam(defaultValue = "20")
          final int size,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    final int cappedSize = Math.min(size, paginationProperties.getMaxSize());
    final Pageable pageable =
        PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    final Page<JobSummaryResponse> jobs =
        jobService.listJobs(actorId, actorRole, status, certificateType, pageable);
    return ApiResponse.of(jobs, requestId(httpRequest));
  }

  // ── GET /api/v1/jobs/{id} — detail ───────────────────────────────────────

  @GetMapping("/{id}")
  @Operation(
      summary = "Get job details",
      description =
          "Returns full details of a specific job."
              + " Access is restricted to the owning customer, assigned engineer, or an admin.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no access to this job"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found")
  })
  public ApiResponse<JobResponse> getJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    return ApiResponse.of(jobService.getById(id, actorId, actorRole), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/accept — ENGINEER only ─────────────────────────

  @PutMapping("/{id}/accept")
  @Operation(
      summary = "Accept a matched job",
      description =
          "Engineer accepts a matched job and commits to a scheduled time slot."
              + " Transitions the job from MATCHED to ACCEPTED status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job accepted successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
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
        description = "Invalid state transition")
  })
  public ApiResponse<JobResponse> acceptJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      @Valid @RequestBody final AcceptJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.acceptJob(id, engineerId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/propose-schedule — ENGINEER only ───────────────

  @PutMapping("/{id}/propose-schedule")
  @Operation(
      summary = "Propose a schedule for a job",
      description =
          "The assigned engineer proposes a date and time slot for the job."
              + " Can only be called when the job is in ACCEPTED status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Schedule proposed successfully"),
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
        description = "Job not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Invalid state transition — job not in ACCEPTED status")
  })
  public ApiResponse<JobResponse> proposeSchedule(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      @Valid @RequestBody final ProposeScheduleRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(
        jobService.proposeSchedule(id, engineerId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/decline — ENGINEER only ────────────────────────

  @PutMapping("/{id}/decline")
  @Operation(
      summary = "Decline a matched job",
      description =
          "Engineer declines a matched job with an optional reason."
              + " The job returns to CREATED status for re-matching.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job declined successfully"),
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
        description = "Invalid state transition")
  })
  public ApiResponse<JobResponse> declineJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      @RequestBody(required = false) final DeclineJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    final DeclineJobRequest safeRequest = request == null ? new DeclineJobRequest(null) : request;
    return ApiResponse.of(
        jobService.declineJob(id, engineerId, safeRequest), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/en-route — ENGINEER only ───────────────────────

  @PutMapping("/{id}/en-route")
  @Operation(
      summary = "Mark engineer as en route",
      description =
          "Engineer marks themselves as on the way to the property."
              + " Transitions the job from ACCEPTED to EN_ROUTE status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job marked as en route"),
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
        description = "Invalid state transition")
  })
  public ApiResponse<JobResponse> enRoute(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.markEnRoute(id, engineerId), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/start — ENGINEER only ──────────────────────────

  @PutMapping("/{id}/start")
  @Operation(
      summary = "Start work on a job",
      description =
          "Engineer begins on-site work for the certification."
              + " Transitions the job from EN_ROUTE to IN_PROGRESS status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job started successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
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
        description = "Invalid state transition")
  })
  public ApiResponse<JobResponse> startJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      @Valid @RequestBody final StartJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.startJob(id, engineerId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/complete — ENGINEER only ───────────────────────

  @PutMapping("/{id}/complete")
  @Operation(
      summary = "Complete a job",
      description =
          "Engineer marks the certification job as completed."
              + " Transitions the job from IN_PROGRESS to COMPLETED status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job completed successfully"),
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
        description = "Invalid state transition")
  })
  public ApiResponse<JobResponse> completeJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.completeJob(id, engineerId), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/cancel — any authenticated user ────────────────

  @PutMapping("/{id}/cancel")
  @Operation(
      summary = "Cancel a job",
      description =
          "Cancels a job. Can be called by the customer, assigned engineer, or an admin."
              + " Automatic refunds may be triggered depending on the current job status.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Job cancelled successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error in request body"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no permission to cancel this job"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Job cannot be cancelled in its current state")
  })
  public ApiResponse<JobResponse> cancelJob(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      @Valid @RequestBody final CancelJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    return ApiResponse.of(
        jobService.cancelJob(id, actorId, actorRole, request), requestId(httpRequest));
  }

  // ── GET /api/v1/jobs/{id}/history ─────────────────────────────────────────

  @GetMapping("/{id}/history")
  @Operation(
      summary = "Get job status history",
      description =
          "Returns the full status transition history for a job."
              + " Access is restricted to the owning customer, assigned engineer, or an admin.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "History retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Not authenticated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden — no access to this job"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Job not found")
  })
  public ApiResponse<List<JobStatusHistoryResponse>> getHistory(
      @Parameter(description = "Job ID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    return ApiResponse.of(jobService.getHistory(id, actorId, actorRole), requestId(httpRequest));
  }
}

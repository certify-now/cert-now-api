package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.job.AcceptJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CancelJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.CreateJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.DeclineJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.JobResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobStatusHistoryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.JobSummaryResponse;
import com.uk.certifynow.certify_now.rest.dto.job.MatchJobRequest;
import com.uk.certifynow.certify_now.rest.dto.job.StartJobRequest;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import com.uk.certifynow.certify_now.service.job.JobService;
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
import org.springframework.security.core.GrantedAuthority;
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
public class JobController {

  private final JobService jobService;

  public JobController(final JobService jobService) {
    this.jobService = jobService;
  }

  // ── POST /api/v1/jobs — CUSTOMER only ────────────────────────────────────

  @PostMapping
  public ResponseEntity<ApiResponse<JobResponse>> createJob(
      @Valid @RequestBody final CreateJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID customerId = extractUserId(authentication);
    final JobResponse job = jobService.createJob(customerId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(job, requestId(httpRequest)));
  }

  // ── GET /api/v1/jobs — list (role-aware) ─────────────────────────────────

  @GetMapping
  public ApiResponse<Page<JobSummaryResponse>> listJobs(
      @RequestParam(required = false) final String status,
      @RequestParam(required = false) final String certificateType,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    final int cappedSize = Math.min(size, 50);
    final Pageable pageable =
        PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    final Page<JobSummaryResponse> jobs =
        jobService.listJobs(actorId, actorRole, status, certificateType, pageable);
    return ApiResponse.of(jobs, requestId(httpRequest));
  }

  // ── GET /api/v1/jobs/{id} — detail ───────────────────────────────────────

  @GetMapping("/{id}")
  public ApiResponse<JobResponse> getJob(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    return ApiResponse.of(jobService.getById(id, actorId, actorRole), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/match — ADMIN temp endpoint ────────────────────
  // TODO: Remove when MatchingService is built (Phase 6+)

  @PutMapping("/{id}/match")
  public ApiResponse<JobResponse> matchJob(
      @PathVariable final UUID id,
      @Valid @RequestBody final MatchJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID adminId = extractUserId(authentication);
    return ApiResponse.of(jobService.matchJob(id, adminId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/accept — ENGINEER only ─────────────────────────

  @PutMapping("/{id}/accept")
  public ApiResponse<JobResponse> acceptJob(
      @PathVariable final UUID id,
      @Valid @RequestBody final AcceptJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.acceptJob(id, engineerId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/decline — ENGINEER only ────────────────────────

  @PutMapping("/{id}/decline")
  public ApiResponse<JobResponse> declineJob(
      @PathVariable final UUID id,
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
  public ApiResponse<JobResponse> enRoute(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.markEnRoute(id, engineerId), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/start — ENGINEER only ──────────────────────────

  @PutMapping("/{id}/start")
  public ApiResponse<JobResponse> startJob(
      @PathVariable final UUID id,
      @Valid @RequestBody final StartJobRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.startJob(id, engineerId, request), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/complete — ENGINEER only ───────────────────────

  @PutMapping("/{id}/complete")
  public ApiResponse<JobResponse> completeJob(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID engineerId = extractUserId(authentication);
    return ApiResponse.of(jobService.completeJob(id, engineerId), requestId(httpRequest));
  }

  // ── PUT /api/v1/jobs/{id}/cancel — any authenticated user ────────────────

  @PutMapping("/{id}/cancel")
  public ApiResponse<JobResponse> cancelJob(
      @PathVariable final UUID id,
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
  public ApiResponse<List<JobStatusHistoryResponse>> getHistory(
      @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {
    final UUID actorId = extractUserId(authentication);
    final UserRole actorRole = extractRole(authentication);
    return ApiResponse.of(jobService.getHistory(id, actorId, actorRole), requestId(httpRequest));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private UserRole extractRole(final Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> UserRole.fromDatabaseValue(a.replace("ROLE_", "")))
        .findFirst()
        .orElse(UserRole.CUSTOMER);
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

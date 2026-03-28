package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.CertificateTypeProperties;
import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateListItemResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateTypeResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificatesListResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.GetCertificatesRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.MissingCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.ShareCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.UpdateCertificateRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.UploadCertificateRequest;
import com.uk.certifynow.certify_now.service.CustomerCertificateService;
import com.uk.certifynow.certify_now.service.CustomerCertificateService.CertificateDownloadPair;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Customer certificate management and compliance tracking")
public class CertificateController {

  private final CustomerCertificateService customerCertificateService;
  private final CertificateTypeProperties certTypeProperties;

  public CertificateController(
      final CustomerCertificateService customerCertificateService,
      final CertificateTypeProperties certTypeProperties) {
    this.customerCertificateService = customerCertificateService;
    this.certTypeProperties = certTypeProperties;
  }

  // ── GET /api/v1/certificates/types ───────────────────────────────────────

  @GetMapping("/types")
  @Operation(
      summary = "List uploadable certificate types",
      description =
          "Returns the YAML-configured list of certificate types available for manual upload,"
              + " including whether an expiry date is mandatory for each type."
              + " Requires CUSTOMER role.")
  public ApiResponse<List<CertificateTypeResponse>> getCertificateTypes(
      final HttpServletRequest httpRequest) {

    final List<CertificateTypeResponse> types =
        certTypeProperties.getUploadable().stream()
            .map(
                d ->
                    new CertificateTypeResponse(
                        d.getType(), d.getName(), d.getDescription(), d.isExpiryRequired()))
            .collect(Collectors.toList());

    return ApiResponse.of(types, requestId(httpRequest));
  }

  // ── POST /api/v1/certificates/upload ─────────────────────────────────────

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Upload a certificate",
      description =
          "Saves a landlord-supplied certificate (PAT, Fire Risk, Boiler Service, etc.) for a"
              + " property. Accepts an optional file attachment (PDF or image). Requires CUSTOMER role.")
  public ApiResponse<CertificateListItemResponse> uploadCertificate(
      @RequestParam final UUID propertyId,
      @RequestParam final String certType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate issuedAt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate expiresAt,
      @RequestParam(required = false) final String notes,
      @RequestParam(required = false) final String customTypeName,
      @RequestPart(required = false) final List<MultipartFile> files,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID customerId = extractUserId(authentication);
    final UploadCertificateRequest request =
        new UploadCertificateRequest(
            propertyId, certType, issuedAt, expiresAt, notes, customTypeName);
    return ApiResponse.of(
        customerCertificateService.uploadCertificate(customerId, request, files),
        requestId(httpRequest));
  }

  // ── GET /api/v1/certificates/my-certificates ──────────────────────────────

  @GetMapping("/my-certificates")
  @Operation(
      summary = "List customer certificates",
      description =
          "Returns all certificates for properties owned by the authenticated customer."
              + " Includes dynamically computed compliance status and missing certificate entries."
              + " Set include_history=true to also return superseded (replaced) certificates"
              + " for a full compliance audit trail."
              + " Requires CUSTOMER role.")
  public ApiResponse<CertificatesListResponse> getMyCertificates(
      @Parameter(description = "Filter by type: GAS_SAFETY, EICR, EPC, PAT")
          @RequestParam(required = false)
          final String type,
      @Parameter(
              description = "Filter by status: VALID, EXPIRED, EXPIRING_SOON, MISSING, SUPERSEDED")
          @RequestParam(required = false)
          final String status,
      @Parameter(description = "Filter by property UUID")
          @RequestParam(name = "property_id", required = false)
          final UUID propertyId,
      @Parameter(description = "Sort: expiry_asc, expiry_desc, issued_desc (default: smart sort)")
          @RequestParam(required = false)
          final String sort,
      @Parameter(description = "Include superseded (replaced) certificates for full history view")
          @RequestParam(name = "include_history", defaultValue = "false")
          final boolean includeHistory,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID customerId = extractUserId(authentication);
    final GetCertificatesRequest filters =
        new GetCertificatesRequest(type, status, propertyId, sort, includeHistory);
    return ApiResponse.of(
        customerCertificateService.getCustomerCertificates(customerId, filters),
        requestId(httpRequest));
  }

  // ── GET /api/v1/certificates/missing ─────────────────────────────────────

  @GetMapping("/missing")
  @Operation(
      summary = "Get missing certificates",
      description =
          "Analyses all customer properties and returns an actionable list of certificates"
              + " that are required but missing or expired. Requires CUSTOMER role.")
  public ApiResponse<List<MissingCertificateResponse>> getMissingCertificates(
      final Authentication authentication, final HttpServletRequest httpRequest) {

    final UUID customerId = extractUserId(authentication);
    return ApiResponse.of(
        customerCertificateService.getMissingCertificates(customerId), requestId(httpRequest));
  }

  // ── GET /api/v1/certificates/{id} ─────────────────────────────────────────

  @GetMapping("/{id}")
  @Operation(
      summary = "Get certificate detail",
      description =
          "Returns full certificate detail including inspection data."
              + " Accessible by the property owner, the issuing engineer, or an admin.")
  public ApiResponse<CertificateDetailResponse> getCertificate(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID userId = extractUserId(authentication);
    final UserRole role = extractRole(authentication);
    return ApiResponse.of(
        customerCertificateService.getCertificateForUser(id, userId, role), requestId(httpRequest));
  }

  // ── DELETE /api/v1/certificates/{id}/documents/{docId} ───────────────────

  @DeleteMapping("/{id}/documents/{docId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remove a document from a certificate",
      description =
          "Removes a specific document attachment from an uploaded certificate."
              + " Only the property owner may remove documents."
              + " Requires CUSTOMER role.")
  public void removeCertificateDocument(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      @Parameter(description = "Document UUID") @PathVariable final UUID docId,
      final Authentication authentication) {

    final UUID customerId = extractUserId(authentication);
    customerCertificateService.removeDocument(id, docId, customerId);
  }

  // ── DELETE /api/v1/certificates/{id} ─────────────────────────────────────

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a certificate",
      description =
          "Permanently removes an uploaded certificate and its document associations."
              + " Only the property owner may delete their own certificates."
              + " Deletion is blocked if a renewal reminder is linked to the certificate.")
  public void deleteCertificate(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication) {

    final UUID customerId = extractUserId(authentication);
    customerCertificateService.deleteCertificate(id, customerId);
  }

  // ── PATCH /api/v1/certificates/{id} ──────────────────────────────────────

  @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update an uploaded certificate",
      description =
          "Partially updates a customer-uploaded certificate (test date, expiry, provider/notes,"
              + " custom type name). Only the property owner may update their own certificates."
              + " Null fields in the request body are ignored (no change).")
  public ApiResponse<CertificateListItemResponse> updateCertificate(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      @RequestBody final UpdateCertificateRequest request,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID customerId = extractUserId(authentication);
    return ApiResponse.of(
        customerCertificateService.updateCertificate(id, customerId, request),
        requestId(httpRequest));
  }

  // ── GET /api/v1/certificates/{id}/download ────────────────────────────────

  @GetMapping("/{id}/download")
  @Operation(
      summary = "Download certificate PDF",
      description =
          "Returns the certificate PDF as a binary file download."
              + " EPC certificates are not downloadable — use the government register link."
              + " Accessible by the property owner, the issuing engineer, or an admin.")
  public ResponseEntity<byte[]> downloadCertificate(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication) {

    final UUID userId = extractUserId(authentication);
    final UserRole role = extractRole(authentication);
    final CertificateDownloadPair result =
        customerCertificateService.downloadCertificate(id, userId, role);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(result.meta().filename()).build());
    headers.setContentLength(result.bytes().length);

    return new ResponseEntity<>(result.bytes(), headers, HttpStatus.OK);
  }

  // ── GET /api/v1/certificates/{id}/document ────────────────────────────────

  @GetMapping("/{id}/document")
  @Operation(
      summary = "Serve certificate document",
      description =
          "Streams the attached document (any MIME type) through the API server."
              + " This avoids exposing internal MinIO URLs to mobile clients."
              + " Accessible by the property owner, issuing engineer, or an admin.")
  public ResponseEntity<byte[]> getCertificateDocument(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication) {

    final UUID userId = extractUserId(authentication);
    final UserRole role = extractRole(authentication);
    final var result = customerCertificateService.getCertificateDocument(id, userId, role);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(result.mimeType()));
    headers.setContentDisposition(ContentDisposition.inline().filename(result.filename()).build());
    headers.setContentLength(result.bytes().length);

    return new ResponseEntity<>(result.bytes(), headers, HttpStatus.OK);
  }

  // ── POST /api/v1/certificates/{id}/share ──────────────────────────────────

  @PostMapping("/{id}/share")
  @Operation(
      summary = "Share certificate",
      description =
          "Generates a cryptographically secure share token for the certificate."
              + " The returned URL can be shared with tenants, letting agents, or insurers."
              + " Idempotent — returns the same token if one already exists.")
  public ApiResponse<ShareCertificateResponse> shareCertificate(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID userId = extractUserId(authentication);
    return ApiResponse.of(
        customerCertificateService.shareCertificate(id, userId), requestId(httpRequest));
  }

  // ── DELETE /api/v1/certificates/{id}/share ────────────────────────────────

  @DeleteMapping("/{id}/share")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Revoke certificate share",
      description =
          "Revokes the share token for the certificate. Any existing share links become invalid.")
  public void revokeShare(
      @Parameter(description = "Certificate UUID") @PathVariable final UUID id,
      final Authentication authentication) {

    final UUID userId = extractUserId(authentication);
    customerCertificateService.revokeShare(id, userId);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private UUID extractUserId(final Authentication authentication) {
    return UUID.fromString((String) authentication.getPrincipal());
  }

  private UserRole extractRole(final Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> UserRole.valueOf(a.replace("ROLE_", "")))
        .findFirst()
        .orElse(UserRole.CUSTOMER);
  }

  private String requestId(final HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
  }
}

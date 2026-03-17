package com.uk.certifynow.certify_now.rest;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import com.uk.certifynow.certify_now.rest.dto.ApiResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificateDetailResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.CertificatesListResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.GetCertificatesRequest;
import com.uk.certifynow.certify_now.rest.dto.certificate.MissingCertificateResponse;
import com.uk.certifynow.certify_now.rest.dto.certificate.ShareCertificateResponse;
import com.uk.certifynow.certify_now.service.CustomerCertificateService;
import com.uk.certifynow.certify_now.service.CustomerCertificateService.CertificateDownloadPair;
import com.uk.certifynow.certify_now.service.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Customer certificate management and compliance tracking")
public class CertificateController {

  private final CustomerCertificateService customerCertificateService;

  public CertificateController(final CustomerCertificateService customerCertificateService) {
    this.customerCertificateService = customerCertificateService;
  }

  // ── GET /api/v1/certificates/my-certificates ──────────────────────────────

  @GetMapping("/my-certificates")
  @Operation(
      summary = "List customer certificates",
      description =
          "Returns all certificates for properties owned by the authenticated customer."
              + " Includes dynamically computed compliance status and missing certificate entries."
              + " Requires CUSTOMER role.")
  public ApiResponse<CertificatesListResponse> getMyCertificates(
      @Parameter(description = "Filter by type: GAS_SAFETY, EICR, EPC, PAT")
          @RequestParam(required = false)
          final String type,
      @Parameter(description = "Filter by status: VALID, EXPIRED, EXPIRING_SOON, MISSING")
          @RequestParam(required = false)
          final String status,
      @Parameter(description = "Filter by property UUID")
          @RequestParam(name = "property_id", required = false)
          final UUID propertyId,
      @Parameter(description = "Sort: expiry_asc, expiry_desc, issued_desc (default: smart sort)")
          @RequestParam(required = false)
          final String sort,
      final Authentication authentication,
      final HttpServletRequest httpRequest) {

    final UUID customerId = extractUserId(authentication);
    final GetCertificatesRequest filters =
        new GetCertificatesRequest(type, status, propertyId, sort);
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

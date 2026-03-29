package com.uk.certifynow.certify_now.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    request = mock(HttpServletRequest.class);
    when(request.getAttribute("request_id")).thenReturn("test-request-id");
  }

  @Test
  void handleBusiness_returnsCorrectStatusAndErrorCode() {
    final BusinessException ex =
        new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_VALUE", "Already exists");

    final ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "DUPLICATE_VALUE");
    assertThat(response.getBody()).containsEntry("message", "Already exists");
  }

  @Test
  void handleNotFound_returns404() {
    final EntityNotFoundException ex = new EntityNotFoundException("Job not found");

    final ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("error", "NOT_FOUND");
    assertThat(response.getBody()).containsEntry("message", "Job not found");
  }

  @Test
  void handleAccessDenied_returns403() {
    final AccessDeniedException ex = new AccessDeniedException("forbidden");

    final ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).containsEntry("error", "ACCESS_DENIED");
  }

  @Test
  void handleDataConflict_phoneInMessage_returnsPhoneExistsCode() {
    final DataIntegrityViolationException ex = buildDataIntegrityEx("phone constraint violated");

    final ResponseEntity<Map<String, Object>> response = handler.handleDataConflict(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "PHONE_EXISTS");
  }

  @Test
  void handleDataConflict_emailInMessage_returnsEmailExistsCode() {
    final DataIntegrityViolationException ex = buildDataIntegrityEx("email unique constraint");

    final ResponseEntity<Map<String, Object>> response = handler.handleDataConflict(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "EMAIL_EXISTS");
  }

  @Test
  void handleDataConflict_uniqueConstraint_returnsDuplicateValueCode() {
    final DataIntegrityViolationException ex = buildDataIntegrityEx("unique constraint violation");

    final ResponseEntity<Map<String, Object>> response = handler.handleDataConflict(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "DUPLICATE_VALUE");
  }

  @Test
  void handleDataConflict_nullValue_returnsInvalidDataCode() {
    final DataIntegrityViolationException ex = buildDataIntegrityEx("null value in column");

    final ResponseEntity<Map<String, Object>> response = handler.handleDataConflict(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "INVALID_DATA");
  }

  @Test
  void handleMissingParam_returnsBadRequest() {
    final MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("page", "int");

    final ResponseEntity<Map<String, Object>> response = handler.handleMissingParam(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "MISSING_PARAMETER");
    assertThat(response.getBody().get("message").toString()).contains("page");
  }

  @Test
  void handleBadJson_returnsBadRequest() {
    final HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
    when(ex.getMostSpecificCause()).thenReturn(new RuntimeException("Unexpected character"));

    final ResponseEntity<Map<String, Object>> response = handler.handleBadJson(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "BAD_REQUEST");
  }

  @Test
  void handleFallback_returns500() {
    final RuntimeException ex = new RuntimeException("unexpected");

    final ResponseEntity<Map<String, Object>> response = handler.handleFallback(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("error", "INTERNAL_ERROR");
  }

  @Test
  void handleBusiness_responseBodyContainsMeta() {
    final BusinessException ex =
        new BusinessException(HttpStatus.BAD_REQUEST, "TEST_ERROR", "test message");

    final ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex, request);

    assertThat(response.getBody()).containsKey("meta");
    @SuppressWarnings("unchecked")
    final Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
    assertThat(meta).containsKey("timestamp");
    assertThat(meta).containsKey("request_id");
  }

  @Test
  void handleBusiness_withDetails_includesDetailsInBody() {
    final List<Map<String, String>> details =
        List.of(Map.of("field", "email", "message", "must be valid"));
    final BusinessException ex =
        new BusinessException(
            HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Validation failed", details);

    final ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex, request);

    assertThat(response.getBody().get("details")).isEqualTo(details);
  }

  private DataIntegrityViolationException buildDataIntegrityEx(final String causeMessage) {
    final Throwable cause = new RuntimeException(causeMessage);
    return new DataIntegrityViolationException("data integrity", cause);
  }
}

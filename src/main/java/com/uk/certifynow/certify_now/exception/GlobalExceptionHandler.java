package com.uk.certifynow.certify_now.exception;

import com.uk.certifynow.certify_now.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, Object>> handleBusiness(
      final BusinessException ex, final HttpServletRequest request) {
    return build(request, ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex.getDetails());
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      final EntityNotFoundException ex, final HttpServletRequest request) {
    return build(request, HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(
      final AccessDeniedException ex, final HttpServletRequest request) {
    return build(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      final MethodArgumentNotValidException ex, final HttpServletRequest request) {
    final List<Map<String, String>> details =
        ex.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return build(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_ERROR",
        "Request validation failed",
        details);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataConflict(
      final DataIntegrityViolationException ex, final HttpServletRequest request) {
    final String rawMessage =
        ex.getMostSpecificCause() == null ? "" : ex.getMostSpecificCause().getMessage();
    final String message = rawMessage == null ? "" : rawMessage.toLowerCase();
    if (message.contains("phone")) {
      return build(
          request, HttpStatus.CONFLICT, "PHONE_EXISTS", "Phone already registered", List.of());
    }
    if (message.contains("email")) {
      return build(
          request, HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already registered", List.of());
    }
    if (message.contains("not-null")
        || message.contains("null value")
        || message.contains("not null")) {
      return build(
          request,
          HttpStatus.BAD_REQUEST,
          "INVALID_DATA",
          "A required field is missing or null",
          List.of());
    }
    if (message.contains("unique") || message.contains("duplicate")) {
      return build(
          request,
          HttpStatus.CONFLICT,
          "DUPLICATE_VALUE",
          "A unique constraint was violated",
          List.of());
    }
    return build(request, HttpStatus.CONFLICT, "DATA_CONFLICT", "Data integrity error", List.of());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(
      final MissingServletRequestParameterException ex, final HttpServletRequest request) {
    return build(
        request,
        HttpStatus.BAD_REQUEST,
        "MISSING_PARAMETER",
        "Required parameter '" + ex.getParameterName() + "' is missing",
        List.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleBadJson(
      final HttpMessageNotReadableException ex, final HttpServletRequest request) {
    return build(
        request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request payload", List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleFallback(
      final Exception ex, final HttpServletRequest request) {
    return build(
        request,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Unexpected server error",
        List.of());
  }

  private Map<String, String> toFieldError(final FieldError fieldError) {
    return Map.of(
        "field",
        fieldError.getField(),
        "message",
        fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage());
  }

  private ResponseEntity<Map<String, Object>> build(
      final HttpServletRequest request,
      final HttpStatus status,
      final String errorCode,
      final String message,
      final List<?> details) {
    final String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID);
    final String safeRequestId = requestId == null ? UUID.randomUUID().toString() : requestId;
    final Map<String, Object> body =
        Map.of(
            "error",
            errorCode,
            "message",
            message,
            "details",
            details == null ? List.of() : details,
            "meta",
            Map.of("request_id", safeRequestId, "timestamp", Instant.now()));
    return ResponseEntity.status(status).body(body);
  }
}

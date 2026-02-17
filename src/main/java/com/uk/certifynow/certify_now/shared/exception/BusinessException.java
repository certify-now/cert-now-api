package com.uk.certifynow.certify_now.shared.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;
  private final List<Map<String, String>> details;

  public BusinessException(final HttpStatus status, final String errorCode, final String message) {
    this(status, errorCode, message, List.of());
  }

  public BusinessException(
      final HttpStatus status,
      final String errorCode,
      final String message,
      final List<Map<String, String>> details) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
    this.details = details == null ? List.of() : details;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public List<Map<String, String>> getDetails() {
    return details;
  }
}

package com.uk.certifynow.certify_now.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends BusinessException {

  public EmailNotVerifiedException(final String message) {
    super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", message);
  }
}

package com.uk.certifynow.certify_now.shared.exception;

import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends BusinessException {

  public AccountNotActiveException(final String message) {
    super(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", message);
  }
}

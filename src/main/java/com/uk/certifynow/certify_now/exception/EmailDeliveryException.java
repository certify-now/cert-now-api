package com.uk.certifynow.certify_now.exception;

public class EmailDeliveryException extends RuntimeException {
  public EmailDeliveryException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

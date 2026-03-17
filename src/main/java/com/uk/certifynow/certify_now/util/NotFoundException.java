package com.uk.certifynow.certify_now.util;

import com.uk.certifynow.certify_now.exception.EntityNotFoundException;

/**
 * Legacy convenience alias — extends {@link EntityNotFoundException} so that all usages are handled
 * by {@code GlobalExceptionHandler} and return HTTP 404 consistently.
 *
 * <p>Prefer {@link EntityNotFoundException} directly for new code.
 */
public class NotFoundException extends EntityNotFoundException {

  public NotFoundException() {
    super("Resource not found");
  }

  public NotFoundException(final String message) {
    super(message);
  }
}

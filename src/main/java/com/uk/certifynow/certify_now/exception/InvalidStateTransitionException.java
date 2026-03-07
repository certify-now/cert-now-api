package com.uk.certifynow.certify_now.exception;

import com.uk.certifynow.certify_now.service.job.JobStatus;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a job state transition is not allowed by the state machine.
 *
 * <p>Maps to HTTP 409 Conflict via GlobalExceptionHandler → handleBusiness().
 */
public class InvalidStateTransitionException extends BusinessException {

  public InvalidStateTransitionException(final JobStatus from, final JobStatus to) {
    super(
        HttpStatus.CONFLICT, "INVALID_TRANSITION", "Cannot transition from " + from + " to " + to);
  }

  public InvalidStateTransitionException(final String message) {
    super(HttpStatus.CONFLICT, "INVALID_TRANSITION", message);
  }
}

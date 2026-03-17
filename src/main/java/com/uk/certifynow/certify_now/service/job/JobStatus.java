package com.uk.certifynow.certify_now.service.job;

/**
 * Valid job statuses and their allowed state transitions.
 *
 * <p>Terminal states: CERTIFIED, CANCELLED, FAILED — no further transitions allowed.
 */
public enum JobStatus {
  CREATED,
  AWAITING_ACCEPTANCE,
  MATCHED,
  ACCEPTED,
  EN_ROUTE,
  IN_PROGRESS,
  COMPLETED,
  CERTIFIED,
  FAILED,
  CANCELLED,
  ESCALATED;

  /** Statuses after which no further state transitions are allowed. */
  public static final java.util.Set<String> TERMINAL_STATUSES =
      java.util.Set.of(COMPLETED.name(), CERTIFIED.name(), CANCELLED.name(), FAILED.name());

  /**
   * Returns true if a transition from this status to {@code target} is valid.
   *
   * <p>Every state transition method in JobService must call this and throw
   * InvalidStateTransitionException if it returns false.
   */
  public boolean canTransitionTo(final JobStatus target) {
    return switch (this) {
      case CREATED ->
          target == AWAITING_ACCEPTANCE
              || target == MATCHED
              || target == CANCELLED
              || target == ESCALATED;
      case AWAITING_ACCEPTANCE -> target == MATCHED || target == ESCALATED || target == CANCELLED;
      case MATCHED -> target == ACCEPTED || target == CREATED || target == CANCELLED;
      case ACCEPTED -> target == EN_ROUTE || target == CANCELLED;
      case EN_ROUTE -> target == IN_PROGRESS || target == CANCELLED;
      case IN_PROGRESS -> target == COMPLETED;
      case COMPLETED -> target == CERTIFIED;
      case ESCALATED -> target == MATCHED || target == CANCELLED;
      case CERTIFIED, CANCELLED, FAILED -> false;
    };
  }

  /** Parse from the database String value, case-insensitive. */
  public static JobStatus fromString(final String value) {
    if (value == null) {
      throw new IllegalArgumentException("JobStatus value must not be null");
    }
    try {
      return JobStatus.valueOf(value.toUpperCase());
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown JobStatus: " + value);
    }
  }
}

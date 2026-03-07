package com.uk.certifynow.certify_now.service.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pure unit tests for {@link JobStatus#canTransitionTo(JobStatus)}.
 *
 * <p>No Spring context required — tests the enum's state machine rules directly.
 */
class JobStatusTest {

  // ────────────────────────────────────────────────────────────────────────────
  // VALID TRANSITIONS
  // ────────────────────────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} → {1} should be VALID")
  @CsvSource({
    "CREATED,    MATCHED",
    "CREATED,    CANCELLED",
    "CREATED,    ESCALATED",
    "MATCHED,    ACCEPTED",
    "MATCHED,    CREATED",
    "MATCHED,    CANCELLED",
    "ACCEPTED,   EN_ROUTE",
    "ACCEPTED,   CANCELLED",
    "EN_ROUTE,   IN_PROGRESS",
    "EN_ROUTE,   CANCELLED",
    "IN_PROGRESS, COMPLETED",
    "COMPLETED,  CERTIFIED",
    "ESCALATED,  MATCHED",
    "ESCALATED,  CANCELLED",
  })
  @DisplayName("Valid transitions return true")
  void validTransitions(final JobStatus from, final JobStatus target) {
    assertThat(from.canTransitionTo(target))
        .as("%s → %s should be a valid transition", from, target)
        .isTrue();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // INVALID TRANSITIONS
  // ────────────────────────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} → {1} should be INVALID")
  @CsvSource({
    "CREATED,     COMPLETED",
    "CREATED,     IN_PROGRESS",
    "CREATED,     ACCEPTED",
    "MATCHED,     EN_ROUTE",
    "EN_ROUTE,    COMPLETED",
    "IN_PROGRESS, CANCELLED",
    "CREATED,     CERTIFIED",
    "CREATED,     FAILED",
    "MATCHED,     COMPLETED",
    "MATCHED,     IN_PROGRESS",
    "MATCHED,     CERTIFIED",
    "ACCEPTED,    COMPLETED",
    "ACCEPTED,    IN_PROGRESS",
    "ACCEPTED,    MATCHED",
    "EN_ROUTE,    ACCEPTED",
    "EN_ROUTE,    MATCHED",
    "IN_PROGRESS, EN_ROUTE",
    "IN_PROGRESS, ACCEPTED",
    "IN_PROGRESS, MATCHED",
    "COMPLETED,   CANCELLED",
    "COMPLETED,   CREATED",
    "ESCALATED,   ACCEPTED",
    "ESCALATED,   COMPLETED",
  })
  @DisplayName("Invalid transitions return false")
  void invalidTransitions(final JobStatus from, final JobStatus target) {
    assertThat(from.canTransitionTo(target))
        .as("%s → %s should be an invalid transition", from, target)
        .isFalse();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // TERMINAL STATES — no outbound transitions at all
  // ────────────────────────────────────────────────────────────────────────────

  private static final List<JobStatus> TERMINAL_STATES =
      List.of(JobStatus.CERTIFIED, JobStatus.CANCELLED, JobStatus.FAILED);

  static Stream<Arguments> terminalStateTransitions() {
    return TERMINAL_STATES.stream()
        .flatMap(
            terminal ->
                Stream.of(JobStatus.values()).map(target -> Arguments.of(terminal, target)));
  }

  @ParameterizedTest(name = "Terminal {0} → {1} should be INVALID")
  @MethodSource("terminalStateTransitions")
  @DisplayName("Terminal states cannot transition to any status")
  void terminalStatesRejectAllTransitions(final JobStatus terminal, final JobStatus target) {
    assertThat(terminal.canTransitionTo(target))
        .as("Terminal state %s should not transition to %s", terminal, target)
        .isFalse();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // SELF-TRANSITIONS — no state should transition to itself
  // ────────────────────────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} → {0} self-transition should be INVALID")
  @EnumSource(JobStatus.class)
  @DisplayName("Self-transitions are not allowed")
  void selfTransitionsNotAllowed(final JobStatus status) {
    assertThat(status.canTransitionTo(status))
        .as("%s → %s self-transition should be invalid", status, status)
        .isFalse();
  }

  // ────────────────────────────────────────────────────────────────────────────
  // fromString()
  // ────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("fromString parses valid value case-insensitively")
  void fromStringValid() {
    assertThat(JobStatus.fromString("created")).isEqualTo(JobStatus.CREATED);
    assertThat(JobStatus.fromString("MATCHED")).isEqualTo(JobStatus.MATCHED);
    assertThat(JobStatus.fromString("En_Route")).isEqualTo(JobStatus.EN_ROUTE);
  }

  @Test
  @DisplayName("fromString throws on null")
  void fromStringNull() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> JobStatus.fromString(null));
  }

  @Test
  @DisplayName("fromString throws on unknown value")
  void fromStringUnknown() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> JobStatus.fromString("BOGUS"));
  }
}

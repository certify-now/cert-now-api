package com.uk.certifynow.certify_now.service.job;

/**
 * Who performed a cancellation action on a job. Stored as a String in the
 * database.
 */
public enum CancellationActor {
    CUSTOMER,
    ENGINEER,
    SYSTEM,
    ADMIN;

    public static CancellationActor fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("CancellationActor value must not be null");
        }
        try {
            return CancellationActor.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown CancellationActor: " + value);
        }
    }
}

package com.uk.certifynow.certify_now.service.auth;

/**
 * Sealed interface for polymorphic profile views.
 *
 * <p>This replaces the use of {@code Object} for profile data in AuthResponse, providing
 * compile-time type safety while supporting different profile types (customer vs engineer).
 *
 * <p>Sealed interfaces ensure that only known profile types can implement this interface, making
 * the code more maintainable and preventing runtime surprises.
 */
public sealed interface ProfileView permits CustomerProfileView, EngineerProfileView {}

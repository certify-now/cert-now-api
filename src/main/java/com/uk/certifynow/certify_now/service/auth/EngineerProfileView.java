package com.uk.certifynow.certify_now.service.auth;

import java.math.BigDecimal;

/**
 * Engineer profile view for API responses.
 *
 * @param status application status (e.g., APPROVED, PENDING)
 * @param tier engineer tier (BRONZE, SILVER, GOLD, PLATINUM)
 * @param bio engineer bio
 * @param serviceRadiusMiles service radius in miles
 * @param avgRating average rating (0-5)
 * @param totalReviews total number of reviews
 * @param totalJobsCompleted total jobs completed
 * @param isOnline whether engineer is currently online
 * @param stripeOnboarded whether Stripe onboarding is complete
 */
public record EngineerProfileView(
    String status,
    String tier,
    String bio,
    BigDecimal serviceRadiusMiles,
    BigDecimal avgRating,
    Integer totalReviews,
    Integer totalJobsCompleted,
    Boolean isOnline,
    Boolean stripeOnboarded)
    implements ProfileView {}

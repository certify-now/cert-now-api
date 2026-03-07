package com.uk.certifynow.certify_now.rest.dto.engineer;

/** Request body for PUT /api/v1/engineer/location. */
public record UpdateLocationRequest(double latitude, double longitude) {}

package com.uk.certifynow.certify_now.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(T data, Meta meta) {

  public static <T> ApiResponse<T> of(final T data, final String requestId) {
    final String safeRequestId =
        requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    return new ApiResponse<>(data, new Meta(safeRequestId, Instant.now()));
  }

  public record Meta(String requestId, Instant timestamp) {}
}

package com.uk.certifynow.certify_now.shared.security;

/**
 * Abstraction over the JWT denylist store.
 *
 * <p>Implementations must be fast (called on every authenticated request). Kept as an interface so
 * production Redis and in-memory test doubles can be swapped freely.
 */
public interface TokenDenylistService {

  /**
   * Adds a jti to the denylist with an explicit TTL.
   *
   * <p>The TTL should match the remaining lifetime of the access token so the entry is
   * automatically cleaned up when the token would have expired anyway.
   *
   * @param jti JWT ID claim value
   * @param ttlSeconds time-to-live in seconds
   */
  void denyToken(String jti, long ttlSeconds);

  /**
   * Returns {@code true} if the given jti has been denied (logged-out or revoked).
   *
   * <p>Implementations must fail-open: if the backing store is unavailable, return {@code false}
   * and log a warning rather than blocking all requests.
   *
   * @param jti JWT ID claim value
   * @return true if the token is on the denylist
   */
  boolean isDenied(String jti);
}

package com.uk.certifynow.certify_now.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory implementation of {@link TokenDenylistService}.
 *
 * <p>Stores token JTIs with expiry timestamps. Intended for environments where Redis is not used.
 */
@Service
public class InMemoryTokenDenylistService implements TokenDenylistService {

  private final Map<String, Instant> denylistedJtis = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryTokenDenylistService(final Clock clock) {
    this.clock = clock;
  }

  @Override
  public void denyToken(final String jti, final long ttlSeconds) {
    if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
      return;
    }
    denylistedJtis.put(jti, Instant.now(clock).plusSeconds(ttlSeconds));
  }

  @Override
  public boolean isDenied(final String jti) {
    if (jti == null || jti.isBlank()) {
      return false;
    }

    final Instant expiresAt = denylistedJtis.get(jti);
    if (expiresAt == null) {
      return false;
    }

    if (expiresAt.isBefore(Instant.now(clock))) {
      denylistedJtis.remove(jti);
      return false;
    }

    return true;
  }

  /** Test helper to reset denylist state between scenarios. */
  public void clearAll() {
    denylistedJtis.clear();
  }
}

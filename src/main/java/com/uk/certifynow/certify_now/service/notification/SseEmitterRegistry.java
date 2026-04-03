package com.uk.certifynow.certify_now.service.notification;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Maintains a per-user registry of active SSE connections.
 *
 * <p>Multiple connections per user are supported (e.g. two devices logged in at once). All stale
 * emitters (timed-out, errored, or completed) are removed automatically.
 */
@Component
public class SseEmitterRegistry {

  private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

  private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

  /**
   * Registers a new SSE emitter for the given user and returns it for use as a controller response
   * body. The emitter is automatically removed when it completes, times out, or errors.
   */
  public SseEmitter register(final UUID userId) {
    final SseEmitter emitter = new SseEmitter(0L);
    emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
    final Runnable cleanup = () -> remove(userId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    log.debug("SSE: registered connection for user={}", userId);
    return emitter;
  }

  /**
   * Pushes a named SSE event to all active connections for the given user. Connections that fail to
   * receive the event (e.g. client disconnected) are removed silently.
   */
  public void push(final UUID userId, final String event, final Object data) {
    final Set<SseEmitter> userEmitters = emitters.getOrDefault(userId, Set.of());
    if (userEmitters.isEmpty()) {
      return;
    }
    log.debug(
        "SSE: pushing event={} to user={} ({} connections)", event, userId, userEmitters.size());
    userEmitters.forEach(
        emitter -> {
          try {
            emitter.send(SseEmitter.event().name(event).data(data));
          } catch (IOException e) {
            log.debug("SSE: write failed for user={}, removing stale emitter", userId);
            remove(userId, emitter);
          }
        });
  }

  private void remove(final UUID userId, final SseEmitter emitter) {
    Optional.ofNullable(emitters.get(userId))
        .ifPresent(
            set -> {
              set.remove(emitter);
              if (set.isEmpty()) {
                emitters.remove(userId, set);
              }
            });
  }
}

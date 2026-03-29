package com.uk.certifynow.certify_now.scheduler;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Evicts date-sensitive pricing caches at midnight UTC so that rule effectiveFrom / effectiveTo
 * boundaries take effect without requiring an admin write to trigger eviction.
 *
 * <p>Both certificate-types and pricing-calc use CURRENT_DATE in their underlying queries. Although
 * Caffeine TTLs will eventually expire stale entries, this scheduler acts as a safety net to ensure
 * pricing rules always refresh at midnight regardless of TTL timing.
 */
@Component
public class PricingCacheEvictionScheduler {

  private static final Logger log = LoggerFactory.getLogger(PricingCacheEvictionScheduler.class);

  private static final List<String> DATE_SENSITIVE_CACHES =
      List.of("certificate-types", "pricing-calc");

  private final CacheManager cacheManager;

  public PricingCacheEvictionScheduler(final CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void evictAtMidnight() {
    for (final String cacheName : DATE_SENSITIVE_CACHES) {
      final var cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
        log.info("Midnight eviction: cleared cache '{}'", cacheName);
      }
    }
  }
}

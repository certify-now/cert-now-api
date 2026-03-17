package com.uk.certifynow.certify_now.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class PricingCacheEvictionSchedulerTest {

  @Mock private CacheManager cacheManager;

  private PricingCacheEvictionScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new PricingCacheEvictionScheduler(cacheManager);
  }

  @Test
  void evictAtMidnight_clearsCertificateTypesCache() {
    final Cache cache = mock(Cache.class);
    when(cacheManager.getCache("certificate-types")).thenReturn(cache);
    when(cacheManager.getCache("pricing-calc")).thenReturn(null);

    scheduler.evictAtMidnight();

    verify(cache).clear();
  }

  @Test
  void evictAtMidnight_clearsPricingCalcCache() {
    final Cache certCache = mock(Cache.class);
    final Cache pricingCache = mock(Cache.class);
    when(cacheManager.getCache("certificate-types")).thenReturn(certCache);
    when(cacheManager.getCache("pricing-calc")).thenReturn(pricingCache);

    scheduler.evictAtMidnight();

    verify(certCache).clear();
    verify(pricingCache).clear();
  }

  @Test
  void evictAtMidnight_cacheNotFound_doesNotThrow() {
    when(cacheManager.getCache("certificate-types")).thenReturn(null);
    when(cacheManager.getCache("pricing-calc")).thenReturn(null);

    // Should not throw even if caches don't exist
    scheduler.evictAtMidnight();
  }
}

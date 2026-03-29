package com.uk.certifynow.certify_now.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class DomainConfigCacheTest {

  private DomainConfig domainConfig;
  private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    domainConfig = new DomainConfig();
    cacheManager = domainConfig.cacheManager();
    // SimpleCacheManager requires afterPropertiesSet() to initialise
    ((org.springframework.cache.support.SimpleCacheManager) cacheManager).afterPropertiesSet();
  }

  @Test
  void cacheManager_containsAllExpectedCaches() {
    final List<String> expectedCaches =
        List.of(
            "address-autocomplete",
            "address-resolve",
            "postcode-centroid",
            "epc-lookup",
            "feature-flags",
            "engineer-profiles",
            "my-properties",
            "customer-certificates",
            "users",
            "users_all",
            "users_email",
            "users_me",
            "pricing-calc",
            "pricing-rules",
            "urgency-multipliers",
            "certificate-types",
            "jobs");

    for (final String cacheName : expectedCaches) {
      assertThat(cacheManager.getCache(cacheName))
          .as("Cache '%s' should be present", cacheName)
          .isNotNull();
    }
  }

  @Test
  void cacheManager_cacheNamesMatchExpected() {
    assertThat(cacheManager.getCacheNames()).hasSize(17);
  }

  @Test
  void cacheManager_returnsNullForUnknownCache() {
    assertThat(cacheManager.getCache("non-existent-cache")).isNull();
  }

  @Test
  void cacheManager_cachesPutAndGetCorrectly() {
    final var cache = cacheManager.getCache("feature-flags");
    assertThat(cache).isNotNull();

    cache.put("testKey", "testValue");
    assertThat(cache.get("testKey")).isNotNull();
    assertThat(cache.get("testKey").get()).isEqualTo("testValue");
  }

  @Test
  void cacheManager_cachesAreIndependent() {
    final var flagsCache = cacheManager.getCache("feature-flags");
    final var usersCache = cacheManager.getCache("users");
    assertThat(flagsCache).isNotNull();
    assertThat(usersCache).isNotNull();

    flagsCache.put("key1", "flagValue");
    assertThat(usersCache.get("key1")).isNull();
  }

  @Test
  void cacheManager_cacheEvictionWorks() {
    final var cache = cacheManager.getCache("my-properties");
    assertThat(cache).isNotNull();

    cache.put("owner1", "data");
    assertThat(cache.get("owner1")).isNotNull();

    cache.clear();
    assertThat(cache.get("owner1")).isNull();
  }
}

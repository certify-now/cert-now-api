package com.uk.certifynow.certify_now.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EntityScan("com.uk.certifynow.certify_now.domain")
@EnableJpaRepositories("com.uk.certifynow.certify_now.repos")
@EnableTransactionManagement
@EnableCaching
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class DomainConfig {

  @Bean(name = "auditingDateTimeProvider")
  public DateTimeProvider dateTimeProvider() {
    return () -> Optional.of(OffsetDateTime.now());
  }

  @Bean
  public CacheManager cacheManager() {
    final SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(
        List.of(
            buildCache("address-autocomplete", Duration.ofMinutes(5), 500),
            buildCache("address-resolve", Duration.ofHours(24), 1000),
            buildCache("postcode-centroid", Duration.ofHours(24), 1000),
            buildCache("epc-lookup", Duration.ofHours(24), 2000),
            buildCache("feature-flags", Duration.ofMinutes(10), 100),
            buildCache("engineer-profiles", Duration.ofMinutes(5), 500),
            buildCache("my-properties", Duration.ofMinutes(2), 500),
            buildCache("customer-certificates", Duration.ofMinutes(2), 500),
            buildCache("users", Duration.ofMinutes(10), 500),
            buildCache("users_all", Duration.ofMinutes(10), 500),
            buildCache("users_email", Duration.ofMinutes(10), 500),
            buildCache("users_me", Duration.ofMinutes(10), 500),
            buildCache("pricing-calc", Duration.ofHours(24), 1000),
            buildCache("pricing-rules", Duration.ofHours(24), 1000),
            buildCache("urgency-multipliers", Duration.ofHours(24), 500),
            buildCache("certificate-types", Duration.ofHours(24), 500),
            buildCache("jobs", Duration.ofMinutes(5), 1000)));
    return manager;
  }

  private CaffeineCache buildCache(final String name, final Duration ttl, final long maxSize) {
    return new CaffeineCache(
        name, Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
  }
}

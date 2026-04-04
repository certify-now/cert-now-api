package com.uk.certifynow.certify_now.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.uk.certifynow.certify_now.service.certificate.CustomerCertificateService;
import com.uk.certifynow.certify_now.service.engineer.EngineerProfileService;
import com.uk.certifynow.certify_now.service.epc.EpcLookupService;
import com.uk.certifynow.certify_now.service.feature.FeatureFlagService;
import com.uk.certifynow.certify_now.service.inspection.EpcInspectionService;
import com.uk.certifynow.certify_now.service.inspection.GasSafetyRecordService;
import com.uk.certifynow.certify_now.service.property.AddressLookupService;
import com.uk.certifynow.certify_now.service.property.PropertyService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

/**
 * Verifies that the expected caching annotations are present on service methods. This is a
 * compile-time-style safety net: if someone accidentally removes a caching annotation during a
 * refactor, this test will catch it.
 */
class CachingAnnotationsTest {

  // ── AddressLookupService ────────────────────────────────────────────────

  @Test
  void addressLookupService_autocomplete_isCacheable() throws Exception {
    final Method method = AddressLookupService.class.getMethod("autocomplete", String.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("address-autocomplete");
  }

  @Test
  void addressLookupService_resolve_isCacheable() throws Exception {
    final Method method = AddressLookupService.class.getMethod("resolve", String.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("address-resolve");
  }

  @Test
  void addressLookupService_lookupPostcodeCentroid_isCacheable() throws Exception {
    final Method method =
        AddressLookupService.class.getMethod("lookupPostcodeCentroid", String.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("postcode-centroid");
    assertThat(annotation.unless()).isEqualTo("#result == null");
  }

  // ── EpcLookupService ───────────────────────────────────────────────────

  @Test
  void epcLookupService_lookup_isCacheable() throws Exception {
    final Method method = EpcLookupService.class.getMethod("lookup", String.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("epc-lookup");
    assertThat(annotation.unless()).isEqualTo("#result == null");
  }

  // ── FeatureFlagService ─────────────────────────────────────────────────

  @Test
  void featureFlagService_findAll_isCacheable() throws Exception {
    final Method method = FeatureFlagService.class.getMethod("findAll");
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("feature-flags");
  }

  @Test
  void featureFlagService_get_isCacheable() throws Exception {
    final Method method = FeatureFlagService.class.getMethod("get", java.util.UUID.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("feature-flags");
  }

  @Test
  void featureFlagService_create_evictsCache() throws Exception {
    final Method method =
        FeatureFlagService.class.getMethod(
            "create", com.uk.certifynow.certify_now.model.FeatureFlagDTO.class);
    final CacheEvict annotation = method.getAnnotation(CacheEvict.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("feature-flags");
    assertThat(annotation.allEntries()).isTrue();
  }

  @Test
  void featureFlagService_update_evictsCache() throws Exception {
    final Method method =
        FeatureFlagService.class.getMethod(
            "update",
            java.util.UUID.class,
            com.uk.certifynow.certify_now.model.FeatureFlagDTO.class);
    final CacheEvict annotation = method.getAnnotation(CacheEvict.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("feature-flags");
    assertThat(annotation.allEntries()).isTrue();
  }

  @Test
  void featureFlagService_delete_evictsCache() throws Exception {
    final Method method = FeatureFlagService.class.getMethod("delete", java.util.UUID.class);
    final CacheEvict annotation = method.getAnnotation(CacheEvict.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("feature-flags");
    assertThat(annotation.allEntries()).isTrue();
  }

  // ── EngineerProfileService ─────────────────────────────────────────────

  @Test
  void engineerProfileService_getMyProfile_isCacheable() throws Exception {
    final Method method =
        EngineerProfileService.class.getMethod("getMyProfile", java.util.UUID.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("engineer-profiles");
  }

  @Test
  void engineerProfileService_getProfile_isCacheable() throws Exception {
    final Method method =
        EngineerProfileService.class.getMethod("getProfile", java.util.UUID.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("engineer-profiles");
  }

  @Test
  void engineerProfileService_updateProfile_evictsCache() throws Exception {
    final Method method =
        EngineerProfileService.class.getMethod(
            "updateProfile",
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.engineer.UpdateEngineerProfileRequest.class);
    final CacheEvict annotation = method.getAnnotation(CacheEvict.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("engineer-profiles");
    assertThat(annotation.allEntries()).isTrue();
  }

  @Test
  void engineerProfileService_setOnlineStatus_evictsCache() throws Exception {
    final Method method =
        EngineerProfileService.class.getMethod(
            "setOnlineStatus", java.util.UUID.class, boolean.class);
    final CacheEvict annotation = method.getAnnotation(CacheEvict.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("engineer-profiles");
    assertThat(annotation.allEntries()).isTrue();
  }

  // ── PropertyService ────────────────────────────────────────────────────

  @Test
  void propertyService_getMyPropertiesWithCompliance_isCacheable() throws Exception {
    final Method method =
        PropertyService.class.getMethod("getMyPropertiesWithCompliance", java.util.UUID.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("my-properties");
  }

  @Test
  void propertyService_create_evictsMyPropertiesCache() throws Exception {
    final Method method =
        PropertyService.class.getMethod(
            "create",
            com.uk.certifynow.certify_now.rest.dto.property.CreatePropertyRequest.class,
            java.util.UUID.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).isNotNull();
    boolean hasMyProperties = false;
    boolean hasComplianceVault = false;
    for (final CacheEvict evict : caching.evict()) {
      if ("my-properties".equals(evict.value()[0])) hasMyProperties = true;
      if ("compliance-vault".equals(evict.value()[0])) hasComplianceVault = true;
    }
    assertThat(hasMyProperties).as("Should evict my-properties").isTrue();
    assertThat(hasComplianceVault).as("Should evict compliance-vault").isTrue();
  }

  @Test
  void propertyService_softDelete_evictsMyPropertiesCache() throws Exception {
    final Method method =
        PropertyService.class.getMethod("softDelete", java.util.UUID.class, java.util.UUID.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).isNotNull();
    boolean hasMyProperties = false;
    boolean hasComplianceVault = false;
    for (final CacheEvict evict : caching.evict()) {
      if ("my-properties".equals(evict.value()[0])) hasMyProperties = true;
      if ("compliance-vault".equals(evict.value()[0])) hasComplianceVault = true;
    }
    assertThat(hasMyProperties).as("Should evict my-properties").isTrue();
    assertThat(hasComplianceVault).as("Should evict compliance-vault").isTrue();
  }

  // ── CustomerCertificateService ─────────────────────────────────────────

  @Test
  void customerCertificateService_getCustomerCertificates_isCacheable() throws Exception {
    final Method method =
        CustomerCertificateService.class.getMethod(
            "getCustomerCertificates",
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.certificate.GetCertificatesRequest.class);
    final Cacheable annotation = method.getAnnotation(Cacheable.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("customer-certificates");
  }

  @Test
  void customerCertificateService_uploadCertificate_evictsAllThreeCaches() throws Exception {
    final Method method =
        CustomerCertificateService.class.getMethod(
            "uploadCertificate",
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.certificate.UploadCertificateRequest.class,
            java.util.List.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).isNotNull();
    assertThat(caching.evict()).hasSize(3);

    boolean hasCustomerCerts = false;
    boolean hasMyProperties = false;
    boolean hasComplianceVault = false;
    for (final CacheEvict evict : caching.evict()) {
      if ("customer-certificates".equals(evict.value()[0])) hasCustomerCerts = true;
      if ("my-properties".equals(evict.value()[0])) hasMyProperties = true;
      if ("compliance-vault".equals(evict.value()[0])) hasComplianceVault = true;
    }
    assertThat(hasCustomerCerts).as("Should evict customer-certificates").isTrue();
    assertThat(hasMyProperties).as("Should evict my-properties").isTrue();
    assertThat(hasComplianceVault).as("Should evict compliance-vault").isTrue();
  }

  @Test
  void customerCertificateService_deleteCertificate_evictsAllThreeCaches() throws Exception {
    final Method method =
        CustomerCertificateService.class.getMethod(
            "deleteCertificate", java.util.UUID.class, java.util.UUID.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).isNotNull();
    assertThat(caching.evict()).hasSize(3);
  }

  @Test
  void customerCertificateService_updateCertificate_evictsAllThreeCaches() throws Exception {
    final Method method =
        CustomerCertificateService.class.getMethod(
            "updateCertificate",
            java.util.UUID.class,
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.certificate.UpdateCertificateRequest.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).isNotNull();
    assertThat(caching.evict()).hasSize(3);
  }

  // ── GasSafetyRecordService ────────────────────────────────────────────

  @Test
  void gasSafetyRecordService_submitGasSafetyRecord_evictsAllThreeCaches() throws Exception {
    final Method method =
        GasSafetyRecordService.class.getMethod(
            "submitGasSafetyRecord",
            java.util.UUID.class,
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.inspection.GasSafetyRecordRequest.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).as("@Caching annotation must be present").isNotNull();
    assertThat(caching.evict()).hasSize(4);

    boolean hasJobs = false;
    boolean hasCustomerCerts = false;
    boolean hasMyProperties = false;
    boolean hasComplianceVault = false;
    for (final CacheEvict evict : caching.evict()) {
      if ("jobs".equals(evict.value()[0])) hasJobs = true;
      if ("customer-certificates".equals(evict.value()[0])) hasCustomerCerts = true;
      if ("my-properties".equals(evict.value()[0])) hasMyProperties = true;
      if ("compliance-vault".equals(evict.value()[0])) hasComplianceVault = true;
    }
    assertThat(hasJobs).as("Should evict jobs").isTrue();
    assertThat(hasCustomerCerts).as("Should evict customer-certificates").isTrue();
    assertThat(hasMyProperties).as("Should evict my-properties").isTrue();
    assertThat(hasComplianceVault).as("Should evict compliance-vault").isTrue();
  }

  // ── EpcInspectionService ──────────────────────────────────────────────

  @Test
  void epcInspectionService_submitEpcRecord_evictsAllFourCaches() throws Exception {
    final Method method =
        EpcInspectionService.class.getMethod(
            "submitEpcRecord",
            java.util.UUID.class,
            java.util.UUID.class,
            com.uk.certifynow.certify_now.rest.dto.inspection.EpcRecordRequest.class);
    final Caching caching = method.getAnnotation(Caching.class);
    assertThat(caching).as("@Caching annotation must be present").isNotNull();
    assertThat(caching.evict()).hasSize(4);

    boolean hasJobs = false;
    boolean hasCustomerCerts = false;
    boolean hasMyProperties = false;
    boolean hasComplianceVault = false;
    for (final CacheEvict evict : caching.evict()) {
      if ("jobs".equals(evict.value()[0])) hasJobs = true;
      if ("customer-certificates".equals(evict.value()[0])) hasCustomerCerts = true;
      if ("my-properties".equals(evict.value()[0])) hasMyProperties = true;
      if ("compliance-vault".equals(evict.value()[0])) hasComplianceVault = true;
    }
    assertThat(hasJobs).as("Should evict jobs").isTrue();
    assertThat(hasCustomerCerts).as("Should evict customer-certificates").isTrue();
    assertThat(hasMyProperties).as("Should evict my-properties").isTrue();
    assertThat(hasComplianceVault).as("Should evict compliance-vault").isTrue();
  }
}

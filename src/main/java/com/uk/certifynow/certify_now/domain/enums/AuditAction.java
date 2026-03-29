package com.uk.certifynow.certify_now.domain.enums;

/** Actions recorded in the {@code audit_log} table. */
public enum AuditAction {
  // Tier 1 — Pricing
  PRICING_RULE_CREATED,
  PRICING_RULE_UPDATED,
  URGENCY_MULTIPLIER_UPDATED,

  // Tier 1 — Feature flags
  FEATURE_FLAG_CREATED,
  FEATURE_FLAG_UPDATED,
  FEATURE_FLAG_DELETED,

  // Tier 2 — User lifecycle
  USER_SOFT_DELETED,
  USER_RESTORED,

  // Tier 2 — Property lifecycle
  PROPERTY_SOFT_DELETED,
  PROPERTY_RESTORED,

  // Tier 2 — Engineer lifecycle
  ENGINEER_APPROVED
}

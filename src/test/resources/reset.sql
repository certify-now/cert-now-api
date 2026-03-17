-- Reset all tables between integration tests.
-- All FK constraints use CASCADE, so ordering is derived from leaf tables first.
TRUNCATE TABLE
  gas_safety_record,
  epc_assessment,
  certificate,
  job_status_history,
  job_match_log,
  payment,
  job,
  property,
  pricing_modifier,
  pricing_rule,
  urgency_multiplier,
  email_verification_tokens,
  refresh_token,
  user_consent,
  customer_profile,
  engineer_profile,
  "user"
  CASCADE;

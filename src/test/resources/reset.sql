-- Reset all tables between integration tests.
-- All FK constraints use CASCADE, so ordering is derived from leaf tables first.
TRUNCATE TABLE email_verification_tokens, refresh_token, user_consent, customer_profile, engineer_profile, "user" CASCADE;

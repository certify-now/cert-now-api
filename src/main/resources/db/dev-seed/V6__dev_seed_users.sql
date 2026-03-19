-- ============================================================
-- V6: Dev seed users
--
-- This migration only runs in the dev profile because
-- application-dev.yml includes classpath:db/dev-seed in the
-- Flyway locations list.
--
-- Three fixed-UUID users are created so DevAuthFilter can
-- inject a stable principal without a JWT token:
--
--   CUSTOMER  00000000-0000-0000-0000-000000000001  dev-customer@certifynow.local
--   ENGINEER  00000000-0000-0000-0000-000000000002  dev-engineer@certifynow.local
--   ADMIN     00000000-0000-0000-0000-000000000003  dev-admin@certifynow.local
--
-- Password for all three: Dev1234!
-- ============================================================

-- ── CUSTOMER user ────────────────────────────────────────────
INSERT INTO "user" (
    id, email, full_name, password_hash,
    role, status, auth_provider,
    email_verified, phone_verified,
    created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'dev-customer@certifynow.local',
    'Dev Customer',
    '$2a$10$bsphxgU.0qZXhNY5vL297OVEZBP7CjVXDNjAr09vAJsboKiIhxCcO',
    'CUSTOMER', 'ACTIVE', 'EMAIL',
    true, false,
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM "user" WHERE id = '00000000-0000-0000-0000-000000000001'
);

INSERT INTO customer_profile (
    id, user_id,
    is_letting_agent, total_properties,
    notification_prefs,
    created_at, updated_at,
    date_created, last_updated
)
SELECT
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000001',
    false, 0,
    '{}',
    NOW(), NOW(), NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM customer_profile WHERE user_id = '00000000-0000-0000-0000-000000000001'
);

-- ── ENGINEER user ────────────────────────────────────────────
INSERT INTO "user" (
    id, email, full_name, password_hash,
    role, status, auth_provider,
    email_verified, phone_verified,
    created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000002',
    'dev-engineer@certifynow.local',
    'Dev Engineer',
    '$2a$10$bsphxgU.0qZXhNY5vL297OVEZBP7CjVXDNjAr09vAJsboKiIhxCcO',
    'ENGINEER', 'ACTIVE', 'EMAIL',
    true, false,
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM "user" WHERE id = '00000000-0000-0000-0000-000000000002'
);

INSERT INTO engineer_profile (
    id, user_id,
    status, tier,
    acceptance_rate, avg_rating,
    is_online, max_daily_jobs,
    on_time_percentage, service_radius_miles,
    stripe_onboarded,
    total_jobs_completed, total_reviews,
    created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000022',
    '00000000-0000-0000-0000-000000000002',
    'APPROVED', 'GOLD',
    100.00, 5.00,
    true, 10,
    100.00, 15.0,
    false,
    0, 0,
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM engineer_profile WHERE user_id = '00000000-0000-0000-0000-000000000002'
);

-- ── ADMIN user ───────────────────────────────────────────────
INSERT INTO "user" (
    id, email, full_name, password_hash,
    role, status, auth_provider,
    email_verified, phone_verified,
    created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000003',
    'dev-admin@certifynow.local',
    'Dev Admin',
    '$2a$10$bsphxgU.0qZXhNY5vL297OVEZBP7CjVXDNjAr09vAJsboKiIhxCcO',
    'ADMIN', 'ACTIVE', 'EMAIL',
    true, false,
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM "user" WHERE id = '00000000-0000-0000-0000-000000000003'
);

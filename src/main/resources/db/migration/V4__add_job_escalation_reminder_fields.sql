-- Tracks how many admin alerts have been dispatched for an escalated job.
-- DEFAULT 0 ensures existing rows are back-filled without a table rewrite.
ALTER TABLE job ADD COLUMN IF NOT EXISTS admin_alert_count INTEGER NOT NULL DEFAULT 0;

-- Records when the last admin alert (initial or reminder) was sent.
-- NULL for jobs escalated before this column was introduced; the reminder
-- scheduler handles NULL via an explicit IS NULL OR < cutoff predicate.
ALTER TABLE job ADD COLUMN IF NOT EXISTS last_admin_alert_at TIMESTAMPTZ NULL;

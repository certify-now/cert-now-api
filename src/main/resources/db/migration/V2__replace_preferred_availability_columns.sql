ALTER TABLE job DROP COLUMN IF EXISTS preferred_days;
ALTER TABLE job DROP COLUMN IF EXISTS preferred_time_slots;
ALTER TABLE job ADD COLUMN IF NOT EXISTS preferred_availability TEXT;

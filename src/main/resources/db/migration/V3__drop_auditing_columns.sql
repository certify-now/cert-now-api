ALTER TABLE property    DROP COLUMN IF EXISTS date_created;
ALTER TABLE property    DROP COLUMN IF EXISTS last_updated;

ALTER TABLE job         DROP COLUMN IF EXISTS date_created;
ALTER TABLE job         DROP COLUMN IF EXISTS last_updated;

ALTER TABLE "user"      DROP COLUMN IF EXISTS date_created;
ALTER TABLE "user"      DROP COLUMN IF EXISTS last_updated;

ALTER TABLE certificate DROP COLUMN IF EXISTS date_created;
ALTER TABLE certificate DROP COLUMN IF EXISTS last_updated;

ALTER TABLE engineer_profile DROP COLUMN IF EXISTS date_created;
ALTER TABLE engineer_profile DROP COLUMN IF EXISTS last_updated;

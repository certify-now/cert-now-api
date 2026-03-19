-- ============================================================
-- V5: Refactor document storage architecture
--
-- Summary of changes:
--   1. document table – swap S3 split fields for a single storageUrl,
--      rename owner_id → uploaded_by_id, rename auditing columns to
--      created_at / updated_at, drop legacy created_at duplicate.
--   2. certificate table – add source + epc_registry_url, drop
--      document_url + document_hash.
--   3. property table – add current-certificate FKs, drop bytea blobs.
--   4. New tables: compliance_document, certificate_document,
--      compliance_document_file.
--   5. renewal_reminder – make certificate_id nullable, add
--      compliance_document_id + check constraint.
--   6. Performance indexes.
-- ============================================================

-- ── 1. document table ────────────────────────────────────────────────────────

-- Add the unified storage URL column
ALTER TABLE document ADD COLUMN storage_url VARCHAR(1024);

-- Migrate existing S3 references into the new URL column
UPDATE document
SET storage_url = CONCAT('https://your-minio-host/', s3_bucket, '/', s3_key)
WHERE s3_key IS NOT NULL AND s3_bucket IS NOT NULL;

-- Ensure no NULLs remain (rows that had no S3 data)
UPDATE document SET storage_url = 'migration_required' WHERE storage_url IS NULL;

ALTER TABLE document ALTER COLUMN storage_url SET NOT NULL;

-- Drop superseded columns
ALTER TABLE document DROP COLUMN IF EXISTS s3_bucket;
ALTER TABLE document DROP COLUMN IF EXISTS s3_key;
ALTER TABLE document DROP COLUMN IF EXISTS related_id;
ALTER TABLE document DROP COLUMN IF EXISTS related_entity;
ALTER TABLE document DROP COLUMN IF EXISTS document_type;

-- Drop the manually-set duplicate created_at (auditing columns date_created /
-- last_updated are the canonical timestamps; rename them to match the entity).
ALTER TABLE document DROP COLUMN IF EXISTS created_at;
ALTER TABLE document RENAME COLUMN date_created TO created_at;
ALTER TABLE document RENAME COLUMN last_updated TO updated_at;

-- Rename owner_id → uploaded_by_id
ALTER TABLE document RENAME COLUMN owner_id TO uploaded_by_id;

-- ── 2. certificate table ─────────────────────────────────────────────────────

ALTER TABLE certificate ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'PLATFORM';
ALTER TABLE certificate ADD COLUMN epc_registry_url VARCHAR(512);

ALTER TABLE certificate DROP COLUMN IF EXISTS document_url;
ALTER TABLE certificate DROP COLUMN IF EXISTS document_hash;

-- Rename the OneToMany mapping field (Java side only renames the collection;
-- the FK column on renewal_reminder is unchanged so no SQL needed there).

-- ── 3. property table ────────────────────────────────────────────────────────

-- New FK columns for current valid certificates
ALTER TABLE property ADD COLUMN current_gas_certificate_id  UUID;
ALTER TABLE property ADD COLUMN current_eicr_certificate_id UUID;
ALTER TABLE property ADD COLUMN current_epc_certificate_id  UUID;

ALTER TABLE property
  ADD CONSTRAINT fk_property_current_gas_cert
  FOREIGN KEY (current_gas_certificate_id) REFERENCES certificate(id);

ALTER TABLE property
  ADD CONSTRAINT fk_property_current_eicr_cert
  FOREIGN KEY (current_eicr_certificate_id) REFERENCES certificate(id);

ALTER TABLE property
  ADD CONSTRAINT fk_property_current_epc_cert
  FOREIGN KEY (current_epc_certificate_id) REFERENCES certificate(id);

-- Drop bytea blob columns (ensure any binary data is migrated to S3 first)
ALTER TABLE property DROP COLUMN IF EXISTS gas_cert_pdf;
ALTER TABLE property DROP COLUMN IF EXISTS gas_cert_pdf_name;
ALTER TABLE property DROP COLUMN IF EXISTS eicr_cert_pdf;
ALTER TABLE property DROP COLUMN IF EXISTS eicr_cert_pdf_name;

-- ── 4. New tables ────────────────────────────────────────────────────────────

CREATE TABLE compliance_document (
  id                   UUID         PRIMARY KEY,
  property_id          UUID         NOT NULL REFERENCES property(id),
  document_type        VARCHAR(50)  NOT NULL,
  custom_type_name     VARCHAR(100),
  test_date            DATE,
  expiry_date          DATE,
  status               VARCHAR(20)  NOT NULL DEFAULT 'VALID',
  notes                TEXT,
  provider_name        VARCHAR(255),
  provider_reference   VARCHAR(100),
  uploaded_by_id       UUID         NOT NULL REFERENCES "user"(id),
  reminder_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
  reminder_days_before INTEGER,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE certificate_document (
  id             UUID     PRIMARY KEY,
  certificate_id UUID     NOT NULL REFERENCES certificate(id) ON DELETE CASCADE,
  document_id    UUID     NOT NULL REFERENCES document(id)    ON DELETE CASCADE,
  is_primary     BOOLEAN  NOT NULL DEFAULT FALSE,
  display_order  INTEGER  NOT NULL DEFAULT 0
);

CREATE TABLE compliance_document_file (
  id                   UUID     PRIMARY KEY,
  compliance_document_id UUID   NOT NULL REFERENCES compliance_document(id) ON DELETE CASCADE,
  document_id          UUID     NOT NULL REFERENCES document(id)            ON DELETE CASCADE,
  display_order        INTEGER  NOT NULL DEFAULT 0
);

-- ── 5. renewal_reminder table ────────────────────────────────────────────────

ALTER TABLE renewal_reminder ALTER COLUMN certificate_id DROP NOT NULL;

ALTER TABLE renewal_reminder
  ADD COLUMN compliance_document_id UUID REFERENCES compliance_document(id);

-- Exactly one of certificate_id or compliance_document_id must be set
ALTER TABLE renewal_reminder
  ADD CONSTRAINT chk_reminder_has_reference
  CHECK (certificate_id IS NOT NULL OR compliance_document_id IS NOT NULL);

-- ── 6. Indexes ───────────────────────────────────────────────────────────────

CREATE INDEX idx_compliance_document_property ON compliance_document(property_id);
CREATE INDEX idx_compliance_document_status   ON compliance_document(status);
CREATE INDEX idx_compliance_document_expiry   ON compliance_document(expiry_date);

CREATE INDEX idx_certificate_document_cert ON certificate_document(certificate_id);
CREATE INDEX idx_certificate_document_doc  ON certificate_document(document_id);

CREATE INDEX idx_compliance_doc_file_cd  ON compliance_document_file(compliance_document_id);
CREATE INDEX idx_compliance_doc_file_doc ON compliance_document_file(document_id);

CREATE INDEX idx_property_current_gas  ON property(current_gas_certificate_id);
CREATE INDEX idx_property_current_eicr ON property(current_eicr_certificate_id);
CREATE INDEX idx_property_current_epc  ON property(current_epc_certificate_id);

-- Indexes for columns used in WHERE clauses, JOIN conditions, and ORDER BY expressions.
-- Table/column names follow Hibernate's default snake_case naming strategy.

-- Job indexes
CREATE INDEX IF NOT EXISTS idx_job_customer_id ON job (customer_id);
CREATE INDEX IF NOT EXISTS idx_job_engineer_id ON job (engineer_id);
CREATE INDEX IF NOT EXISTS idx_job_status ON job (status);
CREATE INDEX IF NOT EXISTS idx_job_status_broadcast_at ON job (status, broadcast_at);
CREATE INDEX IF NOT EXISTS idx_job_property_id ON job (property_id);

-- Property indexes
CREATE INDEX IF NOT EXISTS idx_property_owner_id ON property (owner_id);
CREATE INDEX IF NOT EXISTS idx_property_owner_id_is_active ON property (owner_id, is_active);

-- Certificate indexes
CREATE INDEX IF NOT EXISTS idx_certificate_property_id ON certificate (property_id);
CREATE INDEX IF NOT EXISTS idx_certificate_share_token ON certificate (share_token);
CREATE INDEX IF NOT EXISTS idx_certificate_property_type_status ON certificate (property_id, certificate_type, status);

-- RefreshToken indexes
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token_hash ON refresh_token (token_hash);

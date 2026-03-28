CREATE TABLE share_tokens (
  id UUID NOT NULL,
  token VARCHAR(64) NOT NULL,
  certificate_id UUID NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  accessed_at TIMESTAMP,
  access_count INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT pk_share_tokens PRIMARY KEY (id),
  CONSTRAINT uq_share_tokens_token UNIQUE (token),
  CONSTRAINT fk_share_tokens_certificate FOREIGN KEY (certificate_id) REFERENCES certificates(id) ON DELETE CASCADE,
  CONSTRAINT fk_share_tokens_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_share_tokens_token ON share_tokens(token);
CREATE INDEX idx_share_tokens_certificate_id ON share_tokens(certificate_id);
CREATE INDEX idx_share_tokens_expires_at ON share_tokens(expires_at);

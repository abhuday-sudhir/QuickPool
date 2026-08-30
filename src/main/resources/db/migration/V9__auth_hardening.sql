-- Refresh tokens become tracked and single-use. Each carries a jti; refreshing
-- revokes the presented one and issues a new pair. Presenting an already-revoked
-- token means it leaked, so the whole family is burned.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_reason VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id, revoked);

-- Soft-delete: ride and booking history stays intact for the other party,
-- but every piece of personal data is scrubbed.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

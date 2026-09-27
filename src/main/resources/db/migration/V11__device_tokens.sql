-- FCM registration tokens. One row per device, several rows per user: a person may
-- carry a phone and a tablet, and a push must reach both.
CREATE TABLE device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    -- Unique across the whole table, not per user: FCM reissues the same token to whoever
    -- installs next on that device. Re-registering must move the row to the new owner
    -- rather than leaving the previous user's push going to a device they no longer hold.
    token TEXT NOT NULL UNIQUE,
    platform VARCHAR(16) NOT NULL DEFAULT 'android',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);

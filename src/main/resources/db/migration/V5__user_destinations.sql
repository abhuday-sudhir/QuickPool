-- Durable record of where each user actually travels. Redis caches the derived
-- "frequent destinations" list on top of this; losing Redis must never lose data.
CREATE TABLE user_destinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    -- lat/lng rounded to 4dp (~11m) so re-picking the same place bumps the counter
    geo_key VARCHAR(32) NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_destination UNIQUE (user_id, geo_key)
);

CREATE INDEX idx_user_destinations_ranked
    ON user_destinations(user_id, use_count DESC, last_used_at DESC);

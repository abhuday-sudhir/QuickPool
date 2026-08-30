-- Addresses a user explicitly saves (Home, Work, or a custom label).
-- Distinct from user_destinations, which is passive travel history.
CREATE TABLE saved_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    label VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_saved_address_label UNIQUE (user_id, label)
);

CREATE INDEX idx_saved_addresses_user ON saved_addresses(user_id, created_at);

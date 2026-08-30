-- One emergency contact per user, offered as the default recipient when sharing a trip.
CREATE TABLE emergency_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(80) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_emergency_contact_user UNIQUE (user_id)
);

-- A shareable, expiring, revocable link to follow a trip. Readable without login,
-- which is the whole point: the person you send it to is not a QuickPool user.
CREATE TABLE trip_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    ride_offer_id UUID NOT NULL REFERENCES ride_offers(id),
    shared_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_shares_ride ON trip_shares(ride_offer_id);

-- Last known position of a running ride, so a share link has something to show.
-- Live updates still go over STOMP; this is only the most recent fix.
ALTER TABLE ride_offers ADD COLUMN last_lat DOUBLE PRECISION;
ALTER TABLE ride_offers ADD COLUMN last_lng DOUBLE PRECISION;
ALTER TABLE ride_offers ADD COLUMN last_location_at TIMESTAMP;

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE email_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    email VARCHAR(160) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_email_verification_active ON email_verifications(user_id) WHERE verified = false;

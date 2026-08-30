-- One registered vehicle per driver, so passengers can identify the car.
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    make VARCHAR(40) NOT NULL,
    model VARCHAR(40) NOT NULL,
    color VARCHAR(24) NOT NULL,
    plate VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_vehicle_user UNIQUE (user_id)
);

-- One rating per (ride, rater, ratee) so nobody can pile on.
CREATE TABLE ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_offer_id UUID NOT NULL REFERENCES ride_offers(id),
    rater_id UUID NOT NULL REFERENCES users(id),
    ratee_id UUID NOT NULL REFERENCES users(id),
    stars SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_rating_per_ride UNIQUE (ride_offer_id, rater_id, ratee_id),
    CONSTRAINT no_self_rating CHECK (rater_id <> ratee_id)
);

CREATE INDEX idx_ratings_ratee ON ratings(ratee_id);

CREATE TABLE user_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(id),
    blocked_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_block UNIQUE (blocker_id, blocked_id),
    CONSTRAINT no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);

CREATE TABLE user_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id),
    reported_id UUID NOT NULL REFERENCES users(id),
    ride_offer_id UUID REFERENCES ride_offers(id),
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT no_self_report CHECK (reporter_id <> reported_id)
);

CREATE INDEX idx_user_reports_reported ON user_reports(reported_id, status);

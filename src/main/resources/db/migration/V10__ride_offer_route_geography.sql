-- 3.1: back the corridor search with a spatial index instead of an in-memory scan.
-- The docker-compose Postgres image (postgis/postgis:16-3.4) ships the extension; this
-- just switches it on for this database.
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE ride_offers ADD COLUMN route geography(LineString, 4326);

-- Kept in sync by a trigger, not by application code, so it can never drift from
-- origin/destination and every insert path (including ones added later) gets it for free.
CREATE OR REPLACE FUNCTION ride_offers_set_route() RETURNS trigger AS $$
BEGIN
    NEW.route := ST_MakeLine(
        ST_SetSRID(ST_MakePoint(NEW.origin_lng, NEW.origin_lat), 4326),
        ST_SetSRID(ST_MakePoint(NEW.destination_lng, NEW.destination_lat), 4326)
    )::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ride_offers_set_route
    BEFORE INSERT OR UPDATE OF origin_lat, origin_lng, destination_lat, destination_lng
    ON ride_offers
    FOR EACH ROW
    EXECUTE FUNCTION ride_offers_set_route();

-- Backfill existing rows — the trigger only fires on future inserts/updates.
UPDATE ride_offers SET route = ST_MakeLine(
    ST_SetSRID(ST_MakePoint(origin_lng, origin_lat), 4326),
    ST_SetSRID(ST_MakePoint(destination_lng, destination_lat), 4326)
)::geography;

CREATE INDEX idx_ride_offers_route ON ride_offers USING GIST (route);

-- seed.sql: 200K rides for ride_offers (route is filled by the trigger)
SELECT setseed(0.42);

DROP TABLE IF EXISTS seed_drivers;
CREATE TEMP TABLE seed_drivers AS
  SELECT id, (row_number() OVER () - 1) AS rn FROM users LIMIT 50;
-- if this has 0 rows, register a few users through your API first

INSERT INTO ride_offers
  (driver_id, origin_lat, origin_lng, destination_lat, destination_lng,
   departure_time, seats_total, seats_available, price_per_seat, status,
   created_at, updated_at)
SELECT
  d.id,
  b.olat, b.olng, b.dlat, b.dlng,
  CASE WHEN b.r < 0.80 THEN now() + random() * interval '30 days'   -- ACTIVE/FULL: future
       ELSE now() - random() * interval '60 days' END,               -- old rides: past
  4,
  CASE WHEN b.r < 0.70 THEN 1 + floor(random() * 4)::int ELSE 0 END,
  50 + floor(random() * 200)::int,
  CASE WHEN b.r < 0.70 THEN 'ACTIVE'
       WHEN b.r < 0.80 THEN 'FULL'
       WHEN b.r < 0.90 THEN 'COMPLETED'
       ELSE 'CANCELLED' END,
  now() - interval '1 day',
  now()
FROM (
  SELECT
    g,
    random() AS r,
    random() < 0.80 AS popular,             -- 80% on the Delhi -> Gurgaon style corridor
    CASE WHEN random() < 0.80 THEN 28.61 + (random() - 0.5) * 0.30
         ELSE 8 + random() * 20 END AS olat,
    CASE WHEN random() < 0.80 THEN 77.20 + (random() - 0.5) * 0.30
         ELSE 70 + random() * 15 END AS olng,
    CASE WHEN random() < 0.80 THEN 28.46 + (random() - 0.5) * 0.30
         ELSE 8 + random() * 20 END AS dlat,
    CASE WHEN random() < 0.80 THEN 77.03 + (random() - 0.5) * 0.30
         ELSE 70 + random() * 15 END AS dlng
  FROM generate_series(1, 200000) g
) b
JOIN seed_drivers d ON d.rn = b.g % (SELECT count(*) FROM seed_drivers);

ANALYZE ride_offers;

SELECT status, count(*) FROM ride_offers GROUP BY status;
SELECT count(*) AS missing_route FROM ride_offers WHERE route IS NULL;  -- must be 0
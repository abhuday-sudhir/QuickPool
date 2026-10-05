EXPLAIN (ANALYZE, BUFFERS)
SELECT id, driver_id, origin_lat, origin_lng, destination_lat, destination_lng,
       departure_time, seats_total, seats_available, price_per_seat, status,
       last_lat, last_lng, last_location_at, created_at, updated_at
FROM ride_offers
WHERE status = 'ACTIVE'
  AND departure_time BETWEEN '2026-10-06 00:00:00' AND '2026-10-13 00:00:00'
  AND driver_id <> '00000000-0000-0000-0000-000000000000'
  AND seats_available > 0
  AND ST_DWithin(route, ST_SetSRID(ST_MakePoint(77.10, 28.55), 4326)::geography, 2000)
  AND ST_DWithin(route, ST_SetSRID(ST_MakePoint(77.04, 28.47), 4326)::geography, 2000);
-- Corridor search was planned as a BitmapAnd of idx_ride_offers_status_time and
-- idx_ride_offers_route. The planner costs the route index at ~126 rows, but a 2km corridor
-- over a city's worth of rides matches ~19k bounding boxes, so most of the query time went
-- into building a bitmap that the time filter then threw away.
--
-- One GiST index over (departure_time, route) lets both conditions prune in a single index
-- walk. It is partial on the rows search can actually return, so booked-out and finished
-- rides never bloat it. btree_gist supplies the GiST operator class for the timestamp column.
--
-- idx_ride_offers_status_time stays: the reminder/expiry lookups filter on status and time
-- with no route predicate, and a plain btree is the right index for those.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE INDEX idx_ride_offers_search_time_route
    ON ride_offers USING GIST (departure_time, route)
    WHERE status = 'ACTIVE' AND seats_available > 0;

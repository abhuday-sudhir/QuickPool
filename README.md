# QuickPool — Carpool Backend: Project Documentation

**Purpose of this document:** complete context of what has been built, how it's structured, what works, what's stubbed, and what's left — intended to be handed to another LLM (or developer) to continue the work with full context.

**Owner/repo:** `github.com/abhuday-sudhir/QuickRide` (private)
**Status:** Working local dev environment with core auth, ride offer/booking flow, live location via WebSocket, and basic observability. Not yet production-deployed. No Android app built yet (separate, out of scope for this backend work).

---

## 1. What this project is

A carpooling backend where:
- **Drivers** post ride offers (origin → destination, departure time, seats).
- **Passengers** search for rides whose route passes near their pickup/drop points, and book a seat.
- Booking a seat notifies the driver; the driver cancelling a ride notifies affected passengers.
- Once a ride is active, driver and passenger can share live location with each other over WebSocket.
- All key user actions are recorded in an activity log for audit purposes.

This is explicitly being built toward **production use**, to be integrated with a separately-built Android app (not part of this backend work).

---

## 2. Tech stack

| Layer | Choice | Version (where known) |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Build tool | Maven | 3.9.11 |
| Database | PostgreSQL + PostGIS | postgis/postgis:16-3.4 image (Postgres 16.4) |
| ORM | Spring Data JPA / Hibernate | Hibernate 7.4.1.Final |
| Migrations | Flyway | 12.4.0 |
| Auth | Custom OTP + JWT (jjwt) | jjwt 0.12.6 |
| Real-time | Spring WebSocket + STOMP | — |
| API docs | springdoc-openapi | 2.8.5 |
| Metrics | Micrometer + Prometheus + Grafana | — |
| Boilerplate reduction | Lombok (`@Data`, `@Slf4j`) | 1.18.46 |
| Dependency injection style | Field-level `@Autowired` (project preference, not constructor injection) | — |

**Package root:** `com.QuickRide`

---

## 3. Local environment setup

### Docker Compose (`docker-compose.yml`)

Runs three containers: Postgres+PostGIS, Prometheus, Grafana.

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    environment:
      POSTGRES_DB: carpool
      POSTGRES_USER: carpool
      POSTGRES_PASSWORD: carpool
    ports:
      - "5434:5432"
    volumes:
      - carpool_pgdata:/var/lib/postgresql/data

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana

volumes:
  carpool_pgdata:
  grafana_data:
```

**Important — non-default Postgres port (5434), not 5432:** the developer's Mac had *two* other processes already bound to 5432 and 5433 (a native Postgres install, and something backing local pgAdmin), causing silent connection failures that looked like credential errors. Postgres runs on **host port 5434** to avoid this. `application.yml` datasource URL must match: `jdbc:postgresql://localhost:5434/carpool`.

### `prometheus.yml` (project root, alongside docker-compose.yml)
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'carpool-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

### `application.yml` (key settings)
```yaml
spring:
  application:
    name: Carpool
  datasource:
    url: jdbc:postgresql://localhost:5434/carpool
    username: carpool
    password: carpool
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate   # Hibernate never auto-creates schema; Flyway owns all DDL
    show-sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  jwt:
    secret: "change-this-to-a-long-random-string-at-least-32-chars-for-production"  # TODO: move to env var before deploy
    access-token-minutes: 15
    refresh-token-days: 30

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, metrics
  endpoint:
    health:
      show-details: always
```

### Key `pom.xml` dependencies (beyond Spring Boot defaults)
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-websocket`, `spring-boot-starter-actuator`
- `spring-boot-starter-flyway` **and** `flyway-database-postgresql` (both required — see gotcha #2 below)
- `postgresql` (JDBC driver)
- `org.projectlombok:lombok` (optional=true) + explicit `maven-compiler-plugin` annotation processor path
- `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.6
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` 2.8.5
- `io.micrometer:micrometer-registry-prometheus`

### Running it
```bash
docker compose up -d
mvn spring-boot:run
```
App on `localhost:8080`. Swagger UI: `localhost:8080/swagger-ui/index.html`. Prometheus targets: `localhost:9090/targets`. Grafana: `localhost:3000` (admin/admin).

---

## 4. Environment gotchas hit during setup (so they aren't re-debugged)

1. **Flyway migration files MUST start with capital `V`, exactly `V<n>__description.sql`** (double underscore). A file named `01_init...sql` (no `V`, single underscore) is silently ignored by Flyway — no error, it just never runs. This cost significant debugging time twice.
2. **Spring Boot 4.x needs `spring-boot-starter-flyway` in addition to `flyway-core`.** Boot 4 split auto-configuration into small per-feature modules; having `flyway-core` alone on the classpath does not trigger Spring's Flyway auto-configuration. You also separately need `flyway-database-postgresql` for Flyway itself to support Postgres — both dependencies are required together.
3. **Port conflicts on 5432/5433 on the dev machine** — resolved by moving Postgres's host port to 5434 (see §3).
4. **Lombok requires both the IntelliJ Lombok plugin AND "Enable annotation processing"** turned on in IntelliJ settings, in addition to being a Maven dependency — missing either causes `log`/getters/setters to fail to resolve in the IDE (and sometimes in actual compilation if the Maven compiler plugin's annotation processor path isn't set).
5. **WebSocket handshake requests must be explicitly `permitAll()`'d in Spring Security**, separately from normal API auth. The JWT for WebSocket auth arrives as a query param (`?token=...`), not an `Authorization` header, so Spring Security's default filter chain rejects the handshake before the custom `JwtHandshakeInterceptor` ever runs, unless `/ws/**` is permitted at the HTTP security layer.
6. **The `RideOfferResponseDto` empty-`{}` serialization bug is UNRESOLVED as of this document.** A ride offer creation call returns `HTTP 200` with body `{}` (no fields) even though the row is correctly persisted in the database. Root cause not yet confirmed — leading theories were a stale build (clean rebuild was suggested but result not confirmed back) or a Lombok annotation mismatch on that specific DTO (it's intentionally NOT `@Data`, kept as `@Getter` with a manual constructor since it's a read-only projection — worth re-verifying this wasn't accidentally changed during the project-wide `@Data`/`@Autowired` conversion). **This needs to be resolved/re-verified by whoever picks this up next.**

---

## 5. Package structure

```
com.QuickRide
├── entity/          # JPA entities (@Entity classes, mostly @Data)
├── enums/           # RideStatus, BookingStatus (referenced as com.QuickRide.entity in some earlier code — verify actual package)
├── repository/       # Spring Data JPA interfaces
├── dto/              # Request/response shapes (mix of "dtos" and "dto" naming seen in screenshots — verify consistency)
├── service/          # Business logic
├── controller/       # REST + WebSocket message-mapped controllers
├── config/           # Security, JWT filter, WebSocket, OpenAPI, rate limiting
├── exception/         # Custom exceptions + GlobalExceptionHandler
├── util/              # GeoUtils (haversine + point-to-segment distance)
└── Carpool.java       # main class
```

**Note for whoever continues this:** the developer's IDE screenshots showed both `dto` and `dtos` as folder names at different points, and enums possibly split into their own `enums` package separately from `entity`. **Verify actual current package names in the codebase before generating new code that imports from them** — a mismatch here caused at least one full round of compile errors already.

---

## 6. Database schema (as built via Flyway migrations)

### V1 — `users`, `otp_verifications`
```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(150),
    role_flags SMALLINT NOT NULL DEFAULT 1,   -- bitmask: 1=rider, 2=driver, 4=admin
    rating_avg NUMERIC(3,2) DEFAULT 5.0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE otp_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,          -- BCrypt hash, never plaintext
    expires_at TIMESTAMP NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Enforces: a phone can have at most ONE unverified/pending OTP at a time
CREATE UNIQUE INDEX idx_otp_active_phone ON otp_verifications(phone) WHERE verified = false;
```

### V2 — `ride_offers`, `bookings`
```sql
CREATE TABLE ride_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES users(id),
    origin_lat DOUBLE PRECISION NOT NULL,
    origin_lng DOUBLE PRECISION NOT NULL,
    destination_lat DOUBLE PRECISION NOT NULL,
    destination_lng DOUBLE PRECISION NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    seats_total SMALLINT NOT NULL,
    seats_available SMALLINT NOT NULL,
    price_per_seat NUMERIC(8,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / FULL / CANCELLED / COMPLETED
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_ride_offers_status_time ON ride_offers(status, departure_time);

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_offer_id UUID NOT NULL REFERENCES ride_offers(id),
    passenger_id UUID NOT NULL REFERENCES users(id),
    seats_booked SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',  -- CONFIRMED / CANCELLED / COMPLETED
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_ride_offer ON bookings(ride_offer_id);
```

### V3 — `activity_logs`
```sql
CREATE TABLE activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,               -- nullable: some actions (e.g. failed OTP for unknown phone) have no resolved user
    action VARCHAR(50) NOT NULL,       -- e.g. OTP_REQUESTED, USER_LOGIN, RIDE_CREATED, BOOKING_CANCELLED
    entity_type VARCHAR(50),
    entity_id UUID,
    metadata TEXT,               -- small JSON string
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_logs_user ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_entity ON activity_logs(entity_type, entity_id);
```

**Known gap:** `ride_offers.status` conflates "open for booking" with "currently happening." There is no `IN_PROGRESS` state — `ACTIVE` is used for both "not yet departed, bookable" and implicitly for "ride is live right now." This matters for the location feature (see §9) — currently nothing stops location updates from being sent for a ride that hasn't actually started yet.

---

## 7. Core entities

- **`User`** — `id, phone, name, email, roleFlags (bitmask: 1=rider,2=driver,4=admin), ratingAvg, createdAt`. Has `isAdmin()` helper checking bit 4.
- **`OtpVerification`** — `id, phone, otpHash, expiresAt, attempts, verified, createdAt`.
- **`RideOffer`** — `id, driverId, originLat/Lng, destinationLat/Lng, departureTime, seatsTotal, seatsAvailable, pricePerSeat, status (RideStatus enum), createdAt, updatedAt`.
- **`Booking`** — `id, rideOfferId, passengerId, seatsBooked, status (BookingStatus enum), createdAt, updatedAt`.
- **`ActivityLog`** — `id, userId (nullable), action, entityType, entityId, metadata, createdAt`.

Enums: `RideStatus { ACTIVE, FULL, CANCELLED, COMPLETED }`, `BookingStatus { CONFIRMED, CANCELLED, COMPLETED }`.

Most entities use Lombok `@Data`. **Caution flagged during build:** `@Data` on JPA entities auto-generates `equals()/hashCode()/toString()` from all fields, which is a known footgun once bidirectional `@OneToMany`/`@ManyToOne` relationships are added (infinite recursion). Currently safe because no entity relationships (JPA associations) exist yet — all references are plain `UUID` foreign key fields, not mapped relationships. **If relationships are added later, revisit this.**

---

## 8. Auth flow (OTP + JWT)

**Login is phone + OTP based, no passwords.**

1. `POST /api/v1/auth/otp/request` `{"phone": "..."}` — generates a 6-digit OTP, BCrypt-hashes it before storing, 5-minute expiry, enforces a 30-second resend cooldown per phone (via a `ConflictException` if violated). **Currently the OTP is only logged to the server console (`log.info`) — this is explicitly a local-dev stub, not wired to a real SMS provider yet.** Must be replaced with Twilio/MSG91 (or similar) before any real user uses this.
2. `POST /api/v1/auth/otp/verify` `{"phone": "...", "otp": "..."}` — validates against the hash, checks expiry and max-attempts (5), creates the `User` row if phone is new (defaults `roleFlags=3`, i.e. both rider+driver — a testing convenience, may want to revisit for real onboarding), issues JWT access token (15 min) + refresh token (30 days).
3. `POST /api/v1/auth/refresh` `{"refreshToken": "..."}` — validates the token is genuinely a `type:refresh` token (rejects access tokens used here), issues a **new** access+refresh pair (rotation — old refresh token should be discarded client-side after use).

**JWT details:** HMAC-SHA (`Jwts.builder()...signWith(key())`), secret from `app.jwt.secret` (currently a placeholder string in `application.yml` — must move to an environment variable before any real deployment), claims include `sub` (userId), `type` (`access`/`refresh`), `iat`, `exp`.

**Request authentication:** `JwtAuthenticationFilter` (a `OncePerRequestFilter`) reads `Authorization: Bearer <token>`, validates, sets `UsernamePasswordAuthenticationToken` with the userId (a `UUID`) as principal — controllers retrieve it via `(UUID) authentication.getPrincipal()`.

**Rate limiting:** `RateLimitFilter` — in-memory, per-IP, max 5 requests/minute to `/api/v1/auth/otp/request`, returns HTTP 429 when exceeded. **Explicitly flagged as not multi-instance-safe** — works correctly on one server instance only; must move to Redis (`INCR`+`EXPIRE`) before horizontal scaling.

---

## 9. Ride offer / booking flow

**Matching approach (deliberate v1 simplification):** no PostGIS/routing-API corridor matching yet. Uses a **straight-line proximity check** — `GeoUtils.distancePointToSegmentMeters()` computes the flat-earth-projected distance from a search point to the straight line between a ride's origin and destination, using a fixed `CORRIDOR_RADIUS_METERS = 2000` threshold. This is a known, intentional approximation — swap for PostGIS `ST_DWithin`/route-polyline matching later without needing to change the surrounding architecture.

**Search** (`POST /api/v1/ride-offers/search`): filters `ACTIVE` offers with `seatsAvailable > 0` within a time window (defaults to next 3 hours if not specified), then filters by corridor proximity for both pickup and drop points.

**Booking concurrency safety:** `RideOfferRepository.findByIdForUpdate()` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` — a real DB row lock — so two simultaneous booking requests for the last seat can't both succeed. `BookingService.bookRide()` and `cancelBooking()` are both `@Transactional`.

**Notifications:** `NotificationService` interface with one implementation, `LoggingNotificationService`, which just logs (`log.info("[NOTIFY] ...")`) — **explicitly a stub for local dev**, designed to be swapped for real APNs/FCM push later without touching calling code. Triggered on: new booking (→ notifies driver), booking cancelled (→ notifies driver), driver cancels ride (→ notifies all affected passengers, and cascades their bookings to `CANCELLED`).

**Activity logging:** wired into `OtpService` (`OTP_REQUESTED`, `USER_REGISTERED`/`USER_LOGIN`, `OTP_VERIFY_FAILED`), `RideOfferService` (`RIDE_CREATED`, `RIDE_CANCELLED`), `BookingService` (`BOOKING_CREATED`, `BOOKING_CANCELLED`) via one-line explicit calls (deliberately not AOP/automatic, for readability/debuggability at this stage).

**Known temporary shortcut, already partially resolved:** controllers originally took `driverId`/`passengerId` as raw `@RequestParam` (no auth) — this has since been replaced with pulling the authenticated user from `Authentication.getPrincipal()` once JWT was wired in. Confirm no leftover `@RequestParam` versions remain anywhere before deployment.

---

## 10. Live location (WebSocket)

**Pattern:** one STOMP "room" per ride. Clients publish to `/app/ride/{rideId}/location`, server broadcasts to `/topic/ride/{rideId}/location`.

- **Endpoint:** `/ws`, registered via `WebSocketConfig` with `JwtHandshakeInterceptor` — auth happens at handshake time (token passed as `?token=...` query param, since WebSocket handshakes can't easily carry custom headers on all mobile platforms), not per-message.
- **`JwtHandshakeInterceptor`** validates the JWT and stashes `userId` into the WebSocket session attributes (`attributes.put("userId", userId)`).
- **`LocationController.updateLocation()`** (a `@MessageMapping` method, not a REST controller) checks the sender is either the ride's driver or a `CONFIRMED` passenger on that ride before broadcasting — rejects with `ForbiddenException`-style logic otherwise. Broadcasts a `LocationBroadcastDto {userId, role, lat, lng, timestamp}`.
- **Security config gotcha:** `/ws/**` must be `permitAll()`'d in the main `SecurityConfig` HTTP filter chain, since the token arrives as a query param and the normal `Authorization`-header-based JWT filter never sees it — otherwise Spring Security rejects the handshake before `JwtHandshakeInterceptor` runs.

**Known gaps, not yet built:**
1. No persistence of "last known location" — a client that reconnects mid-ride gets nothing until the next live update. Planned fix (from original system design): Redis `GEOADD`/hash write inside `updateLocation()`, plus a REST endpoint to fetch last-known-position on reconnect.
2. **No check that the ride is actually "in progress"** before accepting location updates — only checks the sender is a legitimate participant, not that the ride has started. Ties back to the `RideStatus` gap noted in §6 (no distinct `IN_PROGRESS` state).
3. Automatic, periodic sending of location is **entirely a client (Android) responsibility** — the backend only relays what it's sent. The Android app will need a foreground service (Android requirement for background location) sending updates every 3-5s while a ride is active, and must stop on ride completion/cancellation.

**Manual testing tool built:** `websocket-test-client.html` (browser-based STOMP test page, uses native WebSocket + stomp.js, NOT SockJS — matches the server's plain WebSocket endpoint config). Lets you paste a JWT + ride ID, connect, and either send your real browser-geolocated position or a manually-typed lat/lng. Two browser tabs (driver token + passenger token, same ride ID) can be used to verify bidirectional live broadcast.

---

## 11. Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps custom exceptions to proper HTTP status + a consistent JSON error envelope `{code, message, timestamp}`:
- `NotFoundException` → 404
- `BadRequestException` → 400
- `ConflictException` → 409
- `ForbiddenException` → 403
- `MethodArgumentNotValidException` (i.e. `@Valid` failures) → 400 with per-field error map
- Anything else (`Exception.class` catch-all) → 500 with a generic client-safe message; full exception is logged server-side via `log.error(...)`, never leaked to the client.

**Note:** Bean Validation annotations (`@NotNull`, `@Min`, etc.) on request DTOs, and `@Valid` on controller method parameters, were recommended but **not confirmed as actually added** — worth verifying/completing this, since right now most DTOs likely have no field-level validation despite the handler being ready to catch it.

---

## 12. Admin capability

Basic groundwork only: `User.roleFlags` bit `4` = admin (bitmask now `1=rider, 2=driver, 4=admin`). `AdminAuthService.requireAdmin(userId)` throws `ForbiddenException` if the user isn't flagged as admin — intended to be called as the first line of any future admin-only controller method. **No admin-only endpoints or features have actually been built yet** (no user management, KYC review, dispute handling, etc.) — this was deliberately deferred until a concrete admin feature is needed, to avoid building unused scaffolding.

To manually grant admin to a test user:
```sql
UPDATE users SET role_flags = role_flags | 4 WHERE phone = '+91...';
```

---

## 13. API surface (as built so far)

```
POST   /api/v1/auth/otp/request        { phone }                          → 200, OTP logged server-side
POST   /api/v1/auth/otp/verify         { phone, otp }                     → { userId, accessToken, refreshToken }
POST   /api/v1/auth/refresh            { refreshToken }                   → { userId, accessToken, refreshToken }

POST   /api/v1/ride-offers             { originLat/Lng, destinationLat/Lng, departureTime, seatsTotal, pricePerSeat }
                                        (auth: driver, from JWT)           → RideOfferResponseDto  [KNOWN BUG: returns {}]
POST   /api/v1/ride-offers/search      { pickupLat/Lng, dropLat/Lng, earliestTime?, latestTime? } → [RideOfferResponseDto]
PUT    /api/v1/ride-offers/{id}/cancel (auth: must be the ride's driver)  → 200

POST   /api/v1/bookings                { rideOfferId, seatsBooked }
                                        (auth: passenger, from JWT)        → booking UUID
PUT    /api/v1/bookings/{id}/cancel    (auth: must be the booking's passenger) → 200

WS     /ws?token=<jwt>                 STOMP handshake
  APP    /app/ride/{rideId}/location     client → server, publish location
  TOPIC  /topic/ride/{rideId}/location   server → client, broadcast to room

GET    /swagger-ui/index.html          interactive API docs (configured with bearer-auth "Authorize" button)
GET    /actuator/prometheus            metrics scrape endpoint
GET    /actuator/health
```

---

## 14. Observability

- **Micrometer + Actuator** expose `/actuator/prometheus`, tagged with `application: carpool-backend`.
- **Prometheus** (Docker container) scrapes that endpoint every 15s, targeting `host.docker.internal:8080` (container-to-host networking).
- **Grafana** (Docker container) connects to Prometheus (`http://prometheus:9090`, container-to-container name) as a data source. Community dashboard **ID 12900** ("Spring Boot Statistics") imported for out-of-box request rate/latency/error-rate/JVM panels.
- Per-route breakdown achievable via PromQL against `http_server_requests_seconds_*` metrics (auto-tagged by `uri`/`method`/`status`).
- **Explicitly noted as most valuable once actually deployed**, not just local — set up now mainly to establish the habit/pipeline.

---

## 15. Git / account setup

Repo is private, owned by GitHub account `abhuday-sudhir`. Developer's Mac had a different GitHub account's credentials cached (causing an initial 403). Resolved via a **separate SSH key + host alias**, not by logging out of any account:
- Dedicated key: `~/.ssh/id_ed25519_personal`
- `~/.ssh/config` alias: `Host github.com-personal` → `HostName github.com`, `IdentityFile ~/.ssh/id_ed25519_personal`
- Remote set via: `git remote set-url origin git@github.com-personal:abhuday-sudhir/QuickRide.git`
- Local (repo-level, not global) identity: `git config user.name`/`user.email` set to the personal account's details, so other repos/company account are unaffected.

This lets multiple GitHub identities coexist permanently on one machine — each repo's remote URL determines which key/identity is used, no active logout/login switching required.

---

## 16. Immediate priorities for whoever continues this

**Must fix / verify (blocking further reliable testing):**
1. `RideOfferResponseDto` returns `{}` instead of actual fields on ride creation — root cause not confirmed. Check for stale build, verify the DTO wasn't accidentally converted to `@Data` without a no-args constructor, verify Jackson can access the fields.
2. Confirm actual current package structure (`dto` vs `dtos`, `enums` location) matches what's assumed in this doc.
3. Confirm Bean Validation (`@Valid` + constraint annotations) is actually implemented on request DTOs — it was recommended but implementation wasn't confirmed back.

**Known, accepted stubs to eventually replace (not bugs, deliberate placeholders):**
- OTP delivery: console log → real SMS provider (Twilio/MSG91).
- Notifications: console log → real APNs/FCM push.
- JWT secret: hardcoded placeholder string → environment variable / secrets manager.
- Rate limiting: in-memory per-instance → Redis-backed, before multi-instance deployment.
- Matching: straight-line corridor check → PostGIS/routing-API-based real route matching.
- Live location: no persistence of last-known-position → Redis geo-store + REST fetch-on-reconnect endpoint.

**Real gaps to design/build next:**
- `RideStatus` needs an `IN_PROGRESS` (or similar) state distinct from `ACTIVE`, so location-sharing and "ride actually started" logic have something correct to check against.
- Admin feature set (currently only the `isAdmin()`/`requireAdmin()` scaffolding exists, no actual admin endpoints).
- Driver vehicle details + KYC document upload (was in the original system design doc, not yet built).
- Ratings/reviews post-ride.
- Pagination on the ride search endpoint.
- Scheduled job to auto-expire/complete stale ride offers.
- Dockerizing the Spring Boot app itself (currently only its dependencies — Postgres, Prometheus, Grafana — run in Docker; the app runs via `mvn spring-boot:run` directly on the host).

---

## 17. Testing reference (manual curl flow used throughout development)

```bash
# Request OTP (check server console log for the actual code — not yet real SMS)
curl -X POST localhost:8080/api/v1/auth/otp/request \
  -H "Content-Type: application/json" -d '{"phone":"+919000000001"}'

# Verify and capture token programmatically (avoids copy-paste truncation errors)
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"phone":"+919000000001","otp":"<code from logs>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# Create a ride (as driver)
curl -X POST localhost:8080/api/v1/ride-offers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"originLat":27.1767,"originLng":78.0081,"destinationLat":27.2046,"destinationLng":78.0092,"departureTime":"2026-08-15T14:00:00","seatsTotal":3}'

# Book it (as a different user/token, the passenger)
curl -X POST localhost:8080/api/v1/bookings \
  -H "Authorization: Bearer $PASSENGER_TOKEN" -H "Content-Type: application/json" \
  -d '{"rideOfferId":"<id from create response>","seatsBooked":1}'

# Refresh an access token
curl -X POST localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" -d '{"refreshToken":"<refresh token>"}'
```

Database inspection:
```bash
docker exec -it quickride-postgres-1 psql -U carpool -d carpool
\dt public.*
SELECT * FROM ride_offers;
SELECT * FROM activity_logs ORDER BY created_at DESC;
```

---

*End of documentation. This reflects the state of the project as of the most recent work session — verify against the actual current codebase before relying on any specific detail, particularly the items flagged as "not yet confirmed" in §16.*
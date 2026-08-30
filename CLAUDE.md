# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

QuickPool is a **Java/Spring Boot carpooling backend** (drivers post ride offers, passengers
search/book seats, live location shared over WebSocket during active rides). It is not a
polling/voting app despite the name.

Naming has drifted across the repo — be aware so you don't get confused mid-task:
- Java package: `com.QuickPool`
- Maven `artifactId`: `quickpool`
- README still refers to the project as "QuickRide" / "carpool-backend" in places (leftover
  from an earlier name)
- Recent commits renamed things to "QuickPoll"

There is no frontend or mobile client in this repo — a separate Android app is planned to
consume this API but lives out of scope, in a different repo.

See `README.md` for the full architecture write-up (auth flow, DB schema, API surface,
observability, known gaps) — it's an extensive LLM-handoff doc; read it before large changes.

## Commands

```bash
docker compose up -d      # start Postgres+PostGIS (port 5434, NOT 5432), Prometheus, Grafana
mvn spring-boot:run       # run the app -> http://localhost:8080
mvn test                  # run all tests
mvn test -Dtest=ApplicationTests               # run the single existing test class
mvn test -Dtest=ApplicationTests#contextLoads  # run its one test method
mvn compile                # compile
mvn package                 # build the jar
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Prometheus: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (admin/admin)
- Postgres runs on **5434**, not the default 5432 — `application.yml`'s datasource URL must match.
- No linter/formatter is configured (no ESLint/Checkstyle/Prettier/Spotless) and there is no CI
  (no `.github/workflows`) — don't go looking for a lint command.

## Architecture

Package layout under `src/main/java/com/QuickPool/`: `controller`, `service`, `repository`,
`entity`, `dtos`, `enums`, `exception`, `filter`, `config`, `utils`.

- **Auth**: custom phone + OTP → JWT, no passwords. REST requests are authenticated via
  `filter/JwtAuthenticationFilter` reading `Authorization: Bearer <token>`. WebSocket handshakes
  use a *separate* mechanism — `config/JwtHandshakeInterceptor` validates a JWT passed as a
  `?token=` query param — don't conflate the two. Sessions are stateless and CSRF is disabled
  (`config/SecurityConfig`).
- **Database**: Postgres + PostGIS via Spring Data JPA/Hibernate, with `ddl-auto: validate` —
  **Flyway owns all schema** (`src/main/resources/db/migration`, files named `V<n>__description.sql`
  with a capital `V`). Never rely on JPA annotations to auto-create/alter schema; add a new Flyway
  migration instead.
- **Core domain**: `RideOffer` (driver-posted) and `Booking` (passenger reservation) — not
  polls/votes. Key enums: `RideStatus` (`ACTIVE`, `IN_PROGRESS`, `FULL`, `CANCELLED`, `COMPLETED`)
  and `BookingStatus`.
- **Ride matching** is a straight-line corridor approximation (`utils/GeoUtils`: haversine +
  point-to-segment distance, fixed 2000m radius) — a known v1 simplification, not real routing.
- **Booking concurrency**: booking a ride takes a pessimistic row lock on the ride offer
  (`RideOfferRepository.findByIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`) inside `@Transactional`
  service methods — preserve this pattern for any new mutation that touches seat counts.
- **Notifications & OTP delivery** are console-log stubs only (`LoggingNotificationService`,
  `OtpService`) — not wired to a real SMS/push provider yet.
- **Error handling**: custom exceptions (`NotFoundException`, `BadRequestException`,
  `ConflictException`, `ForbiddenException`) are caught by `exception/GlobalExceptionHandler` and
  turned into a consistent `{code, message, timestamp}` JSON body. Bean Validation failures
  (`@Valid` on `@RequestBody` DTOs) produce a `VALIDATION_ERROR` code with a `fieldErrors` map.
- **Activity logging** is explicit — each service calls `ActivityLogService` directly per action
  (not AOP-based).
- **Admin** support is groundwork only (`User.roleFlags` bitmask: 1=rider, 2=driver, 4=admin) —
  no admin endpoints exist yet.
- **Config**: `src/main/resources/application.yml` is checked into git with dev credentials
  (`carpool`/`carpool`) and a placeholder JWT secret — there is no `.env` file in this repo.
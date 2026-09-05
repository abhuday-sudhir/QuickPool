# Testing Guide: Mockito, JUnit 5, AssertJ & Coverage

This explains the test classes under `src/test/java/com/QuickPool/service/` line by
line — what each Mockito/JUnit/AssertJ call actually does — and how to measure code coverage
for this project (JaCoCo, wired up in `pom.xml`).

The one test **not** covered in depth here is `ApplicationTests` — it's a full
`@SpringBootTest` that boots the real application context against Postgres on `:5434`. It's a
different kind of test (integration, not unit) and needs `docker compose up -d` running first.
Everything below is about the fast, DB-free unit tests.

## 1. The testing stack

Three libraries work together in every test file:

| Library | Role | Import prefix |
|---|---|---|
| **JUnit 5 (Jupiter)** | Test runner — discovers `@Test` methods, runs `@BeforeEach`, reports pass/fail | `org.junit.jupiter.*` |
| **Mockito** | Creates fake objects (`@Mock`) standing in for repositories, and lets you script their behavior (`when(...).thenReturn(...)`) and later check what was called on them (`verify(...)`) | `org.mockito.*` |
| **AssertJ** | The assertion library — the fluent `assertThat(x).isEqualTo(y)` style, more readable than JUnit's plain `assertEquals` | `org.assertj.core.api.*` |

All three come bundled through `spring-boot-starter-webmvc-test` (test scope) in `pom.xml` — no
extra dependency was needed to write these tests.

### The shape every test class follows

```java
@ExtendWith(MockitoExtension.class)   // (1)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;   // (2)
    @InjectMocks private RatingService service;         // (3)

    @Test
    void someBehavior() {
        when(ratingRepository.existsBy...(...)).thenReturn(false);  // (4) arrange
        service.rate(driver, dto);                                   // (5) act
        verify(ratingRepository).save(any());                        // (6) assert (interaction)
        assertThat(result).isEqualTo(...);                            // (6) assert (value)
    }
}
```

1. **`@ExtendWith(MockitoExtension.class)`** — tells JUnit 5 to let Mockito process the
   `@Mock`/`@InjectMocks` fields on this class before each test runs. Without this annotation,
   those fields would just be `null`.
2. **`@Mock`** — creates a fake `RatingRepository`. It has all the same method signatures as the
   real interface, but every method does nothing and returns `null`/`0`/`false` **until you tell
   it what to do** with `when(...)`. This is what makes the test a *unit* test: `RatingService` is
   tested completely in isolation, with no real database, Spring context, or network call.
3. **`@InjectMocks`** — creates a real instance of `RatingService` and automatically wires the
   `@Mock` fields above into its constructor. This is the class actually under test.
4. **Arrange** — script the mocks' behavior for this specific scenario.
5. **Act** — call the real method on the real service.
6. **Assert** — check the outcome, either as a return value (AssertJ) or as a side effect on a
   mock (Mockito `verify`).

This is the standard **Arrange-Act-Assert** pattern, and every test method in this repo follows
it even when the three steps aren't labeled.

## 2. Every Mockito/JUnit/AssertJ call used, explained

### Annotations

- **`@BeforeEach`** (`RatingServiceTest.setUp`) — runs before *every* `@Test` method in the
  class. Used here to build a fresh `RideOffer` (`IN_PROGRESS`, with a driver) so each test
  starts from the same known state without repeating the setup code.
- **`@DisplayName("...")`** — a human-readable label shown in test reports instead of the method
  name (e.g. `rejectsSelfRating()` shows up as "rejects rating yourself"). Purely cosmetic, no
  effect on execution.
- **`@Test`** — marks a method as a test case JUnit should run.

### Mockito: scripting behavior

- **`when(mock.method(args)).thenReturn(value)`** — "if this exact call happens, return this
  value." E.g. `SafetyServiceTest`:
  ```java
  when(userRepository.existsById(them)).thenReturn(false);
  ```
  Any call to `existsById` with a *different* argument than `them` still returns Mockito's
  default (`false` for booleans, `null` for objects, empty `Optional` if stubbed loosely).

- **`when(...).thenAnswer(invocation -> ...)`** — like `thenReturn`, but computes the return
  value from the actual call, using the invocation's own arguments. `VehicleServiceTest`:
  ```java
  when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
  ```
  This makes the fake `save()` behave like a real JPA `save()` that just hands back what you
  gave it — needed because `VehicleService.save()` reads fields off the *returned* object.

- **`any()`** (`org.mockito.ArgumentMatchers.any`) — an argument matcher meaning "match this call
  no matter what object is passed here." Used both when stubbing (`when(repo.save(any()))`) and
  when verifying (`verify(ratingRepository).save(any())`). You cannot mix a matcher with a plain
  literal in the same call (e.g. `findById(rideId, any())` is illegal) — see `eq()` below for why.

- **`eq(value)`** — an argument matcher meaning "match this call only when the argument equals
  `value` exactly." Needed because Mockito requires **all** arguments in a stubbed call to be
  matchers if *any* one of them is — you can't write `findByRideOfferIdAndStatusIn(rideId, any())`
  because `rideId` would be interpreted as a matcher error. `RatingServiceTest.ridersOnBoard()`:
  ```java
  when(bookingRepository.findByRideOfferIdAndStatusIn(eq(rideId), any())).thenReturn(List.of(b));
  ```

### Mockito: verifying interactions

- **`verify(mock).method(args)`** — asserts that `method` was called on `mock` with matching
  `args`, exactly once. If it wasn't called (or was called with different args), the test fails.
  `RatingServiceTest`:
  ```java
  verify(ratingRepository).save(any());
  ```

- **`verify(mock, never()).method(args)`** — asserts the call **never** happened.
  `SafetyServiceTest`:
  ```java
  verify(blockRepository, never()).save(any());
  ```
  This is how "blocking twice is a no-op" and "reporting while one is already open is rejected"
  are proven — not just that an exception was thrown, but that no write was attempted either.

- **`verifyNoInteractions(mock)`** — asserts *nothing at all* was called on this mock during the
  test. `RatingServiceTest.rejectsSelfRating()` uses this to prove the self-rating check short-
  circuits before the code ever touches the ratings table.

- **`ArgumentCaptor<T>`** — used when you need to inspect *what* was passed to a mock, not just
  confirm it was called. `RatingServiceTest.savesAndRecomputesAverage()`:
  ```java
  ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
  verify(userRepository).save(saved.capture());
  assertThat(saved.getValue().getRatingAvg()).isEqualByComparingTo("4.50");
  ```
  `save.capture()` acts as the matcher for the `verify` call (like `any()` would), but afterwards
  `saved.getValue()` gives you back the *actual `User` object* the service passed in, so you can
  assert on its fields — here, proving the service recomputed and set the average correctly.

### AssertJ: assertions

- **`assertThat(x)`** — the entry point to every AssertJ assertion; wraps `x` so you can chain
  readable checks (`.isEqualTo(...)`, `.isTrue()`, ...).

- **`.isEqualTo(value)` / `.isTrue()` / `.isZero()`** — straightforward equality/boolean/numeric
  checks. `ImpactServiceTest`: `assertThat(impact.getSharedRides()).isZero();`

- **`.isEqualByComparingTo("4.50")`** — like `isEqualTo`, but for types like `BigDecimal` where
  `equals()` also checks scale (`4.50` vs `4.5` would fail `.isEqualTo` but not represent a real
  difference). `RatingServiceTest` uses it because `ratingAvg` is a `BigDecimal`.

- **`.isCloseTo(expected, Offset.offset(delta))`** — asserts a `double` is within `delta` of
  `expected`, for values with floating-point/approximation error. `ImpactServiceTest`:
  ```java
  assertThat(impact.getSharedKm()).isCloseTo(111.2, Offset.offset(1.0));
  ```
  This is the correct way to assert on GPS/haversine-distance math, where exact equality would
  be flaky.

- **`.containsExactly(items...)`** — asserts a collection has exactly these elements, in this
  order, no more, no less. `SafetyServiceTest.hiddenFromBothDirections()`.

- **`.singleElement()`** — asserts a collection has exactly one element and lets you keep
  chaining on it. `RatingServiceTest.listsRatingsGiven()`:
  ```java
  assertThat(result).singleElement().satisfies(r -> {
      assertThat(r.getRideOfferId()).isEqualTo(rideId.toString());
      ...
  });
  ```

- **`.satisfies(consumer)`** — runs an arbitrary lambda of further assertions against the single
  extracted element. Used above to bundle several field checks under one readable assertion
  block instead of several separate `assertThat(result.get(0)....)` lines.

- **`assertThatThrownBy(() -> ...)`** — the AssertJ way to assert an exception is thrown. Takes
  a lambda so it can catch the exception itself, rather than needing a `try/catch` in the test.
  Chains into:
  - **`.isInstanceOf(SomeException.class)`** — the thrown exception is of this type (or a
    subtype). This is how business-rule violations are checked: `ForbiddenException` for
    "not allowed", `ConflictException` for "state doesn't allow this", `NotFoundException` for
    "doesn't exist".
  - **`.hasMessageContaining("originLat")`** — the exception's message contains this substring.
    `DirectionsServiceTest` uses this to confirm *which* coordinate failed validation, not just
    that *some* validation failed.

### Not Mockito/AssertJ, but appears alongside them

- **`ReflectionTestUtils.setField(service, "apiKey", key)`** (Spring, `DirectionsServiceTest`) —
  sets a private field directly via reflection, bypassing constructor/setter. Used because
  `DirectionsService.apiKey` is normally injected by Spring from `MAPS_SERVER_KEY` via
  `@Value`, and there's no constructor arg to pass it through in a plain `new DirectionsService()`
  unit test.

## 3. What each test class actually verifies

- **`RatingServiceTest`** — the rules around leaving a 1–5 star rating after a ride:
  you can't rate yourself, can't rate before the ride is `IN_PROGRESS`, can't rate someone who
  wasn't on the ride, can't rate the same person twice for the same ride, and — the main happy
  path — that saving a rating also recomputes and persists the target user's running average
  (`ratingAvg`). Also covers `ratingsGivenBy`, which the app uses to grey out "Rate" buttons for
  people already rated.

- **`SafetyServiceTest`** — blocking and reporting other users: no self-block/self-report,
  blocking a nonexistent user 404s instead of silently writing a row, blocking someone twice is
  idempotent (not an error), block visibility (`isHidden`/`hiddenFrom`) is symmetric regardless of
  who blocked whom, and only one *open* report per (reporter, reported) pair is allowed at a time.

- **`VehicleServiceTest`** — normalizing and storing a driver's vehicle: plate numbers are
  uppercased and stripped to alphanumerics (`"dl 3c ab-1234"` → `"DL3CAB1234"`), free-text fields
  are trimmed, saving again *updates* the existing row rather than inserting a duplicate, and
  `describe()` formats a vehicle the way a passenger looks for it at pickup (`"Grey Honda City ·
  UP80AB1111"`).

- **`ImpactServiceTest`** — the "your environmental impact" stats: zero state when a user has no
  bookings/rides, cancelled bookings are excluded from the count, and — the arithmetic-heavy
  case — that CO2 saved and trees-equivalent are derived from shared distance using the
  documented emission factors (`0.171 kg CO2/km`, `21 kg CO2` per tree), computed here against a
  contrived ride exactly 1° of latitude apart (~111.2 km) so the expected math is easy to state.

- **`DirectionsServiceTest`** — the guards in front of the billed Google Directions call: each of
  the four coordinates is range-checked (`originLat` ∈ [-90,90], lngs ∈ [-180,180], etc.) *before*
  the API key is even read, and if `app.maps.directions-key` isn't configured, the service fails
  loudly with `IllegalStateException` rather than silently calling Google without a key. The
  actual HTTP round-trip to Google is deliberately **not** tested here (see the class comment) —
  mocking it would only be testing this codebase's own JSON parsing against a fake response it
  wrote itself, not real integration risk.

- **`RideOfferServiceTest`** (added for PRODUCTION_TASKS.md 3.1/3.3) — `search()`: a viewer's
  block-list is still applied in Java *after* the spatial query comes back (a ride from a blocked
  driver that happens to be on-corridor is still dropped), the default search window is exactly 3
  hours from now when the caller doesn't specify one (`ArgumentCaptor` on the `from`/`to` passed to
  the repository), and driver/vehicle details for a page of results come from one batched
  `findAllById`/`findByUserIdIn` each rather than a lookup per ride. `getConfirmedPassengerOrder()`:
  returns confirmed passengers in booking order for the ride's driver, throws `ForbiddenException`
  for someone not on the ride, and `NotFoundException` for a ride that doesn't exist.
  `getMyRides()`: a `Slice` maps straight through to `PageResponseDto`, `hasNext` included.
  **Not covered here, and can't be with Mockito:** whether the actual `ST_DWithin` predicate
  against `ride_offers.route` correctly includes/excludes rides by distance — that's a live
  PostGIS behavior with no meaningful mock, the same reasoning `DirectionsServiceTest` gives for
  not mocking Google's HTTP response. Checked by hand instead: a real backend against the
  docker-compose Postgres, a pickup/drop on the corridor matched, one 1,000+ km away didn't, and
  `EXPLAIN ANALYZE` confirmed `idx_ride_offers_route` (the GiST index from migration V10) is what
  the planner picks once the table has enough rows for it to matter. Also now covers
  `createRideOffer()` (rejects a past departure time or zero seats, otherwise starts `ACTIVE` with
  every seat available), `cancelRideOffer()` (rejects a non-owner, otherwise cancels both
  `CONFIRMED` and `PENDING` bookings and notifies every affected passenger), and `startRide()`
  (rejects a non-owner or a ride that isn't `ACTIVE`/`FULL`, otherwise moves to `IN_PROGRESS` and
  notifies every confirmed passenger that they can now track live).

- **`BookingServiceTest`** (added for PRODUCTION_TASKS.md 3.2/3.3) — `getMyBookings()` and
  `getBookingRequestsForDriver()` used to call `rideOfferRepository.findById` once per booking
  inside a `.map()` (and the driver-side one also did the same for `userRepository`) — real N+1
  queries, one extra round-trip per row on the page. These tests assert the fix by checking what
  *wasn't* called: `verify(rideOfferRepository, never()).findById(any())` alongside
  `verify(rideOfferRepository, times(1)).findAllById(any())`, so a regression back to per-row
  lookups fails the test even though the returned data would look identical. Also covers the
  pagination wiring (`Slice.hasNext()` reaching the response) and the empty-page short-circuit for
  a driver with no rides at all (`verifyNoInteractions(bookingRepository)` — it must not even
  query bookings when there's no ride to query them against). Also now covers the full booking
  lifecycle: `bookRide()` (no self-booking, blocked drivers refused, ride must be `ACTIVE` with
  enough seats free, no duplicate booking on the same ride, and taking the last seat flips the
  ride to `FULL`), `acceptBooking()`/`rejectBooking()` (both require the caller to actually drive
  the ride and the booking to still be `PENDING`; rejecting releases the held seat and reopens a
  `FULL` ride back to `ACTIVE`), and `cancelBooking()` (only the passenger who made it can cancel,
  only while `PENDING`/`CONFIRMED`, and it releases the seat and notifies the driver).

- **`OtpServiceTest`** — phone + OTP is the only login this app has. Covers the resend cooldown
  (refuses within 30s, deletes-then-`flush()`s a stale pending code before issuing a new one — the
  same Hibernate flush-ordering trap documented in CLAUDE.md), and `verifyOtp()`'s full gate:
  expired code, too many attempts, a wrong code incrementing attempts without verifying, a deleted
  account refused outright, a brand-new phone number creating a user with `roleFlags = 3` (rider +
  driver by default), and an existing phone number logging in without creating a duplicate row.
  Because `OtpService` builds its own `BCryptPasswordEncoder` internally rather than taking one as
  a dependency, the "correct code" tests hash a known OTP with a real encoder in `setUp` rather
  than mocking password matching — there's nothing to mock.

- **`TokenServiceTest`** — refresh-token rotation and reuse detection (CLAUDE.md): `issuePair()`
  stores an unrevoked `RefreshToken` row and returns both JWTs; `rotate()` refuses an invalid
  token, an access token presented as a refresh token, a pre-rotation token with no `jti`, and an
  unknown `jti`; presenting an **already-spent** token calls `TokenRevoker.revokeAllNow(...)`
  (proving the theft response actually burns every session) and refuses to issue a new pair;  an
  expired-but-unspent token is refused without touching `TokenRevoker` at all (that call is theft
  detection, not routine expiry); the happy path marks the old record `revoked`/`ROTATED` and
  issues a fresh pair; and a token whose user account has since been deleted is refused too.

- **`JwtServiceTest`** — the one test class with no mocks at all: `JwtService` has only `@Value`
  fields, which `ReflectionTestUtils.setField` sets directly (the same trick
  `DirectionsServiceTest` uses for its `apiKey`). Covers access- and refresh-token round-tripping
  (subject, `jti`, `type` claim), that a token signed with a different secret fails validation,
  and the `@PostConstruct` guard: refuses to start with a secret under 32 bytes, refuses the
  built-in development secret under the `prod` profile, and allows it (with just a log warning)
  everywhere else.

- **`EmailVerificationServiceTest`** — the same shape of gate as OTP, for the email-verify flow:
  refuses a user with no email on file or one already verified, enforces the 60s resend cooldown
  (delete-then-`flush()` again), and `confirm()` refuses an expired code, too many attempts, or a
  code issued for an email the user has since changed away from — a wrong code increments attempts
  without verifying, and the right code verifies both the code row and the user's `emailVerified`
  flag together.

- **`AccountDeletionServiceTest`** — the account-scrub rules from CLAUDE.md's linked comment:
  refuses to delete an already-deleted account, and refuses outright while the user is actively
  driving (`ACTIVE`/`FULL`/`IN_PROGRESS`) or riding (`PENDING`/`CONFIRMED`) so nobody is stranded
  mid-commitment. The happy path checks the scrub is real — phone replaced with a `del_`-prefixed
  placeholder, name/email/rating cleared, `deletedAt` set — and that it revokes every session via
  `TokenService.revokeAll(..., "ACCOUNT_DELETED")` and deletes the attached vehicle row.

- **`TripShareServiceTest`** — `share()` refuses anyone who isn't the driver or a confirmed
  passenger, reuses an already-live link instead of minting a new one every tap, and mints a fresh
  one otherwise; `revoke()` marks every live share this user made on the ride; `view()` — the one
  genuinely public, unauthenticated endpoint in the app — 404s for an unknown/revoked/expired
  token and falls back to "QuickPool driver" when the real driver hasn't set a name, proving the
  narrow `SharedTripViewDto` shape never depends on data that might leak more than intended.

- **`RideLifecycleServiceTest`** — the 15-minute sweep (CLAUDE.md) that ages out rides nothing
  else ever moves out of `ACTIVE`/`IN_PROGRESS`: a ride departed hours ago and never started is
  `EXPIRED` with its pending/confirmed bookings cancelled and passengers notified; a ride left
  `IN_PROGRESS` for hours is `COMPLETED` with its confirmed bookings completed too (silently, no
  notification — that's what unlocks rating, not something to alert anyone about); and a sweep
  with nothing stale touches neither repository.

- **`SavedAddressServiceTest`** — saving under the 12-address-per-user cap, refusing a *new*
  label once the cap is hit, saving the same label again overwriting the existing row in place
  (so "Home" can be moved) rather than checking the cap or adding a duplicate, and `delete()`
  refusing to remove another user's address.

- **`DestinationServiceTest`** — `record()`'s three paths: a brand-new place starts at
  `useCount = 1`, an already-known place (same rounded lat/lng) bumps the counter and refreshes
  the label (unless the new name is blank, in which case the old label is kept), and the
  concurrent-insert race — two requests recording the same brand-new place at once — falls back
  to bumping the row that won instead of raising `DataIntegrityViolationException` up to the
  caller.

- **`RateLimiterTest`** — the fixed-window counter in front of the auth endpoints: allows a
  request under budget, sets the window's Redis expiry only on the *first* hit (not every
  request), refuses once the count exceeds the limit, and — the one that matters most — fails
  **open** on an unreachable Redis, so an outage there degrades to "no rate limiting" rather than
  locking every user out of login.

## 4. Running the tests

```bash
mvn test                                    # everything, including ApplicationTests (needs docker compose up -d)
mvn test -Dtest='!ApplicationTests'         # just the fast, DB-free unit tests above
mvn test -Dtest=RatingServiceTest           # one class
mvn test -Dtest=RatingServiceTest#rejectsSelfRating   # one method
```

## 5. Measuring code coverage (JaCoCo)

There was **no coverage tool configured** before this — `mvn test` ran the tests but produced no
coverage numbers. I added the `jacoco-maven-plugin` to `pom.xml` (bound to the `test` phase, so
it needs no extra command):

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

- **`prepare-agent`** attaches a Java agent before tests run that instruments bytecode to track
  which lines/branches actually execute.
- **`report`**, bound to the `test` phase, turns the recorded data into an HTML/CSV/XML report
  right after tests finish — no separate `mvn jacoco:report` call needed.

Run it:

```bash
mvn test -Dtest='!ApplicationTests'
open target/site/jacoco/index.html      # human-readable report
```

`target/site/jacoco/index.html` is clickable down to the line level — green lines were executed
by a test, red lines were not, yellow means partially-covered branches (e.g. an `if` where only
the true side was exercised).

**Current numbers**, running the seventeen unit test classes above (`ApplicationTests` needs
`docker compose up -d` for Postgres on `:5434` and isn't a unit test, so it's excluded from this
snapshot):

| Package | Line coverage |
|---|---|
| `com.QuickPool.enums` | 100.0% (20/20) |
| `com.QuickPool.utils` | 87.5% (7/8) |
| `com.QuickPool.service` | 85.0% (713/839) |
| `com.QuickPool.entity` | 78.6% (22/28) |
| `com.QuickPool.dtos` | 72.7% (48/66) |
| `com.QuickPool.exception` | 26.7% (8/30) |
| `com.QuickPool.controller` | 0.0% (0/164) |
| `com.QuickPool.config` / `com.QuickPool.filter` | 0.0% |
| **Overall** | **67.0% (818/1221 lines)** |

Up from 21.7% — the service layer alone went from 21.9% to 85.0% in one pass, adding
`OtpServiceTest`, `TokenServiceTest`, `JwtServiceTest`, `EmailVerificationServiceTest`,
`AccountDeletionServiceTest`, `TripShareServiceTest`, `RideLifecycleServiceTest`,
`SavedAddressServiceTest`, `DestinationServiceTest`, and `RateLimiterTest`, plus filling in the
booking/ride lifecycle methods `RideOfferServiceTest` and `BookingServiceTest` didn't cover yet
(see section 3 above for what each one actually asserts). `com.QuickPool.entity` and
`com.QuickPool.dtos` moved mostly as a side effect — entities and DTOs constructed inside these
tests (`RefreshToken`, `TripShare`, `SharedTripViewDto`, ...) get their getters/setters exercised
for free, not because anything targets them directly.

The remaining gap is now concentrated in one place: `com.QuickPool.controller` at 164 lines with
0% covered — every one of these services is unit-tested, but no request ever goes through
Spring MVC (auth, validation, exception-mapping, the actual routes) in a fast test. Only the
DB-backed `ApplicationTests` context load touches that layer, and it doesn't exercise individual
endpoints. `com.QuickPool.exception` at 26.7% is mostly boilerplate constructors on exception
classes that get *thrown* constantly (and so are covered) but whose other constructor overloads
are never called. `com.QuickPool.config`/`filter` (JWT filter, CORS, cache config, WebSocket
config) are Spring wiring that would need a `@WebMvcTest`/`@SpringBootTest` slice, not a plain
Mockito unit test, to cover meaningfully.

If you want a coverage **threshold that fails the build** below some percentage (common in CI),
that's a `jacoco:check` execution bound to `verify` with `<rules>` — ask if you want that added;
it wasn't included here since the repo has no CI (`.github/workflows`) to enforce it yet, per
`CLAUDE.md`.

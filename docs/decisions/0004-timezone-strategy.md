# ADR 0004: Timezone Strategy

- Status: Accepted
- Context: Streaks, duplicate-prevention, and the leaderboard all depend on one authoritative definition of "a day" for a given user. If that definition shifts — e.g., the user travels and the client auto-detects a new timezone — historical check-in dates would re-interpret, breaking `UNIQUE(habit_id, check_in_date)` invariants and silently moving streak boundaries.
- Decision: Use `users.timezone` (IANA identifier, e.g., `Pacific/Auckland`) as the **sole** source of truth for date-bound business logic. Persist `check_ins.check_in_date` as a calendar `DATE` (not a timestamp). Request-time timezone overrides are not accepted — if a user travels, they must update their profile timezone before writing date-bound data. All timezone arithmetic lives in `common.time.TimezoneResolver` and uses a single `Clock` bean so tests can freeze time.
- Consequences:
  - Streaks, duplicate prevention, and leaderboard eligibility are all anchored to one authoritative value per user.
  - Travellers take on a small friction: update profile first, then check in. This is visible in the UI (a banner when device tz ≠ saved tz) but not silently corrected.
  - Server-side date logic is a pure function of `users.timezone` + UTC clock — trivially unit-testable and reproducible in incident investigations.
- Rationale:
  - **Why saved profile tz over client-supplied tz?** A client-supplied tz makes every write endpoint a potential vector for re-writing history: an attacker could backdate check-ins by sending a skewed tz. Saving the tz server-side turns tz changes into an explicit, audited profile edit.
  - **Why IANA identifiers, not UTC offsets?** Offsets lose DST information. `Pacific/Auckland` in April (+12:00) behaves differently from `Pacific/Auckland` in January (+13:00); using a fixed offset would silently misfire twice a year.
  - **Why `DATE`, not a `TIMESTAMP WITH TIME ZONE`?** A check-in doesn't have a moment — it has a day. Storing `DATE` makes the unique constraint trivial and matches how users reason about habits ("I did it Tuesday", not "I did it at 14:37:22 UTC").
  - **Why reject request-time overrides?** Consistency. The server's answer to "was this check-in for today?" must be deterministic, replayable, and not attacker-influenced. A profile-level tz is the one degree of freedom the user gets.
- Implementation notes:
  - `TimezoneResolver.today(user)` = `LocalDate.now(ZoneId.of(user.timezone), clock)`.
  - Validation on `POST /check-ins.clientDate`: must equal `today(user)` or `today(user).minusDays(1)` — else `400 CLIENT_DATE_OUT_OF_RANGE`.
  - Test matrix covers `Pacific/Auckland` (DST), `Asia/Shanghai` (no DST), and `America/Santiago` (southern-hemisphere DST) to catch off-by-one errors at local midnight transitions.
  - The scheduler for `US-13` reminders uses `reminder_local_time TIME` joined with `users.timezone` to compute "is it 8am local for this user right now?" — same rule, consistently applied.

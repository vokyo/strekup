# ADR 0005: Check-In User Denormalisation

- Status: Accepted
- Context: `check_ins` already carries `habit_id`, and `habits` carries `user_id`, so the user owner of a check-in is derivable via a join. But every read path on `check_ins` — dashboard status, ownership check, `GET /check-ins` filter, heatmap aggregation, leaderboard reconciliation — would pay that join. At the scale of "tens of check-ins per user per week × low-thousand DAU" the join is cheap, but the *repetition* of the join across the codebase is the real cost.
- Decision: Persist `check_ins.user_id` as a denormalised column alongside `habit_id`. Enforce referential integrity via a composite foreign key to `habits(id, user_id)` (which requires a `UNIQUE(id, user_id)` on `habits` — trivial since `id` is already unique).
- Consequences:
  - Ownership checks reduce to `WHERE user_id = :caller` on the single table, no join. Controllers and specifications stay readable.
  - Heatmap/leaderboard aggregations query `check_ins` in isolation; the query planner has one less table to reason about.
  - The composite FK prevents the main hazard of denormalisation: a `check_in` row cannot drift to a different `user_id` than its `habit.user_id`, because the DB rejects the write.
  - One extra column (`BIGINT`, ~8 bytes × row count) per check-in. Negligible at expected scale.
- Rationale:
  - **Why denormalise at all?** Because the same join appears in 6+ query paths. Denormalisation isn't premature optimisation when the cost (1 column, 1 FK) is trivial and the readability win touches every feature that reads check-ins.
  - **Why a composite FK instead of a trigger or app-level guard?** Application-level guards drift — some future endpoint forgets to set `user_id` and the row ends up orphaned to the wrong owner. The composite FK makes the DB the guarantor of the invariant: an `INSERT` that mismatches `user_id` fails hard.
  - **Why not just join every time?** We could. The row count never reaches scale where the join is expensive. The reason to denormalise is developer-cognition cost, not DB latency — every ownership check reading `check_ins` is one join fewer to review.
- Implementation notes:
  - `habits`: `UNIQUE (id, user_id)` in addition to the primary key on `id`. This is what allows a composite FK from `check_ins`.
  - `check_ins`: `FOREIGN KEY (habit_id, user_id) REFERENCES habits(id, user_id)`. No `ON DELETE CASCADE` — habits soft-delete (ADR 0003), so cascade never fires.
  - Insertion path: `CheckInService.create(user, habitId, ...)` sets `user_id` from the authenticated principal, not from a request field, preventing client-forged ownership.

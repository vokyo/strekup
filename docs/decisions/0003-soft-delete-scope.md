# ADR 0003: Soft Delete Scope

- Status: Accepted
- Context: The product needs to preserve habit history when a user "deletes" a habit (`US-05`: *edit preserves existing check-in history; delete requires confirmation; soft delete for data integrity*) and must preserve the leaderboard's cumulative totals across archives. But applying soft-delete universally turns every query into `WHERE deleted_at IS NULL`, and makes unique constraints harder to reason about.
- Decision: Apply soft-delete **only** to `habits`, implemented as a boolean `archived` column. All other tables — `check_ins`, `tags`, `check_in_tags`, `attachments`, `refresh_tokens`, `users` — use hard-delete semantics. `users` has no delete path in MVP; if it ever does, it will cascade to owned rows rather than soft-mark.
- Consequences:
  - Habit history survives archive: a user can archive "Morning Run" without losing the 180 past check-ins counted toward their leaderboard total.
  - `check_ins` uniqueness (`UNIQUE(habit_id, check_in_date)`) stays a real constraint — no `deleted_at` column to work around it.
  - The leaderboard's "count all retained check-ins, including from archived habits" rule (`requirements.md` §Key Business Rules #8) is expressible as a single `SELECT COUNT(*) FROM check_ins ...` without excluding archived data.
- Rationale:
  - **Why boolean `archived`, not a nullable `archived_at` timestamp?** The product never asks "when was this habit archived?" — only "is it hidden from the active dashboard?". A boolean is honest about what we actually need; upgrading to a timestamp later is a one-line migration if a feature ever justifies it.
  - **Why not soft-delete `check_ins` too?** A soft-deleted check-in would either (a) still count on the leaderboard, defeating the delete, or (b) not count, in which case it's functionally identical to a hard delete with extra query complexity. Hard delete is correct.
  - **Why not soft-delete `tags`?** Tags are cheap and orthogonal; deleting a tag cascades into `check_in_tags` but leaves the check-ins intact. There's no history to preserve — the check-in itself is the history.
  - **Scope discipline.** Soft delete is a seductive default that spreads to every table once introduced. Constraining it to exactly one table keeps the rest of the schema honest.
- Implementation notes:
  - `habits.archived BOOLEAN NOT NULL DEFAULT FALSE`, indexed as part of composite indexes on `(user_id, archived)` for the default dashboard query.
  - `DELETE /habits/{id}` sets `archived = true`. There is no "unarchive" endpoint in MVP; it can be added as a `PATCH /habits/{id}` field without schema change.
  - `GET /habits?includeArchived=true` is the only query path that sees archived rows.

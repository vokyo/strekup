# CLAUDE.md — Guidance for Claude Code

This file is loaded automatically when Claude Code opens this repo. It's the project's "operating manual" — what this project is, how it's built, and the conventions to follow when writing or modifying code.

If any instruction here conflicts with the user's explicit ask in the session, follow the user.

---

## Project context

**StreakUp** is a solo-built, 6-week MVP habit tracker. The target audience for the *codebase* is a recruiter scanning a live demo + GitHub source for ~30 seconds, so every engineering choice optimises for:
1. **Depth on the JD's required tech** (Java/Spring, React/TS, AWS, JWT, SQL, Redis).
2. **Readability under a quick scan** — vertical slices, single source of truth, no clever abstractions.
3. **Operational simplicity** — one deployable, AWS free tier, no over-engineering.

Full context lives in `docs/`. The entry points you should read when planning non-trivial changes:
- `docs/requirements.md` — what the product does, user stories, NFRs.
- `docs/architecture.md` — C4 diagrams, deployment topology, package layout.
- `docs/api-spec.md` — REST contract, error shape, rate limits, CORS.
- `docs/er-diagram.md` — schema, indexes, Flyway migration plan.
- `docs/auth-flow.md` — JWT + refresh-token rotation flows (security-sensitive).
- `docs/spring-boot-config.md` — profile layout, `application-*.yml` templates, Testcontainers pins.
- `docs/decisions/` — ADRs with rationale for every locked decision.

---

## Tech stack (don't change without an ADR)

- **Backend**: Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security, Flyway, MySQL 8, Redis 7, JUnit 5 + Mockito + Testcontainers, SpringDoc OpenAPI 3.
- **Frontend**: React 18 + TypeScript + Vite, Zustand (client state), TanStack Query v5 (server state), React Router v6, Tailwind, Recharts, React Hook Form + Zod, Vitest.
- **Infra**: Docker, GitHub Actions, AWS `ap-southeast-2`, Vercel.

Rationale: [docs/decisions/0001-tech-stack-selection.md](docs/decisions/0001-tech-stack-selection.md).

---

## Conventions

Authoritative style + layering rules live in [coding-standards.md](coding-standards.md). Read it once before a non-trivial change — don't re-derive conventions from scratch.

Runtime wiring (Spring profiles, `application-*.yml` templates, the `@ValidTimezone` validator, Testcontainers image pins) lives in [docs/spring-boot-config.md](docs/spring-boot-config.md).

High-priority reminders when you're writing code:

- **Invariants you must not quietly violate** — any change touching these needs to be called out in your response, not buried in a diff:
  - Ownership checks go through `AccessService`; cross-user resource access returns `404`, not `403` ([api-spec.md](docs/api-spec.md) Appendix).
  - All business-day logic flows through `common.time.TimezoneResolver` with an injected `Clock`. Never call `Instant.now()` / `LocalDate.now()` directly.
  - Errors always use the `ApiErrorResponse` envelope. Any new `code` value must land in [docs/error-codes.md](docs/error-codes.md) in the same change.
  - Flyway migrations are forward-only. Fix a broken migration with a new migration, never an edit.
  - Never log raw tokens, passwords, or refresh-cookie values. IDs only.
- **Source-of-truth conflicts** — if two docs disagree, resolve in this order: ADR > `api-spec.md` / `er-diagram.md` > `coding-standards.md` > this file. Then fix the loser in the same PR.
- **Secrets** — CI rejects any commit adding `.env*` without the `.example` suffix. Full env reference: [docs/environment-variables.md](docs/environment-variables.md).

---

## Testing expectations

- **Backend target**: ≥ 70% line coverage on service + controller packages (JaCoCo gate in CI from Day 14).
- **Backend style**: integration tests use `@SpringBootTest` + Testcontainers (real MySQL/Redis, not H2). Unit tests use Mockito and stay fast.
- **Frontend target**: ≥ 50% line coverage on `src/` (Vitest + React Testing Library).
- **What to test**:
  - Every happy path on a public endpoint.
  - Every error path documented in `docs/error-codes.md` for that endpoint.
  - Ownership checks: "can user A read/modify user B's resource?" → must return 404.
  - Timezone edge cases: DST transition days in `Pacific/Auckland`, date boundaries around midnight.
- **What not to mock**: the database. If you're tempted to mock `CheckInRepository`, write an integration test against Testcontainers instead. Mocking the DB has bitten this project's plan before (see ADR 0006 context).

---

## What NOT to do

- **Don't introduce new dependencies** without a short justification in the commit message. The dependency graph is part of the interview surface — every library is something a reviewer might ask about.
- **Don't add features not in `docs/requirements.md`.** The scope is deliberately tight. Post-MVP ideas go in `docs/requirements.md` → "Out of Scope" or a new ADR.
- **Don't soft-delete anything except `habits`.** [ADR 0003](docs/decisions/0003-soft-delete-scope.md) explains why.
- **Don't add request-time timezone overrides.** The only timezone source is `users.timezone`. [ADR 0004](docs/decisions/0004-timezone-strategy.md).
- **Don't bypass `AccessService`** for ownership checks in controllers.
- **Don't amend or force-push** shared branches once CI is live. Create new commits.
- **Don't write comments explaining *what* the code does** if the naming already explains it. Comments are for *why* — a hidden constraint, a subtle invariant, a workaround.
- **Don't use `git add -A`** — stage files by name to avoid pulling in `.env.local`, `.DS_Store`, IDE files.

---

## When in doubt

1. Check `docs/` — the answer is probably already written down.
2. If the docs are silent but a decision has non-obvious tradeoffs, propose an ADR before implementing.
3. If the change touches auth, data integrity, or the API contract, ask before proceeding — those are the three areas where a "small fix" can silently break an invariant.

---

## Current phase

Phase 0 (design) is complete. Phase 1 (backend skeleton) begins Day 4 per [docs/HabitTracker.md](docs/HabitTracker.md). The immediate next deliverables are Spring Boot project bootstrap, `V1__init_schema.sql`, and the auth register/login endpoints.

# Coding Standards

Authoritative style and discipline rules for the StreakUp repo. Architecture / API / auth docs reference this file — when they say "conforms to coding standards", they mean this document.

Scope: applies to backend (Java / Spring Boot), frontend (TypeScript / React), SQL migrations, and repo-wide practices (commits, env, secrets). If a rule here conflicts with a domain-specific doc (`architecture.md`, `api-spec.md`, an ADR), the domain doc wins. Runtime wiring (Spring profiles, `application-*.yml`, custom validators, Testcontainers pins) lives in [docs/spring-boot-config.md](docs/spring-boot-config.md) — this file stays focused on style and layering.

---

## Core Principles

1. **Vertical slices over horizontal layers.** Group files by *feature*, not by technical type. A feature package owns its entity, repository, service, controller, DTO, and tests.
2. **Single source of truth.** Each rule, schema, or configuration value lives in exactly one place. Everything else links to it.
3. **Fail loudly at boundaries, trust the inside.** Validate at system edges (HTTP input, external API responses, user uploads). Don't re-validate trusted internal data.
4. **No unnecessary comments.** Good names and small functions explain *what*. Comments are reserved for *why* — invariants, workarounds, non-obvious constraints.
5. **Forward-only.** Flyway migrations, git history, and ADRs are append-only once merged. Fix with a new artifact, don't rewrite.

---

## Backend (Java / Spring Boot)

### Package layout

```
com.streakup
├── auth/        ─ JWT, refresh-token logic, AuthController/Service/DTOs
├── user/        ─ profile endpoints, User entity
├── habit/       ─ Habit CRUD
├── checkin/     ─ CheckIn CRUD + CheckInSpecifications
├── tag/         ─ Tag + CheckInTag
├── attachment/  ─ S3 presign + Attachment entity
├── stats/       ─ heatmap, streaks, monthly completion, leaderboard
├── security/    ─ SecurityConfig, JwtFilter, AccessService
├── common/
│   ├── error/        ─ ApiErrorResponse, GlobalExceptionHandler, domain exceptions
│   ├── persistence/  ─ BaseEntity, AuditingConfig
│   ├── redis/        ─ RedisTemplate config, key builders
│   └── time/         ─ TimezoneResolver, Clock bean
└── scheduler/   ─ ReminderJob, RefreshTokenCleanupJob (ShedLock-guarded)
```

Each feature package contains: `<Feature>.java` (entity), `<Feature>Repository.java`, `<Feature>Service.java`, `<Feature>Controller.java`, `dto/` (request + response records), and a sibling test package.

### Naming

- **Classes**: `PascalCase`. Entities are singular (`Habit`, not `Habits`). Repositories end in `Repository`, services in `Service`, controllers in `Controller`.
- **DTOs**: request classes end in `Request` (`HabitCreateRequest`), response classes end in `Response` (`HabitResponse`). Use Java `record` for DTOs — immutable, terse, equality by field.
- **Methods**: `camelCase`, verb-first (`createHabit`, `findByUserId`). Avoid `get*` for methods that do work — reserve `get` for pure accessors.
- **Constants**: `UPPER_SNAKE_CASE`. Enum values: `UPPER_SNAKE_CASE`.
- **Packages**: lowercase, single word where possible.

### Layering rules

- **Controllers** are thin: parse the request, delegate to a service, return a response. No business logic, no repository calls.
- **Services** hold business logic and transaction boundaries (`@Transactional`). Services depend on repositories, other services, and `common/*`.
- **Repositories** extend `JpaRepository`. Dynamic queries use `Specification` (see [ADR 0006](docs/decisions/0006-jpa-specification.md)), not string-concatenated JPQL.
- **Ownership checks** go through `AccessService` in `security/`. Controllers do not reach for `SecurityContextHolder` directly.

### Error handling

- Every error returns the `ApiErrorResponse` envelope defined in [docs/api-spec.md](docs/api-spec.md). Never invent a controller-level payload.
- New `code` values land in [docs/error-codes.md](docs/error-codes.md) in the same PR that introduces them.
- Domain exceptions extend a single `DomainException` base so `GlobalExceptionHandler` can map them uniformly.
- Controllers do not `try/catch` domain exceptions — let them propagate to `GlobalExceptionHandler`.

### Time

- **Persisted timestamps**: UTC, stored as `DATETIME(6)` in MySQL (matches [er-diagram.md](docs/er-diagram.md) — MySQL `TIMESTAMP` is 32-bit and wraps in 2038; we avoid it deliberately). Mapped to `Instant` in Java via Hibernate's `jdbc.time_zone=UTC` setting.
- **Business days**: computed from `users.timezone` via `common.time.TimezoneResolver`.
- Inject `java.time.Clock` as a Spring bean. **Never call** `Instant.now()` / `LocalDate.now()` without a `Clock` argument. Tests override the bean to freeze time.

### Security-sensitive code

- Never log raw tokens, passwords, refresh cookie values, or presigned URLs. Log IDs (`jti`, `refresh_token.id`) if you need a trace.
- BCrypt work factor = 12 for passwords; SHA-256 for refresh-token hashing.
- Any code that touches auth, attachments, or presigned URLs gets at least one negative-path test (tampered token, cross-user access, expired cookie).

### Null-safety

- Public method signatures: annotate with `@Nullable` / `@NonNull` from `org.springframework.lang` where ambiguity is possible.
- `Optional` for repository lookups; never return `null` from a service method.

---

## Frontend (TypeScript / React)

### Directory layout

```
src/
├── views/       ─ route-level components (DashboardView, HabitDetailView)
├── components/  ─ reusable UI (HabitCard, Heatmap, StreakBadge)
├── api/         ─ typed axios modules (auth.ts, habits.ts, checkins.ts)
│   └── client.ts  ─ axios instance + refresh-queue interceptor
├── stores/      ─ Zustand stores (authStore, uiStore)
├── hooks/       ─ useHabit, useCheckInMutation, useHeatmapQuery
├── lib/         ─ formatters, date utils (timezone), validators
├── types/       ─ shared TS types
├── router.tsx
└── main.tsx
```

### Naming

- **Components**: `PascalCase.tsx`, one component per file, filename matches the default export.
- **Hooks**: `useXxx.ts`. Must start with `use` to satisfy React Hook rules.
- **Types**: `PascalCase`. Prefer `type` aliases over `interface` unless extending.
- **Boolean props / state**: positive phrasing (`isVisible`, not `isHidden`).

### State separation (non-negotiable)

- **Zustand** = client state (auth tokens, UI preferences, modals open/closed).
- **TanStack Query** = server state (habits, check-ins, stats).
- Never store a server response in Zustand. Never cache client-only UI in TanStack Query.
- Mixing these two is the #1 reason React codebases rot — the linter can't catch it, only discipline can.

### API boundary

- All HTTP goes through `src/api/client.ts`. Base URL, auth header, and the refresh-token retry queue live there and nowhere else.
- Per-domain modules (`api/habits.ts`, `api/auth.ts`) export typed functions; components never call axios directly.
- Request/response types mirror [docs/api-spec.md](docs/api-spec.md). If they drift, the spec wins — update the types.

### Forms

- React Hook Form + Zod. The Zod schema is the source of validation truth.
- Server-side Bean Validation rules mirror the Zod schema; if they differ, the server wins at runtime but the PR should fix the drift.

### Styling

- Tailwind utility classes. Avoid inline `style=` except for dynamic values that can't be expressed in classes (e.g., computed heatmap cell colours).
- No CSS-in-JS libraries.
- Dark mode is out of scope for MVP.

---

## SQL / Flyway

- Migrations: `src/main/resources/db/migration/V{N}__{snake_case_description}.sql`. `N` is monotonic, no gaps.
- **Never edit a migration after it's merged.** Fix with a new migration.
- Every migration is reversible in principle (write the rollback SQL as a comment at the top) but we don't ship a rollback tool in MVP.
- Column names: `snake_case`. Primary keys: `id BIGINT AUTO_INCREMENT`. Timestamps: `created_at`, `updated_at` (`DATETIME(6) NOT NULL`, populated by JPA auditing, not DB defaults, so the audit instant uses the injected `Clock` bean).
- Foreign keys are always declared, even when "the application enforces it". DB constraints are cheap insurance.
- Indexes: add in the same migration that introduces the query pattern. Don't leave index tuning for "later".

---

## Testing

### Coverage targets

| Scope | Target | Tool |
|---|---|---|
| Backend service + controller | ≥ 70% line | JaCoCo |
| Backend `common/` | ≥ 60% line | JaCoCo |
| Frontend `src/` | ≥ 50% line | Vitest |

Gates enforced in CI starting Day 14 (backend) and Day 27 (frontend).

### Backend test style

- **Integration tests** (`@SpringBootTest` + Testcontainers): one per happy path per public endpoint. Use real MySQL/Redis containers, not H2 or mocks.
- **Unit tests** (plain JUnit + Mockito): service-layer failure paths, `TimezoneResolver`, specification composition, security utilities.
- **Do not mock the database.** If you're tempted, write an integration test.
- Freeze `Clock` for any test whose behaviour depends on "today".

### What must be tested

For every endpoint:
- Happy path (201/200).
- Every error `code` listed for that endpoint in [docs/error-codes.md](docs/error-codes.md).
- Ownership check: user A cannot read or modify user B's resource (expect 404, not 403).

For time-sensitive logic:
- DST transition days in `Pacific/Auckland`.
- Date boundaries at local midnight.
- Late check-in (yesterday) vs. out-of-range (2 days ago).

---

## Commits and PRs

### Commit messages

- Conventional prefix: `feat | fix | docs | refactor | test | chore | ci | perf | build`.
- Imperative mood, lowercase after the prefix, no trailing period, ≤ 72 chars.
- Examples:
  - `feat: add refresh token rotation with theft detection`
  - `fix: reject clientDate older than yesterday`
  - `docs: expand ADR 0004 rationale`
- Body (optional): explain *why*, not *what*. Link the relevant ADR or issue if any.

### Commit granularity

- One logical change per commit. A commit that touches 20 files across 5 features is a PR, not a commit.
- During Phase 1–5, expect 5–10 commits per day. Fewer than 3 means commits are too big; more than 15 means they're too small.

### Branching (from Day 31 onward)

- Feature branches: `feat/<kebab-case>`, rebased onto `main`, not merged with merge commits.
- Never force-push shared branches. If history is wrong, fix it with a new commit.
- `main` stays deployable at all times once CI is live.

### Git hygiene

- Never `git add -A` or `git add .` — stage files by name. This prevents `.env.local`, `.DS_Store`, and IDE turds from slipping in.
- `.gitignore` covers the known noise; still verify `git status` before committing.

---

## Environment variables and secrets

- Every runtime variable lives in [docs/environment-variables.md](docs/environment-variables.md). If it's not in that table, the app doesn't read it.
- `.env.local` is gitignored. `.env.local.example` is checked in with dev-safe defaults.
- Prod secrets come from AWS SSM Parameter Store at container boot, never from committed files.
- CI fails any commit that adds a file matching `.env*` without the `.example` suffix.
- Rotating a secret = update SSM + redeploy. No in-repo secret rotation.

---

## ADR discipline

- Any decision that would surprise a future reader gets an ADR in `docs/decisions/`.
- Numbers are monotonic and never reused. ADRs are **append-only** — supersede an old one with a new one that references it; don't rewrite history.
- Required sections: Status, Context, Decision, Consequences, Rationale, Alternatives considered, Implementation notes.
- A one-line ADR is a red flag — if it's worth writing, it's worth explaining *why this choice over the others*.

---

## What we deliberately don't enforce

- **Line length**: prettier/spotless format it; don't argue over 100 vs. 120.
- **Import ordering**: IDE-managed.
- **Trailing commas**: let the formatter decide.
- **Commit sign-off / DCO**: solo project, not needed.

Automated formatting runs on pre-commit via Husky (frontend) and Spotless (backend, introduced in Day 15). Manual formatting arguments waste review time.

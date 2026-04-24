# StreakUp — REST API Specification

> **Base URL (dev)**: `http://localhost:8080/api/v1`
> **Base URL (prod)**: `https://api.streakup.dev/api/v1`
> **Format**: JSON only (`Content-Type: application/json`)
> **Versioning**: URI path (`/api/v1`). A breaking change ships as `/api/v2` with parallel support for one release cycle.
> **Generated docs**: SpringDoc OpenAPI 3 at `/swagger-ui.html` (Day 10).

---

## Conventions

### Authentication

- **Access token**: short-lived JWT (15 min). Sent as `Authorization: Bearer <token>` on every request except the four public auth endpoints.
- **Refresh token**: opaque random string (32 bytes, base64url). Delivered as an `HttpOnly; Secure; SameSite=Lax` cookie scoped to `Path=/api/v1/auth`. Never exposed to JavaScript.
- **Credentialed auth requests in production**: because the SPA and API are split-origin in prod, the client must send `credentials: 'include'` / `withCredentials: true` on `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, and `POST /auth/logout`; otherwise the browser will ignore the cross-origin `Set-Cookie` that establishes or clears the refresh session.
- **Session restore after browser restart**: because the access token lives in memory only, the SPA calls `POST /auth/refresh` on app bootstrap when no access token is present. A valid refresh cookie restores the session without prompting for credentials again.
- Endpoints marked **🔒** require an access token; everything else is public.

### Request / Response Shape

- All timestamps are **UTC ISO-8601** (`2026-04-23T10:15:30.000Z`).
- All dates (not timestamps) are **calendar dates** (`2026-04-23`) interpreted in the authenticated user's saved timezone unless an endpoint says otherwise.
- Writes that depend on the meaning of "today" use the saved `users.timezone` value as the sole source of truth. If the client detects travel or a timezone change, it must update `PATCH /users/me` before creating date-bound data.
- Pagination uses `page` (0-indexed) and `size` (default 20, max 100). Responses wrap list data in a page envelope.

### Error Format

Every error — validation, auth, business, unhandled — returns this shape:

```json
{
  "timestamp": "2026-04-23T10:15:30.000Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request body is invalid.",
  "path": "/api/v1/habits",
  "traceId": "f4a2c19d-0a87-4e6f-9c1b-2c7d3e8f0a4b",
  "details": [
    { "field": "name", "code": "NOT_BLANK", "message": "must not be blank" },
    { "field": "color", "code": "PATTERN", "message": "must match hex pattern #rrggbb" }
  ]
}
```

| Status | `code` values | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `BAD_REQUEST`, `CLIENT_DATE_OUT_OF_RANGE` | Bean Validation failure, malformed JSON, or invalid client-supplied date window |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `TOKEN_REUSE_DETECTED` | Missing / bad / expired access token, or compromised refresh-token chain |
| 403 | `FORBIDDEN` | Authenticated but missing a required role on a non-resource-scoped endpoint |
| 404 | `NOT_FOUND` | Resource does not exist or belongs to someone else (masked as 404 to avoid ID enumeration) |
| 409 | `CONFLICT`, `DUPLICATE_CHECK_IN`, `EMAIL_TAKEN`, `NOT_IN_SLUMP` | Unique constraint or business-rule collision |
| 429 | `RATE_LIMITED` | LLM / presign endpoints under throttle |
| 500 | `INTERNAL_ERROR` | Unhandled; `traceId` matches the request's MDC log |
| 503 | `DEPENDENCY_UNAVAILABLE` | Source-of-truth dependency or non-degradable external service is unavailable |

### Page Envelope

```json
{
  "content": [ /* T[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "hasNext": true
}
```

---

## Endpoint Index

| Method | Path | Auth | Summary |
|---|---|---|---|
| POST | `/auth/register` | — | Create a new account |
| POST | `/auth/login` | — | Exchange credentials for tokens |
| POST | `/auth/refresh` | cookie | Rotate tokens |
| POST | `/auth/logout` | cookie | Revoke session |
| GET | `/users/me` | 🔒 | Current profile |
| PATCH | `/users/me` | 🔒 | Update profile |
| GET | `/habits` | 🔒 | List own habits |
| POST | `/habits` | 🔒 | Create habit |
| GET | `/habits/{id}` | 🔒 | Habit detail |
| PATCH | `/habits/{id}` | 🔒 | Update habit |
| DELETE | `/habits/{id}` | 🔒 | Archive habit |
| GET | `/check-ins` | 🔒 | Search check-ins |
| POST | `/check-ins` | 🔒 | Log a check-in |
| GET | `/check-ins/{id}` | 🔒 | Check-in detail |
| PATCH | `/check-ins/{id}` | 🔒 | Edit note / tags |
| DELETE | `/check-ins/{id}` | 🔒 | Delete check-in |
| GET | `/tags` | 🔒 | List own tags |
| POST | `/tags` | 🔒 | Create tag |
| PATCH | `/tags/{id}` | 🔒 | Update tag |
| DELETE | `/tags/{id}` | 🔒 | Delete tag |
| POST | `/attachments/presign` | 🔒 | Presigned PUT URL for upload |
| GET | `/attachments/{id}/url` | 🔒 | Presigned GET URL for display |
| GET | `/stats/heatmap` | 🔒 | 365-day check-in matrix |
| GET | `/stats/streaks` | 🔒 | Streak for one or all habits |
| GET | `/stats/monthly-completion` | 🔒 | Monthly completion-rate series |
| GET | `/stats/leaderboard` | 🔒 | Global top 10 + self rank |
| POST | `/ai/encouragement` | 🔒 | LLM encouragement for a broken streak (P2) |
| GET | `/actuator/health` | — | Liveness probe |

---

## Auth

### `POST /auth/register`

Create a new user and immediately return a token pair.

**Request**
```json
{
  "email": "alex@example.com",
  "password": "MyStrongPass1",
  "displayName": "Alex",
  "timezone": "Pacific/Auckland"
}
```

**Validation**
- `email`: RFC-5322 format, ≤ 255 chars, lowercased before insert.
- `password`: ≥ 8 chars, must contain at least one uppercase letter, one lowercase letter, and one digit.
- `displayName`: 1–50 chars, trimmed.
- `timezone`: valid IANA zone (validated against `java.time.ZoneId`).

**Response `201 Created`**
- Body: same shape as `/auth/login`.
- Sets `refresh_token` cookie.
- In split-origin prod, the SPA must call this endpoint with `credentials: 'include'` so the browser accepts the refresh cookie.

**Errors**
- `409 EMAIL_TAKEN` — email already exists.
- `400 VALIDATION_FAILED` — field errors in `details`.

---

### `POST /auth/login`

**Request**
```json
{ "email": "alex@example.com", "password": "MyStrongPass1" }
```

**Response `200 OK`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 900,
  "user": {
    "id": 42,
    "email": "alex@example.com",
    "displayName": "Alex",
    "timezone": "Pacific/Auckland",
    "leaderboardVisible": true
  }
}
```

**Headers**
```
Set-Cookie: refresh_token=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=2592000
```

> In split-origin prod, this request must be sent with `credentials: 'include'` / `withCredentials: true`; otherwise the browser will ignore the refresh cookie even though the response is `200 OK`.

**Errors**
- `401 UNAUTHENTICATED` — generic message; never disclose which field is wrong (prevents email enumeration).

---

### `POST /auth/refresh`

No body. Reads `refresh_token` cookie. Validates → rotates → returns a new access token and a new refresh token cookie.

This endpoint serves two client flows:
- **Silent refresh** after a `401 TOKEN_EXPIRED`.
- **Session restore on app bootstrap** after a browser restart. In that bootstrap path, the SPA typically follows a successful refresh with `GET /users/me` to hydrate the profile.

**Response `200 OK`**
```json
{ "accessToken": "...", "accessTokenExpiresIn": 900 }
```

**Errors**
- `401 TOKEN_INVALID` — cookie missing, expired, or already revoked. Cookie is cleared in the response regardless.
- `401 TOKEN_REUSE_DETECTED` — presented a previously-rotated token; **all** tokens for that user are revoked (theft mitigation).

---

### `POST /auth/logout`

Revokes the current refresh token and clears the cookie.

In split-origin prod, the SPA must also call this endpoint with `credentials: 'include'` so the browser accepts the cookie-clearing `Set-Cookie`.

**Response** `204 No Content`

---

## Users

### `GET /users/me` 🔒

**Response `200 OK`** — same `user` shape as the login response.

---

### `PATCH /users/me` 🔒

Partial update. Fields not present are untouched.

**Request**
```json
{
  "displayName": "Alex P",
  "timezone": "Pacific/Auckland",
  "leaderboardVisible": true
}
```

**Response `200 OK`** — updated user.

> `leaderboardVisible` defaults to `true`. Setting it to `false` hides the user from `/stats/leaderboard` without affecting their own history or totals.
>
> Reminder-preference fields (`emailRemindersEnabled`, `reminderLocalTime`) belong to `US-13` and are intentionally absent from MVP request/response examples. They are added once the reminder feature ships behind its P2 flag.

---

## Habits

### `GET /habits` 🔒

**Query params**
| Param | Type | Default | Description |
|---|---|---|---|
| `includeArchived` | boolean | `false` | Include soft-deleted habits |
| `page` | int | 0 | — |
| `size` | int | 20 | max 100 |

**Response `200 OK`** — page envelope of:
```json
{
  "id": 17,
  "name": "Read 30 min",
  "description": "Non-fiction mostly",
  "color": "#22c55e",
  "frequencyType": "DAILY",
  "frequencyDays": null,
  "archived": false,
  "currentStreak": 6,
  "longestStreak": 22,
  "todayChecked": true,
  "createdAt": "2026-03-12T09:02:11.000Z"
}
```

> `currentStreak`, `longestStreak`, and `todayChecked` are computed server-side so the dashboard renders from a single call (avoids the N+1 fan-out pattern on the client).

---

### `POST /habits` 🔒

**Request**
```json
{
  "name": "Gym",
  "description": "Strength 3x/week",
  "color": "#f97316",
  "frequencyType": "CUSTOM",
  "frequencyDays": "1,3,5"
}
```

**Validation**
- `name`: 1–100 chars.
- `color`: matches `^#[0-9a-fA-F]{6}$`.
- `frequencyType`: one of `DAILY | WEEKDAYS | CUSTOM`.
- `frequencyDays`: required iff `frequencyType = CUSTOM`; CSV of ISO weekdays 1–7, no duplicates.

**Response `201 Created`** — habit body, `Location: /api/v1/habits/{id}`.

---

### `GET /habits/{id}` 🔒

Same shape as list items. `404 NOT_FOUND` if the habit doesn't belong to the caller.

### `PATCH /habits/{id}` 🔒

Same fields as POST, all optional.

### `DELETE /habits/{id}` 🔒

**Soft delete** — sets `archived = true`. Check-in history is preserved.

**Response** `204 No Content`

---

## Check-Ins

### `GET /check-ins` 🔒

**Query params** — all optional, AND-combined.

| Param | Type | Description |
|---|---|---|
| `habitId` | long | Filter by habit |
| `from` | date | Inclusive lower bound on `checkInDate` |
| `to` | date | Inclusive upper bound |
| `tagId` | long | Has the given tag |
| `hasAttachment` | boolean | Only rows with ≥ 1 attachment |
| `page` | int | — |
| `size` | int | max 100 |
| `sort` | string | `checkInDate,desc` (default) or `,asc` |

Implemented with a JPA `Specification` composed from query params (Day 13). See `/docs/decisions/0006-jpa-specification.md`.

**Response `200 OK`** — page envelope of:
```json
{
  "id": 501,
  "habitId": 17,
  "habitName": "Read 30 min",
  "habitColor": "#22c55e",
  "checkInDate": "2026-04-22",
  "note": "Finished chapter 4",
  "tags": [{ "id": 3, "name": "reading", "color": "#3b82f6" }],
  "attachments": [
    { "id": 88, "contentType": "image/jpeg", "sizeBytes": 482113 }
  ],
  "createdAt": "2026-04-22T21:14:08.000Z"
}
```

> Attachment **URLs are not in this response** — clients fetch a presigned GET via `/attachments/{id}/url` only when they actually render the image. Keeps list payloads small and avoids minting URLs that may never be used.

---

### `POST /check-ins` 🔒

**Request**
```json
{
  "habitId": 17,
  "note": "Felt good today",
  "tagIds": [3, 7],
  "attachmentIds": [88],
  "clientDate": "2026-04-22"
}
```

- `clientDate` is the client's claimed check-in day. The server validates it against the current server UTC time projected into the user's saved timezone: it must be either the user's local **today** or **yesterday**. This allows a late missed check-in without allowing future-dated or timezone-shifted fraud against streaks and the leaderboard.
- If the user has travelled, they must first update `PATCH /users/me.timezone`; request-time overrides are intentionally unsupported.
- `attachmentIds` refer to rows already created by `/attachments/presign` — see below. Each attachment row is user-owned from presign time, and the server only accepts IDs where `attachments.user_id = currentUser.id` and `status = PENDING`.
- Before attaching a pending row, the server performs an S3 `HEAD Object` against the stored `s3Key` and verifies that the uploaded object exists and matches the recorded `contentType` / `sizeBytes`. No separate "complete upload" endpoint is needed; confirmation happens in the same transaction boundary as the check-in attach step, after the S3 metadata check passes.

**Response `201 Created`** — check-in body as in the list.

**Errors**
- `409 DUPLICATE_CHECK_IN` — a check-in already exists for this habit on this date.
- `400 CLIENT_DATE_OUT_OF_RANGE` — `clientDate` more than 1 day from user's local today.
- `404 NOT_FOUND` — `habitId` does not exist or does not belong to the caller.
- `404 NOT_FOUND` — one or more `attachmentIds` do not exist, do not belong to the caller, were not uploaded to S3, or are no longer attachable.
- `503 DEPENDENCY_UNAVAILABLE` — S3 cannot be reached while verifying an attachment.

---

### `GET /check-ins/{id}` 🔒 / `PATCH /check-ins/{id}` 🔒 / `DELETE /check-ins/{id}` 🔒

- `PATCH` accepts `note`, `tagIds`, `attachmentIds`. The `checkInDate` is immutable (would break the unique constraint).
- `DELETE` is hard-delete → `204 No Content`.

---

## Tags

### `GET /tags` 🔒

**Response `200 OK`** — flat array (tag count is bounded; no pagination).

```json
[ { "id": 3, "name": "reading", "color": "#3b82f6", "usageCount": 14 } ]
```

### `POST /tags` 🔒

```json
{ "name": "study", "color": "#a855f7" }
```

- `name`: 1–30 chars, lowercase, `^[a-z0-9-]+$`.
- `409 CONFLICT` if the same name exists for this user.

### `PATCH /tags/{id}` 🔒

Partial update. Fields not present are untouched.

```json
{ "name": "deep-work", "color": "#0ea5e9" }
```

- Accepts the same validation rules as `POST /tags`.
- `404 NOT_FOUND` if the tag does not belong to the caller.
- `409 CONFLICT` if the updated name collides with another tag owned by the same user.

### `DELETE /tags/{id}` 🔒

Cascades into `check_in_tags`. Check-ins themselves are untouched.

---

## Attachments

> **Pattern**: client gets a **presigned PUT URL** from the backend, uploads the file **directly to S3**, then references the pending `attachmentId` when creating or editing a check-in. The backend confirms the upload with S3 metadata (`HEAD Object`) before marking the row `ATTACHED`; it never sees the bytes. See `/docs/architecture.md` for the flow.

### `POST /attachments/presign` 🔒

**Request**
```json
{
  "contentType": "image/jpeg",
  "sizeBytes": 482113
}
```

**Validation**
- `contentType` ∈ { `image/jpeg`, `image/png` }.
- `sizeBytes` ≤ 5 × 1024 × 1024 (`US-08`).

**Response `200 OK`**
```json
{
  "attachmentId": 88,
  "uploadUrl": "https://streakup-prod.s3.ap-southeast-2.amazonaws.com/u/42/9f2a.../abc123.jpg?X-Amz-...",
  "s3Key": "u/42/9f2a...abc123.jpg",
  "expiresIn": 300
}
```

The attachment row is created in state `PENDING` **and owned by the authenticated caller** (`attachments.user_id`). It only becomes visible to other endpoints once it's been attached to a check-in via `POST /check-ins` or `PATCH /check-ins/{id}`. An hourly job purges pending attachments > 1 hour old along with their S3 objects.

### `GET /attachments/{id}/url` 🔒

Returns a 5-minute presigned GET URL. The caller must own the parent check-in.

```json
{ "url": "https://...?X-Amz-...", "expiresIn": 300 }
```

---

## Stats

### `GET /stats/heatmap` 🔒

**Query params**
| Param | Default | Description |
|---|---|---|
| `from` | today − 365 days | — |
| `to` | today | — |
| `habitId` | all | Filter to one habit |

**Response `200 OK`**
```json
{
  "from": "2025-04-23",
  "to": "2026-04-23",
  "cells": [
    { "date": "2026-04-22", "count": 3 },
    { "date": "2026-04-21", "count": 1 }
  ]
}
```

- Response is **Redis-cached** with a 5-minute TTL, keyed on `user:{id}:heatmap:{from}:{to}:{habitId}` (Day 25). Invalidated on any check-in write.

### `GET /stats/streaks` 🔒

**Query params**: `habitId` (optional; omit → all habits).

```json
{
  "streaks": [
    { "habitId": 17, "habitName": "Read 30 min", "current": 6, "longest": 22 }
  ]
}
```

Values are read from Redis (`user:{id}:habit:{hid}:streak:current` / `:longest`) with DB fallback.

### `GET /stats/monthly-completion` 🔒

**Query params**
| Param | Default | Description |
|---|---|---|
| `fromMonth` | current month − 5 months | Inclusive lower bound, `yyyy-MM` |
| `toMonth` | current month | Inclusive upper bound, `yyyy-MM` |
| `habitId` | all active habits | Filter to one habit |
| `includeArchived` | `false` | Include archived habits in the result set |

**Response `200 OK`**
```json
{
  "fromMonth": "2025-11",
  "toMonth": "2026-04",
  "series": [
    {
      "habitId": 17,
      "habitName": "Read 30 min",
      "months": [
        { "month": "2026-03", "completed": 20, "scheduled": 23, "completionRate": 86.96 },
        { "month": "2026-04", "completed": 15, "scheduled": 18, "completionRate": 83.33 }
      ]
    }
  ]
}
```

- `completionRate = completed / scheduled * 100`, rounded to 2 decimal places.
- `scheduled` is derived server-side from the habit's frequency rules (`DAILY`, `WEEKDAYS`, `CUSTOM`) in the user's timezone, so skipped non-scheduled days do not reduce the rate.
- Uncached in MVP: month-level aggregation is cheap, and keeping the denominator logic authoritative in one place is more valuable than shaving a few milliseconds.

### `GET /stats/leaderboard` 🔒

**Query params**: `limit` (default 10, max 10 — pinned to `US-12`'s acceptance criteria; kept as a param so post-MVP expansion doesn't require a breaking change).

```json
{
  "top": [
    { "rank": 1, "userId": 7,  "displayName": "Mia",  "totalCheckIns": 312 },
    { "rank": 2, "userId": 42, "displayName": "Alex", "totalCheckIns": 287 }
  ],
  "self": { "leaderboardVisible": true, "rank": 37, "totalCheckIns": 51 }
}
```

Backed by a Redis `ZSET` `leaderboard:total_checkins` containing only users where `leaderboardVisible = true`. `ZINCRBY` on check-in create, `ZINCRBY … -1` on delete, and `PATCH /users/me` toggles visibility via `ZADD` / `ZREM`. `self` is computed via `ZREVRANK` when visible; if the caller has opted out, the endpoint returns `leaderboardVisible: false`, `rank: null`, and their private `totalCheckIns` so the UI can explain the hidden state. The leaderboard intentionally counts all retained check-ins, including history from archived habits; only hard-deleted check-ins are excluded. A nightly `LeaderboardReconcileJob` (ShedLock-guarded) rebuilds the ZSET from `SELECT c.user_id, COUNT(*) FROM check_ins c JOIN users u ON u.id = c.user_id WHERE u.leaderboard_visible = true GROUP BY c.user_id`, so drift from missed `INCR`s — due to Redis downtime or a cache flush — self-heals within 24h.

---

## AI (P2, Stretch)

> Implements `US-14`. **Not in MVP** — documented here so the rate-limit table below stays honest. Route goes live in Day 29.

### `POST /ai/encouragement` 🔒

Returns an LLM-generated encouragement message for a habit where the caller has missed ≥ 3 scheduled days in a row.

**Request**: `{ "habitId": 17 }`

**Response `200 OK`**: `{ "message": "Three days off is a pause, not a stop..." }`

- Cached per `(userId, habitId, streakBreakEpoch)` so repeat calls within one slump return the same message (and don't burn the rate limit).
- `429 RATE_LIMITED` after 3 successful generations / user / day.
- `409 NOT_IN_SLUMP` if the habit is not currently in a ≥ 3-day miss streak — prevents the endpoint being used as a general-purpose LLM proxy.

---

## Cross-Cutting Concerns

### Rate Limiting

| Scope | Limit | Backing store |
|---|---|---|
| `/auth/login`, `/auth/register` | 5 req / IP / 15 min | Redis (Day 25) |
| `/attachments/presign` | 20 req / user / hour | Redis |
| LLM endpoints (Day 29) | 3 req / user / day | Redis |

Over-limit → `429 RATE_LIMITED` with `Retry-After` header.

If Redis is unavailable while checking a rate-limit counter, the request **fails open**: the API logs a WARN, skips the limit for that request, and continues. Availability wins over rate-limit precision for the MVP; credential checks and ownership checks still run normally.

### Idempotency

Currently none. Write endpoints are naturally idempotent-ish via unique constraints (`DUPLICATE_CHECK_IN`, `EMAIL_TAKEN`). If a future integration needs strict idempotency we'll add `Idempotency-Key` headers — tracked as a post-MVP item.

### CORS

- Allowed origins (prod): `https://streakup.dev`, `https://app.streakup.dev`.
- Allowed origins (dev): `http://localhost:5173`.
- `Allow-Credentials: true` (required for the refresh-token cookie).
- Headers: `Authorization, Content-Type`.

### Dependency failure policy

- **MySQL** is the source of truth. If it is unavailable, affected endpoints return `503 DEPENDENCY_UNAVAILABLE`.
- **S3** is required for presigned upload/download URLs. If presign or object verification cannot complete, attachment endpoints return `503 DEPENDENCY_UNAVAILABLE`.
- **Redis** is treated as a performance and coordination layer. Heatmap/streak reads fall back to MySQL where possible; check-in writes still commit to MySQL if cache updates fail; rate limiting fails open as described above. A Redis outage should only produce `503` for an endpoint with no documented DB fallback or degrade path.

### Observability

- Every request gets a `traceId` (UUID v4) added to the MDC and echoed in responses and error bodies.
- `/actuator/health` is public; `/actuator/metrics` and `/actuator/info` are behind an `admin` role (not exposed in MVP).

---

## What's *Not* in the API (intentional)

| Capability | Why not |
|---|---|
| `/auth/forgot-password` | Email infra not in the core path; add post-MVP. |
| WebSocket / SSE | No realtime feature in MVP. Leaderboard is pull-refresh. |
| GraphQL | No client-shaped-query need; REST + computed list fields cover dashboard. |
| Batch endpoints | Optimistic UI + HTTP/2 multiplexing make this unnecessary for MVP volume. |
| OpenAPI-driven codegen | Noted in Day 18 as *optional*; SpringDoc generates the spec, but manually-typed TS is cheap at this endpoint count. |

---

## Appendix: HTTP Status Code Policy

| Condition | Status | Why |
|---|---|---|
| Resource belongs to another user | `404 NOT_FOUND` (not 403) | Prevents ID enumeration — attacker cannot distinguish "exists but forbidden" from "doesn't exist". |
| Resource belongs to caller but caller lacks role | `403 FORBIDDEN` | Same-user authorisation failure is informative and not a disclosure. |
| Validation error | `400` with `details[]` | Clients can render field-level errors (`ApiErrorResponse.details`). |
| Unknown JSON field | `400 BAD_REQUEST` | Jackson `FAIL_ON_UNKNOWN_PROPERTIES=true`. Protects against typos. |
| Read after stale cache | Serve cached | Staleness window is 5 min on heatmap; acceptable per `requirements.md` NFRs. |

# StreakUp — API Error Code Reference

> **Audience**: frontend engineers writing error handlers; backend engineers adding new failure paths.
> **Source of truth**: this file. Every `code` value that can appear in an `ApiErrorResponse.code` field is listed here. If the backend wants to return a new code, it lands in this table in the same PR.
> **See also**: `api-spec.md` for the envelope shape and per-endpoint error lists; `auth-flow.md` for the auth-specific codes in context.

---

## Envelope

Every error response — validation, auth, business, unhandled — returns this shape (defined in `api-spec.md`):

```json
{
  "timestamp": "2026-04-23T10:15:30.000Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request body is invalid.",
  "path": "/api/v1/habits",
  "traceId": "f4a2c19d-...",
  "details": [
    { "field": "name", "code": "NOT_BLANK", "message": "must not be blank" }
  ]
}
```

- The top-level `code` is from the **top-level codes** table below — scoped per HTTP status.
- Each entry in `details[]` has its own `code` from the **field-level codes** table. These are smaller and reusable across endpoints.
- `traceId` matches the request's MDC log line; include it in bug reports.

---

## Top-Level Codes

### 400 — Bad Request

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `VALIDATION_FAILED` | Bean Validation failure on the request body. `details[]` is populated. | Any write endpoint | Render field-level errors from `details[]`. |
| `BAD_REQUEST` | Malformed JSON, unknown JSON field (`FAIL_ON_UNKNOWN_PROPERTIES`), type-mismatch on a query param. | Any | Generic "check your input" toast; this usually indicates a client bug. |
| `CLIENT_DATE_OUT_OF_RANGE` | `clientDate` on `POST /check-ins` is not the user's local today or yesterday. | `POST /check-ins` | Prompt the user to check their device clock or timezone; offer to open `/settings`. |

### 401 — Unauthenticated

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `UNAUTHENTICATED` | Credentials rejected at login, or no access token supplied on a protected route. Message is deliberately generic to prevent email enumeration. | `POST /auth/login`; any `🔒` endpoint | On `/auth/login`: show a generic "email or password is incorrect". Elsewhere: trigger silent refresh; if that fails, redirect to `/login`. |
| `TOKEN_EXPIRED` | Access token signature is valid but `exp` is past. | Any `🔒` endpoint | Run the silent-refresh queue in `api/client.ts`. On success, replay the original request. |
| `TOKEN_INVALID` | Access token signature invalid / algorithm mismatch / tampered; or refresh cookie missing, expired, or already revoked. | Any `🔒` endpoint; `POST /auth/refresh` | Clear auth state, redirect to `/login`. |
| `TOKEN_REUSE_DETECTED` | A refresh token in an already-rotated lineage was presented — treated as a theft signal; **all** tokens for that user are revoked. | `POST /auth/refresh` | Clear auth state, redirect to `/login` with a banner ("You were signed out for security"). |

### 403 — Forbidden

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `FORBIDDEN` | Caller is authenticated but lacks the role for a non-resource-scoped endpoint (e.g., `/actuator/metrics`). **Never** used for "this resource isn't yours" — that's `404`. | Admin-only routes (post-MVP) | Generic "you don't have access" view. |

### 404 — Not Found

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `NOT_FOUND` | Resource doesn't exist, **or** it exists but belongs to another user (masked to prevent ID enumeration; see `api-spec.md` Appendix). Also returned when `attachmentIds` on `POST /check-ins` refer to rows the caller doesn't own or that are no longer attachable. | Any resource-scoped `🔒` endpoint | Navigate back to the list view; show "not found" toast. |

### 405 — Method Not Allowed

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `METHOD_NOT_ALLOWED` | The route exists, but the HTTP method is not supported. | Any | Treat as a client integration bug; check the API client method mapping. |

### 409 — Conflict

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `CONFLICT` | Generic unique-constraint violation that doesn't warrant its own code — e.g., tag name already in use for this user. | `POST /tags`, `PATCH /tags/{id}` | Highlight the offending field. |
| `DUPLICATE_CHECK_IN` | A check-in already exists for `(habit, checkInDate)`. | `POST /check-ins` | Tell the user they already checked in; re-fetch the habit card to show the existing state. |
| `EMAIL_TAKEN` | Email address already registered. | `POST /auth/register` | Field-level error on the email input; offer "sign in instead". |
| `NOT_IN_SLUMP` | Caller invoked `/ai/encouragement` for a habit that isn't in a ≥ 3-day miss streak. Prevents the endpoint being used as a general LLM proxy. | `POST /ai/encouragement` (P2) | Hide the encouragement CTA until the client detects a valid slump. |

### 415 — Unsupported Media Type

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `UNSUPPORTED_MEDIA_TYPE` | The request `Content-Type` is not supported by the endpoint. | Any write endpoint | Treat as a client integration bug; send `application/json` unless the endpoint says otherwise. |

### 429 — Too Many Requests

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `RATE_LIMITED` | Caller exceeded the per-scope limit (see `api-spec.md` Rate Limiting). `Retry-After` header is set. | `POST /auth/login`, `POST /auth/register`, `POST /attachments/presign`, `POST /ai/encouragement` | Disable the triggering button until `Retry-After` elapses; show a calm message. |

### 500 — Internal Error

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `INTERNAL_ERROR` | Unhandled exception reaches `GlobalExceptionHandler`. `traceId` matches the request's MDC log. | Any | Generic error toast with "if this keeps happening, share this ID: `{traceId}`". |

### 503 — Dependency Unavailable

| `code` | When returned | Typical trigger endpoint(s) | Frontend handling |
|---|---|---|---|
| `DEPENDENCY_UNAVAILABLE` | A source-of-truth dependency (MySQL) or required external service (S3 presign/object verification) fails or times out. Redis failures usually degrade or fall back per `api-spec.md`; only Redis-only paths with no fallback should emit this code. | Any critical dependency path | Same generic 5xx toast as `INTERNAL_ERROR`; retry is safe for idempotent GETs. |

---

## Field-Level Codes (inside `details[]`)

Field codes are intentionally small and reusable. The backend's validator annotations map 1:1 to these; adding a new one requires adding a row here.

| `code` | Meaning | Typical sources |
|---|---|---|
| `NOT_BLANK` | Required string was empty, whitespace-only, or missing. | `@NotBlank` |
| `NOT_NULL` | Required non-string field was null. | `@NotNull` |
| `SIZE` | String / collection length out of bounds. `message` includes the limits. | `@Size`, `@Length` |
| `MIN` / `MAX` | Numeric bound violated. | `@Min`, `@Max` |
| `PATTERN` | Regex mismatch (hex colour, tag slug, password strength, etc.). | `@Pattern` |
| `EMAIL` | Not an RFC-5322 email. | `@Email` |
| `TIMEZONE_INVALID` | Not a recognised IANA zone (`java.time.ZoneId.of` threw). | Custom `@ValidTimezone` |
| `FREQUENCY_DAYS_REQUIRED` | `frequencyType = CUSTOM` without `frequencyDays`. | Custom validator on `HabitCreateRequest` |
| `FREQUENCY_DAYS_INVALID` | `frequencyDays` not a CSV of 1–7 with no duplicates. | Same |
| `CONTENT_TYPE_UNSUPPORTED` | `contentType` not in `{image/jpeg, image/png}`. | `POST /attachments/presign` |
| `SIZE_EXCEEDS_LIMIT` | `sizeBytes > ATTACHMENT_MAX_BYTES`. | Same |

---

## Per-Endpoint Quick Reference

> Cross-check only — the authoritative error list for each endpoint lives in `api-spec.md`. This table is for answering "what can this endpoint throw?" without scrolling.

| Endpoint | Possible top-level codes |
|---|---|
| `POST /auth/register` | `VALIDATION_FAILED`, `EMAIL_TAKEN`, `RATE_LIMITED` |
| `POST /auth/login` | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `RATE_LIMITED` |
| `POST /auth/refresh` | `TOKEN_INVALID`, `TOKEN_REUSE_DETECTED` |
| `POST /auth/logout` | *(none — idempotent 204)* |
| `GET /users/me` | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `TOKEN_INVALID` |
| `PATCH /users/me` | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| `GET /habits`, `POST /habits`, `GET|PATCH|DELETE /habits/{id}` | `VALIDATION_FAILED`, `NOT_FOUND`, `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| `POST /check-ins` | `VALIDATION_FAILED`, `CLIENT_DATE_OUT_OF_RANGE`, `DUPLICATE_CHECK_IN`, `NOT_FOUND`, `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| `GET|PATCH|DELETE /check-ins/{id}` | `VALIDATION_FAILED`, `NOT_FOUND`, `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| `POST /tags`, `PATCH /tags/{id}` | `VALIDATION_FAILED`, `CONFLICT`, `NOT_FOUND`, `UNAUTHENTICATED` |
| `DELETE /tags/{id}` | `NOT_FOUND`, `UNAUTHENTICATED` |
| `POST /attachments/presign` | `VALIDATION_FAILED`, `RATE_LIMITED`, `UNAUTHENTICATED` |
| `GET /attachments/{id}/url` | `NOT_FOUND`, `UNAUTHENTICATED` |
| `GET /stats/*` | `BAD_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| `POST /ai/encouragement` (P2) | `NOT_FOUND`, `NOT_IN_SLUMP`, `RATE_LIMITED`, `UNAUTHENTICATED` |

Any endpoint can additionally return `500 INTERNAL_ERROR` (uncaught exception) or `503 DEPENDENCY_UNAVAILABLE` (a required dependency is reachable but failing and the endpoint has no documented fallback). Both are emitted by `GlobalExceptionHandler` as the standard `ApiErrorResponse` envelope — Spring's default `{timestamp,status,error,path}` body is **disabled** via `server.error.include-message=never` and a catch-all `@ExceptionHandler(Throwable.class)`. Every response the SPA ever sees has `code` and `traceId`; the frontend's generic 5xx handler branches on `code`, never on shape.

---

## Rules When Adding a New Code

1. Pick the most specific HTTP status. Use an existing top-level code if it fits.
2. If you invent a new code, add a row here in the same PR. No undocumented codes.
3. Codes are `SCREAMING_SNAKE_CASE`, prefer nouns over verbs (`DUPLICATE_CHECK_IN`, not `CHECK_IN_DUPLICATED`).
4. The `message` field is human-readable English; the `code` field is the machine key. Frontend logic must branch on `code`, never on `message`.
5. Don't leak implementation details (stack traces, DB error strings, table names) into `message`. That's what `traceId` + server logs are for.

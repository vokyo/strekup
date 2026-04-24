# StreakUp — Environment Variables

> **Audience**: anyone booting the stack locally or configuring a deployment.
> **Scope**: every env var the backend, frontend, or CI pipeline reads. If a variable isn't listed here, it isn't consumed.
> **Secret handling**: nothing in this table is committed to the repo. Local values live in `.env.local` (gitignored); prod values are loaded from AWS SSM Parameter Store at container boot.

---

## Backend (Spring Boot)

Read via `@Value` or Spring's `application-{profile}.yml`. All variables are required in prod unless marked **Optional**.

### Core

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod`. Controls which `application-*.yml` is active. | Yes |
| `SERVER_PORT` | `8080` | HTTP port the API binds to. Caddy proxies to this in prod. | Yes |
| `APP_BASE_URL` | `http://localhost:8080` | Used for building absolute URLs in email templates and CORS checks. | Yes |
| `APP_FRONTEND_ORIGIN` | `http://localhost:5173` | Allowed CORS origin(s). Comma-separated list in prod: `https://streakup.dev,https://app.streakup.dev`. | Yes |

### Database (MySQL)

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/streakup?useSSL=false&serverTimezone=UTC` | JDBC URL. Prod adds `useSSL=true&requireSSL=true`. | Yes |
| `DB_USERNAME` | `streakup` | — | Yes |
| `DB_PASSWORD` | `streakup` | From SSM in prod. | Yes |
| `DB_POOL_MAX_SIZE` | `10` | HikariCP `maximumPoolSize`. Keep ≤ RDS instance's `max_connections`/2. | Optional (default 10) |

### Redis

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `REDIS_HOST` | `localhost` | — | Yes |
| `REDIS_PORT` | `6379` | — | Yes |
| `REDIS_PASSWORD` | *(empty)* | Required once ElastiCache AUTH is enabled. | Optional (MVP) / Yes (post-ElastiCache) |
| `REDIS_TIMEOUT_MS` | `2000` | Lettuce command timeout. | Optional |
| `REDIS_SSL_ENABLED` | `false` | `false` for the MVP Docker sidecar; set to `true` when using ElastiCache with in-transit encryption. | Optional |

### JWT / Auth (see ADR 0002, 0007)

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `JWT_SECRET` | `dev-only-do-not-ship` | HS256 signing secret; ≥ 32 random bytes base64-encoded in prod. Rotation = redeploy. | Yes |
| `JWT_ACCESS_TTL_SECONDS` | `900` | 15 min. Changing this without coordinating with the frontend's silent-refresh timing can cause user-visible auth churn. | Optional |
| `JWT_REFRESH_TTL_SECONDS` | `2592000` | 30 days. Matches the refresh cookie `Max-Age`. | Optional |
| `REFRESH_COOKIE_SECURE` | `false` | `true` in prod. Cookie is rejected on plain HTTP. | Yes |

> **No `REFRESH_COOKIE_DOMAIN`.** The refresh cookie is intentionally **host-only** on `api.streakup.dev` (see [auth-flow.md](auth-flow.md) §Cookie attributes). Setting a `Domain` attribute would *widen* the cookie to subdomains of `api.streakup.dev`, not narrow it — the opposite of what's wanted. The SPA on `app.streakup.dev` reaches the cookie only via cross-origin fetch + `credentials: 'include'`, never via cookie sharing. The backend sets the cookie without a `Domain` attribute; no env var is provided for overriding this.

### S3 / Attachments

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `AWS_REGION` | `ap-southeast-2` | — | Yes |
| `AWS_S3_BUCKET` | `streakup-dev-uploads` | Bucket is private; objects accessed via presigned URLs only. | Yes |
| `AWS_S3_ENDPOINT_OVERRIDE` | `http://localhost:4566` | LocalStack endpoint in dev; unset in prod. | Optional |
| `AWS_ACCESS_KEY_ID` | `test` | Prod uses IAM instance profile — leave unset on EC2. | Optional |
| `AWS_SECRET_ACCESS_KEY` | `test` | Same. | Optional |
| `ATTACHMENT_PRESIGN_TTL_SECONDS` | `300` | 5 min. | Optional |
| `ATTACHMENT_MAX_BYTES` | `5242880` | 5 MB, aligned with `US-08`. | Optional |

### Email (reminders — P2)

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `MAIL_HOST` | `localhost` | Mailtrap in dev, SES SMTP endpoint in prod. | Yes (once `US-13` ships) |
| `MAIL_PORT` | `1025` | — | Yes (same) |
| `MAIL_USERNAME` | *(empty)* | — | Yes (same) |
| `MAIL_PASSWORD` | *(empty)* | — | Yes (same) |
| `MAIL_FROM_ADDRESS` | `no-reply@streakup.dev` | Must be a verified identity in SES. | Yes (same) |
| `REMINDER_FEATURE_ENABLED` | `false` | Feature flag; keeps the scheduler dormant until reminders ship. | Optional |

### LLM (P2)

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | *(empty)* | Never committed; loaded from SSM. | Yes (once `US-14` ships) |
| `ANTHROPIC_MODEL` | `claude-haiku-4-5-20251001` | Cheapest model that's adequate for short encouragement copy. | Optional |
| `AI_FEATURE_ENABLED` | `false` | Feature flag; when `false`, `POST /ai/encouragement` returns `404`. | Optional |

### Observability

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `LOG_LEVEL_ROOT` | `INFO` | — | Optional |
| `LOG_LEVEL_STREAKUP` | `DEBUG` | Per-package override for `com.streakup`. | Optional |
| `ACTUATOR_EXPOSED_ENDPOINTS` | `health,info` | `metrics` is added here post-MVP once the admin role exists. | Optional |

---

## Frontend (React + Vite)

All vars must be prefixed with `VITE_` to be exposed to the browser bundle. Anything not prefixed is a build-time-only secret (and we deliberately have none on the frontend).

| Variable | Example (dev) | Description | Required in prod? |
|---|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Prod: `https://api.streakup.dev/api/v1`. | Yes |
| `VITE_SENTRY_DSN` | *(empty)* | Sentry is post-MVP; reserved so the wiring is ready. | Optional |
| `VITE_BUILD_SHA` | *(empty)* | CI injects `$GITHUB_SHA`; surfaced in a footer for debugging live demos. | Optional |

---

## CI / CD (GitHub Actions)

These are repository-level secrets, not container env vars.

| Secret | Purpose |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | IAM role assumed via OIDC for pushing to ECR / writing SSM. |
| `EC2_SSH_HOST` | Deploy target hostname. |
| `EC2_SSH_USER` | Deploy user (non-root). |
| `EC2_SSH_KEY` | Private key; pair's public half is in `authorized_keys` on the instance. |
| `VERCEL_TOKEN` | Frontend preview + production deploys. |
| `VERCEL_PROJECT_ID` | — |
| `VERCEL_ORG_ID` | — |

---

## Profile-Specific Defaults

| Profile | Where it loads from | Who uses it |
|---|---|---|
| `dev` | `application-dev.yml` + developer's `.env.local` | `./mvnw spring-boot:run`, `docker compose up` |
| `test` | `application-test.yml` — points at Testcontainers-managed MySQL/Redis | JUnit + Testcontainers |
| `prod` | `application-prod.yml` — every value above comes from SSM | EC2 deployment |

---

## Operational Rules

1. **No secrets in the repo.** `.env.local` is gitignored. CI fails the build if a commit adds a file matching `.env*` without the `.example` suffix.
2. **Rotate `JWT_SECRET` by redeploying.** Every existing access token becomes invalid; the refresh path still works because refresh tokens are opaque, not signed.
3. **Order of precedence** (lowest to highest): `application-*.yml` defaults → SSM values → environment variables → `--arg` overrides at boot. Use this exactly — overriding in the wrong layer is the most common source of "works locally, breaks in prod".
4. **When adding a new variable**: update this file in the same PR. Include the dev default, whether it's required in prod, and any cross-reference to the ADR or spec that justifies its existence.

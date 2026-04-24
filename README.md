# StreakUp

> A full-stack habit tracking web app that helps users build consistency through daily check-ins, streak tracking, visual analytics, and community leaderboards.

**Status**: Phase 0 (design + planning) complete. Implementation starts Day 4.
**Live demo**: TBD (target go-live: Day 33).
**Target window**: 6 weeks (42 days), solo build, aimed at the 2026 Auckland summer internship cycle.

---

## What it does

- Log daily check-ins for any habit you're trying to build
- Visualise consistency with a GitHub-style 365-day heatmap
- See current and longest streaks per habit, timezone-aware
- Compare yourself on a global leaderboard (with opt-out)
- Optional: photo attachments, tags, monthly completion charts, email reminders, AI encouragement on slumps

See [docs/requirements.md](docs/requirements.md) for the full product spec and user-story matrix.

---

## Tech stack

- **Backend**: Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security (JWT), Flyway, MySQL 8, Redis 7, JUnit 5 + Testcontainers, SpringDoc OpenAPI 3
- **Frontend**: React 18, TypeScript, Vite, Zustand, TanStack Query v5, React Router v6, Tailwind CSS, Recharts, React Hook Form + Zod, Vitest
- **Infra**: Docker, GitHub Actions, AWS (EC2 + RDS + S3 + SES) in `ap-southeast-2`, Vercel for the SPA
- **Local dev**: `docker-compose up` gives you MySQL + Redis + LocalStack + Mailtrap with no AWS account required

Full rationale in [ADR 0001](docs/decisions/0001-tech-stack-selection.md).

---

## Repo layout

```
.
├── README.md                  ← you are here
├── CLAUDE.md                  ← guidance for Claude Code
├── coding-standards.md        ← naming, testing, commit conventions
└── docs/
    ├── HabitTracker.md        ← 6-week phase-by-phase roadmap (Chinese)
    ├── requirements.md        ← PRD + user stories
    ├── wireframes.html        ← interactive Phase 0 wireframes
    ├── architecture.md        ← C4 diagrams, request flows, deployment topology
    ├── api-spec.md            ← REST API contract
    ├── auth-flow.md           ← JWT + refresh-token rotation flows
    ├── er-diagram.md          ← DB schema + indexes
    ├── environment-variables.md ← every env var the stack reads
    ├── spring-boot-config.md  ← profile layout, application-*.yml templates, @ValidTimezone
    ├── error-codes.md         ← top-level + field-level error code reference
    ├── decisions/             ← Architecture Decision Records
    │   ├── 0001-tech-stack-selection.md
    │   ├── 0002-refresh-token-hashing.md
    │   ├── 0003-soft-delete-scope.md
    │   ├── 0004-timezone-strategy.md
    │   ├── 0005-checkin-user-denormalisation.md
    │   ├── 0006-jpa-specification.md
    │   └── 0007-jwt-algorithm-choice.md
    └── journal/               ← phase retros (written at the end of each phase)
```

---

## Quick start (once code lands)

```bash
# Start local infra (MySQL, Redis, LocalStack S3, Mailtrap)
docker compose up -d

# Backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

Env vars: copy `.env.local.example` → `.env.local`. Full reference: [docs/environment-variables.md](docs/environment-variables.md).

---

## Roadmap

| Phase | Duration | Goal |
|---|---|---|
| **0** | 3 days | Requirements + design + ADRs locked ✅ |
| **1** | 1 week | Backend core skeleton (auth + habit + check-in CRUD) |
| **2** | 1 week | Backend depth (refresh token, permissions, dynamic query, tests) |
| **3** | 1 week | Frontend skeleton (browser-runnable full flow) |
| **4** | 1 week | Heatmap + leaderboard + S3 + reminders |
| **5** | 4 days | CI/CD + live demo |
| **6** | 3 days | Polish + resume |

Per-day tasks: [docs/HabitTracker.md](docs/HabitTracker.md).

---

## License

Private project — not open source during the job-search window. Source visible on GitHub for recruiter review only.

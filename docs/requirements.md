# StreakUp — Product Requirements Document

## One-Liner

**StreakUp is a full-stack habit tracking web app that helps users build consistency through daily check-ins, streak tracking, visual analytics, and community leaderboards.**

---

## Problem Statement

Building lasting habits is hard — not because people lack motivation, but because they lack **visibility into their own consistency**. Most habit trackers are either too simple (just a checkbox) or too bloated (gamification overload). StreakUp sits in the sweet spot: it gives users a clear picture of their progress through data visualizations (GitHub-style heatmaps, streak counters, completion charts) while adding a social layer (leaderboards) to create gentle accountability.

---

## Target Users

- **Primary**: University students and young professionals who want to build study, fitness, or self-improvement routines
- **Secondary**: Anyone looking for a lightweight, visually appealing habit tracker with community features

---

## Core User Stories

> **Priority definitions**
> - **P0 (Must Ship)** — Core loop; the app is broken without these
> - **P1 (Should Ship)** — MVP target, but first to cut if a phase runs over schedule
> - **P2 (Stretch)** — Post-launch or time-permitting; not promised in MVP

### Authentication & Profile

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-01 | As a new user, I want to **register with my email and password** so that I can start tracking my habits. | P0 | Email uniqueness validated; password strength enforced (min 8 chars, mixed case + digit); success redirects to dashboard |
| US-02 | As a returning user, I want to **log in and stay authenticated** so that I don't have to re-enter credentials constantly. | P0 | Short-lived access token + long-lived refresh token with silent refresh; session survives browser restart |
| US-03 | As a logged-in user, I want to **view and update my profile** so that I can personalise my experience. | P0 | Can update display name and timezone; saved timezone affects all date calculations and is the only supported way to change the app's definition of "today" |

### Habit Management

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-04 | As a user, I want to **create a habit with a name, description, frequency, and colour** so that I can define what I want to track. | P0 | Name required (max 100 chars); frequency options: daily / weekdays / custom days; colour picker for visual distinction |
| US-05 | As a user, I want to **edit or delete my habits** so that I can adjust my goals over time. | P0 | Edit preserves existing check-in history; delete requires confirmation; soft delete for data integrity |
| US-06 | As a user, I want to **see all my habits on a dashboard** so that I can get a quick overview of today's progress. | P0 | Dashboard shows each habit with today's check-in status (done / not done); visual indicator for current streak |

### Check-In (Core Loop)

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-07 | As a user, I want to **check in on a habit for today, or submit a late check-in for yesterday if I missed it** so that I can record my progress accurately. | P0 | One-tap check-in from dashboard; optional notes field (max 500 chars); server accepts the user's local today or yesterday only; cannot duplicate check-in for the same habit on the same day; optimistic UI update |
| US-08 | As a user, I want to **attach a photo to my check-in** so that I can document my progress visually. | P1 | Image upload; max 5 MB; JPEG/PNG only; displayed in check-in detail view |

### Tags

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-15 | As a user, I want to **create and manage tags** so that I can categorise my check-ins thematically (e.g., "reading", "cardio"). | P1 | Name 1–30 chars, `^[a-z0-9-]+$`; user-scoped uniqueness; colour picker; delete cascades into `check_in_tags` but preserves the check-ins |
| US-16 | As a user, I want to **attach tags to a check-in and filter by tag** so that I can review progress by theme. | P1 | Multi-select on create and edit; `/check-ins?tagId=` filters the list; tag chips render on check-in cards |

### Analytics & Visualisation

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-09 | As a user, I want to **see a GitHub-style heatmap of my check-ins** so that I can visualise my consistency over the past year. | P0 | 365-day grid; colour intensity = number of check-ins that day; tooltip shows date + count |
| US-10 | As a user, I want to **see my current streak and longest streak** so that I feel motivated to keep going. | P0 | Streak resets if a scheduled day is missed; displayed prominently on dashboard |
| US-11 | As a user, I want to **see a monthly completion rate chart** so that I can track trends over time. | P1 | Line or bar chart showing % completion per month for each habit |

### Community & Gamification

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-12 | As a user, I want to **see a global leaderboard** of top checkers so that I feel part of a community. | P1 | Top 10 users by total check-ins; shows my own rank even if not in top 10; users can opt out from settings; opted-out users are excluded from public rankings |

### Notifications

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-13 | As a user, I want to **receive a daily email reminder** if I haven't checked in by a configurable time so that I don't forget. | P2 | Timezone-aware scheduling; user can opt in/out; no duplicate sends in multi-instance deployment |

### AI-Powered (Stretch)

| ID | User Story | Priority | Acceptance Criteria |
|----|-----------|----------|-------------------|
| US-14 | As a user, I want to **get an AI-generated encouragement** when I've missed 3+ days so that I'm motivated to restart. | P2 | LLM-powered; result cached per habit per trigger; rate-limited to 3 requests/user/day |

---

## MVP Scope Definition

### In Scope (MVP — Phases 1–5)

**P0 — Must Ship (core loop, non-negotiable)**

| Category | Features |
|----------|----------|
| **Auth** | Email/password registration, login, access + refresh token rotation, logout |
| **Profile** | Display name, timezone selection (saved timezone is authoritative for all date logic) |
| **Habits** | Full CRUD, colour coding, frequency settings (daily / weekdays / custom) |
| **Check-ins** | Daily check-in with notes, duplicate prevention, edit/delete |
| **Heatmap** | GitHub-style 365-day grid showing check-in density |
| **Streaks** | Current streak + longest streak per habit |
| **Timezone** | Full timezone-aware date handling anchored to the user's saved IANA timezone |
| **Infra** | Containerised deployment, CI pipeline, live demo accessible via public URL |
| **Quality** | ≥ 70% backend test coverage, integration + unit tests, auto-generated API docs |

**P1 — Should Ship (MVP target, first to cut if behind)**

| Category | Features |
|----------|----------|
| **Photo upload** | Attach image to check-in (max 5 MB) |
| **Tags** | User-scoped tag CRUD; multi-tag per check-in; filter check-in list by tag |
| **Monthly chart** | Completion rate trend per month |
| **Leaderboard** | Global top 10 by total check-ins + personal rank + opt-in/opt-out visibility |

### Out of Scope (Future Iterations)

| Feature | Rationale for deferring |
|---------|----------------------|
| OAuth / social login (Google, GitHub) | Nice-to-have; email/password sufficient for MVP and demonstrates auth depth |
| Mobile app (React Native) | Web-first; responsive design covers mobile use cases |
| Habit categories / folders | Adds complexity without core value; user-scoped tags already cover lightweight categorisation |
| Social features (friends, sharing) | Leaderboard provides enough social proof for MVP |
| Data export (CSV / PDF) | Low priority for demo; easy to add later |
| Push notifications (browser) | Email reminders cover the use case for now |
| Multi-language (i18n) | English-only for NZ job market |
| Habit templates / presets | Users can create their own; templates are polish, not core |

---

## Information Architecture

```
/                       → Redirect to /dashboard (if logged in) or /login
/login                  → Login form
/register               → Registration form
/dashboard              → Today's habits overview + quick check-in
/habits/new             → Create new habit form
/habits/:id             → Habit detail: check-in history, streak, heatmap
/habits/:id/edit        → Edit habit form
/stats                  → Personal analytics: heatmap, charts, streaks
/leaderboard            → Global leaderboard
/settings               → Profile, timezone, leaderboard visibility (email preferences arrive with P2 reminders)
```

---

## Key Business Rules

1. **One check-in per habit per calendar day** (based on the user's saved timezone), enforced at both database and application level
2. **Late check-ins are limited**: writes may target only the user's local **today** or **yesterday**; future-dated or older backfills are rejected
3. **Streak calculation** is timezone-aware: a "day" is defined by the user's saved IANA timezone, not UTC; request headers never override date logic, so travellers must update their profile timezone first
4. **Soft delete** for habits: check-in history preserved, habit hidden from active views
5. **Refresh token rotation**: each use of a refresh token invalidates the old one and issues a new pair; if a previously-revoked token is reused, all tokens for that user are invalidated (token theft detection)
6. **Resource ownership**: users can only access their own habits and check-ins
7. **Resource-based unauthorised access is masked as 404** to prevent ID enumeration; 403 is reserved for same-user role failures on non-resource-scoped endpoints
8. **Leaderboard** counts all retained check-ins from users who have enabled leaderboard visibility, including history from archived habits; only hard-deleted check-ins are removed, and totals update on each check-in create/delete
9. **Leaderboard opt-out** hides a user from public rankings without deleting their habits, check-ins, or personal stats

---

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| **Response time** | API p95 < 500ms for all endpoints |
| **Availability** | 99% uptime (monitored with external health checks) |
| **Security** | OWASP Top 10 mitigations; no secrets in code; HTTPS everywhere |
| **Test coverage** | Backend ≥ 70% line coverage; Frontend ≥ 50% |
| **Browser support** | Latest 2 versions of Chrome, Firefox, Safari, Edge |
| **Accessibility** | WCAG 2.1 AA for core flows (semantic HTML, ARIA labels, keyboard navigation) |
| **Performance** | Lighthouse score ≥ 80 on all categories |

---

## Success Metrics (for Demo)

To make the live demo compelling during interviews:

- At least **3 demo accounts** with 10+ days of check-in history each
- Heatmap shows **visible patterns** (not just random dots)
- Leaderboard has **meaningful variation** in rankings
- At least one account demonstrates a **20+ day streak**
- All pages load in **< 2 seconds** on a standard connection

---

## Glossary

| Term | Definition |
|------|-----------|
| **Habit** | A recurring activity a user wants to track (e.g., "Read 30 minutes", "Exercise") |
| **Check-in** | A single record of completing a habit on a specific date |
| **Streak** | The number of consecutive scheduled days a user has checked in for a habit |
| **Heatmap** | A 365-day grid visualisation where colour intensity represents check-in volume |
| **Refresh token rotation** | Security pattern where each refresh token can only be used once; using it generates a new one |

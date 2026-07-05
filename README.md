# MessOS Backend

[![CI/CD](https://github.com/prosenjith/messos-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/prosenjith/messos-backend/actions/workflows/ci.yml)

A production-ready REST API for shared-mess household management — tracking meals, expenses, deposits, and dues for groups of people sharing a common kitchen.

Built with **Kotlin 2.4 · Ktor 3.5 · Exposed ORM · PostgreSQL 16**.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [WebSocket Events](#websocket-events)
- [Authentication Flow](#authentication-flow)

---

## Features

- **User auth** — signup, login, BCrypt-hashed passwords, JWT tokens
- **Mess management** — create a mess, generate 8-char join codes, invite members
- **Meal tracking** — log breakfast / lunch / dinner counts per member per day (upsert semantics)
- **Expense & deposit tracking** — MANAGER-controlled financial records tied to the current cycle
- **Dues calculation** — pure-function engine computing per-member meal rate and net balance
- **Monthly cycle management** — close a cycle (snapshots balances), auto-opens the next one
- **Notices** — MANAGER posts announcements visible to all members
- **Real-time push** — WebSocket endpoint broadcasts typed events to all connected mess members on every mutation

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4 (JVM 21) |
| Web framework | Ktor 3.5 (Netty engine) |
| ORM | JetBrains Exposed 0.61 (DSL) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (auto-run on startup) |
| Auth | JWT (Auth0 java-jwt) + BCrypt |
| Serialization | kotlinx.serialization |
| Real-time | Ktor WebSockets |
| Build | Gradle (Kotlin DSL) |

---

## Project Structure

```
src/main/kotlin/com/prosenjith/messos/
├── Application.kt              # Entry point, plugin wiring
├── config/
│   └── AppConfig.kt            # Typed config wrapper (database + jwt)
├── db/tables/                  # Exposed table objects (schema source of truth)
│   ├── Users.kt · Messes.kt · MessMembers.kt · MonthlyCycles.kt
│   ├── Meals.kt · Expenses.kt · Deposits.kt · Notices.kt · CycleSummaries.kt
├── models/                     # Serializable request/response DTOs
│   ├── ApiResponse.kt          # ApiSuccess<T> / ApiFailure envelope
│   └── auth/ · dues/ · cycle/ · deposit/ · expense/ · meal/ · mess/ · notice/ · ws/
├── plugins/                    # Ktor plugin configuration
│   ├── Routing.kt · Security.kt · Serialization.kt · StatusPages.kt · Sockets.kt
├── routes/                     # HTTP + WebSocket handlers
│   ├── AuthRoutes.kt · MessRoutes.kt · MealRoutes.kt · ExpenseRoutes.kt
│   ├── DepositRoutes.kt · DuesRoutes.kt · CycleRoutes.kt · NoticeRoutes.kt
│   └── WebSocketRoute.kt
├── services/                   # Business logic (all DB access lives here)
│   ├── AuthService.kt · MessService.kt · MealService.kt · ExpenseService.kt
│   ├── DepositService.kt · DuesService.kt · CycleService.kt · NoticeService.kt
└── util/
    ├── AppExceptions.kt        # Domain exceptions → mapped to HTTP codes in StatusPages
    ├── DuesCalculator.kt       # Pure function — zero DB/HTTP dependencies
    ├── JwtUtils.kt
    └── WebSocketManager.kt     # Thread-safe session store + mess-scoped broadcast

src/main/resources/
├── application.yaml            # Server, database, and JWT config
├── db/migration/               # Flyway SQL migrations V1–V9
└── openapi/documentation.yaml  # Swagger/OpenAPI spec

src/test/kotlin/com/prosenjith/messos/
└── DuesCalculatorTest.kt       # 10 unit tests for the pure DuesCalculator
```

---

## Getting Started

### Prerequisites

- JDK 21+
- Docker (for PostgreSQL)

### 1 — Start the database

```bash
docker compose up -d postgres
```

Starts PostgreSQL 16 on `localhost:5432` with database `messos_db`, user `messos`, password `messos_dev`.

An optional pgAdmin UI is also available at `http://localhost:5050` (email: `admin@messos.dev`, password: `admin`).

### 2 — Run the server

```bash
./gradlew run
```

Flyway applies all pending migrations automatically on startup. The server listens on **`http://localhost:8080`**.

### 3 — Explore the API

```bash
open http://localhost:8080/swagger
```

### Other tasks

```bash
./gradlew build                                                    # compile + test
./gradlew test                                                     # unit tests only
./gradlew test --tests "com.prosenjith.messos.DuesCalculatorTest"  # single test class
```

---

## Configuration

All settings are in `src/main/resources/application.yaml`.

```yaml
database:
  url: "jdbc:postgresql://localhost:5432/messos_db"
  driver: "org.postgresql.Driver"
  username: "messos"
  password: "messos_dev"
  pool:
    maximumPoolSize: 10
    minimumIdle: 2

jwt:
  secret: "dev-secret-change-in-production-min-32-chars!!"
  issuer: "messos-api"
  audience: "messos-app"
  expiryDays: 30
```

> **Production:** Replace `jwt.secret` with a cryptographically random string of at least 32 characters and never commit it to version control.

---

## API Reference

All endpoints are prefixed with `/api/v1`. Every response uses a consistent envelope:

```json
// Success
{ "success": true, "data": { ... } }

// Error
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "..." } }
```

### Auth

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/auth/signup` | Register a new user, receive JWT |
| `POST` | `/auth/login` | Login, receive JWT |

### Mess

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/mess` | Create a mess — issues a new mess-scoped JWT (role: MANAGER) |
| `POST` | `/mess/join` | Join a mess by join code — issues a new mess-scoped JWT (role: MEMBER) |
| `GET` | `/mess/{id}` | Get mess details and full member list |

> After `POST /mess` or `POST /mess/join`, the response includes a **new token**. Clients must swap it — the signup/login token has no `messId` claim and will be rejected by all other endpoints.

### Meals

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/meals` | MANAGER / MEMBER* | Log or update meal counts for a date (upsert) |
| `GET` | `/meals` | Any | List all meals in the current cycle |
| `DELETE` | `/meals/{id}` | MANAGER / owner | Delete a meal entry |

*Members can only log their own meals; MANAGERs can log for any member.

### Expenses

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/expenses` | MANAGER | Add an expense to the current cycle |
| `GET` | `/expenses` | Any | List all expenses in the current cycle |
| `DELETE` | `/expenses/{id}` | MANAGER | Delete an expense |

### Deposits

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/deposits` | MANAGER | Record a member's cash deposit |
| `GET` | `/deposits` | Any | List all deposits in the current cycle |
| `DELETE` | `/deposits/{id}` | MANAGER | Delete a deposit |

### Dues

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/dues` | Live per-member balances for the current cycle |

Response fields: `mealRate`, `totalExpenses`, `totalMeals`, and per-member `totalMeals`, `mealCost`, `totalDeposited`, `balance`.

### Cycle

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/cycle/close` | MANAGER | Close the current cycle, snapshot per-member balances, open a new cycle |
| `GET` | `/cycle/history` | Any | List all closed cycles with stored per-member summaries |

### Notices

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/notices` | MANAGER | Post a notice to all members |
| `GET` | `/notices` | Any | List all notices, newest first |
| `DELETE` | `/notices/{id}` | MANAGER | Delete a notice |

### WebSocket

| Endpoint | Auth |
|----------|------|
| `GET /api/v1/ws?token=<jwt>` | JWT via query param (requires `messId` claim) |

See [WebSocket Events](#websocket-events) below.

---

## Database Schema

Flyway migrations in `src/main/resources/db/migration/` run automatically on startup.

| Migration | Table | Description |
|-----------|-------|-------------|
| V1 | `users` | id, name, email (unique), password_hash |
| V2 | `messes` | id, name, join_code (unique), manager_id |
| V3 | `mess_members` | mess_id, user_id, role (MANAGER / MEMBER) |
| V4 | `monthly_cycles` | mess_id, start_date, end_date, status, meal_rate_snapshot, closed_at |
| V5 | `meals` | member_id, date, breakfast/lunch/dinner counts — unique per (member, date) |
| V6 | `expenses` | mess_id, cycle_id, amount, date, note, logged_by |
| V7 | `deposits` | mess_id, cycle_id, member_id, amount, date, logged_by |
| V8 | `notices` | mess_id, message, posted_by, created_at |
| V9 | `cycle_summaries` | cycle_id, member_id, member_name (snapshot), total_meals, meal_cost, total_deposited, balance |

---

## WebSocket Events

Connect using a JWT that carries a `messId` claim (obtained after creating or joining a mess):

```
ws://localhost:8080/api/v1/ws?token=<your-jwt>
```

All events are JSON text frames:

```json
{ "type": "MEAL_UPDATED", "data": { "mealId": "3fa85f64-..." } }
```

| Event | Trigger | `data` keys |
|-------|---------|-------------|
| `MEAL_UPDATED` | Meal logged or updated | `mealId` |
| `MEAL_DELETED` | Meal deleted | `mealId` |
| `EXPENSE_ADDED` | Expense recorded | `expenseId` |
| `EXPENSE_DELETED` | Expense deleted | `expenseId` |
| `DEPOSIT_ADDED` | Deposit recorded | `depositId` |
| `DEPOSIT_DELETED` | Deposit deleted | `depositId` |
| `CYCLE_CLOSED` | Cycle closed | `cycleId`, `newCycleId` |
| `NOTICE_POSTED` | Notice posted | `noticeId` |
| `NOTICE_DELETED` | Notice deleted | `noticeId` |

Events are broadcast to all connected members of the same mess. The recommended client pattern is to re-fetch the relevant resource upon receipt.

---

## Authentication Flow

```
POST /auth/signup  →  JWT (no messId)
POST /auth/login   →  JWT (no messId)

POST /mess         →  new JWT  { sub, messId, role: MANAGER, exp }
POST /mess/join    →  new JWT  { sub, messId, role: MEMBER,  exp }

All subsequent API calls  →  Bearer <mess-scoped JWT>
WebSocket connection      →  ?token=<mess-scoped JWT>
```

JWT claims: `sub` (userId) · `messId` · `role` · `exp`

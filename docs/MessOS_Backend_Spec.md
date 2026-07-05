# MessOS — Backend Technical Spec

> **Note:** This spec reflects the actual implementation as of 2026-07-06, updated after a
> post-implementation audit — see git history for the original pre-implementation version.

Implementation-level companion to `MessOS_PRD.md` sections 6 (Data Model) and 7 (API
Contracts). This document defines exact types, constraints, JSON shapes, and project
structure so the backend can be scaffolded and implemented directly from it.

Stack: Kotlin + Ktor + Exposed + PostgreSQL. All money values use `DECIMAL(10,2)`
(BDT has no subunit in practical use, but 2dp keeps room for paisa/edge cases).
All IDs are UUID v4. All timestamps are UTC `Instant`, serialized as ISO-8601 strings.

All routes are mounted under the `/api/v1` prefix (e.g. `POST /api/v1/auth/signup`).
Mess-scoped resource routes (`/meals`, `/expenses`, `/deposits`, `/dues`, `/cycle/*`,
`/notices`) do **not** include a `{messId}` path segment — the mess is resolved from
the `messId` claim in the caller's JWT.

---

## 1. Database Schema (Exposed)

### 1.1 Users
```kotlin
object Users : UUIDTable("users") {
    val name = varchar("name", 100)
    val phoneOrEmail = varchar("phone_or_email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
```

### 1.2 Messes
```kotlin
object Messes : UUIDTable("messes") {
    val name = varchar("name", 100)
    val joinCode = varchar("join_code", 8).uniqueIndex() // e.g. "GRM4X2Q1"
    val managerId = reference("manager_id", Users)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
```

### 1.3 MessMembers
```kotlin
enum class MemberRole { MANAGER, MEMBER }

object MessMembers : UUIDTable("mess_members") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName("role", 10, MemberRole::class)
    val joinedAt = timestamp("joined_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(messId, userId) // one membership per user per mess
    }
}
```

### 1.4 Meals
```kotlin
object Meals : UUIDTable("meals") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val memberId = reference("member_id", MessMembers, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val breakfastCount = decimal("breakfast_count", 3, 1).default(BigDecimal.ZERO)
    val lunchCount = decimal("lunch_count", 3, 1).default(BigDecimal.ZERO)
    val dinnerCount = decimal("dinner_count", 3, 1).default(BigDecimal.ZERO)
    val updatedBy = reference("updated_by", Users)
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(memberId, date) // one row per member per day, fields updated in place
    }
}
```

### 1.5 Expenses
```kotlin
object Expenses : UUIDTable("expenses") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", 10, 2)
    val date = date("date")
    val note = varchar("note", 200).nullable()
    val receiptPhotoUrl = varchar("receipt_photo_url", 500).nullable()
    val loggedBy = reference("logged_by", Users)
    val cycleId = reference("cycle_id", MonthlyCycles) // see 1.7
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
```

### 1.6 Deposits
```kotlin
object Deposits : UUIDTable("deposits") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val memberId = reference("member_id", MessMembers, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", 10, 2)
    val date = date("date")
    val loggedBy = reference("logged_by", Users)
    val cycleId = reference("cycle_id", MonthlyCycles)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
```

### 1.7 MonthlyCycles
```kotlin
enum class CycleStatus { OPEN, CLOSED }

object MonthlyCycles : UUIDTable("monthly_cycles") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable() // null while OPEN
    val status = enumerationByName("status", 10, CycleStatus::class).default(CycleStatus.OPEN)
    val mealRateSnapshot = decimal("meal_rate_snapshot", 10, 2).nullable()
    val closedAt = timestamp("closed_at").nullable()

    init {
        // Only one OPEN cycle per mess — enforced in application logic, not DB constraint,
        // since Exposed doesn't support partial unique indexes cleanly across all DBs.
    }
}
```

### 1.8 Notices
```kotlin
object Notices : UUIDTable("notices") {
    val messId = reference("mess_id", Messes, onDelete = ReferenceOption.CASCADE)
    val message = varchar("message", 500)
    val postedBy = reference("posted_by", Users)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
```

**Note:** `Expenses` and `Deposits` reference `cycleId` (not in the original PRD table) —
this is a deliberate addition: every expense/deposit belongs to exactly one monthly
cycle, which makes the dues calculation and month-close logic straightforward (sum
by `cycleId` instead of by date-range filtering). Add this column when implementing.

---

## 2. Request / Response DTOs

All responses are wrapped in a standard envelope (see Section 3). Below are the
`data` payloads only.

### 2.1 Auth

**POST /auth/signup**
```json
// Request
{ "name": "string", "phoneOrEmail": "string", "password": "string" }
// Response
{ "userId": "uuid", "token": "jwt-string" }
```

**POST /auth/login**
```json
// Request
{ "phoneOrEmail": "string", "password": "string" }
// Response
{ "userId": "uuid", "token": "jwt-string" }
```

**GET /auth/me** *(requires JWT)*
```json
// Response
{ "id": "uuid", "name": "string", "phoneOrEmail": "string" }
```

### 2.2 Mess

**POST /mess** *(requires JWT)*
```json
// Request
{ "name": "string" }
// Response — nested; callers must swap their token after this call
{
  "mess": {
    "id": "uuid",
    "name": "string",
    "joinCode": "string",
    "managerId": "uuid",
    "createdAt": "iso-8601"
  },
  "token": "jwt-string"   // new JWT now containing messId + role=MANAGER
}
```

**POST /mess/join** *(requires JWT)*
```json
// Request
{ "joinCode": "string" }
// Response — nested; callers must swap their token after this call
{
  "mess": {
    "id": "uuid",
    "name": "string",
    "joinCode": "string",
    "managerId": "uuid",
    "createdAt": "iso-8601"
  },
  "token": "jwt-string"   // new JWT now containing messId + role=MEMBER
}
```

**GET /mess/{id}** *(requires JWT; caller must be a member of this mess)*
```json
{
  "id": "uuid",
  "name": "string",
  "joinCode": "string",
  "managerId": "uuid",
  "createdAt": "iso-8601",
  "members": [
    { "id": "uuid", "name": "string", "role": "MANAGER|MEMBER" }
  ]
}
```

Notes:
- `members[].id` is the user's UUID (from the `users` table), not the `mess_members` row UUID.
- `joinCode` is always returned regardless of the caller's role.
- `currentCycle` is not included; fetch it via `GET /dues` or `GET /cycle/history`.

### 2.3 Meals

**POST /meals** *(requires JWT with messId claim)*
```json
// Request — memberId is required (no default-to-self behaviour)
{
  "memberId": "uuid",
  "date": "2026-07-03",
  "breakfastCount": 1.0,
  "lunchCount": 0.0,
  "dinnerCount": 1.0
}
// Response — the upserted Meal row
{
  "id": "uuid",
  "memberId": "uuid",
  "memberName": "string",
  "date": "2026-07-03",
  "breakfastCount": 1.0,
  "lunchCount": 0.0,
  "dinnerCount": 1.0,
  "updatedAt": "iso-8601"
}
```

**GET /meals** *(requires JWT with messId claim)*
```json
// Response — returns all meals for the current open cycle; no month filter
[
  {
    "id": "uuid",
    "memberId": "uuid",
    "memberName": "string",
    "date": "2026-07-01",
    "breakfastCount": 1.0,
    "lunchCount": 1.0,
    "dinnerCount": 0.0,
    "updatedAt": "iso-8601"
  }
]
```

**DELETE /meals/{id}** *(requires JWT with messId claim)*
```json
// Response
{ "deleted": true }
```

### 2.4 Expenses & Deposits

**POST /expenses** *(requires JWT with messId claim)*
```json
// Request
{ "amount": 1120.00, "date": "2026-07-02", "note": "Rice, dal, veg", "receiptPhotoUrl": null }
// Response
{
  "id": "uuid",
  "amount": 1120.00,
  "date": "2026-07-02",
  "note": "Rice, dal, veg",
  "receiptPhotoUrl": null,
  "loggedBy": "uuid",
  "loggedByName": "string",
  "createdAt": "iso-8601"
}
```

**GET /expenses** *(requires JWT with messId claim)*
```json
// Response — all expenses for the current open cycle; no totalThisCycle aggregate
[
  {
    "id": "uuid",
    "amount": 1120.00,
    "date": "2026-07-02",
    "note": "Rice, dal, veg",
    "receiptPhotoUrl": null,
    "loggedBy": "uuid",
    "loggedByName": "string",
    "createdAt": "iso-8601"
  }
]
```

**DELETE /expenses/{id}** *(requires JWT with messId claim)*
```json
// Response
{ "deleted": true }
```

**POST /deposits** *(requires JWT with messId claim)*
```json
// Request
{ "memberId": "uuid", "amount": 3400.00, "date": "2026-07-01" }
// Response
{
  "id": "uuid",
  "memberId": "uuid",
  "memberName": "string",
  "amount": 3400.00,
  "date": "2026-07-01",
  "loggedBy": "uuid",
  "loggedByName": "string",
  "createdAt": "iso-8601"
}
```

**GET /deposits** *(requires JWT with messId claim)*
```json
// Response — all deposits for the current open cycle; no ?memberId filter
[
  {
    "id": "uuid",
    "memberId": "uuid",
    "memberName": "string",
    "amount": 3400.00,
    "date": "2026-07-01",
    "loggedBy": "uuid",
    "loggedByName": "string",
    "createdAt": "iso-8601"
  }
]
```

**DELETE /deposits/{id}** *(requires JWT with messId claim)*
```json
// Response
{ "deleted": true }
```

### 2.5 Dues

**GET /dues** *(requires JWT with messId claim)*
```json
{
  "cycleId": "uuid",
  "cycleStartDate": "2026-07-01",
  "mealRate": 80.00,
  "totalMeals": 320.0,
  "totalExpenses": 25600.00,
  "balances": [
    {
      "memberId": "uuid",
      "memberName": "string",
      "totalMeals": 58.0,
      "mealCost": 4640.00,
      "totalDeposited": 3400.00,
      "balance": -1240.00   // negative = owes, positive = owed back
    }
  ]
}
```

**POST /cycle/close** *(requires JWT with messId claim; caller must be MANAGER)*
```json
// Response
{
  "cycleId": "uuid",
  "startDate": "2026-07-01",
  "endDate": "2026-07-31",
  "mealRate": 80.00,
  "totalExpenses": 25600.00,
  "totalMeals": 320.0,
  "closedAt": "iso-8601",
  "balances": [
    {
      "memberId": "uuid",
      "memberName": "string",
      "totalMeals": 58.0,
      "mealCost": 4640.00,
      "totalDeposited": 3400.00,
      "balance": -1240.00
    }
  ],
  "newCycleId": "uuid",
  "newCycleStartDate": "2026-08-01"
}
```

**GET /cycle/history** *(requires JWT with messId claim)*
```json
// Response — array of closed cycles, newest first
[
  {
    "cycleId": "uuid",
    "startDate": "2026-07-01",
    "endDate": "2026-07-31",
    "mealRate": 80.00,
    "closedAt": "iso-8601",
    "balances": [
      {
        "memberId": "uuid",
        "memberName": "string",
        "totalMeals": 58.0,
        "mealCost": 4640.00,
        "totalDeposited": 3400.00,
        "balance": -1240.00
      }
    ]
  }
]
```

### 2.6 Notices

**POST /notices** *(requires JWT with messId claim)*
```json
// Request
{ "message": "Bazaar money due by Friday" }
// Response
{
  "id": "uuid",
  "message": "Bazaar money due by Friday",
  "postedBy": "uuid",
  "postedByName": "string",
  "createdAt": "iso-8601"
}
```

**GET /notices** *(requires JWT with messId claim)*
```json
// Response — raw array
[
  {
    "id": "uuid",
    "message": "string",
    "postedBy": "uuid",
    "postedByName": "string",
    "createdAt": "iso-8601"
  }
]
```

**DELETE /notices/{id}** *(requires JWT with messId claim)*
```json
// Response
{ "deleted": true }
```

---

## 3. Standard Response Envelope

Every REST response — success or failure — uses this shape, so the Android client
can handle all responses uniformly:

```json
// Success
{ "success": true, "data": { /* endpoint-specific payload */ } }

// Failure
{ "success": false, "error": { "code": "INVALID_JOIN_CODE", "message": "That join code doesn't match any mess." } }
```

**Standard error codes (extend as needed):**
| Code | HTTP Status | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing/invalid/expired JWT |
| `FORBIDDEN` | 403 | Valid user, wrong role for this action |
| `NOT_FOUND` | 404 | Resource doesn't exist |
| `VALIDATION_ERROR` | 400 | Malformed/missing request fields |
| `INVALID_JOIN_CODE` | 400 | Join code doesn't match any mess |
| `DUPLICATE_ENTRY` | 409 | e.g. phone/email already registered |
| `CYCLE_ALREADY_CLOSED` | 400 | Action attempted on a closed cycle |
| `INTERNAL_ERROR` | 500 | Unhandled server error |

---

## 4. Auth (JWT)

- **Claims:** `sub` (userId), `messId` (active mess, if any), `role` (MANAGER/MEMBER
  for that mess), `iat`, `exp`
- **Expiry:** 30 days for v1 (no refresh token flow — simplicity over security
  hardening at MVP stage; revisit before any real production launch)
- **Header:** `Authorization: Bearer <token>`
- Every mess-scoped endpoint must verify the JWT's `messId` claim is present; the
  mess ID is taken from the JWT, not from the URL. Role-based permission checks
  (e.g. MANAGER-only for `POST /cycle/close`) are enforced server-side.

---

## 5. WebSocket Events

**Endpoint:** `GET /api/v1/ws?token=<jwt>` — connect with JWT in the query string.
The `messId` claim must be present in the token (i.e. the caller must have joined
a mess). Authorization header is not supported for the WebSocket upgrade.

All server-pushed messages share this envelope:
```json
{ "type": "MEAL_UPDATED", "data": { "mealId": "uuid" } }
```

`data` carries only the ID of the affected resource. Clients should re-fetch the
relevant REST endpoint on receipt rather than using the WebSocket payload directly.

| Event | Sent when | `data` key |
|---|---|---|
| `MEAL_UPDATED` | Any meal entry created or edited | `mealId` |
| `MEAL_DELETED` | A meal entry is deleted | `mealId` |
| `EXPENSE_ADDED` | New bazaar expense logged | `expenseId` |
| `EXPENSE_DELETED` | An expense is deleted | `expenseId` |
| `DEPOSIT_ADDED` | New deposit logged | `depositId` |
| `DEPOSIT_DELETED` | A deposit is deleted | `depositId` |
| `NOTICE_POSTED` | New notice posted | `noticeId` |
| `NOTICE_DELETED` | A notice is deleted | `noticeId` |
| `CYCLE_CLOSED` | Manager closes the month | `cycleId`, `newCycleId` |

Client behavior: on any event, refresh the relevant list or screen if currently
visible. There is no `DUES_RECALCULATED` push event; the client should re-fetch
`GET /dues` after any meal, expense, or deposit mutation.

---

## 6. Ktor Project Structure

```
messos-backend/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/com/prosenjith/messos/
│   ├── Application.kt              // main(), module configuration
│   ├── config/
│   │   └── AppConfig.kt            // reads application.yaml once at startup
│   ├── plugins/
│   │   ├── Security.kt             // JWT setup
│   │   ├── Serialization.kt        // kotlinx.serialization config
│   │   ├── Sockets.kt              // WebSocket plugin config
│   │   ├── Databases.kt            // Exposed + connection pool setup
│   │   ├── Routing.kt              // mounts all route extensions under /api/v1
│   │   └── StatusPages.kt          // maps AppExceptions to HTTP status codes
│   ├── db/tables/                  // one file per Exposed table object
│   ├── models/
│   │   ├── ApiResponse.kt          // ApiSuccess<T> / ApiFailure envelope
│   │   ├── auth/AuthDtos.kt
│   │   ├── mess/MessDtos.kt
│   │   ├── meal/MealDtos.kt
│   │   ├── expense/ExpenseDtos.kt
│   │   ├── deposit/DepositDtos.kt
│   │   ├── dues/DuesDtos.kt
│   │   ├── cycle/CycleDtos.kt
│   │   ├── notice/NoticeDtos.kt
│   │   └── ws/WsEvent.kt
│   ├── routes/                     // one file per resource
│   ├── services/                   // business logic; return typed *Record data classes
│   └── util/
│       ├── AppExceptions.kt        // domain exception hierarchy
│       ├── DuesCalculator.kt       // pure function — inputs: meals+expenses+deposits → balances
│       ├── JwtUtils.kt             // generateToken(userId, messId?, role?)
│       ├── PasswordUtils.kt
│       └── WebSocketManager.kt     // ConcurrentHashMap<messId, Set<session>>
└── src/test/kotlin/...             // unit tests (DuesCalculatorTest has 10 cases)
```

**Why `DuesCalculator` is called out specifically:** this is the single most
important piece of logic in the entire app — get it wrong and the whole product's
trust proposition breaks. It should be a pure function (inputs: meals + expenses +
deposits → output: per-member balances) with no database or HTTP dependencies, so
it's trivially unit-testable in isolation. Write tests for it before wiring it into
any route.

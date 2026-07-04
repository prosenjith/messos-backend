# MessOS — Backend Technical Spec

Implementation-level companion to `MessOS_PRD.md` sections 6 (Data Model) and 7 (API
Contracts). This document defines exact types, constraints, JSON shapes, and project
structure so the backend can be scaffolded and implemented directly from it.

Stack: Kotlin + Ktor + Exposed + PostgreSQL. All money values use `DECIMAL(10,2)`
(BDT has no subunit in practical use, but 2dp keeps room for paisa/edge cases).
All IDs are UUID v4. All timestamps are UTC `Instant`, serialized as ISO-8601 strings.

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

### 2.2 Mess

**POST /mess**
```json
// Request
{ "name": "string" }
// Response
{ "messId": "uuid", "name": "string", "joinCode": "string" }
```

**POST /mess/join**
```json
// Request
{ "joinCode": "string" }
// Response
{ "messId": "uuid", "name": "string", "role": "MEMBER" }
```

**GET /mess/{id}**
```json
{
  "messId": "uuid",
  "name": "string",
  "joinCode": "string",           // only included if caller is MANAGER
  "members": [
    { "memberId": "uuid", "userId": "uuid", "name": "string", "role": "MANAGER|MEMBER" }
  ],
  "currentCycle": { "cycleId": "uuid", "startDate": "2026-07-01", "status": "OPEN" }
}
```

### 2.3 Meals

**POST /mess/{id}/meals**
```json
// Request
{
  "memberId": "uuid",             // omit to default to caller's own memberId
  "date": "2026-07-03",
  "breakfastCount": 1.0,
  "lunchCount": 0.0,
  "dinnerCount": 1.0
}
// Response — the updated Meal row
{ "mealId": "uuid", "memberId": "uuid", "date": "2026-07-03",
  "breakfastCount": 1.0, "lunchCount": 0.0, "dinnerCount": 1.0 }
```

**GET /mess/{id}/meals?month=2026-07**
```json
{
  "meals": [
    { "mealId": "uuid", "memberId": "uuid", "date": "2026-07-01",
      "breakfastCount": 1.0, "lunchCount": 1.0, "dinnerCount": 0.0 }
  ]
}
```

### 2.4 Expenses & Deposits

**POST /mess/{id}/expenses**
```json
// Request
{ "amount": 1120.00, "date": "2026-07-02", "note": "Rice, dal, veg", "receiptPhotoUrl": null }
// Response — the created Expense row (includes generated expenseId, cycleId)
```

**GET /mess/{id}/expenses**
```json
{ "expenses": [ { "expenseId": "uuid", "amount": 1120.00, "date": "2026-07-02",
  "note": "Rice, dal, veg", "loggedBy": "uuid" } ],
  "totalThisCycle": 18450.00 }
```

**POST /mess/{id}/deposits**
```json
// Request
{ "memberId": "uuid", "amount": 3400.00, "date": "2026-07-01" }
```

**GET /mess/{id}/deposits** — same list + per-member-filterable via `?memberId=`

### 2.5 Dues

**GET /mess/{id}/dues**
```json
{
  "cycleId": "uuid",
  "mealRate": 80.00,
  "totalMeals": 320.0,
  "totalBazaar": 25600.00,
  "members": [
    {
      "memberId": "uuid", "name": "string",
      "mealsEaten": 58.0, "mealCost": 4640.00,
      "depositsPaid": 3400.00,
      "balance": -1240.00   // negative = owes, positive = owed
    }
  ]
}
```

**POST /mess/{id}/cycle/close**
```json
// Response
{ "cycleId": "uuid", "closedAt": "timestamp", "summary": [ /* same shape as dues.members */ ] }
```

**GET /mess/{id}/cycle/history**
```json
{ "cycles": [ { "cycleId": "uuid", "startDate": "...", "endDate": "...",
  "mealRateSnapshot": 80.00, "summary": [ /* per-member balances at close */ ] } ] }
```

### 2.6 Notices

**POST /mess/{id}/notices**
```json
// Request
{ "message": "Bazaar money due by Friday" }
```

**GET /mess/{id}/notices**
```json
{ "notices": [ { "noticeId": "uuid", "message": "string", "postedBy": "string", "createdAt": "timestamp" } ] }
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
- Every mess-scoped endpoint (`/mess/{id}/...`) must verify the JWT's `messId`
  claim matches the `{id}` in the path, and that `role` permits the action
  (see PRD section 4 permission table) — enforced server-side, never trusted
  from the client.

---

## 5. WebSocket Events

**Endpoint:** `ws/mess/{id}` — connect with JWT in the initial handshake
(`Authorization` header or `?token=` query param).

All server-pushed messages share this envelope:
```json
{ "event": "MEAL_UPDATED", "data": { /* shape matches the relevant REST response */ }, "timestamp": "iso-8601" }
```

| Event | Sent when | `data` shape |
|---|---|---|
| `MEAL_UPDATED` | Any meal entry created/edited | Same as POST /meals response |
| `EXPENSE_ADDED` | New bazaar expense logged | Same as POST /expenses response |
| `DEPOSIT_ADDED` | New deposit logged | Same as POST /deposits response |
| `DUES_RECALCULATED` | After any meal/expense/deposit change | Same as GET /dues response |
| `NOTICE_POSTED` | New notice posted | Same as POST /notices response |
| `CYCLE_CLOSED` | Manager closes the month | Same as POST /cycle/close response |

Client behavior: on `DUES_RECALCULATED`, refresh the home screen dues display;
on other events, refresh the relevant list/screen if currently visible.

---

## 6. Ktor Project Structure

```
messos-backend/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/kotlin/com/prosenjith/messos/
│   ├── Application.kt              // main(), module configuration
│   ├── plugins/
│   │   ├── Security.kt             // JWT setup
│   │   ├── Serialization.kt        // kotlinx.serialization config
│   │   ├── Sockets.kt              // WebSocket plugin config
│   │   └── Databases.kt            // Exposed + connection pool setup
│   ├── db/
│   │   └── Tables.kt               // all Exposed table objects (Section 1)
│   ├── models/
│   │   └── Dtos.kt                 // request/response data classes (Section 2)
│   ├── routes/
│   │   ├── AuthRoutes.kt
│   │   ├── MessRoutes.kt
│   │   ├── MealRoutes.kt
│   │   ├── ExpenseRoutes.kt
│   │   ├── DepositRoutes.kt
│   │   ├── DuesRoutes.kt
│   │   └── NoticeRoutes.kt
│   ├── services/
│   │   ├── DuesCalculator.kt       // the core hisab engine — pure function, unit-testable
│   │   └── MessService.kt, MealService.kt, etc.
│   └── websocket/
│       └── MessSocketManager.kt    // tracks connections per mess, broadcasts events
└── src/test/kotlin/...             // unit tests, especially for DuesCalculator
```

**Why `DuesCalculator` is called out specifically:** this is the single most
important piece of logic in the entire app — get it wrong and the whole product's
trust proposition breaks. It should be a pure function (inputs: meals + expenses +
deposits → output: per-member balances) with no database or HTTP dependencies, so
it's trivially unit-testable in isolation. Write tests for it before wiring it into
any route.

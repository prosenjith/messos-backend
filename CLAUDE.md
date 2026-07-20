# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Local DB (required before running the server)
docker compose up -d postgres

# Run / build / test
./gradlew run          # starts server on :8080
./gradlew build        # compile + test
./gradlew test         # run tests only

# Swagger UI (once server is running)
open http://localhost:8080/swagger
```

There is no lint step configured. There is no way to run a single test class in isolation beyond `./gradlew test --tests "com.prosenjith.messos.SomeTest"`.

## Architecture

**Stack:** Kotlin 2.4 · Ktor 3.5 (Netty) · Exposed ORM · PostgreSQL 16 · Flyway migrations · JWT auth · WebSockets

**Root package:** `com.prosenjith.messos`

**Request lifecycle:** HTTP request → Ktor plugin interceptors (CallLogging → ContentNegotiation → StatusPages → Auth) → `routes/` handler → `services/` business logic → Exposed DSL query → PostgreSQL

**Key architectural rules:**
- Every REST response uses `ApiSuccess<T>` or `ApiFailure` from `models/ApiResponse.kt` — never respond with a raw object.
- All config is read once at startup via `config/AppConfig.kt` which wraps `application.yaml`. Add new config keys there first, not inline.
- DB schema lives in two places that must stay in sync: Exposed table objects in `db/tables/` (Kotlin) and Flyway SQL files in `src/main/resources/db/migration/` (SQL). When you add a column to an Exposed table, also write a new `V{N}__*.sql` migration.
- Flyway migrations run automatically on startup. Migration files are numbered sequentially (`V1` → users, `V2` → messes, `V3` → mess_members, `V4` → monthly_cycles, `V5` → meals, `V6` → expenses, `V7` → deposits, `V8` → notices, `V9` → cycle_summaries, `V10` → refresh_tokens, `V11` → profile_image_url on users, `V12` → category on expenses). FK order matters — parent tables before children.
- `DuesCalculator` is a **pure function** with no DB or HTTP dependencies — inputs: meals + expenses + deposits + allMemberUserIds; output: per-member balances. Lives in `util/DuesCalculator.kt`. 13 unit tests in `DuesCalculatorTest.kt` all pass.
- Ktor catalog quirk: the version catalog (`io.ktor:ktor-version-catalog:3.5.0`) uses camelCase for multi-word entries — `server-contentNegotiation`, `server-statusPages`, `server-callLogging`, `server-requestValidation`. WebSockets package is `io.ktor.server.websocket` (singular); Swagger is `io.ktor.server.plugins.swagger`.

**Build sequence (implemented step by step):**
1. ✅ Skeleton + DB + Swagger
2. ✅ Auth (signup/login/JWT)
3. ✅ Mess + MessMember
4. ✅ Meals
5. ✅ Expenses + Deposits
6. ✅ DuesCalculator pure function + unit tests
7. ✅ GET /dues endpoint
8. ✅ Monthly Cycle close
9. ✅ Notices
10. ✅ WebSocket broadcast layer
11. ✅ Image upload (S3-backed)
12. ✅ Expense categories (BAZAAR / UTILITY)

**JWT design:** Claims carry `sub` (userId), `messId`, `role`. After `POST /mess` or `POST /mess/join`, a **new JWT must be issued** (the signup token has no messId). Both create-mess and join-mess responses return a fresh token alongside the mess details. `JwtUtils.generateToken()` is in `util/JwtUtils.kt` and accepts optional `messId` + `role` — use it for all token minting.

**Auth patterns (established in step 2):**
- Custom domain exceptions live in `util/AppExceptions.kt` — throw them from services, `StatusPages.kt` maps them to HTTP codes. Never `call.respond` an error directly from a service.
- Exposed DSL transactions: use `newSuspendedTransaction(Dispatchers.IO) { ... }` in every service method. Use `Table.insert { ... }` and read back `result[Table.id].value` for the generated UUID — `insertAndGetId` is not available on `UUIDTable`.
- JWT principal extraction: `call.principal<JWTPrincipal>()!!.payload.subject` gives the `userId` string; `payload.getClaim("messId").asString()` gives messId (null before joining a mess).
- `Json { encodeDefaults = true }` is set — the `success` field on `ApiSuccess`/`ApiFailure` is always serialized.

**Meal patterns (established in step 4):**
- `POST /meals` is an upsert keyed on (memberId, date). Conflict columns are `Meals.memberId` and `Meals.date`. **Must pass `onUpdateExclude = listOf(Meals.id, Meals.messId, Meals.memberId, Meals.date)`** — without it, Exposed includes all columns in the UPDATE SET, which regenerates the UUID primary key on conflict (Exposed 0.61.0 known behaviour).
- Authorization gate: check `messId` claim in JWT — if null, throw `ForbiddenException("You must join a mess first")`. Extract with `principal.payload.getClaim("messId").asString()`.
- `deleteWhere { }` lambda has receiver `T.(ISqlExpressionBuilder) -> Op<Boolean>`, not `SqlExpressionBuilder`. Use `with(it) { Meals.id eq mealId }` to bring `ISqlExpressionBuilder` in scope, enabling the `eq` extension function.

**Mess patterns (established in step 3):**
- One-mess-per-user is enforced by querying `MessMembers` before any insert in `MessService` — throw `ValidationException` if already a member.
- `POST /mess` creates the mess, member row (role=MANAGER), and first `MonthlyCycle` (status=OPEN, start_date=today UTC) all in a single `newSuspendedTransaction`.
- Join codes are 8-char uppercase alphanumeric, generated in `MessService.generateJoinCode()`. Input is normalised with `.uppercase()` in `joinMess`.
- After create/join, a fresh JWT with `messId` + `role` is returned alongside the mess payload — callers must swap their token.
- `GET /mess/{id}` uses an `innerJoin` between `MessMembers` and `Users` to return the member list in one query.
- Internal service models (`MessRecord`, `MemberRecord`, `MessWithToken`, `MessDetail`) live in `services/MessService.kt`; serializable DTOs live in `models/mess/MessDtos.kt`.

**Shared service query helpers (`services/ServiceQueries.kt`):**
- `requireMemberInMess(messId, userId)` — joins `MessMembers` + `Users`, returns `MemberInfo(memberId, name)`, throws `NotFoundException` if not found.
- `requireOpenCycle(messId)` — queries `MonthlyCycles` for OPEN status, returns `OpenCycleInfo(id, startDate)`, throws `ValidationException` if none found.
- Use these helpers in any service method that needs to look up a member or the current cycle — do not inline the raw queries again.

**Standard error codes** (defined in spec, implement in StatusPages + routes): `UNAUTHORIZED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `VALIDATION_ERROR` 400, `INVALID_JOIN_CODE` 400, `DUPLICATE_ENTRY` 409, `CYCLE_ALREADY_CLOSED` 400, `INTERNAL_ERROR` 500.

**Cycle summaries (step 8):** `cycle_summaries` table (V9) persists per-member balances at cycle-close time. `POST /cycle/close` atomically: runs DuesCalculator, inserts rows into `cycle_summaries` (stores `member_name` for historical immutability), updates `monthly_cycles` (status=CLOSED, endDate=today, mealRateSnapshot, closedAt), and inserts a new OPEN cycle starting the next day. `GET /cycle/history` reads closed cycles + their summaries ordered newest-first.

**Image upload patterns (step 11):**
- Two endpoints: `POST /api/v1/upload` (generic, stores under `public/receipts/<uuid>.<ext>`) and `PUT /api/v1/auth/me/avatar` (uploads + updates `users.profile_image_url`, stores under `public/avatars/<uuid>.<ext>`). Both require JWT auth.
- `FileStorageService` interface (`util/FileStorageService.kt`) has two implementations: `LocalFileStorageService` (dev/fallback) and `S3FileStorageService` (production). Swap the implementation in `plugins/Routing.kt` — nothing else changes.
- `S3FileStorageService` uses **AWS SDK for Java v2** (`software.amazon.awssdk:s3` + `url-connection-client`). Do NOT use `aws.sdk.kotlin:s3` — it pulls in conflicting `kotlinx.*` transitive dependencies that break Exposed table object initialization at runtime (`NoClassDefFoundError` on `Messes`/`MonthlyCycles`).
- `UploadService.parseAndStore(multipart, folder)` validates content-type (image/jpeg, image/png, image/webp only) and size (≤ 5 MB), then delegates to `FileStorageService.store(bytes, extension, folder)`. Throws `ValidationException` for bad type or oversized file.
- S3 bucket: `messos-uploads` (region `ap-south-1`). Bucket policy grants public `s3:GetObject` on `public/*` only — all uploaded URLs are directly fetchable by clients with no auth header.
- `StorageConfig` in `AppConfig` holds `s3Bucket` and `s3Region`, read via `${?AWS_S3_BUCKET}` and `${?AWS_REGION}` in `application.conf`. **Do not use `System.getenv()` for Railway env vars** — Ktor's `${?VAR}` config pattern is the only reliable path on Railway.
- Shadow jar requires `mergeServiceFiles()` (already in `build.gradle.kts`) — AWS SDK for Java v2 registers service providers via `META-INF/services/` and without this, Shadow drops entries silently.
- `Users.profileImageUrl` is nullable VARCHAR(500) added via V11 migration. `UserResponse` includes `profileImageUrl: String? = null`. `AuthService.updateProfileImage(userId, url)` updates the column and returns the updated `UserRecord`.

**Expense category patterns (step 12):**
- `enum class ExpenseCategory { BAZAAR, UTILITY }` lives in `util/DuesCalculator.kt` (alongside `ExpenseEntry`) — NOT in `db/tables/`, so `DuesCalculator` stays free of DB-layer imports. `Expenses.kt` imports it from `util`.
- `Expenses.category` is `enumerationByName<ExpenseCategory>("category", 10).default(ExpenseCategory.BAZAAR)`. Added via V12 migration (`ALTER TABLE expenses ADD COLUMN category VARCHAR(10) NOT NULL DEFAULT 'BAZAAR'`).
- `AddExpenseRequest.category: String = "BAZAAR"` — optional, defaults to BAZAAR for backward compatibility. Validated in `ExpenseService.addExpense()` via `ExpenseCategory.valueOf(categoryStr.uppercase())`; throws `ValidationException` for invalid values.
- **DuesCalculator formula with categories:** BAZAAR expenses feed `mealRate = totalBazaarExpense / totalMeals`. UTILITY expenses are split equally: `utilityShare = totalUtilityExpense / activeMemberCount`. `balance = totalDeposited − mealCost − utilityShare`.
- `DuesCalculator.calculate()` takes `allMemberUserIds: List<UUID> = emptyList()` — callers (DuesService, CycleService) pass the full mess member list so members who joined mid-cycle with no meals/deposits still appear in balances and are charged `utilityShare`. The default `emptyList()` keeps all existing unit tests passing (utility=0 → utilityShare=0 for all).
- `GET /dues` response includes `totalUtilityExpense` (top-level) and `utilityShare` (per-member balance object).
- `CycleService.closeCycle()` also passes category and `allMemberUserIds` to the calculator so cycle-close balances stored in `cycle_summaries` are correct.

**WebSocket patterns (step 10):**
- Ktor's `authenticate {}` block does not intercept WebSocket upgrades. Auth is handled manually in `routes/WebSocketRoute.kt` by reading `?token=<jwt>` from the query string and verifying with `com.auth0.jwt.JWT.require(...)`.
- `util/WebSocketManager.kt` is a singleton `object` with `ConcurrentHashMap<UUID, CopyOnWriteArraySet<DefaultWebSocketServerSession>>` keyed by messId. Call `connect`/`disconnect` in the WebSocket handler and `broadcastToMess` from route handlers.
- `broadcastToMess` is a suspend function; each per-session send is wrapped in `runCatching` so a dead session never fails the broadcast.
- All 9 mutating routes broadcast after `call.respond(...)`: `MEAL_UPDATED`, `MEAL_DELETED`, `EXPENSE_ADDED`, `EXPENSE_DELETED`, `DEPOSIT_ADDED`, `DEPOSIT_DELETED`, `CYCLE_CLOSED`, `NOTICE_POSTED`, `NOTICE_DELETED`. Event payload is `WsEvent(type, data: Map<String,String>)` carrying the relevant ID — clients re-fetch data on receipt.
- WebSocket endpoint: `GET /api/v1/ws?token=<jwt>` (requires messId claim in JWT — must join a mess first).

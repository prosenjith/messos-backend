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
- Flyway migrations run automatically on startup. Migration files are numbered sequentially (`V1` → users, `V2` → messes, `V3` → mess_members, `V4` → monthly_cycles, `V5` → meals, `V6` → expenses, `V7` → deposits, `V8` → notices). FK order matters — parent tables before children.
- `DuesCalculator` (step 6, not yet implemented) must be a **pure function** with no DB or HTTP dependencies — inputs: meals + expenses + deposits; output: per-member balances. Tests for it must pass before it is wired into any route.
- Ktor catalog quirk: the version catalog (`io.ktor:ktor-version-catalog:3.5.0`) uses camelCase for multi-word entries — `server-contentNegotiation`, `server-statusPages`, `server-callLogging`, `server-requestValidation`. WebSockets package is `io.ktor.server.websocket` (singular); Swagger is `io.ktor.server.plugins.swagger`.

**Build sequence (implemented step by step):**
1. ✅ Skeleton + DB + Swagger
2. ✅ Auth (signup/login/JWT)
3. Mess + MessMember
4. Meals
5. Expenses + Deposits
6. DuesCalculator pure function + unit tests
7. GET /dues endpoint
8. Monthly Cycle close
9. Notices
10. WebSocket broadcast layer

**JWT design:** Claims carry `sub` (userId), `messId`, `role`. After `POST /mess` or `POST /mess/join`, a **new JWT must be issued** (the signup token has no messId). Both create-mess and join-mess responses return a fresh token alongside the mess details. `JwtUtils.generateToken()` is in `util/JwtUtils.kt` and accepts optional `messId` + `role` — use it for all token minting.

**Auth patterns (established in step 2):**
- Custom domain exceptions live in `util/AppExceptions.kt` — throw them from services, `StatusPages.kt` maps them to HTTP codes. Never `call.respond` an error directly from a service.
- Exposed DSL transactions: use `newSuspendedTransaction(Dispatchers.IO) { ... }` in every service method. Use `Table.insert { ... }` and read back `result[Table.id].value` for the generated UUID — `insertAndGetId` is not available on `UUIDTable`.
- JWT principal extraction: `call.principal<JWTPrincipal>()!!.payload.subject` gives the `userId` string; `payload.getClaim("messId").asString()` gives messId (null before joining a mess).
- `Json { encodeDefaults = true }` is set — the `success` field on `ApiSuccess`/`ApiFailure` is always serialized.

**Standard error codes** (defined in spec, implement in StatusPages + routes): `UNAUTHORIZED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `VALIDATION_ERROR` 400, `INVALID_JOIN_CODE` 400, `DUPLICATE_ENTRY` 409, `CYCLE_ALREADY_CLOSED` 400, `INTERNAL_ERROR` 500.

**Cycle summaries:** A `cycle_summaries` table (V9 migration) is needed at step 8 to persist per-member balances at close-time — the spec's schema omits it but `GET /cycle/history` requires it.

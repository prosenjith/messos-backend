# MessOS Backend — Deployment Guide

## Running Locally

### Prerequisites
- Docker Desktop (for PostgreSQL)
- JDK 21+

### Steps

1. **Start the database**
   ```bash
   docker compose up -d postgres
   ```

2. **Run the server**
   ```bash
   ./gradlew run
   ```
   Server starts on http://localhost:8080. Flyway migrations run automatically on startup.

3. **Verify it's up**
   ```bash
   curl http://localhost:8080/health
   # {"status":"ok"}
   ```

4. **Swagger UI** — http://localhost:8080/swagger

### Environment Variables (local)

Local dev uses the defaults baked into `application.conf` — no `.env` file is required.
If you want to override anything locally, copy `.env.example` to `.env` and export the
variables in your shell before running `./gradlew run`. The app reads them automatically
via the `${?VAR}` HOCON substitution in `application.conf`.

---

## Environment Variables Reference

All environment-specific config is controlled by these variables. See `.env.example` for
descriptions and example values.

| Variable | Required in prod | Default (local) | Description |
|---|---|---|---|
| `PORT` | No (Railway sets it) | `8080` | Server listen port |
| `DATABASE_URL` | **Yes** | `jdbc:postgresql://localhost:5432/messos_db` | JDBC connection URL |
| `DATABASE_USERNAME` | **Yes** | `messos` | Database user |
| `DATABASE_PASSWORD` | **Yes** | `messos_dev` | Database password |
| `JWT_SECRET` | **Yes** | (insecure dev placeholder) | JWT signing secret (min 32 chars) |
| `JWT_EXPIRY_DAYS` | No | `30` | Token validity in days |
| `LOG_LEVEL` | No | `INFO` | Logback root level (`DEBUG`/`INFO`/`WARN`/`ERROR`) |

> **Security note:** The `DATABASE_PASSWORD` and `JWT_SECRET` defaults in `application.conf`
> are intentionally weak placeholders. They must be overridden in any non-local environment.
> Generate a strong JWT secret with: `openssl rand -base64 48`

---

## Railway Deployment

### First-time setup

1. **Create a Railway project** at [railway.app](https://railway.app) and connect your
   GitHub repo.

2. **Add a PostgreSQL plugin** from the Railway dashboard. This provisions a managed
   PostgreSQL instance.

3. **Set environment variables** in the Railway service settings (Variables tab):

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `JWT_SECRET` | *(generate with `openssl rand -base64 48`)* |
   | `LOG_LEVEL` | `INFO` |

   > Railway exposes PostgreSQL connection details as `${{Postgres.PGHOST}}` etc. Use the
   > reference syntax above so the values update automatically if the DB is restarted.
   >
   > **Do not set `PORT`** — Railway injects it automatically, and the HOCON config picks
   > it up via `${?PORT}`.

4. **Deploy** — Railway builds from the `Dockerfile` (specified in `railway.toml`) on
   every push to `main`. The multi-stage build produces a slim JRE-only image (~200 MB).

5. **Health check** — Railway will hit `GET /health` (configured in `railway.toml`)
   after each deploy. The service is only marked healthy and routed traffic once this
   returns 200.

### Subsequent deploys

Push to `main` → Railway rebuilds and deploys automatically. Flyway migrations run on
startup before the server accepts traffic, so schema changes are applied atomically.

### Rollback

Railway keeps the previous image. Roll back from the Deployments tab in the dashboard.

---

## Build Details

The `Dockerfile` uses a two-stage build:

- **Stage 1 (`eclipse-temurin:21-jdk-alpine`)** — full JDK + Gradle wrapper builds the
  fat JAR (`./gradlew buildFatJar -x test`). The dependency layer is cached separately
  from source so rebuilds after code-only changes are fast.

- **Stage 2 (`eclipse-temurin:21-jre-alpine`)** — copies only the compiled JAR into a
  minimal JRE image. No build tools, no source code. Final image is ~200 MB.

JVM flags used at runtime:
- `-XX:+UseContainerSupport` — respects Docker memory limits (on by default in JDK 11+)
- `-XX:MaxRAMPercentage=75.0` — caps heap at 75% of container RAM
- `-Djava.security.egd=file:/dev/./urandom` — faster startup by using a non-blocking
  entropy source

---

## Logging

Logs are written to stdout in a human-readable format. Railway collects stdout and makes
it searchable in the dashboard log viewer.

Set `LOG_LEVEL=DEBUG` in Railway Variables temporarily if you need to diagnose an issue —
revert to `INFO` afterward to reduce noise.

If you later need structured JSON logs (e.g. for Datadog or a log aggregator), add
`net.logstash.logback:logstash-logback-encoder` as a dependency and switch the
`logback.xml` appender to `LogstashEncoder`. The `LOG_LEVEL` env var will still work.

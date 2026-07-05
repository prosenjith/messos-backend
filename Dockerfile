# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install bash (needed by gradlew)
RUN apk add --no-cache bash

# Copy wrapper + build scripts first so dependency layer is cached
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

# Build fat JAR (skip tests — run them in CI separately)
COPY src/ src/
RUN ./gradlew buildFatJar --no-daemon -x test

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S messos && adduser -S messos -G messos

COPY --from=build /app/build/libs/*-all.jar app.jar
RUN chown messos:messos app.jar

USER messos
EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

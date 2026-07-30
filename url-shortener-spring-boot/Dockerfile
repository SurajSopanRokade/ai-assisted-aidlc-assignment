# syntax=docker/dockerfile:1

# --- Build stage ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Dependencies are resolved before the sources are copied so that a code-only
# change reuses the cached dependency layer instead of re-downloading the world.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
# Tests run in CI against the test profile; repeating them here would double the
# build time without adding a signal. The image is only ever built from a commit
# whose test run already passed.
RUN mvn -B clean package -DskipTests

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# wget is used by the healthcheck below.
RUN apk add --no-cache wget && \
    addgroup -S app && adduser -S -G app app

WORKDIR /app

# The SQLite file lives here and is expected to be a mounted volume in
# compose/k8s. Ownership is granted to the unprivileged user before the switch.
RUN mkdir -p /app/data && chown -R app:app /app

COPY --from=builder --chown=app:app /build/target/*.jar app.jar

# Running as root means a container escape starts with root on the host
# namespace. Nothing here needs privileges: the port is above 1024 and the only
# writable path is the data volume.
USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Lets the orchestrator distinguish "process alive" from "application usable"
# and restart or drain accordingly.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

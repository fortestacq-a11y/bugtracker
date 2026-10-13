# syntax=docker/dockerfile:1
#
# BugTracker API — Spring Boot 4 / Java 21
# Multi-stage: Maven builds the jar, a JRE-only image runs it.
#
# Build from the REPO ROOT (podman/docker both need backend/ in context):
#   podman build -f backend/Dockerfile -t <user>/bugtracker-api:latest backend
# or just use scripts/docker-build.sh, which handles the context for you.
#
# With podman, add `--format docker`: podman defaults to OCI format, which silently
# drops the HEALTHCHECK below and cannot be loaded by `docker load`. scripts/docker-build.sh
# passes that flag for you.

# ── Stage 1: compile ─────────────────────────────────────────────────────────
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependency layer first. pom.xml is copied and resolved on its own, so a source-only
# change reuses this cached layer instead of re-downloading the whole dependency tree.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp dependency:go-offline

# Now the sources. Tests are skipped by default (they need an H2 in-memory schema and
# add ~40s); pass --build-arg RUN_TESTS=true to include them.
ARG RUN_TESTS=false
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    if [ "$RUN_TESTS" = "true" ]; then \
        mvn -B -ntp clean verify; \
    else \
        mvn -B -ntp clean package -DskipTests; \
    fi

# Split the boot jar into layers. Renaming to application.jar FIRST matters: the
# extracted application layer is named after the source jar, so without the rename
# the entrypoint would have to change every time the project version bumps.
RUN cp /build/target/bugtracker-*.jar /build/application.jar \
 && java -Djarmode=tools -jar /build/application.jar extract --layers --destination /build/extracted

# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM docker.io/library/eclipse-temurin:21-jre-alpine

# Non-root. Alpine has no 'ubuntu' user in this image, so create a fixed-UID one:
# a fixed UID keeps bind-mounted volumes predictable across hosts.
RUN adduser -D -H -u 10001 -h /app appuser

WORKDIR /app

# Layers copied separately so the dependency layer stays cached across code changes.
# Copy order is dependencies -> loader -> snapshots -> application, cheapest first, so
# a one-file change re-uploads only the last ~115KB layer instead of the whole jar.
COPY --from=build --chown=10001:10001 /build/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /build/extracted/application/ ./

USER 10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080

# Spring Boot 4 changed the launcher: the extracted application jar is no longer run
# via org.springframework.boot.loader.launch.JarLauncher. It is a thin jar whose
# manifest points at lib/ via Class-Path, so `java -jar` is both correct and simpler.
#
# Exec form, not shell form: the JVM must be PID 1 so it receives SIGTERM directly and
# Spring's graceful shutdown actually runs instead of the container being killed outright.
ENTRYPOINT ["java", "-jar", "application.jar"]

# Alpine's busybox wget: 200 = serving, 503 = still waiting on MySQL. Either way the
# process is alive, which is all a liveness probe needs to assert.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1
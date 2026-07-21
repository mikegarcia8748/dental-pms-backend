# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
# The Amper wrapper (./kotlin) downloads its own Kotlin toolchain and JDK, so the
# base image only has to provide a shell plus the download/extract utilities.
FROM eclipse-temurin:21-jdk-noble AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Amper derives the module name from the directory name, and the module name is part of
# the output task's path — so this must match the project directory, not be a generic name.
WORKDIR /dental-pms

# Wrapper and manifests first: these change rarely, so the toolchain and dependency
# downloads below stay cached across ordinary source edits.
COPY kotlin libs.versions.toml module.yaml ./
# Strip CR in case the build context came from a CRLF checkout — a `#!/bin/sh\r`
# shebang fails on Linux with a misleading "not found". .gitattributes pins LF, but
# this keeps the build working from a zip export or a stray git config too.
RUN sed -i 's/\r$//' kotlin && chmod +x kotlin

COPY resources ./resources
COPY src ./src

# The cache mounts keep the Kotlin toolchain and Maven dependencies out of the image
# layers and make rebuilds fast. They need BuildKit, which is the default in Docker 23+.
RUN --mount=type=cache,target=/root/.kotlin \
    --mount=type=cache,target=/root/.m2 \
    ./kotlin --shared-cache-dir=/root/.kotlin package --format=executable-jar \
 && cp build/tasks/*executableJarJvm/*-executable.jar /app.jar

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --uid 10001 app

WORKDIR /app
COPY --from=build /app.jar /app/app.jar

USER app

# 0.0.0.0 so the server answers on the container's network interface — without it
# published ports reach nothing. Override PORT to move it.
ENV SERVER_HOST=0.0.0.0 \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

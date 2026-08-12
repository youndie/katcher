# syntax=docker/dockerfile:1.7
# Builds the Kotlin/Native client sample for linuxX64 and runs it against a Katcher server.
#
#   docker build --platform=linux/amd64 -f dev/sample-kotlin-native/Dockerfile -t katcher-native-sample .
#   docker run --rm --platform=linux/amd64 \
#     -e KATCHER_APP_KEY=<api key> -e KATCHER_HOST=http://host.docker.internal:8080 \
#     katcher-native-sample catch
#
# The base image is pinned to jammy on purpose. On an arm64 host the emulated amd64 build breaks with
# GNU tar 1.35 (Ubuntu 24.04, the default `eclipse-temurin:21-jdk`): every extraction fails with
# "Cannot mkdir: Function not implemented", so Gradle cannot unpack the Kotlin/Native distribution.
# Jammy ships tar 1.34, which works under emulation.
#
# ---------- Stage 1 : Build (linuxX64 via the host-based target selection) ----------
FROM --platform=linux/amd64 eclipse-temurin:25-jdk-jammy AS build

WORKDIR /src
COPY . .

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    --mount=type=cache,target=/root/.konan,sharing=locked \
    ./gradlew --no-daemon --no-configuration-cache \
      :dev:sample-kotlin-native:linkDebugExecutableNative \
    && mkdir -p /out \
    && cp dev/sample-kotlin-native/build/bin/native/debugExecutable/sample-kotlin-native.kexe /out/sample

# ---------- Stage 2 : Runtime ----------
FROM --platform=linux/amd64 debian:bookworm-slim

# The client keeps pending reports in ./.katcher_cache, i.e. relative to the working directory.
WORKDIR /data

COPY --from=build /out/sample /usr/local/bin/katcher-sample

ENV KATCHER_HOST=http://host.docker.internal:8080
ENV KATCHER_WAIT_SECONDS=8

ENTRYPOINT ["/usr/local/bin/katcher-sample"]
CMD ["catch"]

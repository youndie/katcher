# ---------- Stage 1 : Build ----------
FROM --platform=linux/amd64 gradle:9.7.1-jdk25-noble AS build

WORKDIR /app
COPY . .

RUN gradle :server:linkReleaseExecutableNative --no-daemon --stacktrace

# ---------- Stage 2 : Runtime ----------
# The two stages are a matched pair: libcrypt is copied across, so the builder's glibc must be no
# newer than the runtime's. noble is 2.39 and debian13 is newer, so this holds. The plain
# `gradle:9.7.0-jdk25` tag is Ubuntu 26.04 with glibc 2.43 and fails at exec, not at build.
FROM gcr.io/distroless/cc-debian13
WORKDIR /app

COPY --from=build /app/server/build/bin/native/releaseExecutable/server.kexe /app/server
COPY --from=build /usr/lib/x86_64-linux-gnu/libcrypt.so.1 /usr/lib/x86_64-linux-gnu/

EXPOSE 8080

ENTRYPOINT ["/app/server"]

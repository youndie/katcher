# ---------- Stage 1 : Build ----------
FROM --platform=linux/amd64 gradle:9.2.1-jdk21-jammy AS build

WORKDIR /app
COPY . .

RUN gradle :server:linkReleaseExecutableNative --no-daemon --stacktrace

# ---------- Stage 2 : Runtime ----------
FROM gcr.io/distroless/cc-debian12:debug-nonroot
WORKDIR /app

COPY --from=build /app/server/build/bin/native/releaseExecutable/server.kexe /app/server
COPY --from=build /usr/lib/x86_64-linux-gnu/libcrypt.so.1 /usr/lib/x86_64-linux-gnu/

EXPOSE 8080

ENTRYPOINT ["/app/server"]

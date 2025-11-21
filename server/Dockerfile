# ---------- Stage 1 : Build ----------
FROM --platform=linux/amd64 gradle:9.2.0-jdk21-jammy AS build

WORKDIR /app
COPY . .

RUN gradle :server-native:linkReleaseExecutableNative --no-daemon --stacktrace

# ---------- Stage 2 : Runtime ----------
FROM gcr.io/distroless/cc-debian12
WORKDIR /app

COPY --from=build /app/server-native/build/bin/native/releaseExecutable/server-native.kexe /app/server-native
COPY --from=build /usr/lib/x86_64-linux-gnu/libcrypt.so.1 /usr/lib/x86_64-linux-gnu/

EXPOSE 8080

ENTRYPOINT ["/app/server-native"]

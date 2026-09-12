# ---------- Stage 1 : Build ----------
FROM --platform=linux/amd64 gradle:9.7.1-jdk25-noble AS build

WORKDIR /app
COPY . .

RUN gradle :server:linkReleaseExecutableNative --no-daemon --stacktrace

# ---------- Stage 2 : Runtime ----------
# distroless/cc and not distroless/base, and the reason is not glibc: Kotlin/Native's exception
# handling imports thirteen `_Unwind_*` symbols from libgcc, which `base` does not carry.
#
# NOTHING IS COPIED BESIDE THE BINARY. This file used to drag `libcrypt.so.1` across from the
# builder, because a Kotlin/Native binary DECLARES it — it arrives with `platform.posix`, whose klib
# manifest names it for every Linux program — while importing not one symbol from it. That copy is
# what made the two stages a matched pair: the file is glibc-version-coupled, so the builder's glibc
# had to be no newer than the runtime's, and when it was not the image built and the container died
# with "GLIBC_2.38 not found" before any of its own logging ran.
#
# `sborka.kmp` links Linux executables with `-Wl,--as-needed` since 0.4.0.57, the declaration is
# gone, and so is the pairing rule. If this image ever fails with `cannot open shared object file`,
# the answer is that the convention did not apply — check `readelf -d` on the binary, not another
# COPY line.
FROM gcr.io/distroless/cc-debian13
WORKDIR /app

COPY --from=build /app/server/build/bin/native/releaseExecutable/server.kexe /app/server

EXPOSE 8080

ENTRYPOINT ["/app/server"]

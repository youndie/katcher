# ---------- Stage 1 : Build ----------
FROM --platform=linux/amd64 gradle:9.7.1-jdk25-noble AS build

# The static archives the link needs: libc.a, crt1.o, libstdc++.a, libgcc.a, libgcc_eh.a. The image
# ships glibc 2.39 — the right version — and none of the archives, and the link fails with `unable
# to find library -lc`, which reads like a linker-flag problem and is not one. gcc 13 here is the
# version the `libGcc` override in `server/build.gradle.kts` names; the two move together.
RUN apt-get update \
    && apt-get install -y --no-install-recommends g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

RUN gradle :server:linkReleaseExecutableNative --no-daemon --stacktrace

# ---------- Stage 2 : Runtime ----------
# NO BASE IMAGE. The binary is linked statically (the `staticLinux` block in
# `server/build.gradle.kts`), so distroless/cc is not under it any more: 15 542 820 bytes to pull
# become 9 570 311, measured on the two finished images. The distroless figure was taken before kore
# was added, which costs this image 47 431 bytes — so the comparison flatters the new image by that
# much and by nothing else.
#
# FIVE THINGS ARE STILL COPIED ACROSS, AND NOT ONE OF THEM IS OPTIONAL. A statically linked glibc is
# not a self-contained one: `iconv` loads its converters with `dlopen`, so it needs the *shared*
# glibc, the loader, and the gconv modules — on disk, at the paths glibc compiled in. Ktor reaches
# that code on every URL it encodes (`encodeURLParameter` → `iconv_open`), which means every page of
# this UI. A `scratch` image with the binary alone starts, serves `/favicon.svg`, answers 401
# everywhere else — and returns 500 from the first authenticated page, with
# `Failed to open iconv for charset UTF-8 with error code 22`.
#
# The list is not guesswork: it is what `strace -e trace=openat` recorded the running binary opening.
# `UTF-16.so` alone would carry this application, in an image 2 811 555 bytes smaller, and the whole
# directory is here anyway — glibc picked *that* module to convert UTF-8, which is enough to say that the set of
# modules a request can reach is not something this file should be predicting.
#
# They come from the build stage on purpose: the shared glibc a static binary dlopens has to be the
# same build as the `libc.a` it was linked against. Copied from one image, they are — and the old
# rule about keeping builder and runtime paired by glibc version is gone with the runtime image it
# was about.
#
# `/usr/share/zoneinfo` is the one thing here that nothing currently reads: the trace shows neither
# it nor `/etc/localtime` being opened, and both this image and the distroless one it replaces
# render UTC and ignore `TZ` — verified, not assumed. It stays as 346 KB of insurance for the day a
# call site asks for a named zone, since nothing in the suite would catch its absence.
#
# What `scratch` costs: no shell, no `ls`, nothing to `kubectl exec` into, and no `/tmp`. The
# directory of the SQLite file has to exist — `WORKDIR` makes this one and the chart mounts a volume
# over it. There are no CA certificates either: `server` and `core` open no outbound `HttpClient`
# today, and the day one is added the first TLS handshake is where this is remembered.
FROM scratch
WORKDIR /app

COPY --from=build /etc/ld.so.cache /etc/ld.so.cache
COPY --from=build /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 /lib/x86_64-linux-gnu/ld-linux-x86-64.so.2
COPY --from=build /lib/x86_64-linux-gnu/libc.so.6 /lib/x86_64-linux-gnu/libc.so.6
COPY --from=build /usr/lib/x86_64-linux-gnu/gconv /usr/lib/x86_64-linux-gnu/gconv
COPY --from=build /usr/share/zoneinfo /usr/share/zoneinfo

COPY --from=build /app/server/build/bin/native/releaseExecutable/server.kexe /app/server

EXPOSE 8080

ENTRYPOINT ["/app/server"]

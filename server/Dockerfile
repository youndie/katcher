# ---------- Stage 1 : the link environment, and where `zoneinfo` comes from ----------
# RQ4. Gradle does not run here. It runs in `docker run` against THIS SAME IMAGE, with the runner's
# `~/.gradle` and `~/.konan` mounted in — see `.github/workflows/image.yml`. Inside `docker build`
# those caches cannot persist between runs: a BuildKit cache mount lives in the builder's own state,
# `cache-to: type=gha` exports layers rather than mounts, and the layer below `COPY . .` is
# invalidated by every commit anyway. Outside it, `actions/cache` handles them.
#
# Nothing of glibc is taken from this stage any more (#86): the binary carries all of it, so which
# build of glibc sits here no longer has to match the `libc.a` it was linked against. The stage stays
# because it names the image the workflows link in, and because `zoneinfo` comes from it.
FROM --platform=linux/amd64 gradle:9.8.0-jdk25-noble AS libc

# ---------- Stage 2 : Runtime ----------
# NO BASE IMAGE. The binary is linked statically (the `staticLinux` block in
# `server/build.gradle.kts`), so distroless/cc is not under it any more: 15 542 820 bytes to pull
# became 9 570 311 while the shared glibc and gconv were still copied in, and 5 682 506 without them
# (#86) — pull bytes, measured on the finished images. (The distroless figure was taken before kore
# was added, which costs this image 47 431 bytes.)
#
# THE BINARY AND `zoneinfo`, AND THAT IS ENOUGH — BECAUSE OF TWO THINGS IN THE LINK, NOT ONE (#86).
# Until then this image also carried `libc.so.6`, the loader, `ld.so.cache` and the whole gconv tree:
# a statically linked glibc is not a self-contained one, because `iconv` loads its converters with
# `dlopen`, and Ktor's charsets on Kotlin/Native are glibc `iconv`. On Ktor 3.5 every URL it encoded
# went there, and without those files the first authenticated page was 500 with
# `Failed to open iconv for charset UTF-8 with error code 22` (#55). Ktor 3.6.0 encodes UTF-8 without
# it, so the pages no longer tell — a form that names its charset still does.
#
#  - `iconv-unicode` (youndie/kotlin-native-rt) is linked in with `--wrap` around `iconv_open`,
#    `iconv` and `iconv_close`, and answers UTF-8, UTF-16LE/BE, ISO-8859-1 and US-ASCII itself. Any
#    other charset still goes to glibc and, with no gconv here, fails with error 22 — `UTF-16` with a
#    byte-order mark and `windows-1251` among them. Ktor's own reach for ISO-8859-1 (Basic auth, the
#    websocket accept key) is covered.
#  - the patched Kotlin/Native (`-yrt.<n>`, same repository) makes `-static` mean static, so there is
#    no interpreter to find either.
#
# `dev/image-smoke.sh` is what says the two held: it renders a page behind authentication, and posts
# a US-ASCII form. Drop the library and the same image answers that form with 500 and fails the
# smoke — checked, not assumed, on 27 September; the page alone rendered either way.
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

COPY --from=libc /usr/share/zoneinfo /usr/share/zoneinfo

# From the build context now, not from a stage. `.dockerignore` un-excludes exactly this path.
COPY server/build/bin/native/releaseExecutable/server.kexe /app/server

# THE ALLOCATOR, CAPPED. glibc gives a thread an arena of its own whenever the one it wants is
# locked, up to eight per core — and it counts the *host's* cores, not the container's CPU quota, so
# a `--cpus=1` container on a twenty-core runner may grow sixteen times what it looks like it can.
# An arena returns pages only from its top, so the resident set becomes the sum of every arena's
# high-water mark rather than the live heap.
#
# MEASURED IN TRACY, NOT HERE: same stack, same `scratch` image, an A/B with the container's own
# environment read back — a third less anonymous memory for identical work (tracy M-137). katcher
# runs the same binary shape with a two-connection pool and about fifteen threads, so the ceiling it
# removes is lower; the saving here has not been measured and the number above must not be read as
# this service's.
#
# The price is contention on the malloc lock, which a service that waits on SQLite and the disk does
# not pay. A workload that allocates hard in parallel would have to re-measure before keeping this.
ENV MALLOC_ARENA_MAX=2

EXPOSE 8080

ENTRYPOINT ["/app/server"]

# sample-kotlin-native

Dogfooding app for the Kotlin/Native client. Used to verify end-to-end that a native binary
(built for `linuxX64` inside Docker) actually delivers crash reports to a Katcher server.

The Gradle target follows the same host-based selection as the rest of the repo, so the module builds
`macosArm64` on a developer machine and `linuxX64` inside a `linux/amd64` container.

## Modes

| arg               | what it does                                                                        |
|-------------------|-------------------------------------------------------------------------------------|
| `catch` (default) | `Katcher.catch()` on a handled exception, then waits for the uploader                 |
| `crash`           | throws an uncaught exception so `setUnhandledExceptionHook` fires                     |
| `flush`           | starts Katcher, then hands the queue to `Katcher.flush()` and prints its answer        |

Configuration comes from the environment: `KATCHER_HOST`, `KATCHER_APP_KEY`, `KATCHER_WAIT_SECONDS`,
`KATCHER_CACHE_DIR` (empty = the platform default) and `KATCHER_GRACE_SECONDS` (how long a fatal crash
waits for its report to leave, and the budget `flush` gets).

The client itself ships **no Ktor engine** — this sample adds `ktor-client-cio`, which supports native
targets and needs no system libraries (unlike `ktor-client-curl`, which needs libcurl at build and run
time).

## 1. Run a server locally

```sh
./gradlew :server:linkDebugExecutableNative
mkdir -p /tmp/katcher-data
DB_PATH=/tmp/katcher-data/local.db SOURCE_MAPS_PATH=/tmp/katcher-data/mappings \
  server/build/bin/native/debugExecutable/server.kexe
```

Register an app so the API key is accepted (`POST /api/reports` answers `401` for an unknown key).
The key lives in `app_keys`, not in `apps`:

```sh
sqlite3 /tmp/katcher-data/local.db \
  "INSERT INTO apps(name, type) VALUES('native-linux-sample','OTHER');"
sqlite3 /tmp/katcher-data/local.db \
  "INSERT INTO app_keys(app_id, api_key, created_at)
   VALUES((SELECT id FROM apps WHERE name='native-linux-sample'),'<api key>',$(date +%s000));"
```

## 2. Run the sample on the host (fast feedback)

```sh
./gradlew :dev:sample-kotlin-native:linkDebugExecutableNative
cd /tmp && KATCHER_HOST=http://localhost:8080 KATCHER_APP_KEY=<api key> \
  <repo>/dev/sample-kotlin-native/build/bin/native/debugExecutable/sample-kotlin-native.kexe catch
```

## 3. Run the linuxX64 build in Docker

```sh
docker build --platform=linux/amd64 -f dev/sample-kotlin-native/Dockerfile -t katcher-native-sample .

docker run --rm --platform=linux/amd64 -v katcher-cache:/data \
  -e KATCHER_APP_KEY=<api key> -e KATCHER_HOST=http://host.docker.internal:8080 \
  -e KATCHER_CACHE_DIR=/data \
  katcher-native-sample catch
```

`host.docker.internal` reaches a server running on the macOS/Windows host; on Linux add
`--add-host=host.docker.internal:host-gateway`.

The `-v katcher-cache:/data` volume matters: without one, anything not uploaded before the process dies
is lost with the container. Point the client at it with `-e KATCHER_CACHE_DIR=/data` — otherwise reports
go to `./.katcher_cache`, relative to the working directory, which is not the volume.

## Verifying delivery

```sh
sqlite3 /tmp/katcher-data/local.db "SELECT id, message, environment, context FROM reports;"
```

A report sent by the containerised build carries `"device.os":"LINUX","device.arch":"X64"` in its
context, which is what distinguishes it from a report sent by the host build.

## Emulation caveats on an arm64 host (Apple Silicon)

The `linux/amd64` image runs under Rosetta, and two things break there:

* **GNU tar 1.35** (Ubuntu 24.04, i.e. the default `eclipse-temurin:21-jdk`) cannot extract anything:
  every entry fails with `Cannot mkdir: Function not implemented`, so Gradle dies on
  `Cannot extract archive ... kotlin-native-prebuilt-2.4.10-linux-x86_64.tar.gz. Tar exit code: 2`.
  Hence the jammy base image (tar 1.34). Plain `mkdir`, busybox tar and tar 1.34 are unaffected —
  it is not a cache-mount, volume or seccomp problem.
* **An uncaught Kotlin exception does not terminate the process.** After the runtime prints
  `Uncaught Kotlin exception: ...` the main thread spins at 100% CPU forever instead of aborting.
  This is emulation, not Katcher: it reproduces with the hook uninstalled (`KATCHER_APP_KEY=`),
  while the same binary aborts normally as a `macosArm64` build. The crash report is still delivered
  before the spin starts.

## What the grace changes (measured here)

`Katcher.catch()` only *signals* the uploader; the POST happens on a `Dispatchers.IO` coroutine, and on
an **uncaught** exception the runtime terminates the process right after the hook returns. Whether the
report makes it out on that run is exactly what `KATCHER_GRACE_SECONDS` decides — same binary, same
server, two runs from an empty cache directory:

```sh
BIN=dev/sample-kotlin-native/build/bin/native/debugExecutable/sample-kotlin-native.kexe
for grace in 0 5; do
  rm -rf /tmp/cache-$grace && mkdir -p /tmp/cache-$grace
  KATCHER_HOST=http://127.0.0.1:8080 KATCHER_APP_KEY=<api key> \
    KATCHER_CACHE_DIR=/tmp/cache-$grace KATCHER_GRACE_SECONDS=$grace "$BIN" crash
  echo "grace=$grace left on disk: $(ls /tmp/cache-$grace)"
done
```

* `grace=0` — the process aborts, the report stays in the cache directory, the server has nothing.
  This is the default, and it is right for a client that will start again.
* `grace=5` — the same crash, the report is in the server's `reports` table before the process aborts,
  and the cache directory is empty.

`flush` mode then delivers whatever the first run left behind — the same thing a host does in its
shutdown group when it still has a live process.

The uploader stops draining the queue at the first failing report and waits for the next signal.

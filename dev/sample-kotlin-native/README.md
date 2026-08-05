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
| `flush`           | starts Katcher only, so pending reports on disk get drained                           |

Configuration comes from the environment: `KATCHER_HOST`, `KATCHER_APP_KEY`, `KATCHER_WAIT_SECONDS`.

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

Register an app so the API key is accepted (`POST /api/reports` answers `401` for an unknown key):

```sh
sqlite3 /tmp/katcher-data/local.db \
  "INSERT INTO apps(name, api_key, type) VALUES('native-linux-sample','<api key>','OTHER');"
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
  katcher-native-sample catch
```

`host.docker.internal` reaches a server running on the macOS/Windows host; on Linux add
`--add-host=host.docker.internal:host-gateway`.

The `-v katcher-cache:/data` volume matters: the client stores pending reports in `./.katcher_cache`,
relative to the working directory. Without a volume, anything not uploaded before the process dies is
lost with the container.

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

## Known behaviour (not a bug in the sample)

* `Katcher.catch()` only *signals* the uploader; the POST happens on a `Dispatchers.IO` coroutine.
  On an **uncaught** exception the runtime terminates the process right after the hook returns, so the
  report is written to disk but not sent on that run. It is delivered by the next start
  (`flush` mode demonstrates this) — and only if `.katcher_cache` survived, hence the volume.
* The uploader stops draining the queue at the first failing report and waits for the next signal.

# Katcher

Lightweight, privacy-friendly crash-reporting service written in Kotlin Multiplatform. Server runs as a
self-contained Kotlin/Native binary (Ktor native engine + SQLite via sqlx4k, HTMX/Tailwind UI, no JVM
required in production). Client is a KMP library apps embed to capture and upload crash reports.

## Module layout

- `core/` — shared KMP UI components (HTMX-style server-rendered) and domain interfaces (`AppRepository`,
  `UserRepository`, `ReportRepository`) used by `server/`.
- `server/` — Ktor application. `commonMain` has routing/app wiring; `jvmMain`/`nativeMain` hold
  platform-specific `ServerConfig`. Native is the real deployment target; JVM exists for dev/testing.
  Feature packages under `io.github.youndie.katcher.feature.<name>/data` (auth, app, user, report, error,
  symbolication) each hold their own Exposed/sqlx4k data access.
- `shared/` — DTOs shared between client and server (`CreateReportParams`, `Breadcrumb`,
  `ReportResource`, `ErrorGroupSort`).
- `client/` — the crash-reporting library consumers embed (`io.github.youndie.katcher:client`).
  `commonMain/Katcher.kt` is the public API (`Katcher.start {}`, `Katcher.catch()`, `Katcher.addBreadcrumb()`).
  Platform-specific `expect/actual`: `setupPlatformHandler()`, `fileSystem` (`KatcherFileSystem`).
  `client/` and `shared/` list their targets explicitly (JVM, both Linux, both macOS, three iOS, mingw)
  — unlike `server/` and `core/`, which still pick one native target from `os.name`. Do not "simplify"
  them back: a host-picked target means the published version carries whichever variant the build
  machine happened to support, and iOS consumers get nothing. Apple klibs cross-compile on Linux
  (`kotlin.native.enableKlibsCrossCompilation` in `gradle.properties`), so CI publishes them from
  `ubuntu-latest`; only running Apple tests needs a Mac.
  The android target lives beside them: `jvmSharedMain` holds what JVM and Android share (the
  `Thread.setDefaultUncaughtExceptionHandler` install, `FileKatcherFileSystem`), and each of the two
  supplies only its cache directory and its system attributes. Its publication is
  `io.github.youndie.katcher:client-android` — the name `dev/client-android` used until 0.4.92 (as
  `io.github.youndie.katcher:client-android`, the group this repository published before 0.7), which
  is why that module no longer publishes anything (#27).
- `dev/` — sample/dogfooding apps (`sample-kotlin-jvm`, `client-android`, `android-gradle-plugin`,
  `server-jvm-keycloak`, `retrace`) — not shipped, used for manual testing.
- `charts/katcher/` — Helm chart for deploying the server.

## Client crash-capture model (important, non-obvious)

`Katcher.catch()` (`client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt`) is **not fully
synchronous**: it synchronously writes the report to disk via `saveReport()`, then only *signals* an
upload — the actual HTTP POST happens later on a `Dispatchers.IO`-backed `CoroutineScope`
(`ReportUploader.work()`). On JVM, `Dispatchers.IO` threads are daemons, so if the crash happens on the
last non-daemon thread (e.g. a startup-time crash before any server threads exist), the JVM can exit
before the upload ever fires; on Kotlin/Native the hook terminates the process right after it returns,
which is the same story without the "can". The report is then delivered by a *later* start, and only if
the on-disk cache directory survived the restart.

A host that has no later start says so, and three things answer it (#50):

- `KatcherConfig.cacheDir` — the directory reports wait in, `null` meaning the platform default.
  `createFileSystem(cacheDir)` is the `expect` that builds the storage; the old `expect val fileSystem`
  was a fixed singleton, which is why the directory could not be repointed before.
- `KatcherConfig.crashUploadGrace` — how long `Katcher.catchFatal()` blocks the dying thread waiting for
  the upload (`ReportUploader.flushBlocking`). `Duration.ZERO` by default: mobile wants the next launch,
  not a held thread. **Only the fatal path waits** — public `catch()` never blocks.
- `Katcher.flush(grace)` — suspending, for the host's own shutdown group; answers whether the queue is
  now empty.

The `runBlocking` in `ReportUploader.flushBlocking` sits in `commonMain` because the client declares
neither a JS nor a Wasm target — `:shared` does declare `wasmJs`, so that is where the pressure will
come from. Adding such a target to `:client` means moving `flushBlocking` behind an `expect`.

Before #50 the JVM handler slept `50 ms` and hoped. Against `connectTimeoutMillis = 3_000` and two
retries that was a lottery, and it is gone.

Platform defaults for the cache directory are **not** persistent paths. Consumers deploying on
Kubernetes/containers either mount a volume at the default (`user.dir` for a Jib-built image is
typically `/app`) or set `cacheDir` to one — otherwise reports from startup-time crashes are lost on
container restart before ever being retried.

On Android the directory comes from `Context.cacheDir` — `user.dir` there is `/`, which is not writable,
and an application cannot repoint it either (`System.setProperty("user.dir", …)` is refused by the runtime
with "Ignoring attempt to set property"). The `Context` arrives without the consumer doing anything:
`KatcherInitProvider` (`client/src/androidMain/.../KatcherContext.kt`) is a `ContentProvider` declared in
the library's own `AndroidManifest.xml`, so it runs before `Application.onCreate`. `Katcher.installContext()`
is the escape hatch when manifest merging dropped it.

`KatcherFileSystem.prepare()` is called from `Katcher.start { }` and throws when the directory cannot be
created or written to; `start` then prints and returns **without setting the config**, so `catch()` is a
no-op afterwards. Before that, an unwritable directory only showed up as a caught exception inside
`Katcher.catch` at crash time — which prints a line and never signals the upload, i.e. a reporter that
says "Storage ready" and reports nothing (#27).

On Kotlin/Native the default cache directory is chosen at runtime from `Platform.osFamily`
(`client/src/nativeMain/.../NativeKatcherFileSystem.kt`, `defaultCacheDir()`): `$HOME/Library/Caches/katcher_cache`
on Apple targets, `.katcher_cache` next to the working directory everywhere else. The working directory of an
iOS app is the read-only bundle, so the relative path would turn every `saveReport()` inside the crash handler
into an exception that only `println`s.

`setupJvmUncaughtExceptionHandler()` installs via `Thread.setDefaultUncaughtExceptionHandler` — this only
fires for exceptions that genuinely propagate uncaught on a thread. If a host framework (e.g. Ktor's
engine bootstrap) catches a startup exception internally and calls `exitProcess()` directly rather than
letting it propagate, Katcher's handler never sees it at all.

## IDE MCP — it is connected, use it before `Edit`/`Grep`

The `mcp__idea__*` tools are deferred, so they are invisible until loaded. Load them once at the start
of a session:

```
ToolSearch("select:mcp__idea__apply_patch,mcp__idea__get_file_problems,mcp__idea__search_symbol,mcp__idea__get_symbol_info,mcp__idea__lint_files,mcp__idea__read_file")
```

Pass `projectPath` = this project's root (the cwd) in **every** call; all other paths are relative to it.

- Edit with `apply_patch`, not `Edit`/`sed`: a patch is atomic per file and goes straight through the
  IDE. Rename with `rename_refactoring`, never text replace.
- Check with `get_file_problems(file, errorsOnly = true)` — under a second, versus tens of seconds for
  `./gradlew`. Keep the build for the final check.
- `search_symbol(include_external = true)` + `read_file` reads dependency sources out of
  `…-sources.jar` — check library behaviour there, not from memory.
- Files written via `Write`/`Bash` take ~10 s to reach the IDE's VFS, so `get_file_problems` right
  after one can answer on stale content. `apply_patch` has no such lag.
- After a dependency change the IDE model is stale until a Gradle sync, and **nothing here can trigger
  one** — there is no sync tool, and an external build-script edit does not start one either. Validate
  those changes with `./gradlew`, not with inspections.

Everything else — call hierarchies, single-test runs, the debugger, `inspection.kts` — is in the
`mcp-idea` skill.

## Build/test

- `./gradlew :server:build` / `:client:build` — standard Gradle multiplatform build.
- Native server tests live in `server/src/nativeTest/kotlin/.../data/*Test.kt` (repository-level tests
  against SQLite).
- On a Mac `:client:build` also runs the client's native suite on the iOS simulator, which needs an
  installed simulator runtime — with Xcode present but no runtime the task fails with "Xcode does not
  support simulator tests for ios_simulator_arm64". Install a runtime, or run
  `./gradlew :client:build -x iosSimulatorArm64Test`; `:client:macosArm64Test` covers the same suite
  on the Apple side. CI runs on Linux and never schedules it.
- Version catalogs: root `libs.versions.toml` (Kotlin/Compose/Android), plus `ktorLibs` and
  `kotlinCrypto` pulled in via Gradle's version-catalog-from-module feature, and `jvmLibs` from
  `gradle/jvmLibs.versions.toml` for JVM-only tooling (Jib, Kotlin JVM plugin).
- Project version is `0.1.<BUILD_NUMBER>` unless `-PVERSION=...` is passed (see root `build.gradle.kts`
  `libVersion()`).

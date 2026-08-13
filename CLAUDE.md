# Katcher

Lightweight, privacy-friendly crash-reporting service written in Kotlin Multiplatform. Server runs as a
self-contained Kotlin/Native binary (Ktor native engine + SQLite via sqlx4k, HTMX/Tailwind UI, no JVM
required in production). Client is a KMP library apps embed to capture and upload crash reports.

## Module layout

- `core/` — shared KMP UI components (HTMX-style server-rendered) and domain interfaces (`AppRepository`,
  `UserRepository`, `ReportRepository`) used by `server/`.
- `server/` — Ktor application. `commonMain` has routing/app wiring; `jvmMain`/`nativeMain` hold
  platform-specific `ServerConfig`. Native is the real deployment target; JVM exists for dev/testing.
  Feature packages under `ru.workinprogress.feature.<name>/data` (auth, app, user, report, error,
  symbolication) each hold their own Exposed/sqlx4k data access.
- `shared/` — DTOs shared between client and server (`CreateReportParams`, `Breadcrumb`,
  `ReportResource`, `ErrorGroupSort`).
- `client/` — the crash-reporting library consumers embed (`ru.workinprogress.katcher:client`).
  `commonMain/Katcher.kt` is the public API (`Katcher.start {}`, `Katcher.catch()`, `Katcher.addBreadcrumb()`).
  Platform-specific `expect/actual`: `setupPlatformHandler()`, `fileSystem` (`KatcherFileSystem`).
- `dev/` — sample/dogfooding apps (`sample-kotlin-jvm`, `client-android`, `android-gradle-plugin`,
  `server-jvm-keycloak`, `retrace`) — not shipped, used for manual testing.
- `charts/katcher/` — Helm chart for deploying the server.

## Client crash-capture model (important, non-obvious)

`Katcher.catch()` (`client/src/commonMain/kotlin/ru/workinprogress/katcher/Katcher.kt`) is **not fully
synchronous**: it synchronously writes the report to disk via `fileSystem.saveReport()`, then only
*signals* an upload — the actual HTTP POST happens later on a `Dispatchers.IO`-backed `CoroutineScope`
(`processQueue()`). On JVM, `Dispatchers.IO` threads are daemons, so if the crash happens on the last
non-daemon thread (e.g. a startup-time crash before any server threads exist), the JVM can exit before
the upload ever fires — the crash report never reaches the server on that boot. It will only get
delivered on a *later* boot's `processQueue()` pass, and only if the on-disk cache directory survived
the process/container restart.

`JvmKatcherFileSystem` (`client/src/jvmMain/kotlin/ru/workinprogress/katcher/JvmKatcherFileSystem.kt`)
stores pending reports at `System.getProperty("user.dir")/.katcher_cache` — this is **not** guaranteed
to be a persistent path. Consumers deploying on Kubernetes/containers must mount a persistent volume at
that path (`user.dir` for a Jib-built image is typically `/app`) or reports from startup-time crashes
are lost on container restart before ever being retried.

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
- Version catalogs: root `libs.versions.toml` (Kotlin/Compose/Android), plus `ktorLibs` and
  `kotlinCrypto` pulled in via Gradle's version-catalog-from-module feature, and `jvmLibs` from
  `gradle/jvmLibs.versions.toml` for JVM-only tooling (Jib, Kotlin JVM plugin).
- Project version is `0.1.<BUILD_NUMBER>` unless `-PVERSION=...` is passed (see root `build.gradle.kts`
  `libVersion()`).

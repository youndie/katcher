---
id: B-03
title: "A CI check sees a crash from a linked Kotlin/Native consumer arrive on the server, and the production silence is explained"
status: question
priority: P1
size: M
stage: stage-1-delivery
---

# B-03 — A native consumer's crash arrives, by a check rather than by hope

**Native production clients have reported nothing since 29.09** — in the stand's group listings
(read 2026-11-09) no group of theirs was seen after that date, and the date coincides with those
services moving to the Kotlin/Native client. Quiet or broken is not known, and nothing in this repository could tell:

- the server's half is exercised — `dev/image-smoke.sh:53-55` posts a report with `curl` to the image
  the `Image` workflow just built (`.github/workflows/image.yml:165-166`);
- the client's half is not. `ReportUploaderTest` drives an in-memory store and a fake `send`
  (`client/src/commonTest/kotlin/io/github/youndie/katcher/ReportUploaderTest.kt:117`); no test sends
  a report over HTTP from a linked binary;
- `dev/sample-kotlin-native` is exactly the consumer that would — modes `catch`, `crash`, `flush`
  (`dev/sample-kotlin-native/src/nativeMain/kotlin/Main.kt`) — and no workflow runs it. It also depends
  on `projects.client`, not on the published artifact, and declares `ktorLibs.client.cio` itself
  (`dev/sample-kotlin-native/build.gradle.kts:39`).

Three ways a native consumer can report nothing while looking configured, each read in the code and
none checked against production — **hypotheses**, in the order they are cheapest to rule out:

1. **No HTTP engine.** The client declares only `ktor-client-core` (`client/build.gradle.kts:65`) and
   builds `HttpClient { … }` without an engine (`client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt:58-79`),
   so the engine is whatever the consumer happens to link. A server that links none fails inside
   `sendReport`, where the exception is printed only under `isDebug` and answered `false`
   (`Katcher.kt:234-237`): the report stays on disk and is retried for ever, silently. The sample
   would never show this — it brings its own engine.
2. **An unwritable working directory.** Off Apple, the default cache is `.katcher_cache` relative to
   the working directory (`client/src/nativeMain/kotlin/io/github/youndie/katcher/NativeKatcherFileSystem.kt:37-38`).
   If the image cannot write there, `start` prints one `Storage error` line and returns without a
   config (`Katcher.kt:91-95`), and every `catch()` after it is a no-op.
3. **A fatal crash in a container.** `crashUploadGrace` defaults to zero
   (`client/src/commonMain/kotlin/io/github/youndie/katcher/KatcherConfig.kt:31`), the native hook
   terminates right after `catchFatal` (`client/src/nativeMain/kotlin/io/github/youndie/katcher/Katcher.native.kt:24-30`),
   and the report waits for a next start on a filesystem the restarted container does not have.
   This one explains missing fatal crashes only; handled ones would still arrive.

- **The decision and its reason.** Run the existing native sample in the `Image` workflow, after
  `image-smoke.sh`, against the same `katcher:ci` container, built against the client **as it
  publishes** (`publishToMavenLocal` of the same commit, the sample resolving the coordinate) — the
  project dependency would hide a broken publication, which is the half a consumer gets. The check
  requires a group from `catch` and one from `crash` (with a grace and a mounted cache, as the
  sample's README describes), read back from the rendered group page the way `image-smoke.sh` does.
  The workflow's path filter (`.github/workflows/image.yml:17-30`) leaves `client/**` out on the
  grounds that a client change cannot affect the image; with this step it can affect the job, so
  `client/**` and `dev/sample-kotlin-native/**` join both lists.
- **A negative control, because the check must be able to fail.** The same run with the sample's
  `ktor-client-cio` line removed must either still deliver or fail loudly at `start`. If it fails
  silently, hypothesis 1 is confirmed in CI, and the fix — the client declaring an engine per native
  target, or `start` refusing without one — is part of this item.
- **The production answer is part of the item.** One deliberate crash from a native production
  consumer, or that consumer's log at startup (`Storage error`, `Transmission failed` with
  `isDebug`), says which of the three it is. Written here without naming the service.
- Not covered: Apple targets (`iosSimulatorArm64Test` needs a Mac runner), and Android, which has its
  own sample.

- AC: the `Image` workflow fails when the native sample's `catch` or `crash` report does not appear
  on the server, and passes on `main`.
- AC: the no-engine run's outcome is recorded here, and if it was silent, a client change makes it
  either deliver or refuse at `start` with a line that says why — held by that same CI run.
- AC: this item names which hypothesis explains the native production silence since 29.09 (or
  "quiet": a deliberate crash arrived), and the consumer-side fix, if any, is a line in the client's
  README section on Kotlin/Native.
- Anchors: `dev/sample-kotlin-native/build.gradle.kts`, `dev/sample-kotlin-native/src/nativeMain/kotlin/Main.kt`,
  `dev/image-smoke.sh`, `.github/workflows/image.yml`, `client/build.gradle.kts`,
  `client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt`,
  `client/src/nativeMain/kotlin/io/github/youndie/katcher/NativeKatcherFileSystem.kt`.

## Findings

2026-11-09.

**The CI half is done.** The `Image` workflow's step "A native consumer's crashes arrive" publishes
`:shared` and `:client` (the `kotlinMultiplatform` and `linuxX64` publications) to Maven Local in the
builder container, links `dev/sample-kotlin-native` against the coordinate
(`-Pkatcher.sample.client=0.0.0-ci`) twice — with `ktor-client-cio`, and with no engine
(`-Pkatcher.sample.engine=none`) — and runs `dev/native-client-smoke.sh` against the `katcher:ci`
container after `image-smoke.sh`. The script creates an app of its own, runs `catch` and `crash` (each
with its own cache directory, `crash` with a 10 s grace), and requires both groups to render; then
requires the engine-less binary to print `No Ktor HTTP client engine is linked` and store nothing.
`client/**`, `dev/sample-kotlin-native/**` and the script joined both path lists. Rehearsed on the Linux
build box against the native server: groups 2 and 3 arrived, the engine-less binary refused.

**The no-engine run was not silent — it was worse.** With the client of `main` (`0.0.0-prefix` in the
rehearsal) and no engine, on Kotlin/Native:

- `start` succeeded and printed `Storage ready`; `catch` saved the report;
- the first upload built the lazy `HttpClient`, and Ktor's engine lookup failed inside a file
  initializer, so what reached `sendReport` was `FileFailedToInitializeException` — an `Error`, not an
  `Exception`. `catch (e: Exception)` did not take it, the uploader coroutine died uncaught, and the
  process **aborted** (`SIGABRT`, core dumped), leaving two reports on disk: the handled one and the
  abort itself.

So hypothesis 1, on native, is not a reporter that retries in silence: it is a server that dies at its
first reported error. The fix: `start` builds the `HttpClient` at once, under `runCatching` (which takes
an `Error` too), and on failure prints the root cause and returns without a config — the same contract
as the storage check. `StartWithoutEngineTest` holds it on every client target (the client's own test
classpath links no engine), and the CI run holds it on a linked binary built from the publication.

**The production answer is not settled; it is the question below.** One native production consumer
was read in its repository (build, image, chart), not in production:

- hypothesis 1 is out for it: it links `ktor-client-curl` (CIO on native has no TLS, its build says);
- hypothesis 2 is out for it: the image is `ubuntu:24.04` with no `USER` and no `WORKDIR`, the
  deployment sets no `securityContext`, so the default `.katcher_cache` lands in `/` of a writable root
  owned by the process's own user;
- hypothesis 3 holds for it — `crashUploadGrace` and `cacheDir` are unset — but explains fatal crashes
  only.

What is left is either "quiet" (no handled error reached `Katcher.catch` since 29.09) or a delivery that
fails where the code cannot be read: the consumer runs with `isDebug = false`, and that hides every
`Transmission failed` and `Server rejected` line — a TLS failure, or a `401` for a key that is no longer
alive, would retry on disk in silence. Nothing on the server separates the two: a refused post leaves no
row, and the MCP listing names apps without their last report.

Also found: a stale native compiler cache. On a machine that had linked the sample against the project
`:client` before, linking it against the published coordinate reused
`build/kotlin-native-ic-cache/.../io.github.youndie.katcher:client-per-file-cache` — the cache is keyed
by the library's unique name, which the project and the publication share — and the binary carried the
old client. A fresh runner does not have that directory; on a developer machine, delete it before
comparing the two.

## Question

Which hypothesis explains the native production silence needs one observation from production, and
production is the owner's:

1. **A deliberate handled crash** from the native production consumer (any route or command that
   reaches `Katcher.catch`), then look for its group on the stand. Arrives — the answer is "quiet", and
   the item closes. Does not arrive — then:
2. **The same consumer with `isDebug = true`** for one deploy: its log then names the failure
   (`Transmission failed: …` or `Server rejected: 401`), which is the consumer-side fix to write into the
   README's Kotlin/Native section.

Separately, whatever the answer, a native server consumer wants `cacheDir` on a volume and a
`crashUploadGrace` for its fatal crashes (README, "Shutting down"). Decides: the owner.

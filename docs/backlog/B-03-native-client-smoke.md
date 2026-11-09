---
id: B-03
title: "A CI check sees a crash from a linked Kotlin/Native consumer arrive on the server, and the production silence is explained"
status: wip
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

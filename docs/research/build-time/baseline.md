# Baseline

Two kinds of statement live here and they are kept apart on purpose: what was **read** from
the repository, and what was **measured**. Read facts are cheap and certain; measured numbers
carry an n and a caveat. Nothing below is an experiment — RQ0 is still open.

Taken at `809a27f` (`build: release the malloc arena cap as 0.8.2`, #63), version 0.8.2.

---

## RQ0's questions of fact

### Is the image built with Gradle inside `docker build`? Yes.

`server/Dockerfile` stage 1 is `gradle:9.7.1-jdk25-noble`; it installs `g++`, does `COPY . .`
and runs:

```
RUN gradle :server:linkReleaseExecutableNative --no-daemon --stacktrace
```

**RQ4 is live, not void.** And that build has no cache of any kind:

- `image.yml` runs a plain `docker build` — no `docker/setup-buildx-action`, no `cache-from`
  or `cache-to`, so nothing survives between runs;
- the Dockerfile has no BuildKit cache mounts, so `~/.gradle` and `~/.konan` start empty inside
  the build stage.

Every Image run therefore re-downloads the ~1 GB Kotlin/Native toolchain and every dependency,
and links from cold. `.dockerignore` exists and excludes `**/build/`, `.gradle/`, `.kotlin/`,
`local.properties`; `.git` is deliberately kept, because the `kore` build plugin reads it.

### Which tasks does a PR run? Two workflows, and 863 tasks in one of them.

| Workflow | On a PR | Runs |
|---|---|---|
| `ci.yml` | always | `./gradlew build` (job `build`) and a `helm template` job (job `chart`) |
| `image.yml` | path-filtered to `server/**`, `core/**`, `shared/**`, `dev/retrace/**`, `dev/image-smoke.sh`, `gradle/**`, the root build files, `.dockerignore`, itself | `docker build`, a smoke test that renders a timestamp, a shutdown-transcript check, an assertion that `MALLOC_ARENA_MAX=2` survived |

`./gradlew build` executed **863 distinct tasks**. By first path segment:

| | tasks |
|---|---|
| `:dev` | 340 |
| `:shared` | 205 |
| `:client` | 202 |
| `:server` | 54 |
| `:core` | 49 |
| wasm tooling and root | 13 |

Against that, the task graph that actually produces the shipped binary is **69 tasks**
(`:shared` 35, `:dev:retrace` 13, `:core` 12, `:server` 9 — and `:client` **zero**), from
`./gradlew :server:linkReleaseExecutableNative --dry-run` on the box
([raw/dry-run-server-link.txt](raw/dry-run-server-link.txt)).

So **8% of the PR's task graph produces the artifact that ships.** That is the size of RQ3's
question — not its answer: the brief requires that the other 92% keep being checked somewhere,
so the question is where they run, not whether they run.

### Which client targets are compiled on a PR? All of them.

`:client` — `jvm`, `android`, `linuxX64`, `linuxArm64`, `macosX64`, `macosArm64`, `iosArm64`,
`iosSimulatorArm64`, `iosX64`, `mingwX64`.
`:shared` — the same minus `android`, plus `wasmJs { browser() }`.

Apple targets cross-compile to klibs on the Linux runner
(`kotlin.native.enableKlibsCrossCompilation=true`). Tests run for the host target only.

### Is `~/.konan` cached in CI? Yes — twice, in the same job.

1. `youndie/sborka/.github/actions/setup-kotlin@main` with `konan-cache: "true"` caches it,
   keyed on `hashFiles('gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties')`.
2. [`ci.yml`](../../../.github/workflows/ci.yml) then adds a second `actions/cache@v6` on the
   same path with a different key, `hashFiles('gradle/*.versions.toml')` — a glob that also
   matches `gradle/jvmLibs.versions.toml`.

In the decomposed run the second restore cost **13 s** on a directory the first had restored
13 s earlier. Its post-step cost 0 s there because the key hit exactly; on a catalog bump both
entries would save, and the restored archive is 1 097 643 302 bytes.

The other three workflows (`build-katcher-native`, `publish-client`, `publish-katcher-native`)
use the sborka action alone and have no duplicate.

---

## Measured: workflow wall-clock

Successful runs between 2026-08-24 and 2026-09-14, from
[raw/gh-run-list-2026-09-14.json](raw/gh-run-list-2026-09-14.json) via
[raw/summarise-runs.py](raw/summarise-runs.py):

| workflow | trigger | n | median | min | max | CV |
|---|---|---|---|---|---|---|
| CI | pull_request | 46 | **5.1 m** | 1.3 m | 8.2 m | 43.9% |
| CI | push | 30 | 4.8 m | 1.9 m | 9.8 m | 44.8% |
| Image | pull_request | 7 | **6.7 m** | 5.7 m | 8.1 m | 9.7% |
| Image | push | 7 | 6.5 m | 6.0 m | 6.8 m | 3.8% |
| Publish server native | dispatch | 6 | **6.2 m** | 6.0 m | 7.0 m | 6.8% |
| Publish client | push | 11 | 3.5 m | 2.5 m | 6.0 m | 24.6% |

**What this is not.** These are whole-workflow durations including queueing, over
heterogeneous pull requests. They are not the brief's `T_pr`, which is a median over three
pushes of one one-line change. The 44% on CI is the spread caused by what the PRs *contained*
— a client-only PR skips Image entirely and can finish in 1.3 m — not a measure of runner
noise, and it must not be quoted as one. It does say that three pushes of one change is the
minimum honest protocol for `T_pr`, not a precaution.

## Measured: one CI `build` job, decomposed

Run `34896166611`, push to main, warm caches, **n = 1**
([raw/ci-run-34896166611.log](raw/ci-run-34896166611.log) via
[raw/decompose-job.py](raw/decompose-job.py)):

| step | starts at | span |
|---|---|---|
| Set up job | 0 s | 4 s |
| `actions/checkout@v7` | 4 s | 1 s |
| `sborka/setup-kotlin` (incl. the ~1 GB `~/.konan` restore) | 5 s | 26 s |
| `actions/cache@v6` — the duplicate `~/.konan` | 31 s | **13 s** |
| `./gradlew build --stacktrace` | 45 s | **280 s** |
| post-steps | 325 s | 12 s |
| **total** | | **337 s** |

First Kotlin task (`:shared:compileKotlinJvm FROM-CACHE`) at **+78 s**.

Two things follow, both to be confirmed at n = 3 before they are treated as results:

- **`T_first_compile` ≈ 78 s against RQ5's green of ≤ 90 s.** If that holds, RQ5 is green at
  baseline — which is a finding ("defaults are fine, here is the number"), not a lever.
- **`./gradlew build` is 83% of the job.** Setup and caching are not where CI time is.

---

## Open questions this baseline raises

### `T_pr` is not yet a single number

Kill criterion 1 stops the project if `T_incr_dbg ≤ 20 s` **and** `T_pr ≤ 5 min`. On a PR that
touches the server, CI (5.1 m) and Image (6.7 m) run in parallel and the merge gate is the
slower of the two; on a client-only PR, Image does not run at all. `T_pr` has to name which,
because 6.7 m and 5.1 m fall on opposite sides of the threshold that ends the project.

### The release binary is linked twice per server PR

Once inside `./gradlew build` with warm caches (`assemble` reaches
`:server:linkReleaseExecutableNative`), and once inside `docker build` from completely cold.
The two run in parallel in different workflows, so the cost is CPU and the Image job's 6.7 m,
not 6.7 m added to CI.

### RQ1 and RQ2 have untouched ground; RQ6 does not

`gradle.properties` sets neither `kotlin.native.cacheKind` nor `kotlin.incremental.native`, and
sets `kotlin.daemon.jvmargs=-Xmx4G` but not `kotlin.native.jvmArgs` — so the K/N compiler JVM
runs at its default while the Gradle daemon and the Kotlin daemon are sized. RQ6 starts from
the other end: `org.gradle.caching`, `org.gradle.parallel` and `org.gradle.configuration-cache`
are already on, so its A/B is a disabling, not an enabling.

## Not yet measured

Every local metric: `T_cold`, `T_warm`, `T_incr_dbg`, `T_incr_rel`. The mutagen session that
makes them possible was only confirmed working on 2026-09-14; no clean-checkout run has been
taken on the box.

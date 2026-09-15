# RQ0 — where does the time go?

**Verdict: green.** Every metric is attributed. RQ1–RQ7 may proceed.

It was not green when first written. The instrumentation that closed it is in #65; what each
section says about the *evidence* is kept below, because the reason a number could not be got is
part of the method, and the first answers were wrong in ways worth keeping.

| metric | attributed | verdict |
|---|---|---|
| `T_pr` (CI `build` job) | 100% of the Gradle invocation | **green** (was 26%, red) |
| `T_release` (Image job) | 100% of the Gradle step, docker steps bounded separately | **green** (was ~83%, amber) |
| `T_incr_rel` | 97%, by compiler phase | **green** |
| `T_incr_dbg`, `T_warm` | measured; phase-profiled | **green** |

---

## The answer, once the instrument existed

Both from `workflow_dispatch` runs with `profile: true` on the instrumentation branch —
[raw/profile-ci-build-34903415756.html](raw/profile-ci-build-34903415756.html) and
[raw/profile-image-34903429789.html](raw/profile-image-34903429789.html), read with
[raw/parse-profile.py](raw/parse-profile.py).

| | CI `build` | Image, Gradle step |
|---|---|---|
| wall-clock of the Gradle invocation | 200.1 s | 348.9 s |
| serial phases (startup → configure) | 18.6 s — 9.3% | **84.3 s — 24.1%** |
| wall-clock left for task execution | 181.4 s | 264.7 s |
| **sum** of task durations | 361.8 s | 343.8 s |
| implied average parallelism | **1.99×** | **1.30×** |
| tasks executed / total | 201 / 863 | 44 / 69 |
| work in the executed ones | 347.4 s | 343.7 s |
| work in the cached and skipped ones | 14.4 s | 0.0 s |

**Read the last three rows before the first three.** Gradle's "Task Execution" figure is a *sum
over tasks that ran in parallel*, so it exceeds the wall-clock span it occupies — 361.8 s of task
time inside a 181.4 s window. It attributes the **work**; it does not attribute wall-clock to an
individual task, and no report can while tasks overlap. A percentage of a task against the job's
wall-clock would be an invented number and none is given here.

The image's 1.30× is the more interesting figure: the native path is a chain, so the runner's
other cores have little to do. That bears on RQ2 and on any thought of a bigger runner.

### The five largest contributors, CI `build`

| task | time | |
|---|---|---|
| `:server:linkReleaseExecutableNative` | 130.3 s | the shipped binary |
| **`:kotlinWasmToolingSetup`** | **52.4 s** | Node and binaryen, for `:shared`'s `wasmJs` target |
| `:server:linkDebugExecutableNative` | 31.3 s | |
| `:server:compileKotlinJvm` | 28.6 s | |
| `:server:compileKotlinNative` | 22.1 s | |

Two things fall out. **The CI job links the binary three times** — release, debug, and
`linkDebugTestNative` (12.9 s): 174.5 s of the 347.4 s of executed work, half of it, is native
linking. And **`:kotlinWasmToolingSetup` costs 52.4 s**, a download of a Node toolchain for a
target that is nowhere near the shipped artifact. Neither was visible before this run; both are
RQ3's business.

### The five largest contributors, Image

| task | time | |
|---|---|---|
| `:server:linkReleaseExecutableNative` | 142.1 s | |
| **`:shared:commonizeNativeDistribution`** | **42.8 s** | the commonizer, over `:shared`'s nine targets |
| `:shared:compileKotlinLinuxX64` | 39.9 s | |
| `:shared:downloadKotlinNativeDistribution` | 17.9 s | |
| `:dev:retrace:compileKotlinNative` | 14.5 s | |

`downloadKotlinNativeDistribution` runs **in four modules** — `:shared` 17.9 s, `:core` 13.5 s,
`:dev:retrace` 13.4 s, `:server` 13.4 s, **58.2 s summed** — into a container whose `~/.konan`
starts empty every time. And `commonizeNativeDistribution` spends 42.8 s commonizing nine targets
for a build that links one.

### A correction to the amber figure

The heartbeat-limited estimate below put "Gradle: startup, configuration, resolution" at 131.9 s.
The profile says the serial phases are **84.3 s** (of which `Configuring Projects` is 63.9 s). The
~48 s difference was early *task* execution — the four `downloadKotlinNativeDistribution` tasks —
which the flush-mark timestamps could not separate from configuration because nothing was printed
between them. The older figure is superseded, not merely refined: it attributed task time to
configuration.

---

## How it looked before the instrument existed

Kept for the method. The verdicts in this section are the ones that were true on 2026-09-14,
before #65.

---

## T_release — the Image job

Three successful runs, decomposed by [raw/decompose-image.py](raw/decompose-image.py). BuildKit's
`#N DONE <s>` markers bound the docker steps; the workflow's own steps come from the job log's
per-line timestamps.

| phase | median | range |
|---|---|---|
| job setup + checkout | ~2 s | |
| pull `gradle:9.7.1-jdk25-noble` | 10.2 s | 8.8–18.7 |
| `apt-get install g++` | 14.9 s | 14.7–15.1 |
| `COPY . .` | 0.2 s | |
| **Gradle: startup, configuration, resolution** | **131.9 s** | 123.4–148.5 |
| **Gradle: Kotlin/Native toolchain download** | **23.5 s** | 23.5–28.0 |
| **Gradle: Kotlin compilation and link** | **216.1 s** | 193.2–288.0 |
| image export | 0.9 s | 0.6–1.0 |
| smoke test, shutdown transcript, allocator assertion | ~5 s | |

Two of those lines are the finding, and neither is compilation:

- **132 s of configuration and dependency resolution, every run.** `.dockerignore` correctly
  excludes `.gradle/`, so the configuration cache is cold inside the image — the log says so
  itself: `Calculating task graph as no cached configuration is available`. `--no-daemon` forks
  a single-use daemon on top.
- **24 s re-downloading the Kotlin/Native toolchain**, every run, because `~/.konan` inside the
  build stage starts empty. It fetches LLVM 21 and the sysroots — and also
  `aarch64-unknown-linux-gnu-gcc` and `qemu-aarch64-static`, which this linuxX64 build never
  uses; they come in because `:shared` declares `linuxArm64`.

Together that is ~156 s of the ~410 s median job, spent before any of this repository's code is
compiled, on work that a BuildKit cache mount would not repeat. That is RQ4's hypothesis, and it
is not tested here — RQ0 is not the place to test it.

### Why this is amber and not green

The `#11 <seconds>` prefixes inside the Gradle step are **not per-line timestamps** — they are
when BuildKit flushed the batch a line arrived in. Most gaps are 0.1 s, but in the quiet stretch
around the native compile the stamps land on a heartbeat: `compileKotlinNative` →
`linkReleaseExecutableNative` measured **exactly 30.0 s in all three runs**, every stamp ending
in `.5`. A duration that identical across three runs on a shared runner is the instrument, not
the subject, so compilation and link are reported as one phase and the boundary marks carry
±heartbeat.

Splitting them needs `gradle --profile` inside the build stage with the report copied out.

## T_pr — the CI `build` job

One run, `34896166611`, decomposed by [raw/decompose-job.py](raw/decompose-job.py):

| step | span | attributable? |
|---|---|---|
| Set up job, checkout | 5 s | yes — CI setup |
| `sborka/setup-kotlin` | 26 s | yes — CI cache restore |
| duplicate `~/.konan` `actions/cache@v6` | 13 s | yes — CI cache restore |
| `./gradlew build --stacktrace` | **280 s** | **no** |
| post-steps | 12 s | yes |

Within the Gradle step, the first Kotlin task starts at +33 s, so ~33 s is configuration and
resolution. **The remaining ~247 s — 73% of the job — is a single opaque bucket.**

It cannot be opened from this log. Gradle's plain console prints a line when a task *starts* and
no duration; with `org.gradle.parallel=true` the tasks overlap, so the gap between two start
lines is not the duration of the first. There is no retroactive fix: the run has to be
instrumented before it happens.

**26% attributed. RQ0 is red for `T_pr`.**

## What the instrumentation change has to be

`--profile`, not a build scan. Nothing in this repository or in sborka configures Develocity, and
the free scan service publishes build data to a third party — an outward-facing step that this
research does not need. `--profile` is built into Gradle, writes an HTML report plus its data
under `build/reports/profile/`, and the CI job already uploads `**/build/reports/**` on failure.

Three places need it, and they are one PR. **Done** — all three are behind a flag that is off by
default, so an ordinary pull request runs exactly what it ran before:

| where | how to ask for it | what comes back |
|---|---|---|
| `ci.yml` | `workflow_dispatch` with `profile: true` | `gradle-profile-build` artifact |
| `server/Dockerfile` | `--build-arg GRADLE_PROFILE=--profile` | the report, via `image.yml` |
| `image.yml` | `workflow_dispatch` with `profile: true` | `gradle-profile-image` artifact |
| [raw/measure-local.sh](raw/measure-local.sh) | `PROFILE=1` | `<tag>.profile.html` beside each timing |

A profile run of `image.yml` builds `--target build` and stops: it does not produce the runtime
image and does not run the smoke test. It is a measurement, never a gate. Building both would be
two full uncached builds — about fourteen minutes — to answer a question the first one answers.

[raw/parse-profile.py](raw/parse-profile.py) turns the report into a TSV. It reports each task's
**outcome** alongside its duration, and that column is not decoration: a task marked `FROM-CACHE`
or `UP-TO-DATE` contributes its name to the graph and almost nothing to the clock, and a table
that drops it invites the reader to total up work that never happened.

**Timings from a profiled run do not go in the results table.** Profiling perturbs what it
measures — the phase-profiled link above took 138 s against the 132 s median of the unprofiled
ones. Profile runs answer *where*; the plain runs answer *how long*.

And for the inside-the-link split, `-Xprofile-phases` **exists on the pinned compiler** — checked
against `kotlin-native-prebuilt-linux-x86_64-2.4.10`, which also offers the experimental
`-Xdetailed-perf`. The brief's caveat ("verify the flag exists") is discharged.

---

## The local metrics — measured, and attributed by compiler phase

Three interleaved reps on the Linux box, [raw/local-20260914-235104/](raw/local-20260914-235104/).
Every one of the nine passed the harness guard: the compile and the link both actually ran.

| metric | n | median | min | max | CV |
|---|---|---|---|---|---|
| `T_warm` | 3 | **0.87 s** | 0.78 s | 0.91 s | 6.6% |
| `T_incr_dbg` | 3 | **12.81 s** | 12.29 s | 13.21 s | 3.0% |
| `T_incr_rel` | 3 | **132.01 s** | 130.36 s | 134.12 s | 1.2% |

**The noise gate is not a problem after all.** §2 refuses a metric above CV 15%, and methodology.md
warned that this box has been seen to swing ±15%. That was a throughput observation, and it does
not transfer: build wall-clock here is 1.2–6.6%. The caution stays in methodology.md for rps-shaped
measurements; for these three it is withdrawn.

### Inside the link

`-Xprofile-phases` on the pinned 2.4.10 compiler, one forced link each
([raw/phase-profile-release-toplevel.txt](raw/phase-profile-release-toplevel.txt),
[raw/phase-profile-debug-toplevel.txt](raw/phase-profile-debug-toplevel.txt)). Named sub-phases
account for 98.1% of the compiler's own umbrella figure, and the umbrella is 130.2 s of the 132.0 s
task — so **97% of `T_incr_rel` is attributed. Green.**

| phase | release | | debug | |
|---|---|---|---|---|
| `ModuleBitcodeOptimization` | 43.7 s | 33.6% | — | — |
| `ObjectFiles` | 31.5 s | 24.2% | 1.3 s | 22.2% |
| `LTOBitcodeOptimization` | 27.5 s | 21.1% | — | — |
| `EscapeAnalysis` | 4.7 s | 3.6% | | |
| `LinkKlibs` | 2.4 s | 1.8% | 1.1 s | 17.9% |
| `Codegen` | 1.5 s | 1.2% | 0.8 s | 14.1% |
| **`Linker`** (the actual `ld`) | **0.21 s** | **0.17%** | 0.2 s | 3.5% |
| `Frontend` | 0.04 s | 0.03% | | |
| **LLVM total** | **103.8 s** | **79.7%** | **1.5 s** | **25.0%** |
| *umbrella* | *130.2 s* | | *6.0 s* | |

Three things are worth saying out loud.

**The task called "link" spends 0.17% of its time linking.** Frontend is 0.03%. Nothing in this
metric is about parsing Kotlin or about `ld`.

**`T_incr_rel` meets the first clause of kill criterion 2.** The brief says to stop if "≥ 70% of
`T_incr_rel` is LLVM opt/codegen **and** no lever in RQ1–RQ7 moves it ≥ 10%". It is 79.7%. The
second clause is untested, so the criterion does not fire — but it is now the question the rest of
the study answers. RQ1 (compiler caches) and RQ7 (input size) are the only listed levers that could
plausibly reach LLVM time; RQ2, RQ3, RQ5 and RQ6 cannot touch it by construction.

**The debug loop is a different machine.** It has no `ModuleBitcodeOptimization` and no LTO at all,
LLVM is a quarter of it rather than four fifths, and `LinkKlibs` — pure I/O — is its second-largest
phase. A lever that helps one has no reason to help the other, and the two must not be reported as
one "edit→link loop".

### And `T_incr_dbg` is already inside kill criterion 1

12.81 s against the brief's 20 s. The other half of that criterion, `T_pr ≤ 5 min`, is not met
(6.7 min for a server-touching PR), so the project does not stop. But the direction is set: **the
debug edit→link loop is not the problem.** The release link and the pipeline are.

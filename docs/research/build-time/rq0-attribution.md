# RQ0 — where does the time go?

**Verdict: not green. The CI side is red, the image side is amber, and the brief's own rule
applies — instrumentation is fixed before anything is optimised.**

> RQ0 Red: cannot attribute ≥ 70% → stop and fix instrumentation before any optimisation.
> Nothing else in this brief proceeds on unattributed time.

| metric | attributed to named phases | verdict |
|---|---|---|
| `T_release` (Image job) | ~83% — but the largest bucket is "Kotlin compilation and link" undivided | **amber** |
| `T_pr` (CI `build` job) | ~26% | **red** |
| `T_incr_dbg`, `T_incr_rel`, `T_warm` | measured; not yet split by K/N phase | pending |

What that buys: the next change to this repository is a **`--profile` change**, not a cache
change. Every RQ1–RQ7 lever is waiting on it.

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

Three places need it, and they are one PR:

1. `ci.yml` — `--profile` on the `build` step, and the report uploaded on success as well as on
   failure.
2. `server/Dockerfile` — `--profile` on the Gradle invocation, with the report copied into the
   build stage output so the Image job can surface it.
3. The local harness — [raw/measure-local.sh](raw/measure-local.sh) already keeps every Gradle
   log; it gains `--profile` and keeps the reports next to the timings.

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

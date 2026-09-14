# Methodology

## Machines

| | |
|---|---|
| **Local (native targets)** | Ubuntu 24.04 under WSL2 on a mobile Core Ultra 7 255HX. 20 cores; **15 GiB** of RAM, of which ~3 were in use at the time of writing. Reached through the `katcher` mutagen session (`~/Documents/GitHub/katcher` → `youndie@127.0.0.1:2222:katcher`) and `~/.claude/bin/wsl-run`. |
| **Local (mac)** | 16 GB. Editor, not a build host. It links **macosArm64**, not linuxX64 — see "The task is not called what the brief called it" below. |
| **CI** | `ubuntu-latest`, GitHub-hosted, standard size. katcher is a **public** repository, so these runners are free: there is no billing term, and `C_pr` is identically zero. RQ5's "does saving the cache pay for itself under the runner's billing" therefore reduces to wall-clock. |

Pinned versions, from `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`:
Kotlin 2.4.10, Gradle 9.7.1, JDK toolchain 25 (`sborka.jvmToolchain`, floor 25), KSP 2.3.11, AGP 9.3.1.

## The task is not called what the brief called it

`:server`, `:core`, `:dev:retrace` and `:dev:sample-kotlin-native` choose their native target
by host and name it `native`:

```kotlin
hostOs == "Linux" && (arch == "x86_64" || arch == "amd64") -> linuxX64("native")
hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
```

So **`linkReleaseExecutableLinuxX64` does not exist as a task in this repository** — the task
is `linkReleaseExecutableNative`, which is what `build-katcher-native.yaml` and
`server/Dockerfile` both invoke. The consequence is not cosmetic: run on the mac, that same
task name links macosArm64 through a different LLVM target, and any `T_cold` / `T_incr_rel`
taken there is a measurement of a backend katcher does not ship. **Every native metric is
taken on the Linux box.**

`:client` and `:shared` declare their targets explicitly and do not have this property; they
also have no executable binaries, so they have no link task at all.

## Noise this repository has to measure through

The brief's §2 gate — a metric is not reportable at CV > 15% — collides with the only machine
that can link linuxX64:

- **The box is a laptop.** Throughput between identical runs on it has been seen to differ by
  ±15% from turbo and thermal throttling, and neither `intel_pstate` nor `cpufreq` is visible
  from inside WSL2, so the governor cannot be pinned. That is the gate's threshold, not a
  margin under it.
- **It is not idle.** It also hosts a self-hosted Actions runner and other syncs. Background
  load at the time of a measurement is part of the measurement and goes in the log beside it.
- **Consequence for the protocol.** Variants are interleaved (A B A B A B), never run in blocks,
  and the reported figure is a median of three per variant with the full spread printed. A
  difference inside the spread is reported as inconclusive, not as a small win.
- **Measured, 2026-09-14: build wall-clock on this box does not swing like that.** Three
  interleaved reps gave CV of 1.2% (`T_incr_rel`), 3.0% (`T_incr_dbg`) and 6.6% (`T_warm`) — well
  inside the gate. The ±15% is a *throughput* observation and does not transfer to a
  mostly-serial, allocator-and-IO-bound build. The caution above stands for any rps-shaped
  measurement and is withdrawn for these three.

## What a measurement of this loop has to survive

Three harness faults produced plausible numbers before being caught; each is written up in
[retractions.md](retractions.md). The rules they leave behind:

- **The source edit is made on the mac, never on the replica.** The mutagen daemon watches the
  beta side and reverts an edit there within seconds — before the compiler reads it.
- **The edit must change the klib, not just the file.** A comment recompiles the source and
  produces byte-identical output, so the link task's inputs do not move and the build cache
  answers for it. The probe appends a top-level `val`.
- **Every timed incremental run asserts that the compile AND the link executed.** Not
  `UP-TO-DATE`, not `FROM-CACHE`. A run that fails the assertion is printed as `void:<reason>`,
  never as a number.
- **BuildKit's `#N <seconds>` prefixes are flush marks, not line marks.** They cannot resolve
  phases inside a quiet stretch.

## What the replica is and is not

The mutagen session is `one-way-replica`: the mac is the source, the box is a copy.

- **Edits go in the mac checkout.** Anything written directly on the box is erased by the next
  `flush`, which `wsl-run` performs before every command — including files a remote build wrote
  outside the ignore list. An artefact needed back on the mac is read in the same `wsl-run`
  call that creates it.
- **There is no `.git` on the replica** (`--ignore-vcs`). `git` commands there fail, `git diff`
  proves nothing about the repository, and the `kore` build plugin's `dirty` flag reads
  `unknown`. This does not affect link time and does affect anything that asserts on `/version`.
- **`build/`, `.gradle/`, `.kotlin/` and `.idea/` are not synced**, so the box keeps its own.
  A `T_cold` therefore means clearing them *on the box*, not on the mac.

## Attribution

- Task level: Gradle build scans and `--profile`.
- Inside the link: Kotlin/Native phase timing. **The flag is unverified for 2.4.10** — the brief
  names `-Xprofile-phases` with the caveat that it be checked, and it has not been. RQ0's red
  condition (cannot attribute ≥ 70%) is about exactly this, so the flag is confirmed against
  the pinned compiler before any phase number is written down, not after.
- CI: step boundaries come from the run log's per-line timestamps — see the docstring in
  [raw/decompose-job.py](raw/decompose-job.py) for what that does and does not resolve.

## Rules carried from the brief

- One variable per experiment; each experiment is a branch off the baseline commit.
- Local numbers never justify a CI change, and CI numbers never justify a local one.
- Green/red thresholds are declared before the measurement, not after it.
- A number that is re-run and does not reproduce goes in [retractions.md](retractions.md)
  rather than quietly out of the table.

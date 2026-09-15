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
- **Consequence for the protocol.** The reported figure is a median per variant with the full
  spread printed, and a difference inside the spread is inconclusive rather than a small win.
  Whether variants are interleaved or blocked depends on what the variant *is* — see below.

## Interleave or block: it depends on what the variant is

- **A variant that does not touch the build's configuration is interleaved** (A B A B A B), so a
  thermal ramp lands on every variant rather than on whichever went last.
- **A variant that is a Gradle property is blocked**, with a throwaway run after each switch.
  Switching one invalidates the configuration cache — `configuration cache cannot be reused
  because Gradle property '…' has changed` — and alternating puts that rebuild inside the timed
  window, a cost no real edit loop pays. This is the reverse of the rule above and was learned by
  publishing an impossible ordering; see retractions.md.
- **Daemons are left running.** A real edit loop runs against a warm daemon, so `./gradlew --stop`
  before a campaign measures start-up rather than work. Warm up instead, and declare the
  warm-up/measure cut *before* the run rather than choosing the split that flatters the result.

## The noise gate, and what it gets wrong

§2 refuses a metric at CV > 15%. Applied to a **difference between variants of different
magnitude, it penalises success**: the absolute jitter on this box is about the same number of
milliseconds either way — roughly what one Gradle invocation varies by — so a variant that halves
the metric doubles its own CV. RQ1's winner failed the gate for having worked.

Judge the difference against the absolute spread instead: RQ1's two medians are 3754 ms apart,
3.3× the larger standard deviation. The CV gate still applies to a metric reported on its own
rather than as a difference.
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


## Incident, 2026-09-15: the box stops, and everything downstream lies about it

Mid-campaign, every `wsl-run` call began printing `mutagen sync flush katcher не прошёл`. The
cause was not the sync and not the tunnel: the WSL guest `Ubuntu-24.04` was in state `Stopped`,
and a diagnostic command was what started it again. All thirty mutagen sessions in the portfolio
were in `Connecting to beta`, not just this one.

**Three things failed quietly, and the order matters.**

- **`wsl-run` returns 0 when the flush fails.** The background campaign reported
  `completed (exit code 0)` having built nothing. Exit status is not a signal here; the output is.
- **The harness guard is what saved the data.** Rep 4 printed `void:link-` rather than a number,
  because the link task did not run. Without it, three numbers from variant B and none from A
  would have looked like a campaign to interpret rather than one to discard.
- **A half-campaign cannot be rescued.** B's three reps are internally consistent and worthless:
  with no A taken under the same conditions there is nothing to compare them to.

**Diagnosis, in the order that actually distinguishes the causes:**

| check | what it rules in or out |
|---|---|
| `ping` the Windows host | the machine, versus everything on it |
| `wsl -l -v` via ssh to port 22 | whether the guest is running at all — this is the one that answered |
| `ss -lnt` inside the guest | whether sshd is listening, versus whether it is reachable |
| `Test-NetConnection` from Windows | the relay, versus the tunnel — and it **lied**: it reported success on a port a real socket connect then refused |
| a raw socket read from Windows | the truth: either the SSH banner comes back or it does not |
| `ssh -vv` from the mac | `kex_exchange_identification: Connection closed` means the far end dropped it before key exchange — not authentication, not the host key |

**What the guest actually needs.** In mirrored networking mode the guest's address *is* the host's
(`hostname -I` returns 192.168.1.102), and Windows reaches sshd there while `127.0.0.1:2222` is
refused. So the tunnel has to be `-L 2222:192.168.1.102:2222`, not `-L 2222:127.0.0.1:2222`. That
gets one working session — and then the guest stops again shortly after the last command exits,
which is the real fault and is a configuration matter on the machine, not something to be worked
around from here.

**For measurement, the lasting point:** a stand that can stop mid-campaign has to be *asked*
whether it was up, per campaign, not assumed. Recording box load and memory at the start of each
block already existed; it is not enough, because a box that is gone records nothing at all.

## The baseline that counts, and the two stands behind it

**Taken 2026-09-15 on the WSL box, in `~/katcher-bench` at `2153193`** —
[raw/baseline-wsl-2026-09-15.tsv](raw/baseline-wsl-2026-09-15.tsv). Four reps, rep 1 discarded as
warm-up, the cut declared before the run.

| metric | median | range | CV |
|---|---|---|---|
| `T_warm` | **0.94 s** | 0.88–1.02 | 5.9% |
| `T_incr_dbg` | **8.03 s** | 7.37–8.97 | 8.1% |
| `T_incr_rel` | **110.13 s** | 109.91–112.40 | 1.0% |

Load stayed between 0.31 and 2.85 on 20 cores for the whole campaign, and that is readable in the
results file rather than asserted here: **every row carries load before, load after and memory
used.** This is the first campaign of the study that can say it ran on a quiet machine and show it.

It is a baseline for the repository *as it now stands* — `kotlin.incremental.native=true` from RQ1
is in it. It is not comparable to the pre-merge figures taken on the mutagen replica.

### bench-a, set up and abandoned

A Hetzner box (4 cores, 7.7 GiB, Intel Xeon Skylake) was provisioned as a second stand and dropped.
Two reasons, and the second is the one that decided it:

1. **The release link does not fit.** With the repository's `-Xmx4G` the Gradle daemon reaches
   5.95 GB RSS — LLVM allocates outside the Java heap — and `clang++` then asks for its own. The
   kernel log carries 125 OOM kills. `-Xmx3g` completes at ~300 s against `-Xmx4G`'s 256–275 s:
   slower, but it finishes.
2. **It was already someone's measurement stand.** A `memory-probe` campaign was running on it, with
   `k6` driving load from bench-b. Two measurement campaigns on one box corrupt each other in both
   directions, and mine was the more damaging: a probe measuring behaviour under a 512 MB limit,
   next to a process triggering global OOM, measures my process.

### What a machine needs to build this at all

Seven independent failures, each looking like a broken build rather than a missing package. Worth
more than most of the settings this study measured: a setting costs minutes, a missing `libcrypt.a`
costs an hour and points at the linker.

| | requirement | how it fails without it |
|---|---|---|
| 1 | **JDK 25 for Gradle itself** | `kore-build` sets a JVM 25 floor at *buildscript* level; foojay provisions compilation toolchains and cannot help |
| 2 | **`g++-13`, not `g++`** | `libGcc` is pinned to `/usr/lib/gcc/x86_64-linux-gnu/13`; a distribution defaulting to 15 leaves that path absent |
| 3 | **`libcrypt-dev`** | `ld.lld: unable to find library -lcrypt` |
| 4 | **`zlib1g-dev`** | `-lz`, and only the **debug** link needs it — the release link succeeds without it |
| 5 | **`libffi-dev`** | |
| 6 | **Reachable `github.com`** | Gradle distributions redirect there from `services.gradle.org`; an IPv6-only host cannot fetch the wrapper |
| 7 | **Reachable `reposilite.kotlin.website`** | IPv4-only, and it carries the `sborka` plugins every module applies |

The comment in `server/Dockerfile` — *"gcc 13 here is the version the `libGcc` override names; the
two move together"* — reads as a historical note and is a working warning. The first port to
another distribution walked straight into it.

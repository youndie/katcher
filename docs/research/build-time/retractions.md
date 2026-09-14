# Retractions

Claims made in the course of this research and later withdrawn. A number that is re-run and
does not reproduce belongs here, not quietly out of a table.

## 2026-09-14 — Four "findings" read off a checkout 20 commits behind

There are two katcher checkouts on the mac:

```
~/IdeaProjects/katcher      200ad6a (#38), version 0.6.2   — 20 commits behind origin/main
~/Documents/GitHub/katcher  809a27f (#63), version 0.8.2   — == origin/main
```

The first survey of the repository read the first one. Four things it reported were already
fixed on `main`:

| Reported | Actually |
|---|---|
| `publish-katcher-native.yaml` greps `^VERSION=` while `gradle.properties` says `version=`, so the image tag is empty | Fixed; the workflow greps `^version=`, and `gradle.properties` carries a comment explaining exactly this breakage |
| No root `.dockerignore`, so `COPY . .` sends `build/`, `.gradle/`, `.kotlin/` | `.dockerignore` exists and excludes them; `.git` is kept deliberately |
| Runtime image is `distroless/cc`, paired with the builder by glibc version | `FROM scratch` with a statically linked binary; the glibc-pairing rule is gone with the base image it was about |
| Group is `ru.workinprogress.katcher` | `io.github.youndie.katcher` |

**How it happened:** the session's additional working directory pointed at the stale checkout,
and nothing about reading it says it is stale. **What prevents it:** `git rev-list --count
HEAD..origin/main` in the checkout before quoting anything out of it — which is how the
discrepancy was eventually found.

## 2026-09-14 — "katcher is not in `mutagen sync list`"

Reported that katcher had no mutagen session, and that it therefore built locally on the mac
and no linuxX64 metric could be taken until a session was created.

**Wrong on every part.** The session `sync_7pu2…` already existed, already pointed at
`~/Documents/GitHub/katcher`, and the replica already held `build/` directories from earlier
builds. The claim came from `mutagen sync list | grep … | head -40`, which showed 10 of the
31 sessions; katcher was the 22nd.

Acting on the retracted claim created a second session under the same name, briefly leaving
two one-way replicas writing the same beta path. It was terminated
(`sync_Qbpj22…`); `sync_7pu2…` is the live one.

**What prevents it:** an absence is not established by a filtered listing. `mutagen sync list
<name>` answers the question that was actually being asked, and exits non-zero when the
session does not exist.

---

## 2026-09-14 — Three harness faults, each caught by an impossible number

All three were found while taking the local baseline, and none of them would have been visible in
the result alone. They are listed because the brief's deliverable includes the method, and a method
that only records what worked teaches nothing.

### 1. The source edit never reached the compiler

The first harness edited `Main.kt` **on the replica** and built there. It reported
`incr_rel` at **690 ms against `warm` at 1043 ms** — a run that recompiles finishing faster than a
run that does nothing.

The mutagen session is `one-way-replica` and its daemon **watches**: a beta-side edit is reverted
within seconds, well before Gradle reads the file. `wsl-run`'s pre-command flush was never the only
thing writing to that tree. Checking afterwards, `grep -c 'build-time probe' Main.kt` on the box
returned **0**.

Fixed by moving the edit to the mac and letting the flush carry it, with the timed window still
opened and closed on the box so the ssh round trip stays outside it.

### 2. A comment is not a one-line change

The rewritten harness then reported `void:no-work` on every incremental run. The log said why:

```
> Task :server:compileKotlinNative                     ← ran
> Task :server:linkDebugExecutableNative FROM-CACHE    ← did not
```

The probe appended a **comment**. That recompiles the file and produces a byte-identical klib, so
the link task's inputs do not move and the build cache answers for it. The single run that had
reported a plausible 133 s was the run that *populated* that cache entry; it never reproduced.

Fixed by making the probe a unique top-level `val`, which changes the klib — as a real edit does.

**Retracted with it:** the `133 693 ms` from the 23:44 validation run, and the claim in the interim
report that the harness was "sound" on the strength of it. The ordering was right; the mechanism
was not, and one run that cannot be repeated is not a measurement.

### 3. BuildKit's timestamps are flush marks, not line marks

The Image decomposition first reported `:server:compileKotlinNative` at **exactly 30.0 s in all
three runs**, every stamp ending in `.5`. A duration identical to the tenth of a second across
three runs on a shared runner is the instrument.

The `#11 <seconds>` prefix is when BuildKit flushed the batch a line arrived in. Most gaps in these
logs are 0.1 s, but in the quiet stretch around the native compile the stamps fall on a heartbeat.
Compilation and link are now reported as one phase, and the surrounding boundaries carry
±heartbeat — which is why `T_release` is amber rather than green.

**Common to all three:** the number looked reasonable in isolation. What exposed each one was a
comparison it had to survive — against another metric, against a task's own log line, against a
second run. A measurement with nothing to contradict it is not yet evidence.

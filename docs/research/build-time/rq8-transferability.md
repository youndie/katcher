# RQ8 — transferability to tracy

**Status: green on the half that was measured. `T_pr` is not measured, and RQ8 asked for both.**

> Apply the recipe to **tracy** with **no repo-specific edits**, measure `T_incr_dbg` and `T_pr`
> before/after.
> **Green:** ≥ 50% of the relative gain seen on katcher reproduces.
> **Red:** < 25% → the skill is labelled "katcher-derived, not validated elsewhere".

## The result on `T_incr_dbg`

| | median | CV |
|---|---|---|
| tracy, before | **5207 ms** | 6.6% |
| tracy, with `kotlin.incremental.native=true` | **3901 ms** | 12.3% |
| **effect** | **−25.1%** | |
| katcher, for comparison | −45.5% | |

**55% of katcher's relative gain reproduced**, against a green threshold of 50%.

Four reps each, rep 1 discarded, the cut declared before the run. Load stayed between 0.60 and
1.49 on 20 cores and is recorded per row —
[raw/rq8-tracy-before.tsv](raw/rq8-tracy-before.tsv),
[raw/rq8-tracy-after.tsv](raw/rq8-tracy-after.tsv).

**The margin is five percentage points and the evidence is not symmetric.** katcher's 45.5% is
pooled from four campaigns; tracy's 25.1% is one. tracy's post-change CV is 12.3%, twice its own
baseline's. A second tracy campaign would be worth more than any further argument about the
threshold.

## What transferred, and what did not

**The setting transferred exactly.** One line in `gradle.properties`, no repo-specific edit, which
is what RQ8 demanded.

**The measurement script did not**, and that was the first thing this question found — before any
number. §7 promises the script lets "the next repo produce its own before/after in one command".
It did not run on the next repo at all:

| | katcher | tracy |
|---|---|---|
| native target | picked by host, named `native` | `linuxX64()`, declared |
| link task | `linkDebugExecutableNative` | `linkDebugExecutableLinuxX64` |
| compile task the guard looks for | `compileKotlinNative` | `compileKotlinLinuxX64` |

Two repositories in one portfolio, two task names. The task names are parameters now, with both
repositories' values in the script's header.

The guard's task name mattered as much as the build's. Left at katcher's value it would have
reported `void:compile-not-run` on every tracy measurement — refusing good numbers rather than
passing bad ones, which is the right way for a guard to fail, and still a failure that costs a
campaign.

## What is not answered

**`T_pr` on tracy.** The CI half of the recipe — dropping the duplicate release link (RQ3), moving
Gradle out of `docker build` (RQ4), removing the duplicate `~/.konan` cache (RQ5) — is not applied
to tracy and not measured there. tracy has the same shape (`ci.yml`, `image.yml`, a
`server/Dockerfile`), so the port is plausible; plausible is not measured.

**So RQ8 is green on one of its two metrics and silent on the other.** The skill can say the
compiler-cache setting is validated on a second repository. It cannot yet say the CI recipe is.

## Also worth recording

tracy's debug loop is **faster** than katcher's — 5.2 s against 8.0 s — while its debug binary is
**42 MB against katcher's 16.7 MB release binary**. Three modules against eleven: the task graph
is shorter, and that outweighs the size. Another reason the thing carried between repositories is
the relative gain and not the seconds.

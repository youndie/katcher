# RQ1 — compiler caches and incremental compilation

**Verdict: green. `kotlin.incremental.native=true` takes `T_incr_dbg` down 45.5%, against a
threshold of 40%. Adopted.**

The `cacheKind` half of the question turned out not to be a question. The control is inconclusive
for a reason worth stating. And the campaign broke the brief's noise gate four times, which
exposed a defect in the gate rather than in the box.

## What the brief asked, and what was actually available

> `kotlin.native.cacheKind` (static vs none as the A/B), `kotlin.incremental.native=true`.
> **Green:** `T_incr_dbg` down ≥ 40%. **Red:** < 15%, or incremental mode produces a wrong/failed
> build in any of 3 runs.

`static` is already the default. `~/.konan/kotlin-native-prebuilt-linux-x86_64-2.4.10/klib/cache/`
holds `linux_x64STATIC-system` and `linux_x64-gSTATIC-user-pl` on a box that has only ever built
this repository with stock settings. So "static vs none" is not a lever to adopt — static is what
already runs. It stays in as a **control**, to price what the default caches are worth, and the
only untested lever in RQ1 is `kotlin.incremental.native`.

| variant | |
|---|---|
| **A** | baseline, nothing passed |
| **B** | `-Pkotlin.incremental.native=true` |
| **C** | `-Pkotlin.native.cacheKind=none`, the control |

Passed on the command line rather than written into `gradle.properties`: this tree is a mutagen
source and an edit travels to the box mid-run.

## Result

Pooled over four campaigns, steady-state values only, each campaign's cut declared before its run:

| variant | n | median | sd | CV |
|---|---|---|---|---|
| A | 10 | **8246 ms** | 1147 ms | 13.0% |
| B | 13 | **4492 ms** | 750 ms | 16.1% |
| C | 3 | 8340 ms | 891 ms | 10.7% |

**B against A: 45.5%.** And the same figure from each campaign separately, computed against that
campaign's own baseline rather than a number from an earlier one:

| campaign | A | B | effect |
|---|---|---|---|
| blocks, busy box | 8301 | 4305 | 48.1% |
| blocks, quiet box (post-ramp) | 8190 | 4216 | 48.5% |
| blocks, declared warm-up cut | 8190 | 4452 | 45.6% |

### Correctness, which is half the red condition

The red condition is not only slowness — it is "or incremental mode produces a wrong/failed build
in any of 3 runs". Speed was guarded; correctness had to be asked separately.
`:server:nativeTest --rerun-tasks` under B, three times: **`:server:nativeTest: 129 test(s)`,
green each time.** The count is quoted because a suite that finds nothing also reports success.

That is evidence of correctness over what those 129 tests cover — the server's repository layer —
and not a proof that the incremental Kotlin/Native compiler is correct in general.

### C is inconclusive, and the reason is not "no difference"

C measured 8340 ms against A's 8246 — no effect. But the only receipt in hand is that the property
reached **Gradle**: the log says `configuration cache cannot be reused because Gradle property
'kotlin.native.cacheKind' has changed`. There is no receipt that it changed what the **compiler**
did. Without one, "the caches are worth nothing here" and "the variant never happened" produce the
same number, and the honest label is inconclusive. Pricing the default caches needs a run that can
show the cache was bypassed — a cold `~/.konan`, or the compiler's own arguments.

## The gate is wrong, not the box

Four campaigns, four different reasons the §2 noise gate failed, and the fourth is the interesting
one.

1. **Interleaved variants.** Each variant is a Gradle *property*, and switching one invalidates the
   configuration cache — the log says so outright. Alternating charged every timed run a
   configuration rebuild that a real edit loop never pays. Void.
2. **Blocks on a busy box.** An hour of measuring took the box from loadavg 0.99 to 2.32 and
   +1.3 GB of memory in use. The stand was measuring its own accumulated daemons.
3. **Blocks on a quiet box.** `./gradlew --stop` freed 7 GB and bought a cold-start ramp instead:
   A went 21556 → 11968 → 8190. A quiet box is not a warm one, and a real edit loop runs on a warm
   daemon — stopping it measures start-up. Two warm-up runs were not enough.
4. **Enough warm-up, and B still fails the gate.** Reps 6–8 of the final campaign: A CV 9.0%,
   B CV 19.3%.

The fourth has a cause that no amount of warm-up fixes:

```
A   sd 1147 ms on a median of 8246
B   sd  750 ms on a median of 4492
```

**The jitter is roughly the same number of milliseconds in both.** It is what one Gradle
invocation varies by on this machine. A variant that halves the metric therefore doubles its own
CV — so a CV gate, applied literally, forbids reporting an optimisation *for having worked*. The
better the result, the louder the gate.

What the difference is worth is the thing the gate was trying to protect, and it survives being
asked directly: **the two medians are 3754 ms apart, 3.3× the larger standard deviation.**

### Proposed amendment to §2

Judge the **difference against the absolute spread**, not each variant's CV:

> A difference is reportable when it exceeds 3× the larger of the two variants' standard
> deviations. A CV gate additionally applies to any metric reported on its own rather than as a
> difference.

Not applied to anything retroactively — RQ1 clears 40% by every campaign's own numbers regardless.

## Adopted

`kotlin.incremental.native=true` in `gradle.properties`.

**What is not measured: its effect on CI.** RQ1's scope is the local debug loop, and a
`gradle.properties` line applies everywhere — CI and the image build included. A fresh CI checkout
has no incremental state to reuse, so the expectation is "no effect or a slight overhead", and an
expectation is not a measurement. This PR's own CI run is a single data point, not a before/after.
If `T_pr` moves, RQ3 and RQ6 will see it.

## Evidence

[raw/rq1/](raw/rq1/) — the four drivers, every `results.tsv`, the environment line of every block
(box load and memory at its start), and the native-test log with its count.

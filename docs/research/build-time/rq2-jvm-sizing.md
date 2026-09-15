# RQ2 — JVM sizing for the two processes

**Status: amber at 9.96%, against a green threshold of 10%. Not adopted.** One campaign
completed; the second was cut off when the Linux box stopped, and its half cannot answer the
question it was asked. Below: the premise check that came first, then the result.

> **RQ2.** Gradle daemon heap and `kotlin.native.jvmArgs` (the compiler is a separate JVM).
> Swiggy tuned one JVM; K/N has two.

Four facts, from a census of every `java`/`kotlin`/`konan` process taken 45 s into a release link —
[raw/rq2-process-census.txt](raw/rq2-process-census.txt). The census is printed whole, without a
`head`, because the first look at it *was* through a `head -4` and it produced a confident wrong
answer.

## 1. There is one JVM doing the work, not two

No `KotlinCompileDaemon`. No separate Kotlin/Native compiler process. During
`linkReleaseExecutableNative` the only processes are two Gradle daemons and the wrapper launcher at
`-Xmx64m`. The native compile runs **inside the Gradle daemon**.

So RQ2's premise does not hold for this build, and two things follow. `kotlin.daemon.jvmargs=-Xmx4G`
in `gradle.properties` is attached to a process that does not exist during the link. And
`kotlin.native.jvmArgs`, the flag the brief names, has no separate JVM to size.

## 2. The repository's `org.gradle.jvmargs` is not in effect

`gradle.properties` declares:

```
org.gradle.jvmargs=-Xmx4G -Dfile.encoding=UTF-8
```

Both running daemons carry `-Xmx5g -XX:MaxMetaspaceSize=1g`, which comes from
`~/.gradle/gradle.properties` on the box. There is no daemon at `-Xmx4G` anywhere in the process
list — not one that lost a race, none at all.

**An RQ2 experiment that edited this repository's `org.gradle.jvmargs` would have measured
nothing**, and would have reported "no effect" with three clean runs behind it. That is the exact
shape of a red result that is really an absent variant — the same shape RQ1's `cacheKind` control
ended in. The difference is that this one was caught before the measurement rather than after.

## 3. Two daemons, on a box with 15 GiB

Two `GradleDaemon` processes, `-Xmx5g` each, RSS 4.96 GB and 3.13 GB at the moment of the census —
8 GB resident between them, out of 15 GiB. Which of the two serves a given `./gradlew` invocation
is not something the harness controls or records.

This is a candidate for the jitter RQ1 could not remove, and it is testable: pin the measurement to
a single daemon and see whether the absolute spread narrows.

**It does not invalidate RQ1.** Both of its variants ran under the same 5 GB daemons across all
four campaigns, so the comparison is unaffected; what moves is only the absolute numbers' spread.

## 4. RSS exceeds the heap cap, and that is expected

4.96 GB resident against `-Xmx5g` is not an overflowing heap. LLVM's allocations are native — the
phase table in [rq0-attribution.md](rq0-attribution.md) puts 79.7% of the release link in
`ModuleBitcodeOptimization`, `ObjectFiles` and `LTOBitcodeOptimization` — and native memory sits
outside `-Xmx` along with metaspace, code cache and thread stacks.

Which sharpens the RQ2 hypothesis rather than killing it: if four fifths of the link is LLVM
working in native memory, a bigger *Java* heap has little of that to touch. That is a prediction,
and RQ2 exists to test it rather than to assert it.

## What RQ2 has to be instead

1. **Establish which knob is live.** The effective setting comes from `~/.gradle/gradle.properties`,
   not from the repository, so the variant is passed on the command line
   (`-Dorg.gradle.jvmargs=…`) or the experiment is about the box, not about katcher.
2. **One daemon during a campaign**, recorded by pid, so a measurement names the process that
   produced it.
3. **Thresholds unchanged**: green is `T_incr_rel` or `T_cold` down ≥ 10% with no OOM in 3 runs;
   red is < 5%, recorded as "defaults are fine".
4. **A receipt that the variant reached the JVM** — the daemon's own `-Xmx` in the process list —
   before any number is compared. RQ1's control had no such receipt and could not be read.


---

# The measurement

## Campaign 1 — complete

One daemon, started for each variant with `-Dorg.gradle.jvmargs`, with the receipt taken before
any number was compared. Rep 1 warm-up, reps 2–4 reported, all declared before the run.

| variant | reps 2–4 | median | CV |
|---|---|---|---|
| A `-Xmx5g` | 132427, 134433, 134762 | **134 433 ms** | 0.8% |
| B `-Xmx10g` | 121047, 123934, 119910 | **121 047 ms** | 1.4% |

**9.96%.** Green is ≥ 10%, red is < 5%, and between them the brief says "inconclusive, not
adopted". It is inconclusive by four hundredths of a percentage point, and rounding it up would be
choosing the answer.

No OOM: nothing in `dmesg`, and no `OutOfMemoryError` or `GC overhead` in any of the eight logs.

**The prediction written down beforehand was red**, on the reasoning that 79.7% of the release link
is LLVM allocating in native memory outside `-Xmx`, leaving a bigger Java heap little to touch.
That was wrong by a factor of two against the red threshold. The heap does have something to touch;
what, is not established here.

### The confound points the other way

The receipt for B shows **two** daemons — A's 5 GB one was still alive beside B's 10 GB one — and
the box went from 2176 MiB used and loadavg 0.57 to 8200 MiB and 1.20. So B was measured under the
worse conditions of the two and still came out ahead: the bias runs against the finding, not for
it.

What is missing is a receipt that B's *builds* were served by the 10 GB daemon. That such a daemon
existed is proved. That the invocations reached it rests on Gradle matching daemons by JVM
arguments, which is documented behaviour and not an observation.

## Campaign 2 — aborted, and it takes the ordering question with it

Reversed order (B first), daemons stopped *between* variants to close both gaps at once. B
produced three valid reps — 129120, 127699, 127260 — then rep 4 came back `void:link-` and
variant A produced nothing at all. Every call was failing on `mutagen sync flush`.

The box had stopped. Not the sync, not the tunnel: the WSL guest itself was in state `Stopped`,
and it was a diagnostic command that started it again.

**The half-campaign cannot be salvaged.** Its B agrees with campaign 1 in shape but sits 5% higher,
and with no A taken under the same conditions there is nothing to compare it to. A baseline is a
property of its conditions.

## What has to happen before RQ2 can be called

1. **The box has to stay up.** See the incident note in [methodology.md](methodology.md).
2. **A receipt that the daemon serving a build is the one with the variant's heap**, not merely
   that such a daemon exists. Gradle's `--status` names pids; the harness should record which one
   answered.
3. **More reps.** Two campaigns straddling a threshold by hundredths is not a verdict. The ranges
   do not overlap at all — 132.4–134.8 against 119.9–123.9 — so the separation is real and it is
   only the point estimate that sits on the line.

Until then RQ2 stays amber and nothing is adopted. `-Xmx10g` on a 15 GiB guest is also not free:
the guest stopped during the campaign that used it, and while no causal link is established, a
setting that may cost the whole virtual machine needs a stronger case than 9.96%.

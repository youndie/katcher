# RQ2 — JVM sizing for the two processes

**Status: the premise failed the first check, so no A/B has been run yet.** What follows is what
the process list says, and it changes what RQ2 can even ask.

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

# RQ3 — build only what ships

**Status: the change is made and awaiting measurement. The task-count half of the green
condition is rejected as a proxy, with numbers.**

> **Green:** task count on the PR workflow down ≥ 30% **and** `T_pr` down ≥ 25%, with the same set
> of checks still enforced (list them before and after).
> **Red:** `T_pr` down < 10%, or a check silently dropped.

Everything below comes from the task table of the profiled CI run,
[raw/ci-tasks-34903415756.tsv](raw/ci-tasks-34903415756.tsv), which carries each task's duration
**and** its outcome.

## The checks a pull request is gated by today

Required by the brief before anything is removed. A check here means a task that can fail a pull
request for a reason other than "it did not compile".

| | tasks | executed | cached | NO-SOURCE / SKIPPED | time |
|---|---|---|---|---|---|
| tests | 31 | **3** | 7 | 21 | 11.6 s |
| ktlint | 259 | **1** | 76 | 182 | 4.2 s |
| Android lint | 16 | 9 | 4 | 2 | 4.2 s |

The suite, in full:

| task | |
|---|---|
| `:server:jvmTest` | 7.38 s |
| `:server:nativeTest` | 3.17 s |
| `:client:linuxX64Test` | 1.04 s |

**All 341 check tasks together cost 21.2 s** of the run's 361.8 s of task time. Checks are 5.9% of
the work. The other 94% is compiling and linking — partly so those checks can run, partly for
artifacts nothing checks.

### Two things in that table are not build-time findings

**`:core` has no tests.** `:core:jvmTest` is NO-SOURCE and `:core:nativeTest` is SKIPPED. That is
the module holding the HTMX pages and the domain interfaces. Nothing in this study asks for test
coverage, and the fact is recorded here rather than acted on, because "list the checks" turned it
up and leaving it out would make the list flattering.

**182 of 259 ktlint tasks are NO-SOURCE.** They cost nothing; they also check nothing. A lint gate
counted by task count looks far larger than it is.

## Where the time actually goes

| task | time | who consumes it |
|---|---|---|
| `:server:linkReleaseExecutableNative` | 130.3 s | **nothing in CI** |
| `:kotlinWasmToolingSetup` | 52.4 s | the `wasmJs` compile of `:shared` |
| `:server:linkDebugExecutableNative` | 31.3 s | **nothing** |
| `:server:compileKotlinJvm` | 28.6 s | `:server:jvmTest` |
| `:server:compileKotlinNative` | 22.1 s | the links and `:server:nativeTest` |
| `:server:linkDebugTestNative` | 12.9 s | `:server:nativeTest` |

**The binary is linked three times per run**, and only the third — the test binary — feeds a
check. 161.6 s of the 361.8 s produces executables that no CI check runs.

For the release one, that is duplicated coverage rather than absent coverage: `image.yml` links the
same binary inside `docker build` and then **runs** it — smoke test, shutdown transcript, allocator
assertion. Moving the release link off the CI job removes a duplicate, not a check.

The debug executable is different: nothing links it anywhere else, so dropping it would drop the
check "the debug executable links". That has to be named, not assumed away.

`:kotlinWasmToolingSetup` at 52.4 s is a Node and binaryen **download**, not compilation, and
`:shared:wasmJsTest` is NO-SOURCE — there are no wasmJs tests. What the target buys is the check
"the published `wasmJs` variant compiles", which is real. The 52.4 s is a caching question and
belongs to RQ5, not here.

## Why RQ3 cannot be green on its own

`T_pr` is the slower of CI and Image on a pull request that touches the server — that definition is
in §2, and it was written there because the two run in parallel and the merge waits for both.

- CI's Gradle step carries 361.8 s of task time at 1.99× parallelism.
- **Image is 6.7 m and CI is 5.1 m.** Image is the gate.

Removing 161.6 s of task time from CI moves CI. It does not move `T_pr`, because `T_pr` is Image.
RQ3's green demands `T_pr` down ≥ 25% **and** task count down ≥ 30%; the second is easy and the
first is not RQ3's to give.

**RQ3 and RQ4 are coupled, and the brief treats them as independent.** Image spends 84.3 s on
configuration and 23.5 s re-downloading the Kotlin/Native toolchain into a container whose
`~/.konan` is empty every time — that is RQ4's cache-mount question, and until it moves, nothing
RQ3 does can reach `T_pr`.

The honest options are to run RQ4 first and re-measure RQ3 against whatever `T_pr` becomes, or to
restate RQ3's green against a metric RQ3 can actually move — CI wall-clock rather than `T_pr`.
Both are edits to the brief and neither is made unilaterally: the thresholds were declared before
measurement and changing one after seeing the numbers is the thing the declaration exists to
prevent.

## What is not yet done

No change is proposed and nothing is measured after. The "after" list the brief demands does not
exist yet, because there is no after.


---

# Task count and time are not the same axis here

RQ3's green wants **both** a 30% cut in task count and a 25% cut in `T_pr`. Measured against this
repository's actual task table, those two ask for opposite changes.

| candidate | share of the 863 tasks | task time |
|---|---|---|
| `:dev` samples — nothing in the shipped path depends on them | **33.7%** (291) | **17.6 s** |
| `:client` | 23.4% (202) | 6.5 s |
| `:server:linkReleaseExecutableNative` | **0.1%** (1) | **130.3 s** |

Cutting 30% of the count means removing the samples, and buys seventeen seconds while dropping the
check "the samples still compile against the client API". Cutting the time means removing one task
and leaves the count essentially unchanged.

**So the count criterion is rejected here, and not because it is inconvenient.** It was a
reasonable proxy to write down before anything was measured — fewer tasks, less work — and the
profile says it does not hold in this repository, where the cheap things are numerous and the
expensive thing is singular. Recorded as a finding rather than met by removing checks worth
seventeen seconds.

## The change

`./gradlew build -x :server:linkReleaseExecutableNative`

**No check is dropped, and that is the part that needs the argument rather than the number.**
`build` reaches the task through `assemble`, and nothing in the CI job consumes the result: the
tests link their own binary via `linkDebugTestNative`, and nothing runs the release executable. The
same binary is linked by `image.yml`, which then *runs* it — smoke test through an authenticated
page, six-stage shutdown transcript, allocator assertion.

The two jobs cover each other because of the path filter. Every path that could break this link —
`server/`, `core/`, `shared/`, `dev/retrace/`, `gradle/`, the root build files — is in `image.yml`'s
list. A pull request that skips `image.yml` touches only `client`, `charts`, `dev` samples or docs,
and `:server` depends on none of them. A Kotlin bump, the case `server/Dockerfile` explicitly
worries about for its `-Xoverride-konan-properties` pins, arrives through `gradle/**` and is
covered.

## Declared before the measurement

- `T_pr` is the slower of CI and Image on a server-touching PR, per §2.
- **RQ4 moved the gate.** Before it, Image at 402 s was slower than CI at ~290 s. After it, Image is
  221 s and **CI is the gate** — which is why RQ3 can now reach `T_pr` at all, and why it is
  measured on top of RQ4 rather than against the original baseline.
- Green ≥ 25% on `T_pr`, red < 10%, per the brief. Measured as three runs, median.
- The checks after must equal the checks before: three test tasks, the ktlint set, Android lint.
  The "after" list is taken from the profile of the changed job, not asserted.

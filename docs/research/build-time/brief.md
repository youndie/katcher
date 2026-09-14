<!-- This is the brief as written, with two edits, both invited by the brief itself.
     §2: the task the metrics name does not exist in this repository — see
     "The task is not called what the brief called it" in methodology.md.
     §9: filled from the repository; the last two rows were filled on 2026-09-15 from a
     decision, not from the repository, and say so.
     §5: kill criterion 4 struck, as a consequence of there being no time box.
     The green/red thresholds are UNCHANGED and therefore stand. -->

# Research brief: katcher build time, local and CI

**Status:** §9 filled. RQ0 answered — see [rq0-attribution.md](rq0-attribution.md); it is red on the CI side, so the instrumentation change comes before any RQ1–RQ7 lever.
**Subject:** `youndie/katcher` — Kotlin/Native `linuxX64` server binary plus a multiplatform client
**Deliverables:**

1. katcher with a measurably faster local edit→link loop and a faster PR / release pipeline, changes merged as PRs with numbers in the description
2. `docs/research/build-time/` — methodology, raw logs, results, retractions
3. Skill `native-service-bootstrap` — the recipe, where every item carries the measured effect and the RQ it came from. Items that did not reach green do not enter the recipe as advice; they enter a "tried, no effect" section.

Reference read: Swiggy, *Scaling Android CI: 44 → 10 minutes* (Jul 2026). Treated as a source of hypotheses, not of results — every claim in it is re-measured here or not used.

---

## 1. Why this repo

- The native binary is the shipped artifact (`ghcr.io/youndie/katcher`, Helm chart), so link time is on the commit→deploy path.
- No Compose, no AGP, no cinterop to system libraries in the server path; sqlx4k ships its own binaries. Fewer confounders than `mani`.
- metrik and tracy have the same shape, so the recipe has an immediate second and third application (RQ8).
  *(Checked 2026-09-15: tracy does — `server/Dockerfile` and an `image.yml`, plain `docker build`. metrik does not, quite: it publishes through `docker/build-push-action` with buildx cache reuse across two calls, so on the RQ4/RQ5 axis it starts ahead of katcher rather than level with it.)*
- The repo carries its own DAG bloat to study: a client published for 10 targets, `dev/` modules, `tailwind/`, `kotlin-js-store/wasm`.

## 2. Metrics

All times are wall-clock, median of 3 runs, with min/max recorded. Machine spec, Kotlin/Gradle/JDK versions and `--scan` / `--profile` link committed next to every number.

| Metric | Definition |
|---|---|
| `T_cold` | `:server:linkReleaseExecutableNative` from clean checkout, empty `~/.gradle` and `~/.konan` |
| `T_warm` | same task, second run, nothing changed (should be ≈ configuration time only) |
| `T_incr_dbg` | one-line change in `:server` source → `:server:linkDebugExecutableNative` |
| `T_incr_rel` | same change → `:server:linkReleaseExecutableNative` |
| `T_pr` | **the slower of the CI and Image workflows** on a PR that touches `server/`, checkout to green, median over 3 pushes of a one-line change. Both are the merge gate and they run in parallel; a client-only PR does not run Image at all and is not what this metric measures |
| `T_release` | release/image workflow, checkout to image pushed |
| `T_first_compile` | CI: workflow start → first Kotlin compile task starts (setup + cache restore) |
| `C_pr` | CI minutes billed per PR run. **Identically zero**: katcher is a public repository, where GitHub's standard runners are free. Recorded so RQ5 is not argued on a cost that does not exist |

There is no `linkReleaseExecutableLinuxX64` task: `:server` selects its native target by host and names it `native`. On the Linux box that target is linuxX64; on the mac it is macosArm64, which is why every native metric is taken on the box.

Noise gate: if the coefficient of variation across the 3 runs of any metric exceeds 15%, the metric is not reportable until the source of noise is found and removed.

## 3. Method

- One variable per experiment. Each experiment is a branch off the baseline commit; the diff is the experiment.
- Phase attribution: Gradle build scan for task-level; Kotlin/Native `-Xprofile-phases` (or the equivalent for the pinned Kotlin version — verify the flag exists) for inside-the-link attribution: frontend / IR lowering / LLVM codegen / LLVM opt / native link.
- Local runs on the machine named in §9; CI runs on the runner named in §9. Local numbers are never used to justify a CI change and vice versa.
- Every number is re-run by Pavel before it is committed to `docs/research/`.

## 4. Research questions

Green/red are declared here, before any measurement. Amber (between the two) is reported as "inconclusive, not adopted".

### RQ0 — Where does the time go?

Baseline decomposition of `T_cold`, `T_incr_dbg`, `T_incr_rel`, `T_pr`, `T_release` into: Gradle configuration, dependency resolution, klib compilation, link (by K/N phase), tests, Docker, CI setup/cache.

- **Green:** ≥ 90% of wall-clock attributed to a named phase for every metric.
- **Red:** cannot attribute ≥ 70% → stop and fix instrumentation before any optimisation. Nothing else in this brief proceeds on unattributed time.

Also answered here, as facts not experiments: is the image built with Gradle inside `docker build`? Which tasks does the PR workflow actually run? Which client targets are compiled on a PR? Is `~/.konan` cached in CI?

### RQ1 — Compiler caches and incremental compilation (local, debug loop)

`kotlin.native.cacheKind` (static vs none as the A/B), `kotlin.incremental.native=true`.

- **Green:** `T_incr_dbg` down ≥ 40% vs baseline.
- **Red:** < 15%, or incremental mode produces a wrong/failed build in any of 3 runs.

### RQ2 — JVM sizing for the two processes

Gradle daemon heap and `kotlin.native.jvmArgs` (the compiler is a separate JVM). Swiggy tuned one JVM; K/N has two.

- **Green:** `T_incr_rel` or `T_cold` down ≥ 10% with no OOM in 3 runs.
- **Red:** < 5% → recorded as "defaults are fine", not adopted.

### RQ3 — Task graph: build only what ships

PR path: which of {debug link, release link, tests, 10 client targets, `dev/*`, tailwind, wasm store} actually gates a merge. Release path: same question for the image.

- **Green:** task count on the PR workflow down ≥ 30% **and** `T_pr` down ≥ 25%, with the same set of checks still enforced (list them before and after).
- **Red:** `T_pr` down < 10%, or a check silently dropped (any check removed must be named in the PR).

### RQ4 — Docker layering

Applies only if RQ0 finds Gradle running inside `docker build`. **It does** — `server/Dockerfile` stage 1 runs `gradle :server:linkReleaseExecutableNative`, with no BuildKit cache mounts and no `cache-from`/`cache-to` on the `docker build` in `image.yml`. RQ4 is not void. Options: BuildKit cache mounts for `~/.gradle` and `~/.konan`; or build outside, `COPY` the binary in.

- **Green:** warm `T_release` down ≥ 50%.
- **Red:** < 15%.
- **Void:** pattern not present → RQ4 is dropped, not counted as red.

### RQ5 — CI cache architecture

What to cache (`~/.gradle/caches`, `~/.gradle/wrapper`, `~/.konan`, K/N compiler caches under `build/`), how to key it (Swiggy's tiered keys: branch → release → main), and whether saving cache per branch pays for itself under the runner's billing.

- **Green:** `T_first_compile` ≤ 90 s and cache hit rate ≥ 80% over 10 consecutive PR runs.
- **Red:** restore + save cost ≥ savings (measured, not assumed), or hit rate < 50%.

### RQ6 — Gradle remote build cache, configuration cache in CI

Two separate A/Bs. Swiggy disabled configuration cache in ephemeral CI and measured ~30 s saved; that number is theirs, not ours.

- **Green:** each setting shows ≥ 30 s net effect on `T_pr` in the measured direction.
- **Red:** |effect| < 10 s → left at default, noted.

### RQ7 — Input size as a build-time lever

Does LLVM phase time track binary size? Use razves to rank dependencies by contribution; test removing or replacing the top one.

- **Green:** one dependency change yields ≥ 10% on `T_incr_rel` without functional regression (test suite green).
- **Red:** no removable contributor ≥ 5% → "code size is not the lever for this repo", recipe says so.

### RQ8 — Transferability

Apply the recipe to **tracy** (chosen in §9) with **no repo-specific edits**, measure `T_incr_dbg` and `T_pr` before/after.

- **Green:** ≥ 50% of the relative gain seen on katcher reproduces.
- **Red:** < 25% → the skill is labelled "katcher-derived, not validated elsewhere" and the generic claims are removed from it.

## 5. Kill criteria

Stop the project and publish the negative result if any of these holds after RQ0:

1. **Nothing to win.** Baseline `T_incr_dbg` ≤ 20 s and `T_pr` ≤ 5 min. The loop is already fast enough; the recipe becomes "here is what a fast baseline looks like" and the project ends at RQ0.
2. **LLVM ceiling.** ≥ 70% of `T_incr_rel` is LLVM opt/codegen and no lever in RQ1–RQ7 moves it ≥ 10%. Document the ceiling with phase numbers and stop; do not chase it with compiler flags.
3. **Unreducible noise.** CV > 15% on the primary metrics after removing obvious sources (thermal, other processes, shared runners). Numbers that cannot be trusted are not published as gains.
4. ~~**Budget.** Time box in §9 exhausted with RQ0–RQ3 not all closed.~~ **Struck 2026-09-15.**
   §9 sets no time box, so this criterion has nothing to fire on. It is crossed out rather than
   deleted: a kill criterion that quietly stops being checked is worse than one that is openly
   withdrawn. The study now stops only on 1, 2 or 3 — nothing to win, the LLVM ceiling, or
   unreducible noise.

## 6. Non-goals

- Migrating the dev loop to a JVM target. The point is to make the native loop fast, not to avoid it.
- Runtime performance, memory, or binary size for their own sake (binary size only as a build-time lever in RQ7).
- Changing the Kotlin version, unless as a single measured experiment with its own before/after.
- Apple Silicon / macOS: `linuxX64` links on Linux; the Swiggy hardware conclusion does not transfer.
- Changing the client library's public API or its published target set. Moving client builds off the PR path is in scope (RQ3); shrinking the matrix is not.
- Bazel or any non-Gradle build system.
- `mani`. It may become a second transferability subject later; not in this brief.
- Any change whose only evidence is a blog post.

## 7. What goes into `native-service-bootstrap`

For each item: what to set, the measured effect on katcher, the RQ it came from, and the condition under which it applies (e.g. "only when Gradle runs inside `docker build`").

- `gradle.properties` block (RQ1, RQ2, RQ6)
- PR workflow template: task selection and cache keys (RQ3, RQ5, RQ6)
- Release workflow template (RQ3, RQ4)
- Dockerfile pattern (RQ4)
- Measurement script and the metric definitions from §2, so the next repo can produce its own before/after in one command
- "Tried, no effect" section (every red and amber)
- Transferability note (RQ8 outcome)

The skill is not written until RQ8 is closed.

## 8. Outputs

- `docs/research/build-time/` in katcher: brief (this file), methodology, raw `--scan`/`--profile` outputs, results table, retractions.
- PRs to katcher, one per adopted change, numbers in the description.
- `native-service-bootstrap` skill directory.
- A short results post for kotlin.website if RQ8 is green; a "what didn't work" post if it is red. Both are outputs.

## 9. Parameters to fill before RQ0

| Parameter | Value |
|---|---|
| Local machine (Linux native, or Docker on macOS) | Linux native: Ubuntu 24.04 under WSL2, 20 cores, 15 GiB, via the `katcher` mutagen session and `wsl-run`. The mac links macosArm64 and is not a measurement host |
| CI runner (GitHub-hosted ubuntu size, or self-hosted) and billing model | `ubuntu-latest`, GitHub-hosted, standard size. Public repository → free; no billing model |
| Pinned Kotlin / Gradle / JDK versions (README badge shows Kotlin 2.4.10 — verify in `libs.versions.toml`) | Kotlin 2.4.10 (badge verified against the catalog), Gradle 9.7.1, JDK toolchain 25. Also: KSP 2.3.11, AGP 9.3.1 |
| Time box | **none** — the study runs until a kill criterion fires. Decided 2026-09-15; see the note under §5 |
| Second subject for RQ8 (metrik or tracy) | **tracy** — `server/Dockerfile` plus an `image.yml`, the same shape as katcher, and a plain `docker build` with no cache, so RQ4 is a genuine before/after there. metrik was the other candidate and is **not** the subject: it already builds through `docker/build-push-action` with buildx cache reuse, which makes it a source of answers rather than a test of transfer |

The green/red thresholds above are proposals. Edit them here, before RQ0 runs, or they stand.

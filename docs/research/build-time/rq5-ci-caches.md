# RQ5 — CI cache architecture

**Status: green at baseline, before any change. One duplicate removed, which the green did not
depend on.**

> **Green:** `T_first_compile` ≤ 90 s and cache hit rate ≥ 80% over 10 consecutive PR runs.
> **Red:** restore + save cost ≥ savings (measured, not assumed), or hit rate < 50%.

## Measured, on the architecture as it already was

Eight consecutive successful CI runs, read from their logs:

| | |
|---|---|
| `T_first_compile` | **median 70 s**, range 59–86 s (n=8) |
| cache hit rate | **8/8 = 100%** |

Both clear the green thresholds without anything being changed. **RQ5 is a finding, not a lever:**
the caching this repository already had was doing its job, and the honest entry in the recipe is a
number rather than a setting.

## The billing half of the question does not exist

> "...and whether saving cache per branch pays for itself under the runner's billing."

katcher is a **public** repository, so GitHub's standard runners are free and `C_pr` is identically
zero. There is no billing term to weigh a cache save against; the question reduces to wall-clock,
and wall-clock says the cache pays.

## What was removed, and why it is not what made it green

`ci.yml` carried a second `actions/cache@v6` on `~/.konan`, below the `sborka/setup-kotlin` step
that already caches it. Two entries, one directory, **different keys**:

| | key |
|---|---|
| `sborka/setup-kotlin` | `hashFiles('gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties')` |
| the duplicate | `hashFiles('gradle/*.versions.toml')` — a glob that also matches `jvmLibs.versions.toml` |

Measured cost in the decomposed run: **13 s** restoring a directory restored 13 s earlier, out of a
337 s job. Its post step cost nothing there because the key hit exactly — but on a version-catalogue
bump both entries would save, writing the same ~1 GB twice under two keys.

Removing it takes 13 s off a job that was already inside the threshold. That is tidying with a
measurement attached, not a lever, and it is reported as such.

## Three `~/.konan` caches now exist in this repository

Worth writing down, because it is the kind of thing that grows quietly:

| workflow | key prefix |
|---|---|
| `ci.yml`, via `sborka/setup-kotlin` | `konan-<os>-` |
| `build-katcher-native.yaml`, `publish-*.yaml`, via the same action | `konan-<os>-` |
| `image.yml`, added by RQ4 | `image-konan-<os>-` |

The third is deliberate — RQ4's job restores into a container mount and needs its own lifecycle —
but three prefixes for one toolchain is a thing to notice before it becomes four.

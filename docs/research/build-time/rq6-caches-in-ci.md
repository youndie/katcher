# RQ6 — remote build cache, configuration cache in CI

**Status: red on both halves. Left at default, noted.**

> Two separate A/Bs. Swiggy disabled configuration cache in ephemeral CI and measured ~30 s saved;
> **that number is theirs, not ours.**
> **Green:** each setting shows ≥ 30 s net effect on `T_pr` in the measured direction.
> **Red:** |effect| < 10 s → left at default, noted.

## Configuration cache: the mechanism is real here, the cost is not

First the mechanism, checked in this repository's own logs before anything was changed. Eight
consecutive CI runs, every one of them:

```
Calculating task graph as no cached configuration is available
...
Configuration cache entry stored
```

**Computed and serialised on every run, reused on none.** The entry lives in the project's
`.gradle/`, every CI run is a fresh checkout, and `setup-gradle` caches `~/.gradle` — a different
directory. So the premise that made the setting worth questioning holds exactly as described.

Then the A/B, three runs each, `--no-configuration-cache` applied to the CI invocation only:

| variant | runs | median | sd |
|---|---|---|---|
| configuration cache on | 147, 160, 159 s | **159 s** | 5.9 |
| `--no-configuration-cache` | 135, 170, 172 s | **170 s** | 17 |

**Effect on the means: 3.7 s, in the direction of *slower*.** Red is |effect| < 10 s, so this is
red — and it would be red at 3.7 s in either direction.

The mechanism costs less in this repository than the runner's own variation. Swiggy's ~30 s was
never going to transfer on its own, and the brief said so in the sentence that introduced the
question. It is worth having measured rather than adopted: the log lines look exactly like a
problem, and the clock says they are not one.

## Remote build cache: already in place, under another name

A remote build cache in the Develocity sense needs a server, and there is none. But the thing it
exists to provide is already here: `gradle/actions/setup-gradle` restores `~/.gradle`, which
contains `caches/build-cache-1`, and the logs show it hitting on **8 of 8** runs
(`gradle-build-cache-v2-d7c1611b…`).

So the A/B has nothing to compare. Standing up a Develocity server to measure against a cache that
already works would be answering a question this repository does not have.

## Not adopted, and the change reverted

The CI-only `--no-configuration-cache` was applied, measured, and reverted in the same branch. The
setting stays at its default.

**One thing the numbers say that the verdict does not.** The variant's spread is three times the
baseline's — 135–172 against 147–160, on n=3 either way. That is too few runs to call it a finding
and too visible to leave out; if this question is ever reopened, the wider spread is the thing to
look at first rather than the median.

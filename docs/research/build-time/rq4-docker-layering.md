# RQ4 — docker layering

**Status: amber at 45.0%, against a green threshold of 50%. Not adopted.**

The change works and is large. It is four percentage points short of the line the brief drew before any of this was measured, and that line is not moved now that the number is in.

RQ0 answered the question RQ4 is conditional on: Gradle **does** run inside `docker build`, so RQ4
is live rather than void.

> **Green:** warm `T_release` down ≥ 50%. **Red:** < 15%.

`T_release` is measured here as the **Image job**, the one on the pull-request path and the one
RQ0 decomposed. `Publish server native` also exists and pushes; it is not what is measured, and
saying so matters because the two are different numbers (6.7 m and 6.2 m medians).

## What the baseline spends

From the profiled Image run, [raw/profile-image-34903429789.html](raw/profile-image-34903429789.html):

| | |
|---|---|
| Gradle step, wall-clock | **348.9 s** of a ~410 s job |
| ...of which serial startup → configure | 84.3 s |
| ...of which `Configuring Projects` alone | 63.9 s |
| `downloadKotlinNativeDistribution`, four modules | 58.2 s summed |
| `:server:linkReleaseExecutableNative` | 142.1 s |

Every run pays all of it, because the container's `~/.gradle` and `~/.konan` start empty and its
configuration cache does not exist — the log says `Calculating task graph as no cached
configuration is available`.

## Why the cache-mount option is not the variant

The brief offers two: BuildKit cache mounts, or build outside and `COPY` the binary in.

The first cannot work on a GitHub runner, and this is **reasoning, not a measurement**:

- `--mount=type=cache` lives in the builder's own state, and the builder is fresh every run.
- `cache-to: type=gha` exports **layers**, not cache mounts. That is the gap
  `buildkit-cache-dance` exists to paper over.
- Layer cache alone buys nothing here: `COPY . .` carries the source, so it changes on every
  commit and the Gradle `RUN` layer below it is invalidated on every run. Only the base image pull
  and the `apt-get` layer — 10.2 s and 14.9 s — could ever be reused, which is 6% of the job and
  cannot reach a 50% threshold.

A variant that cannot move the metric is not worth a campaign. It is written down rather than run.

## The variant

Build outside, assemble inside — but **in the same image the builder stage uses**, which is the
part that keeps this honest.

`server/Dockerfile` copies five glibc artefacts out of the build stage on purpose: a statically
linked binary still `dlopen`s its gconv converters, and the comment is explicit that "the shared
glibc a static binary dlopens has to be the same build as the `libc.a` it was linked against".
Linking on the runner and copying glibc from `gradle:9.7.1-jdk25-noble` would break that pairing —
same glibc *version*, different build — and the failure mode is not a broken build. It is a 500
from the first authenticated page, which is exactly what `dev/image-smoke.sh` was written to catch
after it happened once.

So the link keeps running inside `gradle:9.7.1-jdk25-noble`. What changes is *how* it is invoked:

```
docker run --rm \
  -v "$PWD":/app -w /app \
  -v ~/.gradle:/root/.gradle -v ~/.konan:/root/.konan \
  gradle:9.7.1-jdk25-noble  gradle :server:linkReleaseExecutableNative
```

The caches are the runner's, restored by `actions/cache` — the same ones the CI job already keeps
warm — and they are writable, persistent across runs, and outside BuildKit's reach by design. The
Dockerfile then becomes assembly: the glibc artefacts still come from that image, the binary comes
from the build context.

### What this does not do

It does not remove the duplicate link RQ3 found — CI links the release binary and the Image job
links it again. Sharing one artefact between two jobs is a different change, with its own question
about whether a job that consumes an artefact still checks what a job that builds one checks. One
variable per experiment.

## Thresholds and protocol, declared now

- Green ≥ 50%, red < 15%, on **warm** `T_release`: the first run after the change populates the
  caches and is discarded, and the runs after it are the measurement.
- Three runs, median, spread reported.
- **The image must still pass what it passes today** — the smoke test that renders a timestamp,
  the shutdown transcript, and the `MALLOC_ARENA_MAX=2` assertion. A faster image that fails any of
  them is red, not green, and the smoke test is the one that would catch a broken glibc pairing.
- A receipt that the caches were actually restored, not merely requested: the `actions/cache` hit
  line, and the absence of `downloadKotlinNativeDistribution` work in the run.


---

# The measurement

Three warm runs, each confirmed warm by its own receipt — `Cache restored from key` for **both**
halves — and the run before them discarded because it filled the caches rather than used them.

| | |
|---|---|
| warm `T_release` | 221 s, 216 s, 247 s → **median 221 s**, CV 6.0% |
| baseline, Image on a pull request | 402 s (n=7) |
| baseline, Image on a push | 390 s (n=7) |
| **effect** | **45.0%** against the PR median, 43.3% against the push one |

Green is ≥ 50%, red < 15%. This is neither, so by the brief's own wording it is "inconclusive, not
adopted".

## What the job is made of now

| step | before | after (warm) |
|---|---|---|
| restore caches | — | 27 s |
| Gradle: configure, resolve, download the toolchain | ~108 s | **0 s** |
| link the binary | ~142 s | 179 s |
| build the image | 474 s *(Gradle ran inside it)* | **1 s** |
| smoke test, shutdown transcript, allocator assertion | ~5 s | ~3 s |

Everything outside the link is gone. `docker build` is one second. Configuration, dependency
resolution and the 58 s of `downloadKotlinNativeDistribution` across four modules no longer happen
at all, because the caches they needed now persist where `actions/cache` can reach them.

**So the remaining job is the link plus about forty seconds** — and RQ0 measured the link as 79.7%
LLVM. RQ4 has taken out what was outside the link, and what is left is the thing RQ4 cannot touch.
Another attempt at this question would be re-cutting the same forty seconds.

## Why it is 45% and not more

Not because the caches half-worked — they are confirmed restored — but because the floor is the
link. The 179 s here is slightly *above* the 142 s the profiled cold run spent linking, which is
within the spread of a shared runner and is not treated as a finding.

## Two ambers in a row

RQ2 landed at 9.96% against a 10% threshold. RQ4 lands at 45.0% against 50%. Both changes clearly
work; both fall just short of lines drawn before anything was measured.

That pattern is worth naming rather than averaging away. Either the thresholds were set
optimistically — which is what a threshold declared in advance is *for*, and no reason to move it
afterwards — or the levers are genuinely near their limits, which is what the phase tables suggest.
Nudging either result over its line would make the declaration worthless in both directions.

**The decision is the author's, not the measurement's**: adopt on the numbers as they stand,
restate the thresholds for the RQs still open, or leave both amber and let the recipe say so.
Nothing here is adopted while that is unanswered.

## What did not regress

Every warm run built the image, rendered a timestamp through the authenticated page, produced a
shutdown transcript with all six stages, and kept `MALLOC_ARENA_MAX=2`. The glibc pairing the
design was built around holds — which was the failure this change could plausibly have introduced
and the one the smoke test exists to catch.

# RQ4 — docker layering

**Status: design fixed, variant not yet measured.**

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

# Build time: local and CI

Research into katcher's edit→link loop and its PR / release pipeline. The subject is the
Kotlin/Native server binary, because that binary is the shipped artifact.

**Status: RQ0 green; RQ1 green and adopted; RQ2, RQ3 and RQ4 all amber — 9.96%, 22.2% and 45.0%
against 10%, 25% and 50%.** Three changes that work, each stopping where something else becomes
the binding constraint. The pattern is analysed at the end of rq3-task-graph.md; whether to adopt
on these numbers or restate the thresholds is the author's call and nothing is adopted meanwhile. Every metric is attributed; the CI and
image sides needed a `--profile` change first (#65). `kotlin.incremental.native=true` takes the
debug edit-link loop down 45.5%.

| File | What it holds |
|---|---|
| [brief.md](brief.md) | The research brief. §9 filled, §2 corrected — see the header block in it |
| [methodology.md](methodology.md) | The machines, the metric definitions that are actually runnable here, and the noise this repository has to measure through |
| [baseline.md](baseline.md) | RQ0's "facts, not experiments", and every number measured so far with its provenance |
| [rq0-attribution.md](rq0-attribution.md) | Where the time actually goes, phase by phase, and the RQ0 verdict |
| [rq1-compiler-caches.md](rq1-compiler-caches.md) | RQ1: the lever that worked, the control that could not be read, and why the noise gate is wrong |
| [rq2-jvm-sizing.md](rq2-jvm-sizing.md) | RQ2: the premise checked before the measurement, and what it cost |
| [rq3-task-graph.md](rq3-task-graph.md) | RQ3: what a PR is actually checked by, and why its green condition needs RQ4 first |
| [rq4-docker-layering.md](rq4-docker-layering.md) | RQ4: why the cache-mount option cannot work here, and the variant that can |
| [rq5-ci-caches.md](rq5-ci-caches.md) | RQ5: green before anything was changed, and the duplicate that was not why |
| [rq6-caches-in-ci.md](rq6-caches-in-ci.md) | RQ6: red — a real mechanism that costs less than the noise, and a borrowed number that did not transfer |
| [retractions.md](retractions.md) | Claims made in this research and later withdrawn |
| [raw/](raw/) | The logs the tables were derived from, and the scripts that derive them |

## Reproducing the tables

Both scripts take their evidence as an argument and print the table from it, so a table in
`baseline.md` can be checked without trusting this directory's prose:

```bash
cd docs/research/build-time/raw
./summarise-runs.py gh-run-list-2026-09-14.json
./decompose-job.py ci-run-34896166611.log
./decompose-image.py image-run-*.log
```

The local metrics are taken by [raw/measure-local.sh](raw/measure-local.sh), **from the repository
root on the mac** — it orchestrates here and builds on the box, for reasons its header explains:

```bash
REPS=3 ./docs/research/build-time/raw/measure-local.sh
```

Phase attribution inside the link needs the instrumentation flag, which is off by default:

```bash
wsl-run 'cd ~/katcher && ./gradlew :server:linkReleaseExecutableNative -Pkatcher.profilePhases'
```

Fresh evidence, rather than the captured copies:

```bash
gh run list --limit 120 --json workflowName,conclusion,createdAt,updatedAt,event,headBranch > runs.json
gh run view <run-id> --log > job.log
```

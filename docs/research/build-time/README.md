# Build time: local and CI

Research into katcher's edit→link loop and its PR / release pipeline. The subject is the
Kotlin/Native server binary, because that binary is the shipped artifact.

**Status: RQ0 answered, and its answer is "not yet".** The local metrics are measured and
attributed by compiler phase; the CI side cannot be attributed from the logs it currently
produces, so the next change to this repository is a `--profile` change and not an optimisation.
No RQ1-RQ7 experiment has been run.

| File | What it holds |
|---|---|
| [brief.md](brief.md) | The research brief. §9 filled, §2 corrected — see the header block in it |
| [methodology.md](methodology.md) | The machines, the metric definitions that are actually runnable here, and the noise this repository has to measure through |
| [baseline.md](baseline.md) | RQ0's "facts, not experiments", and every number measured so far with its provenance |
| [rq0-attribution.md](rq0-attribution.md) | Where the time actually goes, phase by phase, and the RQ0 verdict |
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

# Build time: local and CI

Research into katcher's edit→link loop and its PR / release pipeline. The subject is the
Kotlin/Native server binary, because that binary is the shipped artifact.

**Status: RQ0 open.** Nothing in this directory is an experiment yet. What is here is the
baseline and the instrumentation to reproduce it.

| File | What it holds |
|---|---|
| [brief.md](brief.md) | The research brief. §9 filled, §2 corrected — see the header block in it |
| [methodology.md](methodology.md) | The machines, the metric definitions that are actually runnable here, and the noise this repository has to measure through |
| [baseline.md](baseline.md) | RQ0's "facts, not experiments", and every number measured so far with its provenance |
| [retractions.md](retractions.md) | Claims made in this research and later withdrawn |
| [raw/](raw/) | The logs the tables were derived from, and the scripts that derive them |

## Reproducing the tables

Both scripts take their evidence as an argument and print the table from it, so a table in
`baseline.md` can be checked without trusting this directory's prose:

```bash
cd docs/research/build-time/raw
./summarise-runs.py gh-run-list-2026-09-14.json
./decompose-job.py ci-run-34896166611.log
```

Fresh evidence, rather than the captured copies:

```bash
gh run list --limit 120 --json workflowName,conclusion,createdAt,updatedAt,event,headBranch > runs.json
gh run view <run-id> --log > job.log
```

#!/usr/bin/env python3
"""Workflow wall-clock, grouped by workflow and trigger, from `gh run list --json`.

    gh run list --limit 120 --json workflowName,conclusion,createdAt,updatedAt,event,headBranch \
      > gh-run-list-<date>.json
    ./summarise-runs.py gh-run-list-<date>.json

What the number is: updatedAt - createdAt for the whole workflow run, which includes
queueing and every job in it. It is NOT the brief's T_pr: these are heterogeneous pull
requests, not three pushes of one one-line change. The spread across them says how much
the CONTENT of a PR moves the time, not how noisy the runner is.
"""
import collections
import datetime
import json
import statistics
import sys

FMT = "%Y-%m-%dT%H:%M:%SZ"


def duration(run):
    started = datetime.datetime.strptime(run["createdAt"], FMT)
    ended = datetime.datetime.strptime(run["updatedAt"], FMT)
    return (ended - started).total_seconds()


def main(path):
    runs = json.load(open(path))
    grouped = collections.defaultdict(list)
    for run in runs:
        if run["conclusion"] != "success":
            continue
        grouped[(run["workflowName"], run["event"])].append(duration(run))

    print(f"{'workflow':34} {'trigger':18} {'n':>3} {'median':>8} {'min':>8} {'max':>8} {'CV%':>6}")
    for key, seconds in sorted(grouped.items()):
        cv = statistics.pstdev(seconds) / statistics.mean(seconds) * 100 if len(seconds) > 1 else 0.0
        print(
            f"{key[0]:34} {key[1]:18} {len(seconds):3}"
            f" {statistics.median(seconds) / 60:7.1f}m {min(seconds) / 60:7.1f}m"
            f" {max(seconds) / 60:7.1f}m {cv:5.1f}"
        )

    window = [r["createdAt"] for r in runs]
    print()
    print(f"window      {min(window)} .. {max(window)}")
    print(f"runs        {len(runs)} total, {sum(1 for r in runs if r['conclusion'] != 'success')} not successful")


if __name__ == "__main__":
    main(sys.argv[1])

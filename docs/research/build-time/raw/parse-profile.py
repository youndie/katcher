#!/usr/bin/env python3
"""Turn a Gradle `--profile` HTML report into a TSV, so RQ0's opaque bucket can be read.

    ./parse-profile.py build/reports/profile/profile-*.html

Prints the summary lines (total, startup, configuration, task execution) and then one row per
task with its duration and its outcome. The outcome is the half that matters: a task marked
FROM-CACHE or UP-TO-DATE contributes its name to the graph and almost nothing to the clock, and
a table that omits it invites the reader to add up work that never happened.

Why this exists: Gradle's plain-console log prints a line when a task STARTS and never how long
it took, and with `org.gradle.parallel=true` the gap between two start lines is not a duration.
The profile report is the only thing that carries per-task time, and it has to be switched on
before the run — there is no way to recover it afterwards.

Parsed row by row rather than over a flat list of cells. The report has several tables whose
rows differ in width: task rows carry an outcome cell, configuration rows do not. Read flat, a
two-cell configuration row borrows the next row's project name as its "outcome" and prints a
line that looks like data and is not — which this script did on its first outing.
"""
import html
import re
import sys

ROW = re.compile(r"<tr[^>]*>(.*?)</tr>", re.S)
CELL = re.compile(r"<t[dh][^>]*>(.*?)</t[dh]>", re.S)
TAG = re.compile(r"<[^>]+>")

SUMMARY = (
    "Total Build Time", "Startup", "Settings and buildSrc", "Loading Projects",
    "Configuring Projects", "Artifact Transforms", "Task Execution",
)


def to_seconds(text):
    m = re.fullmatch(r"(?:(\d+)m)?\s*([\d.]+)s", text.strip())
    return int(m.group(1) or 0) * 60 + float(m.group(2)) if m else None


def rows(markup):
    for row in ROW.findall(markup):
        yield [html.unescape(TAG.sub("", c)).strip() for c in CELL.findall(row)]


def main(paths):
    print("file\tsection\tname\tseconds\toutcome")
    for path in paths:
        for cells in rows(open(path, errors="replace").read()):
            if len(cells) < 2:
                continue
            name, seconds = cells[0], to_seconds(cells[1])
            if seconds is None:
                continue
            if name in SUMMARY:
                section = "summary"
            elif not name.startswith(":"):
                continue
            elif len(cells) >= 3 and cells[2] == "(total)":
                section = "project"
            elif len(cells) >= 3:
                section = "task"
            else:
                # Two cells and a project name: a configuration or resolution row, not a task.
                section = "configuration"
            outcome = cells[2] if len(cells) >= 3 and cells[2] != "(total)" else ""
            print(f"{path.split('/')[-1]}\t{section}\t{name}\t{seconds:.3f}\t{outcome}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])

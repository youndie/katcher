#!/usr/bin/env python3
"""Step boundaries and task counts for one CI job, from `gh run view --log`.

    gh run view <run-id> --log > ci-run-<run-id>.log
    ./decompose-job.py ci-run-<run-id>.log [job-name]

The log carries a timestamp per line, so step spans are first-line to last-line of that
step. A step that prints nothing therefore reads as 0s, and the span of the last step is
short by whatever ran after its final line. Both are small next to a 280s Gradle step;
neither is corrected for here, because a correction nobody can see in the log is a number
this file would be inventing.
"""
import collections
import datetime
import re
import sys

LINE = re.compile(r"^(\S+)\t(.*?)\t(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z) (.*)$")
TASK = re.compile(r"> Task (:[^\s]+)")


def parse_stamp(stamp):
    # GitHub writes 7 fractional digits; datetime accepts at most 6.
    return datetime.datetime.strptime(re.sub(r"\.(\d{6})\d*Z$", r".\1Z", stamp), "%Y-%m-%dT%H:%M:%S.%fZ")


def main(path, job="build"):
    rows = []
    for line in open(path, errors="replace"):
        match = LINE.match(line.rstrip("\n"))
        if match and match.group(1) == job:
            rows.append((match.group(2), parse_stamp(match.group(3)), match.group(4)))
    if not rows:
        sys.exit(f"no lines for job {job!r} in {path}")

    origin = rows[0][1]
    spans = {}
    for step, stamp, _ in rows:
        spans.setdefault(step, [stamp, stamp])[1] = stamp

    print(f"{'step':56} {'starts at':>10} {'span':>8}")
    for step, (start, end) in spans.items():
        print(f"{step[:56]:56} {(start - origin).total_seconds():9.0f}s {(end - start).total_seconds():7.0f}s")
    print(f"{'job, first line to last':56} {0:9.0f}s {(rows[-1][1] - origin).total_seconds():7.0f}s")

    for _, stamp, message in rows:
        if TASK.search(message) and re.search(r"compile|[Kk]sp", message):
            print(f"\nfirst compile/ksp task at +{(stamp - origin).total_seconds():.0f}s: {message.strip()[:80]}")
            break

    tasks = {TASK.search(m).group(1) for _, _, m in rows if TASK.search(m)}
    by_module = collections.Counter(t.split(":")[1] for t in tasks if t.count(":") >= 1 and t.split(":")[1])
    print(f"\n{len(tasks)} distinct tasks executed, by first path segment:")
    for module, count in by_module.most_common():
        print(f"  {module:28} {count:4}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "build")

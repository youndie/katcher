#!/usr/bin/env python3
"""Phase attribution for the Image job, from `gh run view <id> --log`.

    gh run view <run-id> --log > image-run-<id>.log
    ./decompose-image.py image-run-<id>.log [...]

Two clocks are read and they are not the same clock.

BuildKit stamps each step with `#N DONE <s>`. Those are trustworthy: they bound a step.

Inside the Gradle step every line carries `#11 <seconds-into-the-step>`, and those are NOT
per-line timestamps — they are when BuildKit flushed the batch the line arrived in. Most gaps
are 0.1s, but in the quiet stretch around the native compile the stamps land on a ~30s
heartbeat: `compileKotlinNative` → `linkReleaseExecutableNative` measured EXACTLY 30.0s in
three separate runs, every stamp ending in .5. That is the heartbeat, not the task. So this
script reports compilation and link as ONE phase, and the boundary it does trust — the first
task line, which ends a long silent stretch and is therefore flushed near when it happened —
is still only good to about the heartbeat.

Splitting compile from link needs `gradle --profile` inside the build stage and the report
copied out. That is an instrumentation change, and RQ0 requires it before any RQ4 number.

The workflow's own steps (checkout, smoke test, shutdown transcript) come from the job's
per-line timestamps, as in decompose-job.py.
"""
import re
import statistics
import sys

STEP_DONE = re.compile(r"#(\d+) DONE ([\d.]+)s")
STEP_NAME = re.compile(r"#(\d+) \[([^\]]+)\] (.*)")
IN_GRADLE = re.compile(r"#11 ([\d.]+) (.*)")


def phases(path):
    text = open(path, errors="replace").read()

    done = {n: float(s) for n, s in STEP_DONE.findall(text)}
    names = {n: f"[{stage}] {cmd[:54]}" for n, stage, cmd in STEP_NAME.findall(text)}

    marks = {}
    for seconds, message in IN_GRADLE.findall(text):
        seconds = float(seconds)
        if "> Task :" in message and "first_task" not in marks:
            marks["first_task"] = seconds
        if "downloadKotlinNativeDistribution" in message and "konan" not in marks:
            marks["konan"] = seconds
        if re.search(r"> Task :\w.*compile\w*KotlinMetadata|> Task :shared:compileKotlin", message) and "klib" not in marks:
            marks["klib"] = seconds
        if "> Task :server:compileKotlinNative" in message:
            marks["server_compile"] = seconds
        if "> Task :server:linkReleaseExecutableNative" in message:
            marks["link"] = seconds

    gradle_total = done.get("11", 0.0)
    out = []
    for n in sorted(done, key=int):
        if n == "11":
            continue
        if done[n] >= 0.5:
            out.append((names.get(n, f"#{n}"), done[n]))

    if gradle_total and marks:
        first = marks.get("first_task", 0.0)
        konan = marks.get("konan", first)
        compile_start = marks.get("server_compile", gradle_total)
        link = marks.get("link", gradle_total)
        klib = marks.get("klib", compile_start)
        out += [
            ("gradle: startup, configuration, resolution (±heartbeat)", konan),
            ("gradle: Kotlin/Native toolchain download (±heartbeat)", klib - konan),
            ("gradle: Kotlin compilation and link (one phase, see docstring)", gradle_total - klib),
        ]
    return out, gradle_total


def main(paths):
    per_phase = {}
    for path in paths:
        rows, total = phases(path)
        print(f"\n=== {path}   (gradle step {total:.0f}s)")
        for name, seconds in rows:
            print(f"  {name[:56]:56} {seconds:7.1f}s")
            per_phase.setdefault(name, []).append(seconds)
        print(f"  {'sum of the above':56} {sum(s for _, s in rows):7.1f}s")

    if len(paths) > 1:
        print(f"\n=== median over {len(paths)} runs")
        for name, values in per_phase.items():
            if len(values) == len(paths):
                print(f"  {name[:56]:56} {statistics.median(values):7.1f}s   {min(values):6.1f}–{max(values):.1f}")


if __name__ == "__main__":
    main(sys.argv[1:])

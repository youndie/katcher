---
id: B-NN
title: "One-line statement of the task"
status: open          # open | wip | done | question | dropped
priority: P1          # P0 | P1 | P2 | P3 | infra
size: M               # XS | S | S/M | M | L | XL
stage: <stage-id>     # see the stage table in backlog.md; a field, not a folder
epic: feature-<name>  # optional: the feature this belongs to
blocked_by: [B-NN]    # optional: only what actually blocks it
---

# B-NN — One-line statement of the task

What is wrong today and why it is worth fixing. One paragraph, with facts from the code rather
than generalities: which file, which behaviour, what the user sees.

- **The decision and its reason.** Not "do X" but "do X because Y". In six months the value of
  this item is the reason; the implementation is visible in the diff.
- The alternative that was rejected, and why it is worse.
- What this item deliberately does **not** cover.

- AC: an observable result. "The owner does A and sees B", not "A is implemented".
- Anchors: paths to the files this will touch (`repo/path/File.ext`).

<!--
Links:
  · "related" — an ordinary link in the text: [B-12](B-12-some-slug.md)
  · "blocks"  — the blocked_by field above; the index reads it
After editing: make fix, which regenerates the index (with copies of the checks in scripts/:
python3 scripts/backlog_index.py); then make check
-->

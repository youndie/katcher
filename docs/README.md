# docs — katcher

Crash reporting for Kotlin: a KMP client library that captures crashes and a Kotlin/Native server
that groups and shows them. The documentation here is deliberately thin, and layered by
[docs-bootstrap](https://github.com/youndie/docs-bootstrap): what exists today is research and a
backlog.

| Layer | Directory | Answers | Source of truth |
|---|---|---|---|
| Research | `research/` | *why* the build is the way it is; what was measured, what was assumed | the artefacts each fact names |
| Backlog | `backlog/` | what to do next, one file per item, with the evidence that asks for it | this repository |

There is no `features/`, `screens/`, `api/` or `services/` layer yet. How the modules fit together,
the server's lifecycle and the client's crash-capture model are in the repository's
[CLAUDE.md](../CLAUDE.md); the client's public API is in the root [README](../README.md). A layer
is added when a task needs one, not before.

**Backlog** — [backlog.md](../backlog.md): the index and the stages; the items themselves are one
file each in [`backlog/`](backlog/), cited as `[B-01](backlog/B-01-mask-credentials-in-reports.md)`.

## Conventions

- **`id`** in the frontmatter is unique and equals the filename.
- **Every item carries its evidence**: a path into the code on `main`, an issue or pull request, a
  group on the stand — and a number only with the place it was read from. A cause that has not been
  checked is labelled a hypothesis.
- Paths instead of copies: an item points at the file, it does not paste it.
- Language: English. Code identifiers verbatim as in the code.

## Templates

[`templates/backlog-item.md`](templates/backlog-item.md) — copy it for a new item.

## Checks

`make check` runs the documentation gate — exactly what the `check` workflow runs. The version of
the checks is the `uses: youndie/docs-bootstrap@<tag>` line in `.github/workflows/check.yaml`, and
nowhere else. After editing an item, `make fix` regenerates the index in `backlog.md`.

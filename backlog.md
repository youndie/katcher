# Backlog: a crash that happened is a crash on the stand, and nothing else is

> Role of this document: the product backlog. **One file per item in
> [`docs/backlog/`](docs/backlog/)** — `B-NN-<slug>.md`. What lives here is the index (generated)
> and everything that is not an item: the goal and the stages.
>
> New item: copy [`docs/templates/backlog-item.md`](docs/templates/backlog-item.md), take the next
> free `B-NN`, and run `make fix` after editing; `make check` is the gate.

## Goal

Two promises a crash tracker makes and katcher does not yet check. **That reports arrive**: native
production clients have reported nothing since 29.09, and whether that is quiet or broken is not
known, because nothing exercises the published client against a server. **That a report is safe to
show and counted once**: a crash title on the stand carried a bot-API URL with its token in clear,
and one failure through one code path opens a group per variant of its message.

The order follows the cost of being wrong. A silent reporter is the worst failure a crash tracker
has — it looks exactly like a healthy application — so delivery comes first; a stored secret comes
next, because it is already on a page and in the MCP listing; grouping, client cost and the link to
tracy after that.

Earlier history is in the issues and pull requests; this backlog starts at B-01 on 2026-11-09.

## Stages

A stage is a field on the item, not a directory. Items are cited by id, so re-prioritising an item
must never move its file.

| Stage id | Stage | What it is |
|---|---|---|
| `stage-1-delivery` | Reports arrive | A crash in a consumer built from the published client is seen on the server, by a check rather than by hope. |
| `stage-2-hygiene` | A report is safe and counted once | No credential is stored or shown; one failure is one group. |
| `stage-3-client-cost` | The client is cheap | What the client costs the application it lives in, on every target. |
| `stage-4-correlation` | A crash leads to its trace | From a crash to the request that raised it in tracy, in one hop. |

## Marks

`[ ]` open · `[~]` in progress · `[x]` done · `[?]` open question · `[-]` dropped

<!-- BEGIN INDEX -->

## Open (4)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-03](docs/backlog/B-03-native-client-smoke.md) `[?]` | A CI check sees a crash from a linked Kotlin/Native consumer arrive on the server, and the production silence is explained | P1 | M | - |
| [B-02](docs/backlog/B-02-one-failure-one-group.md) `[ ]` | One failure is one group: request data in the message no longer opens a group per variant | P2 | M | - |
| [B-05](docs/backlog/B-05-breadcrumb-zone-once.md) `[ ]` | addBreadcrumb resolves the time zone once per process, not once per call (#78) | P2 | XS | - |
| [B-04](docs/backlog/B-04-trace-id-in-report.md) `[ ]` | A report carries the trace id of the request that raised it, and the crash links to that trace in tracy | P3 | M | - |

## Closed (1)

**A report is safe and counted once**

- [B-01](docs/backlog/B-01-mask-credentials-in-reports.md) `[x]` - Mask credentials in a report on the client and again at ingest, before anything is stored or shown

<!-- END INDEX -->

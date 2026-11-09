---
id: B-04
title: "A report carries the trace id of the request that raised it, and the crash links to that trace in tracy"
status: done
priority: P3
size: M
stage: stage-4-correlation
---

# B-04 — From a crash to its trace

Nothing in katcher knows about traces: `git grep -niE 'traceId|traceparent|trace_id|spanId'` on
`main` (`b164854`) finds nothing. A crash on a Ktor service is therefore one hop short of the request
that raised it — the logs, the spans and the timings of that request are in tracy, and the only way
to find them is by time window and guesswork.

The other half exists. tracy's server agent adopts an incoming `traceparent` or starts a trace, and
runs the call inside a coroutine context element carrying `traceId`
(`youndie/tracy@4bfe2d47066ff1bc144ad9bea44b7dd8a6c3536e!/agent-ktor-server/src/commonMain/kotlin/io/github/youndie/tracy/agent/TracyPlugin.kt`,
`youndie/tracy@4bfe2d47066ff1bc144ad9bea44b7dd8a6c3536e!/agent/src/commonMain/kotlin/io/github/youndie/tracy/agent/TracyTraceContext.kt`).
tracy's backlog has the link as M-90 — crash → trace → logs → metrics window — and says it costs
tracy little and needs changes in the other two repositories
(`youndie/tracy@4bfe2d47066ff1bc144ad9bea44b7dd8a6c3536e!/BACKLOG.md`). This is the katcher change.

- **The decision and its reason.** The trace id travels as a context key, `trace.id` (32 lowercase
  hex, the W3C form), not as a new field of `CreateReportParams`
  (`shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/CreateReportParams.kt`).
  The context map is already on the wire and already stored, so a new client reporting to an old
  server, and an old client to a new one, both work; a top-level field would only work once every
  server in the field ignores unknown keys. The server's `Json` does today
  (`core/src/commonMain/kotlin/io/github/youndie/katcher/Common.kt:32`), but a context key does not
  have to rely on it.
- **katcher does not depend on tracy.** The client gets no tracy import; the consumer that runs both
  passes the id where it catches — `Katcher.catch(throwable, mapOf("trace.id" to …))` from its
  `StatusPages` handler, inside the call's coroutine where the trace element is readable. The client
  README shows the three lines. A `traceIdProvider` hook on `KatcherConfig` was considered and
  rejected for now: the fatal path runs in the unhandled-exception hook, outside any request's
  coroutine, so a provider there would return the id of nothing.
- **What the server does with it.** The group and report pages render `trace.id` as a link when the
  server is configured with a trace URL template (an env variable such as
  `KATCHER_TRACE_URL=https://tracy.example/traces/{traceId}`; unset means plain text, not a broken
  link). `get_crash_metadata` returns it as a scalar only when it matches the 32-hex shape — the MCP
  screen's rule that scalars are validated holds for it too
  (`server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/CrashMetadata.kt`) — so an agent can
  hand it to tracy's `get_trace` without reading free text.
- Not covered: the reverse link (tracy showing "this trace crashed"), and metrik — both are the
  other repositories' halves of M-90.

- AC: a server test posts a report with `context["trace.id"]` set; the report page links to the
  configured template with that id, and without the template shows it as text.
- AC: `get_crash_metadata` returns `traceId` for a 32-hex value and omits it for anything else
  (a test with a value carrying markup is the negative case).
- AC: the client README's Ktor section shows passing the trace id from a `StatusPages` handler.
- Anchors: `shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/CreateReportParams.kt`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/ui/ReportsPage.kt`,
  `server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/CrashMetadata.kt`,
  `server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/KatcherMcpServer.kt`,
  `README.md`.

## Findings

2026-11-09, done.

- `TRACE_ID_KEY` (`"trace.id"`) and `isTraceId` live in `:shared`
  (`shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/TraceId.kt`): 32 lowercase
  hex, the all-zero W3C "invalid" id refused. One definition for the consumer that writes the key and the
  server that reads it.
- The template is `ServerConfig.traceUrlTemplate`, from `KATCHER_TRACE_URL` on both targets. The module
  stores it as an application attribute (`installTraceLinks`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/TraceLinks.kt`) rather than
  threading it through every renderer: the context block is drawn from the report page and from the
  expanded row of the group page, and both already have the call. A template that is not an `http(s)` URL
  with `{traceId}` stops the start. Only a value of the W3C shape is put into a URL — the value is the
  reporting application's.
- `reportContextBlock` renders `trace.id` as a link (new tab, `noopener noreferrer`) when both hold, and
  as text otherwise. `get_crash_metadata` carries `traceId` only when `isTraceId` holds; the payload does
  not write the default, so the key is absent rather than `null` otherwise.
- `TraceLinkTest` drives the whole module through Ktor's test engine with a crash ingested by the use
  case: the report page with and without the template, and `get_crash_metadata` over `/mcp` with a valid
  id and with one carrying markup. The link and the shape check were each removed once and the matching
  tests went red. `TraceIdTest` in `:shared` holds the shape.
- README: "From a Ktor server: the crash and its trace", the `StatusPages` handler with tracy's
  `TracyTraceContext`.
- Not done here: the chart does not pass `KATCHER_TRACE_URL` yet, so on the stand the id stays text
  until a chart value is added for it. The reverse link and metrik stay with the other repositories'
  halves of M-90.

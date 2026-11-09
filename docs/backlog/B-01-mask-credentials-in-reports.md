---
id: B-01
title: "Mask credentials in a report on the client and again at ingest, before anything is stored or shown"
status: open
priority: P1
size: M
stage: stage-2-hygiene
---

# B-01 — Mask credentials in a report on the client and again at ingest

A crash title on the stand carried a bot-API URL with its token in clear: an HTTP client exception
printed the request URL into its message, and the URL of a bot API has the token in its path
(`…/bot<id>:<secret>/<method>`). Nothing between the throw and the page touched it:

- the client copies the throwable as it is — `message = throwable.message.toString()` and
  `stacktrace = throwable.stackTraceToString()` (`client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt:191-199`),
  writes it to disk (`storage.saveReport`) and uploads it;
- the ingest route deserialises and queues it (`core/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/ReportRouting.kt:23`);
- `ProcessReportUseCase.process` stores the first two lines of the stacktrace as the group title
  (`core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/ProcessReportUseCase.kt:53-58`)
  and `CrashSummary.of` keeps up to 500 characters of the header as the message
  (`core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/CrashSummary.kt:25-40`);
- the MCP screen does not catch it either. `CrashTrust` is built against injection — its
  `CREDENTIAL_PATTERNS` are *names* of secrets (`github_token`, `client_secret`,
  `authorization: bearer`; `server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/CrashTrust.kt:118-134`),
  which suggest exfiltration. A secret *value* inside a URL matches none of them, so
  `list_error_groups` hands the title to the agent unwithheld
  (`server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/KatcherMcpServer.kt:318-321`).

The same path carries every other place a client library prints a URL or a header: `user:pass@` in
a connection string, `?token=` / `?api_key=` in a query, an `Authorization` header echoed into a
message, a JWT. Breadcrumbs (`message`, `data`) and context values travel the same way
(`shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/Breadcrumb.kt`).

- **The decision and its reason.** One masking function in `:shared`, called twice: on the client
  in `Katcher.record` before `saveReport`, so the secret never reaches the device's disk or the
  wire; and on the server at the top of `ProcessReportUseCase.process`, before the fingerprint and
  the title are computed, because the ingest key ships inside applications and anyone holding it can
  post a report that never went through this client (`CrashTrust`'s own KDoc says why). One
  function in the module both sides already depend on means the two cannot disagree about what a
  secret looks like.
- **Masking by shape, not by entropy.** The shapes are known and few: a bot-API path segment, URL
  userinfo, query parameters named like credentials (`token`, `key`, `secret`, `password`,
  `access_token`, `api_key`, `sig`), `Authorization:` header values, JWTs. The secret is replaced
  and the shape kept (`…/bot<redacted>/getUpdates`, `https://<redacted>@host`), so the title still
  says *which* call failed. An entropy detector was rejected: stack traces are full of hashes,
  addresses and ids, and the grouping already has to tell those apart (`normalize`,
  `ProcessReportUseCase.kt:180-194`) — a second, fuzzier heuristic on the display path would mangle
  titles nobody asked to change.
- **What is already stored stays leaked.** Masking new reports does not unpublish the old one; a
  secret that reached the store is to be treated as leaked and rotated by whoever owns it. This item
  re-masks the stored rows so the page stops showing it, nothing more.
- Not covered: masking the fingerprint input differently from the display (the masked text is the
  input to both), and a user-configurable pattern list — wait for a second shape that the built-in
  list misses.

- AC: a test with a stacktrace whose header holds `https://api.example.org/bot123456:FAKE-test-secret/getUpdates`
  and `postgres://user:FAKE-pass@db:5432/x` produces a stored group whose title, message and report
  contain neither `FAKE-test-secret` nor `FAKE-pass`, and still contain `/getUpdates` and `db:5432`.
- AC: the same input through `Katcher.record` on the client leaves no secret in the file
  `saveReport` writes (the client's `InMemoryKatcherFileSystem` test double is enough).
- AC: two reports of one failure, one masked by the client and one unmasked from an old client,
  land in the same group.
- AC: a migration re-masks `title`, `message` and the stored stacktrace of existing groups and
  reports; running it twice changes nothing the second time.
- Anchors: `client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/ProcessReportUseCase.kt`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/CrashSummary.kt`,
  `shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/CreateReportParams.kt`,
  `server/src/commonMain/kotlin/io/github/youndie/katcher/mcp/CrashTrust.kt`,
  `server/src/commonMain/kotlin/io/github/youndie/katcher/db/Migrate.kt`,
  `client/src/commonTest/kotlin/io/github/youndie/katcher/InMemoryKatcherFileSystem.kt`.

---
id: B-05
title: "addBreadcrumb resolves the time zone once per process, not once per call (#78)"
status: open
priority: P2
size: XS
stage: stage-3-client-cost
---

# B-05 — addBreadcrumb resolves the time zone once per process (#78)

Issue #78 measured `Katcher.addBreadcrumb` on Kotlin/Native at **35 842 ns** per call against
**541 ns** on the JVM (medians, 4530 and 4560 batches), and took the expression apart: 33 138 ns of
it is `TimeZone.currentSystemDefault()`, which the JVM caches and Kotlin/Native looks up anew on
every call; with the zone held in a `val`, the same expression is 277 ns. All figures are the issue's
own. A breadcrumb is added on every user action, and the native client is iOS.

The line is still there on `main`:
`client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt:146` —
`Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())`.

The server-side half of the same mechanism (#79) was fixed by #80: one `serverZone` for the process
(`core/src/commonMain/kotlin/io/github/youndie/katcher/utils/DateUtils.kt:84`), used on the read and
render path. #79 is still open; it closes with this item.

- **The decision and its reason.** The issue's minimal option: the zone resolved once, in a `val` on
  the client object, the way `serverZone` does it on the server. One line, no API change. The price
  is the one the issue names — a process that outlives a change of the system zone keeps stamping
  breadcrumbs in the old one — and for a breadcrumb, whose purpose is the order of events before a
  crash, that is acceptable.
- Rejected: storing an `Instant` instead of a `LocalDateTime` in `Breadcrumb`
  (`shared/src/commonMain/kotlin/io/github/youndie/katcher/feature/report/Breadcrumb.kt`). It is the
  cleaner model and it changes the wire format, which is a separate decision with a migration, not a
  fix for a hot path.
- Not covered: the ring copy (`current + newBreadcrumb`, then `drop(1)`); the issue shows the variant
  with a data map costs the same, so the copy is not where the time goes.

- AC: `addBreadcrumb` on `linuxX64` (or `macosArm64`) is under 2 µs per call in a benchmark run
  the way #78 measured it, with the before and after numbers in the pull request.
- AC: no `currentSystemDefault()` remains on a per-call path in `client/src/commonMain`;
  #78 and #79 are closed by the pull request.
- Anchors: `client/src/commonMain/kotlin/io/github/youndie/katcher/Katcher.kt`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/utils/DateUtils.kt`.

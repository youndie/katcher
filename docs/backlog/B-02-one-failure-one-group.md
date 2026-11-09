---
id: B-02
title: "One failure is one group: request data in the message no longer opens a group per variant"
status: wip
priority: P2
size: M
stage: stage-2-hygiene
---

# B-02 — One failure is one group

The group key is `generateFingerprint`: exception type, the normalised head of the message, and the
symbol and file of the top five frames
(`core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/ProcessReportUseCase.kt:110-141`).
The message refines the frames on purpose — `FingerprintTest` requires two different driver errors
through identical frames to stay apart
(`server/src/commonTest/kotlin/io/github/youndie/katcher/feature/error/FingerprintTest.kt`,
"a different failure through the same driver stays a separate group"). So whatever `normalize`
leaves in the message decides the group.

**Seen on the stand, in a public application (Haul, groups 224–227, 2026-10-13):** one
`io.ktor.server.plugins.BadRequestException: Url decode failed for query parameters of <path>?<query>`
is four groups. `get_crash_metadata` on 224 and 227 returns the same top five frames symbol for
symbol and file for file; only the message differs — by the request path and query. The mechanism is
in `normalize` (`ProcessReportUseCase.kt:151-194`): `pathRegex` stops at `?` (its character class
excludes it), so `/search` becomes `<PATH>` and `?q=%zz` stays; `urlRegex` needs a dotted host, so a
bare request path is not read as a URL at all. Every distinct malformed query is a new group.

**Seen in a production consumer, cause not checked:** one driver's `IndexNotFound` is split over
about seven groups. `FingerprintTest` already holds two `IndexNotFound` dumps with different cluster
times, signatures and replicas in one group, so the split comes from something the test does not
model. *Hypothesis:* the index key or collection named in the message, or groups created before the
current normalisation — grouping by frames instead of raw message text came in `c90e628`
(2026-08-19), and a group keyed by the old hash is never matched again. The first step of this item
is to read those groups' metadata and first-seen dates on the stand and say which.

- **The decision and its reason.** Normalise what a server *receives* as well as what it prints: a
  request target — path *and* query — becomes one token, and so do quoted literals after `of`, `for`,
  `with key:`-style lead-ins where the variable part is the caller's input. The frames still carry
  the identity; the message only has to keep the *kind* of failure, which survives without the input
  that triggered it.
- **A changed fingerprint is a migration, not just a code change.** Every stored group is keyed by
  the old hash (`findByFingerprint`, `ProcessReportUseCase.kt:39-40`). Shipping a new
  `generateFingerprint` alone makes the next report of every open group a *new* group, which is the
  defect this item removes, applied to the whole store at once. So the same change recomputes the
  stored fingerprints from the stored stacktraces and merges groups that now collide: reports moved
  to the older group, occurrences summed, `resolved` kept only if every merged group was resolved.
- Rejected: dropping the message from the fingerprint whenever frames parse. It would merge
  `IndexNotFound` with `DuplicateKey` through the same driver call, which the existing test forbids
  for a reason.
- Not covered: a manual "merge these groups" action in the UI.

- AC: a `FingerprintTest` case with four hand-written `BadRequestException` messages that differ only
  in request path and query (fixtures written by hand, as the test's KDoc requires) gives one
  fingerprint; every existing case in `FingerprintTest` stays green.
- AC: the cause of the seven-way `IndexNotFound` split is written into this item, and either a test
  holds those variants in one group or a follow-up item names why they are different failures.
- AC: a migration test over a database with two groups that the new fingerprint merges ends with one
  group holding both groups' reports and their summed occurrences; a second run changes nothing.
- Anchors: `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/ProcessReportUseCase.kt`,
  `server/src/commonTest/kotlin/io/github/youndie/katcher/feature/error/FingerprintTest.kt`,
  `core/src/commonMain/kotlin/io/github/youndie/katcher/feature/error/ErrorGroupRepository.kt`,
  `server/src/commonMain/kotlin/io/github/youndie/katcher/db/Migrate.kt`.

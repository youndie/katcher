# Retractions

Claims made in the course of this research and later withdrawn. A number that is re-run and
does not reproduce belongs here, not quietly out of a table.

## 2026-09-14 — Four "findings" read off a checkout 20 commits behind

There are two katcher checkouts on the mac:

```
~/IdeaProjects/katcher      200ad6a (#38), version 0.6.2   — 20 commits behind origin/main
~/Documents/GitHub/katcher  809a27f (#63), version 0.8.2   — == origin/main
```

The first survey of the repository read the first one. Four things it reported were already
fixed on `main`:

| Reported | Actually |
|---|---|
| `publish-katcher-native.yaml` greps `^VERSION=` while `gradle.properties` says `version=`, so the image tag is empty | Fixed; the workflow greps `^version=`, and `gradle.properties` carries a comment explaining exactly this breakage |
| No root `.dockerignore`, so `COPY . .` sends `build/`, `.gradle/`, `.kotlin/` | `.dockerignore` exists and excludes them; `.git` is kept deliberately |
| Runtime image is `distroless/cc`, paired with the builder by glibc version | `FROM scratch` with a statically linked binary; the glibc-pairing rule is gone with the base image it was about |
| Group is `ru.workinprogress.katcher` | `io.github.youndie.katcher` |

**How it happened:** the session's additional working directory pointed at the stale checkout,
and nothing about reading it says it is stale. **What prevents it:** `git rev-list --count
HEAD..origin/main` in the checkout before quoting anything out of it — which is how the
discrepancy was eventually found.

## 2026-09-14 — "katcher is not in `mutagen sync list`"

Reported that katcher had no mutagen session, and that it therefore built locally on the mac
and no linuxX64 metric could be taken until a session was created.

**Wrong on every part.** The session `sync_7pu2…` already existed, already pointed at
`~/Documents/GitHub/katcher`, and the replica already held `build/` directories from earlier
builds. The claim came from `mutagen sync list | grep … | head -40`, which showed 10 of the
31 sessions; katcher was the 22nd.

Acting on the retracted claim created a second session under the same name, briefly leaving
two one-way replicas writing the same beta path. It was terminated
(`sync_Qbpj22…`); `sync_7pu2…` is the live one.

**What prevents it:** an absence is not established by a filtered listing. `mutagen sync list
<name>` answers the question that was actually being asked, and exits non-zero when the
session does not exist.

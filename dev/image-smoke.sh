#!/usr/bin/env bash
# Exercises a running katcher through the one path that `scratch` could have broken silently.
#
# The image carries no base system: no shell, no `ls`, and — until the Dockerfile copies it — no time
# zone database. `TimeZone.currentSystemDefault()` is read from the filesystem by three code paths
# that render a timestamp, and every one of them is behind authentication, so a smoke test that
# checks a status code meets the `401` and never reaches them. This script signs in, creates an app,
# sends it a crash, and asserts that the group page RENDERS A DATE — `2026-09-14 12:33` — rather
# than answering 200 with an exception page or a blank.
#
# Usage: dev/image-smoke.sh [base-url]        (default http://localhost:8080)
#
# It assumes an EMPTY DATABASE: the app it creates is app 1 and the group is group 1. Run it against
# a container that was just started, not against an installation.
set -euo pipefail

base=${1:-http://localhost:8080}
auth=(-H "X-Auth-Request-User: smoke" -H "X-Auth-Request-Email: smoke@example.test")

fail() {
    printf 'smoke: %s\n' "$1" >&2
    exit 1
}

# Unauthenticated on purpose: `/favicon.svg` is outside the `authenticate` block, so it answers
# while the rest of the surface is still refusing for want of headers.
printf 'smoke: waiting for %s\n' "$base" >&2
for _ in $(seq 1 60); do
    if curl -fsS -o /dev/null "$base/favicon.svg" 2>/dev/null; then
        break
    fi
    sleep 1
done
curl -fsS -o /dev/null "$base/favicon.svg" || fail "the server never answered on $base"

# The probes and /version, before signing in: they are outside the `authenticate` block on purpose,
# because the kubelet carries no headers. A `version:` line also means the Gradle plugin's generated
# object was compiled in rather than merely written to disk.
for probe in /health/startup /health/ready /health/live; do
    curl -fsS -o /dev/null "$base$probe" || fail "$probe did not answer 200"
done
curl -fsS "$base/version" | grep -q '^version: ' || fail "/version did not report a version"

curl -fsS "${auth[@]}" -X POST \
    --data-urlencode 'name=smoke' \
    --data-urlencode 'type=OTHER' \
    "$base/apps" > /dev/null || fail "creating an app failed"

# The card masks the key; the reveal fragment is the only response that carries it whole.
key=$(curl -fsS "${auth[@]}" "$base/apps/1/key" | grep -oE '[0-9a-f]{32}' | head -1)
[ -n "$key" ] || fail "no api key in the reveal fragment"

curl -fsS -X POST -H 'Content-Type: application/json' \
    -d "{\"appKey\":\"$key\",\"message\":\"smoke\",\"stacktrace\":\"smoke.kt:1\"}" \
    "$base/api/reports" > /dev/null || fail "the report was not accepted"

# Ingestion is queued, so the group appears a moment after the 202. The date pattern is what
# `LocalDateTime.human()` writes, and reaching it means `currentSystemDefault()` resolved.
for _ in $(seq 1 30); do
    page=$(curl -fsS "${auth[@]}" "$base/apps/1/errors/1" 2>/dev/null || true)
    if printf '%s' "$page" | grep -qE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}'; then
        printf 'smoke: rendered %s\n' \
            "$(printf '%s' "$page" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}' | head -1)" >&2
        exit 0
    fi
    sleep 1
done

fail "the group page never rendered a timestamp — the time zone database is the first suspect"

#!/usr/bin/env bash
# A Kotlin/Native consumer's crash arrives on the server — by a check, not by hope (B-03).
#
# `image-smoke.sh` proves the server takes a report that `curl` posts. Nothing proved that the
# client does: a linked native binary, built against the client as it publishes, sending over HTTP
# from its own process. This script runs `dev/sample-kotlin-native` twice against a running katcher —
# `catch` (a handled exception, uploaded in the background) and `crash` (uncaught, so the native hook
# fires and `crashUploadGrace` holds the dying process for the upload) — and requires both groups to
# render on the server.
#
# Then the negative control: the same sample linked with NO Ktor engine. The client ships none of
# its own, so such a binary used to start, keep its report on disk and retry it for ever, with the
# reason printed only under `isDebug`. It must now refuse at `start` and say why, and store nothing.
#
# Usage: dev/native-client-smoke.sh <base-url> <sample.kexe> <sample-without-engine.kexe>
#
# Safe to run after `image-smoke.sh` on the same server: it creates an app of its own and reads that
# app's id from the response rather than assuming one.
set -euo pipefail

base=${1:?base url}
sample=${2:?sample binary}
bare=${3:?sample binary linked without an engine}
auth=(-H "X-Auth-Request-User: smoke" -H "X-Auth-Request-Email: smoke@example.test")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail() {
    printf 'native-smoke: %s\n' "$1" >&2
    exit 1
}

created=$(curl -fsS "${auth[@]}" -X POST \
    --data-urlencode 'name=native-sample' \
    --data-urlencode 'type=OTHER' \
    "$base/apps") || fail "creating an app failed"
app=$(printf '%s' "$created" | grep -oE '/apps/[0-9]+' | head -1 | grep -oE '[0-9]+' || true)
[ -n "$app" ] || fail "no app id in the response to creating one"

key=$(curl -fsS "${auth[@]}" "$base/apps/$app/key" | grep -oE '[0-9a-f]{32}' | head -1)
[ -n "$key" ] || fail "no api key in the reveal fragment of app $app"

run() {
    local binary=$1 mode=$2 cache=$3
    mkdir -p "$cache"
    # `crash` exits through the unhandled-exception hook, which is a non-zero status by design.
    KATCHER_HOST="$base" KATCHER_APP_KEY="$key" KATCHER_CACHE_DIR="$cache" \
        KATCHER_WAIT_SECONDS=8 KATCHER_GRACE_SECONDS=10 \
        "$binary" "$mode" 2>&1 || true
}

printf 'native-smoke: catch\n' >&2
run "$sample" catch "$work/catch" | sed 's/^/  | /' >&2
printf 'native-smoke: crash\n' >&2
run "$sample" crash "$work/crash" | sed 's/^/  | /' >&2

# A group page carries the exception's message; the sample's two messages differ, so each page names
# which run it came from. Ingestion is queued, hence the wait.
find_group() {
    local needle=$1
    for _ in $(seq 1 30); do
        for group in $(seq 1 20); do
            if curl -fsS "${auth[@]}" "$base/apps/$app/errors/$group" 2>/dev/null | grep -q "$needle"; then
                printf '%s' "$group"
                return 0
            fi
        done
        sleep 1
    done
    return 1
}

handled=$(find_group 'Handled crash from Kotlin/Native sample') ||
    fail "the handled crash from the native sample never appeared on app $app"
printf 'native-smoke: handled crash arrived as group %s\n' "$handled" >&2
uncaught=$(find_group 'Uncaught crash from Kotlin/Native sample') ||
    fail "the uncaught crash from the native sample never appeared on app $app — the fatal path did not wait for its upload"
printf 'native-smoke: uncaught crash arrived as group %s\n' "$uncaught" >&2

printf 'native-smoke: no engine\n' >&2
said=$(run "$bare" catch "$work/bare")
printf '%s\n' "$said" | sed 's/^/  | /' >&2
printf '%s' "$said" | grep -q 'No Ktor HTTP client engine is linked' ||
    fail "a binary without an engine did not say so at start"
[ -z "$(ls -A "$work/bare")" ] ||
    fail "a binary without an engine stored a report it can never send: $(ls -A "$work/bare")"
printf 'native-smoke: a binary without an engine refused at start\n' >&2

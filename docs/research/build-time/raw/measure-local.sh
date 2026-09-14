#!/usr/bin/env bash
# Local build-time metrics for a Kotlin/Native service, per §2 of the brief.
#
#   ./docs/research/build-time/raw/measure-local.sh          # from the repository root, ON THE MAC
#   REPS=3 ./docs/research/build-time/raw/measure-local.sh
#
# THE ORCHESTRATOR RUNS ON THE MAC AND THE BUILD RUNS ON THE LINUX BOX, and that split is
# forced, not stylistic. The box holds a one-way mutagen replica whose daemon WATCHES: an edit
# made on the replica is reverted within seconds, so a script that edits the source there and
# then builds has its change taken away before the compiler reads it. The first version of this
# file did exactly that and reported `incr_rel` FASTER than `warm` — a run doing strictly more
# work finishing quicker, which is what an unnoticed no-op looks like. The source edit therefore
# happens here, on the mac, and `wsl-run` carries it over on its pre-command flush.
#
# The timed window is opened and closed ON THE BOX, around the Gradle invocation alone, so the
# flush and the ssh round trip — a second or two, the size of some of the differences being
# measured — stay outside it.
#
# Metrics, wall-clock milliseconds:
#   warm       link the release binary twice, time the second: nothing changed
#   incr_dbg   one-line change in :server → linkDebugExecutableNative
#   incr_rel   the same change          → linkReleaseExecutableNative
#
# PROFILE=1 additionally runs Gradle with --profile and keeps <tag>.profile.html beside each
# timing. Read them with parse-profile.py. The timings from a profiled run are for attribution,
# not for the results table.
#
# Reps are INTERLEAVED (warm, dbg, rel, warm, dbg, rel, …) rather than blocked, so a thermal
# ramp on this laptop lands on every metric instead of on whichever ran last.
#
# EVERY INCREMENTAL MEASUREMENT IS GUARDED. If the compile or the link task comes back
# UP-TO-DATE or FROM-CACHE, the edit did not arrive or the cache answered for it, and the run
# is printed as `void` rather than as a number. A harness that cannot tell a fast build from an
# absent one is the failure this guard exists to make loud.

set -u -o pipefail

REPS="${REPS:-3}"
SUBJECT="${SUBJECT:-server/src/commonMain/kotlin/Main.kt}"
REMOTE="${REMOTE:-\$HOME/katcher}"
WSL_RUN="${WSL_RUN:-$HOME/.claude/bin/wsl-run}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${OUT:-$PWD/docs/research/build-time/raw/local-$STAMP}"

REL=":server:linkReleaseExecutableNative"
DBG=":server:linkDebugExecutableNative"

[ -f "$SUBJECT" ] || { echo "run me from the repository root: $SUBJECT not found" >&2; exit 2; }
[ -x "$WSL_RUN" ] || { echo "no wsl-run at $WSL_RUN" >&2; exit 2; }
mkdir -p "$OUT"
cp "$SUBJECT" "$OUT/subject.orig"

probe=0
restore() { cp "$OUT/subject.orig" "$SUBJECT"; }
trap restore EXIT

# A UNIQUE TOP-LEVEL DECLARATION, NOT A COMMENT, and the difference is the whole measurement.
# A comment recompiles the file and produces a byte-identical klib, so the link task's inputs do
# not move and the Gradle build cache answers for it: the log reads
# `linkDebugExecutableNative FROM-CACHE` and the timed window contains no link at all. The first
# version of this probe was a comment; it produced a plausible 133 s only on the run that
# populated that cache entry, and `void:no-work` on every run after. A declaration changes the
# klib, so the linker actually runs — which is also what a real edit in this loop does.
edit_subject() {
    probe=$((probe + 1))
    printf '\ninternal val buildTimeProbe_%s_%s: String = "%s-%s"\n' \
        "${STAMP//-/_}" "$probe" "$STAMP" "$probe" >> "$SUBJECT"
}

# Opens and closes the timed window on the box. Prints "<ms>" on stdout, log to $OUT/<tag>.log.
remote_build() {
    local task="$1"
    local tag="$2"
    local log="$OUT/$tag.log"
    "$WSL_RUN" "cd $REMOTE && S=\$(date +%s%N) && ./gradlew $task $PROFILE_ARG --console=plain; RC=\$?; E=\$(date +%s%N); echo \"ELAPSED_MS=\$(( (E - S) / 1000000 ))\"; echo \"EXIT=\$RC\"" > "$log" 2>&1
    if [ -n "$PROFILE_ARG" ]; then
        # Reports are written on the box and the replica does not carry them back, so they are
        # fetched in a call of their own rather than looked for locally afterwards.
        "$WSL_RUN" "cd $REMOTE && cat \$(ls -t build/reports/profile/profile-*.html | head -1)" \
            > "$OUT/$tag.profile.html" 2>/dev/null || true
    fi
    local ms rc
    ms=$(grep -o 'ELAPSED_MS=[0-9]*' "$log" | tail -1 | cut -d= -f2)
    rc=$(grep -o 'EXIT=[0-9]*' "$log" | tail -1 | cut -d= -f2)
    if [ -z "${ms:-}" ] || [ "${rc:-1}" != "0" ]; then
        echo "FAILED $tag (exit ${rc:-?}) — see $log" >&2
        tail -30 "$log" >&2
        restore
        exit 1
    fi
    echo "$ms"
}

# Did the timed run actually compile AND link, or did Gradle answer from a previous state?
# Prints the reason when it did not, because "void" without a reason is a second mystery.
did_work() {
    local log="$OUT/$1.log"
    local task="$2"
    if ! grep -qE "^> Task :server:compileKotlinNative\$" "$log"; then
        echo "compile-not-run" >&2
        return 1
    fi
    if ! grep -qE "^> Task $task\$" "$log"; then
        echo "link-$(grep -oE "^> Task $task .*" "$log" | awk '{print tolower($NF)}' | tr -d '\r')" >&2
        return 1
    fi
    return 0
}

printf 'rep\tmetric\tms\tverdict\tlog\n' | tee "$OUT/results.tsv"

{
    echo "# repository   $(pwd) @ $(git rev-parse --short HEAD)"
    echo "# subject      $SUBJECT"
    echo "# started      $(date -Iseconds)"
    echo "# reps         $REPS"
    "$WSL_RUN" "cd $REMOTE && echo \"# box cores    \$(nproc)\" && free -m | awk '/^Mem:/ {print \"# box memory   \"\$2\" MiB total, \"\$3\" MiB used\"}' && echo \"# box loadavg  \$(cut -d' ' -f1-3 /proc/loadavg)\" && echo \"# box kernel   \$(uname -r)\"" 2>/dev/null
} > "$OUT/environment.txt"
cat "$OUT/environment.txt"

emit() { printf '%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5" | tee -a "$OUT/results.tsv"; }

for rep in $(seq 1 "$REPS"); do
    for metric in warm incr_dbg incr_rel; do
        case "$metric" in
            warm)
                remote_build "$REL" "r$rep-warm-settle" > /dev/null
                ms=$(remote_build "$REL" "r$rep-warm")
                # The guard is inverted here: a warm run SHOULD be all up to date.
                if did_work "r$rep-warm" "$REL" 2>/dev/null; then
                    emit "$rep" "$metric" "$ms" "void:recompiled" "r$rep-warm.log"
                else
                    emit "$rep" "$metric" "$ms" "ok" "r$rep-warm.log"
                fi
                ;;
            incr_dbg|incr_rel)
                [ "$metric" = incr_dbg ] && task="$DBG" || task="$REL"
                remote_build "$task" "r$rep-$metric-settle" > /dev/null
                edit_subject
                ms=$(remote_build "$task" "r$rep-$metric")
                restore
                reason=$(did_work "r$rep-$metric" "$task" 2>&1 >/dev/null) && verdict=ok || verdict="void:$reason"
                emit "$rep" "$metric" "$ms" "$verdict" "r$rep-$metric.log"
                ;;
        esac
    done
done

echo "# finished     $(date -Iseconds)" >> "$OUT/environment.txt"
echo >&2
echo "results  $OUT/results.tsv" >&2
echo "logs     $OUT/" >&2

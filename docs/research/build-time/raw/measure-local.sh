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
# HOW A COMMAND REACHES THE BUILD HOST. Empty means "this machine" — which is how it runs on a
# server you can edit on directly. `wsl-run` is the mutagen case, where the orchestrator has to
# stay on the mac because the replica daemon reverts an edit made on the far side.
RUNNER="${RUNNER-$HOME/.claude/bin/wsl-run}"

# WHERE THE BUILD RUNS — decided AFTER RUNNER, because it depends on it. The first version of this
# tested RUNNER above its own assignment and inverted the sense of the test as well, so local mode
# still defaulted to ~/katcher and the assertion below refused to start. Two bugs in four lines,
# both invisible until something downstream said no.
if [ -z "$RUNNER" ]; then
    REMOTE="${REMOTE:-$PWD}"
else
    REMOTE="${REMOTE:-\$HOME/katcher}"
fi
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${OUT:-$PWD/docs/research/build-time/raw/local-$STAMP}"

# PROFILE=1 adds --profile to every timed run and keeps the report beside the timing. Off by
# default: profiling perturbs what it measures, so a timing taken with it is not comparable to one
# taken without, and the two must not end up in the same table.
PROFILE_ARG=""
[ "${PROFILE:-0}" = "1" ] && PROFILE_ARG="--profile"

# GRADLE_ARGS carries the variant under test, e.g. GRADLE_ARGS="-Pkotlin.incremental.native=true".
# Passed on the command line rather than edited into gradle.properties: this tree is a mutagen
# source, and an edit here travels to the box mid-run. A variant that survives measurement becomes
# a gradle.properties line in its own PR; while it is being measured it is an argument.
GRADLE_ARGS="${GRADLE_ARGS:-}"

# METRICS selects which of the three to run. RQ1 only needs incr_dbg, and running incr_rel for it
# would spend four minutes a rep on a metric that compiler caches cannot touch.
METRICS="${METRICS:-warm incr_dbg incr_rel}"

REL=":server:linkReleaseExecutableNative"
DBG=":server:linkDebugExecutableNative"

[ -f "$SUBJECT" ] || { echo "run me from the repository root: $SUBJECT not found" >&2; exit 2; }
[ -z "$RUNNER" ] || [ -x "${RUNNER%% *}" ] || { echo "no runner at ${RUNNER%% *}" >&2; exit 2; }
# THE TREE EDITED AND THE TREE BUILT MUST BE THE SAME ONE. They were not, once: run from a second
# checkout without setting REMOTE, the probe went into ~/katcher-bench and `./gradlew` ran in
# ~/katcher, so every incremental measurement came back `void:compile-not-run`. The guard caught it,
# but a guard that reports a symptom twelve times is worse than an assertion that names the cause
# once. Only checkable in local mode — over a runner, the remote path is not this filesystem.
if [ -z "${RUNNER:-}" ]; then
    case "$REMOTE" in
        "$PWD") : ;;
        *) echo "REMOTE ($REMOTE) is not the tree holding $SUBJECT ($PWD)." >&2
           echo "The probe would be written to one checkout and the build run in another." >&2
           exit 2 ;;
    esac
fi

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
    local script="cd $REMOTE && S=\$(date +%s%N) && ./gradlew $task $GRADLE_ARGS $PROFILE_ARG --console=plain; RC=\$?; E=\$(date +%s%N); echo \"ELAPSED_MS=\$(( (E - S) / 1000000 ))\"; echo \"EXIT=\$RC\""
    if [ -n "$RUNNER" ]; then
        $RUNNER "$script" > "$log" 2>&1
    else
        sh -c "$script" > "$log" 2>&1
    fi
    if [ -n "$PROFILE_ARG" ]; then
        # Reports are written on the box and the replica does not carry them back, so they are
        # fetched in a call of their own rather than looked for locally afterwards.
        local fetch="cd $REMOTE && cat \$(ls -t build/reports/profile/profile-*.html | head -1)"
        if [ -n "$RUNNER" ]; then
            $RUNNER "$fetch" > "$OUT/$tag.profile.html" 2>/dev/null || true
        else
            sh -c "$fetch" > "$OUT/$tag.profile.html" 2>/dev/null || true
        fi
    fi
    local ms rc
    ms=$(grep -o 'ELAPSED_MS=[0-9]*' "$log" | tail -1 | cut -d= -f2)
    rc=$(grep -o 'EXIT=[0-9]*' "$log" | tail -1 | cut -d= -f2)
    if [ -z "${ms:-}" ] || [ "${rc:-1}" != "0" ]; then
        # PRINTS A SENTINEL, DOES NOT exit. This function is called inside `$( )`, and an `exit`
        # there kills the command substitution's subshell and nothing else: the script carried on
        # and emitted `3 incr_rel <empty> ok` for a build that had failed. A row with no number and
        # a verdict of ok is worse than one marked void — it reads as a measurement whose value got
        # lost rather than as a build that did not happen. The caller checks for FAIL.
        echo "FAILED $tag (exit ${rc:-?}) — see $log" >&2
        tail -30 "$log" >&2
        echo "FAIL"
        return 1
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

printf 'rep\tmetric\tms\tverdict\tload_before\tload_after\tmem_used_mb\tlog\n' | tee "$OUT/results.tsv"

{
    echo "# repository   $(pwd) @ $(git rev-parse --short HEAD)"
    echo "# subject      $SUBJECT"
    echo "# started      $(date -Iseconds)"
    echo "# reps         $REPS"
    echo "# metrics      $METRICS"
    echo "# gradle args  ${GRADLE_ARGS:-(none)}"
    echo "# profile      ${PROFILE:-0}"
    env_script="cd $REMOTE && echo \"# box cores    \$(nproc)\" && free -m | awk '/^Mem:/ {print \"# box memory   \"\$2\" MiB total, \"\$3\" MiB used\"}' && echo \"# box loadavg  \$(cut -d' ' -f1-3 /proc/loadavg)\" && echo \"# box kernel   \$(uname -r)\""
    if [ -n "$RUNNER" ]; then $RUNNER "$env_script" 2>/dev/null; else sh -c "$env_script" 2>/dev/null; fi
} > "$OUT/environment.txt"
cat "$OUT/environment.txt"

# LOAD IS RECORDED PER MEASUREMENT, NOT PER CAMPAIGN. The campaign header used to carry a single
# loadavg taken at the start, which on a shared box is the one moment it is guaranteed to be
# meaningless: a WSL baseline began at 1.66 and ended at 124 because someone else's build and a
# self-hosted runner started during it. The header said 1.66 and nothing contradicted it, while
# incr_dbg drifted 6394 -> 8590 -> 14572 and looked like a mechanism.
probe_load() { if [ -z "$RUNNER" ]; then cut -d' ' -f1 /proc/loadavg 2>/dev/null; else $RUNNER 'cut -d" " -f1 /proc/loadavg' 2>/dev/null; fi; }
probe_mem()  { if [ -z "$RUNNER" ]; then free -m 2>/dev/null | awk '/^Mem:/{print $3}'; else $RUNNER 'free -m | awk "/^Mem:/{print \$3}"' 2>/dev/null; fi; }
emit() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "${LOAD_BEFORE:-}" "$(probe_load)" "$(probe_mem)" "$5" | tee -a "$OUT/results.tsv"; }

for rep in $(seq 1 "$REPS"); do
    for metric in $METRICS; do
        LOAD_BEFORE=$(probe_load)
        case "$metric" in
            warm)
                [ "$(remote_build "$REL" "r$rep-warm-settle")" = FAIL ] && { emit "$rep" "$metric" "" "failed:settle" "r$rep-warm-settle.log"; continue; }
                ms=$(remote_build "$REL" "r$rep-warm")
                if [ "$ms" = FAIL ]; then emit "$rep" "$metric" "" "failed:build" "r$rep-warm.log"; continue; fi
                # The guard is inverted here: a warm run SHOULD be all up to date.
                if did_work "r$rep-warm" "$REL" 2>/dev/null; then
                    emit "$rep" "$metric" "$ms" "void:recompiled" "r$rep-warm.log"
                else
                    emit "$rep" "$metric" "$ms" "ok" "r$rep-warm.log"
                fi
                ;;
            incr_dbg|incr_rel)
                [ "$metric" = incr_dbg ] && task="$DBG" || task="$REL"
                [ "$(remote_build "$task" "r$rep-$metric-settle")" = FAIL ] && { emit "$rep" "$metric" "" "failed:settle" "r$rep-$metric-settle.log"; continue; }
                edit_subject
                ms=$(remote_build "$task" "r$rep-$metric")
                restore
                if [ "$ms" = FAIL ]; then emit "$rep" "$metric" "" "failed:build" "r$rep-$metric.log"; continue; fi
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

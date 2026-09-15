#!/usr/bin/env bash
# RQ1, fourth and final campaign. Three earlier ones each agreed the effect is ~48% and each broke
# the noise gate for a DIFFERENT reason, and the reasons are the finding:
#
#   1. interleaved variants  — switching a Gradle property invalidates the configuration cache,
#                              and the rebuild landed inside the timed window.
#   2. blocks on a busy box  — an hour of measuring took loadavg 0.99 -> 2.32 and memory +1.3 GB;
#                              the stand was measuring its own accumulated daemons.
#   3. blocks on a quiet box — `--stop` freed 7 GB and bought a cold-start ramp instead:
#                              A went 21556 -> 11968 -> 8190, two warm-ups not being enough.
#
# So: enough warm-up to reach steady state, and the cut declared BEFORE the run rather than chosen
# after seeing which split flatters the result. Eight reps per variant, reps 1-5 discarded as
# warm-up, reps 6-8 reported. Daemons are NOT stopped this time — a warm daemon is what a real edit
# loop has, and stopping it measures the start-up instead.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq1e
mkdir -p "$BASE"
for variant in A B; do
  case $variant in
    A) args="" ;;
    B) args="-Pkotlin.incremental.native=true" ;;
  esac
  ~/.claude/bin/wsl-run "cd \$HOME/katcher && ./gradlew :server:linkDebugExecutableNative $args --console=plain >/dev/null 2>&1; echo config-rebuilt"
  REPS=8 METRICS=incr_dbg GRADLE_ARGS="$args" OUT="$BASE/$variant" \
    ./docs/research/build-time/raw/measure-local.sh > "$BASE/$variant.stdout" 2>&1
  echo "=== $variant (reps 1-5 warm-up, 6-8 reported)"
  grep -E '^[0-9]' "$BASE/$variant/results.tsv"
  grep -E 'loadavg|memory' "$BASE/$variant/environment.txt"
done

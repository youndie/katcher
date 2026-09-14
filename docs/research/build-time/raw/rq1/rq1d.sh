#!/usr/bin/env bash
# RQ1, third campaign — on a quieted box. The second campaign's later blocks broke the §2 noise
# gate (A re-check CV 17.0%, B pooled 15.2%), and the source is in the environment lines: loadavg
# 0.99 -> 2.32 and 1.3 GB more memory in use across an hour of measuring. Gradle and Kotlin daemons
# accumulate; the stand was measuring itself as much as the variant.
#
# So: stop the daemons, let the box settle, then one block each. Blocks, not interleaving — these
# are Gradle properties and switching one invalidates the configuration cache.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq1d
mkdir -p "$BASE"

echo "--- before ---"
~/.claude/bin/wsl-run 'cut -d" " -f1-3 /proc/loadavg; free -m | awk "/^Mem:/ {print \$3\" MiB used\"}"; pgrep -c -f GradleDaemon || true; pgrep -c -f KotlinCompileDaemon || true'

~/.claude/bin/wsl-run 'cd $HOME/katcher && ./gradlew --stop >/dev/null 2>&1; pkill -f KotlinCompileDaemon >/dev/null 2>&1; sleep 45; echo stopped'

echo "--- after the stop, box settled ---"
~/.claude/bin/wsl-run 'cut -d" " -f1-3 /proc/loadavg; free -m | awk "/^Mem:/ {print \$3\" MiB used\"}"'

for variant in A B; do
  case $variant in
    A) args="" ;;
    B) args="-Pkotlin.incremental.native=true" ;;
  esac
  # Two throwaways: the first absorbs the configuration rebuild and the daemon start, the second
  # settles the incremental state so the timed runs are steady-state rather than converging.
  for w in 1 2; do
    ~/.claude/bin/wsl-run "cd \$HOME/katcher && ./gradlew :server:linkDebugExecutableNative $args --console=plain >/dev/null 2>&1; echo warm$w"
  done
  REPS=3 METRICS=incr_dbg GRADLE_ARGS="$args" OUT="$BASE/$variant" \
    ./docs/research/build-time/raw/measure-local.sh > "$BASE/$variant.stdout" 2>&1
  echo "=== $variant"; grep -E '^[0-9]' "$BASE/$variant/results.tsv"
  grep -E 'loadavg|memory' "$BASE/$variant/environment.txt"
done

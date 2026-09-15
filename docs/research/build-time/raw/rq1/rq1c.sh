#!/usr/bin/env bash
# Two questions the three-rep block left open.
#
# 1. B's values fell monotonically (5386, 4305, 3779). Incremental compilation ACCUMULATES state
#    across runs, so three samples of a converging sequence are not three samples of one quantity.
#    Six reps say whether it has settled and which number is the steady state.
# 2. The brief's red condition for RQ1 is not only slowness: "or incremental mode produces a
#    wrong/failed build in any of 3 runs". Speed was guarded; correctness was not checked at all.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq1c
mkdir -p "$BASE"

echo "=== B, six reps, continuing from its accumulated state"
REPS=6 METRICS=incr_dbg GRADLE_ARGS="-Pkotlin.incremental.native=true" OUT="$BASE/B6" \
  ./docs/research/build-time/raw/measure-local.sh > "$BASE/B6.stdout" 2>&1
grep -E '^[0-9]' "$BASE/B6/results.tsv"

echo "=== correctness under B: the native suite, three times"
for i in 1 2 3; do
  ~/.claude/bin/wsl-run "cd \$HOME/katcher && ./gradlew :server:nativeTest -Pkotlin.incremental.native=true --rerun-tasks --console=plain 2>&1 | tail -5" \
    > "$BASE/nativeTest-$i.log" 2>&1
  printf 'run %s: %s\n' "$i" "$(grep -oE 'BUILD (SUCCESSFUL|FAILED).*' "$BASE/nativeTest-$i.log" | tail -1)"
done

echo "=== A re-checked after B, to see whether the baseline drifted"
REPS=3 METRICS=incr_dbg GRADLE_ARGS="" OUT="$BASE/A2" \
  ./docs/research/build-time/raw/measure-local.sh > "$BASE/A2.stdout" 2>&1
grep -E '^[0-9]' "$BASE/A2/results.tsv"

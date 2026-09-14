#!/usr/bin/env bash
# RQ1, second attempt. BLOCKS, not interleaving — and that reverses the methodology note on
# purpose. Interleaving exists to spread thermal drift, but each of these variants is a Gradle
# PROPERTY, and switching one invalidates the configuration cache: the log says so in as many
# words. Alternating therefore charges every timed run a configuration rebuild that a real edit
# loop never pays. Build wall-clock on this box measured CV 1.2-3.0%, so drift is the smaller
# risk of the two.
#
# Each block opens with a throwaway run so the configuration rebuild lands OUTSIDE the timed
# window, and records whether the property actually reached Gradle.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq1b
mkdir -p "$BASE"
: > "$BASE/effect.txt"
for variant in A B C; do
  case $variant in
    A) args="" ;;
    B) args="-Pkotlin.incremental.native=true" ;;
    C) args="-Pkotlin.native.cacheKind=none" ;;
  esac
  # Throwaway: absorbs the configuration rebuild, and proves the property arrived.
  ~/.claude/bin/wsl-run "cd \$HOME/katcher && ./gradlew :server:linkDebugExecutableNative $args --info --console=plain 2>&1 | grep -cE \"configuration cache cannot be reused because Gradle property\"" \
    > "$BASE/$variant-switch.txt" 2>&1
  echo "$variant args='$args' config-invalidated=$(tail -1 "$BASE/$variant-switch.txt")" >> "$BASE/effect.txt"
  REPS=3 METRICS=incr_dbg GRADLE_ARGS="$args" OUT="$BASE/$variant" \
    ./docs/research/build-time/raw/measure-local.sh > "$BASE/$variant.stdout" 2>&1
  echo "=== $variant"; grep -E '^[0-9]' "$BASE/$variant/results.tsv"
done
echo "--- did the property reach Gradle? ---"; cat "$BASE/effect.txt"

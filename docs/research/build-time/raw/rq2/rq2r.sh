#!/usr/bin/env bash
# RQ2. Declared before the run:
#
#   metric   T_incr_rel   (T_incr_dbg cannot answer this: RQ0 put 79.7% of the RELEASE link in
#                          LLVM, and the debug link has no LTO at all)
#   green    >= 10% and no OOM in 3 runs
#   red      <  5%, recorded as "defaults are fine", not adopted
#   reps     4 per variant; rep 1 is warm-up, reps 2-4 are reported. Declared now, not after.
#
#   A  -Xmx5g   the value actually in effect, from ~/.gradle/gradle.properties
#   B  -Xmx10g  the box has 15 GiB and one daemon will be alive
#
# MaxMetaspaceSize=1g is held identical in both, because the baseline carries it and dropping it
# from one side would move two variables at once.
#
# The variant is passed with -D rather than written into the repository's gradle.properties: that
# file's org.gradle.jvmargs is NOT in effect here — both daemons run the home file's -Xmx5g and no
# 4G daemon exists — so editing it would have measured an absent variant.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq2r
mkdir -p "$BASE"

echo "=== stopping every daemon, so a campaign runs against one process it can name"
~/.claude/bin/wsl-run 'cd $HOME/katcher && ./gradlew --stop >/dev/null 2>&1; sleep 20; echo "daemons now: $(pgrep -c -f GradleDaemon || echo 0)"'

for variant in B A; do
  case $variant in
    A) heap="-Xmx5g" ;;
    B) heap="-Xmx10g" ;;
  esac
  args="-Dorg.gradle.jvmargs='$heap -XX:MaxMetaspaceSize=1g'"

  ~/.claude/bin/wsl-run 'cd $HOME/katcher && ./gradlew --stop >/dev/null 2>&1; sleep 20; echo stopped'
  # Throwaway: starts the daemon for this variant and absorbs the configuration rebuild.
  ~/.claude/bin/wsl-run "cd \$HOME/katcher && ./gradlew :server:linkReleaseExecutableNative $args --console=plain >/dev/null 2>&1; echo started"

  # THE RECEIPT. RQ1's cacheKind control failed for want of exactly this: proof the variant
  # reached the thing being tuned, not just the tool that passes it along.
  ~/.claude/bin/wsl-run 'pgrep -af GradleDaemon | grep -oE "^[0-9]+|-Xmx[0-9]+[a-zA-Z]|MaxMetaspaceSize=[0-9]+[a-zA-Z]" | paste -sd" " -; echo "daemon count: $(pgrep -c -f GradleDaemon)"' \
    > "$BASE/$variant-receipt.txt" 2>&1
  echo "=== $variant asked for $heap; process list says:"; cat "$BASE/$variant-receipt.txt"

  REPS=4 METRICS=incr_rel GRADLE_ARGS="$args" OUT="$BASE/$variant" \
    ./docs/research/build-time/raw/measure-local.sh > "$BASE/$variant.stdout" 2>&1
  echo "--- $variant (rep 1 warm-up, 2-4 reported)"
  grep -E '^[0-9]' "$BASE/$variant/results.tsv"
  grep -E 'loadavg|memory' "$BASE/$variant/environment.txt"

  # OOM is half the green condition and does not always fail the build loudly.
  ~/.claude/bin/wsl-run 'dmesg 2>/dev/null | tail -40 | grep -ciE "out of memory|oom-kill" || echo 0' > "$BASE/$variant-oom.txt" 2>&1
  echo "oom lines in dmesg tail: $(tail -1 "$BASE/$variant-oom.txt")"
  grep -ciE 'OutOfMemoryError|GC overhead' "$BASE/$variant"/r*-incr_rel.log 2>/dev/null | paste -sd" " - || true
done

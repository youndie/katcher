#!/usr/bin/env bash
# RQ1, interleaved. One rep of each variant per round, three rounds — never three reps of one
# variant in a block, so a thermal ramp lands on all three rather than on whichever went last.
set -u
BASE=/private/tmp/claude-501/-Users-youndie-Documents-GitHub/677415ad-8c25-47e8-88a5-90f396ac8d7c/scratchpad/rq1
mkdir -p "$BASE"
printf 'round\tvariant\tmetric\tms\tverdict\n' > "$BASE/rq1.tsv"
for round in 1 2 3; do
  for variant in A B C; do
    case $variant in
      A) args="" ;;                                       # baseline, caches default (STATIC)
      B) args="-Pkotlin.incremental.native=true" ;;        # the untested lever
      C) args="-Pkotlin.native.cacheKind=none" ;;          # control: what the default caches are worth
    esac
    out="$BASE/r$round-$variant"
    REPS=1 METRICS=incr_dbg GRADLE_ARGS="$args" OUT="$out" \
      ./docs/research/build-time/raw/measure-local.sh > "$out.stdout" 2>&1
    line=$(grep -E '^1\s+incr_dbg' "$out/results.tsv" 2>/dev/null | head -1)
    ms=$(echo "$line" | awk -F'\t' '{print $3}')
    verdict=$(echo "$line" | awk -F'\t' '{print $4}')
    printf '%s\t%s\tincr_dbg\t%s\t%s\n' "$round" "$variant" "${ms:-FAILED}" "${verdict:-FAILED}" >> "$BASE/rq1.tsv"
    echo "round $round variant $variant -> ${ms:-FAILED} ${verdict:-}"
  done
done
echo "--- results ---"
cat "$BASE/rq1.tsv"

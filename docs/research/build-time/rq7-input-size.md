# RQ7 — input size as a build-time lever

**Status: the mechanism is confirmed and the lever is not available. Red, for a reason the brief
did not anticipate.**

> Does LLVM phase time track binary size? Use razves to rank dependencies by contribution; test
> removing or replacing the top one.
> **Green:** one dependency change yields ≥ 10% on `T_incr_rel` without functional regression (test
> suite green).
> **Red:** no removable contributor ≥ 5% → "code size is not the lever for this repo".

## The mechanism: yes, almost exactly one to one

| | with MCP | without | |
|---|---|---|---|
| binary | 16,704,336 B | 13,495,008 B | **−19.2%** |
| `T_incr_rel` | 110.13 s (CV 1.0%) | 87.90 s (CV 3.8%) | **−20.2%** |

**Ratio 1.05.** Time follows size within the noise. Put beside RQ0's phase table — 79.7% of the
release link is `ModuleBitcodeOptimization`, `ObjectFiles` and `LTOBitcodeOptimization` — the
picture closes: LLVM works in proportion to the input it is given, and less input is less time in
the same proportion.

Four reps each, rep 1 discarded, the cut declared before the run. Both campaigns carry per-row load
(1.15–2.85 and 1.46–2.19 on 20 cores), so neither is a number from a contended box.

## The lever: not available, and that is the finding

The 20.2% clears the 10% threshold twice over. The green condition is not only the number:

> ...**without functional regression (test suite green)**.

The only contributor large enough to matter is **the MCP SDK** — `io.modelcontextprotocol.kotlin`,
the largest removable package in the report. It is katcher's endpoint for agents, three source
files and a protocol test. Removing it removes a feature. The suite went from 129 tests to 128 and
the tree had to be restored; it is green again at 129, which is checked rather than asserted.

**So the brief's red is right and its reason is wrong.** It expected "code size is not the lever
here". The truth is the opposite and more useful: **size is an excellent lever — mechanism
confirmed at a ratio of 1.05 — and that is exactly why it is paid for in functionality.** The
recipe should say that, not "size does not matter".

What would make this adoptable is a *smaller* MCP implementation rather than none, which is a
different piece of work with its own question: whether the protocol surface katcher actually uses
justifies a 1.28 MB SDK. That is not a build-time question and it is not answered here.

## What razves measures, and what it does not

The report ([raw/rq7-razves-report.txt](raw/rq7-razves-report.txt)) attributes
`io.modelcontextprotocol.kotlin` **1,282,567 bytes, 7.7% of the file**. Removing the dependency
took **3,209,328 bytes, 19.2%** — two and a half times more.

The difference is not an error in the tool. Package attribution is **direct** contribution: what
carries that package name. It does not include what the dependency pulls in transitively, nor what
dead-code elimination then drops once the entry points are gone.

**Rank candidates with it; do not predict the saving from it.** A table row saying 7.7% is a true
number that answers a different question than "what would removing this cost".

## What else the report says

| origin | bytes | share of attributed |
|---|---|---|
| kotlin | 6,714,617 | 60.5% |
| c | 3,554,648 | 32.0% |
| rust — the sqlx4k driver | 673,463 | 6.1% |
| cxx | 113,322 | 1.0% |
| kotlin_runtime | 40,871 | 0.4% |

Also: metadata — symbol tables and debug info — is **25.2% of the file** and is not attributed to
anyone. It is not part of what LLVM optimises, so it is size that does not cost link time. A
"shrink the binary" instinct aimed there would move the download and not the clock.

**A note carried over and corrected.** razves's own notes say Kotlin is a *minority* of a large
release binary, most of it statically linked C. That holds for the binary it was written against
and not for this one, where Kotlin is 60.5% and C is 32%. The note was applied here before it was
checked, and it was wrong.

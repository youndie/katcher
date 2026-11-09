# The documentation gate. Copied from docs-bootstrap's templates/Makefile to the root of the
# repository, next to .github/workflows/check.yaml copied from templates/workflow-check.yaml.
#
#   make check    the gate and the reports - exactly what CI runs
#   make fix      regenerate the backlog index, append missing coverage-map lines
#
# ONE VERSION OF THE CHECKS, WRITTEN DOWN ONCE: the `uses: youndie/docs-bootstrap@<ref>` line in
# .github/workflows/check.yaml. CI runs the checks at that ref because the runner resolves the line.
# This file reads the same line and fetches the same ref into .docs-bootstrap/, a directory that
# ignores itself, so `make check` here runs what CI runs - the same scripts, the same guard, the same
# flags. Renovate bumps the line, and the next `make check` fetches what CI already moved to.
#
# WHY THE SCRIPTS ARE NOT COPIED IN. A copied check runs, but at the version of the day it was copied,
# and a fix upstream never arrives: across one portfolio 18 copies of backlog_index.py were found in
# three versions, eleven of them without the guard that makes `--check` fail when the backlog has
# gone missing - a guard that existed upstream the whole time.
#
# WHY THE VERSION IS NOT ALSO WRITTEN HERE. A version pinned in the workflow and again in this file is
# two pins, and two pins drift: one is bumped, the other is found months later, and "green here, red
# there" comes back with nobody able to say which side is right. So this file holds none; if the
# workflow names two different refs, it refuses to choose.
#
# WHAT LIVES HERE is what is this repository's own: where the tree is, how the backlog is kept, and
# checks of its own under `gate`. How the documents are checked - including the guard that fails the
# gate when docs/ or the backlog is not there - is in check.mk at the pinned version, and changes
# arrive with a bump instead of with a re-copy.
#
# ONLY A GOAL THAT RUNS THE CHECKS LOADS THEM. A project adds targets of its own below this head - a
# chart, a stand, a release - and make reads every included file, fetching the ones that are
# missing, before it runs any goal at all. Included unconditionally, check.mk made each of those
# targets, `make` alone and even `make -n` read the pin and download it on a fresh clone, and fail
# offline. So it is included only when a goal asked for - on the command line, or the default goal
# when there is none - is in DOCS_BOOTSTRAP_GOALS or is one of check.mk's own `docs-` targets; every
# other goal runs without docs-bootstrap and without the network. A goal of the project's own that
# leads to the checks (`ci: check build`) is added to DOCS_BOOTSTRAP_GOALS, above the line that says
# nothing below is meant to be edited; one that is not added stops on a message naming that
# variable.
#
# OVERRIDES. `DOCS_BOOTSTRAP=<dir>` runs the checks from a directory instead of the pinned ref: a
# clone of docs-bootstrap you are changing, or - offline, or without GitHub Actions - a committed
# copy of its check.mk, scripts/ and .claude-plugin/. That last one is the copy route again, with its
# drift; it is the fallback, not the default.

DOCS ?= docs
BACKLOG ?= backlog.md
# How the backlog is kept (docs-bootstrap SKILL.md, step 7): `files` - one file per item in
# $(DOCS)/backlog/ and the generated index in $(BACKLOG); `milestones` - one hand-kept file at
# $(BACKLOG), usually BACKLOG.md; `none` - no backlog, yet.
BACKLOG_FORM ?= files
# A directory whose subdirectories are the repositories the code anchors point into. `..` is the
# directory this clone sits in - in CI, a directory holding this clone and nothing else; on a laptop,
# its siblings too, which a suffix match can mistake for this repository.
REPOS ?= ..
PY ?= python3
# The code-anchors report does not block until this is set. Set it once the report lists nothing
# and every path outside this repository is an address (SPEC 4.1): then `make check` - and CI, which
# runs it - fails on a path that resolves to nothing, in the pull request that broke it.
# `make report ANCHORS_ARGS=` runs it as a report again for one run. check.mk says when to turn it on.
#ANCHORS_ARGS ?= --check

# Where the pin is, and what it names.
DOCS_BOOTSTRAP_PIN ?= .github/workflows/check.yaml
DOCS_BOOTSTRAP_REPO ?= youndie/docs-bootstrap
DOCS_BOOTSTRAP_CACHE ?= .docs-bootstrap
# The revision of this file. check.mk says so when a newer docs-bootstrap expects a newer one.
DOCS_BOOTSTRAP_SHIM := 2

# The goals that load the checks - and so read the pin and, on a fresh clone, fetch it. check.mk's
# `docs-` targets load them by themselves. A goal of this repository's own that runs one of these
# goes here too, e.g. for `ci: check build`:
#	DOCS_BOOTSTRAP_GOALS += ci
DOCS_BOOTSTRAP_GOALS := check gate report fix

.DEFAULT_GOAL := help
.PHONY: help check gate report fix

help:
	@echo "make check   - the gate and the reports: exactly what CI runs"
	@echo "make gate    - the blocking half alone"
	@echo "make report  - non-blocking: BDD coverage, code anchors"
	@echo "make fix     - regenerate the backlog index, fill in missing coverage-map lines"

check: gate report

# Blocking. Checks of this repository's own are recipe lines of `gate`, so that CI, which runs
# `make check`, runs them too - for example, below `gate: docs-gate`:
#	$(PY) scripts/no_todo_in_main.py
gate: docs-gate

report: docs-report

fix: docs-fix

# -- where the checks come from. Nothing below is meant to be edited. ------------------------------

# The goals this run was asked for: the command line's, or the default goal when it names none.
DOCS_BOOTSTRAP_ASKED := $(or $(MAKECMDGOALS),$(.DEFAULT_GOAL))

ifneq ($(filter $(DOCS_BOOTSTRAP_GOALS) docs-%,$(DOCS_BOOTSTRAP_ASKED)),)

ifndef DOCS_BOOTSTRAP
DOCS_BOOTSTRAP_REF := $(sort $(shell sed -n -E 's|^[[:space:]]*(-[[:space:]]*)?uses:[[:space:]]*"?$(DOCS_BOOTSTRAP_REPO)@([^"[:space:]]+).*|\2|p' $(DOCS_BOOTSTRAP_PIN) 2>/dev/null))
ifeq ($(words $(DOCS_BOOTSTRAP_REF)),0)
$(error no `uses: $(DOCS_BOOTSTRAP_REPO)@<ref>` in $(DOCS_BOOTSTRAP_PIN). That line is the version of the checks, for CI and for this file alike - copy templates/workflow-check.yaml, or run with DOCS_BOOTSTRAP=<a local copy>)
endif
ifneq ($(words $(DOCS_BOOTSTRAP_REF)),1)
$(error $(DOCS_BOOTSTRAP_PIN) pins $(DOCS_BOOTSTRAP_REPO) at more than one ref: $(DOCS_BOOTSTRAP_REF). One version of the checks, one ref - make every uses: line name the same one)
endif
DOCS_BOOTSTRAP := $(DOCS_BOOTSTRAP_CACHE)/$(DOCS_BOOTSTRAP_REF)
else ifeq ($(wildcard $(DOCS_BOOTSTRAP)/check.mk),)
$(error DOCS_BOOTSTRAP=$(DOCS_BOOTSTRAP) holds no check.mk)
endif

include $(DOCS_BOOTSTRAP)/check.mk

else

# Not loaded, so no `docs-` target exists in this run. A goal that reaches one anyway is missing from
# DOCS_BOOTSTRAP_GOALS, and make's own "No rule to make target" would not say so.
docs-%:
	@echo "$@ is a target of docs-bootstrap's check.mk, which this run did not load: '$(DOCS_BOOTSTRAP_ASKED)' is not in DOCS_BOOTSTRAP_GOALS ($(strip $(DOCS_BOOTSTRAP_GOALS))). Add the goal that leads to $@ to DOCS_BOOTSTRAP_GOALS in the Makefile." >&2; exit 2

endif

# The fetch. A tarball of the ref rather than a clone: a tag, a branch and a commit SHA (what
# Renovate writes when it pins digests) are all one URL, and no history is needed. Unpacked next to
# its final place and moved in only once complete, so an interrupted fetch never leaves a directory
# that looks like a version. GNU make 3.81 - the one macOS ships - announces the missing file
# ("check.mk: No such file or directory") just before fetching it; that line is not the error.
$(DOCS_BOOTSTRAP_CACHE)/%/check.mk:
	@echo "docs-bootstrap: fetching $(DOCS_BOOTSTRAP_REPO)@$* - the ref $(DOCS_BOOTSTRAP_PIN) pins"
	@rm -rf "$(@D).part" && mkdir -p "$(@D).part"
	@curl -fsSL --retry 2 -o "$(@D).part/src.tar.gz" "https://codeload.github.com/$(DOCS_BOOTSTRAP_REPO)/tar.gz/$*" || { rm -rf "$(@D).part"; echo "could not fetch $(DOCS_BOOTSTRAP_REPO)@$* - offline, or a ref that does not exist? DOCS_BOOTSTRAP=<dir> runs a local copy instead" >&2; exit 1; }
	@tar -xzf "$(@D).part/src.tar.gz" -C "$(@D).part" --strip-components=1 && rm -f "$(@D).part/src.tar.gz"
	@test -f "$(@D).part/check.mk" || { echo "$(DOCS_BOOTSTRAP_REPO)@$* has no check.mk - versions before 0.3.0 cannot be pinned this way" >&2; rm -rf "$(@D).part"; exit 1; }
	@rm -rf "$(@D)" && mv "$(@D).part" "$(@D)"
	@echo '*' > "$(DOCS_BOOTSTRAP_CACHE)/.gitignore"

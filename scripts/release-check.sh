#!/usr/bin/env bash
# Pre-publish check of a release draft (REQ-REL-10 step 5, REQ-REL-11, AC-REL-09 [A], T1.17). Run it
# after `gh release create <tag> --verify-tag --draft ...`, and again after the tier D boot
# (RELEASING.md). It
#   1. lints the CHANGELOG and requires a dated section for the version (EC-REL-07),
#   2. extracts that section: the expected release notes,
#   3. checks the checkout: HEAD is the tag's commit (or --commit), the working tree is clean and no
#      ignored file sits under src/, where the build would pack it into the jar (REQ-REL-10 step 2),
#   4. fetches the release list with an authenticated `gh api` (drafts are invisible to anonymous
#      clients, and to accounts without push access) and keeps the one draft whose tag_name is <tag>,
#   5. runs ./gradlew releaseDraftCheck on it: the updater's own parser and selector, the asset digests
#      against the local SHA-256 and the sidecar, only jar + sidecar and both uploaded, the sidecar's
#      bytes, title, tag, draft and pre-release flags, the body, and the jar's fabric.mod.json,
#   6. prints one overall PASS or FAIL line, then ok, FAIL or skipped per step.
# Only a PASS of the fetched mode is a publish gate. With --draft-json the script checks a saved
# release object offline and skips steps 3 and 4; its PASS says so. The tier D boot of the exact jar
# is not part of it (RELEASING.md). Its files go to build/release-check/, which it empties first (an
# input inside that folder is copied out before). It never prints or stores a token: gh keeps its
# own credentials.
#
# Usage: scripts/release-check.sh <tag> [--draft-json <file>] [--changelog <file>] [--jar <file>] [--commit <rev>]
#   <tag>                v<version>, e.g. v1.1.0 (strict SemVer, no "+")
#   --draft-json <file>  check this saved release object offline instead of fetching it (tests,
#                        re-runs); never a publish gate
#   --changelog <file>   take the notes from this file (default CHANGELOG.md; the dry run uses its own)
#   --jar <file>         the uploaded jar (default build/libs/k8bas_skyblock_utility-<version>+<mc>.jar);
#                        its sidecar is <jar>.sha256
#   --commit <rev>       the commit the jar is built from (default: the tag). The dry run, which has
#                        no tag, passes HEAD.
# Exit codes: 0 PASS, 1 FAIL, 2 usage error.
set -uo pipefail

REPO=Kesuhi/K8basSkyblockUtility
# The same strict SemVer grammar as scripts/changelog-lint.sh: no empty pre-release identifier and
# no leading zero in a numeric one.
PRE_ID='(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
TAG_RE="^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-$PRE_ID(\\.$PRE_ID)*)?\$"
WORK=build/release-check

usage() {
	echo "usage: $0 <tag> [--draft-json <file>] [--changelog <file>] [--jar <file>] [--commit <rev>]" >&2
	exit 2
}

# An absolute path for a path given relative to the caller's directory.
absolute() {
	case $1 in
		/*|[A-Za-z]:[\\/]*) printf '%s' "$1" ;;
		*) printf '%s/%s' "$PWD" "$1" ;;
	esac
}

# A path Java understands: Git Bash paths (/c/...) become C:/... on Windows.
native_path() {
	if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

STEPS=() RESULTS=()
step() { STEPS+=("$1"); RESULTS+=("..."); echo "== $1" >&2; }
ok() { RESULTS[${#RESULTS[@]} - 1]="ok"; }
skipped() { STEPS+=("$1"); RESULTS+=("skipped"); echo "== $1: skipped" >&2; }

summary() { # <PASS|FAIL> [<note>]
	local i
	echo
	echo "release-check: $1 for $TAG${2:+ $2}"
	for i in "${!STEPS[@]}"; do
		printf '  [%s] %s\n' "${RESULTS[i]}" "${STEPS[i]}"
	done
}

fail() { # <problem> <what to do>
	RESULTS[${#RESULTS[@]} - 1]="FAIL"
	echo "release-check: $1" >&2
	summary FAIL
	echo "Do not publish. $2"
	exit 1
}

# The jar must come from exactly <rev>, built in a clean checkout (REQ-REL-10 step 2).
check_checkout() { # <rev>
	local want head dirty listed hidden ignored
	if ! want=$(git rev-parse --verify --quiet "$1^{commit}"); then
		echo "release-check: $1 is not a commit here (for the tag: git fetch origin --tags)" >&2
		return 1
	fi
	if ! head=$(git rev-parse HEAD); then
		echo "release-check: git rev-parse HEAD failed (see above)" >&2
		return 1
	fi
	if [[ $head != "$want" ]]; then
		echo "release-check: HEAD is $head, but the jar must be built from $1 ($want): git switch --detach $1, then ./gradlew clean build" >&2
		return 1
	fi
	# Every git call is checked: a failing one (a broken index) prints nothing, which would read as clean.
	# --untracked-files=all: with status.showUntrackedFiles=no in the user's config, git would list
	# neither untracked nor ignored files, and Gradle would still pack them.
	if ! dirty=$(git status --porcelain --untracked-files=all); then
		echo "release-check: git status failed (see above)" >&2
		return 1
	fi
	if [[ -n $dirty ]]; then
		echo "release-check: the working tree is not clean:" >&2
		printf '%s\n' "$dirty" >&2
		return 1
	fi
	# Edited files marked assume-unchanged (lowercase tag) or skip-worktree (S) look clean to git status.
	if ! listed=$(git ls-files -v); then
		echo "release-check: git ls-files failed (see above)" >&2
		return 1
	fi
	hidden=$(printf '%s\n' "$listed" | grep -E '^([a-z]|S) ' || true)
	if [[ -n $hidden ]]; then
		echo "release-check: files are marked assume-unchanged or skip-worktree, so git status cannot see edits to them:" >&2
		printf '%s\n' "$hidden" >&2
		return 1
	fi
	if ! ignored=$(git status --porcelain --ignored --untracked-files=all -- src); then
		echo "release-check: git status failed (see above)" >&2
		return 1
	fi
	if [[ -n $ignored ]]; then
		echo "release-check: ignored files under src/ would be packed into the jar; remove them and build again:" >&2
		printf '%s\n' "$ignored" >&2
		return 1
	fi
}

# The one draft whose tag_name is <tag>, as one JSON object, into <file>.
fetch_draft() { # <tag> <file>
	local found count push
	if ! command -v gh >/dev/null 2>&1; then
		echo "release-check: gh is not installed" >&2
		return 1
	fi
	if ! gh auth status --hostname github.com >/dev/null 2>&1; then
		echo "release-check: gh is not logged in to github.com (EC-REL-04); run gh auth login" >&2
		return 1
	fi
	# Without push access the release list silently leaves out every draft.
	if ! push=$(gh api "repos/$REPO" --jq .permissions.push) || [[ $push != true ]]; then
		echo "release-check: the gh account has no push access to $REPO (EC-REL-04), so it cannot see drafts" >&2
		return 1
	fi
	# The tag was checked against TAG_RE, so it is safe inside the jq string.
	if ! found=$(gh api --paginate "repos/$REPO/releases?per_page=100" \
		--jq ".[] | select(.draft == true and .tag_name == \"$1\") | tojson"); then
		echo "release-check: gh api could not list the releases of $REPO" >&2
		return 1
	fi
	count=$(printf '%s\n' "$found" | grep -c '^{' || true)
	if [[ $count -eq 0 ]]; then
		echo "release-check: no draft with tag_name $1 in $REPO (a published release is not checked here)" >&2
		return 1
	fi
	if [[ $count -gt 1 ]]; then
		echo "release-check: $count drafts have tag_name $1; delete the extra ones first" >&2
		return 1
	fi
	printf '%s\n' "$found" > "$2"
}

TAG="" draft_json="" changelog="" jar="" commit=""
while [[ $# -gt 0 ]]; do
	case $1 in
		--draft-json) [[ $# -ge 2 ]] || usage; draft_json=$(absolute "$2"); shift 2 ;;
		--changelog) [[ $# -ge 2 ]] || usage; changelog=$(absolute "$2"); shift 2 ;;
		--jar) [[ $# -ge 2 ]] || usage; jar=$(absolute "$2"); shift 2 ;;
		--commit) [[ $# -ge 2 && -n $2 ]] || usage; commit=$2; shift 2 ;;
		-*) usage ;;
		*) [[ -z $TAG ]] || usage; TAG=$1; shift ;;
	esac
done
[[ -n $TAG ]] || usage
if ! [[ $TAG =~ $TAG_RE ]]; then
	echo "release-check: '$TAG' is not a release tag: v<SemVer version>, e.g. v1.1.0 or v1.2.0-beta.1, without '+'" >&2
	exit 2
fi
if [[ -n $draft_json && -n $commit ]]; then
	echo "release-check: --commit applies to the fetched mode only, not to --draft-json" >&2
	exit 2
fi
VERSION=${TAG#v}
commit=${commit:-$TAG}

cd "$(dirname "$0")/.." || exit 2
MC=$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '\r')
if [[ -z $MC ]]; then
	echo "release-check: no minecraft_version in gradle.properties" >&2
	exit 2
fi
changelog=${changelog:-$PWD/CHANGELOG.md}
jar=${jar:-$PWD/build/libs/k8bas_skyblock_utility-$VERSION+$MC.jar}

# $WORK is emptied below: an input inside it (such as a saved draft.json) is copied out first.
STAGE=$(mktemp -d) || exit 2
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$WORK"
WORK_ABS=$(cd "$WORK" && pwd -P)
keep_input() { # <name> <path>: prints the path to read from
	local dir
	dir=$(cd "$(dirname "$2")" 2>/dev/null && pwd -P) || { printf '%s' "$2"; return; }
	case "$dir/" in
		"$WORK_ABS"/*)
			mkdir -p "$STAGE/$1"
			cp "$2" "$STAGE/$1/" 2>/dev/null
			[[ $1 != jar || ! -f "$2.sha256" ]] || cp "$2.sha256" "$STAGE/$1/"
			printf '%s' "$STAGE/$1/$(basename "$2")" ;;
		*) printf '%s' "$2" ;;
	esac
}
[[ -z $draft_json ]] || draft_json=$(keep_input draft "$draft_json")
changelog=$(keep_input changelog "$changelog")
jar=$(keep_input jar "$jar")
rm -rf "$WORK"
mkdir -p "$WORK"

step "CHANGELOG lint and its [$VERSION] section ($(basename "$changelog"))"
changelog_advice="The release notes come from the CHANGELOG: fix it on the branch (EC-REL-07), then start the release again."
bash scripts/changelog-lint.sh --require "$VERSION" "$changelog" || fail "the CHANGELOG has no valid [$VERSION] section" "$changelog_advice"
ok

step "release notes extracted to $WORK/notes.md"
bash scripts/changelog-lint.sh --extract "$VERSION" --output "$WORK/notes.md" "$changelog" \
	|| fail "the [$VERSION] section could not be extracted" "$changelog_advice"
ok

if [[ -n $draft_json ]]; then
	skipped "checkout (offline mode)"
	step "draft JSON from $(basename "$draft_json") (offline: not fetched)"
	cp "$draft_json" "$WORK/draft.json" || fail "cannot read $draft_json" "Pass a saved release object."
	ok
else
	step "checkout: HEAD is $commit, the tree is clean, no ignored files under src/"
	check_checkout "$commit" \
		|| fail "the local jar may not come from $commit in a clean checkout" "Build from the tag (RELEASING.md step 3), then run the checks again."
	ok
	step "draft JSON fetched with gh api into $WORK/draft.json"
	fetch_draft "$TAG" "$WORK/draft.json" \
		|| fail "the draft could not be fetched" "Check gh auth status, push access and that exactly one draft has tag_name $TAG."
	ok
fi

step "releaseDraftCheck: $(basename "$jar"), its sidecar and the draft"
./gradlew releaseDraftCheck --quiet --console=plain \
	"-Pk8bas.draftJson=$WORK/draft.json" "-Pk8bas.notes=$WORK/notes.md" "-Pk8bas.releaseJar=$(native_path "$jar")" \
	"-Pk8bas.releaseVersion=$VERSION" "-Pk8bas.releaseMinecraft=$MC" 2>&1 | tee "$WORK/release-draft-check.log"
if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
	if grep -q '^release draft check: FAIL' "$WORK/release-draft-check.log"; then
		fail "the draft does not match the release contract (the problems are listed above)" \
			"Fix it inside the draft (EC-REL-05), then run all checks again."
	fi
	fail "releaseDraftCheck could not check the draft: a usage, input or build error (see the lines above)" \
		"Fix the input files or the build, then run release-check.sh again. The draft itself was not checked."
fi
ok

if [[ -n $draft_json ]]; then
	summary PASS "(offline: a saved release object, not the live draft; not a publish gate)"
else
	summary PASS
	echo "Publish only after a PASS of this check that follows the tier D boot of this exact jar (RELEASING.md step 6)."
fi

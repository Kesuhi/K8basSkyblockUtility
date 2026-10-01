#!/usr/bin/env bash
# Builds every commit in <base>..<head> in a separate worktree and checks each commit's subject
# (Conventional Commits) and author e-mail (noreply only). Run before each checkpoint push; the phase
# report lists the output (AC-XC-05, AC-XC-12).
#
# Usage: scripts/build-each-commit.sh <base> [<head>]
#   e.g. scripts/build-each-commit.sh bc0f2f6
set -uo pipefail

base=${1:?usage: build-each-commit.sh <base> [<head>]}
head=${2:-HEAD}
root=$(git rev-parse --show-toplevel)
subject_re='^(feat|fix|refactor|perf|build|ci|test|docs|chore|style|revert|data)(\(.+\))?!?: '
author_re='@users\.noreply\.github\.com$'

work=$(mktemp -d "${TMPDIR:-/tmp}/k8bas-build-XXXXXX")
tree="$work/tree"
logs="$work/logs"
mkdir -p "$logs"
cleanup() { git -C "$root" worktree remove --force "$tree" >/dev/null 2>&1 || true; }
trap cleanup EXIT

commits=$(git -C "$root" rev-list --reverse "$base..$head")
if [[ -z $commits ]]; then
	echo "no commits in $base..$head"
	exit 0
fi
git -C "$root" worktree add --quiet --detach "$tree" "$base"

fail=0
for c in $commits; do
	git -C "$tree" checkout --quiet --detach "$c"
	git -C "$tree" clean -qfdx
	if (cd "$tree" && ./gradlew build --console=plain >"$logs/$c.log" 2>&1); then build=PASS; else build=FAIL; fail=1; fi
	subject=$(git -C "$root" log -1 --format=%s "$c")
	author=$(git -C "$root" log -1 --format=%ae "$c")
	[[ $subject =~ $subject_re ]] && s=ok || { s=BAD; fail=1; }
	[[ $author =~ $author_re ]] && a=ok || { a=BAD; fail=1; }
	printf '%s  build %s  subject %-3s  author %-3s  %s\n' "${c:0:10}" "$build" "$s" "$a" "$subject"
done

if [[ $fail -ne 0 ]]; then
	echo "FAILED. Build logs: $logs" >&2
	exit 1
fi
echo "all $(wc -w <<<"$commits") commits green (logs: $logs)"

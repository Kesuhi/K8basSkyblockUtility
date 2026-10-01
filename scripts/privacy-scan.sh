#!/usr/bin/env bash
# Privacy scan for added lines (REQ-XC-PRIVACY-01, AC-XC-08). It blocks:
#   - e-mail addresses other than the public noreply ones,
#   - local user paths (C:\Users\<name>, /c/Users/<name>, /home/<name>),
#   - dashed UUIDs (the all-zero UUID is allowed),
#   - Hypixel server ids: m, M, mini or mega + 1-4 digits + 1-3 capitals (the fixture placeholder m000XX
#     is allowed).
# Bare pattern mentions with no user segment, such as `C:\Users` in a document, pass.
#
# Usage:
#   scripts/privacy-scan.sh              scan the staged diff (run by .githooks/pre-commit)
#   scripts/privacy-scan.sh --self-test  check that seeded samples are blocked and allowed ones pass
set -euo pipefail

EMAIL_RE='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
EMAIL_OK='^([A-Za-z0-9._%+-]+@users\.noreply\.github\.com|noreply@anthropic\.com)$'
PATH_RE='([A-Za-z]:[\\/]+Users[\\/]+|/[A-Za-z]/Users/|/home/)[A-Za-z0-9._-]'
UUID_RE='[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
UUID_OK='^0{8}-0{4}-0{4}-0{4}-0{12}$'
SERVER_RE='\b(m|M|mini|mega)[0-9]{1,4}[A-Z]{1,3}\b'
SERVER_OK='^m000XX$'

# Reads "path<TAB>line<TAB>text" records on stdin and prints one line per violation to stderr.
# Returns 1 if anything was found.
scan_records() {
	local tmp found=0
	tmp=$(mktemp)
	cat > "$tmp"
	check() {
		local name=$1 re=$2 ok=$3 loc ln text m
		while IFS=$'\t' read -r loc ln text; do
			while IFS= read -r m; do
				[[ -z $m ]] && continue
				[[ -n $ok && $m =~ $ok ]] && continue
				echo "privacy-scan: $name at $loc:$ln: $m" >&2
				found=1
			done < <(printf '%s\n' "$text" | grep -oE -- "$re" || true)
		done < <(grep -E -- "$re" "$tmp" || true)
	}
	check "e-mail address" "$EMAIL_RE" "$EMAIL_OK"
	check "local user path" "$PATH_RE" ""
	check "UUID" "$UUID_RE" "$UUID_OK"
	check "Hypixel server id" "$SERVER_RE" "$SERVER_OK"
	rm -f "$tmp"
	return "$found"
}

# Added lines of the staged diff as records (deleted files and removed lines are not scanned).
staged_records() {
	git diff --cached -U0 --no-color --no-ext-diff --diff-filter=ACMRT | awk '
		/^\+\+\+ / { file = substr($0, 7); next }
		/^@@/ { match($0, /\+[0-9]+/); line = substr($0, RSTART + 1, RLENGTH - 1) + 0; next }
		/^\+/ { print file "\t" line "\t" substr($0, 2); line++ }
	'
}

self_test() {
	# Samples are assembled at run time so that this file itself passes the scan.
	local at='@' u='Users' h='home' fails=0 s
	local -a blocked=(
		"mail jane.doe${at}example.org"
		"path C:\\${u}\\jane\\notes.txt"
		"path C:/${u}/jane/notes.txt"
		"path /c/${u}/jane/notes.txt"
		"path /${h}/jane/notes.txt"
		"uuid 1b2c3d4e-$(printf 9f8e)-4a5b-8c7d-0123456789ab"
		"server m""183BW and mini""45C"
	)
	local -a allowed=(
		"author Kesuhi <110562470+Kesuhi${at}users.noreply.github.com>"
		"trailer noreply${at}anthropic.com"
		'bare mentions `C:\Users`, `/c/Users` and C:/Users'
		"nil uuid 00000000-0000-0000-0000-000000000000"
		"fixture line 09/26/26 m000XX RUBY_2"
		"patterns $EMAIL_RE $PATH_RE $UUID_RE $SERVER_RE"
	)
	for s in "${blocked[@]}"; do
		if printf 'self-test\t1\t%s\n' "$s" | scan_records 2>/dev/null; then
			echo "self-test FAILED: not blocked: $s" >&2; fails=1
		fi
	done
	for s in "${allowed[@]}"; do
		if ! printf 'self-test\t1\t%s\n' "$s" | scan_records; then
			echo "self-test FAILED: wrongly blocked: $s" >&2; fails=1
		fi
	done
	[[ $fails -eq 0 ]] && echo "privacy-scan self-test: ${#blocked[@]} blocked, ${#allowed[@]} allowed, all as expected"
	return "$fails"
}

case "${1:-}" in
	--self-test) self_test ;;
	"")
		if ! staged_records | scan_records; then
			echo "privacy-scan: commit blocked. Remove or sanitise the lines above (fixtures use m000XX and placeholder names)." >&2
			exit 1
		fi ;;
	*) echo "usage: $0 [--self-test]" >&2; exit 2 ;;
esac

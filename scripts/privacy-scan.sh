#!/usr/bin/env bash
# Privacy scan for added lines and file names (REQ-XC-PRIVACY-01, AC-XC-08, REQ-REL-08). It blocks:
#   - e-mail addresses other than the public noreply ones,
#   - local user paths (C:\Users\<name>, /c/Users/<name>, /home/<name>),
#   - dashed UUIDs (the all-zero UUID is allowed),
#   - Hypixel server ids: m, M, mini or mega + 1-4 digits + 1-3 capitals (the fixture placeholder m000XX
#     is allowed).
# Bare pattern mentions with no user segment, such as `C:\Users` in a document, pass. The content of
# binary files (screenshots, captures) cannot be scanned: each one gets a "binary file not scanned"
# note on stderr, without failing, and has to be checked by hand. Other players' names cannot be
# recognised by a pattern either; review the diff for them by hand (RELEASING.md).
#
# Usage:
#   scripts/privacy-scan.sh              scan the staged diff (run by .githooks/pre-commit): the added
#                                        lines and the names of added, copied and renamed files
#   scripts/privacy-scan.sh --range <base>..<head> [--body <file>]
#                                        before a PR: scan the added lines of every commit in the range
#                                        (for a merge, the lines it adds itself), the names of the files
#                                        each commit adds, every commit message, author and committer,
#                                        and the PR body file. A range without commits is an error.
#   scripts/privacy-scan.sh --body <file>
#                                        scan only a text file, such as a PR body or release notes
#   scripts/privacy-scan.sh --self-test  check that seeded samples are blocked and allowed ones pass
# Exit codes: 0 clean, 1 something found, 2 usage error, or a git or file error (nothing was scanned).
set -euo pipefail
# Byte-wise matching: GNU grep in a UTF-8 locale would drop lines with invalid UTF-8 from its output.
export LC_ALL=C

EMAIL_RE='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
EMAIL_OK='^([A-Za-z0-9._%+-]+@users\.noreply\.github\.com|noreply@anthropic\.com)$'
PATH_RE='([A-Za-z]:[\\/]+Users[\\/]+|/[A-Za-z]/Users/|/home/)[A-Za-z0-9._-]'
UUID_RE='[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
UUID_OK='^0{8}-0{4}-0{4}-0{4}-0{12}$'
SERVER_RE='\b(m|M|mini|mega)[0-9]{1,4}[A-Z]{1,3}\b'
SERVER_OK='^m000XX$'

usage() {
	echo "usage: $0 [--range <base>..<head>] [--body <file>] | --self-test" >&2
	exit 2
}

# Reads "path<TAB>line<TAB>text" records on stdin and prints one line per violation to stderr.
# Line 0 means the text is the file name itself. Returns 1 if anything was found, 2 if the records
# could not be stored or read (a full temp folder): then nothing was scanned.
scan_records() {
	local tmp found=0 failed=0
	tmp=$(mktemp) || return 2
	if ! cat > "$tmp"; then
		rm -f "$tmp"
		return 2
	fi
	check() {
		local name=$1 re=$2 ok=$3 loc ln text m where lines rc=0
		# grep exits 1 for no match and 2 for an error, which must not read as "no match".
		lines=$(grep -aE -- "$re" "$tmp") || rc=$?
		if [[ $rc -gt 1 ]]; then failed=1; return; fi
		while IFS=$'\t' read -r loc ln text; do
			[[ -z $loc ]] && continue
			if [[ $ln == 0 ]]; then where="the file name $loc"; else where="$loc:$ln"; fi
			while IFS= read -r m; do
				[[ -z $m ]] && continue
				[[ -n $ok && $m =~ $ok ]] && continue
				echo "privacy-scan: $name at $where: $m" >&2
				found=1
			done < <(printf '%s\n' "$text" | grep -aoE -- "$re" || true)
		done <<< "$lines"
	}
	check "e-mail address" "$EMAIL_RE" "$EMAIL_OK"
	check "local user path" "$PATH_RE" ""
	check "UUID" "$UUID_RE" "$UUID_OK"
	check "Hypixel server id" "$SERVER_RE" "$SERVER_OK"
	rm -f "$tmp"
	if [[ $failed -ne 0 ]]; then
		echo "privacy-scan: cannot read the records in $tmp; nothing was scanned" >&2
		return 2
	fi
	return "$found"
}

# Turns a unified or combined (merge) diff on stdin into records of its added lines, with <prefix>
# before each path. Removed lines and deleted files are not scanned. A combined diff has one prefix
# column per parent: a line is in the result if no column is "-", and added if one is "+". File
# headers are read only before a file's first hunk, so an added line starting with "++ " is content.
# A binary file has no lines to scan: it gets a note on stderr instead.
diff_records() {
	awk -v prefix="$1" '
		/^diff --cc / { header = 1; name = substr($0, 11); next }
		/^diff / { header = 1; name = ""; next }
		header && /^\+\+\+ / { file = substr($0, 7); next }
		header && /^Binary files / {
			if (name == "") { name = $0; sub(/^Binary files .* and (b\/)?/, "", name); sub(/ differ$/, "", name) }
			print "privacy-scan: note: binary file not scanned, check it by hand: " prefix name > "/dev/stderr"
			next
		}
		/^@@/ {
			header = 0
			match($0, /^@+/); columns = RLENGTH - 1
			match($0, /\+[0-9]+/); line = substr($0, RSTART + 1, RLENGTH - 1) + 0
			next
		}
		header || columns == 0 || /^\\/ { next }
		{
			mark = substr($0, 1, columns)
			if (index(mark, "-") > 0) next
			if (index(mark, "+") > 0) print prefix file "\t" line "\t" substr($0, columns + 1)
			line++
		}
	'
}

# NUL-separated file names on stdin as records at line 0, so that the name itself is scanned: an
# empty or binary file named after a UUID or an e-mail address has no line that would show it.
name_records() { # <prefix>
	tr '\0' '\n' | awk -v prefix="$1" 'length($0) > 0 { print prefix $0 "\t0\t" $0 }'
}

# Added lines and the names of added, copied and renamed files of the staged diff, as records.
# Returns 2 if a git command fails (pipefail): a partial result must never read as clean.
staged_records() {
	git diff --cached -U0 --no-color --no-ext-diff --diff-filter=ACMRT | diff_records "" || return 2
	git diff --cached --name-only -z --no-ext-diff --diff-filter=ACR | name_records "" || return 2
}

# Every commit of <range>: its author and committer, its message, the lines it adds and the names of
# the files it adds, copies or renames (for a merge, only those it adds itself), as records located
# at "<commit>:author", "<commit>:message", "<commit>:<path>". Returns 2 if a git command fails (say
# a missing object in a partial clone): a partial result must never read as clean.
range_records() {
	local c short commits
	commits=$(git rev-list --reverse "$1") || return 2
	for c in $commits; do
		short=${c:0:10}
		git log -1 --format='%an <%ae>%n%cn <%ce>' "$c" \
			| awk -v c="$short" '{ print c ":" (NR == 1 ? "author" : "committer") "\t1\t" $0 }' || return 2
		git log -1 --format=%B "$c" | awk -v c="$short" '{ sub(/\r$/, ""); print c ":message\t" NR "\t" $0 }' || return 2
		git diff-tree -p --cc -U0 --root --no-commit-id --no-color --no-ext-diff --diff-filter=ACMRT "$c" \
			| diff_records "$short:" || return 2
		git diff-tree -r --cc --name-only -z --root --no-commit-id --diff-filter=ACR "$c" \
			| name_records "$short:" || return 2
	done
}

# A text file as records, one per line (CR stripped).
file_records() {
	local n=0 line="" cr=$'\r'
	while IFS= read -r line || [[ -n $line ]]; do
		n=$((n + 1))
		printf '%s\t%d\t%s\n' "$1" "$n" "${line%"$cr"}"
	done < "$1"
}

# --range <base>..<head> and/or --body <file>, in any order.
range_main() {
	local range="" body="" base head count=0 rc=0
	while [[ $# -gt 0 ]]; do
		case $1 in
			--range) [[ $# -ge 2 && -z $range ]] || usage; range=$2; shift 2 ;;
			--body) [[ $# -ge 2 && -z $body ]] || usage; body=$2; shift 2 ;;
			*) usage ;;
		esac
	done
	if [[ -n $range ]]; then
		base=${range%%..*} head=${range#*..}
		if [[ $range != *..* || $range == *...* || -z $base || -z $head || $head == *..* ]]; then
			echo "privacy-scan: the range must be <base>..<head>, got '$range'" >&2
			exit 2
		fi
		if ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null || ! git rev-parse --verify --quiet "$head^{commit}" >/dev/null; then
			echo "privacy-scan: unknown revision in '$range'" >&2
			exit 2
		fi
		if ! count=$(git rev-list --count "$range"); then
			echo "privacy-scan: git failed while reading $range (see above); nothing was scanned" >&2
			exit 2
		fi
		if [[ $count -eq 0 ]]; then
			# A clean result for nothing would look like a green gate.
			echo "privacy-scan: the range $range has no commits (is it reversed? the form is <base>..<head>)" >&2
			exit 2
		fi
	fi
	if [[ -n $body && ! ( -f $body && -r $body ) ]]; then
		echo "privacy-scan: cannot read $body (not a readable file)" >&2
		exit 2
	fi
	# The records go through a file, so that a failing producer is seen rather than masked by the pipe.
	# It may hold personal data, so it is removed on every exit.
	REC=$(mktemp) || { echo "privacy-scan: cannot create a temp file; nothing was scanned" >&2; exit 2; }
	trap 'rm -f "$REC"' EXIT
	if [[ -n $range ]] && ! range_records "$range" > "$REC"; then
		echo "privacy-scan: git failed while reading $range (see above); nothing was scanned" >&2
		exit 2
	fi
	if [[ -n $body ]] && ! file_records "$body" >> "$REC"; then
		echo "privacy-scan: cannot read $body; nothing was scanned" >&2
		exit 2
	fi
	scan_records < "$REC" || rc=$?
	if [[ $rc -eq 2 ]]; then
		echo "privacy-scan: the records could not be stored or read (see above); nothing was scanned" >&2
		exit 2
	elif [[ $rc -ne 0 ]]; then
		echo "privacy-scan: personal data found in the lines above. Remove or sanitise it before opening the PR or publishing (RELEASING.md)." >&2
		exit 1
	fi
	echo "privacy-scan: clean (${range:+$count commit(s) in $range}${range:+${body:+, }}${body:+$body})"
}

self_test() {
	# Samples are assembled at run time so that this file itself passes the scan.
	local at='@' u='Users' h='home' fails=0 s
	local -a blocked=(
		"mail jane.doe${at}example.org"
		"latin-1 caf"$'\xe9'" next to jane.doe${at}example.org"
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
	repo_self_test || fails=1
	return "$fails"
}

# The range, body and staged modes on a throwaway repository. Commits are made with plumbing (no
# hooks run) and the user's git config is not read.
repo_self_test() {
	local repo errors fails=0 cases=0 at='@' h='home' uuid
	repo=$(mktemp -d)
	errors=$(mktemp)
	uuid="1b2c3d4e-$(printf 9f8e)-4a5b-8c7d-0123456789ab"
	(
		set -euo pipefail
		unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_PREFIX
		export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL="$repo/no-global-config"
		export GIT_AUTHOR_NAME=Test GIT_COMMITTER_NAME=Test
		export GIT_AUTHOR_EMAIL="1+test${at}users.noreply.github.com" GIT_COMMITTER_EMAIL="1+test${at}users.noreply.github.com"
		cd "$repo"
		git -c init.defaultBranch=main init --quiet .
		git config core.autocrlf false
		commit() { # <message> <parent>...: commits the index, prints the id
			local message=$1 tree p
			local -a parents=()
			shift
			for p in "$@"; do parents+=(-p "$p"); done
			tree=$(git write-tree)
			git commit-tree "$tree" "${parents[@]}" -m "$message"
		}
		# <expected exit> <name> <stderr fragment, or ""> <args...>: a failing case must fail for the
		# reason named by the fragment. No args: the staged mode.
		expect() {
			local want=$1 name=$2 fragment=$3 got=0
			shift 3
			cases=$((cases + 1))
			( main "$@" ) >/dev/null 2>"$errors" || got=$?
			if [[ $got != "$want" ]]; then
				echo "self-test FAILED: $name: exit $got, expected $want" >&2; fails=1
			elif [[ -n $fragment ]] && ! grep -qF -- "$fragment" "$errors"; then
				echo "self-test FAILED: $name: stderr has no \"$fragment\": $(head -c 400 "$errors")" >&2; fails=1
			fi
		}
		printf 'clean line\n' > a.txt
		git add a.txt
		c1=$(commit "feat: first")
		printf 'contact jane.doe%sexample.org\n' "$at" > b.txt
		git add b.txt
		c2=$(commit "feat: add b" "$c1")
		printf 'more\n' >> a.txt
		git add a.txt
		c3=$(commit "fix: notes in /${h}/jane/notes.txt" "$c2")
		printf '++ looks like a header, user%sexample.org\n' "$at" > c.txt
		git add c.txt
		c4=$(commit "feat: c" "$c3")
		printf 'clean b\n' > b.txt
		git add b.txt
		c5=$(commit "fix: drop the address" "$c4")
		c6=$(GIT_AUTHOR_EMAIL="jane.doe${at}example.org" commit "chore: personal author" "$c5")
		# Personal data only in a file name: a clean file named after a UUID, an empty file named
		# after an e-mail address, and a binary file that cannot be scanned.
		mkdir caps
		printf 'clean\n' > "caps/$uuid.json"
		git add caps
		c7=$(commit "test: a capture" "$c6")
		: > "caps/jane.doe${at}example.org.txt"
		git add caps
		c8=$(commit "test: an empty capture" "$c7")
		printf 'PNG\0\1\2' > shot.png
		git add shot.png
		c9=$(commit "docs: a screenshot" "$c8")
		# A side branch and three merges of it: a clean one, one that adds a line itself and one
		# that adds a file named after a UUID itself.
		git read-tree "$c1"
		printf 'x\n' > x.txt
		git add x.txt
		x1=$(commit "feat: x" "$c1")
		git read-tree "$c5"
		git checkout-index -f -a
		git add x.txt
		m1=$(commit "Merge x" "$c5" "$x1")
		printf 'resolved on mini%s\n' 45C >> a.txt
		git add a.txt
		m2=$(commit "Merge x again" "$c5" "$x1")
		git read-tree "$m1"
		: > "merge-$uuid.txt"
		git add "merge-$uuid.txt"
		m3=$(commit "Merge x a third time" "$c5" "$x1")
		printf 'Summary\r\nAll clean.\r\n' > body-clean.md
		printf 'Player uuid %s\n' "$uuid" > body-uuid.md

		expect 0 "clean commit, removed line not scanned" "" --range "$c4..$c5"
		expect 1 "added e-mail" "e-mail address at ${c2:0:10}:b.txt:1:" --range "$c1..$c2"
		expect 1 "path in a commit message" "local user path at ${c3:0:10}:message:1:" --range "$c2..$c3"
		expect 1 "added line that starts with ++" "e-mail address at ${c4:0:10}:c.txt:1:" --range "$c3..$c4"
		expect 1 "personal author e-mail" "e-mail address at ${c6:0:10}:author:1:" --range "$c5..$c6"
		expect 1 "UUID in a file name" "UUID at the file name ${c7:0:10}:caps/$uuid.json:" --range "$c6..$c7"
		expect 1 "e-mail address in the name of an empty file" \
			"e-mail address at the file name ${c8:0:10}:caps/jane.doe${at}example.org.txt:" --range "$c7..$c8"
		expect 0 "binary file noted, not failed" "binary file not scanned, check it by hand: ${c9:0:10}:shot.png" --range "$c8..$c9"
		expect 0 "clean merge" "" --range "$c5..$m1"
		expect 1 "line added by a merge" "Hypixel server id at ${m2:0:10}:a.txt:3:" --range "$c5..$m2"
		expect 1 "file name added by a merge" "UUID at the file name ${m3:0:10}:merge-$uuid.txt:" --range "$c5..$m3"
		expect 0 "clean body" "" --body body-clean.md
		expect 1 "UUID in the body" "UUID at body-uuid.md:1:" --body body-uuid.md
		expect 1 "clean range, UUID in the body" "UUID at body-uuid.md:1:" --range "$c4..$c5" --body body-uuid.md
		expect 0 "clean range and body" "" --body body-clean.md --range "$c4..$c5"
		expect 2 "empty range" "has no commits" --range "$c5..$c5"
		expect 2 "reversed range" "has no commits (is it reversed?" --range "$c5..$c4"
		expect 2 "range without .." "must be <base>..<head>" --range "$c5"
		expect 2 "symmetric range" "must be <base>..<head>" --range "$c4...$c5"
		expect 2 "unknown revision" "unknown revision" --range "$c4..no-such-ref"
		expect 2 "missing body file" "cannot read" --body no-such-file.md
		expect 2 "option without a value" "usage:" --range
		expect 2 "unknown option" "usage:" --staged
		# A git failure (here a missing object, as in a partial clone) must not read as clean.
		printf 'lost jane.doe%sexample.org\n' "$at" > lost.txt
		git add lost.txt
		c10=$(commit "feat: lost" "$c9")
		lost=$(git hash-object lost.txt)
		git rm -q --cached lost.txt
		rm -f lost.txt ".git/objects/${lost:0:2}/${lost:2}"
		expect 2 "git failure while reading the range" "git failed while reading" --range "$c9..$c10"
		# A missing commit object in the middle of the range fails the commit count already.
		git read-tree "$c9"
		d1=$(commit "feat: d1" "$c9")
		d2=$(commit "feat: d2" "$d1")
		rm -f ".git/objects/${d1:0:2}/${d1:2}"
		expect 2 "missing commit inside the range" "git failed while reading" --range "$c9..$d2"
		expect 2 "body that is a directory" "not a readable file" --body caps

		# The staged mode (the pre-commit hook), against HEAD = c5.
		git update-ref refs/heads/main "$c5"
		git read-tree "$c5"
		printf 'staged and clean\n' > s1.txt
		git add s1.txt
		expect 0 "staged: clean" ""
		printf 'PNG\0\3' > staged.png
		git add staged.png
		expect 0 "staged: binary file noted, not failed" "binary file not scanned, check it by hand: staged.png"
		printf 'mail jane.doe%sexample.org\n' "$at" > s2.txt
		git add s2.txt
		expect 1 "staged: added e-mail" "e-mail address at s2.txt:1:"
		git read-tree "$c5"
		printf 'clean\n' > "s-$uuid.txt"
		git add "s-$uuid.txt"
		expect 1 "staged: UUID in a file name" "UUID at the file name s-$uuid.txt:"
		git read-tree "$c5"
		git checkout-index -f -a
		git mv a.txt "mini""45C.txt"
		expect 1 "staged: server id in a renamed file's name" "Hypixel server id at the file name mini""45C.txt:"
		[[ $fails -eq 0 ]] && echo "privacy-scan self-test (range, body and staged mode): $cases cases as expected"
		exit "$fails"
	) || fails=1
	rm -rf "$repo" "$errors"
	return "$fails"
}

# The staged diff, as .githooks/pre-commit runs it.
staged_main() {
	local rc=0
	REC=$(mktemp) || { echo "privacy-scan: commit blocked: cannot create a temp file, so nothing was scanned." >&2; exit 2; }
	trap 'rm -f "$REC"' EXIT
	if ! staged_records > "$REC"; then
		echo "privacy-scan: commit blocked: git failed while reading the staged diff (see above), so nothing was scanned." >&2
		exit 2
	fi
	scan_records < "$REC" || rc=$?
	if [[ $rc -eq 2 ]]; then
		echo "privacy-scan: commit blocked: the staged diff could not be scanned." >&2
		exit 2
	elif [[ $rc -ne 0 ]]; then
		echo "privacy-scan: commit blocked. Remove or sanitise the lines above (fixtures use m000XX and placeholder names)." >&2
		exit 1
	fi
}

main() {
	case "${1:-}" in
		--self-test) [[ $# -eq 1 ]] || usage; self_test ;;
		--range|--body) range_main "$@" ;;
		"") [[ $# -eq 0 ]] || usage; staged_main ;;
		*) usage ;;
	esac
}

main "$@"

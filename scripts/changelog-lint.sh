#!/usr/bin/env bash
# CHANGELOG lint and release-notes extraction (REQ-REL-05, AC-REL-04, EC-REL-07). The file contract:
#   - it starts with "# Changelog" and an intro paragraph,
#   - then exactly one "## [Unreleased]" section, which comes first and may be empty,
#   - then one "## [x.y.z] - YYYY-MM-DD" section per release (strict SemVer, optional pre-release
#     part, no build metadata, a real calendar date), newest first, no version twice, none empty,
#   - inside a section the only "### " headings are Added, Changed, Deprecated, Removed, Fixed and
#     Security, each at most once; free paragraphs and bold lead-in lines are allowed anywhere.
#   - no other headings: no "####" or deeper heading, and no setext heading (a line of only "=" or
#     "-" right after a text line; a thematic break needs a blank line before it),
#   - no link reference definitions ("[x]: url"): the Keep a Changelog link footer would become part
#     of the last section's release notes. Use inline links.
# A release body is every line after the version's "## " heading up to (not including) the next
# "## " heading, with leading and trailing blank lines removed. Lines inside ``` or ~~~ fences are
# never headings. CRLF input is read as LF.
#
# Usage:
#   scripts/changelog-lint.sh [<file>]                      lint the file (default: CHANGELOG.md)
#   scripts/changelog-lint.sh --require <version> [<file>]  also require a dated section for <version>
#   scripts/changelog-lint.sh --extract <version> [--output <notes>] [<file>]
#                                                           lint, then print that release body (LF,
#                                                           no BOM, trailing newline) on stdout, or
#                                                           write it to <notes> (no shell redirect, so
#                                                           PowerShell cannot re-encode it). <notes> is
#                                                           written only on success; on a failure an
#                                                           old <notes> is removed.
#   scripts/changelog-lint.sh --self-test                   check seeded good and bad files
# Exit codes: 0 ok, 1 lint failure (messages on stderr), 2 usage error.
set -uo pipefail
export LC_ALL=C

RELEASE_RE='^## \[([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?)\] - ([0-9]{4})-([0-9]{2})-([0-9]{2})$'
UNRELEASED='## [Unreleased]'
PRE_ID='(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
SEMVER_RE="^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-$PRE_ID(\\.$PRE_ID)*)?\$"
SUBSECTION_RE='^### (Added|Changed|Deprecated|Removed|Fixed|Security)$'
FENCE_OPEN_RE='^ {0,3}(```+|~~~+)'
FENCE_CLOSE_RE='^ {0,3}(```+|~~~+)[[:space:]]*$'
BLANK_RE='^[[:space:]]*$'
H1_RE='^#([[:space:]]|$)'
H2_RE='^##([[:space:]]|$)'
H3_RE='^###([[:space:]]|$)'
H4_RE='^#{4,6}([[:space:]]|$)'
ATX_RE='^#{1,6}([[:space:]]|$)'
INDENTED_RE='^ {1,3}#{1,6}([[:space:]]|$)'
TITLE_RE=$'^(\xef\xbb\xbf)?#([[:space:]]|$)'
# Conservative: any line of only "=" or "-" after a text line, at any indent, also inside a list.
SETEXT_RE='^[[:space:]]*(=+|-+)[[:space:]]*$'
LINK_REF_RE='^ {0,3}\[[^]]+\]:'

usage() {
	echo "usage: $0 [--require <version> | --extract <version> [--output <notes>]] [<file>] | --self-test" >&2
	exit 2
}

# 0 if <year>-<month>-<day> (decimal, leading zeros allowed) is a date of the Gregorian calendar.
is_calendar_date() {
	local y=$((10#$1)) m=$((10#$2)) d=$((10#$3)) last
	(( m >= 1 && m <= 12 && d >= 1 )) || return 1
	case $m in
		2) if (( (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 )); then last=29; else last=28; fi ;;
		4|6|9|11) last=30 ;;
		*) last=31 ;;
	esac
	(( d <= last ))
}

# Numbers without leading zeros, of any length: prints -1, 0 or 1.
num_cmp() {
	if (( ${#1} != ${#2} )); then
		(( ${#1} < ${#2} )) && echo -1 || echo 1
	elif [[ $1 < $2 ]]; then echo -1
	elif [[ $1 > $2 ]]; then echo 1
	else echo 0
	fi
}

# SemVer 2.0.0 precedence of two valid versions without build metadata: prints -1, 0 or 1.
semver_cmp() {
	local a=$1 b=$2 ap="" bp="" c i x y
	local -a A B
	[[ $a == *-* ]] && ap=${a#*-}
	[[ $b == *-* ]] && bp=${b#*-}
	IFS=. read -r -a A <<<"${a%%-*}"
	IFS=. read -r -a B <<<"${b%%-*}"
	for i in 0 1 2; do
		c=$(num_cmp "${A[i]}" "${B[i]}")
		[[ $c != 0 ]] && { echo "$c"; return; }
	done
	if [[ -z $ap || -z $bp ]]; then
		# A version without a pre-release part ranks above the same version with one.
		if [[ -z $ap && -z $bp ]]; then echo 0; elif [[ -z $ap ]]; then echo 1; else echo -1; fi
		return
	fi
	IFS=. read -r -a A <<<"$ap"
	IFS=. read -r -a B <<<"$bp"
	for ((i = 0; i < ${#A[@]} && i < ${#B[@]}; i++)); do
		x=${A[i]} y=${B[i]}
		if [[ $x =~ ^[0-9]+$ && $y =~ ^[0-9]+$ ]]; then c=$(num_cmp "$x" "$y")
		elif [[ $x =~ ^[0-9]+$ ]]; then c=-1
		elif [[ $y =~ ^[0-9]+$ ]]; then c=1
		elif [[ $x < $y ]]; then c=-1
		elif [[ $x > $y ]]; then c=1
		else c=0
		fi
		[[ $c != 0 ]] && { echo "$c"; return; }
	done
	num_cmp "${#A[@]}" "${#B[@]}"
}

# Reads <file> into LINES (CR stripped) and fills the section tables; prints problems to stderr
# and sets ERRORS to their number.
#   SEC_NAME[k]   "Unreleased", the version, or "" for a "## " heading that does not match
#   SEC_LINE[k]   index of the heading line in LINES
#   SEC_END[k]    index of the next "## " heading (or the line count)
lint_file() {
	local file=$1 i n line fence="" in_intro=1 intro_text=0 k=-1 version year month day prev="" c start=1
	local after_text=0 text_line
	local -A seen_version=() seen_sub=()
	LINES=() SEC_NAME=() SEC_LINE=() SEC_END=() ERRORS=0
	err() { echo "changelog-lint: $file:$1" >&2; ERRORS=$((ERRORS + 1)); }

	local cr=$'\r'
	mapfile -t LINES < "$file"
	LINES=("${LINES[@]%"$cr"}")
	n=${#LINES[@]}
	if (( n == 0 )); then
		err "1: the file is empty"
		return
	fi
	if [[ ${LINES[0]} == $'\xef\xbb\xbf'* ]]; then
		err "1: the file starts with a UTF-8 BOM"
	elif [[ ${LINES[0]} != "# Changelog" ]]; then
		err "1: the file must start with \"# Changelog\""
	fi

	# A file without a title is still read from its first line.
	[[ ${LINES[0]} =~ $TITLE_RE ]] || start=0
	for ((i = start; i < n; i++)); do
		line=${LINES[i]}
		# Whether the line before this one was text (not blank, not a heading, not a fence line).
		text_line=$after_text after_text=0
		if [[ -n $fence ]]; then
			if [[ $line =~ $FENCE_CLOSE_RE && ${BASH_REMATCH[1]:0:1} == "${fence:0:1}" ]] \
				&& (( ${#BASH_REMATCH[1]} >= ${#fence} )); then
				fence=""
			fi
			continue
		fi
		if [[ $line =~ $FENCE_OPEN_RE ]]; then
			fence=${BASH_REMATCH[1]}
			(( in_intro )) && intro_text=1
			continue
		fi
		if (( text_line )) && [[ $line =~ $SETEXT_RE ]]; then
			err "$((i + 1)): a line of only \"=\" or \"-\" right after a text line is a setext heading; only \"## \" sections and \"### \" subsections are allowed (put a blank line before a thematic break)"
			continue
		fi
		if [[ $line =~ $LINK_REF_RE ]]; then
			err "$((i + 1)): link reference definitions are not allowed, because a link footer would become part of the last section's release notes; use an inline link: $line"
			continue
		fi
		if [[ $line =~ $INDENTED_RE ]]; then
			err "$((i + 1)): indented heading: $line"
			continue
		fi
		if ! [[ $line =~ $BLANK_RE || $line =~ $ATX_RE ]]; then
			after_text=1
		fi
		if [[ $line =~ $H1_RE ]]; then
			err "$((i + 1)): only the first line may be a \"# \" heading: $line"
		elif [[ $line =~ $H2_RE ]]; then
			if (( in_intro && ! intro_text )); then
				err "$((i + 1)): no intro paragraph between \"# Changelog\" and the first section"
			fi
			in_intro=0
			(( k >= 0 )) && SEC_END[k]=$i
			k=$((k + 1))
			SEC_LINE[k]=$i
			SEC_END[k]=$n
			seen_sub=()
			if [[ $line == "$UNRELEASED" ]]; then
				SEC_NAME[k]="Unreleased"
				if (( k > 0 )); then
					err "$((i + 1)): [Unreleased] must be the first section, and there must be only one"
				fi
			elif [[ $line =~ $RELEASE_RE ]]; then
				version=${BASH_REMATCH[1]} year=${BASH_REMATCH[3]} month=${BASH_REMATCH[4]} day=${BASH_REMATCH[5]}
				SEC_NAME[k]=$version
				if (( k == 0 )); then
					err "$((i + 1)): the first section must be \"$UNRELEASED\""
				fi
				if ! [[ $version =~ $SEMVER_RE ]]; then
					err "$((i + 1)): \"$version\" is not a valid SemVer version (leading zero or empty identifier)"
				elif [[ -n ${seen_version[$version]:-} ]]; then
					err "$((i + 1)): version $version is listed twice (first at line ${seen_version[$version]})"
				else
					if [[ -n $prev ]]; then
						c=$(semver_cmp "$version" "$prev")
						if [[ $c != -1 ]]; then
							err "$((i + 1)): versions must be newest first: $version comes after $prev"
						fi
					fi
					prev=$version
					seen_version[$version]=$((i + 1))
				fi
				if ! is_calendar_date "$year" "$month" "$day"; then
					err "$((i + 1)): the date is not a calendar date: $line"
				fi
			else
				SEC_NAME[k]=""
				err "$((i + 1)): a section heading must be \"$UNRELEASED\" or \"## [x.y.z] - YYYY-MM-DD\": $line"
			fi
		elif [[ $line =~ $H3_RE ]]; then
			if (( in_intro )); then
				err "$((i + 1)): subsection before the first section: $line"
			elif ! [[ $line =~ $SUBSECTION_RE ]]; then
				err "$((i + 1)): only Added, Changed, Deprecated, Removed, Fixed and Security subsections are allowed: $line"
			elif [[ -n ${seen_sub[$line]:-} ]]; then
				err "$((i + 1)): \"$line\" appears twice in this section"
			else
				seen_sub[$line]=1
			fi
		elif [[ $line =~ $H4_RE ]]; then
			err "$((i + 1)): only \"## \" sections and \"### \" subsections are allowed; use a bold lead-in line instead of a level-4 or deeper heading: $line"
		elif (( in_intro )) && ! [[ $line =~ $BLANK_RE ]]; then
			intro_text=1
		fi
	done
	if [[ -n $fence ]]; then
		err "$n: a code fence ($fence) is never closed"
	fi
	if (( k < 0 )); then
		err "$n: no \"$UNRELEASED\" section"
		return
	fi
	if [[ ${SEC_NAME[0]} != "Unreleased" ]]; then
		local has=0
		for ((i = 0; i <= k; i++)); do [[ ${SEC_NAME[i]} == "Unreleased" ]] && has=1; done
		(( has )) || err "$((SEC_LINE[0] + 1)): no \"$UNRELEASED\" section"
	fi
	for ((i = 0; i <= k; i++)); do
		if [[ -n ${SEC_NAME[i]} && ${SEC_NAME[i]} != "Unreleased" ]] && ! body_has_text "$i"; then
			err "$((SEC_LINE[i] + 1)): the [${SEC_NAME[i]}] section is empty"
		fi
	done
}

body_has_text() {
	local i
	for ((i = SEC_LINE[$1] + 1; i < SEC_END[$1]; i++)); do
		[[ ${LINES[i]} =~ $BLANK_RE ]] || return 0
	done
	return 1
}

# The index of the section for <version>, or nothing.
section_of() {
	local i
	for ((i = 0; i < ${#SEC_NAME[@]}; i++)); do
		[[ ${SEC_NAME[i]} == "$1" ]] && { echo "$i"; return; }
	done
}

print_body() {
	local from=$((SEC_LINE[$1] + 1)) to=$((SEC_END[$1] - 1)) i
	while (( from <= to )) && [[ ${LINES[from]} =~ $BLANK_RE ]]; do from=$((from + 1)); done
	while (( to >= from )) && [[ ${LINES[to]} =~ $BLANK_RE ]]; do to=$((to - 1)); done
	for ((i = from; i <= to; i++)); do printf '%s\n' "${LINES[i]}"; done
}

main() {
	local mode=lint version="" file="" output=""
	case "${1:-}" in
		--require|--extract)
			mode=${1#--}
			[[ $# -ge 2 && -n ${2:-} ]] || usage
			version=$2
			shift 2 ;;
		-*) usage ;;
	esac
	if [[ ${1:-} == --output ]]; then
		[[ $mode == extract && $# -ge 2 && -n ${2:-} && ${2:-} != -* ]] || usage
		output=$2
		shift 2
	fi
	[[ $# -le 1 ]] || usage
	file=${1:-CHANGELOG.md}
	[[ $file != -* ]] || usage
	if [[ -n $version ]] && ! [[ $version =~ $SEMVER_RE ]]; then
		echo "changelog-lint: \"$version\" is not a SemVer version (x.y.z or x.y.z-pre, no \"v\", no \"+\")" >&2
		exit 2
	fi
	if [[ -n $output && -e $output && $output -ef $file ]]; then
		echo "changelog-lint: --output must not be the CHANGELOG itself" >&2
		exit 2
	fi
	if [[ ! -f $file || ! -r $file ]]; then
		echo "changelog-lint: cannot read $file" >&2
		[[ -z $output ]] || rm -f -- "$output"
		exit 2
	fi

	lint_file "$file"
	local idx=""
	if [[ -n $version ]]; then
		idx=$(section_of "$version")
		if [[ -z $idx ]]; then
			echo "changelog-lint: $file: no \"## [$version] - YYYY-MM-DD\" section (EC-REL-07: stop before creating the draft)" >&2
			ERRORS=$((ERRORS + 1))
		fi
	fi
	if (( ERRORS > 0 )); then
		echo "changelog-lint: $file: $ERRORS problem(s)" >&2
		# A notes file from an earlier run must never be uploaded by mistake.
		[[ -z $output ]] || rm -f -- "$output"
		exit 1
	fi
	case $mode in
		extract)
			if [[ -z $output ]]; then
				print_body "$idx"
			elif ! print_body "$idx" > "$output"; then
				echo "changelog-lint: cannot write $output" >&2
				rm -f -- "$output"
				exit 2
			else
				echo "changelog-lint: $file: ok, the [$version] release body is in $output" >&2
			fi ;;
		require) echo "changelog-lint: $file: ok, [$version] present" >&2 ;;
		*) echo "changelog-lint: $file: ok" >&2 ;;
	esac
	exit 0
}

self_test() {
	local tmp fails=0 cases=0
	tmp=$(mktemp -d)
	trap 'rm -rf "$tmp"' RETURN
	# <expected exit code> <name> <stderr fragment, or ""> <args...>: runs main in a subshell. A
	# failing case must fail for the reason named by the fragment.
	expect() {
		local want=$1 name=$2 fragment=$3 got=0
		shift 3
		cases=$((cases + 1))
		( cd "$tmp" && main "$@" ) >/dev/null 2>"$tmp/stderr" || got=$?
		if [[ $got != "$want" ]]; then
			echo "self-test FAILED: $name: exit $got, expected $want" >&2; fails=1
		elif [[ -n $fragment ]] && ! grep -qF -- "$fragment" "$tmp/stderr"; then
			echo "self-test FAILED: $name: stderr has no \"$fragment\": $(head -c 400 "$tmp/stderr")" >&2; fails=1
		fi
	}
	# <name> <expected file> <args...>: the extraction must equal the expected bytes.
	expect_output() {
		local name=$1 want=$2
		shift 2
		cases=$((cases + 1))
		if ! ( cd "$tmp" && main "$@" ) > "$tmp/out" 2>/dev/null || ! cmp -s "$tmp/out" "$want"; then
			echo "self-test FAILED: $name: output differs from $want" >&2; fails=1
		fi
	}
	# <name> <command...>: a plain check, counted as a case.
	expect_true() {
		local name=$1
		shift
		cases=$((cases + 1))
		"$@" || { echo "self-test FAILED: $name" >&2; fails=1; }
	}
	mk() { cat > "$tmp/$1"; }
	intro() { printf '# Changelog\n\nAll notable changes are listed here.\n\n'; }

	# Today's layout: only an [Unreleased] section, with content.
	{ intro; cat <<'EOF'; } | mk current.md
## [Unreleased]

**Requirements:** Minecraft 26.2.

### Changed

**Not ported cleanly / behaviour changes**

- **Behaviour change: one.** *Reason:* r. *Effect:* e.

### Added

- A thing.
EOF
	# The release layout: an empty [Unreleased], then [1.1.0], with CRLF line endings, a fenced block
	# and a Markdown line break (two trailing spaces, written as <SP><SP> here) that must survive.
	{ intro; cat <<'EOF'; } | sed 's/<SP>/ /g; s/$/\r/' > "$tmp/release.md"
## [Unreleased]

## [1.1.0] - 2026-10-01


**Requirements:** Minecraft 26.2.

**Upgrading from 1.0.1:** replace the jar.

### Changed

- Changed, with a trailing double space<SP><SP>
  and a fenced block:

```
## not a heading
### Nor this
#### Nor this
Nor a setext heading
---
[Nor]: a link reference
```

### Fixed

- Fixed.


## [1.0.1] - 2026-03-01

- Older entry.

EOF
	printf '%s\n' '**Requirements:** Minecraft 26.2.' '' '**Upgrading from 1.0.1:** replace the jar.' '' '### Changed' '' \
		'- Changed, with a trailing double space  ' '  and a fenced block:' '' '```' '## not a heading' '### Nor this' \
		'#### Nor this' 'Nor a setext heading' '---' '[Nor]: a link reference' '```' '' \
		'### Fixed' '' '- Fixed.' > "$tmp/release-1.1.0.txt"
	printf '%s\n' '- Older entry.' > "$tmp/release-1.0.1.txt"
	# Many versions, pre-releases ordered by SemVer precedence, not by text.
	{ intro; cat <<'EOF'; } | mk order.md
## [Unreleased]

- Next.

## [2.0.0] - 2027-01-02

- a

## [2.0.0-rc.10] - 2027-01-01

- a

## [2.0.0-rc.2] - 2026-12-20

- a

## [2.0.0-beta] - 2026-12-10

- a

## [1.10.0] - 2026-11-01

- a

## [1.9.0] - 2026-10-20

- a
EOF

	expect 0 "current layout" "" current.md
	expect 0 "release layout" "" release.md
	expect 0 "SemVer order" "" order.md
	expect 0 "require 1.1.0" "[1.1.0] present" --require 1.1.0 release.md
	expect 0 "require a pre-release" "[2.0.0-rc.2] present" --require 2.0.0-rc.2 order.md
	expect 1 "require on the current layout" 'no "## [1.1.0] - YYYY-MM-DD" section' --require 1.1.0 current.md
	expect 1 "require a missing version" 'no "## [9.9.9] - YYYY-MM-DD" section' --require 9.9.9 release.md
	expect 1 "extract a missing version" 'no "## [1.2.0] - YYYY-MM-DD" section' --extract 1.2.0 release.md
	expect_output "extract 1.1.0 (CRLF in, LF out, trimmed)" "$tmp/release-1.1.0.txt" --extract 1.1.0 release.md
	expect_output "extract the last section" "$tmp/release-1.0.1.txt" --extract 1.0.1 release.md

	# --output writes the same bytes, and only on success.
	expect 0 "extract to a file" "release body is in notes.md" --extract 1.1.0 --output notes.md release.md
	expect_true "the file holds the release body" cmp -s "$tmp/notes.md" "$tmp/release-1.1.0.txt"
	expect 1 "extract a missing version to a file" 'no "## [1.2.0] - YYYY-MM-DD" section' --extract 1.2.0 --output notes.md release.md
	expect_true "a failed extraction removes the old file" test ! -e "$tmp/notes.md"

	# Good variants of release.md.
	good() { # <name> <sed expression applied to release.md>
		sed "$2" "$tmp/release.md" > "$tmp/good-$1.md"
		expect 0 "good: $1" "" "good-$1.md"
	}
	good leap-day 's/2026-10-01/2028-02-29/;s/2026-03-01/2028-01-31/'
	good leap-century 's/2026-10-01/2000-02-29/;s/2026-03-01/2000-01-31/'
	good thematic-break 's/^- Fixed\./- Fixed.\n\n---/'
	good break-after-heading 's/^### Fixed/### Fixed\n---/'
	good inline-link 's/^- Fixed\./- Fixed, see [the issue](https:\/\/example.org\/1)./'

	# Bad files: each breaks one rule of the contract, and must fail for that reason.
	bad() { # <name> <sed expression applied to release.md> <stderr fragment>
		sed "$2" "$tmp/release.md" > "$tmp/bad-$1.md"
		expect 1 "bad: $1" "$3" "bad-$1.md"
		expect 1 "bad (extract): $1" "$3" --extract 1.1.0 "bad-$1.md"
	}
	bad title 's/^# Changelog/# Change log/' 'must start with "# Changelog"'
	bad bom '1s/^/\xef\xbb\xbf/' 'starts with a UTF-8 BOM'
	bad no-brackets 's/^## \[1\.1\.0\] - /## 1.1.0 - /' 'a section heading must be'
	bad no-date 's/^## \[1\.1\.0\] - 2026-10-01/## [1.1.0]/' 'a section heading must be'
	bad bad-month 's/2026-10-01/2026-13-01/' 'not a calendar date'
	bad day-zero 's/2026-10-01/2026-10-00/' 'not a calendar date'
	bad february-30 's/2026-10-01/2026-02-30/;s/2026-03-01/2026-01-31/' 'not a calendar date'
	bad february-29 's/2026-10-01/2026-02-29/;s/2026-03-01/2026-01-31/' 'not a calendar date'
	bad february-29-century 's/2026-10-01/2100-02-29/' 'not a calendar date'
	bad april-31 's/2026-10-01/2026-04-31/' 'not a calendar date'
	bad short-date 's/2026-10-01/2026-10-1/' 'a section heading must be'
	bad trailing-space 's/^## \[1\.1\.0\] - 2026-10-01/& /' 'a section heading must be'
	bad v-prefix 's/^## \[1\.1\.0\]/## [v1.1.0]/' 'a section heading must be'
	bad leading-zero 's/^## \[1\.1\.0\]/## [1.01.0]/' 'is not a valid SemVer version'
	bad build-metadata 's/^## \[1\.1\.0\]/## [1.1.0+26.2]/' 'a section heading must be'
	bad other-subsection 's/^### Fixed/### Notes/' 'only Added, Changed, Deprecated, Removed, Fixed and Security'
	bad lowercase-subsection 's/^### Fixed/### fixed/' 'only Added, Changed, Deprecated, Removed, Fixed and Security'
	bad twice-subsection 's/^### Fixed/### Changed/' '"### Changed" appears twice'
	bad level-four 's/^### Fixed/#### Anything/' 'level-4 or deeper heading'
	bad level-six 's/^- Fixed\./###### Fixed/' 'level-4 or deeper heading'
	bad setext-h2 's/^- Fixed\./Notes\n---/' 'is a setext heading'
	bad setext-h1 's/^- Fixed\./Other Title\n===========/' 'is a setext heading'
	bad setext-in-list 's/^- Fixed\./- Fixed.\n  -/' 'is a setext heading'
	bad link-footer '$a [Unreleased]: https://github.com/o/r/compare/v1.1.0...HEAD\r\n[1.1.0]: https://github.com/o/r/releases/tag/v1.1.0\r' 'link reference definitions are not allowed'
	bad link-in-section 's/^- Fixed\./- Fixed [1].\n\n[1]: https:\/\/example.org\/1/' 'link reference definitions are not allowed'
	bad unreleased-twice 's/^## \[1\.0\.1\] - 2026-03-01/## [Unreleased]/' '[Unreleased] must be the first section'
	bad unreleased-missing '/^## \[Unreleased\]/d' 'the first section must be "## [Unreleased]"'
	bad unreleased-lowercase 's/^## \[Unreleased\]/## [unreleased]/' 'a section heading must be'
	bad old-on-top 's/^## \[1\.0\.1\]/## [1.2.0]/' 'versions must be newest first: 1.2.0 comes after 1.1.0'
	bad duplicate 's/^## \[1\.0\.1\]/## [1.1.0]/' 'version 1.1.0 is listed twice'
	bad pre-above-release 's/^## \[1\.1\.0\]/## [1.1.0-rc.1]/;s/^## \[1\.0\.1\]/## [1.1.0]/' 'versions must be newest first: 1.1.0 comes after 1.1.0-rc.1'
	bad empty-release '/^- Older entry\./d' 'the [1.0.1] section is empty'
	bad level-one '/^- Older entry\./s/^/# /' 'only the first line may be a "# " heading'
	bad indented 's/^### Fixed/ ### Fixed/' 'indented heading'
	bad open-fence '/^\[Nor\]/,/^```/d' 'is never closed'
	bad no-intro '/^All notable/d' 'no intro paragraph'
	bad subsection-in-intro 's/^All notable.*/### Added/' 'subsection before the first section'

	# Usage errors.
	expect 2 "unknown option" "usage:" --lint release.md
	expect 2 "require without a version" "usage:" --require
	expect 2 "not a SemVer version" "is not a SemVer version" --require 1.1 release.md
	expect 2 "tag instead of version" "is not a SemVer version" --extract v1.1.0 release.md
	expect 2 "two files" "usage:" release.md order.md
	expect 2 "missing file" "cannot read" "$tmp/does-not-exist.md"
	expect 2 "--output without --extract" "usage:" --require 1.1.0 --output notes.md release.md
	expect 2 "--output without a file" "usage:" --extract 1.1.0 --output
	expect 2 "--output onto the CHANGELOG" "must not be the CHANGELOG itself" --extract 1.1.0 --output release.md release.md

	[[ $fails -eq 0 ]] && echo "changelog-lint self-test: $cases cases as expected"
	return "$fails"
}

if [[ ${1:-} == --self-test ]]; then
	[[ $# -eq 1 ]] || usage
	self_test
	exit $?
fi
main "$@"

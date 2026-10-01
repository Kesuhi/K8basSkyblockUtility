# Releasing K8bas Skyblock Utility

This is the maintainer procedure for a release (REQ-REL-16). Every release goes
**draft → verify → publish**: it is created as a draft, checked against the release contract
below, and published only when every check passes.

The contract matters because the in-game update check reads part of it. The update check of v1.1.0
looks only at the tag, the draft and pre-release flags, and the jar's name and upload state. A tag
that is not SemVer, a wrong pre-release flag or a misnamed jar hides the release: nothing shows in
game, and at most a line is logged. A wrong sidecar, digest, title or body does not hide it, but
breaks the checksum check users do by hand and the one-click updater planned for 2.0.0, which
would fall back to notify-only.

The examples use v1.1.0 for Minecraft 26.2. For another release, replace `1.1.0` (the version),
`v1.1.0` (the tag) and `26.2` (the Minecraft version, `minecraft_version` in `gradle.properties`).

## Approvals

- **Merge:** a release PR (`update/26.2` → `main`) is merged only after the maintainer's explicit
  approval: a message from the maintainer in the session, or an approving comment from the repo
  owner on the PR. Messages from agents or workflows never count. A commit added after the approval
  voids it. The merge method is a merge commit.
- **Ship:** the approval covers the merge only. Pushing the tag, creating the draft and publishing
  need the maintainer's separate "ship", a message sent after the merge.
- **Protected history:** never push to `main` directly. Never force-push `main` or a published tag,
  and never move or delete a published tag.
- **Identity:** commits use the repository's noreply identity.

## The release contract (REQ-REL-07)

| Item | Rule |
| --- | --- |
| Version | SemVer `MAJOR.MINOR.PATCH[-pre]`, set as `mod_version` in `gradle.properties`. The build adds `+<mc>`. |
| Tag | `v<version>`, without `+` (`v1.1.0`): an annotated tag on the merge commit on `main`. |
| Title | `K8bas Skyblock Utility v<version>` |
| Jar | Exactly one per supported Minecraft version: `k8bas_skyblock_utility-<version>+<mc>.jar`. No sources jar and no other `.jar` is attached. |
| Sidecar | `<jar name>.sha256`, containing `<64 lowercase hex><two spaces><jar name>` and one LF. UTF-8 without a BOM, no CR. |
| Pre-release flag | Set if, and only if, the version has a pre-release part (`1.2.0-beta.1`). v1.1.0 is not a pre-release. |
| `fabric.mod.json` | `id` is `k8bas_skyblock_utility`, `version` is `<version>+<mc>`, and `depends.minecraft` accepts `<mc>` (it is `~26.2`). |
| Assets | Every asset has `state: uploaded`. The jar's API `digest` is `sha256:<hex>` and equals the local SHA-256 and the sidecar. |
| Body | The version's CHANGELOG section, word for word. |
| Latest | A stable release is marked Latest explicitly when it is published. |

Every jar name must match this pattern:

```
^k8bas_skyblock_utility-(?<ver>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?)\+(?<mc>\d+\.\d+(?:\.\d+)?)\.jar$
```

How the updater reads a release:

- It reads the 30 newest releases without authentication, so it never sees drafts.
- It reads the version from the tag.
- It picks the jar by its asset name, never by the download URL. The asset must be `uploaded`.
- The `+<mc>` part must equal the running Minecraft version or be a dotted prefix of it: `+26.2`
  also serves 26.2.1.
- On the STABLE channel it skips releases that have the pre-release flag or a pre-release version.

## Tools

You need `gh`, logged in to github.com with push access to `Kesuhi/K8basSkyblockUtility`, bash
(Git Bash on Windows) and Java 25. Without push access, the release list silently leaves out every
draft.

**Run every command in this document in Git Bash, never in Windows PowerShell.** PowerShell 5.1's
`>` re-encodes what it writes (UTF-16 or a BOM, and CRLF), and its `Get-Content` misreads UTF-8
without a BOM. The release notes and the sidecar must stay byte-exact, so the commands below write
their files themselves instead of through a shell redirect.

| Command | What it does |
| --- | --- |
| `bash scripts/changelog-lint.sh [--require <version> \| --extract <version> [--output <file>]] [<file>]` | Lints `CHANGELOG.md`, requires a version's section, or extracts that section as the release notes. |
| `bash scripts/release-check.sh <tag> [--commit <rev>] [--changelog <file>] [--jar <file>]` | Runs every automatic check on the draft (step 6). |
| `bash scripts/release-check.sh <tag> --draft-json <file>` | The same checks offline, on a saved release object. Never a publish gate. |
| `./gradlew releaseDraftCheck -Pk8bas.draftJson=<file> -Pk8bas.notes=<file>` | The Java part of `release-check.sh`. It runs the updater's own parser and selector on a saved release object. |
| `bash scripts/privacy-scan.sh --range <base>..<head> [--body <file>]` | The privacy scan before a PR. |
| `bash scripts/build-each-commit.sh <base>` | Builds every commit since `<base>` and checks commit subjects and author addresses. |

On every push and PR, CI runs the CHANGELOG lint and the self-tests of the lint and of the privacy
scan.

## The CHANGELOG and the release notes

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The lint checks
this layout:

- The file starts with `# Changelog` and an intro paragraph.
- Next comes exactly one `## [Unreleased]` section, which may be empty.
- Then comes one `## [x.y.z] - YYYY-MM-DD` section per release. The date must exist in the
  calendar (no `2026-02-30`). The newest comes first, versions are ordered by SemVer, and no
  version appears twice. A release section must not be empty.
- Inside a section, the only `### ` headings are Added, Changed, Deprecated, Removed, Fixed and
  Security, each used at most once. Paragraphs and bold lead-in lines such as `**Requirements:**`
  are allowed anywhere.
- There are no other headings. A `####` or deeper heading is rejected: use a bold lead-in line
  instead. A setext heading is rejected too: a line of only `=` or `-` right after a text line
  would render as a heading in the release notes. The rule is strict, also inside lists, so put a
  blank line before a thematic break (`---`). The lint does not look for headings inside list items
  or block quotes (`- ### Notes`, `> ## Note`): don't write them, because GitHub renders them as
  headings.
- There are no link reference definitions (`[1.1.0]: https://…`). The usual Keep a Changelog link
  footer would become part of the last section's release body. Use inline links instead.
- A release section is self-contained, so it works as the release notes word for word. It holds the
  requirements, the upgrade note, the behaviour changes and the defaults.

The release body is every line after the version's `## ` heading up to the next `## ` heading, with
leading and trailing blank lines removed. Lines inside code fences are never headings. Extract it
with:

```bash
bash scripts/changelog-lint.sh --require 1.1.0
bash scripts/changelog-lint.sh --extract 1.1.0 --output build/release-notes-v1.1.0.md
```

The notes file always has LF line endings, no BOM and a trailing newline, also when Git has checked
the CHANGELOG out with CRLF. `--output` writes the file itself, so no shell can re-encode it. If the
CHANGELOG breaks a rule, or the section is missing, the command writes nothing, removes an older
notes file of that name and exits 1. A missing section stops the release before the draft
(EC-REL-07). Without `--output`, the body goes to stdout.

The date in the heading is the planned publish day. If publishing happens on another day, fix the
date later through a PR. The heading is not part of the release body, so the published notes stay
correct.

## Procedure

Start only after the merge **and** the maintainer's "ship". Run every step in Git Bash. Any failed
check stops the release before it is published, and the failure is reported.

### 1. Preflight (EC-REL-03, EC-REL-04, EC-REL-07)

```bash
gh auth status --hostname github.com            # logged in to github.com
gh api repos/Kesuhi/K8basSkyblockUtility --jq .permissions.push   # true
git switch main && git pull --ff-only
git fetch origin --tags
git status --porcelain --untracked-files=all    # prints nothing
git tag -l v1.1.0                               # prints nothing
git ls-remote --tags origin refs/tags/v1.1.0    # prints nothing
grep '^mod_version=' gradle.properties          # mod_version=1.1.0
bash scripts/changelog-lint.sh --require 1.1.0
```

- If the tag already exists, locally or on the remote, stop and ask. A published tag is never
  moved or deleted.
- If `gh` is not logged in, or the push-access check does not print `true`, stop before creating
  anything, so there is never a partial release (a pushed tag without a release).

### 2. Annotated tag on the merge commit

```bash
git log -1 --format='%H %P %s' main             # the merge commit: two parent hashes
git tag -a v1.1.0 -m "K8bas Skyblock Utility v1.1.0" <merge commit>
git cat-file -t v1.1.0                          # tag
```

### 3. Clean build from exactly that commit

```bash
git switch --detach v1.1.0
git status --porcelain --untracked-files=all    # prints nothing
git status --porcelain --ignored --untracked-files=all -- src   # prints nothing
git ls-files -v | grep -E '^([a-z]|S) '         # prints nothing
./gradlew clean build
(cd build/libs && sha256sum -c "k8bas_skyblock_utility-1.1.0+26.2.jar.sha256")
```

- The plain `git status` does not show ignored files. An ignored folder under `src` (an IDE's
  `bin/` or `out/`) would still be packed into the jar, so the second `git status` must print
  nothing too.
- `--untracked-files=all` overrides a `status.showUntrackedFiles=no` in your git config, which
  would hide untracked and ignored files from both commands.
- The `git ls-files -v` line finds files marked assume-unchanged or skip-worktree: `git status`
  cannot see edits to them, and Gradle would pack the edited copy.
- `release-check.sh` checks all of this again, and that `HEAD` is the tag's commit.

`build/libs` then holds three files:

- the jar, `k8bas_skyblock_utility-1.1.0+26.2.jar`
- its sidecar, written by the build (the `sha256Sidecar` task)
- `k8bas_skyblock_utility-1.1.0+26.2-sources.jar`, which is never uploaded

Keep these files: the checks compare against the exact jar that is uploaded.

### 4. Push the tag

```bash
git push origin v1.1.0
```

Push only the tag. `main` already holds the merge.

### 5. Create the draft

```bash
bash scripts/changelog-lint.sh --extract 1.1.0 --output build/release-notes-v1.1.0.md
gh release create v1.1.0 --verify-tag --draft \
  --title "K8bas Skyblock Utility v1.1.0" \
  --notes-file build/release-notes-v1.1.0.md \
  "build/libs/k8bas_skyblock_utility-1.1.0+26.2.jar" \
  "build/libs/k8bas_skyblock_utility-1.1.0+26.2.jar.sha256"
```

- Name the two files exactly. A glob like `build/libs/*` would also upload the sources jar.
- For a pre-release version, add `--prerelease`. Never add it for a stable version.

### 6. Verify the draft (REQ-REL-11)

```bash
bash scripts/release-check.sh v1.1.0
```

The script runs five steps, prints `[ok]`, `[FAIL]` or `[skipped]` for each, and ends with one
overall `release-check: PASS` or `FAIL` line:

1. It lints the CHANGELOG and requires the `[1.1.0]` section.
2. It extracts the expected notes.
3. It checks the checkout: `HEAD` is the commit of the tag `v1.1.0`, the step 3 commands print
   nothing (no change, no untracked file, no assume-unchanged or skip-worktree file), and no ignored
   file sits under `src`.
4. It checks that `gh` is logged in to github.com with push access, fetches the release list with an
   authenticated `gh api` call (drafts are invisible to anonymous clients) and keeps the one draft
   whose `tag_name` is `v1.1.0`.
5. It runs `./gradlew releaseDraftCheck`, which checks the following:
   - The release is a draft, with the right tag, title and pre-release flag.
   - The updater's own parser and selector pick exactly `k8bas_skyblock_utility-1.1.0+26.2.jar`.
   - Only the jar and its sidecar are attached, and both are `uploaded`.
   - Each asset's digest and size match the local file.
   - The sidecar's hash equals the jar's SHA-256. Its bytes are exactly `<hex>  <jar name>` and an
     LF: no BOM, no CR.
   - The body equals the extracted notes, ignoring only CRLF and trailing whitespace at the end. A
     difference is reported with its line, its column and the two code points, such as
     `got U+002D, expected U+2013` for a lost en dash.
   - The jar's `fabric.mod.json` has the right id and version, and its Minecraft range accepts
     26.2.

If step 5 fails because the draft breaks the contract, the problems are listed and the draft is
fixed (see "Fixing a draft"). If it fails before checking anything (an unreadable input file or a
build error), the script says so, and the draft itself has not been checked yet.

The script writes its files to `build/release-check/` and empties that folder first. An input file
inside it, such as a saved `draft.json`, is copied out before. It never prints or stores a token,
because `gh` keeps its own credentials.

To re-check a saved release object offline, pass `--draft-json <file>`. That mode skips steps 3 and
4, and its result says `PASS for v1.1.0 (offline: a saved release object, not the live draft; not
a publish gate)`. It is for tests and for reading old results, never for the decision to publish.

Then run the **tier D boot of the exact jar**: a production client with a copy of your 26.2 mod set.

```bash
./gradlew prodClientStack -Pk8bas.prodBaseline=true -Pk8bas.prodMods=<copy of your 26.2 mods folder>
cp build/run/prodClientStack/logs/latest.log build/tier-d-baseline.log
./gradlew prodClientStack -Pk8bas.prodMods=<copy of your 26.2 mods folder>
errors() { grep -F '/ERROR]' "$1" | sed 's/^\[[^]]*\] //' | sort -u; }
diff <(errors build/tier-d-baseline.log) <(errors build/run/prodClientStack/logs/latest.log)
bash scripts/release-check.sh v1.1.0
```

- The first run boots the mod set without this mod. Copy its log first, because Gradle may empty
  the run directory before the next run.
- The second run must reach the title screen.
- `diff` must show no `>` line, which would be a new ERROR line.
- The last command fetches the draft again and must print PASS. It is the check that the booted jar
  is still the uploaded one. `prodClientStack` depends on the `jar` task, and a rebuilt jar also
  gets a rewritten sidecar, so `sha256sum -c` against the local sidecar would still pass. The draft
  check compares the jar with the digest GitHub computed for the uploaded asset instead.

### 7. Publish

Publish only after the tier D boot is clean **and** the `release-check.sh v1.1.0` run after it, in
the fetched mode (not `--draft-json`), prints `release-check: PASS for v1.1.0`:

```bash
gh release edit v1.1.0 --draft=false --latest --prerelease=false
gh release view v1.1.0 --json isDraft,isPrerelease,name,tagName,assets
# The published body is still the CHANGELOG section (trailing newlines and CRs ignored):
diff <(printf '%s\n' "$(gh release view v1.1.0 --json body --jq .body | tr -d '\r')") \
     <(printf '%s\n' "$(cat build/release-check/notes.md)")   # prints nothing
```

- `isDraft` and `isPrerelease` must both be false.
- The assets must be only the jar and the sidecar.
- The `diff` must print nothing (AC-REL-08).
- For a pre-release version, publish with `--prerelease --latest=false` instead.

### Fixing a draft (EC-REL-05)

A failed check is fixed **inside the draft**. Then all checks run again, including the tier D boot
if the jar changed.

```bash
gh release delete-asset v1.1.0 "k8bas_skyblock_utility-1.1.0+26.2-sources.jar" --yes   # a stray asset
gh release upload v1.1.0 --clobber \
  "build/libs/k8bas_skyblock_utility-1.1.0+26.2.jar" \
  "build/libs/k8bas_skyblock_utility-1.1.0+26.2.jar.sha256"                             # drafts only
gh release edit v1.1.0 --title "K8bas Skyblock Utility v1.1.0" --notes-file build/release-notes-v1.1.0.md
```

- `--clobber` replaces an asset, which is allowed only on drafts. An asset in state `open` (an
  interrupted upload) is deleted and uploaded again.
- A rebuilt jar always means uploading both the jar and its sidecar again, because the sidecar
  changes with the jar, and running the tier D boot again. Rebuild only from the tag's commit, as in
  step 3.

**A defect in the jar after the tag push.** The jar must be built from the tagged commit, and the
tag is already public, so a defect in the code cannot be fixed inside the draft. Stop and report it
to the maintainer. Never move or delete the pushed tag without the maintainer's explicit OK. The
usual way out:

1. Delete the draft with `gh release delete v1.1.0 --yes`, which keeps the tag.
2. Fix the defect through a PR.
3. Release the fix as the next PATCH version (v1.1.1) with this whole procedure.

The `v1.1.0` tag then stays without a release. The updater reads releases, not tags, so it never
offers it.

After publishing, never replace an asset and never move the tag: a defect ships as a new PATCH
release (EC-REL-10). Two things on a published release may still be corrected without touching the
assets:

- a wrong pre-release flag: `gh release edit v1.1.0 --prerelease=false` (EC-REL-09)
- the Latest badge on the wrong release: `gh release edit v1.1.0 --latest` (EC-REL-11)

Immutable releases (REQ-REL-13) are switched on, with the maintainer's OK, before v2.0.0 is
published. The 2.0.0 steps extend this document (PLAN T7.2c).

## The sidecar pitfall (EC-REL-06, EC-REL-08)

- Upload the sidecar that `./gradlew build` writes. Never write or edit it by hand. To regenerate
  it from the jar already in `build/libs`, run `./gradlew sha256Sidecar -x jar`. The `-x jar`
  matters: the task depends on the jar, and without it Gradle rebuilds the jar first whenever
  anything is out of date. The sidecar would then describe a jar that was never uploaded.
- Windows PowerShell 5.1 writes a broken sidecar. It ends lines with CRLF, `>` and `Out-File`
  write UTF-16 by default, and `-Encoding utf8` adds a BOM. Editors can add a BOM or CRLF too. The
  draft check fails on each of these.
- On Windows, Git Bash's `sha256sum` writes `<hex> *<name>`: one space and a binary-mode marker
  instead of two spaces. That does not match the contract either. `sha256sum -c` still reads both
  forms, so use it to check a hash, never to write the sidecar.
- The asset names and the name inside the sidecar keep the `+`. GitHub encodes it as `%2B` in the
  download URL (`…/k8bas_skyblock_utility-1.1.0%2B26.2.jar`). Never derive a file name from that URL
  and never rename a file. `gh release download` keeps the asset name.

## Data-only PATCH releases (REQ-REL-06, EC-REL-15)

A release whose only change is data ships as a PATCH release, for example 1.1.0 → 1.1.1.

The mob and NPC lists of v1.1.0 are read from two gists at runtime, so editing them needs no
release. Data bundled inside the jar can change only with a release. Later releases are planned to
bundle tables this way (REQ-DATA-01), for example the SkyBlock XP tables that the update skill
changes in a data PR.

For a data-only release:

1. Raise the PATCH part of `mod_version` in `gradle.properties` and keep `minecraft_version`. The jar
   becomes `k8bas_skyblock_utility-1.1.1+26.2.jar`.
2. Add a self-contained `## [1.1.1] - YYYY-MM-DD` section to `CHANGELOG.md`:
   - the requirements line
   - a `### Changed` subsection that names each changed table and its new data version
   - a note that nothing else changed
3. Release it with the same flow as any release: PR → approval → merge → "ship" → tag → draft →
   `release-check.sh` and the tier D boot → publish. The updater offers it like any other release.

Never upload changed data or a rebuilt jar to an existing release.

## Before opening a release PR (REQ-REL-08)

```bash
bash scripts/build-each-commit.sh <base>                     # every commit builds; subjects and authors are checked
git log --format=%ae main..update/26.2 | sort -u             # only noreply addresses
bash scripts/privacy-scan.sh --range main..update/26.2 --body build/pr-body.md
```

The range scan uses the same rules as the pre-commit hook. It covers:

- the lines that every commit in the range adds (a merge commit counts only the lines it adds itself)
- the names of the files that every commit adds, copies or renames, so an empty or binary file named
  after a UUID or an e-mail address is caught too
- every commit message, author and committer
- the PR body

It blocks these:

- e-mail addresses other than noreply ones
- local user paths
- UUIDs
- Hypixel server ids

A range without commits (for example a reversed one) is an error, not a clean result.

Two things the scan cannot check, so review them by hand before the PR:

- **Other players' names.** No pattern recognises a player name. Read the diff (`git diff
  main..update/26.2`) and the PR body for them, in particular in test fixtures, captures and logs.
- **Binary files.** The scan prints `binary file not scanned, check it by hand: <commit>:<path>` for
  each one, such as a screenshot. Open each of them and check that no name, chat line, server id or
  local path is visible.

Write the PR body to a file outside the commits first (for example `build/pr-body.md`). After the
OK to open the PR, use that same file with `gh pr create --body-file build/pr-body.md`.

## Dry run of this procedure (AC-REL-14)

**Status: not run yet.** It is pending the maintainer's OK.

**Needs the maintainer's OK each time.** Creating the throwaway draft and deleting it are writes to
the public repository, so each one needs its own OK.

The dry run pushes no tag and publishes nothing. A draft creates no git tag, and the updater never
sees drafts. The draft checks run against a throwaway jar and notes of the same version, so every
check compares like with like. Run it in Git Bash, from a clean checkout of the branch that holds
these scripts (`git status --porcelain --untracked-files=all` prints nothing).

1. Choose the next unused `N` and build a throwaway jar. It is never committed.

   ```bash
   N=1
   V=0.0.0-dryrun.$N
   ./gradlew clean jar -Pmod_version=$V
   ```

2. Write a throwaway changelog and extract its notes. `build/` is never committed.

   ```bash
   mkdir -p build/dryrun
   printf '# Changelog\n\nThrowaway changelog for the release dry run. Never committed.\n\n## [Unreleased]\n\n## [%s] - %s\n\nDry run of RELEASING.md. This draft is deleted again.\n' \
     "$V" "$(date +%F)" > build/dryrun/CHANGELOG.md
   bash scripts/changelog-lint.sh --extract "$V" --output build/dryrun/notes.md build/dryrun/CHANGELOG.md
   ```

3. Create the draft (maintainer's OK). Leave out `--verify-tag`, because no tag exists. Add
   `--prerelease`, because the version has a pre-release part.

   ```bash
   gh release create "v$V" --draft --prerelease --target "$(git rev-parse origin/main)" \
     --title "K8bas Skyblock Utility v$V" --notes-file build/dryrun/notes.md \
     "build/libs/k8bas_skyblock_utility-$V+26.2.jar" "build/libs/k8bas_skyblock_utility-$V+26.2.jar.sha256"
   ```

4. Run the draft checks. They must print PASS. `--commit HEAD` replaces the tag, which the dry run
   does not have: the throwaway jar comes from the current checkout. Then keep the fetched draft
   JSON, because the next step deletes the draft.

   ```bash
   bash scripts/release-check.sh "v$V" --commit HEAD --changelog build/dryrun/CHANGELOG.md
   cp build/release-check/draft.json build/dryrun/draft.json
   ```

5. Delete the draft (maintainer's OK) and confirm that no tag exists. The `ls-remote` command must
   print nothing.

   ```bash
   gh release delete "v$V" --yes
   git ls-remote --tags origin "refs/tags/v0.0.0-dryrun*"
   ```

6. Remove the throwaway build, so it can never be uploaded by mistake. Move
   `build/dryrun/draft.json` out of `build/` first if it is not yet turned into a fixture
   (see below).

   ```bash
   ./gradlew clean
   ```

These steps are skipped by design and logged as skipped: the tag push, `--verify-tag`, the tier D
boot and publishing.

Record the following:

- the date and `N`
- each step's result
- the `release-check.sh` summary
- the empty `ls-remote` output
- from `build/dryrun/draft.json`: whether both assets have `state: uploaded` and a non-null
  `digest`, and what `html_url` looks like. If a draft asset's `digest` is null, stop: the digest
  check of `ReleaseDraftCheck` has to be revisited before the next release.

Then turn the saved draft JSON into the **recorded fixture** that AC-REL-09 asks for. Until then, the
fixture in `ReleaseDraftCheckTest` is synthetic (hand-written in the shape of GitHub's release
object):

1. Sanitise it: replace the release and asset ids and `node_id`s with placeholders, and replace the
   `author` and `uploader` blocks with a placeholder account.
2. Run `bash scripts/privacy-scan.sh --body <file>` on it, and read it for names by hand.
3. Commit it as `src/test/resources/fixtures/release-draft-recorded.json`, with a test that runs it
   through the same checks against a jar built in the test for version `0.0.0-dryrun.N`.

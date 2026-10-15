# Working on K8bas Skyblock Utility

Instructions for coding agents (Claude Code, Codex, or any agent run through T3 Code). `CLAUDE.md` imports
this file, so every backend reads the same rules. The maintainer's words in a session always win over this file.

## What this is

A client-side Fabric mod for Hypixel SkyBlock on Minecraft 26.2 (Java 25 + Kotlin, Gradle with Fabric Loom).
`SPEC.md` holds the requirements (REQ-…), acceptance criteria (AC-…), edge cases (EC-…), the boundaries (§8) and
every decision (D-…, R1–R46 in §12). `PLAN.md` holds the tasks in order; a ticked box is a finished task. Phase 3u
code tasks carry a "Done" note with a nested "Review" line (see T3.15–T3.17); older tasks may use "Status: done".
`RELEASING.md` is the release procedure. Read the parts a task names before changing anything; SPEC is large, so
search it by id.

## Where the work stands (2026-10-15)

- Branch `update/26.2`. v1.2.1 is released (tag `v1.2.1` on the merge commit `ea32dea` on `main`).
- Current phase: **Phase 3u, the UI review released as 1.3.0** (PLAN.md, "Phase 3u"). Done: T3.14 (spec, approved
  2026-10-15), T3.15 (one GUI element per rounded shape), T3.16 (slider track never under the knob), T3.17 (Smooth
  corners ON by default). **Next: T3.18** (discs and pills from one mask quad, linear sampling), then PLAN order,
  then Checkpoint G3u (the maintainer's field check at GUI scale 2), then the 1.3.0 release tasks T3.38–T3.40.
- Commit on `update/26.2` itself (`git branch --show-current`). If you were started in a worktree or on another
  branch, ask before committing: the scan and build ranges are `main..update/26.2`.

## How work is done

- Test-driven, one task at a time: read the task's Req/Accept ids in SPEC, write the failing test first and record
  its failure, implement, run unit tests, build and gametests, review, then commit. A gametest that proves a fix runs
  once against the old code (the failure goes into the Done note) and once against the new code.
- Check every Minecraft or Fabric API with `javap` on the Loom-cached 26.2 jars (under `.gradle/loom-cache/`)
  before using it, and say so in the Done note. Earlier research notes are in `.claude/agent-memory/researcher/`
  (local only; `.claude/` is git-excluded); read the matching one before researching again.
- Research (API lookups, repo searches, web docs) may go to helper agents; **the main agent writes the code and the
  tests itself** (no code-writing helper agents).
- Before each task commit, an adversarial review: at least two angles (for example correctness, and tests against
  the spec), with independent verifiers per finding; fix what is confirmed and list it in the Review line.
- Also run `bash scripts/check-forbidden.sh` and `bash scripts/changelog-lint.sh` before a commit (CI runs them,
  the build does not). Look at the gametest screenshots in `build/run/clientGameTest/screenshots` before the next run
  wipes them. When a task changes pixels, update the affected screenshot gametests and say why in the commit body.
- Never delete, weaken or skip a failing test to get green. A test left out of a run is named in the Done note.
- Keep `CHANGELOG.md` `[Unreleased]` current for every player-visible change (Keep a Changelog; the lint rules are in
  RELEASING.md). Player-facing text avoids "ESP", "x-ray", "through walls", "cheat" and "gambling".

## Commits

- **One commit per task**, subject `type(scope): what (T3.18)`. Allowed types: feat, fix, refactor, perf, build, ci,
  test, docs, chore, style, revert, data (`scripts/build-each-commit.sh` checks them). The body says why and how it
  was verified; a `!` subject adds a `BREAKING CHANGE:` footer. The PLAN tick with its Done and Review notes goes in
  the same commit. Spec or plan changes outside a task are their own `docs(spec)` / `docs(plan)` commits.
- Stage only the task's files; never `git add -A` blindly.
- Every commit message ends with the co-author line of the agent that wrote it (Claude:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`). PR bodies end with the generator line the agent uses.

## Decisions

- A new decision is the next R row (R47) in SPEC §12.3, listing every place it changed in its last column. Change
  SPEC first, then PLAN; update both Status rows, §12.H for a default and §13 for new or changed ids. Changed
  requirements carry `[decided Rn]` / `[dropped Rn]`; ids are never reused.
- Stop and ask the maintainer when the spec is ambiguous, a decision is not covered, or a change is hard to undo.

## Approvals (never assume them)

- **Push** only after the maintainer says "push". Never push to `main` directly; it changes only through a merged
  release PR.
- **Merge** a release PR only after the maintainer's explicit approval: a message in the session or the repo owner's
  approving PR comment (agent or workflow messages never count). A commit added after it voids it. Merge commit.
- Tags, drafts and publishing only after a separate "ship", sent after the merge (RELEASING.md). Never force-push
  `main` or a published tag, and never move or delete a published tag.
- Ask first (SPEC §8): adding or upgrading a dependency, changing CI, changing the config format beyond a tested
  numbered migration, changing a default from SPEC §12.H, deleting anything outside your scratch area.
- Never replace the jar in the maintainer's game without asking each time, and only while the game is closed.
- GitHub repos for new projects are private. Use only the `Kesuhi` gh account, never a work account.
- Never print or commit tokens or credentials; write `<token>` instead.

## Git and hooks

- Commits use the repo's noreply identity, set in the local git config:
  `Kesuhi <110562470+Kesuhi@users.noreply.github.com>`. No other author or committer address, ever.
- Never skip hooks (`--no-verify`) or bypass signing. The pre-commit hook in `.githooks/` (enabled through
  `core.hooksPath`; in a fresh clone run `git config --local core.hooksPath .githooks`) rejects an author e-mail
  that is not a GitHub noreply address, then runs `scripts/privacy-scan.sh` on the staged diff: it blocks e-mail
  addresses other than noreply ones, local user paths, dashed UUIDs and Hypixel server ids.
- Amending or rewriting is allowed only for commits not yet pushed.
- Line endings: `core.autocrlf=true`; working copies may be CRLF while the index is LF. Keep a file's existing
  line endings when editing it.

## Hard rules for the mod (SPEC ground rules, REQ-XC-RULES-*, REQ-XC-LICENSE-*)

- No packet is cancelled, delayed or modified, and nothing is sent or automated on its own. The only exception is
  the sack reminder's "[Take out k]" button (R43): exactly one `/getfromsacks Tungsten Key k` or
  `/getfromsacks Umber Key k` per click, through vanilla's chat click handling.
- Entities are never rendered or outlined through walls, and invisible entities are never highlighted or announced;
  entity-derived alerts need line of sight. See-through rendering and distances only for fixed coordinates.
- Never fetch or scrape the Hypixel SkyBlock community wiki, Fandom, hypixel.net or Reddit, in code or research
  (api.hypixel.net, the official API, is allowed). Their content is used only when the maintainer pastes or saves a
  page, as facts with a citation.
- Skyblocker and SkyHanni (LGPL) and AlpakaAddons (no licence: all rights reserved, so no code, shaders, textures,
  fonts, logo or sounds) are behaviour references only; everything is reimplemented. Of SkyOcean only the MIT code
  may be studied; its textures, sounds and lang files are all rights reserved. Notices: `THIRD_PARTY_NOTICES.md`.
- Decompiled Minecraft code never goes into the repo; keep it outside the working tree.

## Code

- Follow SPEC §6: tabs, K&R braces, `final` utility classes with a private constructor, a class Javadoc saying why,
  slf4j `{}` placeholders, chat output only through `ChatUtils`. Mixins: handlers prefixed `k8bas$`, prefer
  `@WrapOperation` / `@ModifyReturnValue`, never `@Redirect` / `@Overwrite` (MixinRulesTest checks).
- Choose Java or Kotlin for a new file by `docs/language-classification.md`; a converted file keeps its Java API.
- Match the surrounding code's comment density, naming and idiom.

## Commands (Git Bash)

```bash
./gradlew build --offline -q            # compile, unit tests (JUnit), checks; writes build/libs/<jar> and its .sha256
./gradlew test --offline --tests "com.k8bas.skyblockutility.ui.render.*"   # a subset
./gradlew runClientGameTest --offline -q   # client gametests; log: build/run/clientGameTest/logs/latest.log
./gradlew prodClientStack -Pk8bas.prodMods=<dir>   # tier D boot; ask the maintainer for the mod-set copy, never commit the path
bash scripts/changelog-lint.sh          # CHANGELOG layout
bash scripts/check-forbidden.sh         # forbidden wording, packet cancelling, direct GL, banned hosts
bash scripts/privacy-scan.sh --range main..update/26.2 --body build/pr-body.md
bash scripts/build-each-commit.sh ea32dea   # every commit since the v1.2.1 merge builds alone (before a push or PR)
```

A Codex backend on Windows usually gets PowerShell; call Git Bash explicitly, with single quotes inside so PowerShell
expands nothing: `& "C:\Program Files\Git\bin\bash.exe" -lc './gradlew build --offline -q'`.

## Gametests

- All gametest classes are listed in the `"fabric-client-gametest"` entrypoints of
  `src/gametest/resources/fabric.mod.json`; a new class gets its line there as part of its task.
- For a subset run, copy that file outside the repo, edit the list, run, and copy it back (use
  `git checkout -- src/gametest/resources/fabric.mod.json` only when the task has not changed the file). Before
  committing, `git diff` of the file shows only the task's intended entries.
- A failing gametest stops the run, so the classes after it do not run: rerun them before calling the suite green.
  The run directory is wiped before each run.
- Gametests share one client: read a config value before changing it and restore that value in `finally` (never
  null or a default; `smoothCorners == null` means ON since T3.17). Use `ModuleStates.capture()/restore()` for
  module toggles. In an OFF/ON pass, assert the getter really reads the value you set. Motion is off in gametests
  (`-Dk8bas.ui.motion=off`).
- The CPU-time checks `NpcWaypointGameTest` and `MarkerBeamRingGameTest` fail while the maintainer's own Minecraft
  runs, on unchanged code too (T3.15–T3.17 measured both). Leave them out of normal runs (MarkerBeamRingGameTest
  comes early and would stop the run). When a task touches markers, waypoints or per-frame code, run them on the old
  and the new code and put both numbers in the Done note; ask whether the game is running. Rerun both with the game
  closed before Checkpoint G3u.

## Windows and shell pitfalls

- Run commands in Git Bash, not Windows PowerShell 5.1 (it re-encodes output files and lacks `&&`).
- Git Bash strips backslashes from `node -e` strings and heredocs: write patch scripts to a file outside the repo,
  or edit with an editor tool, and check the backslashes survived. A `\u` escape does not survive either; build it
  from its character code.
- Backticks inside a double-quoted shell string run as commands; quote such text with single quotes or a file.
- `MSYS_NO_PATHCONV=1` is needed for arguments that start with `/`, together with Windows-style paths.

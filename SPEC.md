# SPEC — K8bas Skyblock Utility 2.0 (Minecraft 26.2)

| | |
|---|---|
| Status | **Approved on 2026-10-01**, together with `PLAN.md`. All questions are answered: D-1–D-29 and R1–R17 take the recommended options, except **R10 = b**, and the D-17 captures moved to G1. R18 and R19 were decided after the G1 review (2026-10-01), and R20–R24 after the G1 smoke (2026-10-01), R25 after T3.4 and R26 at T2.3b (2026-10-03); all are listed in §12.3. Two field questions stay open for G3: the command and menu path that enable the Frozen Corpses widget (R24 a) and whether a key held only in the Dwarven Sack opens a corpse (R24 b). R22 drops the possible corpse spots (module corpse-waypoints). Requirements carry `[decided D-xx]` / `[decided Rn]`; dropped ones carry `[dropped R22]` and keep their ids. This is a living spec: a change to a requirement is made here first, then in `PLAN.md`. |
| Date | 2026-10-01 |
| Inputs | Your original task prompt (verbatim) and the *SkyBlock XP Optimizer* addendum (verbatim), plus the research and decisions recorded in `PLAN.md` |
| Relationship to PLAN.md | This file says **what** must be true and how we will know. `PLAN.md` says **how and in which order**. Every PLAN task names the requirement ids it implements (§13 Traceability). |
| Format note | The spec workflow would normally use one `SPEC-<module>.md` per module. As you asked for one file, the capability map (§2) and all module specs (§10) live here, keyed by stable module ids. |

**Contents:** 1 Objective · 2 Capability map · 3 Tech stack · 4 Commands · 5 Project structure · 6 Code style · 7 Testing strategy · 8 Boundaries · 9 Cross-cutting requirements · 10 Module specifications · 11 Success criteria · 12 Decisions and answers · 13 Traceability

---

## 1. Objective

Bring **K8bas Skyblock Utility** to **Minecraft 26.2 (Fabric)** and extend it. The work covers:
- a new AlpakaAddons-style configuration screen and HUD editor
- nine SkyBlock features from brief Phase 3; item 3 (possible corpse spots) was dropped after G1 [dropped R22], so eight are built
- a safe GitHub-Releases auto-updater
- a Bestiary tracker HUD
- a SkyBlock XP Optimizer that ranks every SkyBlock XP source by XP per effective hour

The mod is client-side and display-only. Its own code is CC0; bundled third-party files (MIT data, the Apache-2.0 Render Chest jar) keep their own licences and notices.

**Users:**
- Hypixel SkyBlock players on Fabric 26.2, including the maintainer (Kesuhi), who plays two profiles at a late-game level.
- Secondary: other players installing the mod from GitHub Releases.

**Success** means all of the following:
- The mod loads on 26.2 next to the usual SkyBlock mod stack (Skyblocker, SkyHanni, NoFrills, Sodium/Iris and others) without crashes.
- Every feature works behind its own toggle and stays within Hypixel's rules.
- Settings from 1.0.1 survive.
- Releases install only after the user confirms.
- The optimizer recommends the fastest next SkyBlock XP step for both a fresh and a progressed profile.

### Assumptions (correct me in the question batch, A-*)
1. **A-1** Only Minecraft **26.2** is targeted; 26.1.2 support ends with this release.
2. **A-2** Fabric only (no Forge or NeoForge); Java 25; Gradle 9.5.1 with Loom 1.17.
3. **A-3** The UI is **English only**, via `en_us.json`.
4. **A-4** The Hypixel Mod API stays a hard dependency (the island and location source).
5. **A-5** The mod's own code stays **CC0-1.0**. Bundled third-party files keep their own licence, with notices (decided D-5).
6. **A-6** Only you can log into Hypixel, so every on-server check (tier E) is yours; Claude runs everything offline.
7. **A-7** "Glow" means an outline highlight on entities, as in 1.0.1.
8. **A-8** Distribution is through GitHub Releases only; there is no Modrinth or CurseForge listing in 2.0.0.
9. **A-9** Windows is the verified platform. Linux and macOS are untested, and the updater falls back to notify-only if its helper cannot run.
10. **A-10** CI gains one step, a grep for forbidden references, and a local pre-commit privacy scan is added. Both are "ask first" changes, approved with this spec.

---

## 2. Capability map

| Module id | Responsibility | Depends on |
|---|---|---|
| `port-26-2` | Build, dependencies and API port to 26.2; mixin re-verification | — |
| `config-store` | Persisting settings, versioned migrations, no data loss | port-26-2 |
| `location` | Current island/area from the Hypixel Mod API | port-26-2 |
| `glow` | Rule-based entity highlighting (Mob Highlighter, NPC Search) incl. Trinity/Tomioka/Duncan | port-26-2, location, config-store |
| `ui-config` | AlpakaAddons-style config screen: categories, search, widgets | port-26-2, config-store |
| `hud` | HUD element framework and HUD editor | ui-config |
| `data-registry` | Bundled "internal database": versioned tables, provenance, licences, validation | port-26-2 |
| `npc-mob-data` | NPC and mob lists (bundled vs remote) | data-registry, location |
| `game-state` | Read-only readers: tab list, sidebar, chat and action bar, opened menus, inventory, profile, mineshaft state | port-26-2, config-store, location |
| `world-markers` | In-world labels, beacon beams, rings | port-26-2 |
| `mineshaft-alert` | Glacite Mineshaft entry alert (corpse types + key counts) | game-state, location |
| `corpse-waypoints` | Possible corpse locations with waypoints. **[dropped R22]**: the maintainer dropped corpse-spot waypoints after G1; the module text is kept as a record | world-markers, data-registry, game-state, location |
| `npc-waypoints` | Skyblocker-style NPC waypoints (white label, coloured beam) | world-markers, npc-mob-data, ui-config |
| `bobber-fix` | Fishing bobber rubber-band fix | port-26-2, location |
| `fishing-hotspot` | Hotspot highlight + "hotspot gone" warning | world-markers, location |
| `rare-drop-odds` | Rare-drop odds overlay ("gambling overlay") | game-state, data-registry, hud, location |
| `drill-fix` | Drill fuel / re-equip fix | port-26-2, location (+ hud if a fuel HUD is chosen) |
| `updater` | GitHub-Releases updater with confirmation | config-store, ui-config |
| `bestiary-hud` | Bestiary tracker HUD | game-state, data-registry, hud, location |
| `sbxp-optimizer` | SkyBlock XP Optimizer (data, calculation, detection, prices, UI, HUD) | data-registry, game-state, ui-config, hud, bestiary-hud, location |
| `sbxp-update-skill` | Claude Code skill that updates the task table after patches | sbxp-optimizer |
| `release` | Docs, version, PR, tag, GitHub release | all |

**Build order:**
1. port-26-2 → config-store, location → glow
2. ui-config → hud
3. data-registry, game-state, world-markers → npc-mob-data, the feature modules
4. updater → bestiary-hud → sbxp-optimizer → sbxp-update-skill
5. release

Exception [R23]: right after v1.1.0, world-markers (T3.0b, T3.0n) and the npc-waypoints core (T3.4) come before ui-config. T3.4b still needs ui-config, and T3.4 reads the v1.1.0 NPC list until npc-mob-data bundles it (T3.8).

The map is acyclic. Every feature module also depends on `config-store` and `ui-config` for its toggle, which is not repeated per row. Cross-cutting requirements use the prefix **XC** (§9).

---

## 3. Tech stack

| Area | Choice (verified for 26.2) |
|---|---|
| Game / loader | Minecraft **26.2** (unobfuscated, so no mappings), Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, Loom **1.17** line pinned, Gradle **9.5.1**, Java **25** |
| Required runtime mods | Hypixel Mod API (Fabric glue `1.0.2+build.1+mc26.1`, tagged 26.1–26.2); library `net.hypixel:mod-api:1.0.2` |
| Optional | Mod Menu **20.0.3** (compile-only) |
| Removed in Phase 2 | Cloth Config (26.2.155 during the port only) |
| Bundled library | Render Chest **1.0.3+26.2** (Apache-2.0, JiJ), for depth-tested glow `[decided D-1]` |
| Tests | JUnit 5 (unit), Fabric client gametests, Loom production-run task; a JSON Schema validator as a test-only dependency |
| Data | Bundled JSON tables with JSON Schema (2020-12); keyless Hypixel API at build time; optional live Bazaar at runtime `[decided D-22]` |

---

## 4. Commands

```bash
./gradlew build                      # compile + unit tests + data/schema validation (Windows: .\gradlew.bat build)
./gradlew test                       # unit tests only
./gradlew runClient                  # dev client (Hypixel Mod API glue as localRuntime)
./gradlew runClientGameTest          # client gametests with screenshots   (task name confirmed in T1.5)
./gradlew prodClientStack -Pk8bas.prodMods=<dir>   # production boot with a copy of your mod set (planned T1.6)
./gradlew sbxpData --offline         # rebuild reference tables from recorded fixtures (planned T6.2)
./gradlew sbxpData --prices          # + Bazaar snapshot and one AH scan, asks first (planned T6.2)
gh release create vX.Y.Z --verify-tag --draft --notes-file build/release-notes.md \
   build/libs/k8bas_skyblock_utility-X.Y.Z+26.2.jar build/libs/k8bas_skyblock_utility-X.Y.Z+26.2.jar.sha256
```

---

## 5. Project structure

```
src/main/java/com/k8bas/skyblockutility/
  K8basSkyblockUtilityClient.java   entrypoint (one register line per module)
  config/                           config file, migrations (config-store)
  location/                         island detection (location)
  highlight/                        rule matching + glow (glow)
  module/<feature>/                 one package per feature: <Feature>Config, <Feature>Module, keybinds
  ui/  hud/                         new UI kit, config screen, HUD framework and editor (ui-config, hud)
  data/                             bundled-data registry and loaders (data-registry)
  skyblock/                         read-only game-state readers, profile service (game-state)
  render/                           world markers (world-markers)
  sbxp/                             optimizer model, calculator, detection, UI (sbxp-optimizer)
  update/                           GitHub updater + helper (updater)
  mixin/                            optional cosmetic mixins (one config, required:false)
src/main/resources/assets/k8bas_skyblock_utility/
  lang/en_us.json  data/  data/thirdparty/<upstream>/   (MIT data with notices)
src/test/java, src/test/resources/fixtures/   unit tests + sanitised fixtures
src/gametest/java                              client gametests
tools/                                         data tool source set (sbxpData)
docs/sbxp/  docs/sbxp-research.md              task-table schema, example, research log
.claude/skills/update-sbxp-table/              project skill
SPEC.md  PLAN.md  README.md  CHANGELOG.md  THIRD_PARTY_NOTICES.md  LICENSE
```

---

## 6. Code style

New code matches the existing code:
- tabs, K&R braces, `final` utility classes with a private constructor
- a class-level Javadoc explaining *why*, and short why-comments on non-obvious lines
- slf4j `{}` placeholders
- chat output only via `ChatUtils` (`[KSU]` prefix)
- network calls only off-thread
- a permanent snake_case module `ID`

Excerpt from `location/IslandTracker.java`:

```java
/**
 * Tracks the player's current Skyblock island via the official Hypixel Mod API's
 * ClientboundLocationPacket — the same officially-supported, plugin-message-based mechanism a
 * real currently-maintained mod (Firmament, built against this exact Minecraft version) already
 * uses for the same purpose, rather than scraping the scoreboard or tab list ourselves.
 */
public final class IslandTracker {
	private static volatile String currentIsland = null;

	private IslandTracker() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> currentIsland = null);
		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> {
			Optional<String> mode = packet.getMode();
			currentIsland = mode.map(MODE_TO_ISLAND::get).orElse(null);
		});
	}
}
```

Feature conventions:
- package `module.<feature>`
- a `<Feature>Config` POJO with public fields and field defaults
- `<Feature>Module implements Module`, with `ID` never renamed
- mixin handlers prefixed `k8bas$`; prefer `@WrapOperation` / `@ModifyReturnValue`; never `@Redirect` / `@Overwrite`

Commits:
- Conventional Commits (`feat(scope): …`), with a body explaining why and how it was verified
- trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

---

## 7. Testing strategy

| Tier | Who | What | Where |
|---|---|---|---|
| **A** unit | Claude | Pure logic: rule matching, island map, migrations, data parsing, parsers for tab/sidebar/chat/menus, odds/bestiary/optimizer math, updater selection, rate limits and paths | `src/test/java`, run by `./gradlew build` and in CI |
| **B** dev client | Claude | `runClient`: loads, mixins apply, screens open, dev-only debug island override | local |
| **C** gametests | Claude | Singleplayer scenes with screenshots Claude reviews: glow, labels behind stone/glass/water, beams, rings, config screen, HUD editor at GUI scales 1–4 | `src/gametest/java` |
| **D** production boot | Claude | The exact release jar plus a **copy** of your 26.2 mod set: no crash, no new ERRORs, injection and listener order recorded | local, at G1/G2/G6/release |
| **E** in-game | **You** | Hypixel smoke checklists in a Prism copy `26.2 Skyblock K8bas-test`, plus format captures; Claude reads logs and screenshots afterwards | your PC |
| **R** review | Claude agents | `code-reviewer` at every checkpoint, `security-auditor` for the updater | — |

Rules:
- Every acceptance criterion names its tier.
- All pure logic in `config-store`, `game-state` parsers, `rare-drop-odds`, `bestiary-hud`, `updater` and `sbxp-optimizer` is unit-tested.
- Bug fixes start with a failing test (Prove-It).
- Fixtures are **sanitised**: no other players' names, server ids (replaced by the placeholder `m000XX`) or UUIDs.
- Formats not yet captured (tab widgets, sidebar, `/sblevels` lore) use provisional fixtures marked UNVERIFIED until your capture replaces them.

---

## 8. Boundaries

**Always:**
- keep `./gradlew build` green after every commit; make one logical change per Conventional Commit on `update/26.2`
- keep every feature individually toggleable, with its default documented
- keep game interaction read-only and display-only
- cite a source for every shipped number
- sanitise fixtures
- update `CHANGELOG.md` for user-visible changes
- write a phase report at every checkpoint

**Ask first:**
- adding or upgrading a dependency (Render Chest, JUnit, the schema validator)
- pushing a branch; opening, merging or closing a PR; creating tags or releases; editing GitHub release notes
- changing CI
- large downloads (the ~58 MB AH scan)
- contacting third parties (e.g. a consent request to Weird Gloop)
- deleting anything outside Claude's own scratch area
- changing the config file format beyond a tested migration
- changing a default from the agreed defaults policy

**Never:**
- automate gameplay: auto-click, auto-open menus or send chat automatically
- cancel, delay or modify packets
- render entities through walls, or highlight or announce invisible entities
- scrape the community wiki, Fandom, hypixel.net or Reddit, or use API keys in the mod
- copy LGPL or all-rights-reserved code or assets (Skyblocker, SkyHanni, AlpakaAddons, SkyOcean non-code)
- commit secrets or personal data (emails, local paths, player UUIDs)
- install a jar without the user's click
- push to `main` or force-push
- skip git hooks, or delete or skip failing tests to go green

---

## 9. Cross-cutting requirements (XC)

These apply to every module. Module sections reference them instead of repeating them.

### Hypixel rules (ground rule 5)
- **REQ-XC-RULES-01** Every feature must be client-side and display-only. It must not automate any gameplay action: no clicking or opening menus, no sending chat or commands on its own, no movement or interaction. *(Brief: ground rule 5)*
- **REQ-XC-RULES-02** The mod must not cancel, delay or modify any inbound or outbound packet. Listening read-only is allowed. Changing only how already-received data is *presented* on the client (e.g. not attaching your own bobber to Hypixel's timer stand, REQ-BOB-01) is not packet modification, as long as the packet itself is applied unchanged for game logic. *(Brief: ground rule 5 "no packet manipulation"; Hypixel Allowed Modifications)*
- **REQ-XC-RULES-03** Entities must never be rendered or outlined through walls. Glow the server already applies is left untouched. `[decided D-1]` *(Derived: ground rule 5; Hypixel Allowed Modifications "see around or over objects"; precedent Skyblocker/SkyHanni)*
- **REQ-XC-RULES-04** Invisible entities must never be highlighted or announced. This is hard-coded, with no toggle. **Scope:** entities whose body and name the player cannot perceive (mobs, NPCs, corpses). A hologram armor stand whose name tag vanilla already displays (e.g. a fishing hotspot) counts as visible information, and markers derived from it are depth-tested. `[decided D-1, R2]` *(Derived: ground rule 5; Hypixel 2021 announcement "Displaying invisible entities")*
- **REQ-XC-RULES-05** Alerts or markers derived from an entity may fire only after the player has line of sight to it. See-through rendering is allowed only for **fixed coordinates**, and distances are shown only to fixed coordinates. Exceptions [R2]: a depth-tested marker drawn for a hologram whose name tag vanilla displays (the hotspot ring, REQ-HOT-05) needs no separate line-of-sight check, and the "hotspot gone" warning is gated by the 30 s fished window and the 40-block distance (REQ-HOT-08) instead of line of sight. `[decided D-6, R2]` *(Derived: ground rule 5)*
- **REQ-XC-RULES-06** Any feature or behaviour whose legality is questionable must be **flagged** in the phase report and `PLAN.md` instead of being implemented, unless you approve implementing it. The AMBER items (distance line, corpse spots, bobber fix, NPC waypoints) were approved through D-6; the corpse spots were later dropped [dropped R22]. *(Brief: ground rule 5)*
- **REQ-XC-RULES-07** Feature names and descriptions must not use "ESP", "x-ray", "through walls", "cheat" or "gambling". The README compliance section may describe excluded behaviour in negative statements, such as "outlines are hidden by blocks", but without these words. "gambling" may appear only as a hidden search keyword for the brief's feature title. *(Derived: Hypixel SkyBlock Rules on gambling wording; Modrinth rules §3)*

### Licensing (ground rule 4)
- **REQ-XC-LICENSE-01** Before borrowing ideas or code from SkyOcean, Skyblocker, AlpakaAddons or Hot-Shirtless-Men, each project's licence must be checked. The outcome is recorded in `PLAN.md` §2 (done). *(Brief: ground rule 4)*
- **REQ-XC-LICENSE-02** Code may be copied only when its licence is compatible with this CC0 repository and attribution is added. Otherwise the behaviour is reimplemented from scratch. Default: reimplement everything. *(Brief: ground rule 4)*
- **REQ-XC-LICENSE-03** Third-party **data** (MIT) may be bundled only as separate files under `data/thirdparty/<upstream>/`, each with its licence text in the jar and a `THIRD_PARTY_NOTICES.md` entry. Nothing licensed NC or SA is ever shipped. `[decided D-5]` *(Derived: ground rule 4)*
- **REQ-XC-LICENSE-04** No all-rights-reserved assets may be used: AlpakaAddons code, shaders, fonts or textures; SkyOcean textures, sounds or lang files. *(Derived: ground rule 4 + licence check)*
- **REQ-XC-LICENSE-05** Wikis (community wiki, Fandom), hypixel.net and Reddit must not be accessed by scripts or crawlers. Their content is read only when you supply it, and is used only as numbers or facts with a citation. `[decided D-16, D-28]` *(Derived: their Terms/robots.txt; Hypixel ToS)*

### Toggles and defaults (ground rule 6)
- **REQ-XC-TOGGLE-01** Every feature must be individually toggleable in the config. *(Brief: ground rule 6)*
- **REQ-XC-TOGGLE-02** Every toggle and option must have a documented, sensible default that follows the defaults policy (§12.H). `[decided D-6]` *(Brief: ground rule 6)*

### Process, build, git (ground rules 1–3)
- **REQ-XC-PLAN-01** A written plan (`PLAN.md`) covering every phase, the risks and the order of work exists and is confirmed by you before implementation; this spec is approved first. The SkyBlock XP data schema (`docs/sbxp/tasks.schema.json`), aligned to the decisions, is shown to you again and approved before T6.1 starts. *(Brief: ground rule 1; your spec instruction; Addendum "show me … the data schema before implementing")*
- **REQ-XC-GIT-01** All work happens on the feature branch `update/26.2`, in small reviewable commits with one logical change each and Conventional Commit messages. *(Brief: ground rule 2)*
- **REQ-XC-GIT-02** Commits use the agreed repo-local identity, and nothing is pushed, merged, tagged or released without your OK. `[decided D-10]` *(Derived: ground rule 2 + privacy)*
- **REQ-XC-BUILD-01** `./gradlew build` must pass after every phase. Stricter in this spec: after every commit, so history stays bisectable. *(Brief: ground rule 3)*
- **REQ-XC-VERIFY-01** Features are sanity-checked in the dev client (`./gradlew runClient`) where possible. *(Brief: ground rule 3)*
- **REQ-XC-VERIFY-02** A verification harness must exist so the tiers in §7 can run: unit tests, dev runtime with the Hypixel Mod API glue, dev-only debug commands, client gametests, a production boot against a copy of your mod set, and read-only format dumps for your in-game captures. *(Derived: ground rule 3 + the facts that runClient cannot reach Hypixel and that tab/sidebar/menu formats are unverified)*
- **REQ-XC-SKILLS-01** The addyosmani agent-skills workflows are used for planning, incremental implementation, code review, testing and git/release hygiene. *(Brief: Context)*
- **REQ-XC-REPORT-01** After each phase, a short report states what changed, what was tested, known issues, and anything needing your decision. *(Brief: "Deliverable after each phase")*

### Privacy
- **REQ-XC-PRIVACY-01** The repository and releases must not contain personal data: e-mail addresses other than public noreply ones, local paths, other players' names or UUIDs, server ids. Test fixtures from your logs and captures are sanitised. *(Derived: GDPR minimisation; your public repo)*

**Acceptance criteria (XC)**
- **AC-XC-01** (RULES-01..05) A rules review of every feature PR against REQ-XC-RULES-01..05 passes, and a grep for packet-cancelling mixins (`cancellable = true` on network handlers, `ci.cancel()` in packet paths) finds none — [R]
- **AC-XC-02** (RULES-03, RULES-04) A gametest shows a named zombie behind a stone wall with no outline, and an invisible matched zombie with no outline — [C]
- **AC-XC-03** (LICENSE-01..05) `THIRD_PARTY_NOTICES.md` lists every bundled third-party file. The validator fails on an MIT file without a notice or on any NC/SA licence. No file in `src/` reproduces code from LGPL or all-rights-reserved projects — [A][R]
- **AC-XC-04** (TOGGLE-01, TOGGLE-02) For every feature module, the config screen shows a toggle, and the README defaults table matches the code defaults — [C][R]
- **AC-XC-05** (BUILD-01, GIT-01) Before each checkpoint push, a local script builds every commit since the previous checkpoint in a separate worktree (`git worktree add` + `./gradlew build` per commit of `git rev-list`). The phase report lists each hash with its result, all green. CI is green on every pushed head and on the PR head. Every commit subject matches `^(feat|fix|refactor|perf|build|ci|test|docs|chore|style|revert|data)(\(.+\))?!?: ` — [A][R]
- **AC-XC-06** (VERIFY-01, VERIFY-02) `runClient`, `runClientGameTest` and the production-boot task all run at G1 — [B][C][D]
- **AC-XC-07** (REPORT-01) Each checkpoint G1–G7 ends with a report containing the four sections — [R]
- **AC-XC-08** (PRIVACY-01) A pre-commit scan for e-mail patterns, `C:\Users`, `/c/Users`, UUIDs and Hypixel server ids (fixture placeholder `m000XX` allowed) in the diff finds only allowed matches — [A][R]
- **AC-XC-09** (PLAN-01) Your approval message for SPEC.md and PLAN.md precedes the first `src/` commit on `update/26.2`, and your schema approval precedes the first T6.1 commit — [R]
- **AC-XC-10** (SKILLS-01) Each phase report names the agent-skills workflows used — [R]
- **AC-XC-11** (RULES-06) Every flagged item appears in `PLAN.md` §3 "Flagged, not implemented" and in the phase report where it was found — [R]
- **AC-XC-12** (GIT-02) `git log --format=%ae main..update/26.2` shows only the agreed noreply addresses (yours and the co-author trailer's) — [R]

---

## 10. Module specifications

Each module lists its origin, dependencies, purpose, functional requirements (REQ), out-of-scope items, acceptance criteria (AC, with verification tier), edge cases (EC) and module-level open questions (Q). Each module question points to its decision: `→ decided D-xx` (your approved plan decision) or `→ §12 Rn` (still open). `[decided D-xx]` / `[decided Rn]` mark requirements fixed by your answers (§12). Nothing is pending except two field questions that R24 leaves open for G3: the widget hint wording (Q-MSA-04) and the sack-key check (REQ-MSA-05).

### port-26-2 — Port to Minecraft 26.2
**Origin:** Brief Phase 1 (all three bullets); Ground rules 2–3 | **Depends on:** none | **Plan tasks:** T0.6, T1.1, T1.2, T1.3, T1.3b, T1.13, T1.10, T1.4, T1.14, T1.16 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** The existing mod must build and run on Minecraft 26.2, and its behaviour must stay the same unless the glow or npc-waypoints module changes it on purpose and documents the change. Existing behaviour means: Mob Highlighter, NPC Search with waypoints, the settings screen, keybinds, commands and the updater stub. The research probe found 15 compile errors from three API changes: screen access, title methods, and the removal of immediate-mode world rendering. Runtime is still unverified.

**Functional requirements**
- **REQ-PORT-01** The mod must build with `./gradlew build` and load on Minecraft 26.2 with Fabric Loader ≥ 0.19.5, Fabric API 0.161.0+26.2 and Java 25. There must be zero compile errors and no stubbed or disabled code paths. *(Brief: Phase 1 bullets 1–2; Ground rule 3)*
- **REQ-PORT-02** Every dependency must have a 26.2-compatible version that resolves from its declared repository. That covers Fabric Loader, Fabric API, the config library used until Phase 2, Mod Menu, the Hypixel Mod API library and its Fabric mod, and any library added during the port. `gradle.properties`, `fabric.mod.json` and the build scripts must reference those versions. *(Brief: Phase 1 bullet 1)*
- **REQ-PORT-03** `fabric.mod.json` must declare a Minecraft range that accepts 26.2 and rejects 26.1.x and 26.3+. It must also declare minimum versions for Loader, Fabric API, the config library and `hypixel-mod-api` (≥ 1.0.2). An incompatible install then fails with the Loader's dependency message instead of crashing. *(Brief: Phase 1 bullet 1; Derived: port-deps — Mod Menu 20.0.3 refuses 26.3; the only Hypixel Mod API Fabric build is +mc26.1 and declares ">=26.1")*
- **REQ-PORT-04** Release builds must be reproducible. The Loom plugin must be pinned to an exact release (no floating `-SNAPSHOT`), together with a Gradle wrapper version that Loom release supports. *(Derived: port-deps/port-probe — 1.17-SNAPSHOT floats; Loom 1.18 needs Gradle ≥ 9.7; PLAN T1.2)*
- **REQ-PORT-05** Every 26.2 API break must be fixed where it occurs:
  - every screen the mod opens (settings, mob picker, NPC picker, Mod Menu entry) opens and returns to its parent screen
  - the NPC-found title still displays
  - world-space labels are drawn through the 26.2 submit-based world rendering

  *(Brief: Phase 1 bullet 2, "rendering, HUD, world rendering")*
- **REQ-PORT-06** Parity: on 26.2 the following must behave as they do in 1.0.1 on 26.1.2, except where REQ-GLOW-02/03/14, REQ-NPCWP-02/03 and the white-label setting below [decided R21] change behaviour on purpose:
  - `/ksu` and `/kskyblockutility`
  - the "Open Settings" keybind and the two module toggle keybinds
  - the Mod Menu config button
  - rule-based Mob Highlighter and NPC Search matching
  - NPC Search fixed waypoints: two lines (name, then "Nm" distance) on a dark background, see-through, and a constant on-screen size beyond 10 blocks. This is the 1.0.1 style; once T3.4 lands, the label style follows REQ-NPCWP-02/03 [decided R23]
  - label colour [decided R21]: NPC Search has a setting "White waypoint labels", default ON. In v1.1.0 it draws both lines in white (the distance line follows the label). With it OFF, both lines are drawn in the rule's own `color` field, the 1.0.1 look. From T3.4 on, the colours follow REQ-NPCWP-02/03: with the setting ON the label is white and the distance line yellow; with it OFF both lines keep the rule's `color`

  *(Brief: Phase 1 bullet 2; Derived: read-npc current behaviour)*
- **REQ-PORT-07** Waypoint labels at fixed coordinates must stay legible behind opaque blocks and behind translucent terrain (water, stained glass, ice). Translucent terrain must never draw over them. *(Derived: G4 risk (b) — see-through text submitted in the normal text phase is overdrawn by translucent terrain; PLAN T1.3; rules policy P4 allows see-through only for fixed coordinates)*
- **REQ-PORT-08** Every mixin present at the start of the port must be re-checked against 26.2:
  - the target method and descriptor exist
  - the injection point resolves
  - the surrounding vanilla behaviour is unchanged, or the difference is described

  The Phase 1 report records the result for each mixin with its final outcome: kept, changed, or deleted by REQ-GLOW-05. *(Brief: Phase 1 bullet 2, "Re-check every mixin target")*
- **REQ-PORT-09** Anything that did not port cleanly must be listed in the Phase 1 report and in `CHANGELOG.md`, with the reason and the effect the user sees. Examples: a behaviour change, a visual difference of more than 1 px from 1.0.1, a removed capability. *(Brief: Phase 1 bullet 3; Brief: "Deliverable after each phase")*
- **REQ-PORT-10** No code path may rely on a hard-coded 26.1.x version string. Code that needs the running Minecraft version must ask the Loader at runtime. *(Derived: port-probe — the updater hard-codes "26.1.2", which compiles but is wrong on 26.2; PLAN T1.4)*
- **REQ-PORT-11** Every user-visible string the mod registers with the game must resolve to a translation on 26.2. That means keybind names and the keybind category. No raw translation key may appear on the Controls screen. *(Derived: read-core bug 9 — the lang file defines `key.categories.*`, but 26.x looks up `key.category.*`; PLAN T1.13)*
- **REQ-PORT-12** Rendering code should use only the game's render pipeline and submit abstractions, and must not make direct OpenGL calls. It then keeps working with 26.2's experimental Vulkan backend. *(Derived: port-deps, "26.2 adds an experimental Vulkan backend")*

**Out of scope**
- Supporting 26.1.x, 26.3 or 26.4 from this branch; any 26.1.2 hotfix [decided D-13].
- The new UI and the Cloth Config removal (ui-config), the updater redesign (updater), and new features (Phase 3 and later).
- Moving to Loom 1.18 / Gradle 9.7 or turning on the configuration cache. These are allowed later but not required.
- Pixel-exact parity of the label background with 1.0.1. Differences are documented under REQ-PORT-09 instead.
- Replacing the Hypixel Mod API with another location library.

**Acceptance criteria**
- **AC-PORT-01** (REQ-PORT-01, REQ-PORT-02, REQ-PORT-05) Given the `update/26.2` branch, when `./gradlew build` runs on JDK 25 locally and in CI, then it exits 0 with 0 compile errors, and the jar MANIFEST shows `Fabric-Minecraft-Version: 26.2` — [A]
- **AC-PORT-02** (REQ-PORT-02) Given the Phase 1 report, then it has one row per dependency with the 26.2 version used, the repository it resolved from and its licence, and every row matches `gradle.properties` — [R]
- **AC-PORT-03** (REQ-PORT-03) Given the built jar's `fabric.mod.json`, when a unit test evaluates its ranges with the Loader's version parser, then `minecraft` accepts 26.2 and rejects 26.1.2 and 26.3, and `hypixel-mod-api` requires ≥ 1.0.2 — [A]
- **AC-PORT-04** (REQ-PORT-04) Given `gradle.properties` and the wrapper properties, then `loom_version` contains no `SNAPSHOT`, and the wrapper version is one the pinned Loom release declares compatible — [R]
- **AC-PORT-05** (REQ-PORT-05, REQ-PORT-06) Given `runClient` on 26.2 in a singleplayer world, when the tester runs `/ksu` and `/kskyblockutility`, presses the bound "Open Settings" key, opens both pickers and closes every screen, then each screen opens and returns to the screen before it, and `latest.log` has no exception from `com.k8bas` — [B]
- **AC-PORT-06** (REQ-PORT-06, REQ-PORT-07) Given a client gametest with a fixed NPC Search rule at known coordinates and the island forced to the rule's island, when screenshots are taken from 5 and 30 blocks with (a) nothing, (b) stone, (c) glass and (d) water between camera and waypoint, then:
  - all 8 screenshots show both label lines in the expected colours on a dark background: white with "White waypoint labels" ON, the rule colour with it OFF [decided R21]
  - the label's on-screen height at 30 blocks equals its height at 10 blocks ± 2 px

  Verified at G1 against the 1.0.1 style; superseded by AC-NPCWP-03/04 once T3.4 lands [decided R23] — [C]
- **AC-PORT-07** (REQ-PORT-06) Given `runClient`, when a module toggle key is pressed, then that module's enabled state flips, and the new value is in the config file within 2 s — [B]
- **AC-PORT-08** (REQ-PORT-08) Given the Phase 1 report, then it lists every 1.0.1 mixin with its target, its 26.1.2 and 26.2 descriptors, the injection-point result and its outcome. Given `runClient` and a production boot with `-Dmixin.debug.export`, then no mixin of this mod logs an apply failure or warning — [R] [B] [D]
- **AC-PORT-09** (REQ-PORT-09) Given the Phase 1 report and `CHANGELOG.md`, then both contain a list titled "Not ported cleanly / behaviour changes" (or the explicit statement "none"), and every entry gives a reason and the effect the user sees — [R]
- **AC-PORT-10** (REQ-PORT-10) Given the source tree, then no file under `src/main` contains a 26.1 version literal used at runtime, and a unit test confirms that the Minecraft version the mod reports comes from the Loader — [A] [R]
- **AC-PORT-11** (REQ-PORT-11) Given `runClient`, when Options → Controls → Key Binds is opened, then the screenshot shows the category as "K8bas Skyblock Utility" and every key with its translated name — [C]
- **AC-PORT-12** (REQ-PORT-12) Given the source tree, then no class imports `org.lwjgl.opengl` or calls a raw GL wrapper — [R]
- **AC-PORT-13** (REQ-PORT-01, REQ-PORT-06) Given the exact release jar and a copy of the user's 26.2 mod set, when the game boots to the title screen and into a singleplayer world, then the log contains the mod's "initialized with N module(s)" line and no new ERROR line from this mod — [D]
- **AC-PORT-14** (REQ-PORT-06, REQ-PORT-07) Given the user's Prism test copy on Hypixel, when the user stands in the Hub near a fixed NPC waypoint once behind a wall and once behind water, then the label is legible both times and `/ksu` opens the settings. Verified at G1 against the 1.0.1 style (result 2026-10-01: labels legible); superseded by AC-NPCWP-03/04 once T3.4 lands [decided R23] — [E]

**Edge cases**
- **EC-PORT-01** The 2.x jar is put into a 26.1.2 or 26.3 instance → the Loader refuses it with a dependency message naming the Minecraft range. No crash, and no config file is written.
- **EC-PORT-02** The Hypixel Mod API mod is missing → the Loader reports the missing dependency. The mod never runs without it.
- **EC-PORT-03** Mod Menu is not installed → the mod loads normally, and settings stay reachable through `/ksu` and the keybind.
- **EC-PORT-04** Both 1.0.1 and the new jar are enabled in `mods/` → the Loader silently loads the newer one (research: no crash). The release notes tell 1.0.x users to remove the old jar (release).
- **EC-PORT-05** Iris shaders are on, or the experimental Vulkan backend is selected → labels render, or degrade visually without an exception. Never a crash.
- **EC-PORT-06** A waypoint sits exactly at the camera position (distance 0) or behind the camera → no NaN scale and no exception; the label is drawn or skipped.
- **EC-PORT-07** GUI scale or window size changes while labels are visible → label size and placement are recomputed on the next frame.
- **EC-PORT-08** Settings are opened from Mod Menu on the title screen, or from `/ksu` in a world → closing returns to the screen they were opened from, or to the game.
- **EC-PORT-09** Gradle or javac runs under a German Windows locale → the messages are localised, but the build result is identical. CI runs in English.

**Open questions**
- **Q-PORT-01** Ship the port plus the compliance fixes early, as v1.1.0+26.2 after G1? This decides whether the interim work in T1.4 and T1.12 is needed, and whether the first config migration reaches users before Phase 2. → decided D-13 (v1.1.0+26.2 after G1, warning on the v1.0.1 release notes, no 26.1.2 hotfix; the T1.4/T1.12 interim work is therefore needed, and the first migration reaches users before Phase 2). Release details (warning also on v1.0.0, early release via an intermediate PR to main) → decided R4.

---

### config-store — Config persistence & migration
**Origin:** Derived from Brief Phase 2 bullet 3 ("Migrate existing config values so users don't lose settings") and Ground rule 6. Also from defects the research found: the save race, defaults overwriting a file that failed to parse, and no schema version (read-core bugs 2–5; PLAN AD-3) | **Depends on:** port-26-2 | **Plan tasks:** T0.2, T0.3, T1.13, T1.7, T1.7c, T1.7b, T1.9b, T1.4a, T2.4c, T2.6, T2.7, T4.1 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Users must never lose settings across the 26.2 port, the UI rebuild, or later feature and updater changes. The config store must save atomically through one serialized path. It must survive corrupt or newer files without overwriting them, and it must carry a schema version with ordered, tested migrations.

**Functional requirements**
- **REQ-CFG-01** The mod must keep its settings at `config/k8bas_skyblock_utility.json`: `general` plus one section per module under `modules.<id>`. Module ids (`mob_highlighter`, `npc_search`), field names, enum constant names and the colour encoding (decimal 0xRRGGBB integers) must stay readable, and are renamed only by a migration step. Unknown module sections are kept unchanged when the file is saved. *(Brief: Phase 2 bullet 3; Derived: read-core §3)*
- **REQ-CFG-02** The file must carry an integer `configVersion`. A file without one is treated as 1.0.x (version 0). On load, every migration step from the file's version up to the current one runs exactly once and in order, before any module reads its section. The migrated file is then saved with the new version. *(Derived: AD-3)*
- **REQ-CFG-03** Loading a 1.0.1 config and saving it must preserve every value. That covers the general settings (both update flags, the scan range), every mob rule and every NPC rule with all of their fields. The only exceptions are values a documented migration step changes. *(Brief: Phase 2 bullet 3)*
- **REQ-CFG-04** Saves must be atomic. The new content is written in full to a temporary file in the same folder, then moved over the old file in one step. A crash or kill at any moment leaves either the previous complete file or the new complete file. *(Derived: read-core bug 4; AD-3)*
- **REQ-CFG-05** There must be exactly one save path:
  - at most one write is in progress at a time
  - requests made while a write is pending are merged into one
  - the content written is a consistent snapshot taken on the client thread
  - the last requested state always ends up on disk

  *(Derived: read-core bug 2 — one Save & Done today starts two async writes and one sync write; AD-3)*
- **REQ-CFG-06** If the file, or one module section in it, cannot be parsed or has wrong types, the mod must:
  - keep the original bytes as a timestamped backup next to the file before anything writes to the config path
  - use defaults only for the unreadable part, while the other sections load normally
  - not crash
  - log the failure
  - show one chat notice naming the backup after the player joins a world

  *(Derived: read-core bugs 3 and 5 — today defaults silently overwrite the file, and a type mismatch in a section crashes init; AD-3)*
- **REQ-CFG-07** Loading must clean up values that Gson leaves invalid:
  - an unknown or null match mode becomes CONTAINS
  - a null rule list becomes empty
  - a null `general` becomes defaults
  - a negative scan range becomes 0 (unlimited)
  - a missing rule id is generated once and saved

  Null values are never written, so the file doesn't churn. *(Derived: read-core "Gson behaviours"; read-mob bug 7 — a null match mode crashes inside rendering)*
- **REQ-CFG-08** If `configVersion` is higher than the running mod knows, for example after an update is rolled back, the mod must back up the file before its first save and log a warning. It must never drop the newer version's fields without that backup. *(Derived: updater reconciliation, PLAN T4.3; the goal that users never lose settings)*
- **REQ-CFG-09** Each migration step must be a pure transformation of the JSON tree with its own number. The phase that introduces a step takes the next free number (PLAN §5), and every step has fixture tests. *(Derived: AD-3; PLAN §5 Git)*
- **REQ-CFG-10** [decided D-8] A change made in-game must be on disk within 2 s: of the change itself for keybinds and commands, and of closing the settings screen for changes made there (live-apply with save on close). It is saved again on normal client shutdown. Quitting right after a change then loses nothing. *(Derived: AD-3 debounced save; D-8 "live-apply with save on close")*
- **REQ-CFG-11** Before the first migration that changes data, the mod must keep a one-time copy of the pre-migration file, for example `k8bas_skyblock_utility.json.v0.bak`, and never overwrite it later. *(Derived: Brief Phase 2 bullet 3 — every migration can then be undone by hand)*
- **REQ-CFG-12** Every other file the mod persists (per-profile state, updater state, any future separate file) must get the same guarantees: atomic writes, one save path, versioned migrations. HUD positions are not a separate file: they live in the config file (section `general.hud.positions`, REQ-HUD-03) and migrate through REQ-CFG-02. *(Derived: PLAN §10, "User state … saved atomically with migrations"; T3.0d; T4.1)*
- **REQ-CFG-13** The keybind names the mod registers with the game must not change, so bindings the game stores in `options.txt` survive the port and the UI rebuild. *(Brief: Phase 2 bullet 3; PLAN T2.6 accept)*

**Out of scope**
- Config sync, import or export; locking between game instances.
- Picking up hand edits while the game runs: the next save overwrites them, and this is documented.
- The contents of migrations owned by other modules: the island split (location), the updater flags (updater), beam colours (npc-waypoints), removing Cloth (ui-config). Each of those modules specifies its own step.
- The schemas of per-profile and SkyBlock XP data (game-state, sbxp-optimizer). Those modules only reuse the facility from REQ-CFG-12.
- Downgrading to 1.0.x. That isn't possible across Minecraft versions anyway.

**Acceptance criteria**
- **AC-CFG-01** (REQ-CFG-01, REQ-CFG-03) Given a fixture shaped like the user's 1.0.1 config (general {check true, download true, scan range 128}; 5 mob rules; 5 NPC rules including 3 moving "Catacombs" rules), when the new version loads and saves it, then:
  - every field equals the original or the documented migrated value
  - `configVersion` equals the current version
  - no other new key appears beyond the documented ones

  — [A]
- **AC-CFG-02** (REQ-CFG-02, REQ-CFG-09) Given files at version 0, at an intermediate version and at the current version, when they load, then exactly the missing steps run, in ascending order. Loading the result a second time produces byte-identical output — [A]
- **AC-CFG-03** (REQ-CFG-04) Given a save that a test hook interrupts after the temporary file is written but before the move, then the config path still holds the previous complete file and it parses — [A]
- **AC-CFG-04** (REQ-CFG-05) Given 100 save requests within 1 s from the client thread, plus 1 from another thread, when they run against an instrumented writer, then at most one write is ever active at a time and the final file equals the last requested state — [A]
- **AC-CFG-05** (REQ-CFG-06) Given a config file containing invalid JSON, when the mod loads and later saves:
  - a backup holding the exact original bytes exists before the config path is written
  - the mod runs on defaults
  - exactly one chat notice naming the backup appears after the player joins a world

  — [A] [B]
- **AC-CFG-06** (REQ-CFG-06) Given a valid file in which `modules.npc_search.rules` is a string, when it loads, then the game does not crash, NPC Search uses defaults, the mob rules load intact, and a backup is written — [A]
- **AC-CFG-07** (REQ-CFG-07) Given a rule with `nameMatchMode: "FOO"`, a section with `rules: null` and `general: null`, when the file loads, then the rule uses CONTAINS, the list is empty, `general` has defaults, and evaluating the rule against an entity throws nothing — [A]
- **AC-CFG-08** (REQ-CFG-08) Given `configVersion` = current + 1, when the mod saves for the first time, then a backup of the original exists and one warning is logged — [A]
- **AC-CFG-09** (REQ-CFG-10) Given a fake clock, when a module is toggled, then the file reflects the change at most 2 s later. Given a normal client shutdown 0.2 s after a change, then the file reflects the change — [A] [B]
- **AC-CFG-10** (REQ-CFG-11) Given a version-0 file, when the first migration runs, then the `.v0.bak` copy equals the original bytes, and a later migration does not touch it — [A]
- **AC-CFG-11** (REQ-CFG-12) Given a second persisted file type that uses the shared facility (a test file), then AC-CFG-03 and AC-CFG-04 also pass for it — [A]
- **AC-CFG-12** (REQ-CFG-13) Given a 1.0.1 `options.txt` with the mod's three key lines bound, when the new version starts, then all three bindings are still active (the key names are unchanged) — [B] [R]
- **AC-CFG-13** (REQ-CFG-01) Given a file with a section for an unknown module id, when the mod saves, then that section is still present and unchanged — [A]

**Edge cases**
- **EC-CFG-01** A 0-byte config file → treated as a parse failure (backup plus defaults), not as a missing file.
- **EC-CFG-02** A hand-edited file saved with a UTF-8 BOM → it parses normally.
- **EC-CFG-03** The config folder is read-only or the disk is full → the write fails, the previous file stays intact, in-memory settings are kept, and the failure is logged once and shown once in chat. The next change retries.
- **EC-CFG-04** On Windows the target file is briefly locked by an antivirus or indexer → the replace is retried (at least 3 attempts within 1 s), then logged. The temporary file is removed.
- **EC-CFG-05** The game is killed during a migration → the next launch sees the old version and migrates again with the same result.
- **EC-CFG-06** A temporary file left over from a crash → ignored on load and removed after the next successful save.
- **EC-CFG-07** Labels containing an apostrophe, `§` or non-ASCII text (for example "Spider's Den", which Gson stores as `'`) → they round-trip to equal strings.
- **EC-CFG-08** A keybind tick and the update check request saves at the same moment → the saves are serialized and no file is torn.
- **EC-CFG-09** A rule with an invalid regex → kept in the file unchanged, never dropped, and inert at runtime (REQ-GLOW-10).
- **EC-CFG-10** A config copied from a 26.1.2 Prism instance → handled as version 0 and migrated.

---

### location — Hypixel location & island detection
**Origin:** Derived. This is the root cause behind Brief Phase 3 item 1: the Trinity/Tomioka/Duncan rules never fire because mode `dungeon` is unmapped (research G5). It is also a prerequisite for items 2, 6 and 7 (and item 3 until R22 dropped it) and for Phases 5–6 (mineshaft, fishing islands, per-run state) | **Depends on:** port-26-2, plus config-store for its migration step | **Plan tasks:** T0.4, T0.4c, T1.9, T1.9b, T2.5a, T3.0m (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Features need to know which SkyBlock island and which server instance the player is on, so they can gate by island and reset per run or per shaft. That knowledge must come only from Hypixel's official Mod API location event. Today the table maps a mode Hypixel never sends (`catacombs`), lumps the Dungeon Hub together with runs, and is missing `dungeon`, `mineshaft` and other modes.

**Functional requirements**
- **REQ-LOC-01** Location must come only from the Hypixel Mod API location event that the client subscribes to. The mod must not send `/locraw` or any other chat or command, and must not infer the island from chat. *(Brief: Ground rule 5; Derived: rules policy P5)*
- **REQ-LOC-02** The table that maps a mode to an island name must cover every SkyBlock mode seen in the user's logs and in the reference mods:

  | Mode | Island name |
  |---|---|
  | `dynamic` | Private Island |
  | `hub` | Hub |
  | `garden` | Garden |
  | `farming_1` | The Farming Islands |
  | `foraging_1` | The Park |
  | `foraging_2` | Moonglade Marsh |
  | `foraging_3` | Torrhus Canyon |
  | `combat_1` | Spider's Den |
  | `combat_3` | The End |
  | `crimson_isle` | Crimson Isle |
  | `mining_1` | Gold Mine |
  | `mining_2` | Deep Caverns |
  | `mining_3` | Dwarven Mines |
  | `crystal_hollows` | Crystal Hollows |
  | `mineshaft` | Glacite Mineshafts |
  | `fishing_1` | Backwater Bayou |
  | `lotus_atoll` | Lotus Atoll |
  | `safari` | Critter Safari |
  | `kuudra` | Kuudra |
  | `winter` | Jerry |
  | `rift` | The Rift |
  | `dark_auction` | Dark Auction |
  | `dungeon` / `dungeon_hub` | see REQ-LOC-03 |

  The keys Hypixel never sends (`catacombs`, `jerry`) are removed. Island names already stored in rules and data keep their exact strings. *(Derived: read-core §8; G4 log tally; G5 §2)*
- **REQ-LOC-03** [decided D-2] Dungeons are split into two islands:
  - `dungeon` → "Catacombs" (inside runs) and `dungeon_hub` → "Dungeon Hub".
  - A one-time migration through config-store moves every NPC Search rule that has a fixed position and island "Catacombs" to "Dungeon Hub". Moving-NPC rules and all mob rules keep "Catacombs".
  - NPC data entries that are fixed and on "Catacombs" are normalised to "Dungeon Hub" when loaded, so the picker creates "Dungeon Hub" rules.

  *(Brief: Phase 3 item 1, "fix it if needed"; Derived: G5 §5; PLAN D-2, T1.9)*
- **REQ-LOC-04** The current location must be available as one immutable snapshot: raw mode, map, server name, server type, the mapped island name (or none), and whether the player is on SkyBlock. An island is reported only when the server type is SkyBlock. Reads are safe from any thread. *(Derived: read-core §8; PLAN T1.9, "Expose raw mode/map/server")*
- **REQ-LOC-05** Consumers must be able to subscribe to location changes. An event fires once on the client thread whenever the server name or the mode differs from the previous snapshot, and once on disconnect. Every dungeon run and every mineshaft is a new server. *(Derived: needed by REQ-GLOW-14 (per-run reset), mineshaft-alert, fishing-hotspot and rare-drop-odds; read-core §8; G5 §6)*
- **REQ-LOC-06** An unmapped mode gives no island, but the raw mode stays in the snapshot. Each distinct unmapped mode is logged once per session. *(Derived: PLAN T1.9, "log unknown modes once")*
- **REQ-LOC-07** On disconnect, and on a world change before the next location event arrives, the island must be cleared, so no feature gates on the previous server's island. *(Derived: read-core §8 — today the old value stays during transfers; this stops Dungeon Hub waypoints flashing into a new run)*
- **REQ-LOC-08** A dev-only command must let a tester force a mode or island in `runClient`. It must not exist in production builds. Only the two simulators are dev-only: `/ksu debug island` and `/ksu debug shaft` (a simulated mineshaft for gametests such as the corpse odds panel of AC-ODDS-04; its first user, AC-CORPSE-03, was dropped with R22). The read-only capture commands ship in production (REQ-GS-13). *(Derived: REQ-XC-VERIFY; PLAN T0.4)*
- **REQ-LOC-09** The list of island names, each with a short description, must be available to the UI, so island fields are picked from a list instead of typed. Examples: "Catacombs — inside dungeon runs", "Dungeon Hub — dungeon lobby". *(Derived: G5 §5; read-mob — free-text island fields allow silent typos)*
- **REQ-LOC-10** No build may ship the "Catacombs" run mapping (REQ-LOC-03) without the invisible-entity exclusion (REQ-GLOW-03), because the mapping switches on Catacombs rules for invisible mobs such as Shadow Assassin and Fels. *(Brief: Ground rule 5; Derived: G5 §4)*

**Out of scope**
- Sub-areas, dungeon floor or mineshaft variant from the sidebar, tab list or chat (game-state, mineshaft-alert).
- Special handling for Hypixel's BETA or TEST network; it is treated like production.
- Locations on non-Hypixel servers or in singleplayer; there the island is always none.
- Renaming stored island names to newer in-game names (for example `foraging_2` "Moonglade Marsh" vs the map name "Galatea"). That would need its own migration step.
- Replacing the Hypixel Mod API, or adding a chat- or scoreboard-based fallback.

**Acceptance criteria**
- **AC-LOC-01** (REQ-LOC-02) Given a table-driven test with every mode string seen in the user's logs, when each is mapped, then each gives its expected island name, and `catacombs` and `jerry` give none. The modes are `mining_3`, `mineshaft`, `dynamic`, `hub`, `garden`, `farming_1`, `crimson_isle`, `dungeon_hub`, `dungeon`, `foraging_2`, `combat_3`, `fishing_1`, `combat_1`, `mining_1` and `crystal_hollows` — [A]
- **AC-LOC-02** (REQ-LOC-03) Given a version-0 fixture containing a fixed Croesus rule on "Catacombs", the three moving Trinity/Tomioka/Duncan rules on "Catacombs" and one mob rule on "Catacombs", when the migration runs, then only the Croesus rule becomes "Dungeon Hub". A second load changes nothing — [A]
- **AC-LOC-03** (REQ-LOC-03) Given NPC data with a fixed Croesus entry and a moving Trinity entry, both on "Catacombs", when the data loads, then Croesus is listed under "Dungeon Hub" and Trinity under "Catacombs". Adding Croesus from the picker creates a "Dungeon Hub" rule — [A]
- **AC-LOC-04** (REQ-LOC-03, REQ-LOC-07) Given `runClient` with a fixed Croesus waypoint, when the island is forced to "Dungeon Hub", then the waypoint renders; when it is forced to "Catacombs", it does not. On Hypixel: hub waypoints show in the Dungeon Hub and never inside a run — [B] [E]
- **AC-LOC-05** (REQ-LOC-04, REQ-LOC-05) Given the packet sequence hub/mini1 → hub/mini1 (repeat) → mineshaft/mini2 → mineshaft/mini3, then subscribers get exactly 3 change events, each on the client thread and each carrying the right snapshot — [A]
- **AC-LOC-06** (REQ-LOC-06) Given the unknown mode "limbo" twice, then the island is none, the raw mode is "limbo", and exactly one log line is written — [A]
- **AC-LOC-07** (REQ-LOC-07) Given an island of "Dungeon Hub", when a disconnect or a world change happens without a new packet, then the island is none and subscribers are told — [A]
- **AC-LOC-08** (REQ-LOC-01) Given the location code, then it sends no chat message or command and reads no scoreboard or chat to find the island — [R]
- **AC-LOC-09** (REQ-LOC-08) Given `runClient`, then `/ksu debug island Catacombs` sets the island. Given a production boot of the release jar, then the command does not exist — [B] [D]
- **AC-LOC-10** (REQ-LOC-10) Given the branch history and the release jar, then the commit with the invisible-entity exclusion comes before the dungeon-mapping commit, and every tagged build contains both — [R]
- **AC-LOC-11** (REQ-LOC-02, REQ-LOC-04) Given the user's Hypixel smoke test, then `latest.log` shows the islands "Hub", "Dungeon Hub", "Catacombs" (in a run) and "Glacite Mineshafts" with their raw mode and server — [E]
- **AC-LOC-12** (REQ-LOC-04) Given a location packet whose server type is not SkyBlock, then the island is none and "on SkyBlock" is false — [A]
- **AC-LOC-13** (REQ-LOC-09) Given the exposed island list, then it contains every name from REQ-LOC-02/03 exactly once, each with a description — [A]

**Edge cases**
- **EC-LOC-01** Hypixel sends Hello on every server switch → the subscription stays single, with no duplicate handlers or events.
- **EC-LOC-02** The same location is sent again for the same server → no change event.
- **EC-LOC-03** A party warp goes from the Dungeon Hub straight into a run → the island is cleared on the world change, then becomes "Catacombs". Hub waypoints are never drawn in the run.
- **EC-LOC-04** Limbo/AFK, or a non-SkyBlock Hypixel game → no island; every island-gated feature stays inactive.
- **EC-LOC-05** Singleplayer or a non-Hypixel server → no location event ever arrives; the island stays none and nothing errors.
- **EC-LOC-06** A location event arrives during login, before the player entity exists → the snapshot is stored, and the change event is delivered on the client thread.
- **EC-LOC-07** Hypixel adds a new island mode → handled by the unknown-mode path (REQ-LOC-06); no crash.
- **EC-LOC-08** A rule has a hand-typed island that matches no name (for example lowercase "catacombs") → it never matches, and the UI flags it.
- **EC-LOC-09** Another mod uses a different Hypixel location library (Skyblocker's HM API) → our subscription is unaffected.
- **EC-LOC-10** Each new mineshaft has a new server name (mode `mineshaft`, map "Mineshaft") → one change event per shaft.

**Open questions**
- **Q-LOC-01** Split Dungeon Hub from Catacombs runs, or merge them? → decided D-2 (split, with migration)

---

### glow — Rule-based glow (Mob Highlighter, NPC Search) & Trinity/Tomioka/Duncan
**Origin:** Brief Phase 3 item 1; Brief Phase 1 (existing features keep working); Ground rules 5 and 6 | **Depends on:** port-26-2, location, config-store | **Plan tasks:** T0.2, T0.3, T1.8, T1.8b, T1.8c, T1.9b, T1.10a, T1.10, T1.11, T1.11b, T1.14, T1.16, T2.1, T2.5a, T3.1, T7.1 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Keep the existing rule-based glow working on 26.2 and make it compliant. It glows only what the player can already see, never invisible entities, and never changes glow the server sets. It also makes the three rare dungeon NPCs glow inside runs by fixing the island bug. The "You found X" title then fires only once the NPC is actually in sight, once per run, and only for five special moving NPCs [decided R20].

**Functional requirements**
- **REQ-GLOW-01** Mob Highlighter and NPC Search moving-NPC rules must keep matching as in 1.0.1:
  - entity type filter (blank means any type)
  - island gate
  - distance gate: the per-rule maximum distance and the general scan range (default 64, 0 = unlimited, UI 0–128) apply to both modules
  - name match modes NONE, CONTAINS, REGEX and EXACT on the colour-stripped name tag
  - per-rule colour, module toggles and toggle keybinds

  *(Brief: Phase 1 bullet 2; Ground rule 6)*
- **REQ-GLOW-02** [decided D-1] Glow must be depth-tested:
  - an entity fully hidden behind solid or cutout blocks shows no glow
  - a partly hidden entity glows only on its visible parts
  - no setting, keybind or command turns off the depth test

  *(Brief: Ground rule 5; Derived: G3 verdict RED for see-through glow — Allowed Modifications "see around or over objects", Modrinth §3; rules policy P1)*
- **REQ-GLOW-03** An invisible entity must never glow and must never trigger a title or alert, even when a rule matches it. This is hard-coded, with no toggle. It also covers invisible entities wearing visible armour. Once the entity becomes visible, glow may start. *(Brief: Ground rule 5; Derived: Hypixel 2021 announcement, "Displaying invisible entities … BANS BEING ISSUED"; rules policy P2)*
- **REQ-GLOW-04** Glow the server already applies to an entity must be left exactly as the server sets it: never recoloured, removed or extended. *(Derived: AD-1 — recolouring server glow would make our colour visible behind blocks; rules policy P1)*
- **REQ-GLOW-05** [decided D-1] The mod must not change how the game or other mods decide whether an entity is visible or should be culled. There must be no "force glowing" override. *(Derived: G3/AD-1 — the 1.0.1 override un-culls entities behind terrain, and it caused an off-thread world-query race that matches the user's 2026-08-21 crash)*
- **REQ-GLOW-06** Rule matching must run only on the client thread. The render path only looks up precomputed results, and each entity's name tag is resolved at most once per tick. *(Derived: G4 risk (c); read-mob performance — today matching runs about twice per entity per frame and sometimes off-thread)*
- **REQ-GLOW-07** With the user's 26.2 mod set (Skyblocker, SkyHanni, NoFrills, EntityCulling, Sodium, Iris and others), the game must boot and play without a crash or a new ERROR from this mod. The order in which the glow providers are consulted is recorded in the Phase 1 report. *(Derived: G4 tier D; PLAN risk table — the glow callback is shared and the first provider wins)*
- **REQ-GLOW-08** When Mob Highlighter and NPC Search both match the same entity, Mob Highlighter's colour wins, as today. Within one module, the first matching rule in list order wins. *(Derived: read-mob — current behaviour preserved)*
- **REQ-GLOW-09** Name-tag resolution must skip action-label lines ("CLICK" and the other labels Hypixel puts under NPCs) and empty lines. It should also skip lines whose name is not shown, then take the nearest remaining line. An NPC with a name line plus a "CLICK" line then resolves to its name. *(Brief: Phase 3 item 1, "fix it if needed"; Derived: G5 §3 — with nearest-stand matching the name may resolve to "CLICK"; PLAN T3.1)*
- **REQ-GLOW-10** A rule that can't be evaluated must be inert and never match everything. That covers an empty pattern in CONTAINS or EXACT mode, an invalid regex, an unparsable entity type, and a rule that ignores names (NONE) without an entity type, which would outline every visible entity, players included [decided R18]. Each such rule is logged once and flagged in the UI (in the rule editor: a warning sign in its title and a line saying why), and it never throws during rendering. *(Derived: read-mob bugs 6–9 — an empty CONTAINS pattern lights up every entity, and a typo'd type becomes "any type")*
- **REQ-GLOW-11** The local player must never be highlighted. Other real players should not be matched by name-tag rules: their own name is never read, so any match comes from a neighbouring name tag and is a false positive. NPCs that are player-type entities (Hypixel NPCs, Shadow Assassin) stay matchable. *(Derived: read-mob/G5 — players standing near a matched NPC, or the local player in F5, glow by mistake)*
- **REQ-GLOW-12** Inside a Catacombs run (mode `dungeon`), Trinity, Tomioka and Duncan must each glow in their rule's colour once they are in view, when their rule is enabled. That includes the user's existing rules (moving NPC, island "Catacombs", CONTAINS name). *(Brief: Phase 3 item 1; depends on REQ-LOC-03 and REQ-GLOW-09)*
- **REQ-GLOW-13** [decided R1] The glow for these three NPCs must be switchable on its own:
  - each NPC Search rule has its own on/off switch
  - the NPC data picker offers all three under "Catacombs"
  - a fresh install glows none of them until the user adds the rule
  - the user's existing three rules stay enabled after migration

  *(Brief: Phase 3 "each behind its own toggle"; Ground rule 6)*
- **REQ-GLOW-14** [decided D-6, R20] The "You found <label>" title, in the rule colour:
  - fires only for five special moving NPCs: Trinity, Tomioka and Duncan (Catacombs), and Xalx and Pete (Crystal Hollows) [decided R20]. A rule qualifies when its NPC data sourceId is `trinity`, `tomioka`, `duncan`, `xalx` or `pete`, or, for a hand-made rule without a sourceId, when its label equals one of those five names, ignoring case and surrounding spaces. Every other NPC Search rule still outlines its NPC but never triggers the title
  - appears only after the local player has an unobstructed line of sight to the matched entity, within the scan range
  - appears at most once per rule per server instance, and resets on every location change (REQ-LOC-05), so it can show again in the next run
  - has its own toggle, default ON
  - never fires for an invisible entity, and never runs off the client thread

  *(Brief: Phase 3 item 1; Derived: G3 rules policy P3; G5 §6 — today it fires from render extraction once per game launch; PLAN T1.11)*
- **REQ-GLOW-15** The behaviour changes must be documented in `CHANGELOG.md` and the README compliance section, and shown as an in-game tooltip on the highlight modules:
  - glow is visible-only
  - invisible mobs are never outlined
  - NPCs are outlined only while in view; blocks hide the outline
  - the title appears after line of sight, once per run, and only for Trinity, Tomioka, Duncan, Xalx and Pete [decided R20]

  *(Derived: PLAN risk table "reported as a regression"; rules policy P6 tooltips; Brief Phase 1 bullet 3)*
- **REQ-GLOW-16** With shaders (Iris) on, glow must either render depth-tested or not render at all. It must never show behind blocks that hide the entity. *(Derived: G4 risk (a) — Iris logs a missing entity_outline program with the user's shader pack)*
- **REQ-GLOW-17** [decided D-1] If a third-party library provides the depth-tested glow, it ships as a separate bundled jar together with its licence text, and none of its code is copied into this repo (REQ-XC-LICENSE). *(Brief: Ground rule 4; PLAN D-1 — Render Chest, Apache-2.0)*

**Out of scope**
- Any see-through glow (glow visible behind blocks), including as an opt-in. Any form of revealing invisible entities, including particle-based boxes.
- Custom glow styles (thickness, fill, per-entity style) and recolouring server glow.
- Glowing armour stands: corpses, hotspot markers and holograms belong to other features.
- Beams or waypoints on moving NPCs (npc-waypoints).
- Rare-room detection, which Skyblocker already provides.
- A dedicated "Dungeon NPC glow" module [decided R1].
- Hiding the title automatically when Skyblocker is installed [decided D-6].

**Acceptance criteria**
- **AC-GLOW-01** (REQ-GLOW-01) Given characterization tests for NONE, CONTAINS, REGEX and EXACT, `§` stripping, the island gate, the distance gate and the entity-type filter, then they pass with identical results on 26.1.2 before the port and on 26.2 after it — [A]
- **AC-GLOW-02** (REQ-GLOW-02) Given a client gametest with a named zombie matched by a rule in magenta (#FF00FF), when the zombie is in view, then the screenshot region holds more than 50 magenta pixels. With the zombie behind a 3-block stone wall, it holds 0 — [C]
- **AC-GLOW-03** (REQ-GLOW-03) Given the same zombie with the Invisibility effect, then it has 0 magenta pixels and no title. Within 1 s of the effect ending, magenta pixels appear. A unit test of the eligibility check covers invisible, invisible with armour, and visible — [A] [C]
- **AC-GLOW-04** (REQ-GLOW-04) Given a matched zombie that also has the vanilla Glowing effect, then its outline shows the vanilla team colour (white), not magenta — [C]
- **AC-GLOW-05** (REQ-GLOW-05, REQ-GLOW-06) Given the code, then no hook forces an entity's glowing or visibility state. Given a test that asserts the thread, then every world query made while matching runs on the client thread. Given EntityCulling with safeMode off and 2 minutes in a busy area, then no exception occurs — [R] [A] [E]
- **AC-GLOW-06** (REQ-GLOW-07) Given a production boot with a copy of the user's mod set, then there is no crash and no new ERROR from this mod, and the Phase 1 report records the glow-provider order. On Hypixel, glow appears on a known visible mob with the Skyblocker/SkyHanni/NoFrills glow options on and off — [D] [E]
- **AC-GLOW-07** (REQ-GLOW-08) Given an entity matched by both modules, then the Mob Highlighter colour is used. Given two matching rules in one module, then the first one in the list is used — [A]
- **AC-GLOW-08** (REQ-GLOW-09) Given these name-line candidate lists, then the resolver picks:
  - "Tomioka" 2.0 above the feet plus "CLICK" 1.7 above → "Tomioka"
  - "CLICK" only → the entity's own name or empty
  - one mob tag alone → that tag, unchanged
  - a line whose name is hidden → ignored

  Given an entity dump at the G1 smoke (Mort in a run, or a Dungeon Hub NPC), then the resolved name is the NPC's name — [A] [E]
- **AC-GLOW-09** (REQ-GLOW-10) Given a CONTAINS rule with an empty pattern, an invalid-regex rule, a rule with entity type "minecraft:zombi" and a NONE rule without an entity type, then none of them matches any entity, each is logged once, and nothing throws — [A]. The rule editor marks the NONE rule and explains why, and leaves a valid rule unmarked — [C]
- **AC-GLOW-10** (REQ-GLOW-11) Given the local player in third person next to a matched NPC's name tag, and a player entity with a standard player UUID standing there too, then neither glows. Given a player-type NPC with a non-standard UUID, then it still matches — [A] [C]
- **AC-GLOW-11** (REQ-GLOW-12) Given a gametest with a player-type stand-in entity carrying a "Trinity" name line and a "CLICK" line, the island forced to "Catacombs" and a rule shaped like the user's, then it glows in view and not behind a wall. On Hypixel the check is opportunistic, whenever a rare room appears; if none appears before release, the phase report says so — [C] [E]
- **AC-GLOW-12** (REQ-GLOW-13) Given a fresh install, then no NPC glows. Given the user's 1.0.1-shaped config, after migration, then the three rules are present and enabled, and switching one off stops only that NPC's glow — [A] [B]
- **AC-GLOW-13** (REQ-GLOW-14) Given unit tests of the title gate, then:
  - a match without line of sight → no title
  - a match with line of sight → exactly one title
  - a second match by the same rule on the same server → no title
  - after a location change → a title again
  - toggle off → no title
  - an invisible entity → no title
  - a rule for a non-special NPC (sourceId and label not among the five) → never a title, while its NPC still glows [decided R20]
  - each of the sourceIds `trinity`, `tomioka`, `duncan`, `xalx` and `pete` → a title; a hand-made rule without a sourceId labelled " trinity " → a title; a rule with another sourceId labelled "Trinity" → no title [decided R20]

  A gametest with the NPC behind a wall shows no title; walking into view shows it. A matched non-special NPC in view shows no title — [A] [C]
- **AC-GLOW-14** (REQ-GLOW-15) Given `CHANGELOG.md`, the README and the highlight module cards, then each states the four behaviour changes and none uses a P7 word (REQ-XC-RULES-07). The card's tooltip text matches the CHANGELOG wording — [R] [C]
- **AC-GLOW-15** (REQ-GLOW-16) Given the user's smoke test with Iris shaders on and off, then a matched mob behind a wall never shows glow in either mode — [E]
- **AC-GLOW-16** (REQ-GLOW-17) Given the release jar, then any bundled glow library sits as a nested jar with its LICENSE, and `THIRD_PARTY_NOTICES.md` lists it — [R]
- **AC-GLOW-17** (REQ-GLOW-01) Given a rule with colour 0x000000, then the entity glows black and is not treated as "no glow" — [A] [C]

**Edge cases**
- **EC-GLOW-01** A Shadow Assassin turns invisible while glowing → its glow stops within one tick. When it reappears, glow resumes.
- **EC-GLOW-02** The NPC is visible but behind glass → it glows (glass doesn't hide it), but the title waits, because glass blocks the line-of-sight check. This conservative behaviour is documented.
- **EC-GLOW-03** Another mod claims the entity's glow first (shared first-wins callback) → our colour isn't shown for that entity. No crash; documented.
- **EC-GLOW-04** Skyblocker's Livid-fight glow option removes glow from other entities → our glow may vanish during that fight. Documented; no crash.
- **EC-GLOW-05** A busy island with more than 200 loaded entities and more than 20 rules → each name tag is still resolved at most once per entity per tick (REQ-GLOW-06).
- **EC-GLOW-06** Disconnect or server switch in the middle of a run → the found-set resets and no glow carries over to the next world.
- **EC-GLOW-07** The same rare NPC appears in two consecutive runs → the title shows once in each run.
- **EC-GLOW-08** A rule matches while a screen is open → the title is queued as a normal HUD title and is visible after the screen closes. No exception.
- **EC-GLOW-09** The user has existing rules for invisible-type mobs (Ghost, Sneaky Creeper, Fels, Invisibug) → the rules stay but outline only while the mob is visible. The UI marks them (npc-mob-data T3.8). [decided R5] The data picker shows such invisible-by-design mobs greyed, not addable, labelled "never highlighted (Hypixel rules)".
- **EC-GLOW-10** A rule's island is "Catacombs" while the player is in the Dungeon Hub (after the split) → the rule does not match there.
- **EC-GLOW-11** Spectator mode with vanilla's spectator outline key → vanilla behaviour is unaffected.

**Open questions**
- **Q-GLOW-01** Accept visible-only glow with no see-through option and a permanent exclusion of invisible entities, even though Trinity/Tomioka/Duncan then glow only once in view? → decided D-1 (yes: depth-tested glow via bundled Render Chest, invisible entities permanently excluded, no see-through option)
- **Q-GLOW-02** Where does the Trinity/Tomioka/Duncan glow's "own toggle" (Brief Phase 3) live: per-rule switches in NPC Search (recommended; the text is written for this), or a dedicated built-in card? → decided R1
- **Q-GLOW-03** "You found X" title: default ON, shown after line of sight and reset per run, or default OFF / off automatically when Skyblocker is installed? → decided D-6 (default ON, after line of sight, reset per run; not switched off when Skyblocker is installed). After the G1 smoke, R20 limits the title to Trinity, Tomioka, Duncan, Xalx and Pete

---

### ui-config — Config screen and UI kit (AlpakaAddons-style)
**Origin:** Brief Phase 2; Brief ground rules 4, 5, 6 | **Depends on:** port-26-2, config-store | **Plan tasks:** T2.1, T2.2, T2.3a, T2.3b, T2.3c, T2.3d, T2.4a, T2.4b, T2.4c, T2.4d, T2.5a, T2.5c, T2.5b, T2.6, T2.8b, T2.9, T2.9b, T2.9d, T3.8c, T4.4, T4.4b, T6.7, T6.8 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Replace the Cloth Config settings screen with a custom screen whose layout, structure, categories, search and visual style feel like the AlpakaAddons config UI. It must copy no Alpaka code, assets or non-UI features, and every existing setting must survive. Its widgets are the UI kit that later phases build on (rule lists, updater confirmation, optimizer screen).

**Functional requirements**
- **REQ-UI-01** Layout. The settings screen must be one centred panel over a dimmed backdrop, with the game still visible around it. The panel has three parts:
  - a header strip with the mod name, the mod version and a search box at its right end, and a 1 px accent-coloured line along its bottom edge
  - a category sidebar on the left, with one tab per category and a count badge on each tab
  - a content area showing the selected category's heading and description, then its options as cards grouped under section headers, scrolling vertically

  It should use these metrics:
  - preferred panel size 70 % × 68 % of the GUI area, clamped to 480–660 × 340–440 GUI px, but never larger than the GUI area minus 4 GUI px per side (REQ-UI-02 wins over the minimum)
  - header 38 px tall, sidebar 160 px wide
  - option cards 44 px tall with 6 px gaps
  - outer panel corners square

  *(Brief: Phase 2 "layout, config screen structure, visual style"; metrics Derived: research-alpaka-ui §2, PLAN §7 Phase 2 spec)*
- **REQ-UI-02** Sizing robustness. This applies to every GUI area from 320×240 GUI px (the smallest vanilla allows) upward:
  - The panel must stay inside the window with at least 4 GUI px of margin on every side.
  - Nothing may be drawn outside the panel.
  - Every region whose content is taller than the region must scroll.
  - Opening the screen, resizing the window or changing the GUI scale while it is open must not crash the game or log a render or clipping error.

  *(Derived: brief Phase 2 plus ground rule 3 runClient sanity check; research-alpaka-ui §14 and Risks: Alpaka needed a fix for crashes caused by negative clip rectangles in small windows)*
- **REQ-UI-03** Hit-testing matches drawing.
  - A click inside a control's drawn bounds must activate that control and no other.
  - A click outside every control activates nothing, except where a card defines a whole-card action: a toggle card toggles on a click anywhere on the card.
  - A slider's value changes only from presses on its track.

  *(Derived: research-alpaka-ui §4, §14: Alpaka computes geometry separately for drawing and for clicks, which made clicks land on the wrong option)*
- **REQ-UI-04** Categories, in this order: General, Highlights, Waypoints, Mining, Fishing, Odds & Trackers, and SkyBlock XP (added with the optimizer). A category with no feature in the current build is hidden. Each feature card appears in exactly one category. A setting may move to a different place in the screen than in 1.0.1, but it does not move in the config file.

  Placement should be:

  | Category | Contents |
  |---|---|
  | General | Interface (accent colour; notice settings, REQ-UI-21), HUD ("Edit HUD layout"), Keybinds, Updates (content per the updater spec) |
  | Highlights | Mob Highlighter, including the mob scan range |
  | Waypoints | NPC Search: fixed NPC waypoints and name-matched NPC glow (including Trinity, Tomioka and Duncan as NPC Search rules [decided R1]), the "You found X" title, beam and label options |
  | Mining | Glacite Mineshaft alert, Drill re-equip fix (possible corpse spots [dropped R22]) |
  | Fishing | Bobber fix, hotspot highlight, "hotspot gone" warning |
  | Odds & Trackers | Rare Drop Odds, Bestiary Tracker |
  | SkyBlock XP | Optimizer settings |

  *(Brief: Phase 2 "categories"; list Derived: PLAN §7 Phase 2 spec, T6.8, research-alpaka-ui §15)*
- **REQ-UI-05** Feature cards.
  - Every user-facing feature must appear as a feature card with its own on/off toggle and its own sub-options. Each toggle's default is the one set in that feature's module spec (REQ-XC-TOGGLE).
  - When a feature is switched off, its sub-options stay visible and editable but are drawn dimmed.
  - Behaviour that is hard-coded for Hypixel-rule compliance must not be offered as a toggle. Examples: never highlighting invisible entities, always depth-testing entity highlights (PLAN §3 P1/P2) [decided D-1].
  - Features rated AMBER in PLAN §3 carry a tooltip that explains their restriction (REQ-UI-19).

  *(Brief: ground rule 6; Derived: PLAN §3 P1, P2, P6 / D-6)*
- **REQ-UI-06** Controls. The screen must provide:
  - a toggle switch
  - a numeric slider for integer and decimal values with a step. It also steps with the scroll wheel while hovered; Shift gives a fine step.
  - a single-choice dropdown that opens its list above the cards and closes on a click outside it. Choices are never faked with a slider.
  - a text field with:
    - caret and selection, plus Ctrl+A/C/X/V
    - Ctrl word jumps and word deletes, Home and End
    - paste that keeps only the first line
  - buttons in normal, primary and destructive styles
  - a colour swatch (REQ-UI-13)
  - keybind capture (REQ-UI-14)

  *(Brief: Phase 2 "rebuild this mod's config screen"; Derived: parity with the 1.0.1 Cloth screen, which uses toggles, an int slider, an enum selector, text fields, a colour field, keybind fields and buttons (read-ui))*
- **REQ-UI-07** Single option declaration.
  - Each option must be declared once, with a title, a description and optional hidden search keywords. The card shows at most 2 wrapped description lines; the full text goes in the tooltip.
  - The same declaration feeds both the card and the search index, so every option shown in the screen is found by searching its title.
  - Option declarations must not depend on a third-party UI library.

  *(Derived: complete search (REQ-UI-08) and removing Cloth Config (REQ-UI-23) both need this; PLAN AD-4)*
- **REQ-UI-08** Search.
  - Matching: typing in the search box filters options by case-insensitive substring over title, description, keywords and dropdown entry labels. The query is trimmed and limited to 35 characters.
  - Sidebar: it shows only categories with at least one match, and each badge shows that category's match count. If the active category has no match, the screen switches to the first category that has one.
  - No match anywhere: the content area shows `No settings found for "<q>"`.
  - Keys and controls: Ctrl+F focuses the box. Esc while the box is focused leaves the box without closing the screen. A clear (×) control empties the box.
  - Switching category keeps the query.

  *(Brief: Phase 2 "search"; behaviour Derived: research-alpaka-ui §5)*
- **REQ-UI-09** Search should also match rule cards by rule label and name pattern. A matching rule card is shown expanded inside its category. *(Derived: rule cards hold most of the user's configuration; research-alpaka-ui §5 recommendation)*
- **REQ-UI-10** Entry points:
  - `/ksu` and `/kskyblockutility` (client-side commands) open the screen on the next tick, so the chat's Enter key does not close it.
  - `/ksu <text>` opens the screen with `<text>` in the search box. The exception is when `<text>` starts with a reserved subcommand word (`hud`, `debug`, `update`, `sbxp`, and any subcommand a later module defines); that runs its own action instead. (`corpses` was on this list until R22 dropped `/ksu corpses` [dropped R22].)
  - The "Open settings" keybind opens the screen. It is unbound by default.
  - With Mod Menu installed, Mod Menu's configure button opens the screen. Without Mod Menu, the mod must still load and every other entry point must work.
  - Closing the screen returns to whatever screen it was opened from.

  *(Brief: Phase 2; Derived: the 1.0.1 entry points (read-ui), Alpaka's `/aa <term>` (research-alpaka-ui §5), subcommands from PLAN T0.4, T2.8, T4.4, T6.8; T3.3 dropped by R22)*
- **REQ-UI-11** Rule lists.
  - The Mob Highlighter and NPC Search rules must be editable as collapsible rule cards, one per rule.
  - The collapsed header shows the rule's label and a colour dot.
  - The expanded card shows every editable field: label, enabled, entity type, island restriction, name match mode, name pattern, colour, and fields that later features add (e.g. the optional per-rule beam colour override from npc-waypoints, separate from the glow colour [decided D-15]). It also has a destructive Delete.
  - Fixed NPC rules show their coordinates read-only.
  - "Add from database" opens a picker with a search field, island folders and an Add button per entry. Entries that already back a rule are hidden. Mobs that are invisible by design are shown greyed, cannot be added and are labelled "never highlighted (Hypixel rules)" (npc-db REQ-NPCDB-08) [decided R5].
  - Every add, edit and delete is kept (REQ-UI-15).

  *(Brief: Phase 2 rebuild; Derived: parity with the 1.0.1 screen (read-ui))*
- **REQ-UI-12** A REGEX name pattern that does not compile should be marked on its rule card with an error colour and a message, not silently dropped. *(Derived: read-ui bug #17)*
- **REQ-UI-13** Colour picker. A colour swatch must open a modal over the settings screen containing:
  - a hue/saturation wheel and a brightness slider
  - a hex field that accepts `#RRGGBB`, plus `#AARRGGBB` where the colour stores alpha
  - preset swatches
  - Cancel and Save buttons

  Its behaviour:
  - Alpha controls appear only for colours that store alpha; rule colours are RGB only.
  - Moving brightness to 0 and back must not lose the hue or saturation picked in the dialog.
  - Invalid hex input is not applied, and the last valid colour stays.
  - Cancel leaves the stored colour unchanged.

  *(Derived: parity with the existing colour wheel; fixes read-ui bugs #1 (HSB reset), #2 (colour-only changes lost) and #18 (unfiltered hex))*
- **REQ-UI-14** Keybind capture.
  - Each of the mod's keybinds (Open settings, Toggle Mob Highlighter, Toggle NPC Search, and later ones) has a capture widget that shows the current binding.
  - Click arms the widget and the next key binds. Esc unbinds, and right-click resets to the default.
  - Bindings are written to `options.txt` under the same key names as in 1.0.1, and they stay listed in vanilla Controls under the mod's category.

  *(Derived: parity with 1.0.1 (read-ui); Brief Phase 2 "users don't lose settings")*
- **REQ-UI-15** Save model [decided D-8].
  - Changes take effect immediately.
  - The config file is written through config-store's single save path. This happens when the screen closes by any route (Esc, another screen replacing it, disconnect) and on discrete commits (rule add or delete, colour picker Save, keybind capture).
  - A continuous drag (slider) writes at most once, on mouse release.
  - Each module's derived state is rebuilt once per close.
  - There is no "unsaved changes" prompt.

  *(Derived: D-8; read-ui bugs #2, #4, #5)*
- **REQ-UI-16** Migration, no setting lost.
  - Every value in a valid 1.0.1 config must appear in the new screen with the same value.
  - Opening and closing the screen without edits must leave every existing key with an equal value. The only exceptions are values changed by migration steps defined in other module specs (e.g. the location split, decided D-2, and the updater).
  - Existing field names and types are not changed; new settings are added as new keys with defaults.
  - A stored value outside a control's range (e.g. a hand-edited `mobScanRangeBlocks` of 200) is kept until the user changes that control.
  - Keybinds in `options.txt` are preserved.

  *(Brief: Phase 2 "Migrate existing config values so users don't lose settings")*
- **REQ-UI-17** Visual style [decided D-8: vanilla font, fill-based corners, teal accent].
  - Palette: a dark palette with tokens for panel, sidebar, card, card hover, border, and primary/muted/dark text.
  - Accent:
    - One accent colour is used for selection, focus, the header line and section titles.
    - It is configurable in General › Interface. The default is teal `#29B6B2`.
    - Destructive and error colours are fixed and do not follow the accent.
  - Shapes: cards, tabs and controls have rounded corners (radius 6/5/4 GUI px), drawn without access to private game render internals.
  - Text: the vanilla Minecraft font, without text shadow.
  - Motion and sound: hover and toggle changes ease over about 150 ms, and UI clicks play the vanilla button-click sound.

  *(Brief: Phase 2 "visual style"; values Derived: research-alpaka-ui §7, PLAN D-8, AD-4)*
- **REQ-UI-18** These may be added as optional stretch goals after G2 [decided D-8]:
  - an open animation (scale 0.90 → 1.00 over 220 ms)
  - a 200 ms slide-and-fade on category switch
  - keyboard focus navigation
  - anti-aliased (shader) rounded corners

  None of them is required for 2.0.0, and no acceptance criterion or checkpoint may wait for them. If one is built, it must not break any other REQ-UI requirement. The T2.9 multi-select dropdown is not a ui-config item: it is built with the optimizer table (sbxp-optimizer, PLAN T6.7). HUD snapping is REQ-HUD-14.

  *(Derived: research-alpaka-ui §6; PLAN T2.9, D-8 "T2.9 items as stretch goals")*
- **REQ-UI-19** Tooltips. An option with a tooltip shows it on hover as a multi-line, wrapped box that stays entirely inside the window at every GUI scale. Every feature rated AMBER in PLAN §3 has one. *(Derived: PLAN §3 P6 / D-6; T2.3c)*
- **REQ-UI-20** Long lists. Lists with many rows (the database picker with ~370 entries, long rule lists, the optimizer table with ~2,800 rows) must draw and hit-test only the visible rows, so cost does not grow with the row count. *(Derived: research GAP-1 §7–8 row volumes; T2.3c)*
- **REQ-UI-21** Notices (toasts) [decided D-8; uses decided R12].
  - Shape: a notice has a title and up to 2 body lines.
  - Position and duration: it appears in a configurable corner or top-centre and stays for a configurable 1–15 s. At most 4 are stacked, and the oldest is retired first.
  - Input: notices never take focus or input.
  - Actions: a notice may point to a screen, but it must never itself start an install or any other consequential action.
  - Uses:
    - Update prompts from the updater show a notice.
    - A warning feature may offer a notice as an extra channel, default OFF. In 2.0.0 only the "hotspot gone" warning does; the Glacite Mineshaft alert stays a chat line (mineshaft-alert out of scope). The channels each module spec takes from the brief (e.g. title, sound, chat) stay the defaults.
    - The early v1.1.0 release has no notices; its update check uses a chat line only [decided R4].

  *(Derived: D-8 "toasts for warnings and update prompts", R12; consumers PLAN T4.4, and as an optional channel T3.6b)*
- **REQ-UI-22** Input focus. While a text field or keybind capture is focused, key presses must not trigger game or mod keybinds. Esc in a focused field leaves the field before it can close the screen. *(Derived: research-alpaka-ui §12)*
- **REQ-UI-23** Cloth removal [decided D-8]. By the end of Phase 2 the mod must not depend on Cloth Config, at build time or at runtime. The files whose Javadoc says they were modelled on Cloth's source are deleted. *(Derived: D-8; Brief ground rule 4 provenance concern, PLAN §2)*
- **REQ-UI-24** Clean room, UI only.
  - No AlpakaAddons code, shaders, textures, logo, fonts or sounds may be included or translated. The UI is written from the written behaviour spec.
  - Alpaka's non-UI features are not reproduced (e.g. chat blur, custom pause/main menu, item viewmodel screens, command wheel, cosmetics).

  *(Brief: Phase 2 "UI only … Do not copy any non-UI features"; ground rule 4; REQ-XC-LICENSE)*
- **REQ-UI-25** Local only. The UI and its commands must not send packets, chat messages or server commands. All commands are client-side. *(Brief: ground rule 5; REQ-XC-RULES)*

**Out of scope**
- Copying anything from AlpakaAddons. Its non-UI features (chat blur, pause/main menu, viewmodel, item-size and block-overlay screens, command wheel, cosmetics).
- TrueType/OFL menu fonts, blur effects and a custom GUI shader pipeline in v1 (shader corners only as an optional stretch item, REQ-UI-18) [decided D-8].
- Importing settings from other mods (e.g. the user's Alpaka accent `#FF5252`).
- Translations: UI strings are English.
- An option to draw any highlight without depth testing (P1) [decided D-1]. A remote data source or "Refresh now" option [decided D-12]. A "Disable all" danger zone.
- Editors for fields the 1.0.1 screen did not expose (rule `maxDistance`, fixed NPC coordinates). They are kept, not editable.
- Screen-reader narration beyond vanilla defaults.
- Optimizer-only widgets (sortable table, number field with presets, multi-select filter, view switcher): specified under sbxp-optimizer (PLAN T6.7).
- The content of the update confirmation screen (updater module).

**Acceptance criteria**
- **AC-UI-01** (REQ-UI-01, REQ-UI-17) **Given** a 1920×1080 window at GUI scale 1 and a fresh config, **when** `/ksu` is run, **then** a screenshot shows, with each measurement within ±1 GUI px:
  - a centred 660×440 GUI px panel
  - a 38 px header with the mod name, version and a search box on the right, and a 1 px accent line under it
  - a 160 px sidebar with category tabs and count badges
  - 44 px option cards under section headers

  — [C]
- **AC-UI-02** (REQ-UI-02) **Given** a window of 854×480 at GUI scales 1 and 2, and of 1920×1080 at GUI scales 1–4, **when** the screen is opened and scrolled to the end of its longest category, and the window is resized from 1920×1080 to 854×480 while it is open, **then** in every screenshot:
  - the panel lies inside the window with at least 4 GUI px of margin
  - no widget is drawn outside the panel
  - `latest.log` contains no exception or render-pass error

  — [C]
- **AC-UI-03** (REQ-UI-03) **Given** the layout of a test category with one control of every type, **when** synthetic clicks are sent to each control's centre, to its inner edge and to 1 px outside it, **then** the centre and edge clicks reach only that control, and the outside click reaches no control except the whole-card toggle — [A]. The same clicks in a gametest change only the expected setting — [C]
- **AC-UI-04** (REQ-UI-04, REQ-UI-05) **Given** the build at each checkpoint, **when** each category is opened, **then**:
  - every feature in the spec appears as exactly one card with a toggle, in the category from REQ-UI-04
  - each toggle's default equals its module spec's default
  - switching a feature off dims its sub-options without hiding them
  - no card offers highlighting without depth testing or of invisible entities

  — [C] + [R]
- **AC-UI-05** (REQ-UI-06, REQ-UI-22) **Given** the text edit model, unit tests cover:
  - insert, and Backspace/Delete with and without Ctrl
  - Ctrl+A/C/X/V, Shift+arrow selection, Home/End
  - paste keeping only the first line, with tabs turned into spaces
  - the maximum length

  — [A]. **Given** the search box is focused in a world, **when** E and the bound "Open settings" key are pressed, **then** the inventory does not open and the screen stays open — [C]
- **AC-UI-06** (REQ-UI-06) **Given** a decimal slider with step 0.1, **when** the scroll wheel turns 3 notches up while it is hovered, **then** the value rises by 0.3 — [A]. **Given** an open single-choice dropdown, selecting an entry closes it with that value, and a click outside closes it unchanged — [C]
- **AC-UI-07** (REQ-UI-07, REQ-UI-08) **Given** the search index of all registered options, searching each option's title returns that option — [A]. The following also hold — [A] + [C]:
  - "range" returns "Mob scan range", and "  RANGE " gives the same result.
  - Badges equal the per-category hit counts.
  - An active category without a hit switches to the first category with one.
  - "zzzz" shows `No settings found for "zzzz"` and an empty sidebar.
  - Esc in the focused box leaves the box and keeps the screen open.
- **AC-UI-08** (REQ-UI-09) **Given** an NPC rule labelled "Trinity", **when** "trini" is searched, **then** its category is listed with a badge of at least 1 and the rule card is shown expanded — [C]
- **AC-UI-09** (REQ-UI-10) **Given** chat is open in a world:
  - `/ksu glow` + Enter opens the screen on the next tick with "glow" in the search box, and it stays open.
  - `/ksu hud` opens the HUD editor, not a search for "hud".
  - The bound "Open settings" key opens the screen.
  - Mod Menu's configure button opens it, and closing returns to the Mod Menu list.

  These are checked by runClient smoke — [B]. A production boot without Mod Menu logs no `NoClassDefFoundError`, and `/ksu` works — [D]
- **AC-UI-10** (REQ-UI-11, REQ-UI-15) **Given** the Mob Highlighter rule list, **when**:
  - a rule is added from the picker and its label, pattern and colour are changed
  - only the colour of a second rule is changed
  - a third rule is deleted
  - the game is restarted

  **then** all four changes are present, and the added entry no longer appears in the picker — [A] + [C]. Closing the picker never shows a "Changes not saved" prompt — [C]
- **AC-UI-11** (REQ-UI-12) **Given** a rule with the REGEX pattern `([`, its card shows an error marker with a message and the rule is reported inactive. Correcting the pattern removes the marker — [A] + [C]
- **AC-UI-12** (REQ-UI-13) **Given** the colour picker — [A] + [C]:
  - `#1A2B3C` round-trips to 0x1A2B3C.
  - With hue 200° and saturation 0.8, moving brightness to 0 and back to 1 restores the hue within ±1° and the saturation within ±0.01.
  - Typing `#GG1234` keeps the last valid colour and marks the field invalid.
  - Pasting `29b6b2` gives `#29B6B2`.
  - Rule colours show no alpha control.
  - Cancel leaves the stored colour unchanged.
- **AC-UI-13** (REQ-UI-14) **Given** the "Open settings" capture widget:
  - **When** it is armed and F7 is pressed, **then** after closing, `options.txt` contains `key_key.k8bas_skyblock_utility.open_settings:key.keyboard.f7` and vanilla Controls shows F7 under the mod's category.
  - Arming it and pressing Esc writes `key.keyboard.unknown`.
  - Right-click restores the default.

  — [C]
- **AC-UI-14** (REQ-UI-15) **Given** the screen is open:
  - Clicking a feature toggle changes that feature's state in the game immediately.
  - Dragging a slider across 50 values writes the config file 0 times during the drag and once on release.
  - Closing the screen with Esc, or having it replaced by a disconnect screen, writes the file exactly once, and each module's rebuild hook runs exactly once.

  Verified with a write counter on config-store's save path — [A], plus runClient smoke — [B]
- **AC-UI-15** (REQ-UI-16) **Given** a sanitised fixture shaped like the user's 1.0.1 config, containing:
  - 3 mob rules with island and sourceId
  - the NPC rules Trinity, Tomioka and Duncan without sourceId
  - a fixed NPC rule with x/y/z
  - `mobScanRangeBlocks` 128
  - both update flags

  **when** the new build loads it and the screen is opened and closed without edits, **then**:
  - Every original key exists in the written file with an equal value. The only exceptions are migrations defined in other modules' specs, and the only other differences are added keys — [A].
  - The screen shows each rule's original label, island, pattern and colour — [C].
  - A hand-edited `mobScanRangeBlocks` of 200 is still 200 after open and close — [A].
  - The three keybind lines in `options.txt` are unchanged — [A].
- **AC-UI-16** (REQ-UI-17) **Given** a fresh config, the accent is `#29B6B2` — [A]. **When** the accent is changed to `#FF5252` in General › Interface, **then** the header line, the selected tab label and focus borders use it immediately and after a restart, while destructive buttons and error text keep their fixed red — [C]
- **AC-UI-17** (REQ-UI-19) **Given** a 6-line tooltip on an option at the right and bottom edges of the panel, at GUI scales 1–4 the tooltip lies entirely inside the window — [C]. Every card for an AMBER feature has a non-empty tooltip — [A]
- **AC-UI-18** (REQ-UI-20) **Given** a 5,000-row list with 18 px rows in a 200 px viewport, at every tested scroll offset the layout pass returns at most 13 rows (ceil(200/18)+1) and maps a click's y position to the correct row index — [A]. The NPC picker scrolls from start to end in a screenshot sequence — [C]
- **AC-UI-19** (REQ-UI-21) **Given** 5 notices posted within one second — [A] + [C]:
  - at most 4 are visible, and the oldest is retired first
  - each disappears after its configured duration ±1 client tick, plus the slide time
  - keyboard and mouse input still reach the game while notices are visible
  - on a fresh config, the notice channel of every warning feature is OFF — [A]
- **AC-UI-20** (REQ-UI-23) `grep -r shedaniel src` is empty, `fabric.mod.json` has no `cloth-config` dependency, and the production boot with the user's mod set minus Cloth Config succeeds — [R] + [D]
- **AC-UI-21** (REQ-UI-24) Review — [R]:
  - no repository file matches a file of the AlpakaAddons tree by hash
  - Phase 2 added no font, sound, shader or texture asset
  - THIRD_PARTY_NOTICES has no AlpakaAddons entry
- **AC-UI-22** (REQ-UI-25) Review: UI and command code contains no call that sends chat, commands or packets — [R]
- **AC-UI-23** (REQ-UI-01, REQ-UI-04, REQ-UI-08, REQ-UI-17) At G2 the user compares the screen with AlpakaAddons in their own instance. They confirm it "feels similar" in layout, structure, categories, search and style, or list the differences to fix — [E]

**Edge cases**
- **EC-UI-01** The window is resized or the GUI scale changes while the screen is open → the layout is redone; scroll positions are clamped; the search query, focused field and expanded cards are kept.
- **EC-UI-02** The screen is replaced while open (disconnect, kick, server transfer, another mod opening a screen) → the changes so far are saved once, the same as a normal close, with no crash.
- **EC-UI-03** `/ksu` is sent from chat → the screen opens on the next tick, and the Enter key does not close it.
- **EC-UI-04** The screen is opened from the title screen through Mod Menu (no world) → every option can be edited; features that need world state show no live status; closing returns to Mod Menu.
- **EC-UI-05** A stored value lies outside a control's range (e.g. `mobScanRangeBlocks` 200) → the control shows its maximum, and the stored value stays 200 unless the user moves the control.
- **EC-UI-06** The config contains unknown keys or sections (from a newer version or a removed feature) → they are preserved unchanged on save (config-store).
- **EC-UI-07** A rule label is very long (100 characters) or empty → it is shortened with an ellipsis, or shown as "(unnamed)", and never overlaps the card's controls.
- **EC-UI-08** The picker opens while the NPC or mob table failed to load (data-registry disabled it) → the picker shows "database unavailable" and Add is disabled; existing rules stay editable.
- **EC-UI-09** Two rules have the same label → both are shown, and search finds both.
- **EC-UI-10** A query has upper-case letters or surrounding spaces → it is trimmed and matched case-insensitively. Input past 35 characters is ignored.
- **EC-UI-11** A captured key is already used by another mapping → it is accepted, as vanilla does, and the widget marks the binding in yellow, as vanilla 26.2 Controls does [decided R26]. The error red stays for real errors.
- **EC-UI-12** The scroll wheel is used over the sidebar versus over the content → only the region under the cursor scrolls.
- **EC-UI-13** Another GUI mod in the user's instance (Alpaka, ImmediatelyFast, Skyblocker) transforms or batches screen rendering → the backdrop still covers the screen and clicks still hit the drawn controls (checked in the tier D boot).
- **EC-UI-14** A colour-only change is followed by Esc → the change is saved, unlike 1.0.1.
- **EC-UI-15** The accent is set to the error red → error messages and destructive buttons stay recognisable by their text and style, not by colour alone.
- **EC-UI-16** The config failed to parse, so config-store backed it up and loaded defaults → the screen opens with defaults and shows a one-time notice naming the backup file.
- **EC-UI-17** A rule list has 300 rules → scrolling stays responsive, because only visible rows are drawn (REQ-UI-20).

**Open questions**
- **Q-UI-01** (see D-8) Should the core UI decisions be accepted as recommended? They are: no UI library, vanilla font, rounded corners from fills, live-apply with save on close, and Cloth Config removed at the end of Phase 2. → decided D-8 (accept all)
- **Q-UI-02** (see D-8) Which accent colour should be the default: teal `#29B6B2`, your Alpaka red `#FF5252`, or a K8bas colour? → decided D-8 (teal `#29B6B2`, configurable)
- **Q-UI-03** (see D-8) Where should notices (toasts) be used? → decided R12 (D-8 decided that toasts exist; R12 recommends: update prompts, plus an optional notice channel on warnings, default OFF)
- **Q-UI-04** (see D-8) Which T2.9 stretch items, if any, belong in 2.0.0? → decided D-8 (all T2.9 items stay optional stretch goals; none is required for 2.0.0; the multi-select dropdown is built with the optimizer table, T6.7)

---

### hud — HUD framework and HUD editor
**Origin:** Brief Phase 2 ("HUD editor", "HUD editing"); Brief Phase 5 ("movable, scalable HUD element (integrated into the new HUD editor)"); Addendum UI ("Optional HUD element (integrated into the HUD editor)") | **Depends on:** ui-config (and through it port-26-2, config-store) | **Plan tasks:** T2.7, T2.7b, T2.8, T2.8b, T2.9c, T5.3, T6.9, T7.4 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** One framework for all of the mod's on-screen HUD elements, and one HUD editor modelled on AlpakaAddons' editor, where the user moves, scales and resets every element. Later HUDs (the rare-drop odds panels, bestiary tracker, SkyBlock XP "next best task", and a drill fuel HUD if one is ever added) plug into it without their own position or scale code.

**Functional requirements**
- **REQ-HUD-01** Element contract.
  - Every HUD element has a stable id, a display name, the feature toggle that enables it, its live content, its preview content, and a default anchor, offset and scale.
  - The framework draws an element at its stored position and scale only when its feature is enabled and it has content.
  - An element with no content draws nothing; there are no empty boxes.

  *(Brief: Phase 5, Addendum UI; Derived: PLAN AD-5)*
- **REQ-HUD-02** Visibility [decided R12].
  - Elements are hidden while the vanilla HUD is hidden (F1).
  - Elements are hidden while the HUD editor is open; the editor draws each of them itself, exactly once.
  - Elements are hidden while any screen other than chat is open. There is no menu-context exception in v1: odds shown inside a container screen (Croesus) are tooltip lines, not a HUD element (rare-drop-odds REQ-ODDS-05).

  *(Derived: research-alpaka-ui §9 (Alpaka hides HUDs under screens); rare-drop-odds REQ-ODDS-05; R12)*
- **REQ-HUD-03** Position model.
  - A position is stored per element id in the config file as {anchor, offset x/y in GUI px, scale}. The anchor is one of 9 screen points: the 4 corners, the 4 edge midpoints, or the centre.
  - When a drag ends, the anchor becomes the point of the screen third (in each axis) that contains the element's centre. Re-anchoring must not move the element on screen.
  - An element grows away from its anchor; for example, a right-anchored element stays flush right when it gets wider.
  - Positions survive a restart, a GUI-scale change and a window resize.

  *(Brief: Phase 5 "movable"; Derived: PLAN AD-5, research-alpaka-ui §9 (absolute pixels break on GUI-scale change))*
- **REQ-HUD-04** Draw-time clamp.
  - If the stored position would put any part of an element outside the window, the element is drawn shifted fully inside it. If it is larger than the window, it is drawn from the window's top-left.
  - The stored position is not rewritten by this, so returning to the earlier window size or GUI scale restores the original placement.

  *(Derived: research-alpaka-ui §9)*
- **REQ-HUD-05** Scale. The range is 0.5–3.0, changed in steps of 0.1 and stored rounded to 0.01. *(Brief: Phase 5 "scalable"; PLAN T2.8)*
- **REQ-HUD-06** Editor entry points:
  - `/ksu hud`
  - an "Edit HUD layout" button in General › HUD
  - an "Edit position" button on every feature card that owns a HUD element, which opens the editor with that element selected

  Closing the editor returns to the screen it was opened from. *(Brief: Phase 2 "HUD editor"; PLAN T2.8; research-alpaka-ui §9, §15)*
- **REQ-HUD-07** Editor display.
  - It has a dimmed full-screen backdrop, a line of instructions, and a status line with the selected element's name, X, Y and scale (or "Click a HUD to select it").
  - Every registered element is drawn. Preview content is used when there is no live content.
  - Each element has a 1 px outline with distinct colours for idle, hovered, selected and disabled, and its name as a label above the box. The label goes below the box when the box is within the top 48 px.
  - Elements whose feature is off are drawn greyed with "(disabled)", so they can be placed before they are turned on.

  *(Brief: Phase 2 "HUD editor"; behaviour Derived: research-alpaka-ui §9)*
- **REQ-HUD-08** Editor interaction [Esc decided R12].
  - Pointer:
    - Left-press on the topmost element under the cursor selects it and starts a drag without the element jumping.
    - Clicking empty space deselects.
    - The scroll wheel scales the hovered element, or the selected one when the cursor is over empty space.
  - Keys: arrow keys nudge the selection by 1 GUI px, and Shift+arrow by 10 px.
  - Drags and nudges keep the element inside the window.
  - Buttons:
    - Reset Selected (disabled with no selection) and Reset All both restore defaults.
    - Cancel restores every position and scale to what it was when the editor opened, then closes.
    - Save writes the changes and closes.
  - Esc acts as Save: it writes the changes and closes. Only the Cancel button reverts.
  - An external close (disconnect, server transfer, another screen replacing the editor) is handled like Esc.
  - The config file is written only on Save, on a close or on a reset, never per drag event.

  *(Brief: Phase 2, Phase 5; PLAN T2.8)*
- **REQ-HUD-09** The editor's buttons and texts should use the Phase 2 visual style (REQ-UI-17), not vanilla buttons. *(Derived: Brief Phase 2 "feel similar"; research-alpaka-ui §9)*
- **REQ-HUD-10** In production builds, the editor and the in-game HUD contain only elements of shipped features. Development or test elements must not appear. *(Derived: the PLAN T2.7 sample element is test scaffolding)*
- **REQ-HUD-11** Drawing an element must only read state computed elsewhere. File, network or other blocking I/O on the HUD draw path is not allowed. *(Derived: frame stability; research-alpaka-ui Risks "saving on every drag writes from the render thread")*
- **REQ-HUD-12** The framework must support multi-line text elements:
  - The feature supplies the list of lines, and its own options decide which lines show.
  - The element's bounds follow the drawn text plus a 2 px margin, and update when the content changes.

  *(Brief: Phase 5 "Make the displayed lines configurable"; Addendum "next best task with XP/h")*
- **REQ-HUD-13** The default positions of shipped elements should not overlap one another, measured with preview content at 1920×1080 and GUI scale 2. *(Derived: up to 5 elements (Scatha, corpse and slayer odds panels, bestiary, next best task) must be usable on a fresh config; no drill fuel HUD is built [decided D-4]; rare-drop-odds REQ-ODDS-05)*
- **REQ-HUD-14** The editor may snap edges within 4 px to the screen edges, the centre lines and other elements, showing 1 px guide lines while snapped. Holding Alt disables snapping. [optional stretch, decided D-8; not required for 2.0.0] *(Derived: research-alpaka-ui §9 "NEW" proposal; PLAN T2.9c)*

**Out of scope**
- Resize handles, a grid, selecting several elements at once, a right-click context menu, and per-element toggles inside the editor. Toggles live on the feature cards.
- Snapping and guide lines, except as the optional T2.9c stretch item (REQ-HUD-14).
- Drawing HUD elements above a container or menu screen (no menu-context exception in v1, REQ-HUD-02).
- The content, data and rule verdicts of individual HUDs (odds, bestiary, next best task, drill fuel). These are specified in their own modules.
- Moving or changing vanilla HUD elements (hotbar, scoreboard, chat).
- Importing HUD positions from AlpakaAddons or other mods.

**Acceptance criteria**
- **AC-HUD-01** (REQ-HUD-01, REQ-HUD-02) **Given** an enabled test element with content in a singleplayer world, **then** — [C]:
  - it is drawn at its stored position
  - F1 hides it
  - switching its feature off hides it
  - with empty content nothing is drawn
  - opening the inventory or a chest screen hides it, and closing the screen shows it again
  - with chat open it stays visible
- **AC-HUD-02** (REQ-HUD-03) Unit tests over all 9 anchors with a W×H window and a stored offset — [A]:
  - dropping an element whose centre is in screen third (col, row) gives the matching anchor
  - the element's screen rectangle is the same (±0.5 px) before and after re-anchoring
  - a right-anchored element whose width grows from 50 to 80 px keeps its right edge
- **AC-HUD-03** (REQ-HUD-03, REQ-HUD-04) **Given** an element placed at the bottom right at GUI scale 2 on 1920×1080, **when** the GUI scale changes to 3 and 4 and the window to 854×480, **then** it stays fully visible at the bottom right — [C]. After returning to scale 2 and 1920×1080, its stored config entry is identical to before — [A]
- **AC-HUD-04** (REQ-HUD-05, REQ-HUD-08) Editor gametest — [C] + [A]:
  - dragging the selected element by (+40, +25) GUI px moves its on-screen rectangle by the same amount
  - 3 scroll notches up from 1.00 give 1.30; scrolling up at 3.00 stays 3.00, and scrolling down at 0.50 stays 0.50
  - an arrow key moves it 1 px, and Shift+arrow 10 px
  - Cancel after several changes restores every element to its state when the editor opened
  - Reset Selected resets only that element, and Reset All resets every element to its default
- **AC-HUD-05** (REQ-HUD-08) **Given** a drag of 100 mouse-move events, the config file is written 0 times before Save and exactly once on Save or Esc — [A]
- **AC-HUD-06** (REQ-HUD-06) The editor opens from each entry point — [C]:
  - `/ksu hud` opens it
  - "Edit HUD layout" opens it from General
  - "Edit position" on a HUD feature card opens it with that element selected, and the status line shows its name
  - closing returns to the settings screen
- **AC-HUD-07** (REQ-HUD-07) A screenshot of the editor in singleplayer (no SkyBlock data) shows — [C]:
  - every registered element with its preview content and outline
  - a disabled element greyed with "(disabled)"
  - the label below the box for a box within the top 48 px
- **AC-HUD-08** (REQ-HUD-02) While the editor is open, each element is drawn exactly once: no duplicate from the in-game HUD (draw counter) — [A] + [C]
- **AC-HUD-09** (REQ-HUD-10) In the production jar, the editor lists only the shipped HUD features and no test element — [D] + [R]
- **AC-HUD-10** (REQ-HUD-11) Review: no file, network or blocking call on any HUD draw path — [R]
- **AC-HUD-11** (REQ-HUD-12) **Given** a 9-line element with lines 2 and 5 switched off, it draws 7 lines, and its outline equals the text bounds plus 2 px — [A] + [C]
- **AC-HUD-12** (REQ-HUD-13) **Given** all shipped elements with preview content at 1920×1080 and GUI scale 2, no two default rectangles intersect — [A]
- **AC-HUD-13** (REQ-HUD-09) An editor screenshot shows buttons in the Phase 2 style (accent, rounded corners) — [C]
- **AC-HUD-14** (REQ-HUD-14, only if built) Dragging an element to within 4 px of the vertical centre line snaps it there and shows a guide line; with Alt held, it does not snap — [C]

**Edge cases**
- **EC-HUD-01** The GUI scale or window size changes while the editor is open → elements are laid out again from their anchors; the selection and the unsaved changes are kept; the status line updates.
- **EC-HUD-02** At scale 3.0 an element is larger than a 320×240 GUI area → it is drawn from the top-left of the window, can still be selected, and can be scaled down with the scroll wheel.
- **EC-HUD-03** Two elements overlap → the one drawn on top (registered later) receives the click; the other can be reached after moving the first.
- **EC-HUD-04** The config holds a position for an unknown element id (a removed feature or a newer version) → the entry is kept unchanged and ignored.
- **EC-HUD-05** A position entry is malformed (unknown anchor, scale 0, NaN or 10, missing fields) → that element uses its default anchor and offset, and a scale clamped to 0.5–3.0. Other elements are unaffected. It is logged once, and the entry is rewritten only when the user saves.
- **EC-HUD-06** An element's live content changes size during a drag → the grab point stays under the cursor; there is no jump.
- **EC-HUD-07** The editor is closed by an external event (disconnect, server transfer, another mod opening a screen) → this is handled exactly like Esc (save and close, REQ-HUD-08) [decided R12], and the config file is never left half-written.
- **EC-HUD-08** The editor is opened from the title screen (Mod Menu → settings → Edit HUD layout) → it works with preview content over the backdrop and needs no world.
- **EC-HUD-09** A feature is on but the player is not on SkyBlock or has no data → nothing is drawn in game; the editor shows the preview.
- **EC-HUD-10** A fresh config gets a new HUD consumer in a later phase → its default rectangle does not overlap the existing defaults (REQ-HUD-13), checked when that element is added.

**Open questions**
- **Q-HUD-01** What should Esc, or an external close such as a disconnect, do in the HUD editor? → decided R12 (recommended: save and close; the Cancel button reverts)
- **Q-HUD-02** Should HUD elements stay visible while an inventory or menu is open? → decided R12 (recommended: hidden while any screen other than chat is open; no menu-context exception in v1)

---

### data-registry — Internal bundled database

**Origin:** Addendum (intro line "reuse the internal database from Phase 3.9"; section Data); Brief Phase 3 item 9; Derived (ground rule 4 licensing, PLAN AD-7, §2) | **Depends on:** port-26-2 | **Plan tasks:** T3.0c, T3.0i, T3.0j, T3.0k, T3.8, T6.0, T6.1, T7.1b, T7.2b (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** One internal, versioned database of bundled JSON tables. Every data-driven feature loads through it: NPC and mob lists, drop tables, bestiary, and the SkyBlock XP tasks, rates, reference tables and prices. As a result, every table carries its provenance and licence, is validated at build time and fails safe at runtime.

**Functional requirements**
- **REQ-DATA-01** Every table a feature needs must ship inside the mod jar and load with no network access. Table content changes only with a mod release. The only runtime network data allowed is an opt-in overlay that the consuming feature declares (today only the live Bazaar feed, see sbxp-optimizer). Such an overlay may sit on top of a bundled table but must never replace or delete it. *(Brief: Phase 3 item 9; Addendum: Data "internal database"; Derived: AD-7, D-22)* [decided D-12]
- **REQ-DATA-02** Every table file must carry exactly this envelope, the one pinned envelope for all tables (it replaces the "map or list" wording of PLAN AD-7):
  - `table`: a unique id
  - `schemaVersion`: an integer that changes only when the shape changes
  - `dataVersion`: an integer that increases by exactly 1 on every content change
  - `gameVersion`: the SkyBlock version the content was last checked against, e.g. `0.27.1`
  - `generatedAt`: an ISO-8601 UTC timestamp
  - `license`: the table's licence, one of `CC0-1.0`, `MIT` or `Apache-2.0`. A table of official-API facts is `CC0-1.0`; the licence or "facts" status of each upstream is recorded on its source record.
  - `sources`: an object map from a source id to a source record. Each record names the upstream and its pinned revision (commit SHA, API build/lastUpdated, or capture date). Rows cite sources by these ids.

  Tables licensed `MIT` or `Apache-2.0` must live under `data/thirdparty/<upstream>/` (REQ-DATA-05). A `sources` list, a non-integer `dataVersion` or any other licence value fails validation. *(Addendum: Data "versioned JSON file … with a JSON schema and a dataVersion + gameVersion field"; Derived: ground rule 4 provenance, AD-7, REQ-XC-LICENSE-03)*
- **REQ-DATA-03** Every table must have a JSON Schema (draft 2020-12) in the repository. Schemas do not need to ship in the jar. *(Addendum: Data "with a JSON schema")*
- **REQ-DATA-04** `./gradlew check`, and so `build`, must fail when any bundled table:
  - violates its schema
  - has duplicate primary keys
  - has a cross-table reference (`table:id`) that does not resolve
  - differs from its index entry in sha256, row count or schemaVersion
  - is in the jar without an index entry, or is listed in the index without a file

  The same validation must also be runnable on its own, for the update skill. *(Addendum: Data; Claude Code skill step 4 "validate it against the schema"; Derived: AD-7)*
- **REQ-DATA-05** Licence enforcement. The build must fail when:
  - a table's licence is not on the allowlist: `CC0-1.0`, or `MIT` / `Apache-2.0` from an upstream approved in PLAN §2
  - a table whose licence is not `CC0-1.0` lies outside `data/thirdparty/<upstream>/`
  - a third-party upstream has no entry in `THIRD_PARTY_NOTICES.md`, or its licence text is missing from the jar under `META-INF/licenses/`
  - a table comes from a denylisted source: CC BY-NC-SA material (including NEU `george.json`), or any scripted wiki, Fandom or hypixel.net extraction

  MIT data is bundled with notices from the pinned upstream commit and ships with credit. *(Brief: Ground rule 4; Derived: PLAN §2, D-5, D-16, D-25)* [decided D-5]
- **REQ-DATA-06** Prose guard. Bundled tables must contain no Minecraft formatting codes (`§`). They must also contain no free-text string longer than 80 characters outside fields the schema explicitly allowlists (names, `assumes` notes). *(Derived: ground rule 4; this stops wiki or NEU prose from being copied; PLAN §10 "Text and licensing")*
- **REQ-DATA-07** Fail-safe runtime loading:
  - A malformed row is skipped and logged once, with its table and row id.
  - A table that is missing, unreadable, fails its checksum or has an unknown schemaVersion disables only the features that depend on it. Those features show a visible "data unavailable" state.
  - Bundled data must never crash the game.

  *(Brief: Phase 3 item 9 "reliability"; Derived: the current NPE on a null island, research read-npc bug 6)*
- **REQ-DATA-08** The loader must accept UTF-8 with or without a BOM, and LF or CRLF line endings. *(Derived: the current DB files are UTF-8 with BOM and CRLF)*
- **REQ-DATA-09** Startup:
  - Loading does no network I/O.
  - Loading blocks the client thread for at most 200 ms in total.
  - A consumer that asks before loading has finished gets a "loading" state and then the data, without reopening anything. It must never get a permanently empty table, which is the current "close and reopen" bug.

  *(Derived: Brief Phase 3 item 9 "reliability"; research read-mob)*
- **REQ-DATA-10** The registry should offer layered values: bundled < live cache (only for features with an opt-in feed) < user override. Each value reports which layer won and that layer's timestamp, and a failed live layer falls back silently to bundled. *(Addendum: Prices "fall back to bundled values silently"; UI "per-task rate and time overrides"; Derived: GAP-1 §5)*
- **REQ-DATA-11** Each loaded table's id, dataVersion and gameVersion should be logged once at startup and be viewable in-game (a debug command or an About card). *(Derived: needed to diagnose reports and to flag stale SBXP overrides, PLAN T6.8)*
- **REQ-DATA-12** Bundled data should add at most 1 MB to the compressed release jar, and the validator reports each table's size. *(Brief: Phase 3 item 9 "size"; Derived: GAP-1 measured ≈0.15 MB)*
- **REQ-DATA-13** Every bundled table must load through this registry, and no feature may ship its own resource loader. That covers the NPC DB, mob DB, odds tables, bestiary, and the SBXP tasks, rates, reference tables and prices. (The corpse-spot table was on this list until R22 dropped it [dropped R22].) *(Addendum: intro "reuse the internal database"; Derived: D-26)*

**Out of scope**
- Downloading or refreshing bundled tables at runtime from a gist, GitHub raw or third-party repos (meowdding-repo, SkyHanni-REPO, NEU) [decided D-12].
- User-supplied replacement table files; embedded database engines (SQLite/H2).
- Any table built from scripted wiki, Fandom or hypixel.net extraction (rules and licensing: D-16, D-25).
- A JSON Schema library in the runtime jar (it is used in tests only).
- The SBXP data-build tool that generates reference tables and prices (sbxp-optimizer, T6.2).

**Acceptance criteria**
- **AC-DATA-01** (REQ-DATA-01, REQ-DATA-13)
  - Given a fresh install with the network unreachable, when the client starts and the Mob Highlighter and NPC Search pickers are opened, then every indexed table reports "loaded", the pickers list entries, and the log has no HTTP request from data loading — [B].
  - Review: no resource-JSON loading exists outside the registry — [R].
- **AC-DATA-02** (REQ-DATA-02, REQ-DATA-03, REQ-DATA-04) Given fixtures {valid table; schema violation; duplicate id; dangling `table:id`; sha256 mismatch; file missing from index; index entry without file; missing `dataVersion`; `dataVersion` as a date string; `sources` as a list; licence `facts`}, when the validator runs, then only the valid one passes, and each failure names the table and the offending key — [A].
- **AC-DATA-03** (REQ-DATA-05)
  - Given fixtures {MIT table outside the third-party folder; Apache-2.0 table outside the third-party folder; MIT table with no notice entry; upstream licence text missing from the jar; licence `CC-BY-NC-SA-3.0`; a file named `george.json`}, when `./gradlew check` runs, then each one fails — [A].
  - When the release jar is built, `META-INF/licenses/` holds one licence file per upstream listed in `THIRD_PARTY_NOTICES.md` — [R].
- **AC-DATA-04** (REQ-DATA-06) Given a table with `§a`, or an 81-character string in a field not on the allowlist, when validated, then the build fails — [A].
- **AC-DATA-05** (REQ-DATA-07)
  - Given a table where one row lacks a required field, when loaded at runtime, then every other row loads, exactly one WARN names the table and row, and no exception escapes — [A].
  - Given one table removed from the runtime classpath, then only its dependent feature shows "data unavailable" and the others work — [B].
- **AC-DATA-06** (REQ-DATA-08) Given the same table as LF without a BOM and as CRLF with a BOM, when parsed, then the results are equal — [A].
- **AC-DATA-07** (REQ-DATA-09)
  - Given `runClient`, when the title screen is reached, then the log shows the registry's client-thread blocking time at or below 200 ms — [B].
  - Given a consumer query issued before loading completes, then it returns "loading" and later the full table — [A].
- **AC-DATA-08** (REQ-DATA-10) Given a value with bundled = 10, live = 12 and override = 15, then 15 is returned with source "override". After the override is removed, 12 is returned. When the live layer fails, 10 is returned with no user-visible error — [A].
- **AC-DATA-09** (REQ-DATA-11) Given `runClient`, when the version view is opened (command or About card), then every indexed table is listed with the dataVersion and gameVersion from the index — [B].
- **AC-DATA-10** (REQ-DATA-12) The validator report prints bytes per table, and the release jar's compressed data folder is at most 1 MB — [A]/[R].

**Edge cases**
- **EC-DATA-01** Table A references table B, and B is disabled at runtime → A's referencing rows count as unavailable and are logged once; nothing crashes.
- **EC-DATA-02** A resource pack ships files under the mod's asset namespace → bundled tables are still read from the mod's own jar, so a resource pack cannot replace them.
- **EC-DATA-03** The live SkyBlock version is newer than a table's `gameVersion` → the table is still used and its version stays visible (REQ-DATA-11). Nothing is invalidated automatically.
- **EC-DATA-04** Two files declare the same `table` id → the build fails.
- **EC-DATA-05** The render thread and a worker ask for the same table at once on first access → it is loaded once, and both get the same immutable instance.
- **EC-DATA-06** In the dev run (`runClient`), tables are read from the build output folder rather than a jar → the behaviour is identical.
- **EC-DATA-07** Disk full or an unwritable config folder → there is no effect, because the registry is read-only and writes nothing to disk.

**Open questions**
- **Q-DATA-01** Third-party data licensing: MIT with notices, or CC0 only? → decided D-5 (MIT with notices, pinned commit, ship now with credit)
- **Q-DATA-02** Confirm that the addendum's "internal database from Phase 3.9" means brief Phase 3 item 9. → decided D-26 (the shared data registry, T3.0c)

---

### game-state — Read-only game-state readers

**Origin:** Derived. Needed by: brief Phase 3 item 2 (corpse list and keys), item 7 (item 3 was dropped by R22), Phase 5 (bestiary), and the addendum's Progress detection. Basis: PLAN AD-6, §3, and research detection §4–7 | **Depends on:** port-26-2, config-store, location | **Plan tasks:** T0.4, T0.4c, T0.4b, T0.4d, T3.0a, T3.0g, T3.0h, T3.0m, T3.0d, T3.0e, T5.3 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Shared, strictly passive readers for what the server already shows the player: tab-list widgets, the sidebar, chat and action bar, menus the player opens, the player's own inventory, profile identity and the mineshaft state. The mineshaft alert, odds, bestiary and SBXP features all build on one tested source, and the readers never interact with the server.

**Functional requirements**
- **REQ-GS-01** Read-only. The readers must never:
  - send packets, commands or chat
  - click, move or take items
  - open, close, page or refresh a menu
  - simulate hovering
  - cancel, delay or change any packet or message

  Prompts to the player (e.g. "enable the widget") are local text, optionally with a click-to-fill chat suggestion that the player sends themselves. *(Brief: Ground rule 5; Addendum: Progress detection "read-only, display-only, no automated clicking"; Derived: P5)*
- **REQ-GS-02** Tab widgets.
  - The reader takes every listed tab entry's display text, strips formatting, and exposes widgets by header line. That covers at least `Profile:`, the `SB Level` line, `Frozen Corpses:`, `Bestiary:`, `Stats:` and `Pity:`.
  - Lines come back in order and do not depend on tab sort order.
  - It distinguishes "widget absent", "widget present but empty" and "line not recognised".
  - Consumers see a change within 500 ms, and parsing never runs more than once per client tick.

  *(Derived: Brief Phase 3 item 2 via the Frozen Corpses widget, research G6; Phase 5 Bestiary widget; Addendum: profile and level anchor)*
- **REQ-GS-03** Sidebar. The reader exposes the sidebar lines top to bottom as displayed (objective title plus each line composed from its team prefix and suffix), with formatting and Hypixel's invisible filler characters removed, and notifies on change. *(Derived: the shaft code for Brief Phase 3 item 2 (and item 3 until R22 dropped it), research G6 §5)*
- **REQ-GS-04** Chat and action bar. Every game (system) message is delivered to subscribers:
  - exactly once
  - with its overlay flag (chat or action bar)
  - in the text the server sent
  - including messages another mod cancels or rewrites through Fabric's message events

  *(Derived: PLAN §3 row "Action-bar / chat XP lines"; Addendum: Progress detection; research detection §4 and §6 — Skyblocker rules hide profile lines and rewrite the action bar)*
- **REQ-GS-05** No parser for server events may match a line a player typed in public, party, guild or private chat, such as a pasted copy of a level-up or action-bar message. *(Derived: research detection §4–5, observed player-chat false matches)*
- **REQ-GS-06** Opened menus. When the player opens a container menu whose title matches a pattern registered by an enabled consumer, the reader exposes:
  - the title
  - each top-container slot: index, item name, the lore lines from the server-sent item data, and the SkyBlock id when present

  Further rules:
  - Contents count as stable after the content arrives and no slot changes for 2 consecutive client ticks.
  - Each page or content change is a new snapshot, and closing the menu is signalled.
  - Player-inventory slots are excluded.
  - Lore is read without hovering and is not affected by other mods rewriting tooltips.

  *(Addendum: Progress detection "parsing the SkyBlock Leveling menu (/sblevels) item tooltips when I open it"; Brief: Phase 5 menu snapshot; Derived: D-27)*
- **REQ-GS-07** Inventory counts.
  - The reader counts items by SkyBlock id across the player's own inventory: the 36 main and hotbar slots plus the offhand.
  - Counts update within 2 client ticks of an inventory change.
  - No other container is read for counting. Sacks, ender chest, backpacks and storage are excluded.

  *(Brief: Phase 3 item 2 "how many of the matching keys the player owns")* [decided D-7]
- **REQ-GS-08** Profile identity.
  - The reader finds the current SkyBlock profile from the tab `Profile:` widget and from profile chat lines, including chat lines that other mods hide.
  - Key = Minecraft account UUID + normalised profile name (case-folded and trimmed, with the type glyph removed).
  - The profile id and type (normal, ironman, stranded, bingo) are attached when seen.
  - The profile is "unknown" until identified.
  - A change event fires on a profile or account switch.

  *(Addendum: Progress detection "Cache results per profile", "Handle multiple SkyBlock profiles separately"; Derived: research detection §6)*
- **REQ-GS-09** Per-profile store.
  - Consumer state for a profile is kept in its own file per (account, profile).
  - Files are written atomically: a process killed mid-save leaves the previous file intact.
  - Nothing is written while the profile is unknown.
  - If a known profile name shows up with a different profile id, the old state is archived, never merged.

  *(Addendum: Progress detection "Cache results per profile"; Derived: config-store atomic save; a deleted and recreated profile can reuse its name)*
- **REQ-GS-10** An unrecognised line inside a known widget, or a known message type with an unexpected shape, is logged once per distinct shape per session. The consumer then shows "unknown" or "no data" and never a wrong number. *(Derived: PLAN §8 "Hypixel formats change")*
- **REQ-GS-11** Game objects are read only on the client thread. Other threads get only immutable snapshots. *(Derived: research G4(c), the 2026-08-21 off-thread crash)*
- **REQ-GS-12** Raw chat, tab, sidebar and menu contents must not be written to disk. Two exceptions: parsed per-profile results that features need, and a capture the player starts explicitly, which disarms itself after at most 60 min. *(Derived: privacy; PLAN §5 fixture sanitisation)*
- **REQ-GS-13** Format capture. The player must be able to write raw tab, sidebar and opened-menu contents to the log, tagged for fixture creation:
  - tab and sidebar with a command
  - menus with an "armed" mode, because no command can be typed while a menu is open

  The names of real players, yours included, are replaced in the written lines by placeholders (Self, Player1, Player2, … stable for the session); NPC names and Hypixel's tab-widget entries stay [decided R19].

  The capture is read-only and covers only allowlisted menu titles. The capture commands (`/ksu debug dump tab|sidebar`, `/ksu debug dump containers on|off`) must ship in production builds, because the G1 captures are made on Hypixel in the user's Prism copy. Only the simulators `/ksu debug island` and `/ksu debug shaft` are dev-only (REQ-LOC-08). *(Derived: PLAN §5 "formats UNVERIFIED until captured", T0.4 "available in production", D-17)*
- **REQ-GS-14** Every parser has unit tests on sanitised fixtures. A fixture not yet confirmed by an in-game capture is marked UNVERIFIED, and the phase report lists it until G1 captures replace it. *(Derived: PLAN §5; REQ-XC-VERIFY)*
- **REQ-GS-15** On disconnect, server switch or world change, the readers clear their cached widget, sidebar and menu state, so nothing from the previous server is reported (e.g. the last shaft's corpses). *(Derived: research G6 timing; location dependency)*
- **REQ-GS-16** A reader works only while at least one enabled feature subscribes to it. With every consuming feature disabled, it parses and stores nothing. *(Brief: Ground rule 6 — a disabled feature must have no effect)*
  - [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* This requirement also said: a `/ksu corpses` request counts as a subscriber to the mineshaft state (REQ-GS-17) for the current shaft, reads the current sidebar and tab state at once and keeps the mineshaft state running until the player leaves that shaft, so the command works with the mineshaft alert and the corpse markers OFF *(was derived from REQ-CORPSE-06)*.
- **REQ-GS-17** Mineshaft state. Game-state owns the shaft identification and the corpse list, and exposes both to mineshaft-alert and rare-drop-odds (corpse-waypoints was the third consumer until R22 dropped it):
  - **Shaft code:** the code `<TYPE>_<VARIANT>` (pattern `[A-Z]{4}_[12CL]`), taken from the sidebar scoreboard data the server sends (REQ-GS-03), not from the rendered sidebar. The search starts on entry into a Glacite Mineshaft (location) and runs for up to 15 s.
  - **Display names:** a table of the 34 current codes maps each code to a display name, e.g. `OPAL_1` → "Opal 1", `RUBY_C` → "Ruby Crystal", `FAIR_1` → "Vanguard", `LITT_L` → "Littlefoot's Den". A code that matches the pattern but is not in the table is exposed raw. No code within 15 s gives "Unknown shaft".
  - **Frozen Corpses list:** the corpse types and per-type state from the server's "Frozen Corpses:" tab widget (REQ-GS-02). Line format `<Type>: NOT LOOTED|LOOTED`; `UNLOOTED` also counts as not looted. The list is ordered as listed and does not depend on tab order.
  - The state never comes from entities: no armor stand or other entity is scanned, classified or counted.
  - The state resets on leaving the shaft, server switch and disconnect (REQ-GS-15).

  *(Brief: Phase 3 items 2 and 7, and item 3 until R22 dropped it; Derived: one owner for state that several modules need, research domain-mining-rules B2, G6 §1 and §5)*
- **REQ-GS-18** Widget hint. One shared hint string must name the command and menu path for enabling a Hypixel tab widget. The exact wording is verified in-game before release (Q-MSA-04). At G1 the maintainer reported believing that the Frozen Corpses widget is on by default, but gave no command or menu path, so the wording stays UNVERIFIED and is checked at G3 [decided R24]. The hint is the failsafe that tells the player a widget is off. Every consumer that asks the player to enable a widget (mineshaft-alert, bestiary-hud, sbxp-optimizer) uses this string, and no consumer hard-codes its own widget command. *(Derived: three modules named three different commands; research G6, domain-mining)*

**Out of scope**
- Any active acquisition: sending `/profileid`, `/tablist` or `/widget`, opening, paging or hovering menus (other mods send `/profileid`; excluded by P5).
- Calls to the Hypixel API with player data.
- Caches of sacks, ender chest, backpacks, storage or museum contents [decided D-7].
- Messages that another mod hides below Fabric's message events (e.g. packet-level mixins). This is a documented limitation.
- Location mode, map and server detection (owned by location).
- Reading prices from Auction House screens (D-20: later option).

**Acceptance criteria**
- **AC-GS-01** (REQ-GS-01) An architecture test fails if reader packages reference slot-click, container-close, command, chat-send or packet-send APIs — [A]. Review confirms there is no cancellation path — [R].
- **AC-GS-02** (REQ-GS-02)
  - Given sanitised tab fixtures in shuffled order, with and without `Frozen Corpses:`, with a `Bestiary:` widget and one unknown line, when parsed, then widgets group correctly, absent and empty are reported differently, and the unknown line is flagged — [A].
  - In a Glacite Mineshaft, the parsed widget equals the raw capture — [E].
- **AC-GS-03** (REQ-GS-03)
  - Given a sidebar fixture with lines split across team prefix and suffix and filler characters, then the first line reads exactly `09/26/26 m000XX RUBY_2` (the server id is replaced by the fixture placeholder) — [A].
  - Confirmed in a mineshaft — [E].
- **AC-GS-04** (REQ-GS-04)
  - Given a gametest with a test listener that cancels a profile chat line and another that rewrites an action-bar line, when both messages arrive, then our subscribers get each original text once, with the right overlay flag — [C].
  - Given the user's mod set (Skyblocker chat rules hiding profile lines), the profile is identified and action-bar SkyBlock XP lines are seen — [D]/[E].
- **AC-GS-05** (REQ-GS-05) Given the fixtures `[326] ☀ [MVP+] X: SKYBLOCK LEVEL UP Level 325 ➡ [326]` and a guild-chat paste of an action-bar XP echo, then no server-event parser matches — [A].
- **AC-GS-06** (REQ-GS-06)
  - Given recorded sequences (open → content → slot updates → page change → close), then exactly one snapshot is emitted per stable state.
  - The lore equals the server lore even when a test tooltip modifier is active.
  - Player-inventory slots are absent.
  - A menu with a non-matching title gives no snapshot.

  Verified by [A]. The armed capture of `/sblevels` matches the G1 capture — [E].
- **AC-GS-07** (REQ-GS-07)
  - Given one `TUNGSTEN_KEY` in the hotbar, one in the main inventory and one `UMBER_KEY` in the ender chest, then the counts are Tungsten 2 and Umber 0.
  - After one key is dropped, the count is 1 within 2 ticks.

  Verified by [A], then in a mineshaft — [E].
- **AC-GS-08** (REQ-GS-08)
  - Given the tab line `Profile: Strawberry ♲`, then the key is `strawberry` and the type is ironman.
  - Given no widget and the chat line `You are playing on profile: Raspberry (Co-op)` (cancelled by a test listener), then the profile is identified.
  - Given a different account UUID, then the key is separate.

  Verified by [A].
- **AC-GS-09** (REQ-GS-09)
  - Given a simulated kill between writing the temp file and moving it, then the previous file is intact.
  - Given an unknown profile, then no file is written.
  - Given a switch A → B → A, then A's state is restored.
  - Given name `strawberry` with a new profile id, then the old state is archived.

  Verified by [A].
- **AC-GS-10** (REQ-GS-10) Given 1,000 ticks of the same unknown widget line, then exactly one WARN is logged — [A].
- **AC-GS-11** (REQ-GS-11) Review: snapshot types are immutable, and reader entry points assert the client thread in dev — [R]/[A].
- **AC-GS-12** (REQ-GS-12)
  - After a 30-minute Hypixel session with capture off, the config folder holds no raw chat, tab or sidebar text — [E].
  - An armed capture disarms after 60 min (fake clock) — [A].
- **AC-GS-13** (REQ-GS-13)
  - `/ksu debug dump tab|sidebar` writes tagged lines to the log — [B].
  - Real player names are masked (Self, Player1, …; whole, case-exact names only; NPCs and fake tab profiles kept), and the logged tab line shows the player's own name as Self — [A] [C].
  - While armed, each allowlisted menu the player opens is written once, after its contents are stable — [E].
  - In a production boot of the release jar, `/ksu debug dump tab|sidebar` and `/ksu debug dump containers on|off` exist, while `/ksu debug island` and, from Phase 3 on, `/ksu debug shaft` do not — [D].
- **AC-GS-14** (REQ-GS-14) Review: every parser test names its fixtures and their provenance, and the phase report lists the UNVERIFIED fixtures — [R].
- **AC-GS-15** (REQ-GS-15) Given a server switch from a mineshaft to the Dwarven Mines, then Frozen Corpses reports "absent" within 1 s of the switch event — [A].
- **AC-GS-16** (REQ-GS-16)
  - Given all consumers of the tab reader disabled, then its parse counter stays at 0 over 600 ticks — [A].
  - [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Given the mineshaft alert and the corpse markers OFF and a simulated shaft entry with sidebar code `OPAL_1`, when `/ksu corpses` is issued, then the shaft is reported as "Opal 1" and the state is kept until the simulated exit — [A].
  - Given the mineshaft alert and Rare Drop Odds OFF and a simulated entry with sidebar code `OPAL_1`, then the mineshaft state stores no code or corpse list and parses nothing over 600 ticks — [A].
- **AC-GS-17** (REQ-GS-17) Given a simulated clock:
  - all 34 known codes inside a sidebar line shaped like `<date> <server> OPAL_1` give their display names, `ABCD_1` gives "ABCD_1", and no code line gives "Unknown shaft" after 15 s
  - a Frozen Corpses fixture with `Umber: NOT LOOTED`, `Tungsten: LOOTED` and `Lapis: UNLOOTED` gives two not-looted types and one looted type
  - after a simulated server switch, the code and the corpse list are cleared

  Verified by [A]. In a mineshaft, the reported code and corpse list equal the raw capture — [E].
- **AC-GS-18** (REQ-GS-18)
  - Review: the widget hint text exists once in the translation file, and mineshaft-alert, bestiary-hud and sbxp-optimizer reference that key — [R].
  - In-game, the command and menu path in the hint open Hypixel's widget settings — [E]. Planned for G1; not answered there, so checked at G3 [decided R24].

**Edge cases**
- **EC-GS-01** The tab list has more than 80 entries and a widget is cut off → visible lines are parsed and nothing is inferred for missing lines.
- **EC-GS-02** The player has disabled a widget → "absent"; the consumer shows the shared hint (REQ-GS-18) at most once per session.
- **EC-GS-03** Skyblocker chat rules hide the profile lines → they are still observed through the cancelled-message path.
- **EC-GS-04** A mod hides the action bar below Fabric's events → it is not observed; consumers fall back to other sources, and the README documents this.
- **EC-GS-05** Hypixel re-sends the same action-bar text about every second → each receipt is delivered; consumers deduplicate by meaning (e.g. SBXP's window, REQ-SBXP-38).
- **EC-GS-06** A multi-page menu keeps one title for every page → each page is its own snapshot.
- **EC-GS-07** The menu closes before its contents are stable → no snapshot, no partial data.
- **EC-GS-08** A disconnect happens while a menu is open → close is signalled and nothing is stored after the disconnect.
- **EC-GS-09** The profile switches while a menu is open → snapshots before the new profile is identified are thrown away.
- **EC-GS-10** In the Rift the tab profile name appears reversed (e.g. `yrrebwartS`) → the tab profile name is ignored in the Rift, and the last identified profile is kept.
- **EC-GS-11** Dungeon or Kuudra tab lists have no profile type glyph → the name still matches and the type stays as last seen.
- **EC-GS-12** An item is held on the cursor while a menu is open → it is not counted until it is put down, so the count is briefly low.
- **EC-GS-13** The client language is not English → parsing still works, because the server text is English whatever the client language.
- **EC-GS-14** Numbers come with thousands separators, Roman numerals or Arabic numerals (depending on player settings) → all three parse. An abbreviated form (e.g. `12.4k`) is passed to consumers flagged approximate; consumers that need exact counts treat it as unknown, and no exact value is guessed from it.
- **EC-GS-15** Two Minecraft accounts are used in one instance → each has its own per-profile stores.
- **EC-GS-16** The GUI scale or window size changes while a menu is open → the snapshot is unaffected.

**Open questions**
- **Q-GS-01** Which storage counts toward "keys the player owns"? → decided D-7 (inventory only)

---

### world-markers — World marker toolkit (labels, beams, rings)

**Origin:** Derived. Needed by: brief Phase 3 item 4 (white labels and coloured beams) and item 6 (hotspot highlight), and item 3 (corpse waypoints) until R22 dropped it; ground rule 5; PLAN AD-2 and P1/P3/P4 | **Depends on:** port-26-2 | **Plan tasks:** T1.3, T3.0b, T3.0n (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** One shared renderer for in-world text labels, beacon beams and rings, used by NPC waypoints and hotspot highlights (corpse-spot waypoints were dropped by R22). It is where the rule is enforced that only fixed coordinates may be drawn through blocks.

**Functional requirements**
- **REQ-MARK-01** The toolkit provides three primitives:
  - a text label of 1–3 lines with a colour per line and an optional background
  - a beacon beam with a colour per marker
  - a horizontal ring with centre, radius, colour and alpha, drawn as an outline, a filled disc, or both. The outline is always opaque so it stays readable; the alpha applies to the disc, so a ring with a disc needs an alpha above 0 (an opacity setting of 0% means outline only) [T3.0n review]

  *(Brief: Phase 3 item 4 "white text labels with an individually colored beacon beam per waypoint"; item 3 "waypoints" (dropped by R22); item 6 "highlight active hotspots")*
- **REQ-MARK-02** See-through policy:
  - A label may show through blocks only if it is anchored to fixed world coordinates (static data or a fixed point).
  - Any marker anchored to or derived from a live entity is depth-tested. This includes markers derived from Hypixel name-tag holograms, such as hotspot rings [decided R2].
  - Beams and rings are always depth-tested.
  - Callers cannot ask for see-through on a marker anchored to an entity.
  - Fixed-coordinate labels that may show through blocks are the NPC waypoint labels. (The corpse-spot labels, text only under R7, were dropped with R22.)

  *(Brief: Ground rule 5; Derived: P1, P3, P4)* [decided D-6]
- **REQ-MARK-03** Label legibility:
  - See-through labels stay fully readable behind opaque blocks, glass, water and ice, with no overdraw or tint from translucent terrain, with Improved Transparency (the Fabulous graphics preset) on and off.
  - Labels face the camera.
  - Labels have natural size within 10 blocks and a constant on-screen size beyond 10 blocks.
  - The default text colour is white.

  *(Brief: Phase 3 item 4 "white text labels"; Derived: research G4 translucent overdraw; Skyblocker behaviour study)*
- **REQ-MARK-04** Beams:
  - A beam rises from the marker block to the world's build height, in the marker's colour (always opaque), animated like a vanilla beacon.
  - Its width grows with horizontal distance so it stays visible from far away.
  - No beacon block is needed.

  *(Brief: Phase 3 item 4 "individually colored beacon beam per waypoint")*
- **REQ-MARK-05** If the caller enables it, a fixed-coordinate label shows a line with the distance in whole metres from the player's position (not the camera) to the label's position (the anchor plus the label's rise above it), updated every frame. Markers anchored to entities have no distance line. *(Derived: P4; research read-npc bug 17; the toggle belongs to npc-waypoints)*
- **REQ-MARK-06** Several features supply markers at once (NPC waypoints, hotspots). Each feature's markers follow its own toggle and island gating, and turning one feature off never hides another's markers. *(Brief: Ground rule 6; Derived: research read-npc §3b — the current single static list blocks reuse)*
- **REQ-MARK-07** Markers outside the view frustum are not submitted. With 150 label-plus-beam markers active (about every Hub NPC), the marker pass should average at most 1 ms CPU per frame on the dev machine. *(Derived: Hub has 113 NPC entries; research read-npc performance)*
- **REQ-MARK-08** All markers are dropped on world change, server switch and disconnect. A marker from the previous world is never drawn in the next one. *(Derived: correctness across islands)*
- **REQ-MARK-09** Markers render correctly with shaders on and off, next to Skyblocker and SkyHanni world rendering. They need no mixin and cause no new ERROR log lines. *(Derived: the user's mod set, research G4; AD-9; REQ-XC-VERIFY)*

**Out of scope**
- Tracer lines from the player to targets; filled block boxes or highlights; entity outlines (owned by glow).
- See-through rings or beams; any see-through marker on a live entity.
- Textures, shaders or render types copied from SkyOcean, meowdding-lib or Skyblocker (ARR, attribution-clause or LGPL).
- Hiding particles; minimaps or radar HUDs; a UI for players to create their own waypoints (not requested).

**Acceptance criteria**
- **AC-MARK-01** (REQ-MARK-01, REQ-MARK-03, REQ-MARK-04)
  - Setup: a superflat gametest with a fixed marker (white label plus red beam) 30 blocks away.
  - Screenshots behind a stone wall, a glass pane, a 3-block water column and ice show the label fully legible with no tint, and the beam visible above the obstacle. Behind the water column and ice also with Improved Transparency on.
  - The label's pixel height at 20 m and at 60 m matches within ±10%.

  Verified by [C].
- **AC-MARK-02** (REQ-MARK-02)
  - Unit test: a see-through request for an entity-anchored marker is refused, so it renders depth-tested — [A].
  - Gametest: an entity-anchored label behind stone is not visible, while a fixed-coordinate label in the same spot is — [C].
- **AC-MARK-03** (REQ-MARK-01, REQ-MARK-02) A gametest ring of radius 3 on a water surface is visible in the open, untinted, and hidden behind a stone wall, with Improved Transparency on and off — [C].
- **AC-MARK-04** (REQ-MARK-05) Given the player at (0,64,0), the camera in third person 4 blocks behind, and a marker at (0,64,100), then the distance line reads `100m` — [A] and [C].
- **AC-MARK-05** (REQ-MARK-06) Given an NPC waypoint provider and a second provider (a test provider until the hotspot ring exists) both active, when NPC waypoints are turned off, then the second provider's markers are still drawn — [A]/[C]. (The second provider was the corpse-spot provider until R22 dropped it.)
- **AC-MARK-06** (REQ-MARK-07) A gametest with 150 markers logs an average marker-pass time of at most 1 ms over 600 frames, and markers behind the camera are counted as not submitted — [C]. The marker pass is measured both as its submit step and as what the markers add to the CPU frame time (A/B against no markers); the gametest runs one frame per tick, so GPU time is not part of it.
- **AC-MARK-07** (REQ-MARK-08) After a world change, the first frame of the new world has 0 markers from the old one — [A]/[C].
- **AC-MARK-08** (REQ-MARK-09)
  - User smoke test: a Hub NPC label and beam, and a hotspot ring, are visible with shaders on and off and next to Skyblocker waypoints — [E].
  - A production boot with the user's mod set shows no new ERROR lines — [D].

**Edge cases**
- **EC-MARK-01** The player stands on the marker (distance 0) → the label renders without NaN or flipping and the distance shows `0m`.
- **EC-MARK-02** The marker is beyond render distance or in an unloaded chunk → the label is still drawn (fixed coordinates) and no chunk or block data is queried.
- **EC-MARK-03** The marker is below the player in a cave → the beam starts at the marker block, and terrain hides it where it passes through (depth-tested).
- **EC-MARK-04** The marker coordinate is inside an opaque block → the see-through label above it is still readable.
- **EC-MARK-05** Third-person front view or spectator → labels face the camera, and distance is measured from the player.
- **EC-MARK-06** F1 (hide GUI) → labels should hide like vanilla name tags; beams and rings stay.
- **EC-MARK-07** The GUI scale changes → world labels are unaffected.
- **EC-MARK-08** A beam colour has alpha 0 → the beam is drawn opaque.
- **EC-MARK-09** Two markers share a position (duplicate data) → both are drawn; deduplication is the provider's job.
- **EC-MARK-10** A shader pack changes how beacon beams look → the beam may look different, but the label stays readable (checked in the [E] smoke test).
- **EC-MARK-11** The marker is at or above build height, or below y = −64 → the beam is clipped and nothing crashes.

**Open questions**
- **Q-MARK-01** May fixed-coordinate markers be drawn through blocks? → decided D-6 (P4: only fixed-coordinate labels show through blocks; beams, rings and anything derived from an entity are depth-tested)

---

### npc-mob-data — NPC & mob list review and bundling

**Origin:** Brief Phase 3 item 9; Derived (ground rule 5, P2) | **Depends on:** data-registry, location | **Plan tasks:** T1.12, T3.8a, T3.8, T3.8b, T3.8c, T7.4 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Audit the existing NPC and mob lists and recommend, per list, whether each stays external or moves into the internal database, based on update frequency, size and reliability. Then implement that choice, fix the data defects, and keep existing user rules and 1.0.x clients working.

**Functional requirements**
- **REQ-NPCDB-01** The repository must contain a written audit of each list (NPC DB and mob DB) recording:
  - entries per island
  - duplicates and near-duplicates
  - entries with missing or invalid fields
  - mobs that are invisible by design
  - islands with no coverage
  - the remote source's revision history (count and dates)
  - file size in bytes
  - the reliability findings for the current fetch (timeouts, caching, fallback, validation)

  *(Brief: Phase 3 item 9 "audit the current NPC and mob data")*
- **REQ-NPCDB-02** For each list, the audit must state a recommendation (external or bundled) and justify it with the measured update frequency, size and reliability. *(Brief: Phase 3 item 9 "Recommend whether each list should stay external or move into an internal (bundled) database, based on update frequency, size, and reliability")*
- **REQ-NPCDB-03** Implementation: both lists load from the internal database (data-registry). There is no startup request to the gists, and both database pickers work on an offline first launch. *(Brief: Phase 3 item 9 "Implement the recommendation")* [decided D-12]
- **REQ-NPCDB-04** No remote source. From the release that bundles the lists (T3.8) on, the mod must keep no remote NPC or mob source:
  - no request to the gists or any other host for NPC or mob data
  - no on-disk cache of remote NPC or mob data
  - the bundled table is the only source, and updates arrive with mod releases

  The brief's "keep a fallback if remote data is still used" therefore does not apply. *(Brief: Phase 3 item 9 "keeping a fallback if remote data is still used"; Derived: AD-7)* [decided D-12]
- **REQ-NPCDB-05** Interim hardening for the release that still fetches the gists (v1.1.0, published after G1 and before T3.8):
  - HTTP requests time out after at most 10 s to connect and 10 s for the request.
  - A malformed entry is skipped and never raises an exception in the picker. Malformed means: a null id, displayName or island, a fixed NPC without coordinates, or a moving NPC or mob without matchText.

  *(Derived: Brief item 9 "reliability"; research read-npc bugs 5–6)* [decided D-13]
- **REQ-NPCDB-06** Entry validity.
  - **NPC entries:** unique id, non-blank displayName, and an island that is one of the location module's island names.
    - Fixed entries have integer coordinates with y in [−64, 320) and are not (0,0,0).
    - Moving entries have a non-blank matchText.
  - **Mob entries:** unique id, non-blank displayName and matchText, an island that is a location island name or an allowlisted ungated group (e.g. "Fishing", "Mythological Creatures", "Spooky Festival"), and an optional subfolder.

  *(Derived: Brief item 9 "reliability"; location dependency)*
- **REQ-NPCDB-07** Data corrections:
  - (a) NPCs that stand in the Dungeon Hub carry the island "Dungeon Hub". Trinity, Tomioka and Duncan stay "Catacombs" (dungeon runs) [decided D-2].
  - (b) Each near-duplicate is resolved and the outcome recorded in the audit (merged, corrected, or confirmed distinct): `bramass_beastslayer`/`_2`, `hendrik`/`_2`, `bestiary` sharing coordinates with `bramass_beastslayer_2`, and Arba/Arbadak.
  - (c) Glacite mobs that spawn in Glacite Mineshafts can also be found under "Glacite Mineshafts".
  - (d) Ids are stable: a merged or renamed entry keeps its old id or declares an alias, so rules that link to it keep their link.

  *(Derived: Brief item 9 audit; research read-npc §3d; PLAN T1.9)*
- **REQ-NPCDB-08** Mobs that are invisible by design must not be addable as highlight targets from the picker. That is at least Ghost, Fels, Sneaky Creeper and Invisibug. They stay in the bundled data, and the picker shows them greyed, not addable, labelled "never highlighted (Hypixel rules)". *(Brief: Ground rule 5; Derived: P2, D-1)* [decided D-1] [decided R5]
- **REQ-NPCDB-09** Existing rules survive the switch:
  - A rule created from the 1.0.x database keeps its name pattern and island (with the location migration).
  - A fixed-NPC rule created from a DB entry must follow that entry's corrected coordinates, through its sourceId (the DB entry id).

  *(Derived: Brief item 9 "Implement" — fixes are useless if they never reach existing rules; research read-npc bug 7)* [decided R5]
- **REQ-NPCDB-10** The two existing gists are frozen, not deleted. They must stay reachable at their current URLs and keep serving a JSON array that 1.0.x clients can parse. The user removes the invisible-by-design mobs (REQ-NPCDB-08) from the 1.0.x mob gist himself; the mod's code never writes to the gists. *(Derived: 1.0.x clients fetch them on every launch; PLAN AD-7; ground rule 5)* [decided D-12] [decided R5]

**Out of scope**
- Adding NPCs or mobs for islands that have no coverage today (Rift, Backwater Bayou, Farming Islands, Galatea, Glacite Mineshaft NPCs), beyond the corrections in REQ-NPCDB-07. The gaps are listed in the audit (REQ-NPCDB-01) [decided R5].
- Filling in entity types for mob entries (the schema may reserve an optional field) [decided R16].
- In-game editing of the databases; user-supplied database files.
- Matching and glow behaviour (glow) and waypoint visuals (npc-waypoints).
- Deleting the gists [decided D-12].
- Any remote refresh, cache or fallback path for NPC or mob data (REQ-NPCDB-04) [decided D-12].

**Acceptance criteria**
- **AC-NPCDB-01** (REQ-NPCDB-01, REQ-NPCDB-02) Review: the audit document has one table per list with every field from REQ-NPCDB-01, plus a recommendation row that cites the measured revisions and dates, the size in bytes and the observed failure modes — [R].
- **AC-NPCDB-02** (REQ-NPCDB-03)
  - Given the network unreachable and a fresh config, when both pickers are opened, then each lists exactly the row count of its table's index entry, grouped by island, and no request to `gist.githubusercontent.com` appears in the log — [B].
  - Review: the gist URLs no longer appear in the main source set — [R].
- **AC-NPCDB-03** (REQ-NPCDB-04)
  - Given a launch with the network reachable, when both pickers are opened, then no request for NPC or mob data goes to any host, and the config folder holds no cached NPC or mob data — [B].
  - Review: the main source set has no remote-load, cache or fallback path for NPC or mob data — [R].
- **AC-NPCDB-04** (REQ-NPCDB-05)
  - Given a payload with one null-island entry and one fixed NPC without coordinates, when parsed, then all other entries load and no exception is raised — [A].
  - Given a server that accepts the connection and never responds, then the request ends within 10 ± 1 s with one WARN — [A].
- **AC-NPCDB-05** (REQ-NPCDB-06) Data tests on the bundled tables:
  - ids are unique
  - island values come from the location island set or the ungated allowlist
  - fixed coordinates are valid
  - moving entries have a matchText

  Verified by [A].
- **AC-NPCDB-06** (REQ-NPCDB-07)
  - Data tests: no `fixed=true` NPC has island "Catacombs" [decided D-2]; trinity, tomioka and duncan are `fixed=false` on "Catacombs"; every near-duplicate pair is resolved as the audit says; at least one "Glacite Mineshafts" mob group exists — [A].
  - Each merged or renamed id resolves through its alias — [A].
- **AC-NPCDB-07** (REQ-NPCDB-08) Given the mob picker and a search for "Ghost", then Ghost is listed greyed with "never highlighted (Hypixel rules)" and cannot be added; the same holds for Fels, Sneaky Creeper and Invisibug — [C] [decided R5].
- **AC-NPCDB-08** (REQ-NPCDB-09)
  - Given a config fixture shaped like 1.0.1 with rules added from the old DB, when it is loaded, then every rule keeps its pattern and migrated island — [A].
  - Given an entry whose coordinates differ between two table versions, then a rule with that sourceId resolves to the new coordinates — [A].
- **AC-NPCDB-09** (REQ-NPCDB-10) At release (T7.4):
  - both gist raw URLs return 200 and a JSON array that the 1.0.1 parsing code reads without error — [R]
  - the mob gist contains no Ghost, Fels, Sneaky Creeper or Invisibug entry — [R] [decided R5]

**Edge cases**
- **EC-NPCDB-01** A rule's sourceId no longer exists (the entry was removed with no alias) → the rule keeps its stored values and still works; the picker shows it as a custom rule.
- **EC-NPCDB-02** A user rule without a sourceId duplicates a DB entry (like the user's Trinity, Tomioka and Duncan rules) → the migration creates no second rule; the picker marks the entry as already added when name pattern and island match.
- **EC-NPCDB-03** Hypixel adds or renames an island mode → the build-time validator rejects unknown island values until the location module knows them; rules gated on that island stay inactive and are logged once.
- **EC-NPCDB-04** First launch with no network → both pickers are fully populated from the bundled data.
- **EC-NPCDB-05** A 1.0.x client and a 2.x client run side by side in different instances → 1.0.x still reads the gists, and 2.x never contacts them.
- **EC-NPCDB-06** An NPC entry sits at a coordinate inside a block (wrong y) → it is caught in the audit; the waypoint still renders (fixed coordinates).

**Open questions**
- **Q-NPCDB-01** Bundled only, or keep a remote refresh? → decided D-12 (bundled only, gists frozen)
- **Q-NPCDB-02** Should invisible-by-design mobs be removed from the picker, or shown greyed with "never highlighted"? → decided R5
- **Q-NPCDB-03** What happens to the gists that 1.0.x clients still read? → decided R5 (frozen per D-12; the user removes the invisible mobs from the mob gist)
- **Q-NPCDB-04** Should v1 add NPCs or mobs for islands with no coverage? → decided R5
- **Q-NPCDB-05** Should corrected DB coordinates reach existing fixed-NPC rules? → decided R5

---

### mineshaft-alert — Mineshaft entry alert (corpse list + key count)
**Origin:** Brief Phase 3 item 2 | **Depends on:** port-26-2, game-state (shaft code and Frozen Corpses state, REQ-GS-17; widget hint, REQ-GS-18), location (toggles through config-store / ui-config per REQ-XC-TOGGLE) | **Plan tasks:** T3.2 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** When the player enters a Glacite Mineshaft, the mod posts one local chat message. It names the shaft, lists the frozen corpses the server reports for it, and shows how many of each matching key the player carries. The player then knows before mining whether every corpse can be opened.

"Entry" below means the moment the location service reports Hypixel mode `mineshaft` for a server id that differs from the previous one. All times are measured from entry.

**Functional requirements**
- **REQ-MSA-01** Trigger. The alert must run on every entry into a Glacite Mineshaft:
  - shafts the player found;
  - shafts reached by party summon or warp into another player's shaft.

  It must not require that the player found the portal (SkyOcean's 60 s "found" gate is not copied). Per server id there is at most one first alert, plus the follow-ups of REQ-MSA-07/08. *(Brief: Phase 3 item 2 "when entering a Glacite Mineshaft"; Derived: party-summon cases in the user's logs, research G6 §1)*
- **REQ-MSA-02** Shaft identification.
  - The alert must name the shaft from game-state's mineshaft state (REQ-GS-17). REQ-GS-17 owns the shaft code `<TYPE>_<VARIANT>` (pattern `[A-Z]{4}_[12CL]`, read from the sidebar scoreboard data the server sends, not from the rendered sidebar), the 15 s search after entry and the 34-code display table. This module must not parse the code itself.
  - Display names come from that table, e.g. `OPAL_1` → "Opal 1", `RUBY_C` → "Ruby Crystal", `FAIR_1` → "Vanguard", `LITT_L` → "Littlefoot's Den".
  - A code that matches the pattern but is not known is shown raw.
  - No code within 15 s gives "Unknown shaft".

  *(Derived: corpse data is per shaft; one shared parser for mineshaft-alert and rare-drop-odds (corpse-waypoints was dropped by R22); research domain-mining-rules B2, G6 §5)*
- **REQ-MSA-03** Corpse source.
  - Corpse types and per-type counts must come **only** from the server's "Frozen Corpses:" tab-list widget, as parsed by game-state (REQ-GS-17) from the tab-list entries the server sends, independent of tab order and of how other mods draw the tab list.
  - Line format (parsed in REQ-GS-17): `<Type>: NOT LOOTED|LOOTED`. `UNLOOTED` also counts as not looted.
  - The feature must never scan, classify or count armor stands or any other entity, and must never fall back to entity data.

  *(Brief: item 2 "listing the corpse types found"; Brief ground rule 5; Derived: PLAN §3 P3, research G3 row H, G6 §1, critic)*
- **REQ-MSA-04** Settling. The first alert must be posted once the widget's corpse list has been non-empty and unchanged for 1.5 s. Expected arrival is 1–5 s after the server switch, and 1–2 s when party-summoned. *(Derived: avoid posting a partial list; research G6 §1, §5)*
- **REQ-MSA-05** Key mapping and count [decided D-7].
  - Every corpse type present gets a key entry: Tungsten → Tungsten Key (`TUNGSTEN_KEY`), Umber → Umber Key (`UMBER_KEY`), Vanguard → Skeleton Key (`SKELETON_KEY`). Lapis needs no key and gets no entry.
  - Keys are recognised by the SkyBlock item id in the item's custom data, never by display name. Stack sizes are summed.
  - **Counted:** only the player's own inventory, meaning the 36 hotbar + main slots and the offhand slot, read when the message is built.
  - **Not counted:** sacks (including the Dwarven Sack), ender chest, backpacks, storage, the cursor stack, and slots of any open container.
  - The entry must say the count is "in inventory".
  - **Fallback if the sack check fails:** if the in-game check (Q-MSA-04) shows that a Tungsten or Umber key held only in the Dwarven Sack opens a corpse, then for those types the shortage marker of REQ-MSA-06 is not shown, the entry reads "n in inventory (sack keys also work)", and D-7 is put to the user again with "include sacks" recommended. The switch is a build constant set from the check's result, not a user toggle.
  - **G1 result [decided R24]:** the sack check could not be tested at G1. D-7 (inventory only) stands, the fallback constant stays off, and the check stays open for G3.

  *(Brief: item 2 "how many of the matching keys the player owns"; Derived: Hypixel 0.20.6 "consumes from inventory", PLAN D-7, research G6 §2; review coverage#4)*
- **REQ-MSA-06** Shortage marker.
  - For each keyed type, *keys needed* = the number of that type's corpses in state NOT LOOTED.
  - If the inventory count is lower than keys needed (including 0 keys), the entry must show the number, the warning sign U+26A0 and red text.
  - Otherwise it shows the number in green with no warning sign.
  - Under the REQ-MSA-05 fallback, the affected types never show the warning sign or red text.

  *(Derived: purpose of the key count; PLAN T3.2 example)*
- **REQ-MSA-07** Late corpses (Dead Man's Chest) [decided R7].
  - If the corpse list grows within 30 s after entry (e.g. after "MORE! You have spawned 1 additional Frozen Corpse!"), the mod must post one follow-up line. It carries the full updated list and recomputed key entries, and is posted once the list has again been stable for 1.5 s.
  - A change from NOT LOOTED to LOOTED never produces a message.
  - Growth after 30 s is ignored.

  *(Derived: extra corpse appears at +3/+4 s, research G6 §1; PLAN T3.2 "update if a Dead Man's Chest adds one")*
- **REQ-MSA-08** Missing widget.
  - If no corpse line has appeared 8 s after entry, the mod must post the alert with the shaft name and "corpse list unavailable".
  - At most once per game launch, it adds a hint: the Frozen Corpses tab widget is not visible, and here is how to enable it in Hypixel's tab-list widget settings. The command and menu path come from the shared widget-hint string of REQ-GS-18 (Q-MSA-04; UNVERIFIED after G1, checked at G3 [decided R24]). This module must not hard-code its own command.
  - This hint is the failsafe the maintainer asked for at G1 [decided R24]: the widget is believed to be on by default, and the hint tells the player when it is off.
  - There is no entity fallback.
  - If corpse lines appear later within the 30 s window, a normal alert follows as a follow-up.

  *(Derived: the widget's default state is UNVERIFIED; research G6 §1, §5; PLAN T3.2)*
- **REQ-MSA-09** Message form.
  - One local, client-side chat line with the mod's `[KSU] ` prefix (ChatUtils), in this order:
    1. shaft name;
    2. corpse counts per type, in the fixed order Lapis, Tungsten, Umber, Vanguard, with types not in the widget omitted;
    3. key entries in the same order, or "no keys needed" when no keyed type is present.
  - Example: `[KSU] Opal 1 · 1 Lapis, 1 Tungsten, 1 Umber · Tungsten Key: 2 in inventory · Umber Key: 0 in inventory ⚠`.
  - Corpses already LOOTED when the message is built are shown as "(n looted)" after that type's count, e.g. "2 Lapis (1 looted)".
  - Hovering a key entry should show that keys in sacks or storage are not counted.

  *(Brief: item 2 "post a chat alert listing ..."; Derived)*
- **REQ-MSA-10** Display-only. The feature must never:
  - send chat or commands to the server (no automatic `/pc`, party, guild or all chat);
  - open, click or page any menu (e.g. no automatic `/sacks`);
  - cancel, delay or modify any packet.

  *(Brief ground rule 5; Derived: PLAN §3 P5 and "Flagged, not implemented: automatic party-chat announcements")*
- **REQ-MSA-11** Toggles [decided D-6]. Both live in the Mining category and are found by search terms "mineshaft", "corpse" and "key":
  - "Mineshaft entry alert", default ON;
  - "Show corpse key counts", default ON. When OFF, the line has no key part.

  *(Brief ground rule 6; REQ-XC-TOGGLE; PLAN §3 table, D-6; research G6 §5 toggle list)*
- **REQ-MSA-12** Reset.
  - All alert state (posted flag, pending timers, last list) resets on server or world change and on disconnect.
  - A pending alert for a shaft the player has left is cancelled.

  *(Derived)*

**Out of scope**
- Counting keys in sacks, ender chest, backpacks or storage, and any per-profile sack or storage cache (v1) [decided D-7]. The REQ-MSA-05 fallback changes only the wording and the shortage marker; it adds no sack count.
- Automatic party, guild or all-chat announcements (SkyOcean's default PARTY mode): excluded by ground rule 5. A click-to-fill share button is not in v1 [decided R7].
- Finding, highlighting or marking corpses or the invisible portal armor stand from entity data.
- Title, sound, notice (toast) or HUD versions of the alert (the brief asks for a chat alert; REQ-UI-21), and a live key-count HUD.
- Corpse loot, profit or odds (covered by rare-drop-odds, T3.9).
- Mineshaft pity, scrap count and cave-in timer.
- Opening any menu to refresh data.

**Acceptance criteria**
- **AC-MSA-01** (REQ-MSA-03, REQ-MSA-05, REQ-MSA-09)
  - Given: a sanitised widget fixture with ` Lapis: NOT LOOTED`, ` Tungsten: NOT LOOTED`, ` Umber: NOT LOOTED`, sidebar code `OPAL_1`, and an inventory of 2 `TUNGSTEN_KEY` and 0 `UMBER_KEY`.
  - When: the message is built.
  - Then: the line starts with `[KSU] `, and its text after the tag equals `Opal 1 · 1 Lapis, 1 Tungsten, 1 Umber · Tungsten Key: 2 in inventory · Umber Key: 0 in inventory ⚠`.
  - — [A] (the fixture is replaced by the real G1 capture and is marked UNVERIFIED until then)
- **AC-MSA-02** (REQ-MSA-02, REQ-GS-17)
  - Given: a table of all 34 known codes inside a sidebar line shaped like `<date> <server> OPAL_1`, plus `ABCD_1`, plus no code line.
  - When: each is parsed by the game-state mineshaft reader and the alert names the shaft.
  - Then: every known code gives its display name, `ABCD_1` gives "ABCD_1", and no code gives "Unknown shaft" after 15 s of simulated time. Code review confirms that mineshaft-alert has no shaft-code parser of its own.
  - — [A] + [R]
- **AC-MSA-03** (REQ-MSA-05)
  - Given: 3 `UMBER_KEY` in the hotbar, 2 in the main inventory, 1 in the offhand; an item renamed "Umber Key" with another id; 4 `UMBER_KEY` in an open chest's slots.
  - When: keys are counted.
  - Then: the Umber count is 6.
  - — [A]
- **AC-MSA-04** (REQ-MSA-06, REQ-MSA-09). Given each case below, when the message is built, then:

  | Corpses | Keys | Result |
  |---|---|---|
  | 2 Tungsten, NOT LOOTED | 1 | warning sign + red |
  | 2 Tungsten, NOT LOOTED | 2 | green, no warning sign |
  | 1 Tungsten LOOTED + 1 Tungsten NOT LOOTED | 1 | green, and "2 Tungsten (1 looted)" |
  | Lapis-only shaft | – | "no keys needed" |

  — [A]
- **AC-MSA-05** (REQ-MSA-04, REQ-MSA-07). Given a simulated clock, when the alert state machine runs, then:
  - **(a)** Empty until 2.0 s, 2 corpses at 2.0 s, 3 at 3.0 s, stable after that: exactly one alert at 4.5 s listing 3 corpses.
  - **(b)** 2 corpses at 2.0 s, stable: alert at 3.5 s listing 2. A third corpse at 4.5 s: one follow-up at 6.0 s listing 3.
  - **(c)** Growth at 31 s: no follow-up.
  - **(d)** A NOT LOOTED → LOOTED change: no message.
  - — [A]
- **AC-MSA-06** (REQ-MSA-08). Given a simulated clock, then:
  - No corpse line by 8 s: an alert with "corpse list unavailable", plus the hint.
  - A second widget-less shaft in the same launch: no second hint.
  - Corpse lines at 12 s, stable: a follow-up alert at 13.5 s.
  - — [A]
- **AC-MSA-07** (REQ-MSA-01, REQ-MSA-12). Given simulated location events, then:
  - `mineshaft` reported twice for the same server id: one alert.
  - A new server id: a new alert.
  - The player leaves at 1.0 s, before the list settles: no alert.
  - — [A]
- **AC-MSA-08** (REQ-MSA-10)
  - Given: a recording fake for server-bound chat, commands and container clicks.
  - When: every unit scenario of this module runs.
  - Then: nothing is recorded. Code review also confirms that the module has no call that sends chat, sends commands or clicks.
  - — [A] + [R]
- **AC-MSA-09** (REQ-MSA-11)
  - In a fresh config both toggles are ON.
  - With "Mineshaft entry alert" OFF, no message is built.
  - With "Show corpse key counts" OFF, the line has no key part.
  - — [A]
- **AC-MSA-10** (REQ-MSA-01..REQ-MSA-09)
  - Given: the user enters a shaft they found, and then (opportunistically) is party-summoned into someone else's shaft.
  - Then, each time: exactly one alert appears within 10 s. Its corpse counts match the tab list, and its key counts match the keys in the inventory.
  - — [E]
- **AC-MSA-11** (REQ-MSA-03, REQ-MSA-08)
  - Given: the user turns off the Frozen Corpses widget in Hypixel's tab-list settings (or confirms that it cannot be turned off).
  - When: they enter a shaft.
  - Then: the alert says "corpse list unavailable", the hint appears once, and no entity-based list appears.
  - — [E]
- **AC-MSA-12** (REQ-MSA-03)
  - Given: a production boot with the user's mod set (Skyblocker fancy tab HUD, SkyHanni custom scoreboard, SkyOcean).
  - When: the alert pipeline runs.
  - Then: there is no crash and no new ERROR line. In-game, the corpse list still appears while those mods restyle the tab list and sidebar.
  - — [D] + [E]
- **AC-MSA-13** (REQ-MSA-05, REQ-MSA-06)
  - Given: 1 Umber corpse NOT LOOTED, 0 `UMBER_KEY` in the inventory, and the sack-keys fallback constant switched on.
  - When: the message is built.
  - Then: the entry reads `Umber Key: 0 in inventory (sack keys also work)`, with no warning sign and no red text. With the constant off, the AC-MSA-04 results hold unchanged.
  - — [A]
- **AC-MSA-14** (REQ-MSA-08)
  - Given: the shared widget-hint string of REQ-GS-18 replaced by a test value.
  - When: the missing-widget hint is built.
  - Then: the hint contains the test value. Code review finds no literal `/tablist`, `/widget` or `/widgets` in this module.
  - — [A] + [R]

**Edge cases**
- **EC-MSA-01** Party summon or warp into another player's shaft: the alert fires for the joiner (widget filled 1–2 s after arrival). LOOTED reflects the player's own looting, which is per player since Hypixel's Apr 11 2024 fix.
- **EC-MSA-02** Dead Man's Chest corpse appears after the first alert: one follow-up line (REQ-MSA-07).
- **EC-MSA-03** 0 keys of a needed type: "Key: 0 in inventory" plus the warning sign, in red. It is never hidden.
- **EC-MSA-04** Keys only in the Dwarven Sack: counted as 0 with the warning sign, by design [decided D-7]. The hover text explains why. If the sack check (open after G1, done at G3 [decided R24]) shows that sack keys work, the REQ-MSA-05 fallback applies instead.
- **EC-MSA-05** Widget disabled, or cut off because the tab list has more than 80 entries (UNVERIFIED): handled by REQ-MSA-08.
- **EC-MSA-06** Widget header present but no corpse lines after 8 s: "no corpses listed", without the hint (the widget is visible).
- **EC-MSA-07** Unknown corpse type line (a new type): listed by its raw name after the known types, with no key entry. Logged once per launch.
- **EC-MSA-08** Another mod hides the vanilla sidebar or tab list: the data is still read from the scoreboard and tab-list entries. If no code appears, "Unknown shaft" is shown and the corpse part still works.
- **EC-MSA-09** Disconnect, `/warp` or a server switch before the list settles: the pending alert is cancelled.
- **EC-MSA-10** Location re-sent for the same server id: no duplicate alert.
- **EC-MSA-11** The player takes keys from the sack after the alert: no re-post. Only follow-ups recount.
- **EC-MSA-12** SkyOcean or SkyHanni print their own mineshaft messages: ours is independent, and there is no de-duplication across mods.
- **EC-MSA-13** Not on Hypixel SkyBlock (singleplayer or dev run): the feature stays inactive and nothing is logged.

**Open questions**
- **Q-MSA-01** Should the key count be inventory-only, or include sacks and storage? → decided D-7 (inventory only; asked again only if the sack check triggers the REQ-MSA-05 fallback). The check could not be tested at G1, so D-7 stands and the check moves to G3 [decided R24]
- **Q-MSA-02** Dead Man's Chest corpses that appear after the first alert: a follow-up line, one delayed message, or editing the earlier line? → decided R7
- **Q-MSA-03** Should a user-clicked "share" button exist, one that only fills the chat box? → decided R7
- **Q-MSA-04** Will you run two short in-game checks at G1? → decided R17
  - The widget's default state, and the command and menu path that enable it (`/tablist` vs `/widgets`; becomes the REQ-GS-18 string). → G1 answer [decided R24]: the maintainer believes the widget is on by default and asks for a failsafe that says when it is off; the REQ-MSA-08 hint is that failsafe. The command and menu path were not given, so the hint wording stays UNVERIFIED and is checked at G3.
  - Whether a key held only in the Dwarven Sack opens a corpse (decides the REQ-MSA-05 fallback). → G1 answer [decided R24]: not testable at G1. D-7 stands, the fallback is not triggered, and the question stays open for G3.

---

### corpse-waypoints — Possible corpse spots (waypoints + list) [dropped R22]
**Origin:** Brief Phase 3 item 3 | **Depends on:** world-markers, data-registry, game-state (shaft code and Frozen Corpses state, REQ-GS-17), location | **Plan tasks:** T3.3a and T3.3, both dropped by R22; none remain (§13.1)

**Status: [dropped R22].** The maintainer dropped corpse-spot waypoints after G1 (2026-10-01). Nothing in this module is built: no spot table, no markers and no `/ksu corpses`. The text below is kept as the record. Every requirement, acceptance criterion, edge case and open question in it carries `[dropped R22]`; no id is reused. The rest of the mineshaft area stays: the entry alert (mineshaft-alert, T3.2), the Frozen Corpses widget reading (game-state REQ-GS-17, T3.0m) and the corpse odds (rare-drop-odds, T3.9d and T3.9e). The README's excluded-behaviour line "filtering possible corpse spots by entities you cannot see" stays in REQ-REL-03 as an exclusion.

**Purpose:** Inside a Glacite Mineshaft, the mod shows every known possible corpse spawn spot for the current shaft layout. The spots appear as in-world waypoints and as a chat list on request. They come only from bundled static data, so the player can search the shaft systematically without the mod ever revealing live corpse entities.

**Functional requirements**
- **REQ-CORPSE-01** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Bundled spot data [decided D-5].
  - The mod must ship a table of possible corpse spots per shaft code as a table in the internal data registry, with `dataVersion`, `gameVersion`, sources with pinned commits, and `license`.
  - Every spot should record its source(s).
  - **Content (D-5: MIT with notices, pinned commit, ship now with credit):**
    - SkyHanni-REPO PR #759 @ `e2c8edb932` as the base, plus the meowdding-repo @ `42d01278a8` extras.
    - Coordinates normalised to one frame: the block above the corpse armor stand, which is the frame of Skyblocker's "Found a … Corpse at x, y, z" lines.
    - 29 codes, 129 spots; all `_C` (crystal) codes share one list.
  - Codes without known spots (currently `TOPA_2`, `SAPP_2`, `AMET_2`, `AMBE_2`, `JADE_2`) are listed explicitly as "no spot data".

  *(Brief: item 3; Brief ground rule 4; Derived: PLAN D-5, AD-7, research G6 §3–4)*
- **REQ-CORPSE-02** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* No runtime fetch. Spot data must never be downloaded from third-party repositories (meowdding, SkyHanni, ShaftUtils) or any other host at runtime. Data updates ship with mod releases. *(Derived: PLAN AD-7, D-12 policy, research G6 §4)*
- **REQ-CORPSE-03** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Markers [decided D-6; decided R7].
  - Markers show when all of these hold:
    - the feature is ON;
    - the player is in a Glacite Mineshaft;
    - the shaft code (REQ-GS-17) is known and has spots.
  - Each spot for that code then gets a marker: the text label "Possible corpse #n", where n is the spot's stable 1-based index in the data list.
  - The label is anchored at the stored block's centre. It keeps a constant on-screen size from 10 blocks out.
  - Markers are **text only** (no box, no beam). They may be visible through terrain because they are fixed coordinates.
  - Feature and label names must follow naming policy P7 (PLAN §3).

  *(Brief: item 3 "with waypoints"; Derived: PLAN §3 P4, P7, research G3 row F)*
- **REQ-CORPSE-04** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Unfiltered.
  - Markers must never be hidden, filtered, recoloured, reordered or added based on any entity data, including corpse armor stands, whether or not they are in line of sight.
  - The displayed set may depend only on the shaft code and on REQ-CORPSE-05.

  *(Brief ground rule 5; Derived: PLAN §3 "never filtered by unseen entities", research G3 row F, domain-mining-rules C)*
- **REQ-CORPSE-05** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Hide when all looted.
  - Applies when the Frozen Corpses widget (as parsed in REQ-GS-17) lists at least one corpse and every listed corpse is LOOTED. All markers must then hide within 1 s.
  - Markers return if the widget later lists a NOT LOOTED corpse.
  - Own toggle "Hide spots when all corpses are looted", default ON.
  - With no widget, markers stay until the player leaves the shaft.

  *(Derived: the only allowed pruning is from server UI, research G6 §4; PLAN T3.3)*
- **REQ-CORPSE-06** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* List command [decided R7].
  - `/ksu corpses` must print a local chat list:
    - a header with the shaft name and spot count;
    - one line per spot, `#n x, y, z · d m`, where d is the 3-D distance from the player's position rounded half-up to whole blocks;
    - lines sorted by ascending distance.
  - Special replies:
    - outside a shaft: "Not in a Glacite Mineshaft";
    - during the code search: "Shaft not identified yet";
    - code without data: "No spot data for <code>";
    - data table unavailable: "Corpse spot data unavailable".
  - The command works whatever the marker toggle says, including while the markers and the mineshaft entry alert are both OFF. A running `/ksu corpses` request counts as a subscriber of the mineshaft state (REQ-GS-16, REQ-GS-17) for the current shaft, so the shaft code is read even when no feature subscribes.

  *(Brief: item 3 "show a list of possible corpse locations"; PLAN T3.3; review consistency#5)*
- **REQ-CORPSE-07** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Lifetime. Markers appear within 1 s after the shaft code becomes known. They disappear within 1 s after the player leaves the shaft, switches server or disconnects. *(Derived)*
- **REQ-CORPSE-08** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Toggle and default [decided D-6; decided R7].
  - The "Possible corpse spots" toggle sits in the Mining category and defaults to OFF.
  - Its tooltip says these are known possible spawn spots from community data, not detected corpses.

  *(Brief ground rule 6; REQ-XC-TOGGLE; PLAN D-6, §3 AMBER verdict)*
- **REQ-CORPSE-09** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Attribution. The jar and `THIRD_PARTY_NOTICES.md` must carry the MIT licence text and copyright line of every shipped source, e.g. "Copyright (c) 2022 hannibal2" and "Copyright (c) 2026 meowdding", plus credit to GrowlingGrizzly. The README must say this data file is MIT, not CC0. *(Brief ground rule 4; REQ-XC-LICENSE)*
- **REQ-CORPSE-10** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Data validation (build).
  - Every key must be a known code matching `[A-Z]{4}_[12CL]`.
  - Every spot must parse as three integers inside the plausibility box x ∈ [−260, −40], y ∈ [−10, 80], z ∈ [−260, −90]. Current data spans x −196…−106, y 1…29, z −198…−154.
  - No code may hold duplicate spots.
  - Every `_C` code must resolve to the shared crystal list.
  - Any violation fails `./gradlew check`.

  *(Derived: REQ-XC-BUILD, data-registry validator)*

**Out of scope**
- Detecting real corpses from armor stands, even after line of sight. Skyblocker already does this, and the brief does not ask for it.
- Any see-through corpse marking based on entities: disallowed by ground rule 5.
- Boxes or beacon beams on corpse spots [decided R7].
- Hiding a single spot after looting (would need entity or position inference).
- Remote overrides of the spot data, and asking users to report unknown spots (SkyOcean asks on Discord).
- Automatic sharing of spots in chat, and routes or pathfinding between spots.
- Spot data for codes that have no known source yet (`TOPA_2`, `SAPP_2`, `AMET_2`, `AMBE_2`, `JADE_2`).

**Acceptance criteria**
- **AC-CORPSE-01** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-01, REQ-CORPSE-10)
  - Given: the shipped table.
  - When: the validator runs.
  - Then:
    - 29 codes have spots and 5 are listed as "no spot data", together the 34 known codes;
    - there are 129 spots, none out of bounds and no duplicates;
    - all `_C` codes resolve to the shared list.
  - A copy with one unknown code, or one coordinate out of bounds, fails `./gradlew check`.
  - — [A]
- **AC-CORPSE-02** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-01)
  - Given: a sanitised fixture of the user's logged sightings: shaft code plus Skyblocker "Found a … Corpse at" position, 144 sightings over 22 codes, with no player names.
  - When: each sighting is matched against the shipped spots of its code.
  - Then: every sighting lies within 1.0 block (Euclidean) of a spot.
  - — [A]
- **AC-CORPSE-03** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-03, REQ-CORPSE-07)
  - Given: a dev-only simulated mineshaft with code `OPAL_1` in singleplayer, with the feature ON.
  - When: a client gametest captures screenshots.
  - Then: one "Possible corpse #n" label shows per `OPAL_1` spot, including one behind a stone wall. There are no boxes or beams. With the feature OFF, or after leaving the simulated shaft, no label shows within 1 s.
  - — [C]
- **AC-CORPSE-04** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-04)
  - Given: a simulated shaft, run once with no armor stands and once with visible and invisible armor stands placed on, near and away from spots.
  - When: the marker set is computed.
  - Then: count, positions and labels are identical in both runs. Code review confirms that the module reads no entity data.
  - — [A] + [R]
- **AC-CORPSE-05** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-05). Given widget states and toggles, when markers are computed, then:

  | Widget | Hide toggle | Markers |
  |---|---|---|
  | [NOT LOOTED, NOT LOOTED] | ON | shown |
  | [LOOTED, LOOTED] | ON | hidden |
  | [LOOTED, LOOTED] | OFF | shown |
  | widget absent | ON | shown |
  | back to [NOT LOOTED] | ON | shown again |

  — [A]
- **AC-CORPSE-06** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-06)
  - Given: a simulated `OPAL_1` with the player at a fixed position.
  - When: `/ksu corpses` runs.
  - Then:
    - the lines are sorted by distance, with half-up rounding (12.5 → 13);
    - the indices match the marker labels;
    - the special replies match the text in REQ-CORPSE-06;
    - the output is the same with the marker toggle OFF, and with the marker toggle and "Mineshaft entry alert" both OFF (no "Shaft not identified yet" once the code is in the sidebar).
  - — [A]
- **AC-CORPSE-07** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-08)
  - In a fresh config "Possible corpse spots" is OFF and "Hide spots when all corpses are looted" is ON.
  - The card's tooltip text is present in a config-screen screenshot.
  - — [A] + [C]
- **AC-CORPSE-08** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-09)
  - `./gradlew check` fails when the corpse table's notice file is removed.
  - The built jar contains both licence files.
  - THIRD_PARTY_NOTICES names both copyright lines and GrowlingGrizzly.
  - — [A] + [R]
- **AC-CORPSE-09** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-02)
  - Given: a gametest with networking to external hosts blocked.
  - Then: the markers still render. Code review finds no network call in this module.
  - — [C] + [R]
- **AC-CORPSE-10** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* (REQ-CORPSE-03, REQ-CORPSE-06)
  - Given: a real Glacite Mineshaft with the feature ON.
  - When: Skyblocker reports "Found a … Corpse at x, y, z".
  - Then: that position lies within 1.5 blocks of a displayed marker, and `/ksu corpses` lists the same spots.
  - — [E]

**Edge cases**
- **EC-CORPSE-01** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Code without spot data (`TOPA_2`, …): no markers. With the feature ON, one local note per shaft says "No spot data for <code>".
- **EC-CORPSE-02** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* A new code, unknown to the table: treated like EC-CORPSE-01 and logged once per launch.
- **EC-CORPSE-03** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* The shaft code arrives late (up to 15 s): markers appear within 1 s of it.
- **EC-CORPSE-04** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* The widget is missing or disabled: hide-when-looted is unavailable and markers stay.
- **EC-CORPSE-05** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Hypixel changes a layout: markers are wrong until a data update ships. The AC-CORPSE-10 field check detects it. 51 of the 129 spots are not yet confirmed by the user's logs.
- **EC-CORPSE-06** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Crystal shafts (`*_C`) all use the shared 3-spot list. `LITT_L` uses its own 4 spots.
- **EC-CORPSE-07** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* SkyOcean draws its own "Corpse" labels at the same spots: the texts overlap, with no crash and no interference ([D]).
- **EC-CORPSE-08** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Shaders (Iris) on: labels stay readable ([E]).
- **EC-CORPSE-09** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* The data file is missing or corrupt at runtime: the feature is disabled with one log line, `/ksu corpses` replies "Corpse spot data unavailable", and the game continues.
- **EC-CORPSE-10** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* The player loots every corpse while the widget has not yet updated (it lags 1–4 s): markers hide when the widget shows all LOOTED.

**Open questions**
- **Q-CORPSE-01** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Which corpse-spot dataset should ship, and when? → decided D-5 (MIT data with notices, pinned commit, ship now with credit)
- **Q-CORPSE-02** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* How should corpse spot markers look, and should they ship at all? → decided R7
- **Q-CORPSE-03** [dropped R22] *(the maintainer dropped corpse-spot waypoints after G1)* Should `/ksu corpses` work while the marker toggle is OFF? → decided R7

---

### npc-waypoints — Skyblocker-style NPC waypoints
**Origin:** Brief Phase 3 item 4 | **Depends on:** world-markers, npc-mob-data, ui-config (also location, config-store) | **Plan tasks:** T1.3b, T3.4, T3.4b (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** The fixed-position NPC waypoints the player adds in NPC Search should look like Skyblocker's: a white name label plus a beacon beam whose colour can be set per NPC or per category. Several NPCs can then be told apart and found from far away.

**Functional requirements**
- **REQ-NPCWP-01** Scope.
  - Waypoints are drawn for every enabled fixed-position NPC rule whose island matches the current island. A blank island means any island, as today.
  - Each such rule gets exactly one waypoint at its stored coordinates. A rule created from an NPC data entry uses that entry's current coordinates through its sourceId, so corrected coordinates in a data update move the waypoint (REQ-NPCDB-09) [decided R5]. A rule whose sourceId is no longer in the data keeps its stored coordinates.
  - Moving NPC rules get no waypoint; they keep the depth-tested glow [decided D-1].
  - Island matching uses the corrected island table, including separate "Dungeon Hub" and "Catacombs" [decided D-2].

  *(Brief: item 4; Derived: existing NPC Search behaviour, PLAN T1.9)*
- **REQ-NPCWP-02** Label.
  - The rule's label is drawn in white (#FFFFFF), with no drop shadow and no background plate, and always faces the camera.
  - White is the default of the NPC Search setting "White waypoint labels" (default ON). With it OFF, the label and its distance line are drawn in the rule's own `color` field, the 1.0.1 look, not in the resolved beam colour [decided R21]. The setting ships in v1.1.0 on the 1.0.1-style label (REQ-PORT-06), where the distance line follows the label (white while the setting is ON). It stays when the beams arrive; the beam then carries the colour (REQ-NPCWP-04, REQ-NPCWP-05), and with the setting ON the distance line turns yellow (REQ-NPCWP-03). T3.4 updates the toggle tooltip to say so.
  - It is centred horizontally on the block centre (x + 0.5, z + 0.5), 1.5 blocks above the stored y.
  - It keeps a constant on-screen size from 10 blocks out, and true world size when closer.
  - It is visible through terrain, including behind water, glass and other translucent blocks, because it marks a fixed coordinate.

  *(Brief: item 4 "white text labels", "Skyblocker style"; Derived: PLAN §3 P4, T1.3, research Skyblocker (a), read-npc bug 14)*
- **REQ-NPCWP-03** Distance line [decided D-6].
  - An optional second line below the label reads `<d>m` in yellow (Skyblocker style) while "White waypoint labels" is ON. With it OFF it is drawn in the rule's own `color` field, like the label, not in the resolved beam colour [decided R21]. This applies from T3.4 on; in v1.1.0 the distance line follows the label (white while the setting is ON).
  - d is the distance from the **player's** position (not the camera) to the label anchor, rounded half-up to whole metres.
  - Own toggle "Show distance", default ON.
  - Distance is never shown for live entities.

  *(Derived: keeps the existing distance line; research G3 row D, read-npc bug 17)*
- **REQ-NPCWP-04** Beam. A beacon-style beam rises from the waypoint block to the top of the world, in the waypoint's resolved colour:
  - fully opaque (any alpha is ignored);
  - animated like a vanilla beacon beam;
  - depth-tested like a vanilla beam, so terrain in front hides it;
  - wider with horizontal distance beyond 96 blocks so it stays visible (not widened while using a spyglass), as vanilla does.

  *(Brief: item 4 "individually colored beacon beam per waypoint"; Derived: Skyblocker behaviour study)*
- **REQ-NPCWP-05** Colour resolution [decided D-15].
  - The beam colour is resolved in this order:
    1. the rule's own beam colour, if set;
    2. otherwise its category colour, where category = island;
    3. otherwise the global default beam colour (for a blank island or an island without a colour).
  - Every island in the NPC data has an editable colour with a documented default.
  - "Use island colour" clears a rule's override.
  - Documented defaults (T3.4; chosen by Claude to fill a gap in this spec, kept by R25). Every island name of the location table has one; the global default is `0x0AA351`, the mod's long-standing NPC green:

    | Island | Default | Island | Default |
    |---|---|---|---|
    | Hub | `0x55FFFF` | Dwarven Mines | `0x00AAAA` |
    | Private Island | `0x55FF55` | Crystal Hollows | `0xB266FF` |
    | Garden | `0x00AA00` | Glacite Mineshafts | `0xAEEBFF` |
    | The Farming Islands | `0xFFFF55` | Backwater Bayou | `0x3C8DBC` |
    | The Park | `0x2E8B57` | Lotus Atoll | `0xFF88CC` |
    | Moonglade Marsh | `0x6B8E23` | Critter Safari | `0xD2B48C` |
    | Torrhus Canyon | `0xCD853F` | Kuudra | `0xAA0000` |
    | Spider's Den | `0xAA00AA` | Jerry | `0xFFFFFF` |
    | The End | `0xFF55FF` | The Rift | `0xBF40BF` |
    | Crimson Isle | `0xFF5555` | Dark Auction | `0x8B008B` |
    | Gold Mine | `0xFFAA00` | Dungeon Hub | `0xAAAAAA` |
    | Deep Caverns | `0x5555FF` | Catacombs | `0x555555` |

  *(Brief: item 4 "color configurable per NPC/category"; PLAN D-15)*
- **REQ-NPCWP-06** Colour settings UI. The Waypoints category of the new config screen must offer:
  - per-island colour swatches (colour-picker modal and hex input);
  - a per-rule beam colour with a "use island colour" option;
  - the beam toggle and the distance toggle.

  Changes apply to rendered waypoints within 1 s, with no restart or rejoin. *(Brief: item 4 "configurable"; Brief Phase 2; REQ-XC-TOGGLE)*
- **REQ-NPCWP-07** Separate glow colour. Changing island colours or per-rule beam colours never changes the glow colour of moving-NPC rules, and changing a glow colour never changes a beam. *(Derived: PLAN D-15)*
- **REQ-NPCWP-08** Toggles.
  - The existing NPC Search module toggle, default ON.
  - "Show beacon beams", default ON. When OFF, beams are hidden and labels stay.
  - "Show distance" (REQ-NPCWP-03).
  - "White waypoint labels", default ON (REQ-NPCWP-02) [decided R21].
  - The existing per-rule enabled flag.
  - A per-rule beam on/off may be offered.

  *(Brief ground rule 6; REQ-XC-TOGGLE)*
- **REQ-NPCWP-09** Migration [decided R8].
  - A 1.0.1 config must load with every NPC rule field intact: label, enabled, island, fixed, coordinates, sourceId, match mode, pattern, colour.
  - Moving-NPC rules keep `color` as their glow colour.
  - A fixed rule whose `color` differs from the old default `0x0AA351` gets that colour as its beam override.
  - A fixed rule with the old default follows its island colour.

  *(Brief Phase 2 "Migrate existing config values so users don't lose settings"; Derived)*
- **REQ-NPCWP-10** Performance. With 100 enabled waypoints on one island, the mean frame time should rise by at most 1.0 ms against 0 waypoints. It is measured over 600 frames in a client gametest at 854×480, as CPU frame time (the gametest runs one frame per tick, so the GPU never backs up into the frame); GPU cost is judged in the G3 field check. *(Derived: an island can hold about 100 NPC entries; read-npc performance notes)*
- **REQ-NPCWP-11** Licensing. Skyblocker (LGPL-3.0) is a behaviour reference only: the look is reimplemented from scratch and no code is copied. *(Brief ground rule 4; REQ-XC-LICENSE; PLAN §2)*
- **REQ-NPCWP-12** Compliance. Labels, beams and distance lines are drawn only at fixed coordinates from NPC rules or NPC data, never at a live entity's position. *(Brief ground rule 5; PLAN §3 P4)*

**Out of scope**
- Skyblocker's filled box or outline at the waypoint block, tracer lines, and ordered routes.
- Labels, beams or distance for moving NPCs (Trinity, Tomioka, Duncan, Pete, Xalx).
- User-created non-NPC waypoints, editing coordinates, and import or export of Skyblocker waypoint groups.
- Hide-when-near and a maximum render distance (not requested).
- Label colours other than white and the rule's colour. White stays the default (the brief says white text labels); the rule's colour is the opt-out of REQ-NPCWP-02 [decided R21]. Until R21 this line read "Label colours other than white".
- A new NPC "type" category field in the NPC data [decided D-15].

**Acceptance criteria**
- **AC-NPCWP-01** (REQ-NPCWP-05). Given the colour-resolution cases, then:

  | Case | Beam colour |
  |---|---|
  | Per-rule override set | the override |
  | No override, island colour set | the island colour |
  | Blank island | the global default |
  | Island missing from the map | the global default |
  | "Use island colour" chosen after an override | the island colour again |

  — [A]
- **AC-NPCWP-02** (REQ-NPCWP-09)
  - Given: a 1.0.1-shaped config fixture with the user's 5 rules (Trinity, Tomioka, Duncan moving; Udel, Researcher Timmy fixed; all `0x0AA351`) plus a fixed rule coloured `0xFF0000`.
  - When: the config is migrated.
  - Then:
    - every field is unchanged;
    - the red rule's beam is `0xFF0000`;
    - Udel's beam is the Crimson Isle colour;
    - Trinity's glow stays `0x0AA351`.
  - — [A]
- **AC-NPCWP-03** (REQ-NPCWP-02, REQ-NPCWP-04)
  - Given: a client gametest with one fixed waypoint whose rule `color` differs from its island colour.
  - When: screenshots are taken.
  - Then:
    - the label is white with no background, the distance line is yellow, and the beam is in the island colour; with "White waypoint labels" OFF the label and the distance line are in the rule's `color`, not the beam colour, and the beam is unchanged [decided R21];
    - the label stays visible behind stone, glass and water;
    - the part of the beam behind an opaque wall in front of the camera is hidden.
  - — [C]
- **AC-NPCWP-04** (REQ-NPCWP-02)
  - Given: gametest screenshots of the same label at 20 and 100 blocks.
  - Then: the on-screen text height is equal within 1 px. At 5 blocks it is larger.
  - — [C]
- **AC-NPCWP-05** (REQ-NPCWP-08, REQ-NPCWP-03, REQ-NPCWP-02)
  - "Show beacon beams" OFF: the label is drawn, the beam is not.
  - "Show distance" OFF: only one text line.
  - A fresh config has the module, beams, distance and white labels ON.
  - "White waypoint labels" ON gives a white label and a yellow distance line; OFF gives both lines the rule's `color`, also when the resolved beam colour differs. The toggle tooltip says this [decided R21].
  - — [C] + [A]
- **AC-NPCWP-06** (REQ-NPCWP-03)
  - Given: the player at (0, 64, 0) and a waypoint at (10, 64, 0), so the anchor is (10.5, 65.5, 0.5).
  - Then: d = 10.62 is shown as "11m". Moving the camera to third person does not change the value.
  - — [A]
- **AC-NPCWP-07** (REQ-NPCWP-06)
  - Changing an island colour in the config screen while in a world changes the beam within 1 s (gametest).
  - A screenshot of the Waypoints category shows the island swatches and a rule's beam override at GUI scale 2 and in an 854×480 window.
  - — [C]
- **AC-NPCWP-08** (REQ-NPCWP-07)
  - Changing an island colour leaves the glow colours of moving rules unchanged.
  - Changing a glow colour leaves the resolved beam colours unchanged.
  - — [A]
- **AC-NPCWP-09** (REQ-NPCWP-01)
  - A rule for "Dungeon Hub" is active only in mode `dungeon_hub`, not `dungeon`.
  - A rule with a blank island is active on every island.
  - A disabled or moving rule never gets a waypoint.
  - — [A]
- **AC-NPCWP-10** (REQ-NPCWP-10)
  - Given: a gametest with 100 waypoints against 0.
  - Then: the mean frame time over 600 frames is at most baseline + 1.0 ms.
  - — [C]
- **AC-NPCWP-11** (REQ-NPCWP-02, REQ-NPCWP-04)
  - On Hypixel with shaders on and off, the labels and beams of Udel (Crimson Isle) and Researcher Timmy (Lotus Atoll) are visible — [E].
  - A production boot next to Skyblocker waypoints shows no crash and no new ERROR — [D].
- **AC-NPCWP-12** (REQ-NPCWP-11, REQ-NPCWP-12) Review confirms:
  - no Skyblocker-derived code;
  - waypoint positions come only from rule or NPC-data coordinates;
  - no waypoint is created from entity positions.
  - — [R]
- **AC-NPCWP-13** (REQ-NPCWP-01)
  - Given: a fixed rule with sourceId `udel` and stored coordinates A, NPC data in which `udel` now sits at B, a fixed rule without sourceId at C, and a fixed rule whose sourceId is not in the data at D.
  - When: the waypoints are computed.
  - Then: they are drawn at B, C and D.
  - — [A]

**Edge cases**
- **EC-NPCWP-01** Fixed rule with a blank island: drawn on every island at its coordinates, using the global default colour (existing behaviour).
- **EC-NPCWP-02** Island missing from the colour map (a new Hypixel island or unknown mode): uses the global default. An entry is created when the user edits it.
- **EC-NPCWP-03** Two rules at the same coordinates (NPC data near-duplicates): both are drawn, labels may overlap, no crash.
- **EC-NPCWP-04** Player within 1 block of the label (which floats 1.5 blocks above the NPC's block, so standing next to the NPC reads about "2m"): the distance shows "0m" or "1m" and the label sits at its true position.
- **EC-NPCWP-05** Waypoint beyond render distance: the label is still drawn at constant size, and the beam is drawn while its column is in view.
- **EC-NPCWP-06** Hex colour entered with alpha (e.g. `#80FF0000`): the beam is opaque red and the label stays white (with "White waypoint labels" ON).
- **EC-NPCWP-07** Dungeon run vs Dungeon Hub: Dungeon Hub waypoints do not appear inside runs [decided D-2].
- **EC-NPCWP-08** Module turned OFF, or a warp to another island: the old waypoints disappear and the new island's appear within 1 s of the change or location update.
- **EC-NPCWP-09** Config file corrupt: config-store backs it up and loads defaults. No waypoints show until rules are re-added or the backup is restored; there is no crash.
- **EC-NPCWP-10** GUI-scale or window-size change: world labels and beams are unaffected.

**Open questions**
- **Q-NPCWP-01** What is a "category" for beam colours? → decided D-15 (category = island, optional per-rule beam override, glow colour separate)
- **Q-NPCWP-02** How should existing fixed-rule colours migrate? → decided R8
- **Q-NPCWP-03** Should the waypoint distance line default to ON? → decided D-6 (distance line ON)
- **Q-NPCWP-04** Are the documented island colour defaults (the table under REQ-NPCWP-05, chosen by Claude in T3.4 to fill a gap) the ones to ship? → decided R25 (keep them)

---

### bobber-fix — Bobber rubber-banding fix

**Origin:** Brief Phase 3 item 5 (reference: SkyOcean) | **Depends on:** port-26-2; location (needs the "on SkyBlock" signal, see contradictions); config-store + ui-config (toggle) | **Plan tasks:** T3.0f, T3.5 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** On Hypixel SkyBlock your own fishing bobber jitters and teleports. Research found two client-side causes. (1) The client attaches the bobber to Hypixel's bite-timer armor stand and snaps it there every tick, which fights the server's position updates. (2) On lava the client lets the bobber sink while the server keeps it floating. This feature removes both causes so the bobber is drawn where the server has it. It changes only how the bobber is drawn.

**Functional requirements**
- **REQ-BOB-01** While on SkyBlock and with the feature enabled, the local player's bobber must not be attached client-side to Hypixel's bite-timer armor stand. That stand is the one the server reports as the bobber's "hooked entity"; in research it is the armor stand Hypixel spawns right after the hook (entity id = hook id + 1). The bobber must keep its normal floating behaviour, so its drawn position follows the server-sent position and does not snap every tick. *(Brief: Phase 3 item 5; Derived: root cause 1 in research-skyocean §(a))*
- **REQ-BOB-02** While on SkyBlock and with the feature enabled, a bobber on lava must float on the lava surface client-side in the same way it floats on water, with no sink-then-snap-back cycle. *(Brief: Phase 3 item 5 "smooth out … jitter"; Derived: root cause 2, Crimson Isle lava fishing, research-skyocean §(a))*
- **REQ-BOB-03** When the server reports the bobber hooked into any other entity (a mob, a player, or an armor stand that is not the timer stand), vanilla attached rendering and vanilla pull behaviour must be kept. *(Derived: minimal behaviour change; research-skyocean §(a) "only other client use of hookedIn")*
- **REQ-BOB-04** The fix must change presentation only. It must not send, cancel, delay or alter any packet. It must not change casting, reeling, bite or catch timing, or input handling. The server stays authoritative for the bobber. Ignoring the hooked-entity link (REQ-BOB-01) is presentation of already-received data, not packet modification: the entity-data packet is applied unchanged, and only the client-side attach step for the own bobber is skipped (REQ-XC-RULES-02). *(Brief: Ground rule 5; Derived: PLAN §3 P5 and rules row "Bobber rubber-band fix 🟡→🟢"; REQ-XC-RULES-02)*
- **REQ-BOB-05** Outside SkyBlock (other Hypixel games, other servers, singleplayer), bobber behaviour must be exactly vanilla whatever the toggle says. After joining, while the location is still unknown, the client must behave as if it is not on SkyBlock. "On SkyBlock" must be true in every SkyBlock area, including areas the island table does not map (such as a newly added island). *(Derived: gating risk in research-hsm §5 and gap-G2 §7; location)*
- **REQ-BOB-06** The fix must apply to the local player's own bobber only. Other players' bobbers stay vanilla. **[decided R9]** *(Derived: PLAN T3.5)*
- **REQ-BOB-07** The feature has its own toggle "Bobber Fix" in the Fishing category, default ON **[decided D-6]**. It carries the AMBER tooltip required by P6. A change applies without a restart, at the latest from the next cast. *(Brief: Ground rule 6, Phase 3 "each behind its own toggle"; REQ-XC-TOGGLE)*
- **REQ-BOB-08** If the game code the fix needs cannot be hooked (because another mod conflicts or a game update moved it), the game must still start. The feature must then show itself as unavailable on its config card and write one warning to the log. *(Derived: PLAN AD-9, optional mixins)*

**Out of scope**
- SkyOcean's other fishing extras: hiding other players' bobbers, bobber alive-time text, hook-text scaling, and lava-to-water texture replacement. None was requested.
- Any bite alert, auto-reel, catch-timing cue, or change to how the hook talks to the server (automation or packet manipulation: Ground rule 5, P5).
- Re-tuning vanilla's smoothing of server position corrections beyond removing the two causes.
- Copying SkyOcean code or its All-Rights-Reserved assets (such as its lang strings). The behaviour is reimplemented and gets a courtesy credit only (REQ-XC-LICENSE, PLAN §2).

**Acceptance criteria**
- **AC-BOB-01** (REQ-BOB-01, REQ-BOB-03, REQ-BOB-05, REQ-BOB-06) Given the guard decision, when it is evaluated over the table {on SkyBlock yes/no} × {toggle on/off} × {owner local/other} × {linked entity = armor stand with id hook+1 / armor stand with another id / zombie / none}, then the link is ignored only in the row (yes, on, local, timer stand) and kept in every other row. — [A]
- **AC-BOB-02** (REQ-BOB-02, REQ-BOB-05) Given the fluid decision, when the bobber is in lava, then lava counts as floatable only if on SkyBlock, toggle on and own bobber are all true. Water is always floatable and other fluids never are. — [A]
- **AC-BOB-03** (REQ-BOB-05) Given `runClient` in singleplayer with the toggle ON, when the player casts into water and then into lava, then the bobber floats in water and sinks in lava exactly as it does without the mod. — [B]
- **AC-BOB-04** (REQ-BOB-01, REQ-BOB-04) Given the Prism test copy on Hypixel (water in the Hub or Backwater Bayou), when the user fishes for 2 minutes with the fix ON, then the floating bobber shows no visible jumps or teleports. With the fix OFF, the jitter is visible (control). — [E] **[decided R17]**
- **AC-BOB-05** (REQ-BOB-02) Given lava fishing on the Crimson Isle, when the fix is ON, then the bobber stays on the lava surface for at least 30 s without sinking and snapping back. With the fix OFF, the sink-and-snap cycle is visible. — [E] **[decided R17]**
- **AC-BOB-06** (REQ-BOB-04) Given the feature's source, when it is reviewed, then it references no packet class and no send or cancel call, and it touches only the client-side bobber tick and the client's reaction to bobber data that was already applied. The received entity data is stored unchanged; only the own bobber's attach step is skipped (REQ-XC-RULES-02). — [R]
- **AC-BOB-07** (REQ-BOB-08) Given the production jar and the user's mod set, when the game boots with the mixin debug export, then the bobber hooks are applied and no new ERROR lines appear. Given a test build with a deliberately missing target, the game still boots and the card shows "unavailable". — [D]
- **AC-BOB-08** (REQ-BOB-07) Given a cast bobber, when the toggle is switched, then the new value is saved and takes effect for the next cast without a restart. — [B]

**Edge cases**
- **EC-BOB-01** Hypixel changes the spawn order, so the timer stand is no longer hook id + 1 → the fix silently does nothing (vanilla jitter comes back) and nothing crashes. The next field check (AC-BOB-04) catches it.
- **EC-BOB-02** The bobber really hooks a sea creature, mob or player → vanilla attach and pull (REQ-BOB-03).
- **EC-BOB-03** Quick recast (old hook removed, new hook and new timer stand spawned) → each hook is judged on its own, and no state carries over between hooks.
- **EC-BOB-04** The toggle changes while the bobber is already attached to the timer stand → no exception. The change takes effect at the next data update or the next cast.
- **EC-BOB-05** Warp, server change or disconnect while fishing → vanilla removes the hook, and nothing is kept.
- **EC-BOB-06** Joining before Hypixel's location packet arrives → treated as not on SkyBlock, so vanilla behaviour until the location is known (REQ-BOB-05).
- **EC-BOB-07** Another mod with the same fix (such as SkyOcean) is installed → both apply, the visible result is the same, and nothing crashes.
- **EC-BOB-08** Lava fishing outside SkyBlock (singleplayer Nether) → vanilla sinking (REQ-BOB-05).
- **EC-BOB-09** Another player's bobber nearby → unchanged **[decided R9]**.

**Open questions**
- **Q-BOB-01** Should the fix cover only your own bobber (PLAN T3.5) or every bobber on SkyBlock (SkyOcean's behaviour)? → decided R9 (recommended: own bobber only)

---

---

### fishing-hotspot — Fishing hotspot highlight + "hotspot gone" warning

**Origin:** Brief Phase 3 item 6 (reference: SkyOcean) | **Depends on:** world-markers (depth-tested ring); location (on-SkyBlock signal, island, world/server-change events); config-store + ui-config (toggles); no game-state dependency, because "fishing in" is bobber-based, not catch-based **[decided R9]** | **Plan tasks:** T3.0f, T3.6, T3.6b (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Show every active fishing hotspot as a ring on the water or lava surface in the colour of its buff. Warn the player when the hotspot they are fishing in disappears, by title, sound and chat, each configurable. Community data (2026-07) lists hotspots in the Hub (3), Spider's Den (1), The Park (1), Backwater Bayou (2), Jerry's Workshop (2), Lotus Atoll (2), Torrhus Canyon (2) and the Crimson Isle (3, in lava). They appear only after the player has talked to Hattie.

**Terms used below**
- *Hotspot stand:* an armor stand whose displayed name, with formatting stripped and trimmed, is exactly `HOTSPOT`.
- *Centre:* that stand's X/Z.
- *Surface Y:* the top of the first water or lava block within 3 blocks below the stand.
- *Area:* horizontal distance to the centre ≤ radius + 0.5 blocks, and |y − surface Y| < 3.
- *Grace period:* 10 ticks (0.5 s).
- *Re-sent stand:* a new hotspot stand whose centre is within 4.0 blocks horizontally of a removed stand's centre and which appears within the grace period.
- *Fished window:* 30 s.
- *Warning distance:* 40 blocks (3D, player to centre).

The numbers are the research defaults (research-skyocean §(b), research-domain-fishing §1b); they are re-checked at the field check.

**Functional requirements**
- **REQ-HOT-01** On SkyBlock, the mod must recognise an active hotspot from each hotspot stand the client has. Only stands whose name tag vanilla actually displays (custom name visible) count. Such a name-tag hologram counts as visible information under the REQ-XC-RULES-04 clarification, even though the stand's body is invisible **[decided R2]**. A stand whose name vanilla does not display is ignored. Detection must work on every SkyBlock island, including islands added after release; the island only affects the radius limits in REQ-HOT-04. *(Brief: Phase 3 item 6 "highlight active hotspots"; Derived: research-domain-fishing §1a–1b; P2/P3 as clarified in REQ-XC-RULES-04/05, R2)*
- **REQ-HOT-02** The mod must take the buff type from the companion name tag at the same X/Z (within 0.5 blocks horizontally and up to 1 block below). Types: Sea Creature Chance, Fishing Speed, Double Hook Chance, Treasure Chance, Trophy Fish Chance, Shard ("Chance of <X> Shard"); anything else is Unknown. If the buff line arrives or changes later, the type updates. The text patterns live in one place, and an unrecognised buff text is logged once. *(Derived: research-skyocean §(b)1; PLAN AD-6 "regex in one place")*
- **REQ-HOT-03** The surface Y must be the liquid surface under the stand (REQ-HOT terms). If no liquid is found yet (for example, the chunk is not loaded), the lookup is retried on each scan, and the stand's foot Y is used in the meantime. *(Derived: research-skyocean §(b)1)*
- **REQ-HOT-04** The radius must be estimated read-only from the server's ring particles the client receives: pink dust in water, smoke on the Crimson Isle. The estimate is the most frequent horizontal particle-to-centre distance, rounded to 0.5 blocks. It is capped per island: about 5 blocks on the Crimson Isle and Torrhus Canyon, about 4 on Jerry's Workshop and Lotus Atoll, about 3 elsewhere (UNVERIFIED in game). Until particles have been seen, and whenever none arrive, a per-island fallback radius is used (initially the cap, tuned at the field check). Particle packets must never be cancelled, delayed or modified. *(Brief: item 6; Derived: research-skyocean §(b)2; PLAN T3.6 "per-island fallback radius"; P5)*
- **REQ-HOT-05** For each active hotspot, the mod must draw a ring outline at the surface Y in the buff type's colour, plus an optional semi-transparent filled disc. Default colours: Sea Creature Chance dark aqua, Fishing Speed aqua, Double Hook Chance blue, Treasure and Trophy gold, Shard yellow, Unknown white. The ring must be depth-tested: hidden behind opaque blocks, with no see-through option. This applies to every marker derived from a hotspot hologram (REQ-XC-RULES-04) **[decided R2]**. Opacity should be configurable, and per-type colours may be. *(Brief: item 6; Derived: PLAN §3 "Hotspot highlight 🟢 depth-tested ring", P1/P4; SkyOcean defaults)*
- **REQ-HOT-06** A hotspot stays active until its stand is removed and the grace period ends. If a re-sent stand appears, the same hotspot continues (radius estimate and fished-in time kept; no warning). On a world change, server change or disconnect, every hotspot is cleared at once. *(Derived: research-domain-fishing §1b step 3, "Hypixel sometimes re-sends the stand")*
- **REQ-HOT-07** "Fishing in" a hotspot: at least every 10 ticks, while the local player's own bobber exists and is inside a hotspot's area, that hotspot's fished-in time is set to now. So the player is fishing in H when the own bobber was inside H's area at any time in the last 30 s (fished window). Catches are not used, and other players' bobbers never count. **[decided R9]** *(Brief: item 6 "the hotspot the player is fishing in"; Derived: research-domain-fishing §1b)*
- **REQ-HOT-08** A "hotspot gone" warning must fire exactly once for hotspot H, at the end of the grace period after H's stand is removed, if and only if **all** of these hold: (1) the hotspot module and the warning toggle are ON and the player is on SkyBlock; (2) no re-sent stand appeared for H during the grace period; (3) no world change, server change or disconnect happened between the removal and the end of the grace period; (4) at the moment of removal, now − H's fished-in time ≤ 30 s; (5) at the moment of removal, the player was within 40 blocks of H's centre. Conditions (4) and (5) replace the line-of-sight gate of REQ-XC-RULES-05 for this warning (the exception named there); no line-of-sight check is made **[decided R2]**. *(Brief: item 6 "warn … when the hotspot the player is fishing in disappears"; Derived: PLAN T3.6 and §3 "only if you fished it in the last 30 s and are within 40 blocks"; research-skyocean §(b)5)*
- **REQ-HOT-09** The warning must **not** fire when:
  - the player never fished in H, or last fished in it more than 30 s ago;
  - the player was more than 40 blocks away at the moment of removal. This covers leaving the stand's tracking range by walking, flying or riding away;
  - a world change happened: warp, `/hub`, island or server transfer, entering a mineshaft or dungeon, limbo, or disconnect;
  - Hypixel re-sent the stand at the same spot;
  - any toggle in (1) is OFF, or the player is not on SkyBlock;
  - H already produced a warning.

  A hotspot that moves (removed, then a new stand more than 4 blocks away) counts as gone. *(Brief: item 6; Derived: research-domain-fishing §1b "the client cannot tell a despawn from leaving tracking range")*
- **REQ-HOT-10** Warning channels, each with its own toggle:
  - **Title:** "Hotspot gone!" with the buff type as subtitle, shown for at least 2 s.
  - **Sound:** one local sound; volume configurable; the sound may be chosen from a short list of vanilla sounds.
  - **Chat:** a local client-side message such as "[KSU] Sea Creature Chance hotspot is gone."
  - **Toast:** an optional extra channel showing the same text as a toast.

  Defaults: title, sound and chat ON **[decided D-6]**; these three brief channels stay the defaults. The toast channel defaults OFF **[decided D-8; decided R12]**. *(Brief: item 6 "warn (title/sound/chat, configurable)"; Derived: D-6, D-8, R12)*
- **REQ-HOT-11** The feature must not send anything to the server: no chat, party chat, command or click-to-share, and no input. Its messages are local only. *(Brief: Ground rule 5; Derived: PLAN §3 P5, "Flagged, not implemented: automatic party-chat announcements"; REQ-XC-RULES)*
- **REQ-HOT-12** Toggles in the Fishing category:
  - module "Fishing Hotspots": default ON **[decided D-6]**
  - "Highlight ring": ON **[decided D-6]**
  - "Filled area": ON
  - "Hotspot gone warning": ON **[decided D-6]**
  - the four channel toggles from REQ-HOT-10 (title, sound, chat ON; toast OFF **[decided R12]**)

  All of them apply live without a restart. Feature names avoid the words banned by P7. *(Brief: Ground rule 6; REQ-XC-TOGGLE)*
- **REQ-HOT-13** If the particle observation cannot be hooked, the game must still start. Rings then use the fallback radius, and the card shows "radius estimation unavailable". If the entity-based detection cannot run, the whole feature shows as unavailable and logs one warning. *(Derived: PLAN AD-9)*

**Out of scope**
- Announcing hotspots to party or public chat, automatically or by a click-to-share button (P5; research recommends leaving it out of v1).
- Hiding the server's hotspot particles. Packet cancelling is forbidden (P5, PLAN §3), and client-side hiding was not requested.
- See-through rings, sky-high columns, distance labels or waypoints to far-away hotspots, and a hotspot-radar "guesser" (not requested; P1/P4).
- Lifetime countdowns or "moves in N s" predictions (the lifetime is UNVERIFIED, about 2 or 4 minutes depending on the source).
- Wormholes, sea-creature or catch tracking, and a hotspot-status HUD element (not requested).
- Copying SkyOcean or GanKura code. Behaviour is reimplemented (REQ-XC-LICENSE).

**Acceptance criteria**
- **AC-HOT-01** (REQ-HOT-01, REQ-HOT-02) Given a table of name-tag texts, when they are parsed, then: `§d§lHOTSPOT` and ` HOTSPOT ` are hotspot stands; `HOTSPOTS` and `Hotspot Radar` are not; a `HOTSPOT` stand whose custom name is not displayed is ignored; each buff line (`+5α Sea Creature Chance`, `+15☂ Fishing Speed`, `Chance of Lava Shard`, …) maps to its type; an unknown text maps to Unknown and is logged only once. — [A]
- **AC-HOT-02** (REQ-HOT-04) Given particle distance samples with about 10 % outliers, when the radius is estimated, then the result is the mode rounded to 0.5. A value above the island cap is capped. With no samples, the fallback radius is returned. — [A]
- **AC-HOT-03** (REQ-HOT-06, REQ-HOT-08, REQ-HOT-09) Given the warning state machine with a fake clock, when each firing condition is true, then exactly one warning is raised. When any single condition is false, none is. Boundaries: fished 30.0 s ago fires, 30.05 s does not; distance 40.0 fires, 40.1 does not; a re-sent stand at 3.9 blocks inside the grace period means no warning and the same hotspot continues; a new stand at 4.1 blocks means a warning plus a new hotspot; a world change during the grace period means no warning and all hotspots cleared; a second removal of the same hotspot does nothing. — [A]
- **AC-HOT-04** (REQ-HOT-07) Given bobber positions relative to a hotspot with radius 3.0, when they are sampled, then horizontal distances 3.5 (dy 2.9) count as fishing in, while 3.6, or dy 3.0, do not. With no bobber, the fished-in time never updates. — [A]
- **AC-HOT-05** (REQ-HOT-10, REQ-HOT-11) Given a mocked output adapter, when a warning fires with title OFF and toast at its default OFF, then only sound and chat are called. With toast ON, the toast is called as well. The chat output goes to the local chat display only; no server-send API is called. — [A]
- **AC-HOT-06** (REQ-HOT-05) Given a singleplayer gametest with the SkyBlock flag forced by the dev debug command and two named armor stands (`HOTSPOT`, `+5α Sea Creature Chance`) above water, when it renders, then a screenshot shows the ring at the water surface in dark aqua. A second screenshot with a stone wall between the camera and the ring shows no ring. — [C]
- **AC-HOT-07** (REQ-HOT-04, REQ-HOT-11) Given the feature's source, when it is reviewed, then the particle observer never cancels or modifies a packet, and there is no chat, command or packet-send call. — [R]
- **AC-HOT-08** (REQ-HOT-01, REQ-HOT-04, REQ-HOT-05) Given the Prism test copy, when the user stands at a hotspot in the Hub or Backwater Bayou and one on the Crimson Isle (lava), then each ring sits on the surface and its radius is within ±0.5 blocks of the visible particle ring, with shaders on and off. — [E] **[decided R17]**
- **AC-HOT-09** (REQ-HOT-08, REQ-HOT-09, REQ-HOT-10) Given the user fishes in a hotspot until it moves (about 4 min), then exactly one warning appears on the configured channels. Given the user stops fishing and flies more than 40 blocks away, then no warning appears. Given the user warps away while fishing, then no warning appears. The user also records the distance at which the stand disappears when walking away; it must be above 40 blocks, otherwise the warning distance is lowered. — [E] **[decided R17]**
- **AC-HOT-10** (REQ-HOT-13) Given the production jar with the user's mod set (SkyHanni, Skyblocker and others), when it boots, then the particle observer is applied with no new ERROR lines. If another mod cancels the particle packets, rings still show with the fallback radius. — [D]
- **AC-HOT-11** (REQ-HOT-12) Given the config screen, when each hotspot toggle is switched, then the ring, fill or warning channel changes within one second without a restart, and the value is saved. — [B]

**Edge cases**
- **EC-HOT-01** The player walks out of the stand's tracking range within 30 s of fishing → the stand is removed. If the player is more than 40 blocks away, no warning. If Hypixel's tracking range is under 40 blocks, false warnings are possible; AC-HOT-09 measures this and the threshold is adjusted.
- **EC-HOT-02** World change during the grace period → no warning, and all hotspots are cleared (REQ-HOT-06).
- **EC-HOT-03** Hypixel removes and re-adds the stand at the same spot → no warning, and the ring continues (at most a 0.5 s gap). If the re-add comes after the grace period, one false warning is accepted, and the grace period is tuned at the field check.
- **EC-HOT-04** The hotspot moves → the old one is gone (warning if REQ-HOT-08 holds) and the new one is detected as a fresh hotspot.
- **EC-HOT-05** Two hotspots within 4 blocks → a re-sent stand is matched to the nearest removed hotspot; the other stays separate.
- **EC-HOT-06** Another mod (SkyOcean "hide particles") cancels the particle packets, or no particles arrive → fallback radius, and the ring still shows.
- **EC-HOT-07** Similar-coloured dust from a Hermit Crab pet or other sources → ignored by the mode estimate and the island cap.
- **EC-HOT-08** The buff stand arrives after the HOTSPOT stand, or its text changes → type Unknown (white) until it arrives, then updated.
- **EC-HOT-09** No bobber cast → rings are shown, the fished-in time never updates, and no warnings fire.
- **EC-HOT-10** A menu is open when the hotspot vanishes → the warning still fires: the sound plays and chat is logged; the title shows on the HUD.
- **EC-HOT-11** The warning toggle is turned OFF during the grace period → no warning.
- **EC-HOT-12** An island with hotspots that the bundled table does not know (new content) → generic cap and fallback radius; detection still works (REQ-HOT-01).
- **EC-HOT-13** The player has not unlocked hotspots (has not talked to Hattie) → no stands exist, so nothing is shown.
- **EC-HOT-14** Bobber fix OFF, so the client bobber sits on the timer stand or sinks in lava → the fished-in check still uses the bobber's position with the ±3 Y tolerance, so tracking keeps working.
- **EC-HOT-15** Two fished hotspots vanish within 1 s → one warning each; the second title replaces the first, and chat shows both.
- **EC-HOT-16** Disconnect or kick to lobby → everything is cleared, and no warning fires.
- **EC-HOT-17** An armor stand named `HOTSPOT` whose name tag vanilla does not display → not a hotspot: no ring, no warning (REQ-HOT-01, REQ-XC-RULES-04) **[decided R2]**.

**Open questions**
- **Q-HOT-01** What counts as "fishing in" a hotspot: your bobber inside its area in the last 30 s (recommended), a catch made inside it (SkyOcean), or either? → decided R9 (recommended: own bobber inside the area at any time in the last 30 s)
- **Q-HOT-02** Should the "hotspot gone" warning default ON (PLAN D-6) or OFF (SkyOcean's precedent), and which channels should be ON by default? → decided D-6 (warning ON; title, sound and chat ON)

---

---

### rare-drop-odds — Rare Drop Odds (brief title: "Rare Drop Gambling Overlay")
**Origin:** Brief Phase 3 item 7; ground rules 4, 5, 6 | **Depends on:** game-state, data-registry, hud, location (added: island/mode context is needed, see contradictions), config-store, ui-config | **Plan tasks:** T3.9a, T3.9g, T3.9h, T3.9b, T3.9c, T3.9d, T3.9e, T3.9f (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Some rare-drop moments are already visible to the player: a Scatha fight, looting a Frozen Corpse, a dungeon reward chest, a slayer boss. At those moments, show how likely the drop is or was. Numbers come from public drop data and the player's own counters, so the player gets odds context without any automation. v1 is a numeric odds display [decided D-3]. Cosmetic reveal animations, as in SkyOcean, are not built in v1: SkyCase (MIT, 26.2) already offers them, and REQ-ODDS-25/26 are kept only for a later follow-up.

**v1 case list (approved with all five cases, decided D-3)**

| # | Case | When it shows | What it shows | Data source and licence | Data status |
|---|---|---|---|---|---|
| 1 | Scatha | HUD panel while in Crystal Hollows; one chat line on your own Scatha pet drop | Base pet chance per kill: any 0.40% (1 in 250), split Rare 0.24 / Epic 0.12 / Legendary 0.04%. The same chances scaled by your Magic Find + Pet Luck. Scatha kills this session, kills since your last Scatha pet, chance of at least 1 pet by now, expected kills | Pet chances are facts from the community wikis (pages you supply, decided D-28), corroborated by Scatha-Pro's published constants (fact only; ARR code is not copied) | Wiki-only (decided D-16, D-28): `verified: false`, shown with "?", until you supply the pages and Scatha-Pro's constants are confirmed to agree |
| 2 | Croesus / dungeon reward chests | Tooltip lines when you hover an item in a dungeon reward chest screen (end-of-run chest, or a per-run chest menu opened from Croesus) | **Relative rarity plus a base run estimate:** the item's RNG-meter score, its rarity rank among that floor's RNG-meter items, and "≈ 1 in N runs (P%, base, no bonuses)" with N = score / 300 (decided D-3, R10 = b). No wiki per-run % | NEU-REPO `constants/rngscore.json`, MIT (c) 2020 Moulberry (decided D-5) | MIT data; score/300 agrees with the wiki within about 1% on M5/M6/M7/F5/F6 and about 2.5% on F7 |
| 3 | Vanguard corpse | HUD panel in a Glacite Mineshaft whose Frozen Corpses widget lists a Vanguard; one chat line after a "VANGUARD CORPSE LOOT!" block | The rarest items (default 3) with per-roll and per-corpse chance; Vanguard corpses looted this session | Per-roll weights from SkyOcean `src/repo/vanguard.jsonc` (MIT), bundled with a notice [decided R6], which match the wiki (total weight 3,515). Frostbitten Dye is a separate 0.01% per roll (wiki); SkyOcean's "Assumed odds" weight is replaced by it and not used. Rolls 5–8 | Weights have 2 origins; the dye has 1 origin (wiki-only, `verified: false`, shown with "?") |
| 4 | Lapis / Umber / Tungsten corpses | Same as case 3, for those corpse types | Same as case 3 | Per-roll chances: wiki only, since no MIT source was found. Rolls: Lapis 3–6, Umber/Tungsten 4–7 | Wiki-only (decided D-16, D-28): `verified: false`, shown with "?"; the numbers come from the pages you supply, and they keep the "?" while no non-wiki origin agrees |
| 5 | Slayer RNG meter | HUD panel while a slayer quest is active and for 30 s after an RNG-meter chat line; one chat line on a slayer rare drop that is in the table | Selected meter item, stored/required meter XP (%), base chance at the highest tier, meter-boosted chance, bosses until guaranteed | NEU `rngscore.json` slayer section (MIT, decided D-5), plus the public formula base% = 500 × 100 / required XP (250 for the Riftstalker) | Derived; about 1.2% from the wiki for Warden Heart |

Later candidates, not in v1: generic pet drops (your logs show 171 Slug and 10 Rat drops), Kuudra paid chests, Diana / Minos Inquisitor, Baby Yeti, Lord Jawbus / Thunder, Golden Goblin, trophy fish, dragon loot, the Frozen Corpse RNG meter, and a Kismet-reroll advisor.

**Functional requirements**

*General*
- **REQ-ODDS-01** The mod must provide a display-only "Rare Drop Odds" feature with one master toggle in the config category "Odds & Trackers". The toggle defaults to OFF. *(Brief: Phase 3 item 7 and "each behind its own toggle"; Derived: ground rule 6, D-6 default OFF because of HUD clutter)* [decided D-3, D-6]
- **REQ-ODDS-02** No user-visible feature name, description or chat output may contain a word banned by policy P7. The feature is labelled "Rare Drop Odds". The brief title's word "Gambling" may appear only as a hidden search keyword, so that searching for the brief's name finds the card. *(Derived: PLAN P7; SkyBlock Rules L41/L67, see G3)* [decided D-3]
- **REQ-ODDS-03** v1 must implement exactly the five cases of the case list above. Each case must have its own toggle, default ON, effective only while the master toggle is ON. Turning a case off must remove all of its HUD lines, tooltip lines and chat lines. *(Brief: item 7 "Scatha, Vanguard corpses, Croesus chests, and other logically similar cases … for my approval"; ground rule 6)* [decided D-3]
- **REQ-ODDS-04** The feature must only *read* these inputs:
  - system-chat and action-bar messages, including ones that other mods cancel or rewrite
  - tab-list widgets and the sidebar
  - the location
  - the title and contents of containers the player opened
  - entities the player attacked

  It must never send chat messages or commands. It must never click, open or page a menu. It must never cancel, delay or alter a packet or chat message. It must never show or infer loot the server has not displayed: no prediction of unopened chests, rerolls or corpse contents, only published per-attempt probabilities. Every chat line it writes is a local client message. *(Brief: ground rule 5; Derived: PLAN P5 and §3 row "Rare-drop odds overlay")*
- **REQ-ODDS-05** The Scatha, corpse and slayer panels must each be a HUD element registered in the HUD editor: movable, scalable and with preview data. Each panel is drawn only while its context applies:
  - Scatha: in Crystal Hollows
  - corpses: in a Glacite Mineshaft with a matching corpse type
  - slayer: while a slayer quest is active, or within 30 s of an RNG-meter line

  The Croesus case adds tooltip lines inside the chest screen instead of a HUD element, because that moment happens inside a container screen. *(Brief: item 7 "an overlay"; Brief Phase 2 HUD editor; Derived: PLAN T3.9b–f, AD-5)*
- **REQ-ODDS-06** Every displayed probability must use the format "1 in N (P%)":
  - N is rounded to an integer and has thousands separators.
  - P has 2 significant digits, down to a minimum of 0.0001%.
  - Each value names its basis: "per kill", "per roll", "per corpse", "base" or "with your MF/PL"; Croesus rank lines say "relative"; the Croesus run estimate says "base, no bonuses" and is prefixed "≈" (REQ-ODDS-19).
  - A value from a table row with `verified: false` carries a visible "?" marker. Its tooltip names the source. *(Derived: ground rule 4 attribution; the research found conflicting wiki numbers, see G1 and the domain research)*
- **REQ-ODDS-07** Chat summary lines have their own toggle, default ON. There is at most **one** line per rare-drop event:
  - corpse loot: only for looted items whose per-corpse chance is at or below the "rare threshold" (default 2%, range 0.01–10%)
  - Scatha: always on your own Scatha pet drop
  - slayer: on a rare drop that is in the slayer table *(Brief: item 7 "odds context for rare-drop moments"; Derived: PLAN T3.9b–f "plus a chat line", ground rule 6)*
- **REQ-ODDS-08** Math contract. Everything is computed in code; no derived percentage is copied from a source.
  - Chance of at least 1 success in n attempts = 1 − (1 − p)^n. Expected attempts = 1/p. Luck percentile of a drop on attempt k = 1 − (1 − p)^k.
  - Pet drop p = base × (1 + (MF + PetLuck)/100). Any other drop p = base × (1 + MF/100), but only if base < 5%; otherwise p = base. MF is capped at 900.
  - Per-corpse chance = the mean over the type's roll counts n (uniform from min to max) of 1 − (1 − r)^n, where r = item weight / total weight per roll [decided D-3].
  - RNG-meter boost = 1 + min(2 × stored/required, 2). When stored ≥ required, the panel shows "guaranteed" instead of a chance.
  - Slayer base chance at the highest tier = multiplier × 100 / required meter XP, in percent. The multiplier is 500, or 250 for the Riftstalker.
  - Bosses until guaranteed = ceil((required − stored) / last observed per-boss meter gain). *(Derived: research-domain §2a/§2c/§2e, G1 §3/§5; the formulas are public facts)*
- **REQ-ODDS-09** Data contract. Drop data must be bundled as data-registry tables, with no runtime fetch. Each entry holds:
  - the case id, the SkyBlock item id and the display name used for matching
  - either the per-roll weight (together with the table's total weight) or the per-attempt base chance
  - the roll count min/max, where the case rolls more than once
  - `sourceRefs`, `license`, `verified`, and the SkyBlock `gameVersion` the entry was last checked against

  The tables must not contain any wiki per-corpse percentage column, and no wiki prose. *(Brief: ground rule 4; Derived: AD-7, T3.9a, G1's finding that the wiki per-corpse columns assume 4.5 rolls)*
- **REQ-ODDS-10** Sources and licensing:
  - Every number cites at least one source.
  - The MIT tables ship with notices in THIRD_PARTY_NOTICES: NEU rngscore [decided D-5] and the SkyOcean `src/repo/vanguard.jsonc` weights [decided R6].
  - Nothing else is copied from SkyOcean (code, art, sounds, post-effects or lang strings), and nothing from SkyCase pool files, from Scatha-Pro (ARR), or from Skyblocker/SkyHanni (LGPL).
  - Wiki-only numbers (Scatha, Lapis/Umber/Tungsten, Frostbitten Dye) follow the wiki policy: you supply the pages (decided D-16 (a), D-28 (i)). They stay `verified: false` and carry the "?" marker unless a second, non-wiki origin agrees.
  - SkyOcean's "Assumed odds" Frostbitten Dye weight is never used; the bundled Vanguard table replaces it with the wiki per-roll chance [decided R6]. *(Brief: ground rule 4; Derived: PLAN §2, D-5, D-16, D-28)* [decided D-5, D-16, D-28]
- **REQ-ODDS-11** An item, corpse type or floor that is not in the tables is shown without a number, never with a guessed one, and is logged once per name per session. A missing or invalid table disables only its own case and shows a one-line notice on the feature's config card; the game must not crash. *(Derived: AD-7; T3.0c acceptance "the game loads with a table missing")*
- **REQ-ODDS-12** These counters are stored per SkyBlock profile and survive restarts: Scatha kills since the last Scatha pet, Scatha kills counted, and corpses looted per type. Session counters reset on a profile switch, a game restart or a manual "Reset odds counters" button. Counters only cover events seen while the mod runs, and the UI says "counted by K8bas since <date>". *(Derived: "kills since last pet" is only meaningful per profile; PLAN T3.0d)*

*Case 1 — Scatha*
- **REQ-ODDS-13** The Scatha panel must show:
  - the base pet chance per kill: any, Rare, Epic and Legendary
  - the same chances adjusted for MF + Pet Luck
  - Scatha kills this session
  - Scatha kills since the last Scatha pet
  - the chance of at least 1 pet by now, at the adjusted any-pet chance
  - the expected number of kills *(Brief: item 7 "Scatha"; Derived: G1 §6 option A)*
- **REQ-ODDS-14** Magic Find and Pet Luck are read from the "Stats" tab widget when it shows them. Otherwise the panel uses manual config values (non-negative integers, default 0). The panel shows which source is in use ("tab" or "manual"). *(Derived: research-domain §2a; G1 open question on MF/PL)*
- **REQ-ODDS-15** A Scatha kill is counted when a Scatha (not a Worm) that the player attacked dies within 30 s of the player's last hit on it. Scathas the player never hit do not count, and Worms never count. Identification must not depend on maximum health alone, because the Derpy double-HP perk changes it. Detection is UNVERIFIED until the field check. *(Derived: needed for "kills since last pet"; G1 §6; behaviour of Skyblocker's Zealot counter)*
- **REQ-ODDS-16** When the player gets a Scatha PET DROP message in system chat (their own, not guild, party or private chat), the mod posts one local line. The line gives the rarity, the kills since the last Scatha pet, the adjusted per-kill chance and the luck percentile. Then "since last pet" resets to 0. *(Brief: item 7; Derived: G1 §6)*

*Case 2 — Croesus / dungeon reward chests*
- **REQ-ODDS-17** In dungeon reward chest screens (end-of-run chests and per-run chest menus opened from Croesus), each displayed item that is on that floor's RNG-meter list gets one tooltip line: "RNG-meter item · rarity rank k of n on <floor> · score S (relative)". Rank 1 is the highest score, which is the rarest item. Items not on the list get no line. Table entries flagged as placeholders (e.g. dyes) are excluded. The only per-run value is the base estimate of REQ-ODDS-19; no wiki per-run percentage is shown. *(Brief: item 7 "Croesus chests"; Derived: PLAN T3.9 case 2)* [decided D-3, R10]
- **REQ-ODDS-18** The floor (F1–F7, M1–M7) is taken from the screen title or the current dungeon context; if it cannot be determined, no lines are shown. Lines are recomputed whenever the displayed contents change, for example after a Kismet reroll. They never refer to items that are not currently displayed. *(Brief: ground rule 5; Derived: PLAN §3 "never predicts unopened contents")*
- **REQ-ODDS-19** Each RNG-meter item line of REQ-ODDS-17 gets a second tooltip line "≈ 1 in N runs (P%, base, no bonuses)", where N = RNG-meter score / 300 rounded to a whole number and P = 100 / N with two significant digits. It is labelled approximate ("≈") and follows the REQ-ODDS-06 format otherwise. *(Brief: item 7 "drop chances / odds context"; Derived: research-domain §2d — within ~1 % of the wiki on M5/M6/M7/F5/F6, ~2.5 % on F7)* [decided R10 = b]

*Cases 3 and 4 — Frozen Corpses (Vanguard; Lapis/Umber/Tungsten)*
- **REQ-ODDS-20** The corpse panel shows, in a Glacite Mineshaft, every corpse type listed by the Frozen Corpses widget (Vanguard = case 3; Lapis, Umber, Tungsten = case 4). For each type it lists the N rarest items (default 3, range 1–10; ties ordered by name) with their per-roll and per-corpse chance, plus the corpses of that type looted this session. If the widget is unavailable, the panel shows every enabled type with a "(widget off)" note. It never scans entities. *(Brief: item 7 "Vanguard corpses"; the other types approved as case 4, decided D-3; Derived: G1 §7)*
- **REQ-ODDS-21** On a "<TYPE> CORPSE LOOT!" block, the mod identifies the corpse type and the looted items and ignores lines that other mods insert inside the block. Following REQ-ODDS-07, it then posts one line listing every looted item whose per-corpse chance is at or below the threshold, e.g. "Vanguard: Shattered Locket — 1 in 109 per corpse (computed)". Frostbitten Dye uses its own per-roll chance. *(Brief: item 7; Derived: G1 §4 safety flags)*
- **REQ-ODDS-22** Per-corpse values assume no extra-roll perk (Gifts from the Departed), and their tooltip says so. The per-roll value is always shown beside the per-corpse value. *(Derived: G1 §3's 4.5-roll finding conflicts with research-domain §2c)* [decided D-3]

*Case 5 — Slayer RNG meter*
- **REQ-ODDS-23** The slayer panel must show:
  - the selected RNG-meter item, taken from the "You set your … RNG Meter to drop …" line or from the "<slayer> RNG Meter" menu the player opens
  - stored and required meter XP, and the percentage
  - the base chance at the highest tier and the meter-boosted chance
  - bosses until guaranteed, hidden until one per-boss gain has been observed

  Odds are labelled "highest tier"; lower-tier odds are not shown. *(Brief: item 7 "other logically similar cases"; Derived: PLAN T3.9 case 5)* [decided D-3]
- **REQ-ODDS-24** On a slayer rare-drop chat message (RARE / VERY RARE / CRAZY RARE DROP) for an item in the slayer table, the mod posts one line with that item's base chance at the highest tier. *(Brief: item 7; Derived: PLAN T3.9 case 5)* [decided D-3]

*Reveal animations — not in v1 (decided D-3 = A); kept only for a later follow-up*
- **REQ-ODDS-25** *[not in v1, decided D-3 = A; applies only if a later follow-up adds reveal animations]* A code-drawn reveal animation (a CS:GO-style item strip) for Obsidian/Bedrock dungeon chests (also via Croesus), Vanguard corpse loot and Scatha pet drops:
  - each context has its own toggle, default OFF
  - it runs as a non-blocking HUD overlay that ESC skips, with a duration of 1–10 s (default 5)
  - the strip always lands on the item the server actually gave
  - chat lines held back during the animation are replayed afterwards, never dropped
  - only vanilla sounds and original code-drawn visuals are used, with no SkyOcean sprites, post-effect JSON or lang strings *(Brief: item 7 reference "SkyOcean"; Derived: G1 §4)*
- **REQ-ODDS-26** *[not in v1, decided D-3 = A; applies only if a later follow-up adds reveal animations with odds]* The final card of a reveal shows the item's odds in the REQ-ODDS-06 format. *(Derived: G1 §5 hybrid)*

**Out of scope**
- Reveal animations and slot machines (decided D-3 = A; at most a later follow-up). SkyCase (MIT, 26.2) already offers them.
- Any SkyOcean texture, post-effect, sound or lang string (All Rights Reserved), SkyCase pool JSONs (wiki-derived licence chain), and Scatha-Pro code (ARR).
- Predicting unopened chest, reroll or corpse contents, and a Kismet-reroll advisor.
- Wiki per-run % for Croesus, and any bonus-adjusted estimate (score, boss luck, Kismet); only the base estimate of REQ-ODDS-19 is shown (decided D-3, R10 = b). Chest profit or price display.
- Automatic party or guild announcements and automatic commands (P5).
- Scatha spawn or cooldown alerts, titles and sounds (that is SkyOcean's announcer, not requested).
- Lower-tier slayer odds, the Frozen Corpse RNG meter, and the later candidates listed above.
- Runtime download of drop data (AD-7).
- Computing Magic Find from gear, and bestiary family Magic Find (UNVERIFIED).

**Acceptance criteria**
- **AC-ODDS-01** (REQ-ODDS-01, 02, 03) Given a fresh config, when the option model is built, then "Rare Drop Odds" sits in "Odds & Trackers" with the master toggle OFF and one toggle per case (five), each ON. No option title, description or chat template contains a word from the P7 list, and a search for the brief title's word "Gambling" finds the card through its keyword. Verified by [A], plus a config-screen screenshot [C].
- **AC-ODDS-02** (REQ-ODDS-04) Code review finds no chat or command send, container click, screen open, packet or message cancellation, or message modification in the module [R]. The module's chat listeners return "allow" for 100% of the fixture messages [A].
- **AC-ODDS-03** (REQ-ODDS-04, 21) Given your 26.2 mod set (Skyblocker, SkyHanni), when the exact jar boots and a recorded corpse-loot block is injected that another mod cancels, then the module still parses it exactly once and the log shows no new ERROR [D].
- **AC-ODDS-04** (REQ-ODDS-05) Given the HUD editor, then the Scatha, corpse and slayer panels show preview data and can be moved and scaled (0.5–3.0). After Save and a restart they are at the same anchor-relative position. Outside their context they are not drawn. Verified by [C].
- **AC-ODDS-05** (REQ-ODDS-06) p = 1/1389.4 renders as "1 in 1,389 (0.072%)" and p = 0.004 as "1 in 250 (0.40%)". A row with `verified: false` renders the "?" marker [A], and its tooltip names the source [C].
- **AC-ODDS-06** (REQ-ODDS-07) A Vanguard loot block with two items at or below 2% gives exactly one chat line naming both. Raising the threshold to 0.5% leaves only the item below 0.5%. With the chat toggle off, no line appears. Verified by [A].
- **AC-ODDS-07** (REQ-ODDS-08) Math fixtures, verified by [A]:
  - p = 0.004: n = 250 gives 63.3% ±0.1, and n = 500 gives 86.5% ±0.1.
  - Legendary 0.0004 with MF 100 + PL 50 gives 0.001.
  - MF 1,000 is treated as 900.
  - A 6% base drop is unchanged by MF.
  - Vanguard per corpse with uniform 5–8 rolls: Caged Wisp (10/3515) gives 1.83% ±0.01 and Shattered Locket (5/3515) gives 0.92% ±0.01.
  - Meter at s/r = 0.5 gives ×2; at s/r ≥ 1 it gives "guaranteed".
  - Warden Heart (required 3,674,250) gives base 1 in 7,349.
- **AC-ODDS-08** (REQ-ODDS-09, 10) `./gradlew check` fails for an odds entry with no `sourceRefs`, with no `license`, with a per-corpse percentage field, or with SkyOcean's assumed dye weight, and for an MIT table with no notice. It passes for the shipped tables. Verified by [A].
- **AC-ODDS-09** (REQ-ODDS-11) An unknown item gives no number and exactly one log line per session. With one table deleted from the test resources, that case is disabled with a notice, the other cases still work, and `runClient` boots. Verified by [A] and [B].
- **AC-ODDS-10** (REQ-ODDS-12) Given profile A with 312 kills since the last pet, switching to profile B shows B's counter, and switching back shows 312. The value survives a restart. The reset button zeroes only the session counters. Verified by [A].
- **AC-ODDS-11** (REQ-ODDS-13, 14) Given Stats-widget fixture lines "Magic Find: 100" and "Pet Luck: 50" (UNVERIFIED format until the G1 capture), the panel shows any-pet 1 in 100 and Legendary 1 in 1,000 with source "tab". With no widget and manual MF 0 / PL 0, it shows 1 in 250 with source "manual". Verified by [A], plus a preview screenshot [C].
- **AC-ODDS-12** (REQ-ODDS-15, 16) Verified by [A] on events and fixtures, a gametest with simulated entities and injected chat [C], and a field check in Crystal Hollows [E]:
  - When an attacked Scatha dies, the kill count goes +1.
  - A Scatha the player never hit, and an attacked Worm, give +0.
  - A system-chat Scatha PET DROP posts one line and resets "since last pet".
  - The guild-chat line "Guild > X: PET DROP! RARE Scatha" changes nothing.
- **AC-ODDS-13** (REQ-ODDS-17, 18) Given a captured, sanitised M7 Bedrock chest screen containing a Dark Claymore, its tooltip shows "score 416,820" and "rank 1 of n on M7". A non-meter item gets no line, and a placeholder dye gets no line. A screen with an unknown floor gets no lines. Replacing the contents (reroll fixture) recomputes the lines. Verified by [A], a screenshot [C] and a Croesus field check [E].
- **AC-ODDS-14** (REQ-ODDS-19) Given the M7 Dark Claymore with score 416,820, its tooltip shows "≈ 1 in 1,389 runs (0.072%, base, no bonuses)" below the rank line; an item not on the RNG-meter list shows neither line — [A]
- **AC-ODDS-15** (REQ-ODDS-20) Given a widget fixture listing Vanguard and Umber, the panel lists for Vanguard: Frostbitten Dye (1 in 1,539 per corpse), Shattered Locket (1 in 109), then Caged Wisp (1 in 55), each with its per-roll value. Umber follows. With no widget, the panel shows all enabled types with "(widget off)". Verified by [A] and [C].
- **AC-ODDS-16** (REQ-ODDS-21, 22) The sanitised fixture set of all corpse-loot blocks from your logs (92 Lapis, 38 Tungsten, 28 Umber, 3 Vanguard) parses with 0 failures. "[SkyHanni]" and "[Skyblocker]" lines inside a block are ignored. Each logged block has at least the type's minimum roll count of item lines, after merged stacks. Every per-corpse value carries the "no extra-roll perk" note. Verified by [A]; a mineshaft field check [E].
- **AC-ODDS-17** (REQ-ODDS-23, 24) Given the line "set RNG Meter to drop Warden Heart" and stored XP 1,837,125, the panel shows 50%, base 1 in 7,349 and boosted 1 in 3,674. After two stored-XP lines 1,500 apart, it shows bosses until guaranteed = ceil((3,674,250 − s)/1,500). A rare-drop line for a table item gives one chat line. Verified by [A]; a slayer field check [E].
- **AC-ODDS-18** (REQ-ODDS-25, 26; not in v1, decided D-3 = A; applies only to a later follow-up) The animation never blocks movement input. ESC ends it. The strip lands on the received item. Every chat line held during the animation appears afterwards, in order. Verified by [A] on the chat hold/replay logic and [C] screenshots.

**Edge cases**
- **EC-ODDS-01** SkyHanni or Skyblocker cancels or compacts a loot block or PET DROP line → it is still read through the cancelled-message path, and our line appears once, never twice when both paths fire.
- **EC-ODDS-02** Other mods insert profit lines inside a loot block → those lines are ignored, not treated as items.
- **EC-ODDS-03** A disconnect or world change interrupts a loot block, so the closing border never arrives → the block is dropped after 2 s and no partial line is posted.
- **EC-ODDS-04** A merged stack such as "Blue Goblin Egg x5" → counts as one looted item; the number of rolls is never inferred from stacks.
- **EC-ODDS-05** Hypixel renames or adds a loot item, or a new corpse type appears in the widget → it is shown without odds and logged once.
- **EC-ODDS-06** Another player's Scatha pet drop arrives in guild or party chat → ignored.
- **EC-ODDS-07** The PET DROP format differs because another mod added the rarity word or a "(+N)" suffix (UNVERIFIED raw format) → both the raw and the modified forms match.
- **EC-ODDS-08** A Scatha the player hit is killed by another player → it counts as one attempt. A Scatha the player never hit → not counted.
- **EC-ODDS-09** The profile is unknown (Profile widget missing) when a kill happens → the kill is counted in memory and persisted once the profile becomes known in the same session; it is discarded if the profile never becomes known.
- **EC-ODDS-10** The Stats widget shows MF above 900 → the math uses 900 and the panel shows "(cap 900)". If the widget disappears mid-session → the manual value is used and the source label changes.
- **EC-ODDS-11** Croesus titles with a page prefix ("(2/3) Croesus"), "Master Catacombs - Floor VII", and "Bedrock Chest" vs "Bedrock" → all recognised. Same-titled containers outside the Dungeon Hub or a dungeon → ignored (location gate).
- **EC-ODDS-12** A placeholder RNG-meter score (dyes) → excluded from the ranks.
- **EC-ODDS-13** The selected slayer meter item changes → the panel shows the new item and learns the per-boss gain again. A Tier I/II boss gives a gain of 0 → ignored. A full meter → "guaranteed on the next qualifying boss".
- **EC-ODDS-14** A data table is missing or corrupted → only that case is disabled, with a notice.
- **EC-ODDS-15** The GUI scale or window size changes, or F1 is pressed → panels stay anchored or hide; counters keep counting.
- **EC-ODDS-16** One corpse yields two rare items → one combined chat line.

**Open questions**
- **Q-ODDS-01** What should the "Rare Drop Gambling Overlay" be: A numeric odds, B reveal animations, or C both? And may it be named "Rare Drop Odds"? → decided D-3 (A, numeric odds, named "Rare Drop Odds"; reveal animations left to SkyCase or a later follow-up)
- **Q-ODDS-02** Which v1 cases do you approve? → decided D-3 (all five cases of the T3.9 list)
- **Q-ODDS-03** For Croesus, should the tooltip show relative rarity only, or also a number? → decided D-3 (relative rarity, no wiki per-run %) and R10 = b (plus the base "≈ 1 in N runs" line)
- **Q-ODDS-04** Which per-corpse odds model should the corpse cases use? → decided D-3 (per-roll plus per-corpse computed in code from the roll range, the PLAN T3.9a approach)
- **Q-ODDS-05** Where do the wiki-only numbers come from (Scatha, Lapis/Umber/Tungsten, Frostbitten Dye)? → decided D-16, D-28 (you supply the pages; `verified: false` with "?" unless a non-wiki origin agrees)
- **Q-ODDS-06** May the MIT drop data be bundled? NEU rngscore → decided D-5 (MIT with notices); SkyOcean `vanguard.jsonc` → decided R6
- **Q-ODDS-07** Are the defaults right: module OFF, cases ON inside it, chat lines ON, threshold 2%? → decided D-6 (yes)

---

### drill-fix — Drill re-equip fix (Hot-Shirtless-Men equivalent)

**Origin:** Brief Phase 3 item 8 "Drill fuel fix" (reference: Hot-Shirtless-Men) | **Depends on:** port-26-2; location (on-SkyBlock signal); config-store + ui-config (toggle) | **Plan tasks:** T3.0f, T3.7 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Name:** the feature and its config card are called "Drill re-equip fix" everywhere (card, toggle, README, CHANGELOG). The brief title "Drill fuel fix" is used only when quoting the brief.

**Purpose:** The brief asks for "the equivalent fix for drill fuel display" from Hot-Shirtless-Men (HSM). Research shows HSM has no fuel display. It has two fixes for Hypixel refreshing the drill item on every fuel change:
- **(A)** It stops the hand's down-and-up re-equip animation. This is display-only and allowed.
- **(B)** It keeps block-break progress by stopping the vanilla abort/restart mining packets. This alters client–server communication, so it is excluded (Hypixel also fixed the reset on the server on 2026-09-17).

A third reading, **(C)**, a numeric fuel HUD, is not in HSM. This module specifies (A) only, for drills only **[decided D-4]**. (B) is excluded and (C) is not built.

**Functional requirements**
- **REQ-DRILL-01** While on SkyBlock and with the feature enabled, when the stack shown in the first-person main hand or off hand is replaced by a refreshed copy of the same drill, the hand must switch to the new stack without lowering and raising. A refreshed copy has the same vanilla item, the same set of component types, the same SkyBlock item id and the same non-empty SkyBlock unique id, and differs only in lore, custom data (such as fuel) or damage. **[decided D-4]** *(Brief: Phase 3 item 8; Derived: research-hsm §2–3, gap-G2 §3 (A))*
- **REQ-DRILL-02** In every other case the vanilla animation must play. That includes swapping to a different drill (another unique id), to another item, between items without a unique id, and when a component type is added or removed. *(Derived: avoids HSM's loose matching, research-hsm §8; minimal change)*
- **REQ-DRILL-03** The feature must be display-only. It must not change block-breaking progress, the item the game logic believes is being used to mine, or any packet. In particular it must not keep break progress across item refreshes (HSM's fix (B)). *(Brief: Ground rule 5; Derived: PLAN §3 rows "Drill (B) 🔴 excluded", P5; gap-G2 §6, allowed_mods L39; REQ-XC-RULES)*
- **REQ-DRILL-04** It may only add "skip the animation" decisions, never force the animation where vanilla or another mod (Skyblocker, NoFrills, Alpaka) would skip it. When it is disabled, the result is exactly what vanilla and the other mods decide. *(Derived: gap-G2 §7, mixin interplay)*
- **REQ-DRILL-05** Outside SkyBlock, and while the location is unknown, behaviour is vanilla. "On SkyBlock" must also hold in unmapped SkyBlock areas, such as a newly added island. *(Derived: gap-G2 risk "SkyBlock gating"; location)*
- **REQ-DRILL-06** The feature has its own toggle "Drill re-equip fix" in the Mining category, default ON **[decided D-4, D-6]**. Its tooltip says that Skyblocker and NoFrills offer the same effect, and that turning this one off does not turn theirs off. *(Brief: Ground rule 6; Derived: gap-G2 §7 tooltip; REQ-XC-TOGGLE)*
- **REQ-DRILL-07** If the hook cannot be applied, the game still starts, the "Drill re-equip fix" card shows "unavailable", and one warning is logged. *(Derived: PLAN AD-9)*
- **REQ-DRILL-08** Item scope: "drill" means a SkyBlock item whose custom data carries drill fuel. Drills only: Pickonimbus and other SkyBlock items with a unique id are not covered **[decided D-4]**. *(Derived: PLAN T3.7 "same SkyBlock drill", D-4; research-hsm open question on scope)*
- **REQ-DRILL-09** *(not built: D-4 (c) not chosen)* No "Drill Fuel" HUD element is built in v1 **[decided D-4]**. The design that was proposed (`Fuel: <current> / <max> (<pct>%)` from the fuel data and the lore line `Fuel: X/Y`; research-hsm §10 (C)) is the starting point if the user asks for it later as a new request. *(Brief: item 8 "fuel display"; Derived: D-4)*
- **REQ-DRILL-10** *(not built: D-4 (c) not chosen)* No fuel-lore parser is built in v1 **[decided D-4]**. A later request must accept formatting codes, thousands separators and the suffixes k and m (research-hsm §10 "breaks with naive x/y parsing"). *(Derived: D-4)*
- **REQ-DRILL-11** *(not built: D-4 (c) not chosen)* No drill HUD element is registered with the HUD editor in v1 **[decided D-4]**. *(Derived: D-4; hud module)*
- **REQ-DRILL-12** *(not built: D-4 (c) not chosen)* No low-fuel warning is built in v1 **[decided D-4]**. *(Derived: D-4; gap-G2 §7 (C) HUD sketch)*

**Out of scope**
- **(B) Keeping mining progress across item refreshes.** Excluded with no opt-in: it works only by suppressing the abort/restart mining packets (allowed_mods L39, P5), and Hypixel fixed the reset on the server on 2026-09-17.
- **(C) Fuel display.** No Drill Fuel HUD element, no fuel-lore parser and no low-fuel warning in v1 **[decided D-4]** (REQ-DRILL-09..12).
- A fuel durability bar on the item itself **[decided D-4]**. Skyblocker already provides one.
- The re-equip fix for items other than drills, such as Pickonimbus or every SkyBlock item with a unique id **[decided D-4]**.
- HSM's other features: the bow fix, the quiver fix (fakes inventory state; disallowed), the attack-cooldown fix (obsolete in 26.2) and the `/hsm` commands.
- Changing other mods' equivalent options, auto-refuel, refuel reminders and drill-lore compaction.
- Copying HSM (MIT), Skyblocker (LGPL) or NoFrills (GPL) code. The behaviour is reimplemented, with a courtesy credit to Rekteiru and RoseGold's Mobx Drill (REQ-XC-LICENSE, PLAN §2).

**Acceptance criteria**
- **AC-DRILL-01** (REQ-DRILL-01, REQ-DRILL-02, REQ-DRILL-04, REQ-DRILL-05) Given the comparison and gate as a pure function over (vanilla item, component-type set, SkyBlock id, uuid, on SkyBlock, toggle, original result), when evaluated, then:
  - same everything with only lore or fuel changed → skip;
  - different uuid, empty uuid on both, different id, or an added component type → original;
  - original true → true (never forced false);
  - toggle off or off SkyBlock → original. — [A]
- **AC-DRILL-02** (REQ-DRILL-03) Given the feature's source, when it is reviewed (and checked by a grep in CI), then it does not reference the block-breaking or game-mode code or any packet class. Its only hook is the first-person hand item-swap decision. — [R]
- **AC-DRILL-03** (REQ-DRILL-01, REQ-DRILL-02) Given the Prism test copy with Skyblocker's "Cancel Component Update Animation" and NoFrills' "No Equip Animation" both OFF, when the user mines 50 blocks with a drill in the Dwarven Mines and the Drill re-equip fix is ON, then no hand dip appears when the `Fuel:` line changes. With the Drill re-equip fix OFF, the dip is visible (control). Swapping between two drills still dips. — [E] **[decided R17]**
- **AC-DRILL-04** (REQ-DRILL-04, REQ-DRILL-07) Given the production jar with Skyblocker, NoFrills, Alpaka and Debugify, when it boots with the mixin export, then our hook is applied and chained, with no new ERROR lines. — [D]
- **AC-DRILL-05** (REQ-DRILL-05) Given `runClient` in singleplayer with the toggle ON, when the held pickaxe's lore is changed by command, then the vanilla dip plays. — [B]
- **AC-DRILL-06** (REQ-DRILL-09..12) *(not built, D-4)* Given the feature's source, when it is reviewed, then it contains no fuel-lore parser, no Drill Fuel HUD element and no low-fuel warning, and the HUD editor lists no drill element. — [R]
- **AC-DRILL-07** (REQ-DRILL-09, REQ-DRILL-11) *(not built, D-4)* Withdrawn; covered by AC-DRILL-06.
- **AC-DRILL-08** (REQ-DRILL-12) *(not built, D-4)* Withdrawn; covered by AC-DRILL-06.

**Edge cases**
- **EC-DRILL-01** Two drills of the same type with different uuids are swapped → vanilla dip (REQ-DRILL-02).
- **EC-DRILL-02** A drill in the off hand gets refreshed → the same rule applies to the off hand.
- **EC-DRILL-03** Skyblocker or NoFrills already suppress the dip → no visible change. Turning the Drill re-equip fix off does not bring the dip back while they are on (tooltip, REQ-DRILL-06).
- **EC-DRILL-04** Hypixel stops resending the lore on fuel ticks (possible after the 2026-09-17 server fix) → the feature is inert and harmless.
- **EC-DRILL-05** Hypixel renames the custom-data keys (id/uuid/drill fuel) → the comparison fails, and the result is vanilla behaviour with no crash.
- **EC-DRILL-06** Joining before the location is known → vanilla until SkyBlock is confirmed.
- **EC-DRILL-07** A Pickonimbus or other uuid item refreshes → vanilla dip (drills only, REQ-DRILL-08) **[decided D-4]**.
- **EC-DRILL-08** *(not built, D-4)* The user asks for a fuel number or bar → not in v1; Skyblocker's fuel bar is the existing option, and a fuel HUD is a new request (REQ-DRILL-09).
- **EC-DRILL-09** The user reports that mining progress still resets (Hypixel's fix says "sometimes") → not addressed by this mod; (B) stays excluded, and the user is pointed to a Hypixel bug report.

**Open questions**
- **Q-DRILL-01** Which drill symptom do you mean? (a) the drill dips down and up in your hand on each fuel update; (b) the block crack or mining progress restarts; (c) the fuel number or bar is wrong or missing; (d) "just HSM on 26.2". → decided D-4 ((a) only: hand re-equip fix for drills; (b) excluded; (c) not built)
- **Q-DRILL-02** Which items should the animation fix cover: drills only, drills plus other refreshing mining tools such as Pickonimbus, or every SkyBlock item with a unique id? → decided D-4 (drills only)
- **Q-DRILL-03** Only if you pick (c): which display do you want — a numeric HUD element, a fuel bar on the item, a low-fuel warning (with what threshold), or a combination? → decided D-4 ((c) not built, so no fuel display)

---

### updater — Auto-updater on GitHub Releases

**Origin:** Brief Phase 4 (both bullets); Brief ground rules 5 and 6 | **Depends on:** port-26-2, config-store, ui-config | **Plan tasks:** T1.4a, T1.4b, T1.4, T4.1, T4.2, T4.3, T4.3b, T4.4, T4.4b, T4.5, T7.2b; checkpoint G4 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Tell the player when the project's GitHub Releases has a newer K8bas build for their Minecraft version. Install it safely on the next restart, but only after an explicit click. This replaces the current Modrinth updater, which gets HTTP 404, installs without asking (both flags default `true`) and cannot swap the running jar on Windows.

**Functional requirements**

*Review and interim builds*
- **REQ-UPD-01** Until the Phase 4 updater lands, no build made from `update/26.2` may download or install a jar automatically. This includes the early release v1.1.0 [decided D-13]. Interim builds and v1.1.0 handle updates as follows [decided R4]:
  - The Modrinth updater is removed (REQ-UPD-03), and `autoUpdateDownloadEnabled` is dropped (REQ-UPD-19).
  - The GitHub check from T4.1 ships early as **notify-only**. It follows REQ-UPD-03 to REQ-UPD-07, REQ-UPD-09 and REQ-UPD-20, and reads the Minecraft version at runtime instead of the hard-coded `26.1.2`.
  - Its only notice is the once-per-session local chat line of REQ-UPD-10, with [Changelog] and [Open release page]. There is no toast yet, no *Install*, and nothing is downloaded.

  *(Brief: Phase 4 "never replace the jar without user confirmation"; Derived: audit U2, both flags default true; U4, hard-coded MC version; PLAN T1.4, T4.1)*
- **REQ-UPD-02** The Phase 4 report must include the review of the current updater that the brief asks for:
  - how it checks, downloads and verifies updates
  - every security and reliability finding (audit U1–U20), each with a disposition: fixed by a named REQ-UPD-nn, not applicable to the new design, or accepted with a reason

  *(Brief: Phase 4 bullet 1; REQ-XC-REPORT)*

*Source and check scheduling*
- **REQ-UPD-03** The only update source is the GitHub Releases of `Kesuhi/K8basSkyblockUtility`, read through the public REST API without authentication.
  - The mod never sends an `Authorization` header and never ships a token.
  - Every API request carries a `User-Agent` with the mod id and running version, `Accept: application/vnd.github+json`, and a pinned `X-GitHub-Api-Version`.
  - One request (the release list) covers both channels. Drafts are always ignored.
  - The Modrinth code path and its hosts are removed.
  - The production build cannot be pointed at another source. A source override for tests may exist only in the development environment or in test builds.

  *(Brief: Phase 4 "GitHub API: latest release → compare version → download the jar asset"; Derived: AD-8, research §4: `/releases/latest` cannot serve pre-releases and cannot skip a newest release that lacks this MC version's jar)*
- **REQ-UPD-04** Scheduling:
  - The automatic check runs off the render thread after the client has started, never during mod init, and only when the "Check for updates" toggle is ON. The toggle defaults to ON in notify mode [decided D-6].
  - Persisted limits:
    - after a 200 or 304 response, at least 6 h pass before the next automatic request
    - at most 4 automatic requests reach `api.github.com` in any rolling 24 h
    - a manual check (*Check now* or `/ksu update check`) runs at most once per 60 s
    - at most one check or download is in flight at any time
  - With the toggle OFF the updater makes no automatic network request. Manual checks still work.

  *(Derived: REQ-XC-TOGGLE; the unauthenticated GitHub limit is 60 req/h per IP, shared with other software, and a 304 still counts (measured, research §3.7); audit U9)*
- **REQ-UPD-05** Version comparison:
  - Strip one leading `v` from the tag, parse it strictly as SemVer, and ignore build metadata.
  - A release is a candidate only if its version is strictly greater than the running version. Equal versions and downgrades are never offered.
  - A tag that does not parse is skipped and logged once.
  - If the running version is not valid SemVer, the updater offers no install; it only shows the newest version as information.

  *(Brief: Phase 4 "compare version"; Derived: audit U14, where `v1.1.0` parsed as a string always looked newer)*
- **REQ-UPD-06** Asset selection follows the release contract in REQ-REL-07:
  - The asset `name` matches the anchored pattern, and its version part equals the tag version.
  - Its MC part equals the running Minecraft version or is a dotted prefix of it (`+26.2` serves 26.2.1). An exact match beats a prefix match.
  - The asset `state` is `uploaded`.
  - Every other asset (sources jar, sidecar, anything else) is never installed.
  - Every file name the updater uses is the asset `name`, never one derived from the download URL (which encodes `+` as `%2B`).

  The candidate offered is the highest-version release in the active channel that has a matching asset. *(Brief: Phase 4 "download the jar asset", "missing assets gracefully"; Derived: research §4, the bug SkyHanni fixed in PR #5618)*
- **REQ-UPD-07** Pre-releases:
  - A release counts as a pre-release if GitHub flags it `prerelease` **or** its version has a SemVer pre-release part.
  - The setting `updateChannel` is STABLE or BETA, or absent while the user has made no choice.
    - STABLE never offers or mentions pre-releases.
    - BETA considers both, and the highest SemVer wins (a stable `1.2.0` beats `1.2.0-beta.3`).
  - The default is STABLE, or BETA when the running version is itself a pre-release. An explicit choice by the user always wins.

  *(Brief: Phase 4 "pre-releases gracefully")*

*Exact outcomes*
- **REQ-UPD-08** The updater must produce exactly the outcomes in this matrix. "Automatic" is the startup or periodic check; "manual" is *Check now*, `/ksu update check` and the settings card. *(Brief: Phase 4 "Handle rate limits, missing assets, and pre-releases gracefully"; Derived: research §4, §7)*

| Situation | Automatic check | Manual check / settings card | Install offered |
|---|---|---|---|
| Newer matching release, verifiable, install-eligible | toast, plus one chat line per session (REQ-UPD-10) | candidate with an *Install* button | yes, after confirmation |
| Newest release has no jar for this MC; an older release that is still newer than the running one has a jar | offers the older one; the skip is logged once per tag | same, plus the note "vX has no build for Minecraft &lt;mc&gt;" | yes (the older one) |
| A newer release exists, but no newer release has a jar for this MC | nothing shown; logged once per tag | "vX is out but has no build for Minecraft &lt;mc&gt;" + *Open release page* | no |
| No usable SHA-256 for the candidate (digest null or not `sha256:`, and no valid sidecar) | toast/chat "update available, install manually" | *Open release page* only, with the reason | no (notify-only) |
| Digest and sidecar both present and disagree | nothing shown; WARN logged | "release checksum conflict" + *Open release page* | no |
| Candidate is a pre-release, channel STABLE | not seen | not seen | — |
| Candidate is a pre-release, channel BETA | like a stable candidate, labelled "beta" | same | yes |
| Candidate equals `skippedVersion` | no toast, no chat | shown, marked "skipped" | yes, from the card or the command |
| HTTP 304 | cached release list is reused | same | per the cached list |
| Rate-limited (403/429) | nothing shown; back-off per REQ-UPD-09 | "GitHub rate limit reached, try again after HH:mm" (local time) | — |
| Offline, DNS failure, timeout, 5xx | nothing shown; one WARN line without a stack trace; back-off | "Couldn't reach GitHub" | — |
| HTTP 404 (repo renamed or private) | nothing shown; back-off 24 h | "Release source unavailable" | — |
| Not install-eligible (REQ-UPD-16) | toast/chat "update available", with the reason | *Open release page* only, with the reason | no (notify-only) |
| Up to date | nothing | "You're up to date (v&lt;running&gt;)" | — |

- **REQ-UPD-09** Back-off and errors:
  - **Waits:**
    - 403 or 429 with `retry-after`: wait that many seconds.
    - Otherwise, with `x-ratelimit-remaining: 0`: wait until `x-ratelimit-reset`.
    - Otherwise (secondary limit): wait 60 s, doubling on each consecutive occurrence, capped at 1 h.
    - 5xx, or no response at all: wait 1 min, doubling, capped at 1 h.
    - 404: wait 24 h.
    - Every computed wait is clamped to [60 s, 24 h].
  - **Redirects:** a 301 is followed only to `api.github.com`; any other target counts as an error.
  - **Clock:** a stored "last check" time in the future counts as due, so a wrong system clock can neither disable checks nor cause a flood of them.
  - **Manual checks** respect rate-limit waits but not the 6 h interval.
  - **Timeouts:** connect 10 s, API request 20 s, download 120 s.
  - **Limits and parsing:**
    - The API response body is capped at 2 MiB.
    - A malformed release entry is skipped without aborting the check. A malformed body counts as an error.

  *(Brief: Phase 4 "rate limits"; Derived: audit U8, U13, U15; GitHub rate-limit docs)*

*Notification, confirmation, declining*
- **REQ-UPD-10** When a candidate is found, the updater shows:
  - a toast on whatever screen is open
  - once per game session, on the first world join, a **local** chat line with clickable [Changelog] [Update] [Skip this version] [Open release page]
  - in the General → Updates card: running version, latest known version, last check time, channel, and the buttons *Check now*, *Install*, *Open release page* and *Cancel pending update*
  - commands: `/ksu update [show | check | install <version> | skip <version> | cancel]`

  The updater never opens a screen or takes focus on its own, and never sends anything to the server. A skipped version produces no toast and no chat line. *(Derived: audit U10, today's message is dropped while no world is loaded; REQ-XC-RULES policy P5)*
- **REQ-UPD-11** Confirmation:
  - **No download before consent.** Not one byte of a candidate jar is downloaded until the user either clicks **Download & install on restart** on the confirmation screen, or runs `/ksu update install <version>` with a token equal to the current candidate. A stale chat click for any other version is refused and shows the current candidate.
  - **What the screen shows:**
    - running version → new version, and the channel
    - asset name and size
    - the first 12 hex characters of the expected SHA-256
    - the release notes as scrollable plain text: `§` codes and Markdown syntax removed, capped at 4,000 characters
  - **Scope of a confirmation.** It covers only that version. A newer release found later needs its own confirmation.
  - **Release page link.** *Open release page* opens `html_url` only if it starts with `https://github.com/Kesuhi/K8basSkyblockUtility/releases/`; otherwise it opens the repo's releases page. It always goes through the vanilla link-confirmation screen.

  *(Brief: Phase 4 "never replace the jar without user confirmation")*
- **REQ-UPD-12** What happens when the user declines:
  - **Later**, Esc, closing the screen or ignoring the chat line: nothing is downloaded. The next game session may notify again.
  - **Skip this version**, which needs a second click to confirm: the version is stored as `skippedVersion`. Automatic notifications stay silent for it, a higher version notifies again, and the card and manual check still show it.
  - **Cancel pending update** (after staging): the install is disarmed, the staged files are deleted, and the running jar is untouched. It does not count as a skip.

  *(Brief: Phase 4 "never replace the jar without user confirmation"; Derived: research §7)*

*Download, verification, install* — REQ-UPD-13 to REQ-UPD-18 are [decided D-14]
- **REQ-UPD-13** Download:
  - HTTPS on every hop.
  - Redirects are followed manually: at most 5, and only to `github.com` or `*.githubusercontent.com`.
  - The final status must be 200.
  - `Content-Length` (if present) and the received byte count must equal the asset `size`, with a hard cap of 32 MiB.
  - The jar streams into a `.part` file in a staging folder outside `mods/` (`.k8bas_update/` in the game directory).
  - Downloads use `browser_download_url`, which costs no API quota.

  *(Derived: audit U3, U11, U13, U16)*
- **REQ-UPD-14** Integrity:
  - The streamed SHA-256 must equal the asset's API `digest` (`sha256:<64 hex>`).
  - If the digest is absent or not SHA-256, a sidecar asset `<name>.sha256` is used: at most 1 KiB, a leading UTF-8 BOM tolerated, and the first token must be 64 hex characters.
  - With neither available, no install is offered (notify-only). If both exist and disagree, no install is offered either.
  - On a mismatch the `.part` file is deleted, nothing else changes, and the user sees "checksum mismatch, nothing changed".

  *(Brief: Phase 4 "verifies"; Derived: audit U12)*
- **REQ-UPD-15** Jar validation: read only `fabric.mod.json` from the downloaded jar (at most 64 KiB; nothing is extracted) and require:
  - `id` = `k8bas_skyblock_utility`
  - `version` = the expected version
  - `depends.minecraft` accepts the running Minecraft version
  - `depends.fabricloader` accepts the running loader
  - `environment` is `client` or `*`

  If any check fails, the file is deleted and the result is reported as "incompatible build". *(Derived: research §3.4 — Fabric ignores build metadata, so MC compatibility cannot come from the version compare)*
- **REQ-UPD-16** Install eligibility. The updater offers an install only if none of the following applies. Otherwise it is notify-only and names the reason:
  - it is running in the development environment (nothing may be written to `run/mods`)
  - the running mod's origin is not exactly one regular `.jar` file (for example a folder, or nested in another jar)
  - the running jar's folder is not writable
  - the running version is not SemVer
  - the instance looks launcher-managed:
    - a Prism/packwiz `mods/.index/*.pw.toml` that names our jar (verified layout)
    - a CurseForge `minecraftinstance.json` (unverified)
    - a Modrinth App profile path (unverified)

    A false positive only downgrades to notify-only.
  - no usable SHA-256 exists (REQ-UPD-14)

  *(Derived: audit U7, U17, U18; research §6)*
- **REQ-UPD-17** Staged install:
  - The jar in `mods/` is replaced only after the game process has exited. A helper process does it, using only the JDK and no network.
  - It puts the new jar into the running jar's folder under the asset name, re-checks its SHA-256, and only then deletes the old jar.
  - Waits are bounded: at most 120 s for the game to exit, and each move or delete retried for at most 60 s. On a timeout the old jar stays.
  - The destination stays inside the running jar's folder and is never the running jar itself.
  - Nothing is written into `mods/` while the game runs.
  - Every helper step is logged to `.k8bas_update/logs/helper.log`.
  - **Guarantees:** there are never zero copies of the mod, and never a partial `.jar` in `mods/`.

  *(Brief: Phase 4 "download the jar asset", "never replace the jar without user confirmation"; Derived: audit U1, U3, U5, U6; the Windows jar lock was reproduced in research §3)*
- **REQ-UPD-18** Reconciliation runs on every launch, locally and without network:
  - **Update applied** (the running version is the staged target): one toast "Updated to vX"; staged files removed; the helper log kept.
  - **Not applied yet** (the running version is the one that staged it): the staged jar is re-verified and re-armed, and "Update ready, restart Minecraft to apply" is shown. If re-verification fails, the staged files are deleted and the failure is reported.
  - **Two failed applies in a row:** the update is disarmed and the message says "install manually from the release page".
  - **Orphans:** a pending record without its staged file, or staged files without a record, are cleared and logged, with no dialog.
  - **Old jar still present:** if the jar the updater replaced is still there, it is deleted (retried at the next exit if it is locked).
  - **Other copies of our mod id** in the folder are never deleted [decided R13]. The updater deletes only the jar it replaced itself. For any other copy it logs one WARN and shows one local chat line on the first world join, naming the file. Each file name is warned about once; the names are kept in the state file.

  *(Derived: hard kills skip shutdown hooks, research §3.3 and §6)*

*Config, state, network footprint*
- **REQ-UPD-19** Migration from the 1.0.x config:
  - `autoUpdateCheckEnabled` keeps its name, so an opt-out stays an opt-out. It is relabelled "Check for updates (notify)".
  - `autoUpdateDownloadEnabled` is dropped and never maps to any automatic download or install.
  - `updateChannel` is introduced as an optional key that stays absent until the user picks a channel; absent means the REQ-UPD-07 default.
  - Runtime state (ETag, cached minimal release list, check times, back-off, `skippedVersion`, pending install) lives in its own state file, so saving the settings never overwrites it.
  - A corrupt state file is reset and logged. It never crashes the game.

  *(Brief: Phase 2 "Migrate existing config values so users don't lose settings"; Derived: audit U2; AD-3)*
- **REQ-UPD-20** Network footprint: the updater contacts only `api.github.com`, `github.com` and `*.githubusercontent.com`. It sends nothing beyond the request headers in REQ-UPD-03: no player name, UUID or telemetry. *(Derived: REQ-XC-RULES; the README network disclosure in REQ-REL-03)*

**Out of scope**
- Any silent, scheduled or background install, and any "auto-download" option.
- Downgrades or rollback through the updater.
- Authenticated GitHub access, tokens, or a GitHub App.
- Modrinth or CurseForge as a source. Also Mod Menu's update badge: possible later, and it would have to reuse the cached result, never send a second request.
- Signature or code-signing checks beyond SHA-256. The digest proves the file is intact, not who published it.
- Updating other mods or libraries (Fabric API, Hypixel Mod API, Render Chest); delta updates; extracting archives.
- Updating launcher-managed instances (notify-only by design).
- Builds for Minecraft 26.1.x (decided D-13: no 26.1.2 hotfix).
- Deleting copies of the mod that the updater did not replace itself (REQ-UPD-18) [decided R13].

**Acceptance criteria**
- **AC-UPD-01** (REQ-UPD-01) *Given* an interim build with a 1.0.1-shaped config (both update flags `true`) and a mock source that serves a newer version, *when* the client starts and joins a world, *then*:
  - no file is created outside `config/` and `logs/`
  - no request contains `26.1.2` or goes to a Modrinth host
  - exactly one local chat line names the newer version with [Open release page], and no toast appears

  — [A] + [R]
- **AC-UPD-02** (REQ-UPD-02) The Phase 4 report holds a table of U1–U20, each with a disposition and, where fixed, the REQ id that fixes it. — [R]
- **AC-UPD-03** (REQ-UPD-03, REQ-UPD-20) *Given* a recording mock server, *when* a check runs, *then* exactly one GET goes to the release-list path. It carries the UA with the running version, the Accept header and the API-version header, and no `Authorization` header. A string search of the release jar finds no `modrinth` host, and the production jar ignores the test-source override. — [A] + [R]
- **AC-UPD-04** (REQ-UPD-04) With a fake clock:
  - two starts 1 h apart after a 200 → 1 request
  - 24 h of 5xx responses → at most 4 automatic requests
  - toggle OFF → 0 automatic requests
  - two manual checks within 60 s → 1 request plus a cooldown message
  - a manual and an automatic check at the same moment → 1 in flight

  — [A]
- **AC-UPD-05** (REQ-UPD-05) Table tests:
  - `v1.1.0` vs a running `1.0.1` → candidate
  - `1.1.0+26.2` vs `1.1.0+26.1.2` → equal, not offered
  - `1.9.9` vs a running `2.0.0` → not offered
  - tag `vfoo` → skipped, logged once
  - running version `1.0` (not SemVer) → no install offered

  — [A]
- **AC-UPD-06** (REQ-UPD-06) The pattern:
  - **accepts** `k8bas_skyblock_utility-2.0.0+26.2.jar` and `…-2.1.0-beta.1+26.2.jar`
  - **rejects:**
    - `…-2.0.0+26.2-sources.jar`
    - `…-2.0.0%2B26.2.jar`
    - `…-v2.0.0+26.2.jar`
    - `…-2.0.0.jar`
    - a name whose version differs from the tag
    - `state: open`
  - **MC match:** running 26.2.1 prefers `+26.2.1` over `+26.2`; running 26.2 rejects `+26.2.1`

  — [A]
- **AC-UPD-07** (REQ-UPD-06, REQ-UPD-08) Running 2.0.0 on 26.2:
  - *Given* recorded JSON where v2.1.0 has only a `+26.3` jar and v2.0.1 has a `+26.2` jar, *then* the candidate is 2.0.1 and exactly one log line names v2.1.0.
  - *Given* only v2.1.0, *then* the automatic check shows no toast and the manual check shows "v2.1.0 is out but has no build for Minecraft 26.2".

  — [A]
- **AC-UPD-08** (REQ-UPD-07) Channel tests: a flagged pre-release with a plain tag, and an unflagged `-rc.1` tag, both count as pre-releases. STABLE hides both; BETA offers the highest. A running `2.1.0-beta.1` defaults to BETA and is offered a stable `2.1.0`. — [A]
- **AC-UPD-09** (REQ-UPD-08, REQ-UPD-09) Mock-server tests:
  - 200 with ETag, then 304 → the cached list is used and `If-None-Match` is sent
  - 403 with `retry-after: 120` → next allowed check +120 s
  - 403 with `remaining: 0` and reset T → next allowed check at T
  - two 403s without headers → waits of 60 s, then 120 s
  - 429 → back-off
  - 500 → 1 min, then 2 min
  - 404 → 24 h
  - a reset 3 days ahead → clamped to 24 h
  - a last check in the future → due
  - a manual check inside a rate-limit window → message with HH:mm and no request

  — [A]
- **AC-UPD-10** (REQ-UPD-08, REQ-UPD-09) *Given* no network (connection refused, and a black-holed host), *when* the client starts, *then* the title screen is reached, the log shows the check on a background thread with exactly one WARN line per attempt and no stack trace, and no toast appears. A manual check shows "Couldn't reach GitHub" within 25 s. — [A] + [D]
- **AC-UPD-11** (REQ-UPD-10, REQ-UPD-11) Gametest screenshots:
  - the toast on the title screen
  - the chat line shown once on the first join and not on the second
  - the confirmation screen showing every field in REQ-UPD-11
  - notes containing `§` codes and Markdown rendered as plain text and cut at 4,000 characters

  — [C]
- **AC-UPD-12** (REQ-UPD-11) *Given* candidate 2.0.1, *when* the user has not confirmed, or runs `/ksu update install 2.0.0`, *then* the mock download host records 0 requests, and the stale command is refused with the current candidate named. — [A]
- **AC-UPD-13** (REQ-UPD-12)
  - *Later* → no download, and a notification again in the next session.
  - *Skip 2.0.1* → no toast for 2.0.1, a toast for 2.0.2, and the card still lists 2.0.1.
  - *Cancel pending* → the staging folder is empty and no helper starts at exit.

  — [A] + [C]
- **AC-UPD-14** (REQ-UPD-13) Each of the following aborts the download, deletes the `.part` file and leaves `mods/` byte-identical:
  - a redirect to an unknown host
  - a redirect to `http://`
  - 6 hops
  - a final 404
  - `Content-Length` ≠ `size`
  - a body longer than `size`
  - a body over 32 MiB

  — [A]
- **AC-UPD-15** (REQ-UPD-14)
  - digest matches → staged
  - digest mismatch → "checksum mismatch, nothing changed"
  - digest null with a valid sidecar (also one with a BOM) → staged
  - digest null without a sidecar → notify-only, *Install* hidden
  - digest `sha512:…` with a sidecar → the sidecar is used
  - digest and sidecar disagree → no install

  — [A]
- **AC-UPD-16** (REQ-UPD-15) Each of these jar fixtures is rejected as "incompatible build":
  - wrong id
  - wrong version
  - `minecraft: "~26.1"` on 26.2
  - `environment: "server"`

  An array predicate that includes 26.2 is accepted. — [A]
- **AC-UPD-17** (REQ-UPD-16) Eligibility table tests, plus:
  - a dev run → notify-only, with `run/mods` unchanged
  - a fake `mods/.index/k8bas.pw.toml` naming our jar → notify-only, "managed by your launcher"
  - a read-only mods folder → notify-only

  — [A] + [B] + [D]
- **AC-UPD-18** (REQ-UPD-17, REQ-UPD-18) Windows integration on a copy of the 26.2 instance, using a test build whose source is a local mock or test repository:
  - (a) confirm, then quit normally → `helper.log` shows the move, then the delete; the next launch shows "Updated to"; exactly one k8bas jar is in `mods/`
  - (b) confirm, then kill the process → the next launch still runs the old version and shows "ready, restart"; the update applies after the following normal quit
  - (c) the old jar held by another process for over 60 s → the new jar is in place, the old jar remains, and it is removed at the next launch
  - (d) after every scenario: no `*.part` and no partial jar in `mods/`, and never zero k8bas jars
  - (e) a second, older k8bas jar placed by hand → it is still there after two launches, and exactly one WARN and one chat line name it, both in the first launch [decided R13]

  — [D]
- **AC-UPD-19** (REQ-UPD-17) Path-containment tests: an asset name with `..\`, an absolute path, or the running jar's own file name is refused. — [A]
- **AC-UPD-20** (REQ-UPD-19) Migration test with a 1.0.1-shaped config:
  - `autoUpdateCheckEnabled=false` stays false
  - `autoUpdateDownloadEnabled` is gone
  - nothing downloads without a click
  - saving the settings leaves the state file byte-identical
  - a corrupt state file is reset with one WARN, and the game boots

  — [A]
- **AC-UPD-21** (REQ-UPD-20) Across the [A] suite and the AC-UPD-18 run, the mock or proxy log contains only the allowed hosts and no player name or UUID. — [A] + [D]
- **AC-UPD-22** (REQ-UPD-03 to REQ-UPD-20) A `security-auditor` review at G4 leaves no open High finding. — [R]

**Edge cases**
- **EC-UPD-01** The newest release is still uploading (asset `state: open`) → that asset is ignored for this check; a later check picks it up.
- **EC-UPD-02** The same version is rebuilt and re-uploaded → it is equal to the running version, so it is never offered.
- **EC-UPD-03** The network drops mid-download, or the game is closed during it → the `.part` file is deleted (at latest on the next launch), nothing is armed, and *Install* is available again.
- **EC-UPD-04** The user confirms 2.0.1, and 2.0.2 appears before exit → 2.0.1 is applied at exit; 2.0.2 is offered after the restart and needs its own click.
- **EC-UPD-05** The game exits through a crash after staging → shutdown hooks still run, so the confirmed update applies. A hard kill or power loss follows REQ-UPD-18.
- **EC-UPD-06** Two game instances share one mods folder → the helper waits only for its own process. The old jar's delete may time out; it is left in place and removed at a later launch.
- **EC-UPD-07** `mods/` is on another drive than the staging folder → the jar is copied to a non-`.jar` temp name inside `mods/` and then renamed. Fabric ignores the temp name, and it is cleaned up if the step fails.
- **EC-UPD-08** Antivirus blocks the helper or the move → the old version keeps running, the next launch reports it, and after two failed applies the update disarms (REQ-UPD-18).
- **EC-UPD-09** GitHub returns releases in any order → the highest SemVer is computed; list order is never relied on.
- **EC-UPD-10** Release notes contain `§` codes, very long Markdown, or links → shown as plain text, capped at 4,000 characters; only the validated release-page link is clickable.
- **EC-UPD-11** The user clicks an update line from an earlier session → the token no longer matches, so it is refused.
- **EC-UPD-12** A 301 to `api.github.com` (repo renamed) is followed; a 301 elsewhere is an error.
- **EC-UPD-13** Another mod cancels or hides our chat line → the toast and the settings card still show the update.
- **EC-UPD-14** The update toggle is ON in the Prism test copy during [E] checks → an install still needs a click, so nothing is replaced unexpectedly. Keep the check OFF there except during the T4.5 and T7.4 updater tests, to save quota.
- **EC-UPD-15** Two root jars with our mod id → Fabric loads the newest without a warning (source-verified on 0.19.x; confirmed at runtime in AC-UPD-18). Our mod deletes neither copy and warns once (REQ-UPD-18) [decided R13].
- **EC-UPD-16** A stored `skippedVersion` is later deleted from GitHub → harmless; higher versions still notify.

**Open questions**
- **Q-UPD-01** (relates to D-13 and T1.4) What does an interim or early v1.1.0 build do about updates? **→ decided R4**
  - (a) Disable the legacy Modrinth updater completely: no network; the card says "updates: see the GitHub releases page".
  - (b) Ship the GitHub check from T4.1 early as **notify-only**: a chat line with a release-page link, no toast yet, no download. v1.1.0 users then learn about v2.0.0.
  - (c) T1.4 as written: keep the Modrinth updater with a runtime MC version. REQ-UPD-01 rules this out, because its silent-install path remains.

  *Recommendation:* (b), since the early release happens (D-13); REQ-UPD-01 is written for it. *Why it matters:* (c) keeps a silent-install path the brief forbids; it is inert only because Modrinth returns 404. With (a), every v1.1.0 user must install 2.0.0 by hand again. (b) moves T4.1 plus a small notification ahead of the early release, and tests the selection logic on a real release.
- **Q-UPD-02** (T4.3) May the mod delete other copies of itself from `mods/` at startup? **→ decided R13**
  - (a) Delete every lower-version copy of our mod id (PLAN T4.3).
  - (b) Delete only the jar the updater itself replaced, and warn once about any other copy.
  - (c) Never delete anything at startup; warn only.

  *Recommendation:* (b). *Why it matters:* deleting files the user put there is surprising and can't be undone. Extra copies only come from manual installs, and Fabric already loads the newest one. The "leftovers of the 1.0.x deleteOnExit bug" that the plan cites can't exist in practice: the 1.0.x updater never downloaded anything (Modrinth 404 in all 46 logged sessions).
- **Q-UPD-03** (same as D-14) Updater scope: **→ decided D-14 (A)**
  - (A) confirm, then a staged install on restart
  - (B) notify plus a release-page link only, like SkyHanni and Skyblocker

  *Recommendation:* (A), which matches the brief ("download the jar asset … never replace without confirmation"). *Why it matters:* (B) removes REQ-UPD-13 to REQ-UPD-18, about three of the five Phase 4 tasks, and with them the risk of swapping jars on Windows.
- **Q-UPD-04** (part of D-6) Default of the update check: **→ decided D-6 (a)**
  - (a) ON, notify-only
  - (b) OFF

  *Recommendation:* (a). *Why it matters:* with OFF, almost no one learns about updates. A notify-only check is harmless, and opt-outs from 1.0.x are kept either way (REQ-UPD-19).

---

### bestiary-hud — Bestiary Tracker HUD
**Origin:** Brief Phase 5 | **Depends on:** game-state, data-registry, hud, location (added: island context resolves family names), config-store | **Plan tasks:** T5.1, T5.1b, T5.2a, T5.2b, T5.2, T5.2c, T5.2d, T5.2e, T5.3, T5.3b (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** A movable, scalable HUD element in the HUD editor that tracks the player's own bestiary progress: kills, kills to the next tier and milestone, kills per hour and session stats. Each line can be shown or hidden. It uses only numbers the server already shows the player: the Bestiary tab widget, the Bestiary menu pages the player opens, and chat. This module owns the bestiary family table (REQ-BEST-15) and the Bestiary menu parser (REQ-BEST-10); the SkyBlock XP Optimizer reuses both and adds no second table or parser.

**Functional requirements**
- **REQ-BEST-01** The mod must provide a "Bestiary Tracker" HUD element in the HUD editor: movable, scalable 0.5–3.0, with preview data while the editor is open. It has its own toggle in "Odds & Trackers", default OFF. *(Brief: Phase 5 "movable, scalable HUD element (integrated into the new HUD editor)"; Derived: ground rule 6, D-6)* [decided D-6]
- **REQ-BEST-02** For each displayed family, the HUD must show the cumulative kill count the server reports. *(Brief: Phase 5 "kills")*
- **REQ-BEST-03** The HUD must show the current tier and the kills to the next tier. Tiers come from the bundled bracket thresholds. When the tab widget shows a next-tier threshold that differs from the bundled one, the widget value wins and the mismatch is logged once per family per session. *(Brief: Phase 5 "kills to next tier"; Derived: the 0.27 bestiary rebalance makes the bundled data go stale, see SBXP taxonomy)*
- **REQ-BEST-04** The HUD must show the kills to the family's maximum (its cap), or "MAX" once the cap is reached. *(Brief: Phase 5 "tier/milestone", read together with REQ-BEST-05 as the family maximum)* [decided R11]
- **REQ-BEST-05** The HUD must show the global Bestiary Milestone and the tiers left to the next milestone, where 1 milestone = 10 family tiers. Both come only from a Bestiary menu snapshot or milestone/tier-up chat, and the line is hidden while unknown. The line is ON by default and appears as soon as the milestone is known (REQ-BEST-08). It is never shown as a kill count and never computed from bundled totals, because the sources disagree (5,116 vs 4,822 vs 4,128 total tiers). *(Brief: Phase 5 "kills to next … milestone"; Derived: research-domain §3a, GAP-1)* [decided R11]
- **REQ-BEST-06** The HUD must show kills per hour for each tracked family:
  - the rate = kills counted in a rolling window of W minutes (default 5, range 1–60), divided by min(W, time since the first count)
  - "—" until 60 s of data exist
  - an ETA to the next tier and to max = remaining kills / rate, or "—" when the rate is 0 *(Brief: Phase 5 "rate per hour")*
- **REQ-BEST-07** The HUD must keep session stats:
  - kills per tracked family
  - total kills across families
  - session duration, counted only while connected to SkyBlock
  - the session's average kills per hour

  A session ends on a manual reset, a profile switch or a game restart. An option "reset on island change" exists and defaults to OFF. Live-count baselines still resync on every world change (REQ-BEST-11). *(Brief: Phase 5 "session stats")* [decided R11]
- **REQ-BEST-08** Each line type can be shown or hidden on its own: family + tier, kills, to next tier, to max, milestone, rate, ETA, session kills, session average, source/age. The line order should be configurable. Tiers can be shown as Roman or Arabic numerals. A compact one-line layout may be offered. Defaults:
  - ON: family + tier, kills, to next tier, milestone (drawn only while known, REQ-BEST-05), rate, session kills
  - OFF: to max, ETA, session average, source/age *(Brief: Phase 5 "kills to next tier/milestone" and "Make the displayed lines configurable"; Derived: ground rule 6)* [decided R11]
- **REQ-BEST-09** Family selection has two modes:
  - auto-follow (default): the family whose count changed most recently; when two change in the same update, the larger delta wins, then the name
  - a pinned list of up to 5 families

  An option hides maxed families. *(Derived: necessary to choose what the HUD shows; PLAN T5.3)*
- **REQ-BEST-10** Data sources, in order of priority:
  1. The Bestiary tab widget (live). Its line format is **UNVERIFIED until the G1 capture**; provisional fixtures are marked as such.
  2. Bestiary family pages the player opens: "Bestiary ➜ <area>" and "Fishing ➜ <sub>" (snapshot).
  3. Bestiary tier-up and milestone chat blocks, including ones other mods hide.

  Each shown value remembers its source and age, and the newer value wins. This module owns the Bestiary menu parser (source 2). The sbxp-optimizer reads the Bestiary component menu with this parser and adds no second Bestiary parser. *(Brief: Phase 5 "tracks bestiary progress"; Derived: PLAN T5.2, research-domain §3b; §13.4 assigns the parser to T5.2)*
- **REQ-BEST-11** Live counting from the widget:
  - The first value seen for a family is the baseline and is not counted.
  - Later deltas of 1–50 are counted.
  - A delta above 50, or a decrease, resyncs the baseline without counting.
  - An abbreviated (approximate) value is skipped: it neither counts nor becomes the baseline (REQ-BEST-17).
  - Baselines reset on a world or server change, a profile switch and a disconnect. *(Derived: PLAN T5.2; SkyHanni GhostTracker behaviour, studied only)*
- **REQ-BEST-12** If the module is ON and no "Bestiary:" widget section has been seen for 10 s, the HUD shows the hint "Bestiary widget off — enable it in <command>" instead of live lines. Every area of the game counts as a Bestiary area except the Dungeon Hub [decided R24], so the hint applies on every island except the Dungeon Hub, where a missing section is expected and shows no hint. There is no hint while the island is unknown (transfers, unmapped modes). The command and menu path are the shared widget-hint string (REQ-GS-18; its wording is checked at G3, R24); this module hard-codes no command. The hint is never repeated in chat. Any clickable suggestion only fills in the command and never sends it. *(Brief: ground rule 5; Derived: PLAN P5, T5.3)*
- **REQ-BEST-13** The feature is read-only. It never opens /be or the widget menu, never clicks or pages a menu, and never uses the Hypixel API, which needs a key. In v1, counts come only from server-shown numbers; there is no estimated counter from mob deaths. If the G1 capture shows that the Bestiary widget gives no usable live kill count, the v1 fallback is menu snapshots and chat only: no live counting (REQ-BEST-11) and no rate or ETA between snapshots, and the HUD shows "live counting unavailable". The estimated counter is revisited after G1. *(Brief: ground rule 5; Derived: PLAN §3 row "Bestiary HUD")* [decided R11]
- **REQ-BEST-14** The last-known kills and tier per family, and the last milestone state, are stored per SkyBlock profile with a timestamp. After login they are shown with an age label (e.g. "menu, 12 min ago") until a live update replaces them. *(Derived: multiple profiles; PLAN T3.0d/T5.2)*
- **REQ-BEST-15** The bundled family table holds, per family: a stable slug id, display name, island/category, bracket or bracket-set id, cap, maximum tier count and mob ids. A cap that is not a bracket value is handled by using the thresholds below the cap with the cap as the last tier. The table is the trimmed NEU `bestiary.json`, MIT, with a notice. This module owns the table. The sbxp-optimizer must reference families as `bestiary:<id>` and takes its bestiary data from this table; it bundles no second bestiary table. *(Brief: Phase 5; Addendum: "reuse the internal database"; Derived: D-5, GAP-1 §5)* [decided D-5]
- **REQ-BEST-16** Family names from the widget, menu and chat are mapped to table ids by name plus the current island or menu category. An unknown or ambiguous name is shown with the server's values only (kills, tier, next threshold) and logged once. *(Derived: name collisions across islands, GAP-1 "families keyed by island and display name")*
- **REQ-BEST-17** Parsers must accept thousands separators, k/M/B abbreviations, and Roman or Arabic tier and milestone numerals (e.g. "CCLXI➡CCLXII" and "261➡262"). An abbreviated value (e.g. "12.4k") is parsed as approximate:
  - it is shown with "≈", and so are the values derived from it (to next tier, to max, ETA)
  - it is never used as a delta baseline or delta (REQ-BEST-11)
  - it is never stored or written as an exact count (REQ-BEST-14)

  This matches game-state's rule that consumers needing exact counts treat an abbreviated value as unknown. *(Derived: research-domain §3b; your 26.2 logs show Roman milestone numerals; game-state EC-GS-14)*

**Out of scope**
- Bestiary data from the Hypixel API, which needs a key.
- Opening /be or the widget menu automatically, or sending any command.
- Counting or estimating kills from mob deaths in v1, even if the widget proves unusable (R11; revisited after G1).
- Computing the milestone from bundled data, and "kills to next milestone" as a kill count.
- The SkyBlock XP earned from the bestiary (that is sbxp-optimizer).
- A per-variant or per-level breakdown inside a family, other players' progress, and tier-up alerts or sounds.
- Kill Combo chat as a source; a remote data refresh (AD-7).

**Acceptance criteria**
- **AC-BEST-01** (REQ-BEST-01) Given a fresh config, the toggle is OFF [A]. Given the HUD editor with no bestiary data, the element shows preview data and can be dragged and scaled 0.5–3.0. After Save, a restart and a GUI-scale change from 2 to 4, it sits at the same anchor-relative position [C].
- **AC-BEST-02** (REQ-BEST-02, 03, 04) Ghost (bracket 2, cap 25,000) has 20 tiers. At 12,449 kills it shows tier 15, next 12,500 (51 left) and 12,551 to max. Stoneworm (bracket 6, cap 250) at 250 kills shows tier 15, "MAX". A fixture family whose cap is not in its bracket list ends on the cap. Verified by [A].
- **AC-BEST-03** (REQ-BEST-03) The widget line "Ghost 15: 12,449/12,600" displays 151 left and writes exactly one mismatch log line per session. Verified by [A].
- **AC-BEST-04** (REQ-BEST-05) After milestone chat "CCLXI➡CCLXII" followed by 3 tier-up blocks, the HUD shows milestone 262 and 7 tiers to the next. With no milestone information, the line is hidden. Verified by [A].
- **AC-BEST-05** (REQ-BEST-06) With 100 kills counted over 5 min and W = 5, the rate is 1,200/h, and 51 kills left gives ETA 2m33s. With under 60 s of data, the rate is "—". With no kills in the window, the rate is 0/h and the ETA "—". Verified by [A].
- **AC-BEST-06** (REQ-BEST-07) Given 412 session kills: an island change with the option OFF keeps 412 and resyncs the baseline; a profile switch gives 0; a manual reset gives 0; with the option ON, an island change gives 0. Verified by [A].
- **AC-BEST-07** (REQ-BEST-08) A fresh config shows exactly the default ON lines of REQ-BEST-08; the milestone line is among them and is drawn once a milestone fixture is applied. Hiding a line type removes exactly that line, a changed order is rendered in that order, and the Roman/Arabic setting switches "XV" and "15". Verified by [A] on the layout model and [C] screenshots.
- **AC-BEST-08** (REQ-BEST-09) Given updates for Ghost (+3) and then Goblin (+1), auto-follow shows Goblin. In the same update, Ghost +3 and Goblin +1 → Ghost. A pinned list of 5 shows exactly those 5, and a sixth pin is refused. Verified by [A].
- **AC-BEST-09** (REQ-BEST-10, 17) Parser fixtures, verified by [A]:
  - The widget lines "Ghost 15: 12,449/12,500", "Ghost 25: MAX", a Roman-tier line and "12.4k" parse. These are provisional and marked UNVERIFIED; they are replaced by the G1 capture.
  - "12.4k" parses as approximate: it is shown as "≈12.4k"; in the sequence 12,449 → "12.4k" → 12,452 the abbreviated value is skipped, so the baseline stays 12,449 and the session gains +3; the stored profile value stays the last exact count.
  - Every BESTIARY tier-up block in the sanitised log fixtures (378 in your logs) parses family, from-tier and to-tier with 0 failures.
  - Milestone lines parse in both Roman and Arabic form.
  - The menu-lore fixture "Progress to Tier XV: 57.1%" parses.
- **AC-BEST-10** (REQ-BEST-11) The widget sequence 12,449 → 12,452 → 12,600 → 12,590 → 12,592 gives session +3, +0 (resync), +0 (resync), +2, for a total of 5. After a world change, the first value adds 0. Verified by [A].
- **AC-BEST-11** (REQ-BEST-12) A tab list without a "Bestiary:" section for 10 s shows the hint line, built from the shared REQ-GS-18 string, on any island except the Dungeon Hub; with the island set to the Dungeon Hub, the same tab list shows no hint [decided R24], and with the island unknown it shows no hint either. During the test, nothing is sent to the server through chat or commands. Verified by [A] and [C].
- **AC-BEST-12** (REQ-BEST-13) Code review finds no screen open, click, command send or API call [R]. A production boot with your mod set shows BESTIARY chat blocks that SkyHanni or Skyblocker hide are still parsed, with no new ERROR [D].
- **AC-BEST-13** (REQ-BEST-14) After a relogin, the last snapshot shows with its age label. A newer live widget value replaces it. Profile B never shows profile A's values. Verified by [A].
- **AC-BEST-14** (REQ-BEST-15) `check` validates the shipped table, verified by [A]:
  - the family count equals the pinned NEU commit's count (359 at `24564a1ad2`)
  - ids are unique
  - every bracket id exists, caps are > 0 and tier counts ≥ 1
  - the NEU notice is present
  - a sbxp test reference `bestiary:ghost` resolves and a dangling one fails
- **AC-BEST-15** (REQ-BEST-16) "Ghost" seen in Dwarven Mines resolves to its id. A fixture name present on two islands resolves by island. An unknown name shows the server values and logs once. Verified by [A].
- **AC-BEST-16** (REQ-BEST-02, 06, 07, 10) Your G5 field check: with the widget on, kill at least 50 mobs of one family. The HUD's session kills must equal the widget's delta exactly, the rate must be non-zero, and a tier-up must update the tier within 2 s of the chat block. Verified by [E].
- **AC-BEST-17** (REQ-BEST-13, fallback; applies only if the G1 capture shows the widget unusable) With live counting disabled, menu-snapshot and chat values show with their age label, rate and ETA show "—", and the HUD shows "live counting unavailable". Injected mob-death events for mobs the player hit change no count. Verified by [A]. [decided R11]

**Edge cases**
- **EC-BEST-01** The widget is off or shows only player-selected families → other families come from the last menu snapshot with their age, and the hint applies only when there is no "Bestiary:" section at all.
- **EC-BEST-02** "MAX" lines → tier = max tier and the "to next" line is hidden.
- **EC-BEST-03** The widget threshold differs from the bundled data (0.27 rebalance) → the widget wins and the mismatch is logged once.
- **EC-BEST-04** A jump above 50 between updates (the widget re-enabled after a while, or area kills in dungeons) → resync without counting. This is a documented possible undercount.
- **EC-BEST-05** A count decreases (profile switch, bad parse) → resync.
- **EC-BEST-06** A disconnect and reconnect, or a server/world change → baselines reset, with no double count.
- **EC-BEST-07** The AFK rule (no credit after a few minutes without camera movement) → counts stop and the rate falls to 0 across the window; no error.
- **EC-BEST-08** A family name appears on two islands, or a family is renamed → resolved by island; an unknown name shows server values only.
- **EC-BEST-09** Caps outside the bracket list (the research counts 10 or 50 such families) → handled as in REQ-BEST-15, with the widget winning.
- **EC-BEST-10** CRITTERS bracket-set families (10 tiers) → their own set is used.
- **EC-BEST-11** The Stoneworm family mixes Worms and Scathas → shown as the server reports it, not split.
- **EC-BEST-12** Shared credit for dungeon or Kuudra mobs → counted as the server reports it.
- **EC-BEST-13** Other mods hide or compact the BESTIARY chat block → it is read through the cancelled-message path.
- **EC-BEST-14** A menu with "Overall Progress" shown or hidden → both parse.
- **EC-BEST-15** Fresh install with no snapshot and the widget off → only the hint shows; the editor shows preview data.
- **EC-BEST-16** A menu snapshot older than the current widget value → the newer value wins by timestamp.
- **EC-BEST-17** The widget shows a family's count only in abbreviated form (e.g. "12.4k") → the value is shown with "≈", that family gets no live delta counting and its rate shows "—" until an exact value arrives (REQ-BEST-17).

**Open questions**
- **Q-BEST-01** What does "kills to next … milestone" mean? → decided R11 (global milestone as "tiers to next milestone" from menu/chat, plus the family "kills to max" line; milestone line ON by default when known)
- **Q-BEST-02** When does a "session" reset? → decided R11 (manual reset, profile switch or restart; optional "reset on island change", default OFF)
- **Q-BEST-03** If the G1 capture shows the Bestiary widget gives no usable live kill count, what is the fallback? → decided R11 (menu and chat snapshots only, no estimated death counter; revisit after G1)
- **Q-BEST-04** May the trimmed NEU `bestiary.json` (MIT) be bundled with a notice? → decided D-5 (MIT with notices, pinned commit)
- **Q-BEST-05** Should the Bestiary HUD default to OFF? → decided D-6 (OFF)

---

### sbxp-optimizer — SkyBlock XP Optimizer

**Origin:** Addendum (whole); Brief ground rules 4–6 | **Depends on:** data-registry, game-state, ui-config, hud, bestiary-hud | **Plan tasks:** T6.0, T6.1, T6.1b, T6.1c, T6.2, T6.2e, T6.2f, T6.2b, T6.2c, T6.3a, T6.3b, T6.3c, T6.4, T6.5, T6.5b, T6.5e, T6.5f, T6.5c, T6.5d, T6.6, T6.6b, T6.6c, T6.7, T6.8, T6.8b, T6.9, T6.11 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** An in-game overview that ranks every available SkyBlock XP source by SkyBlock XP per effective hour, so the player always sees the fastest next step. Effective hours combine researched play time with coin cost converted at the player's coins per hour. The overview also shows what locked tasks need, plans a greedy route to a target level, and tracks progress per profile from menus the player opens.

**Terms:** *Task* is one SkyBlock XP source as listed in the SkyBlock Leveling menu, or one instance of a generated family (e.g. one minion type). *Step* is one XP-granting stage of a ladder or capped task. An *effort line* is one time component (throughput, drop or fixed minutes). A *cost line* is one coin, item or currency component. A *row* is one table line: one per task instance's next step that is not done [decided D-21].

**Functional requirements**

*A. Task table and data*
- **REQ-SBXP-01** The optimizer must load its tasks from one versioned JSON task table in the bundled internal database (data-registry). The table must have a published JSON Schema and the registry envelope of REQ-DATA-02: `table`, `schemaVersion`, `dataVersion` (integer, +1 on every content change), `gameVersion` (SkyBlock version string, e.g. `0.27.1`), `generatedAt`, `license` and the `sources` map. Licence:
  - The task table is `CC0-1.0` and copies no value from an MIT file.
  - Values from NEU or SkyHanni-REPO files are referenced only through `ref.*` tables under `data/thirdparty/<upstream>/` (MIT, with notice), via `table:id` (REQ-DATA-04, REQ-DATA-05).

  *(Addendum: Data b1; Derived: "internal database from Phase 3.9" read as the shared registry T3.0c [decided D-26]; licence: REQ-XC-LICENSE-03, D-5)*
- **REQ-SBXP-02** Every task must expose these fields:
  - stable id, name, and category (menu category, e.g. Core, Slaying, Skill Related)
  - SkyBlock XP reward, per completion or per step
  - type: `one-time`, `repeatable` (a finite count) or `capped` (a counter up to an XP cap). `ladder` (ordered one-time steps) is a structural form of one-time.
  - the XP cap, where the task is capped
  - effort lines, from which time is derived, and cost lines
  - prerequisites: other tasks or steps; skill, slayer, Catacombs, class, collection, HOTM, HOTF, garden or SkyBlock levels; items; islands; quests; "any of"
  - sources, each with a link or revision
  - verification state, including `verified` and the last-verified game version

  *(Addendum: Core logic b1; ladder Derived: PLAN AD-11)*
- **REQ-SBXP-03** XP values must never be invented. An XP value without an acceptable source must be stored as `null`, and the task is still listed with `verified: false`. A task with `null` XP must not be ranked or planned. *(Addendum: Data b2)*
- **REQ-SBXP-04** Source and verification rules [decided D-16 (a)]:
  - Every non-null XP value, maximum and structure element must cite at least one source that is not a wiki cross-check. Acceptable sources are the Hypixel API, NEU or SkyHanni-REPO MIT data (through `ref.*` tables, REQ-SBXP-01), menu captures and the maintainer's logs.
  - Patch notes the user supplies show what changed. A number they claim is applied only with one of the sources above (REQ-SKILL-04).
  - An unverified rate or drop chance may rest on one wiki page the user supplied (D-28), but only while it stays `verified: false`.
  - `verified: true` needs at least 2 independent agreeing origins or an in-game capture. The community wiki and Fandom count as one origin.

  *(Addendum: Data b2; Derived: ground rule 4, D-16, PLAN §2. Decided D-16 (a): this overrides the addendum's "fill XP values from the official Hypixel SkyBlock Wiki and patch notes", because the official wiki closed on 2026-07-21 and the community wiki's host forbids automated use.)*
- **REQ-SBXP-05** Rates must be stored separately from tasks, so that changing one rate recalculates every dependent task without editing any task. Each rate holds:
  - a value and a unit (`<thing>/h`, or `/day` for wall-clock waits)
  - its assumptions, sources, confidence and `verified` flag
  - an optional range and an optional running coin cost per hour
  - values per stage (`early`, `mid`, `late`), or a single `all` value

  *(Addendum: Research 4, 5)*
- **REQ-SBXP-06** Time, effective hours, XP/h, the task-level confidence (REQ-SBXP-21) and drop quantiles must be computed at runtime and never stored in the table. A rate's researched `confidence` (REQ-SBXP-05) is an input, not a computed value, and is stored. *(Derived: Research 4 "so the time can be recalculated"; AD-11)*
- **REQ-SBXP-07** `./gradlew check` must validate the table against its schema and against these rules:
  - Every reference resolves: rates, sources, tasks, steps, prerequisites and reference tables.
  - Ids are unique after generator expansion.
  - An effort line's unit equals the numerator of its rate's unit.
  - Step numbers are contiguous.
  - There is no prerequisite cycle.
  - The source rules of REQ-SBXP-04 hold.
  - The CC0 task table holds no source of kind `neu` or `skyhanni` except as a `table:id` reference into a `ref.*` table under `data/thirdparty/` (REQ-SBXP-01).
  - Each family maximum, recomputed from the reference tables, equals the declared maximum, or a conflict is recorded.
  - Strings contain no colour codes and no lore, and prose stays under a length limit.

  *(Derived: Addendum Data "with a JSON schema" and "never invent"; PLAN §10; REQ-XC-BUILD)*
- **REQ-SBXP-08** Progress, manual marks and overrides must be keyed on stable task and step ids. Renamed ids must be mapped through an alias table, so user state survives data updates. The user state of removed tasks is hidden, not deleted. *(Derived: per-profile cache + update skill)*
- **REQ-SBXP-09** The table must list every SkyBlock XP family known at its `gameVersion`, including families whose XP or effort is unknown. A family item that appears in a captured leveling menu but has no data entry must show as "unlisted", with its earned/max XP, in the Not yet estimated view and in the unmapped report. *(Addendum: Goal "every available SkyBlock XP source"; Derived: GAP-2, about 467 XP is unlisted)*
- **REQ-SBXP-10** A deterministic data build must produce the reference tables from pinned sources, and the build must be replayable offline from recorded fixtures. The tables are skills, collections, museum, minions, essence shops, attributes, Abiphone and prices. Tables derived from MIT files are `ref.*` tables under `data/thirdparty/<upstream>/` with notices (REQ-SBXP-01). Bestiary data comes from bestiary-hud's family table (REQ-BEST-15) via `bestiary:<id>`; this module builds no second bestiary table. *(Derived: AD-12; needed by sbxp-update-skill steps 2–5)*
- **REQ-SBXP-58** Before T6.1 starts, `docs/sbxp/tasks.schema.json` (PLAN §10) must be aligned to the decisions and shown to the user again for approval. The aligned schema has:
  - the registry envelope of REQ-DATA-02 (REQ-SBXP-01)
  - the CC0 licence, with NEU and SkyHanni-REPO values only as `table:id` references into `ref.*` tables
  - the rule that an unverified rate or drop chance may rest on one wiki page the user supplied (REQ-SBXP-04)

  *(Addendum: intro "show me the updated plan and the data schema before implementing"; Derived: D-16, REQ-DATA-02)*

*B. Calculation*
- **REQ-SBXP-11** Time is the sum of the task's effort lines:
  - throughput: `required_amount / rate_per_hour`
  - drop: expected attempts / attempts per hour. With a pity of N attempts, E = (1 − (1 − p)^N) / p; without pity, E = 1/p.
  - fixed: minutes / 60

  `/day` rates and wall-clock availability add waiting time only, never play time. *(Addendum: Research 3)*
- **REQ-SBXP-12** For drop-based effort, the optimizer must compute P50 and P90 attempts, using quantile q = min(N, ⌈ln(1 − q) / ln(1 − p)⌉), and show them next to the expected value. Ranking uses the expected value. *(Addendum: Research 3 "note the variance")*
- **REQ-SBXP-13** Coin cost is the gross sum of:
  - coin lines
  - item lines (quantity × unit price)
  - currency lines that convert to coins
  - hours spent on a rate × that rate's running coin cost per hour

  No resale is credited in v1 [decided D-19]. *(Addendum: Core logic b2; Derived: D-19)*
- **REQ-SBXP-14** `effective_hours = time_hours + coin_cost / coins_per_hour` and `efficiency = skyblock_xp / effective_hours`. Tasks that need both time and coins combine both terms. Effective hours are floored at 1 minute for any task with non-zero time or coins. *(Addendum: Core logic b2; floor Derived: AD-13)*
- **REQ-SBXP-15** A task that needs zero play time and zero coins (it only involves waiting, e.g. Community Shop) must never be divided by zero. It goes into a Free / waiting group, sorted by XP, that shows its wall-clock wait. Wall-clock waits are never converted at coins per hour [decided D-29]. *(Derived: formula domain; D-29)*
- **REQ-SBXP-16** Missing data never counts as free. If any XP, effort, rate, chance, quantity or price a task needs is absent or `null` after overrides, the task has "no estimate": it is listed in Not yet estimated and never ranked or planned. `effort: []` means "no play time needed" and does not count as missing. *(Addendum: Research "Do not guess time values")*
- **REQ-SBXP-17** When a task or step has alternative options (e.g. buy / grind / wait), the optimizer must use the option with the lowest effective hours at the current coins per hour and stage, among the options the coin-only filter leaves (REQ-SBXP-19), and show which option it chose. *(Derived: Addendum Core logic b2 "combine both terms"; AD-13)*
- **REQ-SBXP-18** XP must be credited correctly in three cases:
  - "Best member counts" groups: doing member m yields max(0, xp(m) − the highest XP among done members).
  - Derived XP, e.g. bestiary milestones, is added to the steps that produce it.
  - Capped counters that decay are handled piecewise.

  *(Derived: correct XP per task; AD-13)*
- **REQ-SBXP-19** Cost classes [decided R14]:
  - The class is computed per estimated option. Prerequisites never change the class.
    - **coin-only:** no throughput or drop effort, fixed effort of at most 5 min, and at least one coin, item or coin-convertible cost line
    - **hybrid:** play time and coin cost
    - **time-only:** play time and no coin cost
    - **free / waiting:** neither
  - A task's class follows from its estimated options and never depends on coins per hour: coin-only only if every option is coin-only; otherwise hybrid if any option has a coin cost; otherwise time-only if any option needs play time; otherwise free / waiting.
  - With "exclude coin-only" on, coin-only options are removed before option choice (REQ-SBXP-17). A task is removed from Ranked, Free / waiting and the planner only when no estimated option remains.
  - `docs/sbxp-research.md` and the G6 report must list every family classified coin-only or hybrid, with its options and the class of each.

  Research found the groups below at 0.27.1. They seed that list; the shipped classification is computed from the data.
  - Bazaar: Attribute Levels (shards), Essence Shop perks (essences), Unlocking Powers (power stones), SkyBlock XP consumables, Garden plots (compost), bank account upgrades (coins + Enchanted Gold Blocks), Craft Minions (recipe ingredients), Fossil Research, Carrolyn's exportable crops, Hootie candy slots, Recombobulator (raises Accessory Power)
  - Auction House: Accessory Power (accessories), Pet Score (pets), Museum donations, Fast Travel scrolls, George taming-cap pets, Abiphone contact slots and phones
  - Coins paid to NPCs: Jacobus accessory-bag upgrades, personal bank upgrades, Abiphone contacts that cost coins
  - Hybrid: slayer quests (fee + fight), Kuudra (keys), dragons (Summoning Eyes), Arachne (calling items), composter and greenhouse (copper or items plus time), garden visitors, Moonglade Beacon, Kat/Fann pet upgrades
  - Skills: never coin-only. Grind options are time-only; an option that buys inputs to speed up skill XP is hybrid.
  - Not coin-buyable: collections, fairy souls, HOTM/HOTF and powder/whispers, Anita medals, event tokens, Rift motes, dojo, harp, reputation, bestiary, dungeons, Chocolate Factory, trophy fish. Community Shop upgrades are free / waiting, or bought with Gems (REQ-SBXP-20).

  *(Addendum: Core logic b2 "identify all of them")*
- **REQ-SBXP-20** Currencies other than coins are converted only as the data says:
  - via an item price (e.g. essence → its Bazaar product): counted as coins
  - via a researched rate (e.g. copper per hour): counted as play time
  - real-money currencies (SkyBlock Gems): never converted into coins or time. Options that need them are never chosen, ranked or planned, and the row details note them [decided R14].

  *(Derived: Addendum "convert coin cost into time"; D-29)*
- **REQ-SBXP-21** Every estimate must carry a confidence: high = multiple consistent sources, medium = a single source, low = extrapolated. A task's confidence is the lowest of its XP, effort and rate components. A rate taken from a stage other than the selected one forces low. *(Addendum: Research 6; Derived: stage fallback, AD-13)*
- **REQ-SBXP-22** A "player stage" setting (early / mid / late; stored per profile [decided R14]) must pick the matching rate. Lookup order: the selected stage, then `all`, then the nearest stage (ties go to the lower stage and force low confidence). If every candidate is `null`, the task has no estimate.
  - On first identification of a profile, the stage is suggested from the SkyBlock level using the `stages` thresholds in the data (`verified: false` until sourced). Until the level is known, `mid` is suggested.
  - A suggested stage is labelled "suggested" until the user picks a stage [decided R14].

  *(Addendum: Research 5; suggested stage Derived: Q-SBXP-19 (b))*
- **REQ-SBXP-23** Overrides [decided R14]:
  - The user must be able to override any task's time, rate or coin cost, any rate id (applies to every task that uses that rate), and any item's price.
  - Overrides are stored per profile.
  - Precedence for time: task time override > task rate override > rate-id override > researched rate for the stage.
  - Precedence for coins: task coin override > item price override > live Bazaar price > bundled price.
  - Each override records the `dataVersion` it was made against and is kept across data updates. It is flagged "outdated" when the researched value it overrides has changed or its id was removed.
  - Each override can be reset to the researched value on its own.

  *(Addendum: Research 5 "override any individual rate or time"; UI Config)*

*C. Ranking, locked section, recalculation*
- **REQ-SBXP-24** The Ranked view must list every task that is unlocked, not completed and estimated. It is sorted by efficiency, highest first, with ties broken by task id, and each row shows a rank number. *(Addendum: Core logic b3)*
- **REQ-SBXP-25** There is one row per task instance's next step that is not done (about 2,800 rows). Completing a step moves the row on to the next step [decided D-21]. *(Derived: D-21)*
- **REQ-SBXP-26** Tasks with at least one unmet prerequisite must appear in a separate Locked section. It lists each unmet prerequisite in words (e.g. "Farming 30 needed", "needs <task> step 3") and the task step that produces it. It should also show chain efficiency: Σ xp / Σ effective hours over the task plus its unmet producer steps, or "n/a" when a producer or an estimate is unknown. *(Addendum: Core logic b3)*
- **REQ-SBXP-27** Each prerequisite must evaluate to met, unmet or unknown:
  - Levels come from detected component state (REQ-SBXP-37) or a manual "set level".
  - Items, islands and quests come from a manual check-off.
  - Ladder step n requires step n − 1.
  - A prerequisite with an unknown threshold counts as unmet and shows "requirement unknown".

  By default, a task whose only open prerequisites are unknown is ranked with a "?" badge. The setting "treat unknown as locked" (default OFF) moves such tasks to Locked instead [decided R14]. *(Derived: Addendum Core logic b3; PLAN T6.6)*
- **REQ-SBXP-28** A calendar or event task outside its window should stay listed with "next window in X". A mayor-gated task should show "wait unknown". *(Derived: availability in the schema)*
- **REQ-SBXP-29** The optimizer should show "ranking covers X of Y remaining XP", where X is the XP of estimated tasks and Y is all remaining XP. *(Derived: transparency, because unestimated tasks are excluded)*
- **REQ-SBXP-30** These views must update without a manual refresh: Ranked, Locked, Not yet estimated, Free / waiting, the planner and the HUD. They update when coins per hour, stage, any override, a filter, prices or progress change. Recalculation must not block the render thread for more than 50 ms. Selection and scroll position are kept by task id. *(Addendum: Core logic b4; progress and prices Derived)*

*D. Plan to target level*
- **REQ-SBXP-31** A "plan to target level" view takes a target SkyBlock level and plans greedily:
  - It repeatedly picks the eligible row with the highest efficiency and applies it: marks it done, unlocks dependents and advances ladders. Then it recomputes and continues until total XP ≥ target × 100.
  - Free / waiting tasks are picked first, because they cost zero effective hours, and their waits are added up.
  - It honours the active filters.
  - It shows the ordered picks, total play time, total coins, total effective hours, total wall-clock waiting (separately), and the count and XP of tasks left out for missing estimates.

  *(Addendum: UI Optimizer screen "plan to target level")*
- **REQ-SBXP-32** The planner must handle these cases:
  - target ≤ the current level: report "already reached"
  - target not reachable: plan up to all reachable estimated XP and report "short by N XP"
  - target above the known maximum level: warn. The maximum is data-driven and "unknown" until captured [decided D-24].
  - Current XP is level × 100 + progress from detection. If the current level is unknown, the planner asks for it.

  The planner runs off the render thread and finishes within 2 s for the shipped table. *(Derived)*

*E. Progress detection*
- **REQ-SBXP-33** When the player opens the SkyBlock Leveling menus (`/sblevels` and its sub-menus), the mod must read the item names and tooltips the server has already sent. From them it updates each task's earned/max XP, step states and completion. This is strictly read-only and display-only: no clicks, no automatic opening or paging, no commands sent, and no packets cancelled or modified. *(Addendum: Progress b1; Brief: ground rule 5; REQ-XC-RULES)*
- **REQ-SBXP-34** Detected state must be cached per player account and SkyBlock profile and persisted across restarts. It is written only once the profile is identified, never under a placeholder. *(Addendum: Progress b1, b3)*
- **REQ-SBXP-35** The user must be able to mark any task or step as done or not done. For ladder instances whose menu shows only totals, the user can set a level. Precedence: a menu snapshot newer than a manual mark > the manual mark > live deltas. A newer snapshot that contradicts a mark clears the mark and shows a one-time conflict note. *(Addendum: Progress b2; precedence Derived: PLAN T6.5)*
- **REQ-SBXP-36** SkyBlock profiles and accounts must be handled separately. Within 5 s of a new profile being identified, the optimizer loads that profile's progress and per-profile settings. It never mixes state between profiles. *(Addendum: Progress b3)*
- **REQ-SBXP-37** Some families show only totals in the leveling menu [decided D-27 (a)]. For these, the mod must:
  - read the component menus the player opens (Skills, Collections, Museum, Bestiary, HOTM/HOTF, Pets, Accessory Bag), under the same read-only rules. The Bestiary menu is read with bestiary-hud's parser (REQ-BEST-10); this module adds no second Bestiary parser.
  - infer per-instance tiers from cumulative XP only where the answer is unique; otherwise mark the tier "unknown" and offer a manual "set level"
  - use the same levels in prerequisite checks

  *(Derived: needed for "ranks correctly for a progressed profile")*
- **REQ-SBXP-38** Between snapshots, two signals should update progress provisionally: the action-bar line `+N SkyBlock XP (<label>) (x/100)` and chat reward lines [decided R14]. This includes messages that other mods cancel or rewrite. Reports of one gain within a 3 s dedupe window are counted once; this is the only SBXP dedupe window. A snapshot replaces provisional deltas. *(Derived: keeps the ranking and HUD fresh)*
- **REQ-SBXP-39** The optimizer should show text-only hints such as "open <menu> to refresh" for families never seen, or not seen since the last `gameVersion` change. It must also collect unmapped action-bar labels and menu items per profile for the update skill. *(Derived: coverage; feeds sbxp-update-skill)*

*F. Prices*
- **REQ-SBXP-40** Coin costs must default to bundled prices. These are versioned and dated separately from the XP data, with a price snapshot version and date. *(Addendum: Prices b1)*
- **REQ-SBXP-41** An optional toggle (default OFF) fetches live prices from Hypixel's public Bazaar endpoint, which needs no API key [decided D-22]:
  - It updates only items tradable on the Bazaar.
  - It sends at most 1 request per 15 min, and only while the optimizer screen is open. Requests are conditional.
  - It keeps a disk cache: fresh under 15 min, stale up to 24 h, ignored after that.
  - It backs off exponentially on errors and on 429/503.
  - On any failure it silently falls back to the cache or the bundled prices: no chat line, toast or dialog.
  - A passive badge shows live / stale / bundled and the price date.

  *(Addendum: Prices b2)*
- **REQ-SBXP-42** Bazaar purchases are priced at the instant-buy price by default, with buy-order as an option [decided D-22]. *(Derived: D-22)*
- **REQ-SBXP-43** The client must not fetch Auction House data [decided D-20]:
  - Items sold only on the Auction House use bundled reference prices. These are built at data-build time from one keyless scan of the official auctions endpoint per data update, keeping aggregates only and no player identifiers.
  - Otherwise the user's price override applies.
  - Third-party price APIs are not used.

  *(Addendum: Prices b3 "propose it first")*
- **REQ-SBXP-44** Where prices are shown, the optimizer should state that it is not affiliated with or endorsed by Hypixel. *(Derived: Hypixel API policy of 2026-09-30; REQ-XC-LICENSE)*

*G. Research (rates and time estimates)*
- **REQ-SBXP-45** Every task's effort must be broken down into its underlying grind, as one of:
  - amount + unit + rate (kills, collection, skill XP, runs, commissions)
  - drop chance + pity + attempt rate
  - fixed minutes, with the assumptions stated

  *(Addendum: Research 1)*
- **REQ-SBXP-46** Rates must come from multiple sources. Each source records its kind, link or revision, date, game version and how it was accessed. Sources dated after the last patch touching the grind are preferred; older ones are version-adjusted or marked stale in the research log. Wiki, forum and reddit pages are used under D-28 (i): only pages the user pastes or saves, and search snippets are leads only. The official Hypixel SkyBlock Wiki closed on 2026-07-21, so the community wiki serves as a cross-check [decided D-28 (i), D-16 (a)]. *(Addendum: Research 2; decided D-28 (i): Claude reads these sources only as user-supplied pages, which narrows the addendum's direct research on them)*
- **REQ-SBXP-47** Rates must be given for early, mid and late game where they differ meaningfully, and once for all stages otherwise. *(Addendum: Research 5)*
- **REQ-SBXP-48** `docs/sbxp-research.md` must list, for each family and each rate id: the sources, the rates found, how conflicting numbers were reconciled, and the final assumption. Every task id, including generated ones, must resolve to its family entry. *(Addendum: Research 7)*
- **REQ-SBXP-49** Research runs in priority order [decided D-18 (c)]: a family's rank is the better of its rank by the user's remaining XP and its rank by total XP for a fresh profile. The top families are finished completely before the next one starts. Unfinished values stay `null` / `verified: false`. The phase report says where research stopped and lists every `verified: false` task and every row without an estimate. *(Addendum: Research 8, Data b2; REQ-XC-REPORT)*

*H. UI (Phase 2 style)*
- **REQ-SBXP-50** A "SkyBlock XP" config category must offer:
  - coins per hour: a number input with presets 1M / 5M / 15M / 40M / custom. It accepts `1000000`, `1,000,000`, `1.5m`, `500k` and `2b`. It visibly rejects 0, negative and non-numeric input and keeps the last valid value. Default 5,000,000. Global, with an optional per-profile value.
  - player stage (early / mid / late): per profile, default the suggested stage (REQ-SBXP-22)
  - access to per-task rate, time and coin overrides, rate-id overrides and item price overrides (REQ-SBXP-23), stored per profile
  - category filters (multi-select), global
  - include / exclude coin-only tasks, default include. Exclude removes coin-only options before option choice (REQ-SBXP-19).
  - include locked tasks, default ON: shows or hides the Locked section. Locked tasks are never ranked.
  - treat unknown prerequisites as locked, default OFF (REQ-SBXP-27)

  All defaults, scopes and filter rules in this list are [decided R14]. *(Addendum: UI Config)*
- **REQ-SBXP-51** The optimizer screen must show:
  - a sortable table with the columns rank, task, category, XP, time, cost, effective time, XP/h and confidence. A header click toggles between descending and ascending; the sort is stable, with task id as the tie-breaker.
  - a search box: case-insensitive substring match over name, category and id
  - the filters of REQ-SBXP-50
  - the views Ranked, Locked, Not yet estimated, Free / waiting and Plan to target

  It opens via `/ksu sbxp` and via a button on the config card. A keybind may be offered, unbound by default. *(Addendum: UI Optimizer screen)*
- **REQ-SBXP-52** Low-confidence values must be visibly marked in the table, with a text badge plus colour (never colour alone). Unverified XP and outdated overrides must also be marked. *(Addendum: Research 6)*
- **REQ-SBXP-53** Selecting a row should show its details:
  - the chosen option
  - effort lines, with assumptions and sources
  - E / P50 / P90 for drops
  - cost lines, with price source and date
  - unmet prerequisites, the task's cost class and the class of each option (REQ-SBXP-19)
  - options that need Gems, noted as not ranked (REQ-SBXP-20)
  - an override editor with "reset"

  *(Derived: Research 3 variance, Research 4 assumptions)*
- **REQ-SBXP-54** The optimizer screen must be usable in an 854×480 window at GUI scales 1–2 (the highest vanilla allows at that size) and in a 1920×1080 window at GUI scales 1–4, with no overlapping or clipped text. Below width thresholds, lower-priority columns may be hidden; their values then show in the row tooltip. *(Derived: GAP-1 width budget; ui-config layout rules)*
- **REQ-SBXP-55** An optional "next best task" HUD element must:
  - show the top ranked task's name, its XP/h and its confidence
  - be movable and scalable in the HUD editor
  - have its own toggle, default OFF
  - show only on SkyBlock and follow the HUD framework's hide rules
  - show "no estimate yet" when nothing is ranked

  *(Addendum: UI "Optional HUD element")*
- **REQ-SBXP-56** The optimizer must have its own module toggle. Defaults: module ON, passive menu reading ON, HUD OFF, live Bazaar OFF [decided D-6, D-22]; live deltas ON [decided R14]. *(Brief: ground rule 6; REQ-XC-TOGGLE)*

*I. Documentation*
- **REQ-SBXP-57** The README must document the optimizer: how to open it, its settings, views and planner, data sources and licences, the non-affiliation statement, and its limitations (greedy plan, no credit when one grind advances several tasks, gross costs, unverified and unestimated rows). It must also explain how maintainers run the update skill. CHANGELOG must have an entry for the feature. *(Addendum: Done when)*

**Out of scope**
- Any automation, for Hypixel rules (REQ-XC-RULES): opening, clicking, paging or sorting menus; sending commands; auto-buying, listing or bidding.
- Client-side Auction House scans, third-party lowest-BIN price APIs, any Hypixel API key or keyed endpoint, and reading AH screens for prices (a later option, D-20).
- A resale / net cost mode (D-19), capital limits, and modelling the Derpy tax.
- Crediting grinds that advance several tasks at once, optimal (non-greedy) planning, a "cautious" ranking mode based on P90, per-family stages, and calibrating personal rates from chat logs.
- Cost models specific to Ironman, Stranded or Bingo profiles, and merging progress across co-op members.
- Paid carry services as a way to buy XP with coins (D-29).
- Shipping wiki-derived data files and scripted wiki access without consent (D-16 c). The wiki-derived taxonomy draft from planning is not an input (D-25).
- Hypixel text in languages other than English.

**Acceptance criteria**
- **AC-SBXP-01** (REQ-SBXP-01, REQ-SBXP-04, REQ-SBXP-07) Given the shipped table, when `./gradlew check` runs, then it passes. Given one fixture per rule, check fails and names the offending id. The fixtures cover:
  - a schema violation
  - a dangling rate, source, task or prerequisite reference
  - a duplicate id after expansion
  - an effort unit that differs from its rate unit
  - a gap in step numbers
  - a prerequisite cycle
  - non-null XP backed only by wiki cross-checks
  - `verified: true` with one origin, or with only community wiki + Fandom
  - a `neu` or `skyhanni` source in the task table that is not a `table:id` reference into a `ref.*` table
  - a family maximum that differs from the recomputed maximum without a conflict entry
  - `§` or lore text in a string

  Given an unverified rate that cites one user-supplied wiki page, then check passes; the same rate with `verified: true` fails — [A]
- **AC-SBXP-02** (REQ-SBXP-02, REQ-SBXP-03) Given the expanded shipped table, when a test iterates over all tasks, then each has id, name, category, type, and verification with `lastVerifiedGameVersion`. Every non-null XP value has at least one source. Every null XP value has `verified: false` and never appears in Ranked or the planner — [A]
- **AC-SBXP-03** (REQ-SBXP-05, REQ-SBXP-06) Given rate R used by tasks A and B, when R for the selected stage changes from 1,200/h to 2,400/h (in data or by override), then the throughput time of both A and B halves without editing either task. The schema has no field on tasks for time, effective hours, XP/h, task-level confidence or quantiles; only rates carry their researched `confidence` (REQ-SBXP-05) — [A]
- **AC-SBXP-04** (REQ-SBXP-08) Given progress, a manual mark and an override on id X, when a data update maps X → Y in the alias table, then all three apply to Y after load. Given X removed, then its override is listed as outdated and nothing is deleted from the profile file — [A]
- **AC-SBXP-05** (REQ-SBXP-09) Given a captured leveling menu with a family item that has no data entry, when it is parsed, then Not yet estimated shows the family as "unlisted" with its earned/max XP, and the unmapped report contains it — [A]
- **AC-SBXP-06** (REQ-SBXP-10) Given recorded source fixtures, when the data build runs offline twice, then the outputs are byte-identical, and an online run equals the offline replay. Recomputed family maxima derived from the API match the recorded values (skills 8,710, collections 3,160, museum 3,647, minions 3,165) — [A]
- **AC-SBXP-07** (REQ-SBXP-11) Given throughput of 30,000 kills at 1,200 kills/h, then time = 25.0 h. Given fixed 6 min, then 0.1 h. Given both lines, then 25.1 h. Given a `/day` wait of 2 days, then play time is unchanged and the wall-clock wait = 48 h — [A]
- **AC-SBXP-08** (REQ-SBXP-11, REQ-SBXP-12) Given p = 0.00264 with pity 600, then E = 301.2 ± 0.1 attempts, P50 = 263 and P90 = 600; at 60 attempts/h, time = 5.02 h. Given p = 0.01 without pity, then E = 100, P50 = 69 and P90 = 230 — [A]
- **AC-SBXP-09** (REQ-SBXP-14) Given a task worth 100 XP that needs 2 h and 10,000,000 coins:
  - at 5,000,000 coins/h: effective = 4.0 h, 25.0 XP/h
  - at 20,000,000 coins/h: 2.5 h, 40.0 XP/h

  Given a coin-only fixture worth 24 XP that costs 2,490,000 coins:
  - at 1,000,000 coins/h: 2.49 h, 9.64 XP/h
  - at 40,000,000 coins/h: 0.06225 h, 385.5 XP/h

  — [A]
- **AC-SBXP-10** (REQ-SBXP-14) Given 10 XP, 1,000 coins and no effort at 40,000,000 coins/h, then effective = 1/60 h and efficiency = 600 XP/h. A property test over coins/h from 1 to 10^12 with random fixtures never yields NaN, Infinity or a negative value — [A]
- **AC-SBXP-11** (REQ-SBXP-13) Given an item line of 9 × 1,000 coins with `consumed: false`, then 9,000 coins are added (gross). Given 3 h on a rate with a running cost of 100,000 coins/h, then 300,000 coins are added — [A]
- **AC-SBXP-12** (REQ-SBXP-15) Given a task with no play time, no coins and a 48 h wait, then it appears only in Free / waiting, sorted by XP with the highest first, showing "48 h". The planner adds 48 h to wall-clock waiting and 0 to play time — [A]
- **AC-SBXP-13** (REQ-SBXP-16) Given, in turn, absent effort, a rate that is null in every stage, an item price that is null with no override, and null XP, then each task has "no estimate", sits only in Not yet estimated, and is counted in the planner's "left out" total. Given `effort: []` and `cost: []` with a wait, then the task is Free / waiting, not "no estimate" — [A]
- **AC-SBXP-14** (REQ-SBXP-17) Given the options "buy for 50,000,000 coins" and "grind 10 h", then at 2,000,000 coins/h grind is chosen (10 h < 25 h), and at 10,000,000 coins/h buy is chosen (5 h). The row details name the chosen option — [A]
- **AC-SBXP-15** (REQ-SBXP-18) Given group members worth 6 and 9 XP with the 6 done, then doing the 9 yields 3 XP. Given bestiary milestone XP derived from tier steps, then it is added to the steps that complete a milestone and is never ranked on its own. Given a decaying counter, then the units per step change at each band boundary — [A]
- **AC-SBXP-16** (REQ-SBXP-19) Given these options, the option class is:
  - no effort + coins: coin-only
  - fixed 5 min + item: coin-only
  - fixed 6 min + item: hybrid
  - throughput + coins: hybrid
  - throughput or drop, no cost: time-only
  - no effort, no cost, a wait: free / waiting
  - copper via rate only: time-only
  - essence via item only: coin-only
  - a coin-only option of a task whose prerequisite needs a grind: still coin-only

  Given a task with the options "buy for 50,000,000 coins" and "grind 10 h", then the task class is hybrid at both 2,000,000 and 10,000,000 coins/h. Given a task whose only options are two coin-only options, then the task is coin-only — [A]
- **AC-SBXP-17** (REQ-SBXP-20) Given a gems-only option, then it is never chosen or ranked, and the row details note it. Given gems vs a 48 h wait, then the wait is chosen. Gems never appear in coin totals. Given 100 essence at 500 coins each, then 50,000 coins. Given 1,000 copper at 500 copper/h, then 2 h of play time — [A]
- **AC-SBXP-18** (REQ-SBXP-21) Given verified XP, a high-confidence rate and a verified amount, then confidence is high. Given any medium component, then medium. Given a rate from a fallback stage, then low. Low is rendered with a "low" badge — [A], [C]
- **AC-SBXP-19** (REQ-SBXP-22) Given byStage {early 100, late 300}, then mid gives 100 with low confidence (the tie goes to the lower stage) and late gives 300. Given {all 200}, then 200 in every stage. Given all null, then no estimate. Given a profile first identified at a SkyBlock level inside the `mid` threshold band, then the stage is `mid`, labelled "suggested"; once the user picks a stage, the label is gone. Given an unknown level, then `mid`, labelled "suggested" — [A]
- **AC-SBXP-20** (REQ-SBXP-23)
  - Given a time override of 3 h, a task rate override, a rate-id override and a researched rate, then 3 h is used. Without the time override, the task rate override is used; without that, the rate-id override; without that, the researched rate.
  - Given a rate-id override on rate R used by tasks A and B, then both A and B use it.
  - Given a price override, a live price and a bundled price, then the override is used; without it, the live price; without that, the bundled price.
  - Given a data update that changes the overridden researched value, then the override is kept and flagged outdated. Given an update that leaves the value unchanged, then it is not flagged.
  - "Reset" restores the researched value.

  — [A]
- **AC-SBXP-21** (REQ-SBXP-24) Given any profile state and the shipped table, then in Ranked efficiency never increases down the list, ties are ordered by id, and no row is locked, done, unestimated or free / waiting — [A]
- **AC-SBXP-22** (REQ-SBXP-25) Given a ladder with steps 1–5 and steps 1–2 done, then exactly one row shows step 3. After step 3 is marked done, the row shows step 4 — [A]
- **AC-SBXP-23** (REQ-SBXP-26, REQ-SBXP-27)
  - Given "requires Farming 30" and Farming 25, then the task is in Locked with "Farming 30 needed". When Farming 30 is detected or set, it moves to Ranked.
  - Ladder step n stays locked until step n − 1 is done.
  - A chain A → B → C shows chain efficiency Σ xp / Σ effective h.
  - An unknown level shows the "?" badge, or the task is Locked when "treat unknown as locked" is on.
  - A null threshold shows "requirement unknown".

  — [A]
- **AC-SBXP-24** (REQ-SBXP-28, REQ-SBXP-29) Given a calendar task outside its window, then it stays listed with "next window in X h". Given 1,000 remaining XP of which 600 is estimated, then the header reads "ranking covers 600 of 1,000 remaining XP" — [A]
- **AC-SBXP-25** (REQ-SBXP-30) Given the optimizer open on Ranked, when the coins/h preset changes from 5M to 40M, then the next screenshot shows the reordered table with the same row still selected (by id) — [C]. A full recalculation of the shipped table takes at most 50 ms median over 20 warm runs on the developer machine (a performance test, not run in CI) — [B]
- **AC-SBXP-26** (REQ-SBXP-31) Given a 6-task fixture with hand-computed efficiencies, current XP 10,000 and target level 103, then the planner picks the tasks in the expected order. It unlocks and picks a dependent task after the pick that meets its prerequisite. Play time, coins, effective hours and waits are each within 0.01 of the expected totals. With coin-only excluded, no coin-only option is picked — [A]
- **AC-SBXP-27** (REQ-SBXP-32)
  - Given a target at or below the current level, then "already reached".
  - Given a target beyond all estimated XP, then the plan ends with "short by N XP".
  - Given a target above the known maximum, then a warning.
  - Given an unknown current level, then the planner asks for it.
  - Planning the shipped table up to its maximum finishes within 2 s, off the render thread.

  — [A]
- **AC-SBXP-28** (REQ-SBXP-33) Given sanitised captures of the leveling menus [decided D-17], when they are parsed, then earned/max XP, step states and completion match the capture. A recording network stub shows the parser and menu reader send no click, command or outgoing container packet, and a code review finds no call in this module that sends them — [A], [R]
- **AC-SBXP-29** (REQ-SBXP-34, REQ-SBXP-36) Given state for profiles A and B, when the tab-list profile changes from A to B, then B's state is shown within 5 s and A's is saved. Given no profile identified yet, then nothing is written. After a restart both states load unchanged — [A]. The user switches profiles in game and sees separate progress — [E]
- **AC-SBXP-30** (REQ-SBXP-35) Given a manual "done" at t1 and a snapshot at t2 > t1 that says not done, then the mark is cleared and one conflict note shows. Given a snapshot older than the mark, then the mark wins. A live delta never overrides a mark — [A]
- **AC-SBXP-31** (REQ-SBXP-37) Given cumulative XP that maps to exactly one tier, then that tier is set. Given an ambiguous total, then the tier is "unknown" with "set level". Given a parsed Skills menu with Farming 30, then "requires Farming 30" is met — [A], on sanitised captures [decided D-17, D-27]
- **AC-SBXP-32** (REQ-SBXP-38) Given the recorded log fixtures (702 action-bar events), then the deltas per task match the expected table. A gain echoed by two mods inside the dedupe window of REQ-SBXP-38 counts once. A message cancelled by another mod is still counted — [A]. In a production boot with Skyblocker and SkyHanni, action-bar XP events are recorded — [D]
- **AC-SBXP-33** (REQ-SBXP-39) Given a family never seen, then its row hint reads "open <menu> to refresh" as plain text. Given an unknown action-bar label, then it is stored once per profile with a count — [A]
- **AC-SBXP-34** (REQ-SBXP-40, REQ-SBXP-41, REQ-SBXP-42) Given a mock Bazaar server:
  - 200: Bazaar items use live prices, and Auction-House-only items keep bundled prices.
  - 304: the cache is kept.
  - 429, 503, a timeout, malformed JSON, or `lastUpdated` in the future: bundled or cached prices are used, with no chat line, toast or dialog, and the badge shows "bundled (date)" or "stale".
  - No second request is sent within 15 min.
  - No request is sent while the toggle is OFF or the screen is closed.
  - No request carries an API key.
  - Buy-order mode uses the buy-order price.

  — [A]
- **AC-SBXP-35** (REQ-SBXP-43) Given the runtime code, then the only network host it contacts for prices is the keyless Bazaar endpoint (allowlist test plus review). A forbidden-key scan finds no player UUIDs or listing data in the bundled price file — [A], [R]
- **AC-SBXP-36** (REQ-SBXP-44, REQ-SBXP-52, REQ-SBXP-53) Screenshots show the non-affiliation line, a low-confidence badge, an unverified-XP marker, an outdated-override marker, and row details with the chosen option, E / P90 and sources — [C]
- **AC-SBXP-37** (REQ-SBXP-45–REQ-SBXP-49) Given the research batches:
  - The validator is green.
  - `docs/sbxp-research.md` has an entry for every family and rate id, with sources, rates found, reconciliation and final assumption.
  - A script confirms every task id resolves to an entry.
  - The phase report names where research stopped and lists every `verified: false` task and every row without an estimate.

  — [A], [R]
- **AC-SBXP-38** (REQ-SBXP-50) Given the coins/h input, then `1,000,000` → 1,000,000, `1.5m` → 1,500,000, `500k` → 500,000 and `2b` → 2,000,000,000. `0`, `-5`, `abc` and an empty field are shown as invalid, and the previous value stays active — [A]. Preset chips, the stage dropdown and filters work in a gametest — [C]
- **AC-SBXP-39** (REQ-SBXP-51, REQ-SBXP-54) Given the table:
  - All 9 column headers are present.
  - Clicking XP/h twice toggles between descending and ascending, and the order stays stable.
  - Searching "minion" keeps only rows whose name, category or id contains it (case-insensitive).
  - Each view lists the expected fixture rows.
  - Screenshots at 854×480 at GUI scales 1–2 and at 1920×1080 at GUI scales 1–4 show no overlapping or clipped text.
  - A 3,000-row list scrolls.

  — [A], [C]
- **AC-SBXP-40** (REQ-SBXP-55) Given the HUD enabled, then the HUD editor can move and scale it, and its preview shows a sample task with XP/h. In game it shows the top ranked task and changes after that task completes — [C], [E]
- **AC-SBXP-41** (REQ-SBXP-56, REQ-SBXP-50) Given a fresh config, then the module is ON, passive reading ON, live deltas ON, HUD OFF and live Bazaar OFF, and each has its own toggle. Coins/h is 5,000,000, coin-only tasks are included, the Locked section is shown and "treat unknown as locked" is OFF — [A]
- **AC-SBXP-42** (REQ-SBXP-57) README and CHANGELOG contain the sections REQ-SBXP-57 lists, and their claims match the shipped defaults — [R]
- **AC-SBXP-43 (Done when: fresh profile)** (REQ-SBXP-24, REQ-SBXP-27, REQ-SBXP-31) Given an all-zero synthetic profile and the shipped table at a fixed coins/h and stage, then the Ranked top 20 equals the committed golden table `docs/sbxp/golden-fresh.md`, and the AC-SBXP-21 invariants hold — [A]. The golden table is hand-computed, not produced by the code under test, and lists each task's XP, time, cost, effective hours and efficiency. The user reviews it at G6 — [E] [decided R14]
- **AC-SBXP-44 (Done when: progressed profile)** (REQ-SBXP-24, REQ-SBXP-33, REQ-SBXP-37) Given a progressed profile built from the detector's output on the user's sanitised captures [decided D-17], then no completed task is ranked, the top 20 equals the golden list, and Σ category earned XP equals the captured total — [A]. The user judges the top 20 plausible in game at G6 — [E] [decided R14]
- **AC-SBXP-45 (Done when: recalculation)** (REQ-SBXP-30) Given the golden fixtures, when coins/h changes from 1M to 40M and the stage changes from early to late, then the order changes exactly as precomputed: coin-heavy tasks move up as coins/h rises, and tasks with stage-dependent rates swap places — [A]. The change is visible without reopening the screen — [C]
- **AC-SBXP-46** (REQ-SBXP-17, REQ-SBXP-19, REQ-SBXP-50) Given a task with the options "buy for 50,000,000 coins" and "grind 10 h" at 10,000,000 coins/h:
  - With coin-only excluded, the task stays ranked with grind chosen (10 h).
  - With the filter off, buy is chosen (5 h).

  Given a task whose only estimated option is coin-only, then with coin-only excluded it is neither ranked nor planned, and it returns when the filter is turned off — [A]
- **AC-SBXP-47** (REQ-SBXP-19) Given the shipped table, then a script confirms that the coin-only / hybrid list in `docs/sbxp-research.md` equals the classification computed from the data, family by family, with the class of each option. The G6 report contains the same list — [A], [R]
- **AC-SBXP-48** (REQ-SBXP-58) Given the session history and the git log, then the user's approval of the aligned `docs/sbxp/tasks.schema.json` comes before the first T6.1 commit. The approved schema has the REQ-DATA-02 envelope and the REQ-SBXP-04 source rules — [R]

**Edge cases**
- **EC-SBXP-01** Coins/h is set to 10,000,000,000. → Coin terms are close to 0, coin-only rows hit the 1-minute floor, no Infinity appears, and the ranking stays stable by id.
- **EC-SBXP-02** A rate exists only for `late` while the stage is `early`. → The late value is used with low confidence and a badge.
- **EC-SBXP-03** A Bazaar product has no sell offers or is missing from the live response. → The bundled price is used. If the bundled price is null and there is no override, the task has no estimate.
- **EC-SBXP-04** A live fetch hits offline, 429/503, a timeout, a gzip error or `success: false`. → Silent fallback, back-off up to 60 min, no chat spam. Opening the screen retries only after the back-off ends.
- **EC-SBXP-05** A menu opens while Hypixel is still filling in lore placeholders. → It is parsed only once the contents are stable; placeholder lore never marks a task done.
- **EC-SBXP-06** Paginated sub-menus share one truncated title (about 31 characters). → Pages are merged by item identity or the overview item, never by title.
- **EC-SBXP-07** The tab-list Profile widget is disabled and another mod hides the profile chat lines. → Nothing is written, and one hint names the Profile widget using the shared widget-hint string (REQ-GS-18; its wording is checked at G3, R24).
- **EC-SBXP-08** Two accounts each have a profile with the same fruit name. → State is kept separate, keyed by player UUID (game-state REQ-GS-09). If a profile is deleted and recreated with the same name, its profile id changes and game-state archives the old state (REQ-GS-09). A manual reset from the optimizer is an extra option, not the mechanism.
- **EC-SBXP-09** The Rift reverses the profile name. → Handled by game-state (EC-GS-10): the reversed name is ignored and the last identified profile is kept.
- **EC-SBXP-10** On a co-op profile: → state comes from the current member's menus only and is never summed across members. Families that may be co-op-shared are marked "may be shared".
- **EC-SBXP-11** The player disconnects or changes world while a menu is open. → The partial observation is discarded unless the contents had already stabilised.
- **EC-SBXP-12** The task table is missing, unlisted or corrupt at load. → The optimizer is disabled with one message, and the rest of the mod keeps working.
- **EC-SBXP-13** The per-profile state file is corrupt. → It is backed up, a fresh state starts, and one notice shows. It is never silently overwritten.
- **EC-SBXP-14** A data update renames or removes a task. → Aliases carry the state over; overrides of removed tasks are listed as outdated.
- **EC-SBXP-15** A drop chance is tiny (1e-6) with no pity. → E = 1,000,000 attempts and stays finite; P90 is computed with logarithms, without overflow. The schema rejects p = 0 and p > 1.
- **EC-SBXP-16** The target level is unreachable with estimated tasks, or above the maximum (known or unknown). → Handled as REQ-SBXP-32 describes.
- **EC-SBXP-17** The GUI scale or window size changes while the optimizer is open. → The layout is recomputed, the selection is kept, and nothing is clipped.
- **EC-SBXP-18** A search has no matches. → "No matching tasks" shows instead of an empty table.
- **EC-SBXP-19** The HUD is enabled while the player is not on SkyBlock or no profile is identified. → The HUD is hidden. If nothing is ranked, it shows "no estimate yet".
- **EC-SBXP-20** The profile is Ironman or Stranded (no Bazaar or AH access). → One notice says coin costs assume Bazaar/AH access; there is no separate model.
- **EC-SBXP-21** An event task is outside its window, or mayor-gated. → It stays listed with "next window in X" or "wait unknown".
- **EC-SBXP-22** Hypixel text is not in English. → Parsers match nothing, labels go to the unmapped report, one hint shows, and no wrong marks are set.
- **EC-SBXP-23** An override was made against dataVersion 5, and the data is now at 7 with the same researched value. → It is not flagged.
- **EC-SBXP-24** An action-bar line and a chat reward line report the same gain inside the dedupe window (REQ-SBXP-38). → It is counted once.
- **EC-SBXP-25** A snapshot shows a family at its maximum while one instance is manually marked "not done". → The snapshot wins (everything done) and a note shows.

**Open questions**
- **Q-SBXP-01** Data sources for XP and rates, and the deviation from "official wiki" → decided D-16 (a: the wiki is a human cross-check only; overrides the addendum, see REQ-SBXP-04)
- **Q-SBXP-02** Access to wiki, forum and reddit rate sources → decided D-28 (i: only pages the user pastes or saves)
- **Q-SBXP-03** Research priority order → decided D-18 (c: better rank of remaining XP and fresh-profile XP)
- **Q-SBXP-04** `/sblevels` and component menu captures → decided D-17, timing changed by you on 2026-10-01: every capture (family level, component menus, optional step walk) happens at G1
- **Q-SBXP-05** Gross vs net cost model → decided D-19 (gross in v1; resale mode later)
- **Q-SBXP-06** Auction House price proposal → decided D-20 (bundled reference prices from one keyless scan per data update, plus overrides)
- **Q-SBXP-07** Live Bazaar defaults → decided D-22 (opt-in, default OFF, ≤ 1 request per 15 min, instant-buy by default)
- **Q-SBXP-08** Row granularity → decided D-21 (next step per instance)
- **Q-SBXP-09** Component menu parsers → decided D-27 (a: read-only parsers, tier inference, manual "set level")
- **Q-SBXP-10** Community Shop and carry services → decided D-29 (free / waiting group; carry services excluded)
- **Q-SBXP-11** Maximum level → decided D-24 (data-driven, "unknown" until captured)
- **Q-SBXP-12** Meaning of "internal database from Phase 3.9" → decided D-26 (the shared data layer, T3.0c)
- **Q-SBXP-13** Which tasks count as coin-only, and what the coin-only filter removes → decided R14 (class per option; the filter removes coin-only options before option choice)
- **Q-SBXP-14** Real-money currencies (Gems) → decided R14 (never converted; gem-only options never ranked, noted in details)
- **Q-SBXP-15** Tasks with unknown prerequisite state → decided R14 ("?" badge; "treat unknown as locked" setting)
- **Q-SBXP-16** Meaning of "include locked tasks" → decided R14 (shows or hides the Locked section)
- **Q-SBXP-17** Override scope (per task vs per rate id) → decided R14 (per rate id and per task, task wins; per-item prices)
- **Q-SBXP-18** Which settings are per profile and which are global → decided R14 (stage and overrides per profile; coins/h global with an optional per-profile value; filters global)
- **Q-SBXP-19** Defaults for coins/h and stage → decided R14 (5,000,000 coins/h; stage suggested from the SkyBlock level)
- **Q-SBXP-20** Live progress deltas in v1 → decided R14 (action bar and chat reward lines, 3 s dedupe window)
- **Q-SBXP-21** What counts as "ranks correctly" → decided R14 (tests, `docs/sbxp/golden-fresh.md`, the user's top-20 review at G6)
- **Q-SBXP-22** Optimizer defaults → decided D-6 (module and passive reading ON, HUD OFF, live Bazaar OFF)

---

### sbxp-update-skill — Update skill for the SkyBlock XP table

**Origin:** Addendum "Claude Code skill: updating the table after SkyBlock updates"; Addendum Done when | **Depends on:** sbxp-optimizer (task table, schema, validator, data build, research log) | **Plan tasks:** T6.2, T6.2e, T6.2c, T6.2d, T6.10, T6.10b (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** A project skill the maintainer runs after every SkyBlock patch. It updates the task table, rates, research log and changelog with cited sources, validates and builds them, and delivers the result on a data branch with a PR, without ever guessing values.

**Functional requirements**
- **REQ-SKILL-01** The skill must live at `.claude/skills/update-sbxp-table/SKILL.md` and follow the Agent Skills format: YAML frontmatter with `name` equal to the directory name and a `description`, a body under 500 lines, and supporting scripts and references in the same directory. The only deviation from the portable format is the Claude Code key `disable-model-invocation: true` (REQ-SKILL-02). It is developed with Anthropic's skill-creator skill; its formal eval loop runs in a local Python venv (the Python in `~/.local/bin`, PyYAML, the `claude` CLI) [decided D-23]. *(Addendum: skill intro; Derived: D-23)*
- **REQ-SKILL-02** The skill must run only when the user invokes it (`/update-sbxp-table`), never triggered automatically by the model, because it writes data and git state. Its frontmatter sets `disable-model-invocation: true` for this [decided D-23]. *(Derived: a workflow that changes data; D-23)*
- **REQ-SKILL-03** Step 1: the skill asks the user for the latest patch notes and update post, pasted or saved to a file. It never fetches hypixel.net [decided D-23]. It determines the new `gameVersion` from them and compares it with the data file's `gameVersion`, numerically per component. If the version is not newer and no upstream data source has changed, it stops with "up to date" and changes nothing. If the user supplies no patch notes, the skill runs on data-source diffs only (API version, NEU pin, captures) and labels the report "no patch notes supplied" [decided R15]. *(Addendum: skill step 1; decided D-23: the user supplies the notes, which replaces the addendum's "or find")*
- **REQ-SKILL-04** Step 2: the skill lists added, removed and changed SkyBlock XP sources, rewards, caps and prerequisites:
  - It diffs the pinned upstream data (Hypixel API resources, NEU MIT files) and the supplied notes, citing one source per change.
  - Wiki pages are cross-checks only [decided D-16 (a)].
  - Removed tasks are kept, marked as removed with evidence, and never silently deleted.
  - Rename candidates are proposed as aliases, and the user confirms them.
  - Numbers claimed in patch notes are recorded as claims and applied only with a supporting source.

  *(Addendum: skill step 2)*
- **REQ-SKILL-05** Step 3: the skill re-checks the rates of every grind the patch affects (buffs or nerfs to mobs, drops, items or gear), following REQ-SBXP-45–REQ-SBXP-47. Affected times follow automatically from the changed rates. The report lists each affected rate id as "re-check needed", with the recalculated times of every task that uses it. *(Addendum: skill step 3)*
- **REQ-SKILL-06** Step 4: the skill updates the task table, bumps `dataVersion` by exactly 1, sets `gameVersion`, and validates the result against the schema and the validator (REQ-SBXP-07). It also updates `docs/sbxp-research.md` for every changed task and rate. *(Addendum: skill step 4)*
- **REQ-SKILL-07** Step 5: the skill runs the unit tests and `./gradlew build`. Any failure stops the run before a commit and reports the failing check. *(Addendum: skill step 5; REQ-XC-BUILD)*
- **REQ-SKILL-08** Step 6: the skill writes a CHANGELOG entry listing every changed task id and rate id, with old → new value and a source link. Anything uncertain becomes `verified: false` (and `null` where no acceptable source exists) and is never guessed. *(Addendum: skill step 6)*
- **REQ-SKILL-09** Step 7: the skill commits on branch `data/sbxp-<gameVersion>`, or on `data/sbxp-<gameVersion>-d<dataVersion>` when the first name already exists [decided R15], and opens a PR to the default branch. It needs an explicit user approval before the commit and a second one before the push and PR [decided D-23]. It never pushes to `main`, never force-pushes and never merges. *(Addendum: skill step 7; gates Derived: D-10)*
- **REQ-SKILL-10** A dry-run mode runs every step end to end on the current inputs or on recorded fixtures and prints the full report: changes with sources, flags, proposed `dataVersion` / `gameVersion`, branch name, changelog draft, and validation and build results. It applies its changes in a temporary detached git worktree outside the repository, runs the validator and `./gradlew build` there, then deletes the worktree. It leaves the repository's tracked files, branches and commits unchanged. *(Addendum: Done when "runs end-to-end on a dry run")*
- **REQ-SKILL-11** Scripts and the data build extract all numbers; the model never takes numbers from its own summaries of pages. The data tool is written in Java, so CI needs only the JDK [decided D-23]. A run replayed from recorded fixtures must produce byte-identical output. *(Derived: WebFetch misreads found in GAP-5; Addendum "never invent XP values")*
- **REQ-SKILL-12** For changed families that have no API or NEU value, the skill asks the user for a `/sblevels` capture of those menus. Without one, those values become `null` / `verified: false` and are listed in the report. *(Derived: D-16, D-17)*
- **REQ-SKILL-13** The skill must not commit wiki or forum page content, player names, UUIDs or other personal data. Committed fixtures are synthetic CC0 data or permitted third-party data with notices. If a pinned upstream file changes licence (e.g. to a NonCommercial licence), the run is blocked. *(Derived: ground rule 4, REQ-XC-LICENSE; GAP-4)*
- **REQ-SKILL-14** Refreshing prices is optional, and the skill must ask before the roughly 58 MB Auction House scan. Prices are versioned separately from XP data [decided D-20]. *(Derived: D-20; Addendum Prices)*
- **REQ-SKILL-15** Before any write, the skill checks its preconditions: a clean working tree, a present and valid data file, and a target branch name that does not exist yet after the naming rule of REQ-SKILL-09 (`-d<dataVersion>` appended when `data/sbxp-<gameVersion>` exists) [decided R15]. If a check fails, it stops and explains why. *(Derived: safety, idempotency)*

**Out of scope**
- Scheduled or automatic runs, and detecting patches by crawling hypixel.net or the wiki (Hypixel ToS, Weird Gloop terms).
- Scripted wiki access without consent (D-16 b), and WebFetch as a source of numbers.
- Merging the PR, pushing to `main` and releasing.
- Changing Java code or the schema itself; a schema change is a normal development task.

**Acceptance criteria**
- **AC-SKILL-01** (REQ-SKILL-01, REQ-SKILL-02) Given the skill directory, then the frontmatter parses, `name` equals `update-sbxp-table`, the description is present and the body is under 500 lines. Run on the committed skill, the skill-creator `quick_validate` reports only the `disable-model-invocation` key. On a copy with that single line removed, it passes, and a diff shows that line is the only difference [decided D-23]. The skill does not run unless `/update-sbxp-table` is invoked — [A], [R]
- **AC-SKILL-02** (REQ-SKILL-03–REQ-SKILL-08, REQ-SKILL-10) Given recorded fixtures (a previous data lock, synthetic patch notes, recorded API/NEU responses) with:
  - one added, one changed, one removed and one renamed family
  - a patch note that nerfs a mob whose rate `rate.X` is used by tasks A and B

  when the skill runs in dry-run mode, then the report lists exactly those four family changes, each with a source, and lists `rate.X` as "re-check needed" with the recalculated times of A and B. It also shows `dataVersion` + 1, the new `gameVersion`, the branch name, a changelog draft, and the validator and `./gradlew build` results from the temporary worktree. Afterwards `git status --porcelain` is empty, `git branch --list "data/sbxp-*"` is unchanged and `git worktree list` shows no extra worktree — [R] (run in Claude Code); scripts [A]
- **AC-SKILL-03** (REQ-SKILL-03) Given the same `gameVersion` and unchanged upstream data, then the skill reports "up to date" and writes nothing. Given no patch notes and a changed NEU pin, then the report is labelled "no patch notes supplied" and lists the data-source diffs only [decided R15] — [R]
- **AC-SKILL-04** (REQ-SKILL-06, REQ-SKILL-07, REQ-SKILL-09, REQ-SKILL-15) Given a malformed upstream fixture, a schema violation, a failing unit test, a dirty working tree or an existing `data/sbxp-<gameVersion>-d<dataVersion>` branch, then the run stops before any commit and names the failed check. Given an existing `data/sbxp-<gameVersion>` only, then the target name is `data/sbxp-<gameVersion>-d<dataVersion>` and the run continues [decided R15] — [R], [A]
- **AC-SKILL-05** (REQ-SKILL-04, REQ-SKILL-08, REQ-SKILL-12) Given a patch-note claim "X now grants 80 SkyBlock XP" with no supporting source, then X keeps its value, stays `verified: false`, is flagged "patch-note claim" and appears in the report. Given a changed family with no API/NEU value and no capture, then its value is `null` / `verified: false`. A script check confirms every changed id appears in the changelog draft — [A]
- **AC-SKILL-06** (REQ-SKILL-11) Given the same fixtures, two runs produce byte-identical outputs — [A]
- **AC-SKILL-07** (REQ-SKILL-09) Given a real run on a local branch with the user's OK, then exactly one commit lands on the branch REQ-SKILL-09 names and nothing is pushed before the second approval. After that approval, a PR targets the default branch — [E], [R]
- **AC-SKILL-08** (REQ-SKILL-13) Given the committed fixtures and data, a scan finds no UUID patterns, no forbidden keys (auctioneer, profile_id, coop, bidder, item_bytes, lore) and no file under a NonCommercial licence — [A]
- **AC-SKILL-09** (REQ-SKILL-14) Given a run without explicit approval for the AH scan, then no auctions request is made and prices stay unchanged. A dry run never downloads auctions — [R]

**Edge cases**
- **EC-SKILL-01** The patch notes contain no SkyBlock XP numbers (the normal case). → Changes come from the data diff and captures; the notes only mark which families to re-check.
- **EC-SKILL-02** The user supplies no patch notes. → The skill runs on data-source diffs only (API version, NEU pin, captures), and the report is labelled "no patch notes supplied" [decided R15].
- **EC-SKILL-03** The data changed but `gameVersion` did not (e.g. a NEU fix). → If `data/sbxp-<gameVersion>` already exists, the branch is `data/sbxp-<gameVersion>-d<dataVersion>` (REQ-SKILL-09) [decided R15].
- **EC-SKILL-04** The network is unavailable. → The skill replays offline from recorded fixtures only, or stops; it never writes partially.
- **EC-SKILL-05** `gh` is not authenticated, or there is no remote. → The commit stays local, the report says so, and nothing else changes.
- **EC-SKILL-06** A NEU pin bump changes a file's licence or removes a file. → The run is blocked and names the file.
- **EC-SKILL-07** A rename candidate appears (a removal plus an addition with identical shape). → The user confirms it: yes adds an alias, no keeps it as a removal plus an addition.
- **EC-SKILL-08** The skill runs on Windows Git Bash. → Scripts work (POSIX shell, no PowerShell-only syntax), and paths with spaces are handled.
- **EC-SKILL-09** The user declines gate 1. → No files are written; only the dry-run report remains. If the user declines gate 2, the commit stays local.
- **EC-SKILL-10** The notes describe a release candidate (e.g. 0.27.2 RC). → It is recorded as a `release_candidate` change, and live values stay unchanged until the live version.

**Open questions**
- **Q-SKILL-01** Branch name: the addendum's `data/sbxp-<gameVersion>` vs the PLAN's `-d<dataVersion>` suffix → decided R15 (`data/sbxp-<gameVersion>`, `-d<dataVersion>` appended only if that branch exists)
- **Q-SKILL-02** Skill design: patch-note source, frontmatter, gates and eval loop → decided D-23 (user-supplied notes, `disable-model-invocation: true`, Java data tool, two gates, eval loop in a local venv)
- **Q-SKILL-03** Behaviour when no patch notes are supplied → decided R15 (run on data-source diffs, labelled "no patch notes supplied")

---

### release — Repository & release

**Origin:** Brief Phase 6; Brief ground rules 2–4; Addendum "Done when" (README and CHANGELOG) | **Depends on:** all modules; in particular updater (format contract), port-26-2, ui-config, sbxp-optimizer | **Plan tasks:** T0.5, T1.14, T1.15, T1.16, T1.17, T1.18, T1.19, T4.4b, T6.11, T7.1, T7.1b, T7.2, T7.2b, T7.2c, T7.3, T7.4, T7.4b; checkpoint G7 (generated from the PLAN.md `Req:` lines; per-requirement mapping in §13.1)

**Purpose:** Ship the 26.2 work as a documented, correctly versioned GitHub release, merged into `main` only after the user's approval. The release must be in exactly the format the updater can find, verify and install, and must stay that way for later releases.

**Functional requirements**
- **REQ-REL-01** The README must contain:
  - **Supported version:** "Minecraft 26.2 (Fabric)", with the requirements: Java 25, the minimum Fabric Loader, Fabric API, and the Hypixel Mod API mod. It also states that 26.1.x is no longer supported and that 1.0.1 was the last 26.1 build.
  - **Feature list:** every shipped feature with a one-line description, its config category and its default (ON/OFF).
  - **Screenshot placeholders:** one per user-visible feature (config screen, HUD editor, each HUD, waypoints and beams, mineshaft alert, hotspot ring, optimizer screen). None may render as a broken image on GitHub: use either a committed placeholder image or a visible text marker.
  - **Commands:** every user-facing command.
  - **SkyBlock XP Optimizer:** how to use it, its data sources and its limitations.

  *(Brief: Phase 6 bullet 1; Addendum: Done when)*
- **REQ-REL-02** Every claim in the README and in `fabric.mod.json` must match the shipped behaviour and defaults.
  - The stale "Modrinth-based self-updater (off by default)" README text is replaced.
  - So is the `fabric.mod.json` description "self-updating via Modrinth".
  - Neither file uses any word banned by policy P7 (word list in REQ-XC-RULES).

  *(Brief: Phase 6; Derived: documentation drift at README.md:16 and fabric.mod.json:6; REQ-XC-RULES P7)*
- **REQ-REL-03** README disclosures:
  - **Compliance section:**
    - client-side and display-only
    - policies P1–P7 in plain words
    - the behaviours deliberately not implemented: a glow option for mobs out of line of sight, showing invisible entities, the packet-suppressing drill fix, automatic party chat, particle-packet cancelling, and "filtering possible corpse spots by entities you cannot see" (this line stays after R22 dropped the corpse spots)
  - **Non-affiliation:** the statement that the mod is not affiliated with or endorsed by Hypixel.
  - **Network access:** every host the mod contacts, when, and the toggle that stops it:
    - `api.github.com` and the GitHub download hosts, for updates
    - `api.hypixel.net`, for Bazaar prices, only if opted in
  - **Licensing:**
    - the repo is CC0-1.0
    - the bundled third-party data files are MIT (not CC0), listed in `THIRD_PARTY_NOTICES.md`
    - Render Chest is Apache-2.0
    - courtesy credits for the reference mods

  *(Brief: ground rules 4 and 5; Derived: Hypixel API policy of 2026-09-30 requires the non-affiliation statement; REQ-XC-LICENSE)*
- **REQ-REL-04** Upgrade notes, in the README and in the release notes:
  - Users of 1.0.x must install the new jar by hand once, because their updater asks a Modrinth project that returns 404. Users of 1.1.0 get a chat line about the new release, but its check is notify-only, so they too install by hand once (REQ-UPD-01) [decided R4].
  - Settings migrate automatically, with a backup.
  - The behaviour changes:
    - glow is visible-only
    - invisible mobs are never highlighted
    - "You found X" appears only after line of sight, and only for Trinity, Tomioka, Duncan, Xalx and Pete [decided R20]
    - NPC waypoint labels are white by default; "White waypoint labels" OFF restores each NPC's colour [decided R21]
    - Catacombs and Dungeon Hub are split
    - Cloth Config is no longer required
    - the updater now notifies and asks for confirmation
  - How updates are installed: one click is required, and some cases are notify-only.

  *(Brief: Phase 6; Derived: research §8 migration note; G3 — the behaviour change must be called out)*
- **REQ-REL-05** `CHANGELOG.md` follows Keep a Changelog:
  - an `## [Unreleased]` section during development
  - one `## [x.y.z] - YYYY-MM-DD` section per release, using only Added / Changed / Deprecated / Removed / Fixed / Security
  - behaviour changes marked as such
  - every default listed (policy P6)
  - the optimizer listed
  - SBXP data-table changes, as written by the update skill, recorded in the same file

  Each release section is self-contained, including requirements and the upgrade note, so it can serve as the release notes word for word. *(Brief: Phase 6; Addendum: Done when, and skill step 6)*
- **REQ-REL-06** Versioning:
  - SemVer `MAJOR.MINOR.PATCH[-pre]` plus build metadata `+<mc>`. The final release is **2.0.0** [decided D-9].
  - One version string appears in all of these places:
    - the Gradle mod version
    - `fabric.mod.json` `version` (`2.0.0+26.2`)
    - the jar name
    - the tag (`v2.0.0`, no build metadata)
    - the CHANGELOG heading
    - the release title
  - `depends.minecraft` is `~26.2`.
  - A data-only update, such as an SBXP table PR from the update skill, ships as a PATCH release.

  *(Brief: Phase 6 "semantic version bump"; Derived: AD-10; AD-7, bundled data reaches users only through releases)*
- **REQ-REL-07** Release format contract. This is normative, and the updater (REQ-UPD-06, REQ-UPD-14) relies on it:
  - **Tag and title:** tag `v<version>`, with no `+`; title `K8bas Skyblock Utility v<version>`.
  - **Jar:** exactly one per supported MC version, named `k8bas_skyblock_utility-<version>+<mc>.jar` and matching `^k8bas_skyblock_utility-(?<ver>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?)\+(?<mc>\d+\.\d+(?:\.\d+)?)\.jar$`. No sources jar or other `.jar` is attached.
  - **Sidecar:** `<jar name>.sha256` containing `<64 lowercase hex><two spaces><jar name>\n`, UTF-8 without a BOM.
  - **Pre-release flag:** the GitHub `prerelease` flag is set if, and only if, the version has a pre-release part.
  - **Jar metadata:** `fabric.mod.json` `version` equals `<version>+<mc>`, and `depends.minecraft` accepts `<mc>`.
  - **Assets:** `state: uploaded`, and the jar has a non-null `digest`.

  *(Brief: Phase 6 "Make sure the release format matches what the new auto-updater expects")*
- **REQ-REL-08** Pull request from `update/26.2` to `main`.
  - **The body:**
    - summarises all changes since v1.0.1 (`main` @ `bc0f2f6`), per phase, marking those already released in v1.1.0 [decided R4]
    - gives the test evidence per tier (A–E results, linked only where safe to make public) and the field-check results (pass/fail)
    - lists known issues
    - lists the decisions taken: D-1 to D-29 (approved by the user on 2026-10-01), the answers to §12 R1–R17, and any later change to them
  - **CI:** the `build` workflow is green on the PR head.
  - **Personal data:** the diff and the body contain none (emails other than noreply, local paths, player names, UUIDs).

  *(Brief: Phase 6 bullet 2; ground rule 3; Derived: D-10 and the personal-data rule of T0.1)*
- **REQ-REL-09** Merging and pushing:
  - **Approval:** merge only after the user's explicit approval: a message from the user in the session, or an approving comment from the repo owner on the PR. (GitHub does not let a PR's author approve it, and the PR is opened under the owner's account.) Messages from agents or workflows never count. Any commit added after the approval voids it.
  - **Scope of the approval:** it covers the merge only. Pushing the tag and publishing need the separate "ship" of REQ-REL-10 [decided R4].
  - **Merge method:** a merge commit [decided D-10].
  - **Protected history:** never push directly to `main`; never force-push `main` or a published tag.
  - **Branch pushes:** `update/26.2` is pushed to `origin` only at checkpoints, so CI runs. D-10 is the user's OK for these pushes; any other push needs a new OK [decided D-10].
  - **Identity:** commits use the repo-local noreply identity `Kesuhi <110562470+Kesuhi@users.noreply.github.com>` [decided D-10].

  *(Brief: Phase 6 "After I approve, merge"; ground rule 2)*
- **REQ-REL-10** Tag and publish. Nothing in this list starts before the merge **and** the user's explicit "ship", a separate message after the PR approval [decided R4]. Then, in this order:
  1. Create an annotated tag `v<version>` on the merge commit on `main`.
  2. Build the jar from exactly that commit with a clean working tree (`./gradlew clean build` green).
  3. Push the tag.
  4. Create a **draft** with `gh release create v<version> --verify-tag --draft --notes-file <the CHANGELOG section>`, attaching the jar and the sidecar.
  5. Run the REQ-REL-11 checks on the draft.
  6. Publish (draft → public, marked latest).

  A failed REQ-REL-11 check stops before step 6 and is reported to the user. After a fix in the draft (EC-REL-05), all REQ-REL-11 checks run again.

  *(Brief: Phase 6 "create a git tag and a GitHub release (via `gh release create`) with the changelog as release notes and the built jar attached")*
- **REQ-REL-11** Checks on the draft before publishing:
  - The draft's release JSON, fetched with authentication because drafts are invisible to anonymous clients, passes the updater's own selection, digest and jar-validation logic.
  - Each asset's API `digest` equals the local SHA-256 and the sidecar.
  - The release body equals the CHANGELOG section.
  - The exact jar passes a production boot with the user's 26.2 mod set (tier D), with no new ERROR lines against the baseline.

  *(Brief: Phase 6 "release format matches"; ground rule 3; Derived: PLAN §5 lists tier D for the release)*
- **REQ-REL-12** End-to-end check after publishing: a test install runs a lower version that already contains the new updater, for example a locally built, never-published `2.0.0-test.1+26.2` in the test copy. It must find the published release, install it after one click, and boot it. *(Brief: Phase 6 "matches what the new auto-updater expects"; Derived: 1.0.x has no working updater and 1.1.0's check is notify-only (REQ-UPD-01), so neither can install the release — see contradictions)*
- **REQ-REL-13** Immutable releases [decided R4]:
  - They are enabled on the repo before v2.0.0, the first release the new updater installs from, is published. The API call runs only with the user's OK.
  - After that, a defect in a published asset is fixed with a new PATCH release, never by replacing assets or moving tags.

  *(Derived: research §9 — the digest proves integrity only at upload time; immutable releases stop later changes)*
- **REQ-REL-14** Early port release [decided D-13]:
  - After G1 passes, publish `v1.1.0` (`k8bas_skyblock_utility-1.1.0+26.2.jar`) with the port and the compliance fixes, as a normal release (not a pre-release).
  - It follows REQ-REL-05 to REQ-REL-11, including the format contract.
  - **Git flow:** an intermediate PR from `update/26.2` to `main` at G1, merged after the user's approval, with the tag on `main`. Approval, "ship" and order are the same as for 2.0.0 (REQ-REL-08 to REQ-REL-10) [decided R4].
  - Its README and CHANGELOG state at least: 26.2 support, the behaviour changes and the upgrade note.
  - Its update behaviour follows REQ-UPD-01: a notify-only GitHub check shown as a chat line, with no toast yet [decided R4].

  *(Derived: PLAN D-13; risk "users keep running rule-breaking 1.0.1")*
- **REQ-REL-15** Warning on the old releases [decided D-13]:
  - The notes of **v1.0.0 and v1.0.1** get a warning at the top. D-13 names v1.0.1; v1.0.0 is added because it has the same glow [decided R4].
  - The user approves the exact wording before either edit is made, because editing public release notes is an ask-first action [decided R4].
  - The warning says these versions outline mobs that are out of line of sight and outline invisible mobs, which Hypixel's rules forbid. It tells the reader to disable Mob Highlighter and NPC Search or move to the newest 26.2 release.
  - Their assets stay unchanged.

  *(Derived: the G3 rules finding; D-13; R4 extends the warning to v1.0.0, which has the same glow)*
- **REQ-REL-16** The repository documents the release procedure and the REQ-REL-07 contract, for example in a maintainer section or a `RELEASING.md`. It covers:
  - the draft → verify → publish order
  - the BOM and CRLF pitfall for the sidecar
  - how to extract the CHANGELOG section
  - how to make a PATCH release for data-only updates

  *(Derived: research risk "a release published incorrectly silently turns the updater into notify-only"; AD-7)*
- **REQ-REL-17** The Phase 7 report (REQ-XC-REPORT) includes the release URL, the asset digests and the end-to-end result. *(Brief: "Deliverable after each phase")*

**Out of scope**
- Publishing on Modrinth, CurseForge or any other platform.
- Automated release publishing from CI, build-provenance attestations, or signing (possible later).
- Real screenshots. The brief asks for placeholders; the user may replace them.
- 26.1.x maintenance releases (decided D-13: no 1.0.2 hotfix).
- Deleting or replacing the assets of v1.0.0 and v1.0.1; rewriting the history of `main`; squash or rebase merges (decided D-10).
- Changing the repo licence. Legal advice on Hypixel or wiki terms.

**Acceptance criteria**
- **AC-REL-01** (REQ-REL-01, REQ-REL-02) *Given* the README on the release commit:
  - it has the version line and the requirements
  - every toggleable feature appears with a default that matches the config defaults in code
  - every visible feature has a placeholder, with no broken images in the GitHub preview
  - a search of the README and `fabric.mod.json` finds neither "Modrinth" (as the update source) nor any P7 word

  — [R]
- **AC-REL-02** (REQ-REL-03) The README contains:
  - the non-affiliation sentence
  - a compliance section listing every excluded behaviour
  - a network-access list whose hosts equal the hosts in REQ-UPD-20 plus the Bazaar host
  - the licensing note

  — [R]
- **AC-REL-03** (REQ-REL-04) The upgrade note appears in both the README and the release body, and lists every behaviour change named in REQ-REL-04. — [R]
- **AC-REL-04** (REQ-REL-05) A script check of `CHANGELOG.md`:
  - every heading matches `## [x.y.z] - YYYY-MM-DD` or `## [Unreleased]`
  - only the standard subsections are used
  - a section exists for the release version
  - the documented extraction command produces exactly the release body

  — [A] + [R]
- **AC-REL-05** (REQ-REL-06, REQ-REL-07) *Given* `./gradlew clean build` on the tagged commit:
  - `build/libs` contains `k8bas_skyblock_utility-2.0.0+26.2.jar`, which matches the pattern
  - its `fabric.mod.json` has `version` `2.0.0+26.2` and `depends.minecraft` `~26.2`
  - the CHANGELOG heading, the tag and the release title name the same version

  — [A] + [R]
- **AC-REL-06** (REQ-REL-08)
  - The PR from `update/26.2` to `main` exists and its body has every required section.
  - The change summary starts at v1.0.1 (`bc0f2f6`), not at v1.1.0, and marks the items already released in v1.1.0.
  - CI is green on its head.
  - A search of the diff and the body for non-noreply emails, `C:\Users`, and UUID patterns finds nothing.

  — [R]
- **AC-REL-07** (REQ-REL-09) The merge commit on `main` has two parents. It was created after the timestamp of the user's approval, and no branch commit is newer than that approval. — [R]
- **AC-REL-08** (REQ-REL-10) `git cat-file -t v2.0.0` prints `tag`, and the tag points to the merge commit. `gh release view v2.0.0 --json` shows:
  - `isDraft` false and `isPrerelease` false
  - only the jar and the sidecar as assets
  - a body equal to the CHANGELOG section

  The tag push and the draft are both later than the user's "ship" message, which is later than the merge. — [R]
- **AC-REL-09** (REQ-REL-07, REQ-REL-11)
  - *Given* the draft's JSON as a fixture, the updater's selection picks the jar, the digest equals both the local `sha256sum` and the sidecar, and the jar validator passes. — [A]
  - A production boot of the exact jar with the user's mod set reaches the title screen with no new ERROR lines. — [D]
- **AC-REL-10** (REQ-REL-12) *Given* the test install on the lower version, *when* the client starts, *then* a toast and a chat line show v2.0.0; after confirming and quitting, the next launch shows "Updated to v2.0.0", and exactly one k8bas jar remains in `mods/`, with the release's digest. — [D] or [E]
- **AC-REL-11** (REQ-REL-13) Before publishing, `gh api repos/Kesuhi/K8basSkyblockUtility/immutable-releases` returns `enabled: true`; after publishing, `gh release verify v2.0.0` passes [decided R4]. — [R]
- **AC-REL-12** (REQ-REL-14) The v1.1.0 release passes AC-REL-04, AC-REL-05, AC-REL-08 and AC-REL-09 with its own version. — [A] + [R] + [D]
- **AC-REL-13** (REQ-REL-15) `gh release view v1.0.1 --json body` (and the same for v1.0.0) starts with the approved warning, and the asset digest is unchanged (`sha256:5396068…` for v1.0.1). Both edits are later than the user's message approving the wording. — [R]
- **AC-REL-14** (REQ-REL-16) The release document exists. A dry run of its steps against a throwaway draft, deleted afterwards, succeeds. — [R]
- **AC-REL-15** (REQ-REL-17) The Phase 7 report is present, with the URL, digests and end-to-end result. — [R]

**Edge cases**
- **EC-REL-01** CI fails on the PR head → no merge. It is fixed with a new commit, and approval is requested again (REQ-REL-09).
- **EC-REL-02** `main` has moved since the branch point → merge `main` into `update/26.2` (no rebase and no force-push of the shared branch), rebuild, get CI green, and request approval again.
- **EC-REL-03** Tag `v2.0.0` already exists, locally or on the remote → stop and ask the user. A published tag is never moved or deleted.
- **EC-REL-04** `gh` is not authenticated or lacks permission → stop before creating anything, so there is never a partial release.
- **EC-REL-05** An upload is interrupted, an asset is `state: open`, or the digest differs from the local hash → fix it in the draft (delete the asset and upload again; `--clobber` only on drafts) and never publish as is.
- **EC-REL-06** The sidecar was written with a BOM or CRLF (Windows PowerShell 5.1 `Out-File`) → the pre-publish check fails; regenerate it with a BOM-free writer.
- **EC-REL-07** `CHANGELOG.md` has no section for the version → stop before creating the draft.
- **EC-REL-08** The `+` in the asset name → GitHub keeps it in `name` and encodes it as `%2B` in the URL. The sidecar refers to the name with `+`, and nothing is renamed.
- **EC-REL-09** A release is published with the wrong pre-release flag → fix the flag (still editable after publishing) without touching the assets, and note it in the report.
- **EC-REL-10** A defect is found after publishing while immutable releases are on → ship a new PATCH release and add a note to the broken one. The same tag is never re-created.
- **EC-REL-11** GitHub's "Latest" badge lands on the wrong release → set it explicitly when publishing. The updater does not depend on the badge.
- **EC-REL-12** The user asks for changes after approving → the approval is void: new commits, CI, then approval again.
- **EC-REL-13** Tier E field checks are still open when the PR is opened → they are listed as known issues, and the release goes ahead only if the user accepts that.
- **EC-REL-14** A 26.2.x point release appears → `~26.2` and the updater's prefix rule already cover it. The README names it only after a boot check on that point version.
- **EC-REL-15** The SBXP update skill opens a data PR after 2.0.0 → it ships as a PATCH release following REQ-REL-16.

**Open questions**
- **Q-REL-01** (D-9, together with D-13) Version numbers: **→ decided D-9 (a)**
  - (a) early `v1.1.0+26.2`, final `v2.0.0+26.2` (PLAN)
  - (b) early `v2.0.0+26.2`, because it already drops 26.1 and changes the glow behaviour; final `v2.1.0`
  - (c) no early release; a single `v2.0.0`

  *Recommendation:* (a). Mod convention keeps the MC version in build metadata, and the 1.1.0 changes are compliance fixes. But D-9's reason must stop citing "drops 26.1", since 1.1.0 already does; 2.0.0 then rests on the new UI, the Cloth removal and the rebuilt updater. *Why it matters:* the version strings become jar names, tags and updater comparisons, and once published (especially as immutable releases) they can't be renamed.
- **Q-REL-02** (D-13) Early release and warnings: **→ decided D-13 (v1.1.0 after G1, warning on v1.0.1); the extension to v1.0.0 and the wording approval → decided R4**
  - (a) v1.1.0 after G1, plus a warning on **v1.0.0 and v1.0.1** now (PLAN, extended to v1.0.0, which has the same glow)
  - (b) the warnings only, no early release
  - (c) neither

  *Recommendation:* (a). *Why it matters:* until a 26.2 release exists, the rule-breaking 1.0.x is the latest download on the page.
- **Q-REL-03** (new; follows from D-13 and the brief's Phase 6 flow) Git flow for an early release: **→ decided R4**
  - (a) an intermediate PR from `update/26.2` to `main` at G1, merged after your approval, with the tag on `main`
  - (b) tag a commit on `update/26.2` without merging
  - (c) a short-lived `release/1.1` branch, merged into `main`

  *Recommendation:* (a). It is the same flow as the brief's Phase 6, and it keeps `main` (and its README) equal to the newest download; with (b), `main` would still describe 1.0.1/26.1.2 while "Latest" is 1.1.0. *Why it matters:* it decides where tags live, what visitors see, and what the final PR's "summary of all changes" covers.
- **Q-REL-04** (new; PLAN T7.4 step 4 "if wanted") Turn on GitHub immutable releases for the repo? **→ decided R4**
  - (a) Yes, before publishing v2.0.0, the first release the new updater installs from. I would run the API call with your OK.
  - (b) Keep them off.

  *Recommendation:* (a). *Why it matters:* once on, a published release's tag and assets are locked, so a broken asset needs a new PATCH version, but no installed updater can ever be served a changed file under a verified version.
- **Q-REL-05** (new) What does your PR approval cover? **→ decided R4**
  - (a) The merge only; pushing the tag and publishing need a second explicit "ship".
  - (b) Merge, tag and publish all at once.

  *Recommendation:* (a). *Why it matters:* publishing is public, it notifies every installed updater, and with immutable releases it can't be undone.
- **Q-REL-06** (same as D-10) Git: **→ decided D-10 (noreply · push at checkpoints · merge commit)**
  - commit identity: repo-local `Kesuhi <110562470+Kesuhi@users.noreply.github.com>`, or your global address
  - push `update/26.2` at checkpoints so CI runs, or only at the end
  - merge commit, or squash

  *Recommendation:* noreply · push at checkpoints · merge commit. *Why it matters:* your global (work) address becomes public with the first push, and a squash would erase the small commits ground rule 2 asks for.

---

## 11. Success criteria (release 2.0.0)

1. Every module's acceptance criteria (§10) pass at their stated tier. Three exceptions:
   - Criteria explicitly superseded by a later module are checked at their own checkpoint only. Example: the 1.0.1 waypoint style in AC-PORT-06 is checked at G1 and then replaced by the npc-waypoints style.
   - Criteria that depend on rare in-game content (rare rooms, Vanguard corpses, hotspots) are checked opportunistically and listed as open in the phase report until seen.
   - Criteria marked [dropped R22] (AC-CORPSE-01 to AC-CORPSE-10 and the second bullet of AC-GS-16) are not checked.
2. Checkpoints G1–G7 in `PLAN.md` are closed, each with a phase report (REQ-XC-REPORT-01).
3. Every commit on `update/26.2` builds green (AC-XC-05: per-commit worktree build at each checkpoint), and CI is green on every pushed head and on the final PR.
4. A 1.0.1 config file migrates with no setting lost (config-store, ui-config).
5. The production boot with your 26.2 mod set shows no crash and no new ERROR lines.
6. The SkyBlock XP Optimizer meets the addendum's *Done when*:
   - it ranks correctly for a fresh and a progressed profile
   - it recalculates on coins/h and stage changes
   - the update skill runs end to end as a dry run
   - README and CHANGELOG are updated
7. The release is published in exactly the format the updater expects, checked against the draft's API JSON before publishing. The updater installs it only after a click.
8. You approve the PR; it is merged, tagged and released after your separate "ship".

---

## 12. Decisions and open questions

### 12.1 Answer record (2026-10-01)

You approved **all plan decisions D-1–D-29 with the recommended option** ("Plan: approve with recommendations"). They are binding for this spec, and requirements written to them carry `[decided D-xx]`.

| Area | Decided |
|---|---|
| Rules | D-1 visible-only glow (Render Chest), invisible entities never highlighted, no through-wall option · D-6 defaults policy P1–P7 (below) and the defaults table §12.H |
| Data & licensing | D-5 MIT data with notices (pinned PR #759 commit, credit); its corpse-spot part (SkyHanni-REPO PR #759 + meowdding spots) is dropped by R22, and the NEU bestiary and RNG weights stay · D-12 NPC/mob lists bundled only, gists frozen · D-16 (a) wiki = human cross-check; XP and maxima need a non-wiki source; an unverified rate or drop chance may rest on one page you supplied. This overrides the addendum's "fill XP values from the official wiki", which closed on 2026-07-21. No consent request to Weird Gloop. · D-25 delete wiki copies + parsed data after plan approval · D-26 "Phase 3.9 internal database" = shared data registry (T3.0c) · D-28 (i) Claude reads wiki/forum/reddit pages only when you supply them |
| Features | D-2 split Catacombs / Dungeon Hub · D-3 numeric "Rare Drop Odds", 5 v1 cases (Scatha, Croesus/dungeon chests = relative rarity, Vanguard, Lapis/Umber/Tungsten, Slayer RNG) · D-4 drill hand re-equip fix only, drills only · D-7 corpse keys counted from inventory · D-15 beam colour = island colour + optional per-NPC override, glow colour separate |
| UI | D-8 no UI library, vanilla font, corners from fills, accent configurable (default teal `#29B6B2`), live-apply + save on close, Cloth Config removed, toasts, T2.9 items as stretch goals |
| Updater & release | D-9 final 2.0.0 · D-10 repo-local identity `Kesuhi <…noreply…>`, push at checkpoints, merge commit · D-13 early v1.1.0+26.2 after G1, rules warning on old release notes, no 26.1.2 hotfix · D-14 confirm → staged install |
| Optimizer | D-17 `/sblevels` and component-menu captures — **all at G1** (you moved the "now" capture on 2026-10-01) · D-18 blended research priority · D-19 gross costs · D-20 one keyless AH scan per data update (asks first, aggregates only), no third-party LBIN · D-21 next step per instance · D-22 live Bazaar opt-in, ≤ 1/15 min, instant-buy · D-23 skill: you supply patch notes, `disable-model-invocation: true`, gates, Java data tool, venv for the eval loop · D-24 max level from your capture · D-27 component-menu parsers · D-29 free/waiting group, carries excluded |
| Process | D-11 you create the Prism copy `26.2 Skyblock K8bas-test`; G1 smoke ~30–40 min |

**Defaults policy (D-6):**
- **P1:** no entity is ever rendered through walls.
- **P2:** invisible entities are never highlighted or announced. Scope (R2): a hologram whose name tag vanilla displays (fishing hotspots) counts as visible information.
- **P3:** markers or alerts derived from an entity fire only after line of sight. Exceptions (R2): the depth-tested hotspot ring on a displayed name-tag hologram, and the "hotspot gone" warning, which is gated by the fished window and the distance.
- **P4:** see-through rendering only for fixed coordinates.
- **P5:** packets are read-only; no automatic chat sends.
- **P6:** every feature has its own toggle; AMBER items carry a tooltip.
- **P7:** no "ESP", "x-ray", "through walls", "cheat" or "gambling" in feature names.

**Consequences of D-15 and D-3:**
- **Beam colours (D-15):** on one island, all beams without a per-NPC colour share the island colour. Beams look individually coloured once you set per-NPC colours. If you'd rather have every new waypoint get its own colour automatically from a palette, say "D-15 → palette".
- **Croesus tooltip (D-3 + R10 = b):** relative rarity plus a base "≈ 1 in N runs" estimate from the same MIT data.

### 12.2 Spec questions R1–R17 — answered

Your reply (2026-10-01): **"R10 b, rest as recommended"**. Each row below is decided with its recommended option, except R10, which is decided as **b**. Requirements carry `[decided Rn]`.

| # | Question | Options | Recommended |
|---|---|---|---|
| R1 | Where the Trinity/Tomioka/Duncan glow lives (Q-GLOW-02) | a) as NPC Search rules: your 3 rules, each rule's switch is the toggle; a fresh install glows nothing until added · b) dedicated card, default OFF · c) dedicated card, default ON | **a** |
| ★R2 | Fishing hotspots are Hypixel hologram armor stands (invisible body, displayed name tag). Treat a name tag that vanilla already shows as visible information, so the ring may be drawn (depth-tested). The "hotspot gone" warning is gated by "fished there in the last 30 s and within 40 blocks" instead of line of sight. This clarifies REQ-XC-RULES-04/05 (Q-HOT, §13.4) | a) yes · b) no: require line of sight for the warning, ring only while in view | **a** |
| R3 | Assumptions A-1–A-10 (§1): 26.2 only, Fabric only, English UI, Hypixel Mod API required, own code CC0, only you test on Hypixel, glow = outline, GitHub-only distribution, Windows verified, one CI grep + a pre-commit privacy scan. Plus the capability map (§2) with the dependency fixes found by review | a) correct · b) correct me | **a** |
| R4 | Release details (Q-REL-02..05, Q-UPD-01):<ul><li>the rules warning goes on **v1.0.0 and v1.0.1** (same glow), wording approved by you first</li><li>the early release goes through an intermediate PR to `main`</li><li>v1.1.0 replaces the dead Modrinth updater with a notify-only GitHub check shown as a chat line</li><li>your PR approval covers the merge; a separate "ship" authorises tag push + publish</li><li>GitHub immutable releases are enabled before 2.0.0</li></ul> | a) all as described · b) change parts | **a** |
| R5 | NPC/mob data (Q-NPCDB-02..05):<ul><li>invisible-by-design mobs (Ghost, Fels, Sneaky Creeper, Invisibug) stay in the picker greyed, not addable, labelled "never highlighted (Hypixel rules)"</li><li>no new island coverage in v1 (gaps listed)</li><li>rules created from the database follow corrected coordinates</li><li>**you** remove those 4 mobs from the 1.0.x mob gist</li></ul> | a) all as described · b) change parts | **a** |
| R6 | Also bundle SkyOcean's `src/repo/vanguard.jsonc` (MIT under its licence) with a notice, replacing its "assumed" dye weight (Q-ODDS-06) | a) yes · b) no (Vanguard odds from your supplied pages only) | **a** |
| R7 | Mineshaft and corpses (Q-MSA-02/03, Q-CORPSE-02/03):<ul><li>a Dead Man's Chest corpse adds one follow-up line</li><li>no "[Share]" button in v1</li><li>`/ksu corpses` lists spots even with markers OFF</li><li>corpse spot markers are **text labels only** (no beam or box), see-through, default OFF</li></ul> | a) as described · b) corpse markers also get beams · c) list command only, no markers | **a**. Since R22 only the first two points apply: `/ksu corpses` and the corpse spot markers are dropped [dropped R22] |
| R8 | Existing fixed-waypoint colours: a customised colour becomes that rule's beam colour; rules still on the old default green follow the island colour (Q-NPCWP-02) | a) as described · b) all colours stay glow-only · c) every colour becomes a beam colour | **a** |
| R9 | Fishing (Q-BOB-01, Q-HOT-01): the bobber fix applies to **your own** bobber only, and "the hotspot you're fishing in" means your bobber was inside its area at any time in the last 30 s | a) as described · b) every bobber; "fishing in" = a catch within 30 s | **a** |
| R10 | Croesus/dungeon chest tooltip (Q-ODDS-03): keep relative rarity only (decided D-3), or add "≈ 1 in N runs (base, no bonuses)" from the same MIT data (N = RNG score / 300, within ~1–2.5 % of the wiki)? | a) relative only · b) + "≈ 1 in N runs" | **Answer: b** |
| R11 | Bestiary (Q-BEST-01..03):<ul><li>"milestone" = the global milestone shown as "tiers to next milestone" (from menu/chat), plus a family "kills to max" line</li><li>the milestone line is ON by default when known</li><li>a session resets on manual reset, profile switch or restart, with an optional "reset on island change" (OFF)</li><li>if the Bestiary tab widget proves unusable at G1, menu + chat snapshots only (decide any estimated counter after G1)</li></ul> | a) as described · b) change parts | **a** |
| R12 | UI details (Q-UI-03, Q-HUD-01/02):<ul><li>toasts for update prompts, plus an *optional* toast channel on warnings, default OFF (your brief's chat/title/sound stay the defaults)</li><li>HUD editor: Esc = save and close, Cancel reverts</li><li>HUD elements are hidden while any screen other than chat is open</li></ul> | a) as described · b) toasts ON for warnings · c) HUDs stay visible under screens | **a** |
| R13 | Updater startup cleanup (Q-UPD-02) | a) delete every lower-version copy · b) delete only the jar the updater itself replaced; warn once about others · c) never delete | **b** |
| ★R14 | Optimizer details (Q-SBXP-13..21):<ul><li>**coin-only** is judged per option: a task is coin-only only if *every* estimated option is coin-only; "exclude coin-only" removes coin-only options before the cheapest option is chosen, and drops the task only if nothing remains; the research log lists every coin-only and hybrid family</li><li>Gems are never converted</li><li>unknown prerequisites are ranked with a "?" (setting: treat as locked); "include locked tasks" shows/hides the Locked section</li><li>overrides per rate (applies to all tasks using it), per task (wins) and per item price</li><li>stage and overrides per profile; coins/h global with an optional per-profile value; defaults 5,000,000 coins/h and a *suggested* stage from your level</li><li>live progress from action-bar + chat XP lines between menu visits</li><li>"ranks correctly" = tests + a hand-computed fresh-profile golden table + your top-20 review at G6</li></ul> | a) all as described · b) change parts (name them) | **a** |
| R15 | Update skill details (Q-SKILL-01/03):<ul><li>branch `data/sbxp-<gameVersion>`, adding `-d<dataVersion>` only if that branch already exists</li><li>with no patch notes it runs on data-source diffs, labelled "no patch notes supplied"</li></ul>FYI (decided D-23): `disable-model-invocation: true` makes the skill fail the strict official Agent Skills validator; it passes once that single line is removed | a) as described · b) always add `-d<dataVersion>` · c) stop when no notes are given | **a** |
| R16 | Plan tasks without a requirement:<ul><li>**keep:** T1.13 lang fix (REQ-PORT-11), the T5.3 compact mode as a "may" built last, the T6.6 coverage header, the T6.8 keybind (unbound)</li><li>**drop:** the T3.8 `entityType` field</li><li>**move:** location change events to T1.9, multi-select to T6.7, bestiary table + parser to bestiary-hud (reused by the optimizer)</li><li>**replace:** T1.4 by the R4 notify-only check</li><li>**conditional:** T1.12 timeouts kept only because v1.1.0 still reads the gists</li><li>**reword:** T4.3 cleanup per R13; T7.4 step 6 tested with a never-published `2.0.0-test.1` build</li></ul> | a) accept all · b) change some | **a** |
| R17 | Your in-game time, as planned:<ul><li>G1 smoke ~30–40 min + all captures (the `/sblevels` capture moved here)</li><li>short checks spread over G3–G7: ~23 criteria such as mining 50 blocks, 2 min of fishing, one Croesus chest</li><li>the G6 top-20 review</li><li>a few minutes per research batch to paste pages</li></ul> | a) fine · b) G1 only (later in-game checks are reported as open) | **a** |

**Captures (D-17), moved to G1 at your request.** At the G1 smoke you capture the leveling and component menus. Use the mod's own armed dump (`/ksu debug dump containers on`), or SkyBlockAPI's `chest_dumps` toggle (`/sbapi toggle skyblock-api:chest_dumps`, then press S in each menu). Until then:
- the SBXP research priority uses your SkyHanni and SkyBlockAPI caches plus fresh-profile totals (D-18)
- parser fixtures stay provisional and marked UNVERIFIED

### 12.3 Decisions after G1 — R18–R26

You made these decisions on 2026-10-01: R18 and R19 after the G1 review, R20–R24 after the G1 in-game smoke. Requirements and criteria written or changed by them carry the decision tag `[decided Rn]` where the change is made (for R23, the "once T3.4 lands" parts of REQ-PORT-06, AC-PORT-06 and AC-PORT-14); the items R22 drops carry `[dropped R22]` and keep their ids.

| # | Decision | Recorded in |
|---|---|---|
| R18 | A rule that ignores names (NONE) needs an entity type. Without one it would outline every visible entity, so it is inert, logged once and marked in the rule editors with a warning sign and a line saying why | REQ-GLOW-10, AC-GLOW-09; PLAN T1.8c |
| R19 | Captures mask real player names, yours included, as Self, Player1, …; NPC names and Hypixel's tab-widget entries stay | REQ-GS-13, AC-GS-13; PLAN T0.4d |
| R20 | In v1.1.0, the "You found <NPC>" title fires only for five special moving NPCs: Trinity, Tomioka and Duncan (Catacombs), and Xalx and Pete (Crystal Hollows). A rule qualifies when its NPC data sourceId is `trinity`, `tomioka`, `duncan`, `xalx` or `pete`, or, for a hand-made rule without a sourceId, when its label equals one of those five names, ignoring case and surrounding spaces. Other NPC Search rules still outline their NPC but never trigger the title. The other title rules stay: line of sight, once per rule per server, reset on a location change, its own toggle (default ON), never for invisible entities | REQ-GLOW-14, REQ-GLOW-15, AC-GLOW-13, Q-GLOW-03, REQ-REL-04, §12.H; PLAN §3, T1.11b, T7.1, §9 (D-6) |
| R21 | In v1.1.0, NPC Search gets the setting "White waypoint labels", default ON: waypoint labels and their distance line are drawn white. OFF draws them in the rule's colour (the 1.0.1 and 1.1.0-dev look). This replaces the npc-waypoints out-of-scope line "Label colours other than white": white stays the default, and the rule's colour becomes an opt-out. When the beacon beams arrive (T3.4), the setting stays and the beam carries the colour. In v1.1.0 the distance line follows the label (white while the setting is ON). From T3.4 on, ON gives a white label and a yellow distance line (Skyblocker style), and T3.4 updates the toggle tooltip to say so; OFF still draws both lines in the rule's own `color` field, not in the resolved beam colour | REQ-PORT-06, AC-PORT-06, REQ-NPCWP-02, REQ-NPCWP-03, REQ-NPCWP-08, npc-waypoints out of scope, AC-NPCWP-03, AC-NPCWP-05, EC-NPCWP-06, REQ-REL-04, §12.H; PLAN §3, T1.3b, T3.4, T7.1, §9 (D-6) |
| R22 | The possible corpse spots (brief Phase 3 item 3) are dropped from the plan entirely: the corpse-waypoints module, PLAN T3.3a (spot table) and T3.3 (markers and `/ksu corpses`), their defaults row, the `corpses` reserved word, D-5's corpse-spot data (SkyHanni-REPO PR #759 + meowdding spots) and the corpse-spot points of R7. Ids are kept and marked dropped. What stays: the mineshaft entry alert (T3.2), the Frozen Corpses widget reading (T3.0m), the corpse odds (T3.9d, T3.9e) and D-5's NEU bestiary and RNG-weight data. The README's excluded-behaviour line "filtering possible corpse spots by entities you cannot see" stays (REQ-REL-03) | corpse-waypoints (all ids), REQ-GS-16, AC-GS-16, REQ-REL-03, §11, REQ-GS-17, REQ-UI-04, REQ-UI-10, REQ-MARK-02, REQ-MARK-06, AC-MARK-05, REQ-DATA-13, REQ-LOC-08, REQ-XC-RULES-06, R7, §12.1 (D-5), §12.H, §13; PLAN §2, §3, AD-7, T2.5b, T3.0m, T3.3a, T3.3, T7.1, T7.1b, G3, "Not built in 2.0.0", §8, §9 (D-5, D-6) |
| R23 | Order after the v1.1.0 merge: the first Phase 3 work is the waypoint path T3.0b (marker toolkit) → T3.0n (beacon beams) → T3.4 (Skyblocker-style NPC waypoints: white label plus coloured beacon beam), so beams come right after v1.1.0. Reimplemented from scratch: Skyblocker is LGPL-3.0, and no Skyblocker code is used (REQ-NPCWP-11). Whether this ships as an intermediate release is decided then | §2 (build order), REQ-PORT-06, AC-PORT-06, AC-PORT-14 ("once T3.4 lands"); PLAN §6, Phase 3 intro, T3.0b, T3.4, T3.8 |
| R24 | G1 field answers. (a) Q-MSA-04: you believe the Frozen Corpses tab widget is on by default and ask for a failsafe that tells the player when it is off. The once-per-launch widget hint (REQ-MSA-08, REQ-GS-18) is that failsafe. The command and menu path that enable the widget were not given, so the hint wording stays UNVERIFIED and is checked at G3. (b) Whether a Tungsten or Umber key held only in the Dwarven Sack opens a corpse could not be tested: D-7 (inventory only) stands, the REQ-MSA-05 sack fallback is not triggered, and the question stays open for G3. (c) Every area of the game counts as a Bestiary area except the Dungeon Hub | Status, §10 intro, REQ-GS-18, AC-GS-18, EC-SBXP-07, REQ-MSA-05, REQ-MSA-08, EC-MSA-04, Q-MSA-01, Q-MSA-04, REQ-BEST-12, AC-BEST-11, §13.3; PLAN G1, T3.0m, T3.2, T5.3, G3 |
| R25 | After T3.4 (2026-10-03): (a) Q-NPCWP-04, keep the documented island colour defaults under REQ-NPCWP-05 as they are. (b) The waypoint path (T3.0b, T3.0n, T3.4) does not ship as an intermediate release; it ships with the next regular release, so T3.4b (colour settings and the R8 migration) lands first | REQ-NPCWP-05, Q-NPCWP-04; PLAN §6 waypoint path, T3.4 |
| R26 | T2.3b (2026-10-03): a keybind conflict is marked in yellow, as vanilla 26.2 Controls does, not in the error red (EC-UI-11 had assumed vanilla used red) | EC-UI-11; PLAN T2.3b |

### 12.H Defaults table (decided D-6, R1–R17 and R20–R22)

| Feature | Default | | Feature | Default |
|---|---|---|---|---|
| Mob Highlighter / NPC Search modules | ON (rules user-added) | | Mineshaft entry alert | ON; key counts ON |
| Trinity/Tomioka/Duncan glow | your rules (fresh install: none) [R1] | | Possible corpse spots | [dropped R22]: no toggle (was **OFF**; hide when all looted ON [R7]) |
| "You found X" title | ON, after line of sight, once per run; only for Trinity, Tomioka, Duncan, Xalx and Pete [R20] | | Bobber fix | ON (own bobber) [R9] |
| NPC waypoints | ON; beacon beams ON; distance line ON; white labels ON [R21] | | Hotspot ring / filled area | ON / ON [R2] |
| Fixed-coordinate labels see-through | yes | | Hotspot-gone warning | ON (title + sound + chat) [R2] |
| Drill re-equip fix | ON (drills only) | | Rare Drop Odds | **OFF**; once enabled all cases ON, chat lines ON, rare threshold 2 % |
| Bestiary HUD | **OFF**; lines when enabled: tier, kills, to next tier, rate, session kills, milestone (when known) ON; to max, ETA OFF; reset on island change OFF [R11] | | Update check / channel | ON (notify) / STABLE |
| Toast channel on warnings | **OFF** [R12] | | SkyBlock XP module / passive reading / live deltas | ON / ON / ON [R14] |
| "Next best task" HUD | **OFF** | | Live Bazaar | **OFF** |
| Coins per hour | 5,000,000 (global) [R14] | | Player stage | suggested from your level, per profile [R14] |

---

## 13. Traceability (final)

Generated from the `Req:` and `Accept:` bullets in `PLAN.md` §7 by a check script that runs both ways (the `Was Req:` and `Was Accept:` bullets of the dropped tasks T3.3a and T3.3 are excluded; §13.1 and §13.2 show them as "was …"):
- 384 requirements: 363 implemented by at least one task, 5 met at checkpoints (REQ-UPD-02, REQ-REL-17, REQ-XC-RULES-06, REQ-XC-SKILLS-01, REQ-XC-REPORT-01), 6 not built by decision, and 10 dropped after G1 (REQ-CORPSE-01 to REQ-CORPSE-10, R22). **None uncovered.**
- 151 tasks: 149 active and 2 dropped (T3.3a, T3.3, R22). Each active task names at least one requirement and has an `Accept:` line. There are no unknown ids, no dependency on a missing or dropped task, and no task larger than M. This update (R20–R24) added T1.11b and T1.3b, and also added the rows for T0.4c, T0.4d and T1.8c, which were missing here.
- 342 acceptance criteria (withdrawn ones excluded): 332 are each named by a task or a checkpoint, and 10 are dropped (AC-CORPSE-01 to AC-CORPSE-10, R22).

### 13.1 Requirement → PLAN tasks, by module

**port-26-2** (12)

| Requirement | PLAN tasks |
|---|---|
| REQ-PORT-01 | T1.2 |
| REQ-PORT-02 | T1.2 |
| REQ-PORT-03 | T1.2 |
| REQ-PORT-04 | T1.2 |
| REQ-PORT-05 | T1.1, T1.2 |
| REQ-PORT-06 | T1.1, T1.2, T1.3, T1.3b |
| REQ-PORT-07 | T1.3 |
| REQ-PORT-08 | T1.2, T1.10 |
| REQ-PORT-09 | T1.14, T1.3b, T1.16 |
| REQ-PORT-10 | T1.4 |
| REQ-PORT-11 | T1.13 |
| REQ-PORT-12 | T0.6, T1.1 |

**config-store** (13)

| Requirement | PLAN tasks |
|---|---|
| REQ-CFG-01 | T0.3, T1.7b |
| REQ-CFG-02 | T1.7c |
| REQ-CFG-03 | T0.2, T0.3, T1.7b, T2.6 |
| REQ-CFG-04 | T1.7 |
| REQ-CFG-05 | T1.7 |
| REQ-CFG-06 | T1.7b |
| REQ-CFG-07 | T1.7b |
| REQ-CFG-08 | T1.7b |
| REQ-CFG-09 | T1.7c, T1.9b |
| REQ-CFG-10 | T1.7, T2.4c |
| REQ-CFG-11 | T1.7b |
| REQ-CFG-12 | T1.7, T1.7c, T1.4a, T2.7, T4.1 |
| REQ-CFG-13 | T1.13, T2.6 |

**location** (10)

| Requirement | PLAN tasks |
|---|---|
| REQ-LOC-01 | T1.9 |
| REQ-LOC-02 | T1.9 |
| REQ-LOC-03 | T1.9, T1.9b |
| REQ-LOC-04 | T1.9 |
| REQ-LOC-05 | T1.9 |
| REQ-LOC-06 | T1.9 |
| REQ-LOC-07 | T1.9 |
| REQ-LOC-08 | T0.4, T0.4c, T1.9, T3.0m |
| REQ-LOC-09 | T1.9, T2.5a |
| REQ-LOC-10 | T1.9 |

**glow** (17)

| Requirement | PLAN tasks |
|---|---|
| REQ-GLOW-01 | T0.2, T0.3, T1.10a |
| REQ-GLOW-02 | T1.10 |
| REQ-GLOW-03 | T1.8 |
| REQ-GLOW-04 | T1.10 |
| REQ-GLOW-05 | T1.10 |
| REQ-GLOW-06 | T1.10a |
| REQ-GLOW-07 | T1.10 |
| REQ-GLOW-08 | T1.10a |
| REQ-GLOW-09 | T3.1 |
| REQ-GLOW-10 | T1.8b, T1.8c, T2.5a |
| REQ-GLOW-11 | T1.8b |
| REQ-GLOW-12 | T3.1 |
| REQ-GLOW-13 | T1.9b, T3.1 |
| REQ-GLOW-14 | T1.11, T1.11b |
| REQ-GLOW-15 | T1.14, T1.11b, T1.16, T2.1, T7.1 |
| REQ-GLOW-16 | T1.10 |
| REQ-GLOW-17 | T1.10 |

**ui-config** (25)

| Requirement | PLAN tasks |
|---|---|
| REQ-UI-01 | T2.4a |
| REQ-UI-02 | T2.2, T2.4a |
| REQ-UI-03 | T2.3a, T2.4a |
| REQ-UI-04 | T2.4a, T2.4d, T2.8b, T4.4b, T6.8 |
| REQ-UI-05 | T2.1, T2.4a |
| REQ-UI-06 | T2.3a, T2.3b |
| REQ-UI-07 | T2.1, T2.4a |
| REQ-UI-08 | T2.1, T2.4b |
| REQ-UI-09 | T2.5a |
| REQ-UI-10 | T2.5b, T2.8b, T4.4 |
| REQ-UI-11 | T2.5a, T2.5c, T3.8c |
| REQ-UI-12 | T2.5a |
| REQ-UI-13 | T2.3b |
| REQ-UI-14 | T2.3b, T2.4d, T2.6 |
| REQ-UI-15 | T2.4c, T2.5a |
| REQ-UI-16 | T2.4d, T2.6 |
| REQ-UI-17 | T2.2, T2.3a, T2.4a, T2.4d |
| REQ-UI-18 | T2.9, T2.9b, T2.9d |
| REQ-UI-19 | T2.1, T2.3c |
| REQ-UI-20 | T2.3c, T2.5c, T6.7 |
| REQ-UI-21 | T2.3d, T2.4d, T4.4 |
| REQ-UI-22 | T2.3a, T2.3b, T2.4b |
| REQ-UI-23 | T2.5b, T2.6 |
| REQ-UI-24 | T2.2 |
| REQ-UI-25 | T2.5b |

**hud** (14)

| Requirement | PLAN tasks |
|---|---|
| REQ-HUD-01 | T2.7 |
| REQ-HUD-02 | T2.7, T2.8 |
| REQ-HUD-03 | T2.7 |
| REQ-HUD-04 | T2.7 |
| REQ-HUD-05 | T2.7, T2.8 |
| REQ-HUD-06 | T2.8b |
| REQ-HUD-07 | T2.8 |
| REQ-HUD-08 | T2.8 |
| REQ-HUD-09 | T2.8 |
| REQ-HUD-10 | T2.7, T7.4 |
| REQ-HUD-11 | T2.7 |
| REQ-HUD-12 | T2.7b |
| REQ-HUD-13 | T2.7b, T5.3, T6.9 |
| REQ-HUD-14 | T2.9c |

**data-registry** (13)

| Requirement | PLAN tasks |
|---|---|
| REQ-DATA-01 | T3.0c, T3.0k, T3.8 |
| REQ-DATA-02 | T3.0c, T3.0i, T6.0 |
| REQ-DATA-03 | T3.0i, T6.0 |
| REQ-DATA-04 | T3.0i |
| REQ-DATA-05 | T3.0j, T7.1b, T7.2b |
| REQ-DATA-06 | T3.0i, T6.0 |
| REQ-DATA-07 | T3.0c |
| REQ-DATA-08 | T3.0c |
| REQ-DATA-09 | T3.0c |
| REQ-DATA-10 | T3.0k |
| REQ-DATA-11 | T3.0c |
| REQ-DATA-12 | T3.0i |
| REQ-DATA-13 | T3.0c, T3.8, T6.1 |

**game-state** (18)

| Requirement | PLAN tasks |
|---|---|
| REQ-GS-01 | T3.0a |
| REQ-GS-02 | T3.0a |
| REQ-GS-03 | T3.0a |
| REQ-GS-04 | T3.0g |
| REQ-GS-05 | T3.0g |
| REQ-GS-06 | T3.0e |
| REQ-GS-07 | T3.0h |
| REQ-GS-08 | T3.0d |
| REQ-GS-09 | T3.0d |
| REQ-GS-10 | T3.0a |
| REQ-GS-11 | T3.0a |
| REQ-GS-12 | T0.4, T0.4b, T3.0a, T3.0e |
| REQ-GS-13 | T0.4, T0.4c, T0.4b, T0.4d, T3.0m, T3.0e |
| REQ-GS-14 | T3.0a, T3.0g, T3.0m, T3.0d, T3.0e |
| REQ-GS-15 | T3.0a, T3.0m, T3.0e |
| REQ-GS-16 | T3.0a, T3.0m |
| REQ-GS-17 | T3.0m |
| REQ-GS-18 | T3.0m, T5.3 |

**world-markers** (9)

| Requirement | PLAN tasks |
|---|---|
| REQ-MARK-01 | T3.0b, T3.0n |
| REQ-MARK-02 | T3.0b, T3.0n |
| REQ-MARK-03 | T1.3, T3.0b |
| REQ-MARK-04 | T3.0n |
| REQ-MARK-05 | T3.0b |
| REQ-MARK-06 | T3.0b |
| REQ-MARK-07 | T3.0b, T3.0n |
| REQ-MARK-08 | T3.0b |
| REQ-MARK-09 | T3.0n |

**npc-mob-data** (10)

| Requirement | PLAN tasks |
|---|---|
| REQ-NPCDB-01 | T3.8a |
| REQ-NPCDB-02 | T3.8a |
| REQ-NPCDB-03 | T3.8 |
| REQ-NPCDB-04 | T3.8 |
| REQ-NPCDB-05 | T1.12 |
| REQ-NPCDB-06 | T3.8 |
| REQ-NPCDB-07 | T3.8b |
| REQ-NPCDB-08 | T3.8b, T3.8c |
| REQ-NPCDB-09 | T3.8, T3.8c |
| REQ-NPCDB-10 | T3.8, T7.4 |

**mineshaft-alert** (12)

| Requirement | PLAN tasks |
|---|---|
| REQ-MSA-01 | T3.2 |
| REQ-MSA-02 | T3.2 |
| REQ-MSA-03 | T3.2 |
| REQ-MSA-04 | T3.2 |
| REQ-MSA-05 | T3.2 |
| REQ-MSA-06 | T3.2 |
| REQ-MSA-07 | T3.2 |
| REQ-MSA-08 | T3.2 |
| REQ-MSA-09 | T3.2 |
| REQ-MSA-10 | T3.2 |
| REQ-MSA-11 | T3.2 |
| REQ-MSA-12 | T3.2 |

**corpse-waypoints** (10, all dropped by R22)

| Requirement | PLAN tasks |
|---|---|
| REQ-CORPSE-01 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3a) |
| REQ-CORPSE-02 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-03 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-04 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-05 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-06 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-07 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-08 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3) |
| REQ-CORPSE-09 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3a, T7.1, T7.1b) |
| REQ-CORPSE-10 | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was T3.3a) |

**npc-waypoints** (12)

| Requirement | PLAN tasks |
|---|---|
| REQ-NPCWP-01 | T3.4 |
| REQ-NPCWP-02 | T1.3b, T3.4 |
| REQ-NPCWP-03 | T3.4 |
| REQ-NPCWP-04 | T3.4 |
| REQ-NPCWP-05 | T3.4, T3.4b |
| REQ-NPCWP-06 | T3.4b |
| REQ-NPCWP-07 | T3.4 |
| REQ-NPCWP-08 | T1.3b, T3.4 |
| REQ-NPCWP-09 | T3.4b |
| REQ-NPCWP-10 | T3.4 |
| REQ-NPCWP-11 | T3.4 |
| REQ-NPCWP-12 | T3.4 |

**bobber-fix** (8)

| Requirement | PLAN tasks |
|---|---|
| REQ-BOB-01 | T3.5 |
| REQ-BOB-02 | T3.5 |
| REQ-BOB-03 | T3.5 |
| REQ-BOB-04 | T3.5 |
| REQ-BOB-05 | T3.5 |
| REQ-BOB-06 | T3.5 |
| REQ-BOB-07 | T3.5 |
| REQ-BOB-08 | T3.0f, T3.5 |

**fishing-hotspot** (13)

| Requirement | PLAN tasks |
|---|---|
| REQ-HOT-01 | T3.6 |
| REQ-HOT-02 | T3.6 |
| REQ-HOT-03 | T3.6 |
| REQ-HOT-04 | T3.6 |
| REQ-HOT-05 | T3.6 |
| REQ-HOT-06 | T3.6 |
| REQ-HOT-07 | T3.6b |
| REQ-HOT-08 | T3.6b |
| REQ-HOT-09 | T3.6b |
| REQ-HOT-10 | T3.6b |
| REQ-HOT-11 | T3.6b |
| REQ-HOT-12 | T3.6, T3.6b |
| REQ-HOT-13 | T3.0f, T3.6 |

**rare-drop-odds** (26)

| Requirement | PLAN tasks |
|---|---|
| REQ-ODDS-01 | T3.9h |
| REQ-ODDS-02 | T3.9h |
| REQ-ODDS-03 | T3.9h |
| REQ-ODDS-04 | T3.9h |
| REQ-ODDS-05 | T3.9b, T3.9d, T3.9f |
| REQ-ODDS-06 | T3.9a |
| REQ-ODDS-07 | T3.9h |
| REQ-ODDS-08 | T3.9a |
| REQ-ODDS-09 | T3.9g |
| REQ-ODDS-10 | T3.9g, T3.9b, T3.9d, T3.9e |
| REQ-ODDS-11 | T3.9h |
| REQ-ODDS-12 | T3.9h |
| REQ-ODDS-13 | T3.9b |
| REQ-ODDS-14 | T3.9b |
| REQ-ODDS-15 | T3.9b |
| REQ-ODDS-16 | T3.9b |
| REQ-ODDS-17 | T3.9c |
| REQ-ODDS-18 | T3.9c |
| REQ-ODDS-19 | T3.9c |
| REQ-ODDS-20 | T3.9d, T3.9e |
| REQ-ODDS-21 | T3.9d, T3.9e |
| REQ-ODDS-22 | T3.9d, T3.9e |
| REQ-ODDS-23 | T3.9f |
| REQ-ODDS-24 | T3.9f |
| REQ-ODDS-25 | *not built:* Not in v1: D-3 = A (numeric odds only); |
| REQ-ODDS-26 | *not built:* Not in v1: D-3 = A; |

**drill-fix** (12)

| Requirement | PLAN tasks |
|---|---|
| REQ-DRILL-01 | T3.7 |
| REQ-DRILL-02 | T3.7 |
| REQ-DRILL-03 | T3.7 |
| REQ-DRILL-04 | T3.7 |
| REQ-DRILL-05 | T3.7 |
| REQ-DRILL-06 | T3.7 |
| REQ-DRILL-07 | T3.0f, T3.7 |
| REQ-DRILL-08 | T3.7 |
| REQ-DRILL-09 | *not built:* Not built in 2.0.0: D-4 chose (a) only, so there is no Drill Fuel HUD. |
| REQ-DRILL-10 | *not built:* Not built in 2.0.0: D-4 (c) not chosen, so there is no fuel-lore parser. |
| REQ-DRILL-11 | *not built:* Not built in 2.0.0: D-4 (c) not chosen, so no drill HUD element is registered. |
| REQ-DRILL-12 | *not built:* Not built in 2.0.0: D-4 (c) not chosen, so there is no low-fuel warning. |

**updater** (20)

| Requirement | PLAN tasks |
|---|---|
| REQ-UPD-01 | T1.4 |
| REQ-UPD-02 | G4 (checkpoint) |
| REQ-UPD-03 | T1.4a, T7.2b |
| REQ-UPD-04 | T1.4a, T4.1 |
| REQ-UPD-05 | T1.4b |
| REQ-UPD-06 | T1.4b |
| REQ-UPD-07 | T1.4b, T4.4b |
| REQ-UPD-08 | T1.4, T4.1, T4.2, T4.4b, T4.5 |
| REQ-UPD-09 | T1.4a, T4.1, T4.2 |
| REQ-UPD-10 | T1.4, T4.4, T4.4b |
| REQ-UPD-11 | T4.4 |
| REQ-UPD-12 | T4.4 |
| REQ-UPD-13 | T4.2 |
| REQ-UPD-14 | T4.2 |
| REQ-UPD-15 | T4.2 |
| REQ-UPD-16 | T4.3, T4.5 |
| REQ-UPD-17 | T4.3, T4.5 |
| REQ-UPD-18 | T4.3b, T4.5 |
| REQ-UPD-19 | T1.4a, T1.4, T4.1, T4.4b |
| REQ-UPD-20 | T1.4a, T4.2, T4.5 |

**bestiary-hud** (17)

| Requirement | PLAN tasks |
|---|---|
| REQ-BEST-01 | T5.3 |
| REQ-BEST-02 | T5.2, T5.3 |
| REQ-BEST-03 | T5.1, T5.2 |
| REQ-BEST-04 | T5.1, T5.3 |
| REQ-BEST-05 | T5.2a, T5.2b, T5.2, T5.3 |
| REQ-BEST-06 | T5.2c, T5.3 |
| REQ-BEST-07 | T5.2c, T5.3 |
| REQ-BEST-08 | T5.3, T5.3b |
| REQ-BEST-09 | T5.2e, T5.3 |
| REQ-BEST-10 | T5.2a, T5.2b, T5.2 |
| REQ-BEST-11 | T5.2 |
| REQ-BEST-12 | T5.3 |
| REQ-BEST-13 | T5.2a, T5.2b, T5.2, T5.3 |
| REQ-BEST-14 | T5.2d |
| REQ-BEST-15 | T5.1 |
| REQ-BEST-16 | T5.1b |
| REQ-BEST-17 | T5.2a, T5.2b, T5.2, T5.2d, T5.3 |

**sbxp-optimizer** (58)

| Requirement | PLAN tasks |
|---|---|
| REQ-SBXP-01 | T6.0, T6.1, T6.2 |
| REQ-SBXP-02 | T6.0, T6.1 |
| REQ-SBXP-03 | T6.1, T6.2b, T6.3a, T6.3b, T6.3c |
| REQ-SBXP-04 | T6.0, T6.2b, T6.3a, T6.3b |
| REQ-SBXP-05 | T6.1 |
| REQ-SBXP-06 | T6.0, T6.1 |
| REQ-SBXP-07 | T6.2b |
| REQ-SBXP-08 | T6.5c |
| REQ-SBXP-09 | T6.3a, T6.3b, T6.3c, T6.5, T6.6 |
| REQ-SBXP-10 | T6.2, T6.2e, T6.2f |
| REQ-SBXP-58 | T6.0 |
| REQ-SBXP-11 | T6.1 |
| REQ-SBXP-12 | T6.1 |
| REQ-SBXP-13 | T6.1 |
| REQ-SBXP-14 | T6.1 |
| REQ-SBXP-15 | T6.1, T6.6 |
| REQ-SBXP-16 | T6.1, T6.6 |
| REQ-SBXP-17 | T6.1b |
| REQ-SBXP-18 | T6.1c |
| REQ-SBXP-19 | T6.1b, T6.3a, T6.3c |
| REQ-SBXP-20 | T6.1b |
| REQ-SBXP-21 | T6.1b |
| REQ-SBXP-22 | T6.1b, T6.3a, T6.5c |
| REQ-SBXP-23 | T6.1c, T6.5c, T6.8b |
| REQ-SBXP-24 | T6.6, T6.6c |
| REQ-SBXP-25 | T6.6 |
| REQ-SBXP-26 | T6.6 |
| REQ-SBXP-27 | T6.5b, T6.5c, T6.6, T6.6c, T6.8b |
| REQ-SBXP-28 | T6.3a, T6.6 |
| REQ-SBXP-29 | T6.6 |
| REQ-SBXP-30 | T6.6, T6.6c, T6.8 |
| REQ-SBXP-31 | T6.6b, T6.6c |
| REQ-SBXP-32 | T6.6b |
| REQ-SBXP-33 | T6.5, T6.6c |
| REQ-SBXP-34 | T6.5c |
| REQ-SBXP-35 | T6.5c, T6.8b |
| REQ-SBXP-36 | T6.5c |
| REQ-SBXP-37 | T6.5b, T6.5e, T6.5f, T6.6c, T6.8b |
| REQ-SBXP-38 | T6.5d |
| REQ-SBXP-39 | T6.5, T6.5d |
| REQ-SBXP-40 | T6.2c |
| REQ-SBXP-41 | T6.4 |
| REQ-SBXP-42 | T6.4 |
| REQ-SBXP-43 | T6.2c, T6.4 |
| REQ-SBXP-44 | T6.8 |
| REQ-SBXP-45 | T6.3a, T6.3b |
| REQ-SBXP-46 | T6.3a, T6.3b |
| REQ-SBXP-47 | T6.3a, T6.3b |
| REQ-SBXP-48 | T6.3a, T6.3b, T6.3c |
| REQ-SBXP-49 | T6.3a, T6.3b, T6.3c |
| REQ-SBXP-50 | T6.7, T6.8 |
| REQ-SBXP-51 | T6.7, T6.8 |
| REQ-SBXP-52 | T6.8 |
| REQ-SBXP-53 | T6.8b |
| REQ-SBXP-54 | T6.7, T6.8 |
| REQ-SBXP-55 | T6.9 |
| REQ-SBXP-56 | T6.8 |
| REQ-SBXP-57 | T6.11 |

**sbxp-update-skill** (15)

| Requirement | PLAN tasks |
|---|---|
| REQ-SKILL-01 | T6.10, T6.10b |
| REQ-SKILL-02 | T6.10 |
| REQ-SKILL-03 | T6.10 |
| REQ-SKILL-04 | T6.2d, T6.10 |
| REQ-SKILL-05 | T6.2d, T6.10 |
| REQ-SKILL-06 | T6.10 |
| REQ-SKILL-07 | T6.10 |
| REQ-SKILL-08 | T6.2d, T6.10 |
| REQ-SKILL-09 | T6.10 |
| REQ-SKILL-10 | T6.10, T6.10b |
| REQ-SKILL-11 | T6.2, T6.2e, T6.2d, T6.10b |
| REQ-SKILL-12 | T6.10 |
| REQ-SKILL-13 | T6.2d, T6.10, T6.10b |
| REQ-SKILL-14 | T6.2c, T6.10 |
| REQ-SKILL-15 | T6.10 |

**release** (17)

| Requirement | PLAN tasks |
|---|---|
| REQ-REL-01 | T1.16, T6.11, T7.1 |
| REQ-REL-02 | T1.16, T7.1, T7.2 |
| REQ-REL-03 | T1.16, T7.1, T7.1b |
| REQ-REL-04 | T1.16, T7.1, T7.1b |
| REQ-REL-05 | T1.14, T1.16, T1.17, T4.4b, T6.11, T7.1b, T7.2b |
| REQ-REL-06 | T1.15, T7.2, T7.2c |
| REQ-REL-07 | T1.15, T1.17, T7.2, T7.2b, T7.4 |
| REQ-REL-08 | T1.18, T7.3 |
| REQ-REL-09 | T1.18, T7.3 |
| REQ-REL-10 | T1.19, T7.4 |
| REQ-REL-11 | T1.17, T1.19, T7.2b, T7.4 |
| REQ-REL-12 | T7.4b |
| REQ-REL-13 | T7.2c, T7.4 |
| REQ-REL-14 | T1.15, T1.16, T1.18, T1.19 |
| REQ-REL-15 | T0.5 |
| REQ-REL-16 | T1.17, T7.2c |
| REQ-REL-17 | G7 (checkpoint) |

**cross-cutting (§9)** (23)

| Requirement | PLAN tasks |
|---|---|
| REQ-XC-RULES-01 | T0.6, T1.4, T6.5, T6.5b, T6.5e, T6.5f |
| REQ-XC-RULES-02 | T0.6, T3.0g, T6.5 |
| REQ-XC-RULES-03 | T1.10, T3.0b |
| REQ-XC-RULES-04 | T0.4b, T1.8, T3.8c |
| REQ-XC-RULES-05 | T1.11, T3.0b |
| REQ-XC-RULES-06 | every checkpoint report (checkpoint) |
| REQ-XC-RULES-07 | T0.6, T2.1, T7.1 |
| REQ-XC-LICENSE-01 | T0.1 |
| REQ-XC-LICENSE-02 | T1.10 |
| REQ-XC-LICENSE-03 | T3.0j, T3.9g, T5.1, T6.2f, T6.11, T7.1b |
| REQ-XC-LICENSE-04 | T2.2 |
| REQ-XC-LICENSE-05 | T0.1, T0.6, T6.2, T6.2f, T6.3a |
| REQ-XC-TOGGLE-01 | T1.11, T2.1, T2.4a, T5.3, T6.8 |
| REQ-XC-TOGGLE-02 | T1.11, T1.3b, T2.1, T4.4b, T5.3, T6.8, T7.1 |
| REQ-XC-PLAN-01 | T0.1, T6.0 |
| REQ-XC-GIT-01 | T0.0, T7.3 |
| REQ-XC-GIT-02 | T0.0, T1.18, T6.10, T7.3, T7.4 |
| REQ-XC-BUILD-01 | T0.0, T6.2b, T7.3 |
| REQ-XC-VERIFY-01 | T0.4, T0.4c |
| REQ-XC-VERIFY-02 | T0.2, T0.3, T0.4, T0.4c, T0.4b, T1.5, T1.6, T3.0m |
| REQ-XC-SKILLS-01 | every checkpoint report, G7 (checkpoint) |
| REQ-XC-REPORT-01 | every checkpoint report, G6, G7 (checkpoint) |
| REQ-XC-PRIVACY-01 | T0.0, T0.1, T0.4b, T0.4d, T1.17, T3.0a, T6.2c, T6.3a, T7.2b, T7.3 |

### 13.2 PLAN task → requirements

| Task | Title | Requirements |
|---|---|---|
| T0.0 | chore(git): repo-local identity, pre-commit privacy scan, per-commit build script | REQ-XC-GIT-01, REQ-XC-GIT-02, REQ-XC-PRIVACY-01, REQ-XC-BUILD-01 |
| T0.1 | docs: add SPEC.md, PLAN.md and the docs/sbxp/ schema draft | REQ-XC-PRIVACY-01, REQ-XC-PLAN-01, REQ-XC-LICENSE-01, REQ-XC-LICENSE-05 |
| T0.5 | docs(release): rules warning on the v1.0.0 and v1.0.1 release notes | REQ-REL-15 |
| T0.6 | ci: forbidden-reference check | REQ-XC-LICENSE-05, REQ-XC-RULES-01, REQ-XC-RULES-02, REQ-XC-RULES-07, REQ-PORT-12 |
| T0.2 | refactor: test seams, no behaviour change | REQ-XC-VERIFY-02, REQ-GLOW-01, REQ-CFG-03 |
| T0.3 | test: JUnit 5 + characterization tests | REQ-XC-VERIFY-02, REQ-GLOW-01, REQ-CFG-03, REQ-CFG-01 |
| T0.4 | build: dev runtime + /ksu debug island + tab/sidebar dumps | REQ-XC-VERIFY-02, REQ-XC-VERIFY-01, REQ-LOC-08, REQ-GS-13, REQ-GS-12 |
| T0.4c | test(gametest): client gametest source set + 1.0.1 label baseline | REQ-XC-VERIFY-02, REQ-XC-VERIFY-01, REQ-LOC-08, REQ-GS-13 |
| T0.4b | feat(debug): armed container dump + entity name-tag dump | REQ-XC-PRIVACY-01, REQ-XC-VERIFY-02, REQ-GS-13, REQ-GS-12, REQ-XC-RULES-04 |
| T1.1 | refactor(npcsearch): submit-based waypoint renderer | REQ-PORT-12, REQ-PORT-05, REQ-PORT-06 |
| T1.2 | build!: target Minecraft 26.2 | REQ-PORT-05, REQ-PORT-06, REQ-PORT-01, REQ-PORT-02, REQ-PORT-03, REQ-PORT-04, REQ-PORT-08 |
| T1.5 | build(test): client gametest source set | REQ-XC-VERIFY-02 |
| T1.6 | build: production-boot task | REQ-XC-VERIFY-02 |
| T1.3 | fix(npcsearch): see-through labels after translucent terrain | REQ-PORT-06, REQ-PORT-07, REQ-MARK-03 |
| T1.13 | fix(lang): keybind category translation key | REQ-PORT-11, REQ-CFG-13 |
| T1.7 | fix(config): atomic, single-path saving | REQ-CFG-04, REQ-CFG-05, REQ-CFG-10, REQ-CFG-12 |
| T1.7c | fix(config): configVersion + ordered migration runner | REQ-CFG-12, REQ-CFG-02, REQ-CFG-09 |
| T1.7b | fix(config): safe load — backups, per-section recovery, clean-up | REQ-CFG-03, REQ-CFG-01, REQ-CFG-06, REQ-CFG-07, REQ-CFG-08, REQ-CFG-11 |
| T1.8 | fix(highlight)!: never highlight invisible entities | REQ-XC-RULES-04, REQ-GLOW-03 |
| T1.8b | fix(highlight): inert invalid rules, no player matches | REQ-GLOW-10, REQ-GLOW-11 |
| T1.9 | fix(location): correct mode table + location snapshot + change events | REQ-LOC-08, REQ-LOC-01, REQ-LOC-02, REQ-LOC-03, REQ-LOC-04, REQ-LOC-05, REQ-LOC-06, REQ-LOC-07, REQ-LOC-09, REQ-LOC-10 |
| T1.9b | fix(location): Dungeon Hub migration + NPC data normalisation | REQ-CFG-09, REQ-LOC-03, REQ-GLOW-13 |
| T1.10a | refactor(highlight): match on the client tick, render reads a cache | REQ-GLOW-01, REQ-GLOW-06, REQ-GLOW-08 |
| T1.10 | feat(highlight)!: depth-tested glow via Render Chest | REQ-PORT-08, REQ-GLOW-02, REQ-GLOW-04, REQ-GLOW-05, REQ-GLOW-07, REQ-GLOW-16, REQ-GLOW-17, REQ-XC-RULES-03, REQ-XC-LICENSE-02 |
| T1.11 | fix(npcsearch): "You found X" only after line of sight, once per run | REQ-GLOW-14, REQ-XC-RULES-05, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02 |
| T1.12 | fix(net): HTTP timeouts + DB entry validation | REQ-NPCDB-05 |
| T1.4a | refactor(update): GitHub release source + check scheduling | REQ-CFG-12, REQ-UPD-03, REQ-UPD-04, REQ-UPD-09, REQ-UPD-19, REQ-UPD-20 |
| T1.4b | feat(update): SemVer compare + asset selection | REQ-UPD-05, REQ-UPD-06, REQ-UPD-07 |
| T1.4 | feat(update)!: notify-only GitHub check replaces the Modrinth updater | REQ-XC-RULES-01, REQ-UPD-19, REQ-UPD-01, REQ-UPD-08, REQ-UPD-10, REQ-PORT-10 |
| T1.14 | docs(changelog): start CHANGELOG.md | REQ-PORT-09, REQ-GLOW-15, REQ-REL-05 |
| T1.8c | fix(highlight): a rule that ignores names needs an entity type | REQ-GLOW-10 |
| T0.4d | fix(debug): mask real player names in captures | REQ-GS-13, REQ-XC-PRIVACY-01 |
| T1.11b | fix(npcsearch): "You found" title only for the five special NPCs | REQ-GLOW-14, REQ-GLOW-15 |
| T1.3b | feat(npcsearch): white waypoint labels by default | REQ-NPCWP-02, REQ-NPCWP-08, REQ-PORT-06, REQ-PORT-09, REQ-XC-TOGGLE-02 |
| T1.15 | build(release): version 1.1.0 + <version>+<mc> jar naming + sidecar | REQ-REL-06, REQ-REL-07, REQ-REL-14 |
| T1.16 | docs(release): README, CHANGELOG and mod description for v1.1.0 | REQ-PORT-09, REQ-GLOW-15, REQ-REL-05, REQ-REL-14, REQ-REL-01, REQ-REL-02, REQ-REL-03, REQ-REL-04 |
| T1.17 | build(release): release check + RELEASING.md | REQ-XC-PRIVACY-01, REQ-REL-05, REQ-REL-07, REQ-REL-11, REQ-REL-16 |
| T1.18 | chore(release): intermediate PR update/26.2 → main for v1.1.0 | REQ-XC-GIT-02, REQ-REL-14, REQ-REL-08, REQ-REL-09 |
| T1.19 | chore(release): tag and publish v1.1.0 | REQ-REL-14, REQ-REL-11, REQ-REL-10 |
| T2.1 | refactor(module): option model independent of Cloth | REQ-XC-RULES-07, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02, REQ-GLOW-15, REQ-UI-05, REQ-UI-07, REQ-UI-08, REQ-UI-19 |
| T2.2 | feat(ui): render kit | REQ-UI-17, REQ-UI-02, REQ-UI-24, REQ-XC-LICENSE-04 |
| T2.3a | feat(ui): basic widgets | REQ-UI-17, REQ-UI-06, REQ-UI-03, REQ-UI-22 |
| T2.3b | feat(ui): advanced widgets | REQ-UI-06, REQ-UI-22, REQ-UI-13, REQ-UI-14 |
| T2.3c | feat(ui): tooltip helper + virtual-row list | REQ-UI-19, REQ-UI-20 |
| T2.3d | feat(ui): notices (toasts) | REQ-UI-21 |
| T2.4a | feat(ui): config screen layout | REQ-XC-TOGGLE-01, REQ-UI-05, REQ-UI-07, REQ-UI-17, REQ-UI-02, REQ-UI-03, REQ-UI-01, REQ-UI-04 |
| T2.4b | feat(ui): search | REQ-UI-08, REQ-UI-22 |
| T2.4c | feat(ui): live-apply save model | REQ-CFG-10, REQ-UI-15 |
| T2.4d | feat(ui): General category | REQ-UI-17, REQ-UI-14, REQ-UI-21, REQ-UI-04, REQ-UI-16 |
| T2.5a | feat(ui): rule lists | REQ-GLOW-10, REQ-LOC-09, REQ-UI-15, REQ-UI-09, REQ-UI-11, REQ-UI-12 |
| T2.5c | feat(ui): database picker | REQ-UI-20, REQ-UI-11 |
| T2.5b | refactor(ui): switch entry points, remove the modules' Cloth code | REQ-UI-10, REQ-UI-23, REQ-UI-25 |
| T2.6 | build(ui)!: remove Cloth Config | REQ-CFG-03, REQ-CFG-13, REQ-UI-14, REQ-UI-16, REQ-UI-23 |
| T2.7 | feat(hud): HUD framework | REQ-CFG-12, REQ-HUD-01, REQ-HUD-02, REQ-HUD-03, REQ-HUD-04, REQ-HUD-05, REQ-HUD-10, REQ-HUD-11 |
| T2.7b | feat(hud): multi-line text element + default-layout check | REQ-HUD-12, REQ-HUD-13 |
| T2.8 | feat(hud): HUD editor | REQ-HUD-02, REQ-HUD-05, REQ-HUD-07, REQ-HUD-08, REQ-HUD-09 |
| T2.8b | feat(hud): editor entry points | REQ-UI-04, REQ-UI-10, REQ-HUD-06 |
| T2.9 | feat(ui): open and category-switch animations | REQ-UI-18 |
| T2.9b | feat(ui): keyboard focus navigation | REQ-UI-18 |
| T2.9c | feat(hud): snapping + guide lines in the HUD editor | REQ-HUD-14 |
| T2.9d | feat(ui): anti-aliased rounded corners | REQ-UI-18 |
| T3.0a | feat(skyblock): reader base + tab-widget and sidebar readers | REQ-XC-PRIVACY-01, REQ-GS-12, REQ-GS-01, REQ-GS-02, REQ-GS-03, REQ-GS-10, REQ-GS-11, REQ-GS-14, REQ-GS-15, REQ-GS-16 |
| T3.0g | feat(skyblock): chat + action-bar listener | REQ-XC-RULES-02, REQ-GS-14, REQ-GS-04, REQ-GS-05 |
| T3.0h | feat(skyblock): inventory counter by SkyBlock id | REQ-GS-07 |
| T3.0c | feat(data): shared bundled-data registry (runtime) | REQ-DATA-01, REQ-DATA-02, REQ-DATA-07, REQ-DATA-08, REQ-DATA-09, REQ-DATA-11, REQ-DATA-13 |
| T3.0i | build(data): table validator in ./gradlew check | REQ-DATA-02, REQ-DATA-03, REQ-DATA-04, REQ-DATA-06, REQ-DATA-12 |
| T3.0j | build(data): licence enforcement for bundled data | REQ-DATA-05, REQ-XC-LICENSE-03 |
| T3.0k | feat(data): layered values | REQ-DATA-01, REQ-DATA-10 |
| T3.0m | feat(skyblock): mineshaft state + shared widget hint | REQ-XC-VERIFY-02, REQ-LOC-08, REQ-GS-13, REQ-GS-14, REQ-GS-15, REQ-GS-16, REQ-GS-17, REQ-GS-18 |
| T3.0d | feat(skyblock): profile service + per-profile store | REQ-GS-14, REQ-GS-08, REQ-GS-09 |
| T3.0e | feat(skyblock): read-only menu reader | REQ-GS-13, REQ-GS-12, REQ-GS-14, REQ-GS-15, REQ-GS-06 |
| T3.0b | feat(render): world marker toolkit, labels + providers | REQ-MARK-03, REQ-XC-RULES-03, REQ-XC-RULES-05, REQ-MARK-01, REQ-MARK-02, REQ-MARK-05, REQ-MARK-06, REQ-MARK-07, REQ-MARK-08 |
| T3.0n | feat(render): beacon beams + rings | REQ-MARK-01, REQ-MARK-02, REQ-MARK-07, REQ-MARK-04, REQ-MARK-09 |
| T3.0f | build(mixin): optional cosmetic mixin config + hook status | REQ-BOB-08, REQ-HOT-13, REQ-DRILL-07 |
| T3.8a | docs(data): NPC & mob list audit | REQ-NPCDB-01, REQ-NPCDB-02 |
| T3.8 | feat(data): bundle NPC & mob databases [brief 3.9] | REQ-DATA-01, REQ-DATA-13, REQ-NPCDB-03, REQ-NPCDB-04, REQ-NPCDB-06, REQ-NPCDB-09, REQ-NPCDB-10 |
| T3.8b | data(npcdb): NPC & mob data corrections | REQ-NPCDB-07, REQ-NPCDB-08 |
| T3.8c | feat(ui): invisible-by-design mobs greyed in the picker | REQ-XC-RULES-04, REQ-UI-11, REQ-NPCDB-09, REQ-NPCDB-08 |
| T3.1 | fix(highlight): verify Trinity/Tomioka/Duncan glow [brief 3.1] | REQ-GLOW-13, REQ-GLOW-09, REQ-GLOW-12 |
| T3.2 | feat(mining): mineshaft entry alert [brief 3.2] | REQ-MSA-01, REQ-MSA-02, REQ-MSA-03, REQ-MSA-04, REQ-MSA-05, REQ-MSA-06, REQ-MSA-07, REQ-MSA-08, REQ-MSA-09, REQ-MSA-10, REQ-MSA-11, REQ-MSA-12 |
| T3.3a | feat(data): corpse spot table [brief 3.3] [dropped R22] | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was REQ-XC-LICENSE-03, REQ-CORPSE-01, REQ-CORPSE-09, REQ-CORPSE-10) |
| T3.3 | feat(mining): possible corpse spots [brief 3.3] [dropped R22] | *dropped R22:* the maintainer dropped corpse-spot waypoints after G1 (was REQ-CORPSE-02, REQ-CORPSE-03, REQ-CORPSE-04, REQ-CORPSE-05, REQ-CORPSE-06, REQ-CORPSE-07, REQ-CORPSE-08) |
| T3.4 | feat(waypoints): Skyblocker-style NPC waypoints [brief 3.4] | REQ-NPCWP-01, REQ-NPCWP-02, REQ-NPCWP-03, REQ-NPCWP-04, REQ-NPCWP-05, REQ-NPCWP-07, REQ-NPCWP-08, REQ-NPCWP-10, REQ-NPCWP-11, REQ-NPCWP-12 |
| T3.4b | feat(waypoints): beam colour settings + migration | REQ-NPCWP-05, REQ-NPCWP-06, REQ-NPCWP-09 |
| T3.5 | feat(fishing): bobber rubber-band fix [brief 3.5] | REQ-BOB-08, REQ-BOB-01, REQ-BOB-02, REQ-BOB-03, REQ-BOB-04, REQ-BOB-05, REQ-BOB-06, REQ-BOB-07 |
| T3.6 | feat(fishing): hotspot detection + ring [brief 3.6] | REQ-HOT-13, REQ-HOT-01, REQ-HOT-02, REQ-HOT-03, REQ-HOT-04, REQ-HOT-05, REQ-HOT-06, REQ-HOT-12 |
| T3.6b | feat(fishing): "hotspot gone" warning | REQ-HOT-12, REQ-HOT-07, REQ-HOT-08, REQ-HOT-09, REQ-HOT-10, REQ-HOT-11 |
| T3.7 | feat(mining): drill re-equip fix [brief 3.8] | REQ-DRILL-07, REQ-DRILL-01, REQ-DRILL-02, REQ-DRILL-03, REQ-DRILL-04, REQ-DRILL-05, REQ-DRILL-06, REQ-DRILL-08 |
| T3.9a | feat(odds): odds math + "1 in N (P%)" format | REQ-ODDS-06, REQ-ODDS-08 |
| T3.9g | feat(odds): bundled drop tables (two MIT, one CC0) | REQ-XC-LICENSE-03, REQ-ODDS-09, REQ-ODDS-10 |
| T3.9h | feat(odds): Rare Drop Odds module | REQ-ODDS-01, REQ-ODDS-02, REQ-ODDS-03, REQ-ODDS-04, REQ-ODDS-07, REQ-ODDS-11, REQ-ODDS-12 |
| T3.9b | feat(odds): Scatha case | REQ-ODDS-10, REQ-ODDS-05, REQ-ODDS-13, REQ-ODDS-14, REQ-ODDS-15, REQ-ODDS-16 |
| T3.9c | feat(odds): Croesus / dungeon reward chest tooltips | REQ-ODDS-17, REQ-ODDS-18, REQ-ODDS-19 |
| T3.9d | feat(odds): Frozen Corpse odds panel + loot parser, Vanguard | REQ-ODDS-10, REQ-ODDS-05, REQ-ODDS-20, REQ-ODDS-21, REQ-ODDS-22 |
| T3.9e | feat(odds): Lapis / Umber / Tungsten corpse case | REQ-ODDS-10, REQ-ODDS-20, REQ-ODDS-21, REQ-ODDS-22 |
| T3.9f | feat(odds): slayer RNG meter case | REQ-ODDS-05, REQ-ODDS-23, REQ-ODDS-24 |
| T4.1 | feat(update): check outcomes, manual checks and state | REQ-CFG-12, REQ-UPD-04, REQ-UPD-09, REQ-UPD-19, REQ-UPD-08 |
| T4.2 | feat(update): secure download + integrity + jar validation | REQ-UPD-09, REQ-UPD-20, REQ-UPD-08, REQ-UPD-13, REQ-UPD-14, REQ-UPD-15 |
| T4.3 | feat(update): install eligibility + staged install via helper | REQ-UPD-16, REQ-UPD-17 |
| T4.3b | feat(update): launch reconciliation | REQ-UPD-18 |
| T4.4 | feat(update): notices, confirmation screen and /ksu update | REQ-UPD-10, REQ-UI-21, REQ-UI-10, REQ-UPD-11, REQ-UPD-12 |
| T4.4b | feat(update): General → Updates card | REQ-XC-TOGGLE-02, REQ-UPD-19, REQ-UPD-07, REQ-UPD-08, REQ-UPD-10, REQ-REL-05, REQ-UI-04 |
| T4.5 | test(update): Windows integration | REQ-UPD-20, REQ-UPD-08, REQ-UPD-16, REQ-UPD-17, REQ-UPD-18 |
| T5.1 | feat(bestiary): family table + tier math | REQ-XC-LICENSE-03, REQ-BEST-15, REQ-BEST-03, REQ-BEST-04 |
| T5.1b | feat(bestiary): family name resolver | REQ-BEST-16 |
| T5.2a | feat(bestiary): tab-widget and chat parsers | REQ-BEST-10, REQ-BEST-05, REQ-BEST-17, REQ-BEST-13 |
| T5.2b | feat(bestiary): Bestiary menu parser | REQ-BEST-10, REQ-BEST-05, REQ-BEST-17, REQ-BEST-13 |
| T5.2 | feat(bestiary): live session tracking | REQ-BEST-03, REQ-BEST-10, REQ-BEST-05, REQ-BEST-17, REQ-BEST-13, REQ-BEST-02, REQ-BEST-11 |
| T5.2c | feat(bestiary): session stats + rate and ETA | REQ-BEST-06, REQ-BEST-07 |
| T5.2d | feat(bestiary): per-profile last-known values | REQ-BEST-17, REQ-BEST-14 |
| T5.2e | feat(bestiary): family selection | REQ-BEST-09 |
| T5.3 | feat(bestiary): HUD element + Bestiary Tracker card | REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02, REQ-HUD-13, REQ-GS-18, REQ-BEST-04, REQ-BEST-05, REQ-BEST-17, REQ-BEST-13, REQ-BEST-02, REQ-BEST-06, REQ-BEST-07, REQ-BEST-09, REQ-BEST-01, REQ-BEST-08, REQ-BEST-12 |
| T5.3b | feat(bestiary): compact one-line layout | REQ-BEST-08 |
| T6.0 | docs(sbxp): align tasks.schema.json with the decisions + your re-approval | REQ-XC-PLAN-01, REQ-DATA-02, REQ-DATA-03, REQ-DATA-06, REQ-SBXP-58, REQ-SBXP-01, REQ-SBXP-02, REQ-SBXP-04, REQ-SBXP-06 |
| T6.1 | feat(sbxp): task model, loader, time and coin math | REQ-DATA-13, REQ-SBXP-01, REQ-SBXP-02, REQ-SBXP-06, REQ-SBXP-03, REQ-SBXP-05, REQ-SBXP-11, REQ-SBXP-12, REQ-SBXP-13, REQ-SBXP-14, REQ-SBXP-15, REQ-SBXP-16 |
| T6.1b | feat(sbxp): options, cost classes, currencies, stage lookup, confidence | REQ-SBXP-17, REQ-SBXP-19, REQ-SBXP-20, REQ-SBXP-21, REQ-SBXP-22 |
| T6.1c | feat(sbxp): XP crediting + override precedence | REQ-SBXP-18, REQ-SBXP-23 |
| T6.2 | build(sbxp): data tool + skills and collections tables | REQ-XC-LICENSE-05, REQ-SBXP-01, REQ-SBXP-10, REQ-SKILL-11 |
| T6.2e | data(sbxp): museum and minion reference tables | REQ-SBXP-10, REQ-SKILL-11 |
| T6.2f | data(sbxp): NEU-derived reference tables | REQ-XC-LICENSE-05, REQ-XC-LICENSE-03, REQ-SBXP-10 |
| T6.2b | build(sbxp): task-table validator in ./gradlew check | REQ-XC-BUILD-01, REQ-SBXP-04, REQ-SBXP-03, REQ-SBXP-07 |
| T6.2c | build(sbxp): bundled prices (sbxpData --prices) | REQ-XC-PRIVACY-01, REQ-SBXP-40, REQ-SBXP-43, REQ-SKILL-14 |
| T6.2d | feat(tools): update-diff report for the skill | REQ-SKILL-11, REQ-SKILL-04, REQ-SKILL-05, REQ-SKILL-08, REQ-SKILL-13 |
| T6.3a | data(sbxp): task table + research log, batch 1 (top 10 families) | REQ-XC-PRIVACY-01, REQ-XC-LICENSE-05, REQ-SBXP-04, REQ-SBXP-03, REQ-SBXP-19, REQ-SBXP-22, REQ-SBXP-09, REQ-SBXP-28, REQ-SBXP-45, REQ-SBXP-46, REQ-SBXP-47, REQ-SBXP-48, REQ-SBXP-49 |
| T6.3b | data(sbxp): batch 2 (next 20 families) | REQ-SBXP-04, REQ-SBXP-03, REQ-SBXP-09, REQ-SBXP-45, REQ-SBXP-46, REQ-SBXP-47, REQ-SBXP-48, REQ-SBXP-49 |
| T6.3c | data(sbxp): batch 3 (the rest) + coverage report | REQ-SBXP-03, REQ-SBXP-19, REQ-SBXP-09, REQ-SBXP-48, REQ-SBXP-49 |
| T6.4 | feat(sbxp): live Bazaar prices | REQ-SBXP-43, REQ-SBXP-41, REQ-SBXP-42 |
| T6.5 | feat(sbxp): /sblevels menu parser | REQ-XC-RULES-01, REQ-XC-RULES-02, REQ-SBXP-09, REQ-SBXP-33, REQ-SBXP-39 |
| T6.5b | feat(sbxp): component-progress model + Skills and Collections parsers | REQ-XC-RULES-01, REQ-SBXP-37, REQ-SBXP-27 |
| T6.5e | feat(sbxp): Museum and HOTM/HOTF parsers | REQ-XC-RULES-01, REQ-SBXP-37 |
| T6.5f | feat(sbxp): Pets and Accessory Bag parsers | REQ-XC-RULES-01, REQ-SBXP-37 |
| T6.5c | feat(sbxp): per-profile optimizer state | REQ-SBXP-22, REQ-SBXP-23, REQ-SBXP-27, REQ-SBXP-34, REQ-SBXP-35, REQ-SBXP-36, REQ-SBXP-08 |
| T6.5d | feat(sbxp): live progress deltas | REQ-SBXP-39, REQ-SBXP-38 |
| T6.6 | feat(sbxp): ranking, views + prerequisite resolver | REQ-SBXP-15, REQ-SBXP-16, REQ-SBXP-09, REQ-SBXP-28, REQ-SBXP-27, REQ-SBXP-24, REQ-SBXP-25, REQ-SBXP-26, REQ-SBXP-29, REQ-SBXP-30 |
| T6.6b | feat(sbxp): plan to target level | REQ-SBXP-31, REQ-SBXP-32 |
| T6.6c | test(sbxp): golden rankings + recalculation | REQ-SBXP-33, REQ-SBXP-37, REQ-SBXP-27, REQ-SBXP-24, REQ-SBXP-30, REQ-SBXP-31 |
| T6.7 | feat(ui): optimizer table + inputs | REQ-UI-20, REQ-SBXP-50, REQ-SBXP-51, REQ-SBXP-54 |
| T6.8 | feat(sbxp): optimizer screen + "SkyBlock XP" config category | REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02, REQ-UI-04, REQ-SBXP-30, REQ-SBXP-50, REQ-SBXP-51, REQ-SBXP-54, REQ-SBXP-44, REQ-SBXP-52, REQ-SBXP-56 |
| T6.8b | feat(sbxp): row details + editors | REQ-SBXP-23, REQ-SBXP-37, REQ-SBXP-27, REQ-SBXP-35, REQ-SBXP-53 |
| T6.9 | feat(hud): "next best task" HUD element | REQ-HUD-13, REQ-SBXP-55 |
| T6.10 | feat(skill): .claude/skills/update-sbxp-table/ | REQ-XC-GIT-02, REQ-SKILL-14, REQ-SKILL-04, REQ-SKILL-05, REQ-SKILL-08, REQ-SKILL-13, REQ-SKILL-01, REQ-SKILL-02, REQ-SKILL-03, REQ-SKILL-06, REQ-SKILL-07, REQ-SKILL-09, REQ-SKILL-10, REQ-SKILL-12, REQ-SKILL-15 |
| T6.10b | test(skill): recorded fixtures, dry run, eval loop, one real run | REQ-SKILL-11, REQ-SKILL-13, REQ-SKILL-01, REQ-SKILL-10 |
| T6.11 | docs(sbxp): README, CHANGELOG, THIRD_PARTY_NOTICES | REQ-REL-05, REQ-REL-01, REQ-XC-LICENSE-03, REQ-SBXP-57 |
| T7.1 | docs(readme): README for 2.0.0 | REQ-XC-RULES-07, REQ-XC-TOGGLE-02, REQ-GLOW-15, REQ-REL-01, REQ-REL-02, REQ-REL-03, REQ-REL-04 |
| T7.1b | docs(changelog): 2.0.0 section + THIRD_PARTY_NOTICES final pass | REQ-REL-05, REQ-REL-03, REQ-REL-04, REQ-DATA-05, REQ-XC-LICENSE-03 |
| T7.2 | chore(release): version 2.0.0 | REQ-REL-06, REQ-REL-07, REQ-REL-02 |
| T7.2b | build(release): pre-publish checks through the updater's own code | REQ-XC-PRIVACY-01, REQ-UPD-03, REQ-REL-05, REQ-REL-07, REQ-REL-11, REQ-DATA-05 |
| T7.2c | docs(release): extend RELEASING.md (from T1.17) for 2.0.0 | REQ-REL-06, REQ-REL-16, REQ-REL-13 |
| T7.3 | chore(release): pull request update/26.2 → main | REQ-XC-GIT-01, REQ-XC-GIT-02, REQ-XC-PRIVACY-01, REQ-XC-BUILD-01, REQ-REL-08, REQ-REL-09 |
| T7.4 | chore(release): tag and publish v2.0.0 after your "ship" | REQ-XC-GIT-02, REQ-REL-07, REQ-REL-11, REQ-REL-10, REQ-HUD-10, REQ-NPCDB-10, REQ-REL-13 |
| T7.4b | test(release): end-to-end update from a never-published test build | REQ-REL-12 |

### 13.3 Comparison record: SPEC.md against the earlier PLAN.md

The first comparison of this spec with the earlier plan found 111 requirements without a task, 60 contradictions and 26 tasks that no requirement justified. The PLAN update resolved them as follows; each item was re-checked against the current files. This record predates G1: the items below that name T3.3, T3.3a, corpse spots or `/ksu corpses` are kept as history, and R22 has since dropped those tasks (§12.3). Likewise, the items that place the sack-key fallback check (REQ-MSA-05) or the widget hint wording (REQ-GS-18, Q-MSA-04) at G1 are history: neither was answered there, and R24 moved both checks to G3.

**Gaps (111): all now have a task.** The task(s) are given per requirement:

- **location:** REQ-LOC-05 → T1.9 · REQ-LOC-07 → T1.9 · REQ-LOC-04 → T1.9 · REQ-LOC-09 → T1.9, T2.5a
- **config-store:** REQ-CFG-06 → T1.7b · REQ-CFG-07 → T1.7b · REQ-CFG-08 → T1.7b · REQ-CFG-10 → T1.7, T2.4c · REQ-CFG-11 → T1.7b · REQ-CFG-12 → T1.7, T1.7c, T1.4a, T2.7, T4.1
- **glow:** REQ-GLOW-09 → T3.1 · REQ-GLOW-10 → T1.8b, T2.5a · REQ-GLOW-11 → T1.8b · REQ-GLOW-15 → T1.14, T1.16, T2.1, T7.1
- **port-26-2:** REQ-PORT-12 → T0.6, T1.1 · REQ-PORT-08 → T1.2, T1.10
- **ui-config:** REQ-UI-21 → T2.3d, T2.4d, T4.4 · REQ-UI-09 → T2.5a · REQ-UI-10 → T2.5b, T2.8b, T4.4 · REQ-UI-12 → T2.5a · REQ-UI-17 → T2.2, T2.3a, T2.4a, T2.4d · REQ-UI-13 → T2.3b · REQ-UI-15 → T2.4c, T2.5a · REQ-UI-16 → T2.4d, T2.6 · REQ-UI-24 → T2.2
- **hud:** REQ-HUD-02 → T2.7, T2.8 · REQ-HUD-06 → T2.8b · REQ-HUD-08 → T2.8 · REQ-HUD-10 → T2.7, T7.4 · REQ-HUD-13 → T2.7b, T5.3, T6.9
- **npc-mob-data:** REQ-NPCDB-01 → T3.8a · REQ-NPCDB-02 → T3.8a · REQ-NPCDB-09 → T3.8, T3.8c · REQ-NPCDB-07 → T3.8b · REQ-NPCDB-06 → T3.8 · REQ-NPCDB-10 → T3.8, T7.4
- **data-registry:** REQ-DATA-05 → T3.0j, T7.1b, T7.2b · REQ-DATA-09 → T3.0c · REQ-DATA-10 → T3.0k · REQ-DATA-11 → T3.0c · REQ-DATA-12 → T3.0i
- **game-state:** REQ-GS-04 → T3.0g · REQ-GS-05 → T3.0g · REQ-GS-09 → T3.0d · REQ-GS-10 → T3.0a · REQ-GS-11 → T3.0a · REQ-GS-12 → T0.4, T0.4b, T3.0a, T3.0e · REQ-GS-15 → T3.0a, T3.0m, T3.0e · REQ-GS-16 → T3.0a, T3.0m · REQ-GS-01 → T3.0a
- **world-markers:** REQ-MARK-05 → T3.0b · REQ-MARK-07 → T3.0b, T3.0n · REQ-MARK-09 → T3.0n · REQ-MARK-02 → T3.0b, T3.0n
- **mineshaft-alert:** REQ-MSA-11 → T3.2 · REQ-MSA-01 → T3.2 · REQ-MSA-05 → T3.2 · REQ-MSA-08 → T3.2 · REQ-MSA-10 → T3.2
- **corpse-waypoints:** REQ-CORPSE-03 → T3.3 · REQ-CORPSE-01 → T3.3a · REQ-CORPSE-05 → T3.3 · REQ-CORPSE-04 → T3.3
- **npc-waypoints:** REQ-NPCWP-06 → T3.4b · REQ-NPCWP-09 → T3.4b · REQ-NPCWP-10 → T3.4 · REQ-NPCWP-02 → T3.4
- **bobber-fix:** REQ-BOB-05 → T3.5 · REQ-BOB-08 → T3.0f, T3.5
- **drill-fix:** REQ-DRILL-05 → T3.7 · REQ-DRILL-09 → *not built:* Not built in 2.0.0: D-4 chose (a) only, so there is no Drill Fuel HUD. · REQ-DRILL-03 → T3.7
- **fishing-hotspot:** REQ-HOT-13 → T3.0f, T3.6 · REQ-HOT-05 → T3.6 · REQ-HOT-11 → T3.6b · REQ-HOT-09 → T3.6b · REQ-HOT-10 → T3.6b
- **rare-drop-odds:** REQ-ODDS-10 → T3.9g, T3.9b, T3.9d, T3.9e · REQ-ODDS-12 → T3.9h · REQ-ODDS-15 → T3.9b · REQ-ODDS-17 → T3.9c · REQ-ODDS-14 → T3.9b · REQ-ODDS-25 → *not built:* Not in v1: D-3 = A (numeric odds only);
- **bestiary-hud:** REQ-BEST-10 → T5.2a, T5.2b, T5.2 · REQ-BEST-03 → T5.1, T5.2 · REQ-BEST-16 → T5.1b · REQ-BEST-14 → T5.2d
- **updater:** REQ-UPD-01 → T1.4 · REQ-UPD-02 → G4 (checkpoint) · REQ-UPD-03 → T1.4a, T7.2b · REQ-UPD-18 → T4.3b, T4.5
- **release:** REQ-REL-03 → T1.16, T7.1, T7.1b · REQ-REL-06 → T1.15, T7.2, T7.2c · REQ-REL-10 → T1.19, T7.4 · REQ-REL-11 → T1.17, T1.19, T7.2b, T7.4 · REQ-REL-14 → T1.15, T1.16, T1.18, T1.19 · REQ-REL-15 → T0.5 · REQ-REL-16 → T1.17, T7.2c
- **sbxp-optimizer:** REQ-SBXP-09 → T6.3a, T6.3b, T6.3c, T6.5, T6.6 · REQ-SBXP-32 → T6.6b · REQ-SBXP-30 → T6.6, T6.6c, T6.8 · REQ-SBXP-44 → T6.8 · REQ-SBXP-53 → T6.8b · REQ-SBXP-23 → T6.1c, T6.5c, T6.8b · REQ-SBXP-50 → T6.7, T6.8 · REQ-SBXP-57 → T6.11 · REQ-SBXP-36 → T6.5c
- **sbxp-update-skill:** REQ-SKILL-03 → T6.10 · REQ-SKILL-15 → T6.10 · REQ-SKILL-13 → T6.2d, T6.10, T6.10b · REQ-SKILL-07 → T6.10

**Contradictions (60):**

- **Brief Phase 3 ('each behind its own toggle') and research G3 (Trinity/Tomioka/Duncan glow default OFF with a per-NPC sub-toggle) vs PLAN §3 / T3.1 / D-6 ('your rules stay on', no dedicated toggle, verify only)** → resolved (R1 (SPEC §12.2), REQ-GLOW-13, AC-GLOW-12, glow out-of-scope 'dedicated module [decided R1]', §12.H, PLAN §3 row, T3.1, T1.9b): R1 = a decides that each NPC Search rule's own switch is the toggle and that a fresh install glows nothing. REQ-GLOW-13 and AC-GLOW-12 record this, and PLAN §3 ('your rules stay on') and T3.1 now match a decided requirement.
- **PLAN T1.11 ('reset on world/server change', Phase 1) vs PLAN T3.0a ('location change events', Phase 3)** → resolved (T1.9 (snapshot + change events, REQ-LOC-04/05), T1.11 (deps T1.9, T1.10; resets on each location change event), T3.0a (uses T1.9's events)): T1.9 now builds the location change events in Phase 1. T1.11 depends on T1.9 and resets on those events, and T3.0a only consumes them.
- **PLAN T1.8 accept ('gametest with an invisible named zombie') vs T1.8 dependencies (no dependency on T1.5, the gametest source set)** → resolved (T1.8 Accept (AC-GLOW-03 [A] eligibility unit test, 'the gametest is in T1.10'), T1.10 Accept (AC-GLOW-03 [C], AC-XC-02), T1.10 deps T1.5): T1.8 now proves only the unit-testable eligibility check. The invisible-zombie gametest moved to T1.10, which depends on the gametest source set T1.5.
- **Brief Phase 3 item 1 ('Verify it works on 26.2') vs PLAN T3.1 accept ('field check when a rare room appears (opportunistic)')** → resolved (T3.1 Accept, AC-GLOW-08 [E], AC-GLOW-11 [C][E], G1 smoke item '/ksu debug dump entities next to Mort...', T0.4b entity dump, G3 (rare room opportunistic)): Verification uses stand-ins: the resolver table, the G1 entity dump at Mort or a Dungeon Hub NPC, and a player-type stand-in gametest. The Phase 3 report must say 'not field-verified on the three NPCs' if no rare room appears.
- **PLAN T1.4 (fix the updater's hard-coded MC version) vs research read-core / port-probe (the Modrinth project returns 404 on every launch)** → resolved (T1.4 (retitled 'notify-only GitHub check replaces the Modrinth updater', REQ-PORT-10, REQ-UPD-01), T1.4a, T1.4b, R4, R16 'replace T1.4'): T1.4 no longer patches the dead updater. It deletes the Modrinth UpdateChecker and ships a working notify-only GitHub check, which is an observable change backed by R4/R16.
- **PLAN §7 Phase 2 spec ('`/ksu [search term]` opens the screen pre-filtered') vs T2.8 (`/ksu hud`), T0.4 (`/ksu debug …`), T3.3 (`/ksu corpses`), T4.4 (`/ksu update …`), T6.8 (`/ksu sbxp`)** → resolved (REQ-UI-10 (reserved words hud, debug, corpses, update, sbxp, plus later ones), PLAN Phase 2 spec bullet, T2.5b (central list), T2.8b, T3.3, T4.4, T6.8): REQ-UI-10 and T2.5b say that text starting with a reserved subcommand word runs that subcommand and any other text becomes the search term. The words are kept in one central list that T3.3, T4.4 and T6.8 extend.
- **T2.9 'multi-select dropdown' (stretch, after G2) vs T6.7 'multi-select filter' (required for the addendum's category filters)** → resolved (PLAN Phase 2 stretch header ('The multi-select dropdown moved to T6.7'), T2.9 (animations only), T6.7 ('Owns the multi-select widget'), REQ-UI-18, R16): The multi-select widget was removed from T2.9 and is owned only by T6.7, as R16 decided and REQ-UI-18 states.
- **T2.9 'SDF rounded corners' vs AD-4 / D-8 ('rounded corners from fills, no private render APIs in v1') and AD-9 (no core mixin remains)** → resolved (T2.9d, AD-4, AD-9, D-8 ('T2.9 items as stretch goals'), REQ-UI-17, REQ-UI-18, ui-config out of scope, 'Not built in 2.0.0' (REQ-UI-18)): Anti-aliased corners are now an optional post-G2 stretch item, decided under D-8. They may use only public render APIs with no accessor mixin, and fill-based corners stay the default and fallback. The item is skipped if 26.2 has no public path, so AD-4 and AD-9 are no longer contradicted.
- **D-8 'live-apply with save on close' vs T2.8 HUD editor 'Cancel (revert) and Save'** → resolved (R12, REQ-HUD-08, EC-HUD-07, Q-HUD-01, T2.8 ('Esc and external closes act as Save'), PLAN Phase 2 spec bullet): The HUD editor stays staged with Cancel to revert. Esc and every external close act as Save (R12), and the file is written only on Save, close or reset, so the tests (AC-HUD-05) are unambiguous.
- **D-8 'toasts for warnings' vs Brief Phase 3 item 2 ('post a chat alert') and item 6 ('warn (title/sound/chat, configurable)')** → resolved (T2.3d, REQ-UI-21, Not built list): Fixed in the final pass: the mineshaft alert stays a chat line and is no longer listed as a notice consumer; only the hotspot-gone warning (T3.6b) offers the optional notice channel.
- **T2.3c acceptance ('a 3,000-row list scrolls without dropping frames') vs the testable-criteria rule, and research GAP-1 §8 (5,000-row unit test)** → resolved (T2.3c Accept AC-UI-18 [A], AC-UI-18 (SPEC), T2.5c Accept AC-UI-18 [C]): The criterion is now objective: 5,000 rows of 18 px in a 200 px viewport give at most 13 laid-out rows and correct index hit-testing ([A]), plus an NPC picker scroll screenshot sequence ([C]).
- **research-alpaka-ui §15 proposed information architecture vs PLAN §3 P1 and D-12** → resolved (T2.4a ('Placement follows REQ-UI-04, which supersedes the research IA (no through-wall or "Refresh now" option)'), ui-config out of scope (no depth-test-off option [D-1], no remote source or 'Refresh now' [D-12])): SPEC lists both research-IA options as out of scope, and T2.4a states that REQ-UI-04 supersedes the research IA.
- **PLAN D-26 / §6 and Brief Phase 3 item 9 + Addendum intro** → resolved (PLAN §6, §9 D-26, Q-DATA-02): Fixed in the final pass: D-26 and §6 now say the internal database is the brief 3.9 bundled database, generalised by T3.0c.
- **PLAN AD-7 envelope vs docs/sbxp/tasks.schema.json vs research GAP-1 §5** → resolved (REQ-DATA-02 (one pinned envelope; replaces AD-7's 'map or list'), AD-7 (now pinned to REQ-DATA-02), T3.0c, T3.0i (fails on a sources list, non-integer dataVersion, licence outside the enum), AC-DATA-02, T6.0 (aligns tasks.schema.json), PLAN §10 envelope row): One envelope is pinned: integer dataVersion, licence in CC0-1.0/MIT/Apache-2.0 with facts status kept on source records, and sources as an id-keyed map. AD-7, the T3.0c/T3.0i validator and the T6.0 schema alignment all reference it, which supersedes the GAP-1 shape.
- **PLAN T3.8 body vs T3.8 acceptance** → resolved (R5, REQ-NPCDB-08, AC-NPCDB-07, T3.8b ('flagged invisible-by-design and kept'), T3.8c, T2.5c, REQ-UI-11): R5 decides that the four mobs stay listed but are greyed, not addable and labelled 'never highlighted (Hypixel rules)'. Every task body and the acceptance (AC-NPCDB-07 [C]) now say the same.
- **PLAN T3.0a vs PLAN T1.9** → resolved (SPEC location header, T1.9): Fixed in the final pass: the location header names T1.9 as the single owner of the change events; T3.0a only consumes them.
- **PLAN §3 (row 'Action-bar / chat XP lines') vs what Fabric events can observe** → resolved (PLAN §3 action-bar row, EC-GS-04): Fixed in the final pass: the §3 row limits the claim to Fabric's message events and names the packet/mixin-level gap.
- **Brief Phase 3 item 2 ('how many of the matching keys the player owns') vs PLAN D-7 / T3.2 (inventory only)** → resolved (PLAN §3 mineshaft row, D-7): Fixed in the final pass: the §3 row now says keys in inventory.
- **PLAN T3.0b (deps T1.3 only) vs T1.1/T1.3 (NpcWaypointRenderer)** → resolved (T3.0b ('Extracts the T1.3 label renderer into the shared toolkit and switches NPC waypoints to it in the same task'; deps T1.3)): One label implementation remains: T3.0b extracts and generalises the T1.3 renderer and moves NPC waypoints onto it in the same task.
- **Brief Phase 3 item 2 ('how many of the matching keys the player owns') vs PLAN D-7 / T3.2 ('Keys counted from the inventory')** → resolved (D-7, Q-MSA-01, REQ-MSA-05 (incl. G1 sack fallback), REQ-MSA-06, REQ-MSA-09, EC-MSA-04, AC-MSA-13, T3.2, G1 'mineshaft check 2'): Inventory-only is confirmed and labelled 'n in inventory', with a hover about sacks. The sack-only case is a documented design choice (EC-MSA-04), and a G1 check switches to 'sack keys also work' with no red warning if sack keys open corpses.
- **PLAN T3.4 ('migration leaves existing colours as glow colours') vs Brief Phase 2 ('Migrate existing config values so users don't lose settings')** → resolved (R8, REQ-NPCWP-09, AC-NPCWP-02, T3.4b migration step): A fixed rule whose colour differs from 0x0AA351 becomes that rule's beam override, rules on the default green follow the island colour, and moving rules keep their glow colour, so no customised colour is lost.
- **PLAN D-15 (category = island) vs Brief item 4 ('individually colored beacon beam per waypoint')** → resolved (D-15, Q-NPCWP-01, SPEC §12.1 'Consequences of D-15', REQ-NPCWP-05/06, T3.4, T3.4b (per-rule beam colour, 'Use island colour')): The user accepted D-15 with the consequence spelled out: beams on one island share the island colour until per-NPC colours are set, with a palette alternative offered. T3.4b builds the per-rule override.
- **PLAN T3.3 dependencies (T3.0a, T3.0b, T3.0c, D-5) vs T3.3 content (needs the shaft code and Frozen Corpses LOOTED state)** → resolved (T3.0m (game-state owns the shaft code + Frozen Corpses for T3.2, T3.3, T3.9), T3.3 deps T3.0m, T3.2 ('no code parser of its own'), REQ-GS-17, REQ-MSA-02/03): Parsing moved into game-state task T3.0m, and T3.3 depends on it directly, so it no longer relies on T3.2. The SPEC game-state header still says 'T3.0a (including the shaft-code ... parsing)', a stale task id, but T3.0a is still game-state, so the dependency gap does not return.
- **Research G6 / PLAN T3.2 ('/tablist') vs research domain-mining-rules B1 ('/widgets')** → resolved (REQ-GS-18, REQ-MSA-08, Q-MSA-04, T3.0m (one hint key, verified at G1), G1 'mineshaft check 1' ('/tablist' or '/widgets'), AC-GS-18): The exact command and menu path are a G1 verification item. They feed one shared hint string that T3.2, T5.3 and Phase 6 reuse, and no consumer hard-codes a command.
- **PLAN T3.3 ('Static text markers') vs Brief item 3 ('possible corpse locations ... with waypoints') read together with item 4's Skyblocker-style beams** → resolved (R7 (Q-CORPSE-02), §12.H, T3.3 ('text-only ... no box or beam (R7)')): R7 decides text-only, see-through corpse labels with no beam or box, default OFF, and T3.3 cites it.
- **Brief Phase 3 item 8 vs PLAN T3.7** → resolved (D-4 (a), §12.1, drill-fix spec, T3.7 ('(B) is excluded and no fuel HUD is built'), 'Not built in 2.0.0' REQ-DRILL-09..12, AC-DRILL-06): D-4 picks the re-equip animation fix only. T3.7 matches it, there is no T3.7b, and the fuel-HUD requirements are listed as deliberately not built, with AC-DRILL-06 checking they are absent.
- **PLAN §3 policy P2 ('Invisible entities are never highlighted or announced') and P3 ('markers or alerts derived from entities fire only after line of sight') vs PLAN T3.6 and §3 hotspot rows** → resolved (PLAN §3 P3, REQ-XC-RULES-05, SPEC §12.1 P3): Fixed in the final pass: P3 and REQ-XC-RULES-05 name both R2 exceptions (the depth-tested hotspot ring and the hotspot-gone warning).
- **Capability map dependencies vs module needs** → resolved (SPEC §2 map, fishing-hotspot header): Fixed in the final pass: fishing-hotspot no longer depends on game-state, and its header lists T3.6, T3.6b, T3.0n and T3.0f.
- **PLAN T3.5 vs T3.6 / T3.7 (and AD-9)** → resolved (T3.0f (optional cosmetic mixin config + hook-status registry), T3.5/T3.6/T3.7 deps T3.0f, AD-9): The optional mixin config and hook status are their own task, T3.0f, and all three consumers declare it as a dependency.
- **PLAN D-8 ('toasts for warnings') vs Brief Phase 3 item 6 ('warn (title/sound/chat, configurable)')** → resolved (REQ-HOT-10, REQ-HOT-12, AC-HOT-05, R12, §12.H, T2.3d (Phase 2 toast component), T3.6b (title/sound/chat ON, toast OFF; deps T2.3d)): The brief's three channels are the default-ON requirement. The toast is an optional fourth channel, default OFF, and T2.3d builds it in Phase 2 with T3.6b depending on it. T2.3d's consumer list says 'T3.6' where it means T3.6b; item 9's fix corrects this.
- **PLAN T3.5 ('your own hook') vs brief reference SkyOcean** → resolved (PLAN T3.5; SPEC bobber-fix REQ-BOB-06 [decided R9], EC-BOB-09, Q-BOB-01; SPEC §12.2 R9, §12.H): Own-bobber-only is now a decided choice (R9) written into SPEC as REQ-BOB-06, and AC-BOB-01 tests it, so it is no longer an undeclared narrowing of SkyOcean's scope.
- **PLAN T3.7 ('same SkyBlock drill') vs research gap-G2 §7 and HSM behaviour** → resolved (PLAN T3.7; SPEC drill-fix REQ-DRILL-01, REQ-DRILL-08 [decided D-4], EC-DRILL-07, Q-DRILL-02, Out of scope; SPEC §12.1 D-4 'drills only'): The item scope is settled as drills only: items whose custom data carries drill fuel, with Pickonimbus and other uuid items explicitly excluded. T3.7's gate matches this.
- **Brief Phase 3 item 7 title vs PLAN P7 / D-3** → resolved (PLAN D-3 (→ 'A, renamed Rare Drop Odds', approved), T3.9h, T0.6; SPEC rare-drop-odds REQ-ODDS-02 [decided D-3], AC-ODDS-01, Q-ODDS-01): The rename is now an approved decision (D-3), and 'Gambling' survives only as a hidden search keyword, which AC-ODDS-01 tests.
- **PLAN T3.9b–f ('one case each, as a HUD element plus a chat line') vs the Croesus case** → resolved (PLAN T3.9c; SPEC REQ-ODDS-05, REQ-ODDS-07, REQ-ODDS-17..19 [decided R10 = b], AC-ODDS-13/14): T3.9c now adds tooltip lines in the reward chest screens, with 'No HUD element and no chat line'. REQ-ODDS-05 says Croesus uses tooltips instead of a HUD element, and REQ-ODDS-07 gives Croesus no chat line.
- **research gap-G1 §3 vs research-domain-fishing-drops-bestiary §2c (and PLAN §3 'public drop rates')** → resolved (PLAN T3.9a, T3.9d, T3.9e, T3.9g; SPEC REQ-ODDS-08 (per-corpse formula [decided D-3]), REQ-ODDS-09 (no wiki per-corpse column), REQ-ODDS-22, Q-ODDS-04, AC-ODDS-07, AC-ODDS-16): Q-ODDS-04 settles on per-corpse odds computed in code from per-roll weights over uniform roll counts. REQ-ODDS-22 notes the conflict with the domain report, and AC-ODDS-16 checks the roll count of every logged loot block (in T3.9d/T3.9e).
- **PLAN §3 / T3.9a (odds from 'public drop rates') vs D-16 / D-25 / D-28 (wiki only as a human cross-check; research wiki copies deleted)** → resolved (T3.9g, T3.9b, T3.9d, T3.9e): Fixed in the final pass: T3.9g adds the CC0 odds.supplied table for the wiki-only rows, and the Frostbitten Dye is a supplied row of the Vanguard table.
- **PLAN T5.2 ('Resets on world change') vs Brief Phase 5 'session stats'** → resolved (PLAN T5.2 (baselines reset; 'These resets do not end the session'), T5.2c; SPEC REQ-BEST-07, REQ-BEST-11 [decided R11], AC-BEST-06, Q-BEST-02): Baseline resync on world, server or profile change is now separate from the session. A session ends only on a manual reset, a profile switch or a restart; an optional island-change reset defaults to OFF.
- **PLAN T5.3 ('milestone: tiers to the next milestone') vs Brief Phase 5 ('kills to next tier/milestone')** → resolved (PLAN T5.1, T5.2, T5.3; SPEC REQ-BEST-04, REQ-BEST-05 [decided R11], AC-BEST-02/04, Q-BEST-01): R11 settles the unit: the global milestone is shown as 'tiers to next milestone', never as kills, plus a separate family 'kills to max' line.
- **PLAN T5.2 (bestiary menu snapshots) vs PLAN T6.5b (read-only parsers incl. Bestiary)** → resolved (PLAN Phase 5 intro, T5.2b, T6.5b (deps T5.2b), T3.0e; SPEC REQ-BEST-10, REQ-BEST-15): T5.2b owns the Bestiary menu parser. T6.5b depends on it and states 'no second parser', and REQ-BEST-10 makes this binding.
- **Capability map (rare-drop-odds and bestiary-hud deps) vs requirements** → resolved (SPEC §2 capability map (rare-drop-odds and bestiary-hud rows now list location; note that every feature depends on config-store/ui-config), module headers for rare-drop-odds and bestiary-hud; PLAN T3.9h, T5.1b and T5.2 deps T1.9): Both modules now depend on location, config-store/ui-config is covered by the map note and the module headers, and the odds and bestiary tasks depend on T1.9.
- **PLAN D-9 rationale vs PLAN D-13** → resolved (PLAN §9 D-9, Q-REL-01): Fixed in the final pass: D-9 justifies 2.0.0 by the new UI, the Cloth removal and the new updater; the 26.1 drop ships in v1.1.0.
- **PLAN T1.4 (interim updater) vs Brief Phase 4 'never replace the jar without user confirmation'** → resolved (PLAN T1.4 (+T1.4a/T1.4b), T1.19; SPEC REQ-UPD-01 [decided R4], AC-UPD-01, Q-UPD-01): T1.4 deletes the Modrinth updater and drops autoUpdateDownloadEnabled. v1.1.0 ships a notify-only GitHub check that downloads nothing, and AC-UPD-01 tests that.
- **PLAN T7.4 step 6 vs the versions actually published** → resolved (PLAN T7.4 (steps 1-6, no previous-version step), T7.4b, T1.19; SPEC REQ-REL-12, AC-REL-10, §12.2 R16): The end-to-end check now runs from a local, never-published 2.0.0-test.1+26.2 build that contains the new updater (T7.4b, REQ-REL-12).
- **PLAN T7.2 timing vs PLAN D-13 ('this also tests the new asset format early')** → resolved (PLAN T1.15 (Phase 1r), T1.17, T1.19, T7.2): The `<version>+<mc>` jar naming and the sidecar move to T1.15 for v1.1.0. T7.2 keeps only the 2.0.0 bump and says the naming 'landed with v1.1.0'.
- **PLAN T4.3 ('leftovers of the 1.0.x deleteOnExit bug') vs research gap-G4 / research-updater** → resolved (PLAN T4.3b; SPEC REQ-UPD-18, Q-UPD-02 / §12.2 R13 = b): T4.3b replaces the deleteOnExit-leftovers cleanup. It deletes only the jar the updater itself replaced, and keeps every other copy with a single warning.
- **PLAN T7.1 upgrade note vs D-13 early release** → resolved (PLAN T7.1 upgrade note, T1.16; SPEC REQ-REL-04 [decided R4], REQ-UPD-01): The 2.0.0 upgrade note now says that 1.0.x and 1.1.0 users install by hand once, because 1.1.0 is notify-only. This matches the Q-UPD-01/R4 outcome.
- **Brief Phase 4 'GitHub API: latest release → compare version → download the jar asset' vs PLAN AD-8/T4.1 (release list endpoint)** → resolved (PLAN AD-8, T1.4a, T4.1; SPEC REQ-UPD-03 (derivation note on /releases/latest), REQ-UPD-06 ('highest-version release in the active channel that has a matching asset'), REQ-UPD-07): The list endpoint is kept. SPEC states the behaviour the user will see and why /releases/latest does not suffice, so it plainly meets the brief's 'latest release' intent.
- **PLAN D-13 / §6 diagram (early release after G1) vs Brief Phase 6 flow (PR → approval → merge → tag + release)** → resolved (PLAN Phase 1r intro, T1.18, T1.19, Checkpoint G1r, §6 diagram; SPEC REQ-REL-14 [decided R4], Q-REL-03): The early release goes through an intermediate PR to main with the same approval and 'ship' rules. G1r checks that main and its README match the release.
- **PLAN T6.10 step 7 vs Addendum skill step 7** → resolved (PLAN T6.10 step 7; SPEC REQ-SKILL-09, REQ-SKILL-15 [decided R15], AC-SKILL-04, EC-SKILL-03, Q-SKILL-01): The branch is `data/sbxp-<gameVersion>`, with `-d<dataVersion>` appended only when that branch already exists. This is the addendum's name plus option c.
- **PLAN D-16 / T6.3 vs Addendum Data b2** → resolved (SPEC REQ-SBXP-04 [decided D-16 (a)] with explicit override note, §12.1 D-16 row; PLAN D-16, T6.3a): REQ-SBXP-04 records the deviation from the addendum's 'fill XP from the official wiki and patch notes' and gives the reasons (wiki closed on 2026-07-21, host terms). D-16(a) is decided.
- **PLAN D-28 vs Addendum Research 2** → resolved (SPEC REQ-SBXP-46 [decided D-28 (i), D-16 (a)], §12.1; PLAN D-28, T6.3a): REQ-SBXP-46 states D-28(i): wiki, forum and reddit pages are used only when the user supplies them, and snippets are leads only. It is recorded as a decided narrowing of the addendum.
- **PLAN T6.10 step 1 vs Addendum skill step 1** → resolved (PLAN T6.10 step 1; SPEC REQ-SKILL-03 [decided D-23, R15], EC-SKILL-02, AC-SKILL-03, Q-SKILL-02/03): User-supplied notes replace 'ask for or find' (D-23). The no-notes case is defined: the skill runs on data-source diffs only, labelled 'no patch notes supplied' (R15).
- **PLAN T6.10 step 4 vs Addendum skill step 2** → resolved (PLAN T6.10 steps 2-4; SPEC REQ-SKILL-04 ('Wiki pages are cross-checks only [decided D-16 (a)]'), REQ-SKILL-05, REQ-SKILL-12): The skill's sourcing follows D-16(a): API and NEU diffs plus captures, with the wiki as a cross-check and patch-note numbers as claims only. SPEC records this as decided.
- **PLAN D-16(a) vs PLAN §10 validator rules (affects Addendum Research 6)** → resolved (PLAN §10 'Rules the validator enforces' (Sources bullet), T6.0, T6.2b; SPEC REQ-SBXP-04, REQ-SBXP-07): The §10 validator now allows an unverified rate, drop chance or pity on one user-supplied wiki page while XP and maxima stay strictly non-wiki. T6.2b tests both the pass and the fail case.
- **PLAN AD-11 / §10 task types vs Addendum Core logic b1** → resolved (PLAN AD-11, §10, T6.0): Fixed in the final pass: AD-11 lists the addendum's three types and calls ladder the schema form of an ordered one-time task.
- **PLAN T6.3 research log layout vs Addendum Research 7** → resolved (PLAN T6.3a (one entry per family and per rate id), T6.3c (script check); SPEC REQ-SBXP-48, AC-SBXP-37): The per-family/per-rate layout is accepted. REQ-SBXP-48 requires every task id, generated ones included, to resolve to its family entry, and the AC-SBXP-37 script check in T6.3c verifies it.
- **PLAN T6.6 vs Addendum Core logic b3** → resolved (PLAN T6.6, T6.8 filters; SPEC REQ-SBXP-27 [decided R14], REQ-SBXP-50, AC-SBXP-23, AC-SBXP-41): R14 decides that unknown prerequisites are ranked with a '?' badge by default, with 'treat unknown as locked' (default OFF) available. This is now a decided deviation, not an undeclared one.
- **PLAN D-23 vs Addendum skill intro** → resolved (PLAN T6.10, D-23; SPEC REQ-SKILL-01, REQ-SKILL-02 [decided D-23], AC-SKILL-01, §12.2 R15 FYI): The key is kept and documented as the only deviation from the portable format. R15 records the portability cost, and AC-SKILL-01 proves that the validator passes once that one line is removed.
- **PLAN T6.8 vs Addendum UI Config** → resolved (PLAN T6.8, T6.8b ('Opens from the screen and from the config card's override button'); SPEC REQ-SBXP-50 (access to overrides), REQ-SBXP-53): The 'SkyBlock XP' config category must give access to the per-task, rate-id and price overrides (REQ-SBXP-50). T6.8b opens the editor from the config card's override button.
- **PLAN D-26 / T3.0c vs Addendum intro ('internal database from Phase 3.9')** → resolved (PLAN D-26, T3.0c, §6; SPEC REQ-SBXP-01 [decided D-26], REQ-DATA-13, §12.1): D-26 is decided: the addendum's 'internal database from Phase 3.9' is the shared data registry T3.0c, and the requirements are written against data-registry.

**Tasks no requirement justified (26):**

- **T1.4 fix(update): derive the Minecraft version at runtime** → resolved (PLAN T1.4, T1.4a, T1.4b, §9 D-13; SPEC REQ-UPD-01, REQ-PORT-10, §12.2 R4/R16): T1.4 is no longer a version-derivation tweak to the dead updater: 'T1.4 feat(update)!: notify-only GitHub check replaces the Modrinth updater' deletes UpdateChecker, so no Modrinth request remains, and it carries REQ-PORT-10 (version from the Loader) together with REQ-UPD-01, as R16 'replace' decided.
- **T1.12 fix(net): HTTP timeouts + DB entry validation** → resolved (PLAN T1.12, T3.8, §9 D-13; SPEC npc-mob-data REQ-NPCDB-05 [decided D-13], R16 'conditional'): D-13 approved the early v1.1.0, which still reads the gists, so T1.12 is justified by REQ-NPCDB-05 (an npc-mob-data requirement that names v1.1.0). T3.8 later deletes the gist fetch and the T1.12 entry validation.
- **T1.13 fix(lang): keybind category translation key** → resolved (PLAN T1.13; SPEC REQ-PORT-11, REQ-CFG-13, R16 'keep'): T1.13 cites the derived REQ-PORT-11 (no raw translation key on the Controls screen) and REQ-CFG-13, and is accepted by AC-PORT-11 and AC-CFG-12, as R16 decided.
- **T2.9 (keyboard navigation: Tab/Shift+Tab focus, Space/Enter, arrow keys on focused controls)** → resolved (PLAN T2.9b, stretch header before T2.9, 'Not built in 2.0.0' (REQ-UI-18); SPEC REQ-UI-18 [decided D-8], Q-UI-04): Keyboard focus navigation is now its own stretch task T2.9b under REQ-UI-18, which lists it explicitly as an optional item after G2 that nothing waits on (D-8/Q-UI-04 decided). The stale ui-config header id is fixed under item 5.
- **T2.9 (multi-select dropdown)** → resolved (PLAN T2.9 stretch note, T6.7 (Req REQ-SBXP-50); SPEC REQ-UI-18 last paragraph, ui-config out of scope line 'Optimizer-only widgets', Q-UI-04, R16 'move'): The multi-select dropdown was removed from T2.9 ('moved to T6.7'). T6.7 owns it under REQ-SBXP-50 (category filters, multi-select), and SPEC REQ-UI-18 says it is not a ui-config item.
- **T2.9 (snapping and guide lines in the HUD editor)** → resolved (SPEC hud and ui-config headers, T2.9c): Fixed in the final pass: the module headers list the split stretch tasks (T2.9, T2.9b, T2.9c, T2.9d).
- **T2.9 (SDF rounded corners)** → resolved (PLAN T2.9d, AD-4, 'Not built in 2.0.0' (REQ-UI-18); SPEC REQ-UI-18 [decided D-8], REQ-UI-17): Anti-aliased corners are an optional stretch task (T2.9d) that the user explicitly accepted under D-8. It must use public render APIs only (no accessor mixin, consistent with AD-4/REQ-UI-17), keeps the fill-based corners as default and fallback, is skipped if there is no public path, and is not required for 2.0.0.
- **T2.9 (open/tab animations)** → resolved (PLAN T2.9 (Req REQ-UI-18); SPEC REQ-UI-18 [decided D-8], Q-UI-04): The 220 ms open and 200 ms slide animations are stretch task T2.9 under REQ-UI-18, which lists both. D-8 decided the stretch option, so promoting them into T2.2/T2.4a is not needed.
- **T3.8 (sub-item: optional mob entityType field)** → resolved (PLAN T3.8b ('No entityType data is added (R16)'), 'Not built in 2.0.0' npc-mob-data line; SPEC npc-mob-data out of scope [decided R16]): The entityType sub-item is dropped. T3.8 and T3.8b add no entity-type data, and both SPEC and PLAN record it as out of scope, to be revisited only if the glow audit shows false matches.
- **T1.12** → resolved (T1.12, T1.4a, T3.8, T4.2): Fixed in the final pass: the T1.12 helper is deleted with the gist fetch; T1.4a and T4.2 state the REQ-UPD-09 timeouts.
- **T3.0a (sub-item: location change events)** → resolved (SPEC location header, T1.9): Fixed in the final pass: as contra 15, a single owner (T1.9).
- **T3.7** → resolved (PLAN T3.7, §9 D-4, 'Not built in 2.0.0' REQ-DRILL-09..12; SPEC drill-fix REQ-DRILL-01..08 [decided D-4], REQ-DRILL-09..12 (not built), REQ-DRILL-06 tooltip): D-4 decided symptom (a) only, so T3.7 (hand re-equip fix, REQ-DRILL-01..08) is justified. The fuel HUD, parser and warning (REQ-DRILL-09..12) are recorded as not built, AC-DRILL-06 checks they are absent, and the tooltip names the Skyblocker and NoFrills equivalents.
- **T3.9e (Lapis/Umber/Tungsten corpse odds)** → resolved (PLAN T3.9e, T3.9 intro (D-3 = A, five cases); SPEC REQ-ODDS-20..22 ('approved as case 4, decided D-3'), §12.1): D-3 approved the five-case list, Lapis/Umber/Tungsten included. T3.9e implements REQ-ODDS-20..22, and when the user-supplied pages are missing it shows 'no number' (REQ-ODDS-11).
- **T3.9f (Slayer RNG meter odds)** → resolved (PLAN T3.9f; SPEC REQ-ODDS-23, REQ-ODDS-24 [decided D-3], §12.1): The slayer RNG meter is approved case 5 under D-3, and T3.9f implements REQ-ODDS-23/24.
- **T5.3 compact mode** → resolved (PLAN T5.3b; SPEC REQ-BEST-08 ('A compact one-line layout may be offered'), R16 'keep'): Compact mode is a separate XS task T5.3b under the 'may' in REQ-BEST-08. It is built last and dropped if T5.3 runs long [decided R16].
- **T1.4 fix(update): derive the Minecraft version at runtime** → resolved (PLAN T1.4 (migration step 2), T1.4a, T1.4b; SPEC REQ-UPD-01, REQ-UPD-19, Q-UPD-01 -> R4): T1.4 is replaced by the R4 notify-only GitHub check. Migration step 2 drops autoUpdateDownloadEnabled, and nothing is downloaded or installed (REQ-UPD-01/19), so the silent-install path is gone.
- **T4.3 sub-item 'clean up stale copies, including leftovers of the 1.0.x deleteOnExit bug'** → resolved (PLAN T4.3b (replaces the old cleanup item), T4.3; SPEC REQ-UPD-18 [decided R13], Q-UPD-02): The stale-copies/deleteOnExit cleanup is gone from T4.3. T4.3b deletes only the jar the updater itself replaced and warns once about other copies, per R13 (b) and REQ-UPD-18.
- **T7.4 step 4 'If wanted, enable immutable releases'** → resolved (PLAN T7.4 step 5 (Req REQ-REL-13), T7.2c; SPEC REQ-REL-13 [decided R4]): Enabling immutable releases is now the decided requirement REQ-REL-13 (R4: before 2.0.0), and the API call runs only with the user's OK.
- **T7.4 step 6 'Run the real updater from a test install of the previous version'** → resolved (PLAN T7.4b (Req REQ-REL-12, AC-REL-10); SPEC REQ-REL-12, R16 'reword'): The impossible 'previous version' test is replaced by T7.4b, which runs a local, never-published 2.0.0-test.1+26.2 build that contains the new updater, exactly as REQ-REL-12 words it.
- **D-13 sub-option 'Optional: a 1.0.2 hotfix for 26.1.2'** → resolved (PLAN §9 D-13 (decided 'no 26.1.2 hotfix'), 'Not built in 2.0.0' release line; SPEC port-26-2/updater/release out of scope ('no 1.0.2 hotfix'), §12.1): The 1.0.2 hotfix option is decided against in D-13, with no task, and appears in the out-of-scope lists of SPEC and PLAN.
- **T6.5 (live deltas from action-bar and chat reward blocks) + the G6 item 'tier D boot: listener phase order recorded; action-bar XP events seen'** → resolved (PLAN T6.5d (Req REQ-SBXP-38/39), G6 tier D item (AC-SBXP-32 [D]); SPEC REQ-SBXP-38 'should' [decided R14], §12.H live deltas ON): Live deltas were split out of T6.5 into T6.5d under the derived 'should' REQ-SBXP-38, which R14 approved. The G6 tier-D action-bar item is the [D] part of AC-SBXP-32, so it is justified too.
- **T6.5b feat(sbxp): per-component progress** → resolved (PLAN T6.5b, §9 D-27; SPEC REQ-SBXP-37 [decided D-27 (a)]): D-27 decided (a), so T6.5b is justified by REQ-SBXP-37. The Bestiary menu goes through T5.2b's parser, with no second parser.
- **T6.2 --prices: one keyless /v2/skyblock/auctions scan per data update** → resolved (PLAN T6.2c (Req REQ-SBXP-40/43, AC-SKILL-09), §9 D-20; SPEC REQ-SBXP-43 [decided D-20]): D-20 approved the proposal: one keyless auctions scan per data update, made at data-build time. The tool asks before the scan and writes aggregates only. That scan lives in T6.2c under REQ-SBXP-43.
- **T6.6/T6.8 stage and overrides stored per profile** → resolved (PLAN AD-13, T6.5c, T6.8 defaults; SPEC REQ-SBXP-22, REQ-SBXP-23, REQ-SBXP-50 [decided R14], §12.H): R14 confirmed that stage and overrides are per profile, with coins/h global plus an optional per-profile value. REQ-SBXP-22 and REQ-SBXP-23 now state this, and T6.5c stores them.
- **T6.8 keybind to open the optimizer** → resolved (PLAN T6.8 ('a keybind (unbound by default, R16)'); SPEC REQ-SBXP-51 ('A keybind may be offered, unbound by default'), R16 'keep'): The keybind is kept as the 'may' in REQ-SBXP-51, unbound by default, as R16 decided.
- **T6.6 header 'ranking covers X of Y remaining XP'** → resolved (PLAN T6.6 ('Header: ranking covers X of Y remaining XP (kept, R16)', Req REQ-SBXP-29); SPEC REQ-SBXP-29 'should', R16 'keep'): The coverage header is justified by the derived 'should' REQ-SBXP-29 and is kept per R16.

**Tasks and sub-items removed or merged in the update (12):**

- **T1.4 fix(update): derive the Minecraft version at runtime (original scope)**: R16 'replace'.
- **T2.9 sub-item 'multi-select dropdown'**: Duplicate of T6.7's multi-select filter.
- **T3.0a sub-item: location change events**: R16 / SPEC 13.2 and 13.4: owned by location (REQ-LOC-05), moved to T1.9.
- **T3.8 sub-item: optional mob entityType field**: R16 drop.
- **T3.8 sub-item: removing invisible-type mob entries from the data/picker**: R5: they stay in the data and are shown greyed, not addable (T3.8b flag + T3.8c picker).
- **T3.8 dependency 'D-12' and T3.9-style decision deps**: All decisions are made;
- **T3.7 sub-item: drill fuel HUD (C), 'built only if requested'**: D-4 decided (a) only.
- **T3.9 rule 'T3.9b–f: one case each, as a HUD element plus a chat line'**: Replaced by per-case tasks.
- **T4.3 sub-item 'On next launch: … clean up stale copies, including leftovers of the 1.0.x deleteOnExit bug'**: SPEC 13.2 found that no requirement justifies it, and the leftovers it cites cannot exist (the 1.0.x updater never downloaded anything).
- **T6.8 sub-item 'include/exclude ... waiting tasks' filter**: No requirement asks for it: REQ-SBXP-50 lists only category filters, coin-only, include locked and treat unknown as locked.
- **T6.1 sub-item: own confidence derivation ('verified → high; unverified with ≥1 non-wiki source → medium; else low')**: Replaced by REQ-SBXP-21: high = multiple consistent sources, medium = single, low = extrapolated;
- **T6.8 sub-item 'overrides flagged stale when dataVersion changes'**: Contradicts REQ-SBXP-23 and EC-SBXP-23, which flag an override only when the researched value it overrides changed or its id was removed.

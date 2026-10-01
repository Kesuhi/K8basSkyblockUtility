# PLAN — Port & extend K8bas Skyblock Utility (Minecraft 26.1.2 → 26.2)

| | |
|---|---|
| Status | **Approved on 2026-10-01**, together with `SPEC.md`. All decisions are made (D-1–D-29 and the spec questions R1–R17 as recommended, except R10 = b; the D-17 captures moved to G1). **Every task in §7 names the SPEC requirement ids it implements (`Req:`) and the acceptance criteria it proves (`Accept:`).** Work follows §7 in order; ticked boxes mark finished tasks. The optimizer is Phase 6 and the release Phase 7; the data schema draft is in `docs/sbxp/` (§10). |
| Date | 2026-10-01 |
| Branch | `update/26.2` (from `main` @ `bc0f2f6`, v1.0.1) |
| Baseline | `./gradlew build` on `main` is green (26.1.2) |
| How this plan was made | All 40 Java source files and the build files were read. A throwaway 26.2 compile probe was built in a scratch copy outside the repo. 26.1.2 and 26.2 were compared with `javap`. Reference mods, Hypixel rules and game data were researched. A separate review pass checked the plan for brief coverage, facts and quality. |

This file is both the plan document and the task list (agent-skills `planning-and-task-breakdown`). Tasks are checked off here as they land.

---

## 1. What the research found (changes to the brief)

1. **The port is small and compile-proven.** Moving to 26.2 produces 15 compile errors in 5 files, from three API changes:
   - `Minecraft.screen` / `setScreen` moved to `Minecraft.gui`.
   - The title methods moved to `gui.hud`.
   - Immediate-mode world rendering was removed: `MultiBufferSource`, `Font.drawInBatch` and `LevelRenderContext.bufferSource()` are gone.

   The probe compiled and built a jar (7 files, +29/−60 lines, no stubs). Both mixin targets exist unchanged. Every dependency has a 26.2 build. No mappings change is needed: 26.x ships unobfuscated. **Runtime is still unverified.**
2. **Feature 1 (Trinity/Tomioka/Duncan glow) is a bug, not a missing feature.** The three NPCs are already in `npc_database.json`, and your config already has rules for them.
   - Inside a dungeon run, Hypixel reports `mode = "dungeon"`, which your logs show 7 times.
   - `IslandTracker` only maps a `catacombs` mode that never occurs, plus `dungeon_hub`.
   - So the current island is `null` in runs, and every "Catacombs" rule is skipped. `mineshaft`, needed in Phase 3, is missing too.
3. **⚠ The released 1.0.1 breaks Hypixel's rules in two ways.**
   - It draws glow **through walls**: vanilla outline, plus a `MinecraftMixin` that forces it.
   - It outlines **invisible mobs**. Ghost, Fels, Sneaky Creeper and Invisibug are in the mob DB. Hypixel's 2021 announcement names "Displaying invisible entities" ("CURRENT STATUS: BANS BEING ISSUED").

   Skyblocker and SkyHanni ship only visible (depth-tested) glow; Skyblocker says this is explicitly to comply with Hypixel's rules (PR #260). On 26.2 both do it through the *Render Chest* library. The fix is made in code in Phase 1, but **it only reaches users when a release ships** → D-13.
4. **"Rare Drop Gambling Overlay" is ambiguous.** SkyOcean's "Gambling" category contains no odds. It has only *cosmetic reveal animations*: a CS:GO-style case for dungeon/Croesus chests and a Vanguard slot machine. Your text asks for odds. → D-3.
5. **The Hot-Shirtless-Men "drill fuel fix" is not a display fix.** It does two things:
   - (A) It suppresses the hand re-equip animation when the drill's lore changes. That is visual and fine.
   - (B) It keeps block-break progress by **suppressing ABORT/START_DESTROY_BLOCK packets**. That alters client↔server traffic, which is disallowed. Hypixel also fixed the reset server-side on 2026-09-17.

   (A) and a fuel bar already exist in Skyblocker and NoFrills in your instance. → D-4. (B) is excluded.
6. **The auto-updater is inert and unsafe.**
   - The Modrinth project returns 404, so every launch makes one failed request.
   - It would install **without asking**: both flags default to `true`, while the README says "off by default".
   - Verification is only a SHA-1 taken from the same response, and the HTTP status is never checked.
   - `deleteOnExit()` cannot delete the locked jar on Windows (reproduced here).
   - It takes the file name from the server unchecked (path traversal) and has no timeouts or redirect handling.
7. **Config bugs.**
   - Saving the settings screen starts racing async and sync writes.
   - A config that fails to parse is silently replaced by defaults on the next save.
   - There is no schema version.
   - The keybind category lang key is wrong.
8. **Overlap with mods you already run.** Some requested features already exist in your instance:
   - a rare-room alert for these three NPCs (Skyblocker)
   - a corpse finder (Skyblocker)
   - a hotspot radar (SkyHanni)
   - a drill fuel bar and the animation fix (Skyblocker, NoFrills)
   - reveal animations (SkyCase, MIT, 26.2)

   Not a blocker: K8bas is distributed to others too. Coexistence is tested.
9. **SkyBlock XP Optimizer (addendum).**
   - **No existing tool does this.** Every mod or web tool found ranks by *coins* per XP or only shows progress. None combines play time with coins.
   - **What is mapped so far.** The live game is 0.27.1, and a level is a flat 100 XP. My split of the research table has ~127 task families in 8 menu categories. The in-game layout is UNVERIFIED (the wikis show 9 tabs), so the real family list comes from your `/sblevels` capture (D-17).
   - **The current maximum is not settled.** The wiki says 620.39 (unconfirmed), its own table sums to ~613.6–615.5, and the top 50 of a public leaderboard all sit at 620.16, none higher. The real maximum must come from the in-game menu.
   - **Official keyless data covers about 30 % of all XP.** Hypixel's `/v2/resources` gives skills 8,710, collections 3,160, museum 3,647 and minions 3,165. The wiki says 3,646 and 3,164 for the last two, recorded as conflicts.
     - NEU (MIT) or patch notes independently match another ~28 % (50 families). 38 of those rest only on NEU `sblevels.json` (last changed 2024-12-14), so they are single-source and may be stale.
     - ~41 % of XP has its count, maximum or structure *only* on the wiki.
   - ⚠ **The wiki must not be scraped.**
     - The official Hypixel wiki named in the addendum **closed on 2026-07-21**.
     - The community wiki's host (Weird Gloop) and Fandom forbid automated access in their Terms; the community wiki's `robots.txt` blocks `api.php` and Claude's crawlers.
     - Hypixel's ToS bans automated access to **all** of hypixel.net, including the patch notes and forum rate threads.
     - So the wiki is a *human cross-check*, pages are read only when you supply them (D-16, D-28), and patch notes come from you.
     - Patch notes rarely contain SB XP numbers; they show *what* changed, while the numbers come from the API, NEU and your menu captures.
     - *Disclosure:* the scoping research already pulled ~200 community-wiki pages and several Fandom pages through `api.php`. Those copies, and everything parsed from them (including the research draft table), are deleted once the plan is approved and are **not** an input to the shipped data (D-25).
   - **Live progress needs three passive sources.**
     - The `/sblevels` menu ("SkyBlock Leveling" → "Ways to Level Up" → "Tasks ➜ <Category>") is the full picture when you open it, and its whole lore arrives with the menu.
     - The action-bar line `+N SkyBlock XP (<label>) (x/100)` covers ~80 % of gains, but almost none of Skill Level Up (0 %) or Bestiary (2 %). Chat reward blocks cover those.
     - Skyblocker cancels or rewrites the action bar, so our listener must run in an early read-only phase.
   - **Big families only show totals.** The leveling menu gives just earned/max XP for skills, collections, museum, minions, bestiary, HOTM/HOTF, Catacombs and slayers. Per-skill or per-item progress needs read-only parsers for the menus *you* open (`/skills`, `/collection`, `/museum`, Bestiary, HOTM, pets, accessory bag), or a manual "set level" (D-27).
   - **Profile detection must not depend on chat alone.** Your Skyblocker chat rules hide the profile chat lines, so the profile comes from the tab-list "Profile:" widget, keyed by player UUID + profile name.
   - **Coin-only XP (the addendum asked to identify all of it).** About 25 groups can be bought with coins.
     - **Mostly via the Bazaar:** Attribute Levels (3,200 XP; 321 shard products), Essence Shop perks (1,677; essences are Bazaar items), power stones, ~21 consumables, Garden plots (compost), Bank upgrades (200), and minion crafting materials (3,165, needing collection unlocks).
     - **Mostly via the Auction House:** Accessory Power (2,121; 1 XP per point), Pet Score (1,527; 3 XP per point), Museum (3,647; items become soulbound), travel scrolls.
     - **NPC coin sinks:** Jacobus (198 XP, ~1.76 B), personal bank (110 XP, ~6.2 M), some Abiphone contacts.
     - **Not coin-buyable:** skills, collections, HOTM, copper, medals, motes and tokens, and the Community Shop (free but time-gated, or SkyBlock Gems, which are real money).
     - Most groups also need time or prerequisites. "Coin-only" is computed per task (AD-13).
   - **Bazaar prices are free to fetch.** `https://api.hypixel.net/v2/skyblock/bazaar` needs **no key** (verified live: 2,197 products, 492 KB gzip, `max-age=60`). Hypixel's API policy (2026-09-30) allows keyless use from public mods with caching. A full Auction House scan is 43 pages, 58 MB gzip, and carries other players' UUIDs, so it is **not** done on clients (D-20).
   - **XP retention is mixed.** Museum and minion XP stay when the item goes. Pet Score is very likely a high-water mark. Attributes are consumed. v1 costs everything **gross**, with no resale assumed (D-19).

---

## 2. Licensing outcome per project (ground rule 4)

This repo is **CC0-1.0**. Copying LGPL code into it is incompatible, because that code can't be relicensed as CC0. MIT or Apache code may be included only together with its notice, which would make the repo partly non-CC0. **Default: reimplement all behaviour from scratch and copy no code.** The only third-party content proposed for bundling is MIT *data* files with notices (D-5), plus one Apache-2.0 library included as a separate jar.

| Project | License (verified) | Outcome |
|---|---|---|
| **SkyOcean** (meowdding) | Custom "SkyOcean License v1": `.java/.kt/.kts`, shaders, `src/repo/**`, `buildSrc/**` = **MIT**; everything else (textures, sounds, lang, post-effects) = **All Rights Reserved** | Bobber fix, hotspot logic and mineshaft alert **reimplemented** from the described behaviour. One MIT data file, `src/repo/vanguard.jsonc` (Vanguard loot weights), is bundled with a notice, with its "assumed" dye weight replaced (R6). No ARR assets touched. Courtesy credit in README. Not ported: automatic `/pc` chat sends and particle-*packet* cancelling (§3). |
| **Skyblocker** | **LGPL-3.0** | **Behaviour study only** (waypoint look, corpse key mapping). Zero code copied. |
| **AlpakaAddons** | **No LICENSE file** (GitHub: none; `fabric.mod.json` claims "MIT" with no text or notice, which is ambiguous) | Treated as **all rights reserved**. UI rebuilt from a written spec in our own words. No code, shaders, textures, fonts, logo or sounds copied. |
| **Hot-Shirtless-Men** (Rekteiru) | **MIT** © 2024 Rekteiru | Only the visual half (A) is relevant; it is **reimplemented** (`ItemStack.matchesIgnoringComponents` plus a SkyBlock uuid check), so no notice is required. Courtesy credit. |
| Render Chest (AzureAaron) | **Apache-2.0** | Proposed **dependency**, JiJ via Loom `include` as its README asks, for depth-tested glow (D-1). Its LICENCE ships inside its jar. Same version Skyblocker and SkyHanni bundle. |
| Cloth Config | LGPL-3.0 (dependency, not copied) | Removed in Phase 2. `ButtonEntry`/`LiveTextFieldEntry` say they were "modeled directly on Cloth Config's … source"; they are deleted in the UI rebuild, which removes any provenance doubt. |
| NEU-REPO (Moulberry) | MIT © 2020 Moulberry | Proposed: bundle trimmed `bestiary.json` and `rngscore.json` data with notice (D-5) |
| Corpse-spot data | **SkyHanni-REPO PR #759: open and unmerged**, from contributor fork `GrowlingGrizzly/SkyHanni-REPO` @ `e2c8edb932`; MIT © 2022 hannibal2 per the fork's LICENSE. **meowdding-repo**: MIT © 2026 meowdding. | Proposed: one merged file (29 shaft codes) with both notices and credit to GrowlingGrizzly, commit pinned (D-5). CC0 alternative: ShaftUtils `corpse_spawns.json` (93 spots, ~16 fewer). |
| Hypixel wikis | Community wiki CC BY-NC-SA 3.0 (host Weird Gloop: Terms forbid automated use without consent; `robots.txt` blocks `api.php` and Claude's crawlers); Fandom CC BY-SA (Terms forbid automated access) | **Human cross-check only.** No scripted extraction and no runtime fetch. A page is read only when you paste or save it (D-28). XP values, maxima and structure need a non-wiki source. Rates and drop chances may cite one human-read wiki page, but never as the only source of a `verified` value. Risk note: a substantial extraction could touch the EU database right (§87b UrhG); this is research, not legal advice. No consent request in 2.0.0 (D-16 (a); SPEC §12.1). |
| Hypixel API (keyless `/v2/resources/skyblock/*`, `/v2/skyblock/bazaar`, `/v2/skyblock/auctions`) | Official API, policy updated 2026-09-30: no API keys in public mods, keyless endpoints OK with caching, "not affiliated with Hypixel" statement required | Facts (skill and collection XP, museum `donation_xp`, generator tiers, item tradability) built into bundled tables **at data-build time**. AH: one keyless scan per data update, NBT decoded in memory, only rounded per-item aggregates written; auctioneer, profile, coop and bidder UUIDs never touch disk. Optional live Bazaar on clients (D-22). Never raw dumps. README gets the required non-affiliation statement. |
| hypixel.net (patch notes, News & Announcements, forum rate threads) | Hypixel ToS bans automated access to the whole site | **You supply pages** (paste or save the file). Neither Claude nor the update skill fetches hypixel.net. Patch notes show *what* changed, but rarely give XP numbers. |
| NEU-REPO, additional files for SBXP | MIT © 2020 Moulberry (pin `24564a1ad2`, 2026-09-30) | `constants/sblevels.json` (the only non-wiki source for 38 families; stale since 2024-12), `trophyfish.json`, `essenceshops`, `attribute_shards`, `abiphone`, `misc` (minion XP, talisman upgrades), `garden`, `leveling`, `fairy_souls`, `museum.json`, `pets.json`/`petnums.json`, and minion recipes. Bundled with notice (D-5). **`george.json` is CC BY-NC-SA and is excluded.** |
| SkyHanni-REPO `regexesModern.json` | MIT © 2022 hannibal2 (pin commit) | Menu titles and lore patterns as facts and provisional fixtures; notice in THIRD_PARTY_NOTICES (D-5). |
| Reddit | Terms not assessed | Used only as pages you supply, and only as corroboration. |
| Your logs and menu captures | Personal | Used for verification and as test fixtures, **sanitised** (no other player names, server ids or UUIDs). |

Mechanics: `THIRD_PARTY_NOTICES.md` at the repo root. License texts go into the jar under `META-INF/licenses/`. Third-party data lives under `src/main/resources/assets/k8bas_skyblock_utility/data/thirdparty/`. The README states that those files are MIT, not CC0.

---

## 3. Hypixel rules: compliance policy & per-feature verdicts (ground rule 5)

**Basis.** In descending order of weight:
- Hypixel *Allowed Modifications* (updated 2023-02-03). Key lines:
  - "modifications which show limited amounts of additional information which should not give you an in-game advantage" (operative sentence)
  - "alter the way in which your Minecraft client interacts with and communicates with our server … strictly disallowed"
  - Aesthetic mods must not let you "see around or over objects"
- SkyBlock Rules (2024-05-06)
- The 2021 SkyBlock QoL announcement, recovered word for word: "Displaying invisible entities", "manipulation of packets"
- What approved mods actually ship (Skyblocker, SkyHanni, SkyOcean) and their defaults
- Modrinth rules §3 ("cannot contain … ability to see through opaque blocks")

**Binding policies (D-6):**
- **P1:** We never render an entity through walls, not even as an opt-in. Glow the *server* sets on an entity is left untouched; we don't recolour it.
- **P2:** Invisible entities are never highlighted or announced. Hard-coded, no toggle. **Scope (R2):** entities whose body and name the player cannot perceive. A hologram stand whose name tag vanilla already displays (fishing hotspots) counts as visible information, and markers derived from it are depth-tested.
- **P3:** Markers or alerts derived from entities fire only after line of sight. Exceptions (R2): a depth-tested marker on a hologram whose name tag vanilla displays (the hotspot ring) needs no separate line-of-sight check, and the "hotspot gone" warning is gated by "fished there in the last 30 s and within 40 blocks".
- **P4:** See-through rendering is allowed only for **fixed coordinates** (static waypoints). Distance is shown only to fixed coordinates.
- **P5:** Packets are read-only. Never cancel, delay or modify one. No automatic chat sends; at most a click-to-fill button.
- **P6:** Every feature has its own toggle. AMBER items carry a tooltip.
- **P7:** Feature names never use "ESP", "x-ray", "through walls", "cheat" or "gambling".

Authoritative per-feature defaults: `SPEC.md` §12.H.

| Feature | Verdict | Ships as | Default |
|---|---|---|---|
| Existing glow through walls (Mob Highlighter, NPC Search) | 🔴 RED | Replaced by depth-tested glow (Render Chest); `MinecraftMixin` deleted | module ON (user rules) |
| Existing outline of invisible mobs | 🔴 RED | Removed: `isInvisible()` means no glow and no "found" alert | always enforced |
| Trinity/Tomioka/Duncan glow | 🟢 if depth-tested | Depth-tested glow; "You found X" title only after line of sight, reset per run | your rules stay on |
| NPC waypoints: white label + coloured beacon beam | 🟡→🟢 | Fixed coordinates only | module ON |
| Waypoint distance "Nm" line | 🟡 | Only to fixed coordinates; own toggle | ON |
| Mineshaft entry alert (corpse types + keys in inventory) | 🟢 | Reads the server's *Frozen Corpses* tab widget and your inventory; local message only | ON |
| Possible-corpse-spot waypoints | 🟡 | Every known spot for the shaft variant, **never filtered by unseen entities**; text only | **OFF** |
| Bobber rubber-band fix | 🟡→🟢 | Client presentation only; no packets, timing or reel behaviour changed | ON |
| Hotspot highlight | 🟢 | Depth-tested ring; read-only detection; no particle-packet cancelling | ON |
| "Hotspot gone" warning | 🟢 | Only if you fished it in the last 30 s and are within 40 blocks | ON |
| Rare-drop odds overlay | 🟢 | Public drop rates + your own data; never predicts unopened contents | OFF (HUD clutter) |
| Drill hand-animation fix (A) | 🟢 | Render-only | ON |
| Drill "keep mining progress" (B) | 🔴 | **Excluded**: requires suppressing packets | — |
| Bestiary HUD | 🟢 | Your own progress (tab widget, menus *you* open, chat) | OFF (HUD) |
| Auto-updater | n/a | Notify plus explicit confirm; never silent | check ON |
| SkyBlock XP Optimizer (ranking, planner) | 🟢 | Information from public data plus your own progress; no gameplay advantage beyond a guide | module ON, HUD OFF |
| Reading `/sblevels` menus you open | 🟢 | Read-only lore; **no auto-opening, no clicking, no paging** | ON |
| Action-bar / chat XP lines | 🟢 | A read-only `ALLOW_GAME` listener in a phase before Fabric's default, which always returns true, plus `GAME_CANCELED` as a fallback. This also sees messages other mods cancel or rewrite through Fabric's message events; messages hidden at packet or mixin level are not seen (EC-GS-04, documented in the README). | ON |
| Live Bazaar prices | 🟢 | Official keyless endpoint, cached, ≤ 1 request per 15 min while the optimizer is used | OFF (opt-in) |

**Flagged, not implemented:**
- automatic party-chat announcements (SkyOcean)
- particle-packet cancelling
- a through-wall glow option
- filtering static corpse spots by unseen entities
- drill (B)

---

## 4. Architecture decisions

| # | Decision | Rationale |
|---|---|---|
| AD-1 | **Glow via Render Chest `CustomGlowCallback`** (JiJ, Apache-2.0). Our handler returns `NO_GLOW` for invisible entities and for entities the server already makes glow (`isCurrentlyGlowing()`, because Render Chest would *recolour* that through-wall outline). Matching runs on the client thread, cached by entity id. Delete `MinecraftMixin`, `EntityRendererMixin` and the vanilla-outline path. | Depth-tested for normal entities, and never outlines unsubmitted (invisible) bodies. The callback is shared with Skyblocker/SkyHanni and is *first-wins*, so the order is checked in tier D. Deleting `MinecraftMixin` also removes the EntityCulling off-thread world-query race that matches your 2026-08-21 crash. |
| AD-2 | **World rendering = submit-based.** `LevelRenderEvents.COLLECT_SUBMITS` plus `SubmitNodeCollector`. See-through labels go through Fabric's `submitCustom(SubmitRenderPhases.AFTER_TERRAIN, …)` so water and glass don't overdraw them. Beams use `BeaconRenderer.submitBeaconBeam`. One shared marker toolkit (label, beam, ring). | 26.2 removed immediate-mode drawing. Plain `submitText` draws *before* translucent terrain, and SkyHanni and Odin route around this the same way. One toolkit keeps the 26.3 port small. |
| AD-3 | **Config robustness.** A `configVersion` field; atomic save (temp file, then `ATOMIC_MOVE`); one debounced save path; on parse failure, back up the broken file and never overwrite it; ordered, unit-tested migration steps. | Fixes the race and the data-loss bug; required before the UI changes save semantics. |
| AD-4 | **Custom UI, no UI library, pure Java, vanilla font.** Rounded corners from fills (no private render APIs in v1). `Module` is decoupled from Cloth through a small option model that also feeds search. Cloth Config is removed at the end of Phase 2. | Matches AlpakaAddons, which is fully custom. Avoids fragile mixins and drops an LGPL dependency plus the provenance-doubt files. |
| AD-5 | **HUD framework on Fabric `HudElementRegistry`**, with anchor-relative positions (9 anchors + offset + scale) and preview data. | No HUD mixin. Layouts survive GUI-scale and window changes. |
| AD-6 | **Shared SkyBlock data services:** tab widgets, sidebar scoreboard, system chat, inventory counts, location (mode/map/server + change events). | Mineshaft, hotspot, odds and bestiary all need them. Built once and tested against captured lines. |
| AD-7 | **Data bundled in the jar, versioned with releases. No runtime fetch, with one exception:** the opt-in live Bazaar price feed (D-22). Applies to the NPC/mob DBs, corpse spots, bestiary, odds and SkyBlock XP tables. All of them go through **one shared data registry** (T3.0c): envelope per REQ-DATA-02 (`table`, `schemaVersion`, integer `dataVersion` +1 per change, `gameVersion`, `generatedAt`, `license` ∈ CC0-1.0 | MIT | Apache-2.0, `sources` as a map id → source; MIT/Apache tables under `thirdparty/`), an `index.json` with sha256 per file, and a validator inside `./gradlew check`. The gists are frozen, not deleted, because 1.0.x users still read them. | The data is small (50–72 KB) and unchanged since 2026-08-21. The remote fetch is a single point of failure (no cache, fallback, timeout or validation; NPE on a bad entry). Updates arrive with releases through the updater. Simplest option that meets "keep a fallback if remote is used". |
| AD-8 | **Updater on GitHub Releases** (D-14). `/releases` with ETag and back-off; exact asset-name match for the running MC version; SHA-256 checked against the API `digest`; staged outside `mods/`; a JDK-only helper swaps the jar after the game exits; reconciliation on next launch. **Never installs without a click.** | The only design that works on Windows (jar locked while running), and the one established mods use. API facts verified live: digest field, 302 redirect, 60 req/h, and a 304 still counts when unauthenticated. |
| AD-9 | **Mixins:** after AD-1 no core mixin remains. Later cosmetic hooks (bobber, drill animation, read-only particle observer) go into one config `k8bas_skyblock_utility.mixins.json`, package `com.k8bas.skyblockutility.mixin`, `required: false`, `defaultRequire: 0`, with skipped injections logged. Prefer `@WrapOperation` / `@ModifyReturnValue`; never `@Redirect` / `@Overwrite`. | Alpaka, NoFrills and Skyblocker hook the same methods; a cosmetic conflict must not crash the game. |
| AD-10 | **Versioning:** the Gradle `version` + `+<mc>` build metadata, e.g. jar `k8bas_skyblock_utility-2.0.0+26.2.jar`, tag `v2.0.0` (D-9). | The updater picks the right jar per MC version. |
| AD-11 | **SkyBlock XP data model** (§10):<br>• Families, as in the menu, hold tasks of the addendum's three types: `one_time`, `repeatable` (finite) and `capped`. `ladder` is the schema form of an ordered one-time task (REQ-SBXP-02), not a fourth type.<br>• Large sets (skill levels, minions, museum) are **generated at load time** from reference tables, not hand-written.<br>• Rates live in their own table, per stage, with `assumes`, sources and confidence. Tasks hold only amounts, drop chances, costs and prerequisites.<br>• Everything derived (time, effective hours, XP/h, confidence, P90) is computed in code, never stored. | Satisfies "store the rate separately from the result". A rate change recalculates every dependent task. 12k+ steps without 12k hand-written rows. |
| AD-12 | **Data tooling in Java**: a `tools` source set run as `./gradlew sbxpData`. It pulls keyless Hypixel resources and the pinned NEU commit, builds the reference tables, diffs them against the previous data and writes the change report. JSON Schema validation runs as a **test-only** dependency. | CI and the update skill need only the JDK. Deterministic, offline-replayable fixtures. No runtime schema library in the jar. |
| AD-13 | **Optimizer calculation**, pure Java and unit-tested:<br>• `time_h = Σ effort`: throughput `amount/rate`; drop `E[attempts]/rate` with `E = (1−(1−p)^N)/p` under pity N; fixed minutes. `/day` rates add only wall-clock waits.<br>• Coins: summed gross from coins, items (price layering bundled < live Bazaar < your override) and currencies (via item or rate; real money never).<br>• `effective_h = time_h + coins/coins_per_hour`, floor 1 min. Tasks with zero play time and zero coins (only waiting) go into a separate "free / waiting" group sorted by XP, so nothing is divided by zero.<br>• `efficiency = xp / effective_h`.<br>• **Alternatives:** the option with the lowest effective_h at your coins/h and stage wins. **"Best member counts" groups** use marginal XP. **Derived XP** (e.g. bestiary milestones) is added to the steps that produce it.<br>• **Missing data is never free:** any absent or null part means "no estimate", listed separately and never ranked.<br>• Overrides: `time_h` = task time override > task rate override > rate-id override > `rates[ref]` by stage; `coins` = task coin override > your price override > live > bundled. Stage and overrides are per profile (R14).<br>• **Coin-only** is judged per option (R14): an option is coin-only with no throughput/drop effort (fixed ≤ 5 min allowed) and ≥ 1 coins/item/convertible cost; a task is coin-only only if every estimated option is; "exclude coin-only" removes coin-only options before the cheapest option is chosen.<br>• Rows are the *next* step per instance, ~2,800 (D-21).<br>• Planner: greedy to a target level, recomputing after each pick. | Matches the addendum's formulas exactly. Greedy is what was asked for; its limits (no co-progress between grinds) are documented. |

---

## 5. How work is done and verified

**Skills (addyosmani agent-skills, installed v0.6.11):**
- `incremental-implementation` + `test-driven-development`: every task, with a failing test first where logic is testable.
- `source-driven-development`: APIs verified with `javap` on the 26.2 jars or official docs.
- `doubt-driven-development`: for the glow change and the updater.
- `code-review-and-quality`: a `code-reviewer` agent at every checkpoint.
- `security-and-hardening`: a `security-auditor` agent for the updater.
- `git-workflow-and-versioning`, `documentation-and-adrs`, `shipping-and-launch`.

**Git:**
- One logical change per commit, Conventional Commits (`feat(scope):`, `fix`, `refactor`, `build`, `test`, `docs`, `chore`; `!` marks breaking behaviour).
- Bodies explain *why* and how it was verified, matching the repo's existing style.
- Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **The build is green after every commit:** at each checkpoint a local script builds every commit since the last checkpoint in a separate worktree, and the phase report lists the results (AC-XC-05). Commit types: feat, fix, refactor, perf, build, ci, test, docs, chore, style, revert, data.
- Nothing is pushed or merged without your OK (D-10).
- Config migrations are numbered in build order: step 1 is the Dungeon Hub split (T1.9b), step 2 the updater keys (T1.4). Later migrations take the next free number (step 3 onward), so phases that migrate run in sequence, not in parallel.

**Test tiers:**

| Tier | Who | What | When |
|---|---|---|---|
| A | Claude | JUnit 5 unit tests for pure logic: island map, rule matching, config migration (fixture *shaped like* your config), DB parsing with BOM, tab/scoreboard/chat parsers, odds and bestiary math, updater selection/rate-limit/path checks | every commit (`./gradlew build`, also in CI) |
| B | Claude | `./gradlew runClient` smoke: mixins apply, `/ksu` opens, dev-only `/ksu debug island <name>` | every phase |
| C | Claude | Fabric client gametests in singleplayer with screenshots, which Claude reviews: glow on summoned named entities, labels behind stone/glass/water, beams, config screen, HUD editor at GUI scales 1–4 | where content can be simulated |
| D | Claude | Production boot of the exact jar against a **copy** of your 26.2 mod set (`-Pk8bas.prodMods=<dir>`, not part of `check`, `-Dmixin.debug.export`): crashes, new ERRORs, injection order, chat-listener phase order | G1, G2, G3, G4 (updater on a local mock source), G5, G6, both releases |
| E | **You** | ~30–40 min Hypixel smoke checklist in a Prism **copy** `26.2 Skyblock K8bas-test`: Hub, one dungeon run, one Glacite Mineshaft, shaders on/off, plus `/ksu debug dump tab` and `/ksu debug dump sidebar` captures, and an armed **container dump** of the `/sblevels` menus (D-17). Afterwards Claude reads `latest.log` and screenshots from disk. | G1; then batched per feature group |

**Test fixtures.**
- Your logs contain **no** raw tab-widget or sidebar lines; vanilla never logs them. So the Frozen Corpses widget, the shaft-code line and the Bestiary widget formats are **UNVERIFIED** until captured with the dump commands at G1. All D-17 menu captures (`/sblevels`, component menus) also happen at G1.
- Until then, the parsers use provisional fixtures taken from MIT regex tests (SkyHanni-REPO) and marked UNVERIFIED.
- All committed fixtures are sanitised: player names replaced, chat bodies trimmed to the matched format, no server IDs or UUIDs.

Running `runClient` and the gametests opens a game window on this PC, as your ground rule 3 asks; tell me if there are times to avoid.

**Report after each phase (REQ-XC-REPORT-01):** what changed (commits), what was tested (tiers and results), known issues, decisions needed, the skills used and any flagged rules questions.

---

## 6. Order of work

```
Phase 0 seams + tests + dev runtime (on 26.1.2)
 ─► Phase 1 port ─► compliance & correctness ─► [G1: tiers A–D, your smoke E + format captures]
 ─► early release v1.1.0+26.2 (port + compliance, notify-only GitHub check) via PR to main [D-13, R4]
 ─► Phase 2 UI: option model ─► render kit ─► widgets ─► screen ─► rule lists + switch ─► drop Cloth
               HUD framework ─► HUD editor                          ─► [G2]
 ─► Phase 3 features: 3.0 shared services + marker toolkit first; odds (T3.9) last, after D-3 ─► [G3]
 ─► Phase 4 updater ─► [G4]  ─► Phase 5 bestiary HUD ─► [G5]
 ─► Phase 6 SkyBlock XP Optimizer: calculator core ─► data tool + reference tables ─► task table + research
               ─► prices ─► progress detection ─► planner ─► table UI + screen ─► HUD ─► update skill ─► [G6]
 ─► Phase 7 docs, version, PR ─► your approval ─► merge ─► tag + GitHub release ─► [G7]
```

Phase 6 reuses:
- the shared data registry (T3.0c, the generalised brief 3.9 bundled database; D-26, Q-DATA-02)
- the profile service (T3.0d), the menu reader (T3.0e) and the chat/action-bar listener (T3.0g)
- the UI kit (Phase 2) and the HUD editor (T2.7/T2.8)
- the bestiary table (T5.1)

It can only start after Phase 5, but its **calculator core (T6.1)** is pure Java and could be built earlier, once T1.18 has merged, if you want progress sooner.

Highest-risk items come first: the port, the glow replacement, the config migration framework.

Rough size: Phase 1 ≈ 1–2 days; Phase 2 is the largest (~4–5.5k lines of Java); Phase 3 is the second largest; Phase 6 (optimizer) is large, mainly the data research; Phases 4 and 5 are medium; Phase 7 is small.

---

## 7. Tasks

Sizes: XS 1 file · S 1–2 · M 3–5. `Verify` always includes `./gradlew build` (tier A) unless noted.

Every task names the SPEC requirements it implements (`Req:`) and the acceptance criteria it proves (`Accept:`). Requirements that are met at a checkpoint rather than by a task (reviews, reports) are named in that checkpoint's list. **Every checkpoint report (G1, G1r, G2–G7) follows REQ-XC-REPORT-01**: what changed, what was tested, known issues and decisions (AC-XC-07). It also lists the skills used (REQ-XC-SKILLS-01, AC-XC-10) and flags questionable items instead of building them (REQ-XC-RULES-06, AC-XC-11). Requirements deliberately not built are listed under "Not built in 2.0.0" at the end of this section.

### Phase 0 — Setup (still on 26.1.2)

- [x] **T0.0 chore(git): repo-local identity, pre-commit privacy scan, per-commit build script** (S). Sets `Kesuhi <110562470+Kesuhi@users.noreply.github.com>` as the local identity on `update/26.2` (D-10). Adds `scripts/privacy-scan.sh`, installed as the pre-commit hook (A-10), and `scripts/build-each-commit.sh`.
  - Req: REQ-XC-GIT-01, REQ-XC-GIT-02, REQ-XC-PRIVACY-01, REQ-XC-BUILD-01
  - Privacy scan: e-mail patterns (noreply allowed), `C:\Users`, `/c/Users`, UUIDs and Hypixel server ids (`\b(m|M|mini|mega)\d{1,4}[A-Z]{1,3}\b`, placeholder `m000XX` allowed) in the staged diff. Hooks are never skipped.
  - Build script: builds every commit since the last checkpoint in its own worktree, lists hash + result, and checks each subject against the Conventional Commit regex.
  - Accept: AC-XC-08 (a seeded e-mail, path, UUID and server id are each blocked; the bare pattern mentions in SPEC.md, PLAN.md and the scan script itself pass, allowlisted by exact pattern, not by file); AC-XC-05 (script part); `git config --local user.email` is the noreply address (basis for AC-XC-12).
- [x] **T0.1 docs: add SPEC.md, PLAN.md and the `docs/sbxp/` schema draft** (XS, deps T0.0). Committed after your approval, once all files pass the privacy scan (emails, local paths, player names, server ids, UUIDs).
  - Req: REQ-XC-PLAN-01, REQ-XC-LICENSE-01, REQ-XC-LICENSE-05, REQ-XC-PRIVACY-01
  - D-25: right after approval, Claude deletes its scratch wiki/Fandom copies and everything parsed from them (outside the repo).
  - Accept: AC-XC-09 (your approval precedes this commit and the first `src/` commit); AC-XC-08.
- [x] **T0.5 docs(release): rules warning on the v1.0.0 and v1.0.1 release notes** (XS). D-13, extended to v1.0.0 by R4. Claude drafts the text, **you approve the exact wording**, then `gh release edit` puts it at the top of both bodies. Assets stay unchanged.
  - Req: REQ-REL-15
  - Content: these versions outline mobs out of line of sight and invisible mobs, which Hypixel's rules forbid; disable Mob Highlighter and NPC Search, or move to the newest 26.2 release.
  - Accept: AC-REL-13.
- [x] **T0.6 ci: forbidden-reference check** (S). One CI step (A-10, approved with the spec) runs `scripts/check-forbidden.sh` on every push and PR.
  - Req: REQ-XC-RULES-01, REQ-XC-RULES-02, REQ-XC-RULES-07, REQ-XC-LICENSE-05, REQ-PORT-12
  - Patterns: `cancellable = true` or `ci.cancel()` in mixins on network/packet targets; `org.lwjgl.opengl`; P7 words in `en_us.json`, README and `fabric.mod.json` ("gambling" only as a search keyword); wiki, Fandom, hypixel.net and reddit hosts in code that could fetch them (`src/main/java`, `src/client/java`, `tools/`). Hosts inside bundled data `sources` records (`src/main/resources/**/data/`) and validator test fixtures (`src/test/resources/**`) are allowed; the T3.0j validator checks those. T1.4 adds Modrinth hosts for `src/main`.
  - Accept: AC-XC-01 (grep part), AC-PORT-12; a seeded violation fails the job, the current tree passes, and a wiki host in a data `sources` record does not fail it.
- [x] **T0.2 refactor: test seams, no behaviour change** (M, deps T0.1). Files: `CompiledRule`/new `NameMatcher` (`matches(rule, name)`), `ConfigManager` (`load(Path)`), `MobDatabase`/`NpcDatabase` (`parse(String)`).
  - Req: REQ-XC-VERIFY-02, REQ-GLOW-01, REQ-CFG-03
  - Accept: the diff is a pure extraction; the game behaves identically (B).
- [x] **T0.3 test: JUnit 5 + characterization tests** (S, deps T0.2). `build.gradle`, `src/test/java/…`. JUnit is an ask-first dependency (SPEC §8).
  - Req: REQ-XC-VERIFY-02, REQ-GLOW-01, REQ-CFG-01, REQ-CFG-03
  - Accept: AC-GLOW-01 on 26.1.2 (NONE/CONTAINS/REGEX/EXACT, `§` stripping, island, distance and type gates); a fixture shaped like your config loads (baseline for AC-CFG-01); DB parsing with a UTF-8 BOM (EC-CFG-02). All green **before** the port.
- [x] **T0.4 build: dev runtime + `/ksu debug island` + tab/sidebar dumps** (M). `build.gradle`: `localRuntime "maven.modrinth:hypixel-mod-api:1.0.2+build.1+mc26.1"` from the Modrinth maven, verified resolvable; that build also targets 26.1.
  - Req: REQ-XC-VERIFY-01, REQ-XC-VERIFY-02, REQ-LOC-08, REQ-GS-13, REQ-GS-12
  - New `DebugCommand`: `/ksu debug island <name>` is dev-only. `/ksu debug dump tab|sidebar` ships in production and writes raw lines tagged `[K8BAS-DUMP]` to `latest.log`, read-only and only when you run it.
  - The hand-placed glue jar in the ignored `run/mods/` moves to `run/mods.disabled/`, so it does not load twice.
  - Accept: `runClient` reaches the title screen with the glue loaded from the Modrinth maven; the world-level checks are T0.4c's.
- [x] **T0.4c test(gametest): client gametest source set + 1.0.1 label baseline** (S, deps T0.4). Moved here from T1.5 because the waypoint baseline for T1.1 can only be captured reliably by a gametest on 26.1.2. `src/gametest` with `DebugToolsGameTest`, run by `./gradlew runClientGameTest` (not part of `check`, as it opens a game window).
  - Req: REQ-XC-VERIFY-02, REQ-XC-VERIFY-01, REQ-LOC-08, REQ-GS-13
  - In a singleplayer world: the debug commands are registered, the island can be forced and cleared, and the tab and sidebar dumps write tagged lines. Screenshots of the 1.0.1 label in the open and behind stone; the label crop is the template `waypoint-label-1.0.1-behind-stone.png`.
  - Accept: AC-LOC-09 [B], AC-GS-13 [B] (as a gametest); the template matches with the `exact()` comparison, and a one-glyph label change fails the test.
- [x] **T0.4b feat(debug): armed container dump + entity name-tag dump** (M, deps T0.4). Both ship in production. Read-only: no clicks, no paging, nothing opened.
  - Req: REQ-GS-13, REQ-GS-12, REQ-XC-VERIFY-02, REQ-XC-RULES-04, REQ-XC-PRIVACY-01
  - `/ksu debug dump containers on|off` **arms** a passive dump, because no command can be typed while a menu is open. While armed, each allowlisted menu *you* open is written once its contents are stable (the content packet, then 2 quiet ticks). It disarms after 60 min.
  - Allowlist (T3.0e reuses it):
    - "SkyBlock Leveling", "Ways to Level Up", "Skill Related Tasks", "Tasks ➜ *"
    - "<X> ➜ *" only when X is a leveling parent (Core, Event, Dungeon, Essence Shop, Slaying, Mining, Farming, Fishing, Foraging, Miscellaneous, Story, Complete Dungeons). The "Fishing ➜ *" pattern also matches the bestiary's fishing pages; T5.2b tells them apart by lore
    - Bestiary and "Bestiary ➜ *"; the D-27 component menus: Skills, Collections, Museum, HOTM, HOTF, Pets, Accessory Bag
    - Croesus, dungeon reward chests and "* RNG Meter" (fixtures for REQ-ODDS-17/23; titles UNVERIFIED)
  - `/ksu debug dump entities` writes the displayed name tags within 8 blocks, with their height above the entity below (fixture for AC-GLOW-08, T3.1). An invisible body without a displayed name is never listed.
  - Accept: AC-GS-12 [A] (60-min disarm, fake clock); unit tests of the stable-contents check and the allowlist; AC-GS-13 [E] at G1.

### Phase 1 — Port to 26.2 + mandatory hardening

*1A Port*

- [x] **T1.1 refactor(npcsearch): submit-based waypoint renderer** (S, deps T0.4c). `NpcWaypointRenderer` moves to `COLLECT_SUBMITS` + `submitText(… SEE_THROUGH …)`, which also exists in 26.1.2.
  - Req: REQ-PORT-05, REQ-PORT-06, REQ-PORT-12
  - Accept: compiles on 26.1.2; the T0.4c gametest still finds the 1.0.1 label template exactly behind **opaque** blocks (the translucent case is T1.3); no NaN at distance 0 or behind the camera (EC-PORT-06).
- [x] **T1.2 build!: target Minecraft 26.2** (M, deps T1.1).
  - Req: REQ-PORT-01, REQ-PORT-02, REQ-PORT-03, REQ-PORT-04, REQ-PORT-05, REQ-PORT-06, REQ-PORT-08
  - `gradle.properties`: MC 26.2, Loader 0.19.5, Fabric API 0.161.0+26.2, Cloth 26.2.155, Mod Menu 20.0.3, Loom pinned to an exact 1.17 release (no `-SNAPSHOT`), Gradle 9.5.1 kept.
  - `fabric.mod.json`: `minecraft ~26.2`, `fabricloader >=0.19.5`, `fabric-api >=0.161.0`, `cloth-config >=26.2.155`, `hypixel-mod-api >=1.0.2`.
  - One-line API moves (`gui.screen()/setScreen()`, `gui.hud.setTitle/resetTitleTimes`) in `MobHighlighterModule`, `NpcSearchModule`, `SettingsCommand`, `SettingsKeybind` and the T0.4/T0.4b debug code. Mappings: none needed (unobfuscated).
  - Both 1.0.1 mixins re-checked (target, 26.1.2 vs 26.2 descriptor, injection point) for the Phase 1 report; T1.10 deletes them.
  - Accept: AC-PORT-01, AC-PORT-03, AC-PORT-04, AC-PORT-05, AC-PORT-07; AC-PORT-08 [B] (no mixin apply warning in `runClient`).
- [x] **T1.5 test(gametest): client gametests on 26.2** (S, deps T1.2). The source set exists since T0.4c; this task makes it run on 26.2.
  - Req: REQ-XC-VERIFY-02
  - Accept: `runClientGameTest` passes on 26.2, including `DebugToolsGameTest`; tests can force the island through the T0.4 override. A template that changes because of 26.2 itself (not our renderer) is re-captured with a note in the Phase 1 report.
- [x] **T1.6 build: production-boot task** (S, deps T1.2). `prodClientStack` (`ClientProductionRunTask`) with the mod folder passed as `-Pk8bas.prodMods` and `-Dmixin.debug.export`, kept out of `check`.
  - Req: REQ-XC-VERIFY-02
  - Accept: boots with a copy of your mods; the stack's ERROR lines without our jar are recorded as the "no new ERROR" baseline; the CI build stays green.
- [x] **T1.3 fix(npcsearch): see-through labels after translucent terrain** (S, deps T1.5). Route labels through `submitCustom(SubmitRenderPhases.AFTER_TERRAIN, …)`, a 26.2-only Fabric API.
  - Req: REQ-PORT-07, REQ-PORT-06, REQ-MARK-03 (translucent-terrain part; generalised in T3.0b)
  - Accept: AC-PORT-06 (gametest screenshots from 5 and 30 blocks behind nothing, stone, glass and water; height at 30 = height at 10 ± 2 px), against the 1.0.1 style.
- [x] **T1.13 fix(lang): keybind category translation key** (XS, deps T1.5).
  - Req: REQ-PORT-11, REQ-CFG-13
  - Accept: AC-PORT-11; AC-CFG-12 (keybind names unchanged, so `options.txt` bindings survive).

*1B Correctness & compliance (ships with the port; see §1.3)*

- [x] **T1.7 fix(config): atomic, single-path saving** (M, deps T0.3). `ConfigManager`, the modules' `setEnabled`, new `config/store/*`.
  - Req: REQ-CFG-04, REQ-CFG-05, REQ-CFG-10, REQ-CFG-12
  - One serialized save path: coalesced, on disk ≤ 2 s after a change, flushed on shutdown; temp file + atomic move with Windows lock retries. The store is reusable for other files (T1.4a state file, T3.0d profiles).
  - Accept: AC-CFG-03, AC-CFG-04, AC-CFG-09, AC-CFG-11; EC-CFG-03, -04, -06; `saveAsync` removed.
- [x] **T1.7c fix(config): `configVersion` + ordered migration runner** (S, deps T1.7). `SkyblockUtilityConfig`, new `config/migration/*`: numbered steps, run in order, reusable for other persisted files.
  - Req: REQ-CFG-02, REQ-CFG-09, REQ-CFG-12
  - Accept: AC-CFG-02.
- [x] **T1.7b fix(config): safe load — backups, per-section recovery, clean-up** (M, deps T1.7, T1.7c).
  - Req: REQ-CFG-01, REQ-CFG-03, REQ-CFG-06, REQ-CFG-07, REQ-CFG-08, REQ-CFG-11
  - A broken file or section: byte-exact timestamped backup before any write, defaults for that part only, one chat notice after joining. Null or unknown values normalised, except that an absent `updateChannel` stays absent (no explicit choice; never filled with a default on load, repair or save, REQ-UPD-07); a newer `configVersion` is backed up with a WARN; one-time `.v0.bak` before the first data-changing step; unknown module sections kept.
  - Accept: AC-CFG-01, AC-CFG-05, AC-CFG-06, AC-CFG-07, AC-CFG-08, AC-CFG-10, AC-CFG-13; EC-CFG-01, -02, -07, -10.
- [x] **T1.8 fix(highlight)!: never highlight invisible entities** (S, deps T0.3). `HighlightManager.findMatch`: `isInvisible()` means no match and no `onMatch`, also with visible armour.
  - Req: REQ-GLOW-03, REQ-XC-RULES-04
  - Accept: AC-GLOW-03 [A] (eligibility unit test: invisible, invisible with armour, visible; the gametest is in T1.10). **Lands before T1.9** (AC-LOC-10), because the island fix enables Shadow Assassin and Fels rules.
- [x] **T1.8b fix(highlight): inert invalid rules, no player matches** (S, deps T1.8). `NameMatcher`/`CompiledRule`, `HighlightManager`.
  - Req: REQ-GLOW-10, REQ-GLOW-11
  - An empty CONTAINS/EXACT pattern, an invalid regex or an unparsable type makes the rule inert, logged once and kept in the file (UI flag: T2.5a). The local player never matches; real players are skipped by name-tag rules; player-type NPCs still match.
  - Accept: AC-GLOW-09; AC-GLOW-10 [A] (the [C] part is in T1.10); EC-CFG-09.
- [x] **T1.9 fix(location): correct mode table + location snapshot + change events** (M, deps T1.8). `IslandTracker` → immutable snapshot (raw mode, map, server name, server type, island, on SkyBlock); change events on the client thread; cleared on disconnect and world change. The T0.4 override goes through it; T3.0a consumes the events.
  - Req: REQ-LOC-01, REQ-LOC-02, REQ-LOC-03, REQ-LOC-04, REQ-LOC-05, REQ-LOC-06, REQ-LOC-07, REQ-LOC-08, REQ-LOC-09, REQ-LOC-10
  - Mappings: the REQ-LOC-02 table, with `dungeon` → "Catacombs" and `dungeon_hub` → "Dungeon Hub" (D-2); `catacombs` and `jerry` dropped. Unknown modes logged once. Island names with short descriptions exposed for the UI.
  - "On SkyBlock" comes from the server type, so unmapped SkyBlock modes still count (needed by T3.5–T3.7).
  - Accept: AC-LOC-01, AC-LOC-05, AC-LOC-06, AC-LOC-07, AC-LOC-08 [R], AC-LOC-10, AC-LOC-12, AC-LOC-13; EC-LOC-01, -02, -06.
- [x] **T1.9b fix(location): Dungeon Hub migration + NPC data normalisation** (S, deps T1.7b, T1.9). `NpcDatabase`, migration step 1.
  - Req: REQ-LOC-03, REQ-GLOW-13, REQ-CFG-09
  - `NpcDatabase` normalises on load: fixed + "Catacombs" becomes "Dungeon Hub".
  - Migration: your rules with fixed + "Catacombs" move to "Dungeon Hub"; Trinity/Tomioka/Duncan (moving) and mob rules stay, still enabled.
  - Accept: AC-LOC-02, AC-LOC-03, AC-LOC-04 [B], AC-GLOW-12 [A].
- [x] **T1.10a refactor(highlight): match on the client tick, render reads a cache** (S, deps T1.8b). `HighlightManager` resolves name tags and matches once per tick; the render path only looks up results.
  - Req: REQ-GLOW-06, REQ-GLOW-08, REQ-GLOW-01
  - Accept: AC-GLOW-07; the thread-assert part of AC-GLOW-05; EC-GLOW-05 (counter test: each name tag resolved at most once per entity per tick).
- [x] **T1.10 feat(highlight)!: depth-tested glow via Render Chest** (M, deps T1.5, T1.6, T1.10a).
  - Req: REQ-GLOW-02, REQ-GLOW-04, REQ-GLOW-05, REQ-GLOW-07, REQ-GLOW-16, REQ-GLOW-17, REQ-XC-RULES-03, REQ-XC-LICENSE-02, REQ-PORT-08
  - `build.gradle`: `maven { url = 'https://maven.azureaaron.net/releases'; content { includeGroup 'net.azureaaron' } }`, `include(implementation("net.azureaaron:render-chest:1.0.3+26.2"))` (D-1).
  - New `GlowHandler` (AD-1) on the T1.10a cache; delete both mixins, `k8bas_skyblock_utility.mixins.json` and its `fabric.mod.json` entry; start `THIRD_PARTY_NOTICES.md` with Render Chest (Apache-2.0, LICENSE in the nested jar).
  - Accept (gametest): AC-GLOW-02, AC-GLOW-03 [C], AC-GLOW-04, AC-GLOW-10 [C], AC-GLOW-17, AC-XC-02; review AC-GLOW-05 [R], AC-GLOW-16. Tier D: AC-GLOW-06 [D], no crash next to Skyblocker/SkyHanni, callback order recorded.
- [x] **T1.11 fix(npcsearch): "You found X" only after line of sight, once per run** (S, deps T1.9, T1.10). Gated on `player.hasLineOfSight(entity)` within the scan range on the client tick; once per rule per server, reset on each location change event; own toggle, default ON (D-6).
  - Req: REQ-GLOW-14, REQ-XC-RULES-05, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02
  - Accept: AC-GLOW-13 (gate unit tests; gametest: no title behind a wall, one title in view); EC-GLOW-06, -07, -08.
- [x] **T1.12 fix(net): HTTP timeouts + DB entry validation** (S). 10 s connect and request timeouts for the gist fetch; malformed entries skipped. Kept because v1.1.0 still reads the gists (R16); T3.8 removes the fetch.
  - Req: REQ-NPCDB-05
  - Accept: AC-NPCDB-04.

*1C Interim updater (ships in v1.1.0; replaces T1.4's original scope, R4/R16)*

- [ ] **T1.4a refactor(update): GitHub release source + check scheduling** (M, deps T1.7, T1.7c). Pulled forward from T4.1. `GitHubReleaseSource`: one unauthenticated GET of the release list with the required headers, ETag/304 cache, back-off, limits persisted in a separate state file (T1.7 store), 2 MiB cap, malformed entries skipped. Timeouts per REQ-UPD-09: connect 10 s, API request 20 s. The test-source override works only in dev and test builds; the production jar ignores it.
  - Req: REQ-UPD-03, REQ-UPD-04, REQ-UPD-09, REQ-UPD-19, REQ-UPD-20, REQ-CFG-12
  - Accept: AC-UPD-03; AC-UPD-04 automatic rows (interval, 24 h cap, toggle OFF); AC-UPD-09 except the manual-check row; AC-UPD-10 [A] automatic part (background thread, one WARN per attempt, no stack trace, no toast); EC-UPD-12; the mock-server log holds only `api.github.com` and no player name or UUID (AC-UPD-21 [A]).
- [x] **T1.4b feat(update): SemVer compare + asset selection** (S, deps T0.3). Pulled forward from T4.1. `SemVer` and `AssetSelector`: the REQ-REL-07 pattern, exact-or-prefix MC match, `state: uploaded`, drafts ignored, STABLE/BETA channel.
  - Req: REQ-UPD-05, REQ-UPD-06, REQ-UPD-07
  - Accept: AC-UPD-05, AC-UPD-06, AC-UPD-07 (selection and the one log line; the manual-check text is T4.1's), AC-UPD-08 (with no stored channel choice, a running `2.1.0-beta.1` resolves to BETA; a stored STABLE stays STABLE); EC-UPD-01, EC-UPD-09.
- [ ] **T1.4 feat(update)!: notify-only GitHub check replaces the Modrinth updater** (M, deps T1.4a, T1.4b, T1.7b, T1.9b). Formerly "derive the MC version at runtime". The Modrinth `UpdateChecker` is deleted; the check runs off-thread after startup, with the MC version from the Loader.
  - Req: REQ-UPD-01, REQ-UPD-08, REQ-UPD-10, REQ-UPD-19, REQ-PORT-10, REQ-XC-RULES-01
  - A newer candidate gives one local chat line per session, on the first world join, with [Changelog] [Open release page] (validated URL, vanilla link confirmation). No toast, no *Install*, nothing downloaded. Error and no-jar rows of REQ-UPD-08 stay silent with one log line.
  - Migration step 2: `autoUpdateDownloadEnabled` dropped; `autoUpdateCheckEnabled` kept, relabelled "Check for updates (notify)", default ON; `updateChannel` stays absent until you pick a channel (absent = the REQ-UPD-07 default: STABLE, or BETA on a pre-release build; null is never written, REQ-CFG-07). T0.6's check gains Modrinth hosts for `src/main`.
  - Accept: AC-UPD-01, AC-PORT-10; AC-UPD-20 (the parts v1.1.0 has); the chat-line part of AC-UPD-11 (once on the first join, not on the second).

*1D Changelog*

- [ ] **T1.14 docs(changelog): start `CHANGELOG.md`** (XS, deps T1.4, T1.9b, T1.11). Keep a Changelog with `## [Unreleased]`: the behaviour changes from T1.4 and T1.8–T1.11, marked as such, every default (P6), and a "Not ported cleanly / behaviour changes" list.
  - Req: REQ-PORT-09, REQ-GLOW-15, REQ-REL-05
  - Accept: AC-PORT-09 (CHANGELOG part); the CHANGELOG part of AC-GLOW-14 (the four glow changes, no P7 word).

**Checkpoint G1:**
- [ ] tiers A–D green (AC-XC-06): `build`, `runClient`, `runClientGameTest`, and `prodClientStack` with a copy of your mod set and `-Dmixin.debug.export`. No crash and no new ERROR (AC-PORT-13, AC-GLOW-06 [D]); `/ksu debug island` is absent and the dump commands exist (AC-LOC-09, AC-GS-13 [D])
- [ ] `code-reviewer` review, including the rules review (AC-XC-01), the licence check (AC-XC-03), no GL calls (AC-PORT-12) and T1.8 landing before T1.9 (AC-LOC-10)
- [ ] your smoke (E) passes in the Prism copy `26.2 Skyblock K8bas-test` (~30–40 min plus the captures):
  - first, turn "Check for updates (notify)" OFF in the Prism copy (EC-UPD-14); it stays OFF for the G3, G5 and G6 checks
  - boot; your 1.0.1 config migrated (`.v0.bak` present, Croesus rule on "Dungeon Hub", the three dungeon NPC rules still on); your three keybinds still bound
  - island log lines with raw mode and server in the Hub, the Dungeon Hub, one dungeon run and one Glacite Mineshaft (AC-LOC-11); hub waypoints show in the Dungeon Hub and never in the run (AC-LOC-04)
  - glow visible-only, with Iris shaders on and off (AC-GLOW-15), and with the Skyblocker/SkyHanni/NoFrills glow options on and off (AC-GLOW-06)
  - label behind a wall and behind water; `/ksu` opens (AC-PORT-14)
  - EntityCulling stress: safeMode off, 2 min in a busy area (AC-GLOW-05)
  - `/ksu debug dump entities` next to Mort in the run or a Dungeon Hub NPC (fixture for AC-GLOW-08 [E], evaluated in T3.1). If a rare room appears, also dump there and note whether the three NPCs glow: a pre-T3.1 baseline, not an AC-GLOW-11 result (judged from G3 on)
  - **tab and sidebar dumps** (`/ksu debug dump tab|sidebar`) in a bestiary area and in a mineshaft, with the Profile, Stats, Bestiary and Frozen Corpses widgets where shown
  - **menu captures (D-17, all moved to G1):** `/ksu debug dump containers on`, then open `/sblevels` and its category menus (~16 screens, ~5 min) and the component menus Skills, Collections, Museum, Bestiary (plus one "Bestiary ➜" page and one bestiary "Fishing ➜" page), HOTM, HOTF, Pets and Accessory Bag. Optional: the full step-level walk (~15–25 min) and a Croesus or reward-chest menu when passing by. SkyBlockAPI's `chest_dumps` toggle is an alternative (SPEC §12.2)
  - **mineshaft check 1:** is the Frozen Corpses widget on by default, and which command and menu path enable it (`/tablist` or `/widgets`)? This fixes the REQ-GS-18 hint string (AC-GS-18 [E], Q-MSA-04)
  - **mineshaft check 2:** does a Tungsten or Umber key held only in the Dwarven Sack open a corpse? This sets the REQ-MSA-05 fallback constant
- [ ] Claude reads `latest.log` and the screenshots and commits the captures as sanitised fixtures (`test(fixtures): …`; player names, server ids and UUIDs removed); the UNVERIFIED list is updated (REQ-GS-14)
- [ ] every commit since the branch point, including the `test(fixtures)` commit, builds in a worktree (AC-XC-05); author check (AC-XC-12)
- [ ] push `update/26.2` (D-10); CI green on the pushed head
- [ ] Phase 1 report (AC-XC-07): dependency table (AC-PORT-02), per-mixin record (AC-PORT-08), "Not ported cleanly / behaviour changes" (AC-PORT-09), glow-provider order (REQ-GLOW-07), flagged items (AC-XC-11), skills used (AC-XC-10), per-commit build hashes and results (AC-XC-05), open field checks

### Phase 1r — Early release v1.1.0+26.2 (after G1)

Same rules as the final release (D-13, R4): an intermediate PR to `main`. Your approval covers the merge only; the tag push and publishing need your separate "ship".

- [ ] **T1.15 build(release): version 1.1.0 + `<version>+<mc>` jar naming + sidecar** (S, deps phase 1). Mod version `1.1.0` in `gradle.properties`. `build.gradle` names the jar `k8bas_skyblock_utility-1.1.0+26.2.jar`, sets `fabric.mod.json` `version` to `1.1.0+26.2`, and writes `<jar>.sha256` (64 lowercase hex, two spaces, name, LF, no BOM).
  - Req: REQ-REL-06, REQ-REL-07, REQ-REL-14
  - Accept: AC-REL-05 with 1.1.0; T1.4b's selector accepts the built name and the running version string.
- [ ] **T1.16 docs(release): README, CHANGELOG and mod description for v1.1.0** (M, deps T1.14, T1.15).
  - Req: REQ-REL-14, REQ-REL-01, REQ-REL-02, REQ-REL-03, REQ-REL-04, REQ-REL-05, REQ-GLOW-15, REQ-PORT-09
  - README, limited to what v1.1.0 ships: "Minecraft 26.2 (Fabric)" with Java 25, Loader ≥ 0.19.5, Fabric API, Hypixel Mod API and Cloth Config; 26.1.x dropped, 1.0.1 was the last 26.1 build; compliance section; non-affiliation statement; network access (GitHub update check and its toggle, the NPC/mob gists until they are bundled); licensing (CC0, Render Chest Apache-2.0).
  - Upgrade note in README and CHANGELOG: 1.0.x users install by hand once; settings migrate with a backup; behaviour changes (visible-only glow, invisible mobs never highlighted, "You found X" after line of sight, Catacombs/Dungeon Hub split, notify-only update check).
  - `[Unreleased]` becomes `## [1.1.0] - YYYY-MM-DD`, usable word for word as the release notes. The Modrinth claims in README and `fabric.mod.json` are replaced.
  - Accept: AC-REL-03, AC-REL-04 (headings, subsections and the `[1.1.0]` section; the lint and extraction are T1.17's); AC-REL-01 and AC-REL-02 for what v1.1.0 ships; the README part of AC-GLOW-14; T0.6's check passes.
- [ ] **T1.17 build(release): release check + `RELEASING.md`** (M, deps T1.4b, T1.15, T1.16). `scripts/release-check.sh` and a JUnit check run on the draft before publishing; `RELEASING.md` documents the procedure.
  - Req: REQ-REL-07, REQ-REL-11, REQ-REL-16, REQ-REL-05, REQ-XC-PRIVACY-01
  - Checks on the draft's JSON (authenticated `gh api`): T1.4b's selection picks the jar; API digest = local SHA-256 = sidecar (no BOM, no CRLF); body = the CHANGELOG section; the release name is `K8bas Skyblock Utility v<version>` (REQ-REL-07); the jar's `fabric.mod.json` id, version and MC range.
  - `scripts/changelog-lint.sh`: heading regex, standard subsections only, a section for the version, and the extraction command that produces the release body (AC-REL-04).
  - The privacy scan gains a range and PR-body mode (`main..update/26.2` plus a body file) for T1.18 and T7.3.
  - `RELEASING.md`: draft → verify → publish, the REQ-REL-07 contract, the sidecar pitfall, the CHANGELOG extraction command, data-only PATCH releases.
  - Dry run (AC-REL-14) pushes no tag: `gh release create v0.0.0-dryrun.N --draft --target <commit> --title … --notes-file …` (no `--verify-tag`; a draft creates no git tag), the draft checks run on it against a throwaway jar built as version `0.0.0-dryrun.N` (never committed) and a matching notes file, so the selection, name, body and `fabric.mod.json` checks compare like with like, then `gh release delete v0.0.0-dryrun.N --yes`, and `git ls-remote --tags origin` confirms that no such tag exists. The tag push and `--verify-tag` are logged as skipped by design. Creating and deleting the draft each need your OK.
  - Accept: AC-REL-09 [A] on a recorded draft fixture; AC-REL-04 [A] (lint and extraction on the `[1.1.0]` section); AC-REL-14.
- [ ] **T1.18 chore(release): intermediate PR `update/26.2` → `main` for v1.1.0** (S, deps T1.16, T1.17). The body covers changes since v1.0.1 (`bc0f2f6`), test evidence per tier, G1 field-check results, known issues, and decisions D-1–D-29 and R1–R17. Before opening: the per-commit worktree build since G1 is green (AC-XC-05), `git log --format=%ae main..update/26.2` shows only noreply addresses (AC-XC-12), the T1.17 range and PR-body privacy scan is clean, a `code-reviewer` pass on T1.15–T1.17 is done, and CI is green on the head. Pushing the commits made since G1 and opening the PR each need your OK (not a checkpoint push: REQ-REL-09, SPEC §8). **Then wait for your approval**, and merge with a merge commit (D-10).
  - Req: REQ-REL-14, REQ-REL-08, REQ-REL-09, REQ-XC-GIT-02
  - Accept: AC-REL-06 and AC-REL-07 for this PR; AC-XC-05, AC-XC-12; CI green on its head; a commit added after the approval voids it (EC-REL-12).
- [ ] **T1.19 chore(release): tag and publish v1.1.0** (S, deps T1.18). Starts only after the merge **and** your "ship" (R4).
  - Req: REQ-REL-14, REQ-REL-10, REQ-REL-11
  - Order: preflight as in T7.4 step 1 (EC-REL-03/-04/-07) → annotated tag `v1.1.0` on the merge commit → `./gradlew clean build` from that clean commit → push the tag → `gh release create v1.1.0 --verify-tag --draft --title "K8bas Skyblock Utility v1.1.0" --notes-file <CHANGELOG section>` with the jar and sidecar → T1.17 checks and a tier D boot of the exact jar → publish as Latest, not as a pre-release. Any failed check stops before publishing and is reported; fixes happen only inside the draft (EC-REL-05), then all checks rerun.
  - Accept: AC-REL-12 (AC-REL-04, -05, -08 and -09 with v1.1.0); a never-published `1.1.0-test.1` build (tier D) names v1.1.0 in exactly one chat line and downloads nothing.

**Checkpoint G1r:**
- [ ] v1.1.0 is public and marked Latest; `main` and its README match the release
- [ ] the warnings on v1.0.0 and v1.0.1 are in place (AC-REL-13, T0.5)
- [ ] the per-commit build, author check and `code-reviewer` pass were done before T1.18 opened the PR (results in the report)
- [ ] G1r report (AC-XC-07): release URL, asset digests, check results, and the `1.1.0-test.1` notify check

### Phase 2 — New UI (AlpakaAddons-style, UI only)

*Until T1.18 has merged, only Phase 1r commits land on `update/26.2` (all work stays on that branch, REQ-XC-GIT-01), so the v1.1.0 PR holds only the port and compliance work. Phase 2–7 work, including an early T6.0/T6.1, starts after that merge; every Phase 2–7 task depends on T1.18 directly or through its deps.*

**Spec:** SPEC `ui-config` (REQ-UI-*) and `hud` (REQ-HUD-*); decisions D-8 and R12.
- One centred panel: 70 % × 68 % of the GUI area, clamped to 480–660 × 340–440 GUI px, with ≥ 4 px margin (the margin wins). 38 px header (name, version, search), 160 px sidebar with match badges, 44 px cards (6 px gaps) under section headers.
- Categories: General · Highlights · Waypoints · Mining · Fishing · Odds & Trackers · SkyBlock XP. Empty categories are hidden. Every feature is a card with its own toggle.
- Live-apply. The file is written once per close (any route) and on discrete commits. `/ksu <term>` opens the screen pre-filtered, unless `<term>` is a reserved subcommand.
- HUD: anchor-relative positions. In the editor, Esc = Save and Cancel reverts. HUDs are hidden under any screen except chat.
- Order: UI track T2.1 → T2.6; HUD track T2.7 → T2.8 in parallel; T2.8b after T2.5b. Stretch items T2.9–T2.9d only after G2.

- [ ] **T2.1 refactor(module): option model independent of Cloth** (M, deps T1.7, T1.11, T1.18). `Module`, new `ui/option/*`, declarations for General and both modules. Each option is declared once (title, ≤ 2-line description, tooltip, hidden keywords, default, AMBER flag, status), and that declaration feeds both the cards and the search index. Cloth keeps working until T2.5b.
  - Req: REQ-UI-05, REQ-UI-07, REQ-UI-08, REQ-UI-19, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02, REQ-XC-RULES-07, REQ-GLOW-15
  - Index: case-insensitive substring match over title, description, keywords and dropdown labels; query trimmed and capped at 35 chars; hit counts per category.
  - Accept: AC-UI-07 [A]; AC-UI-17 [A] (every AMBER declaration has a tooltip); the AC-UI-04 defaults checked as a unit test against SPEC §12.H; AC-GLOW-14 (card tooltip text = CHANGELOG wording).
  - Also tested: no P7 word in titles or descriptions ("gambling" only as a keyword); no option for through-wall or invisible highlighting; no UI-library import in `ui/option`; every 1.0.1 fixture key (AC-UI-15) maps to a declaration or a documented migration.
- [ ] **T2.2 feat(ui): render kit** (M, deps T1.18). Theme tokens (dark palette; accent read from config, default teal `#29B6B2`; fixed destructive and error colours), rounded rects from fills (radius 6/5/4), vanilla-font text helpers without shadow, ~150 ms easing, click sound, and a scissor that refuses negative clip rects. Written from the behaviour spec only.
  - Req: REQ-UI-17, REQ-UI-02, REQ-UI-24, REQ-XC-LICENSE-04
  - Accept: gametest screenshot of a test panel at GUI scales 1–4; AC-UI-16 [A] (fresh-config accent `#29B6B2`); a zero-size clip at 320×240 logs no error.
- [ ] **T2.3a feat(ui): basic widgets** (M, deps T2.2). Toggle; int/decimal slider (step, scroll wheel, Shift for a fine step, reacts only to presses on its track, commit event on release); buttons (normal/primary/destructive); text field with a caret/selection edit model. A focused field swallows game and mod keys.
  - Req: REQ-UI-06, REQ-UI-03, REQ-UI-22, REQ-UI-17
  - Accept: AC-UI-05 [A] (edit model, incl. first-line paste, tabs → spaces, max length); AC-UI-06 [A] (3 notches at step 0.1 = +0.3); screenshots of every widget state.
- [ ] **T2.3b feat(ui): advanced widgets** (M, deps T2.3a). Single-choice dropdown (opens above the cards, closes on a click outside). Keybind capture (click arms it, Esc unbinds, right-click resets, conflicts shown in the error colour). Colour-picker modal reusing our ColorWheel: wheel, brightness, `#RRGGBB`/`#AARRGGBB` hex, presets, Cancel/Save; alpha controls only for colours that store alpha.
  - Req: REQ-UI-06, REQ-UI-13, REQ-UI-14, REQ-UI-22
  - Accept: AC-UI-12; AC-UI-06 [C] (dropdown selection and outside click); EC-UI-11.
- [ ] **T2.3c feat(ui): tooltip helper + virtual-row list** (S, deps T2.3a). Wrapped multi-line tooltips that stay inside the window, and a list that lays out and hit-tests only the visible rows. The rule lists and the picker use them now; the optimizer table (T6.7) uses them later.
  - Req: REQ-UI-19, REQ-UI-20
  - Accept: AC-UI-17 [C] (a 6-line tooltip at the panel's right/bottom edge stays inside the window at scales 1–4); AC-UI-18 [A] (5,000 rows of 18 px in a 200 px viewport → ≤ 13 rows laid out, click y → correct index).
- [ ] **T2.3d feat(ui): notices (toasts)** (S, deps T2.2). A title plus ≤ 2 body lines, in a corner or top-centre, shown 1–15 s, at most 4 stacked (oldest retired first). Notices never take focus or input, may link to a screen, and never start an action. Includes a "notice channel" option helper for warning features, default OFF.
  - Req: REQ-UI-21
  - Accept: AC-UI-19 (with 5 notices in 1 s: ≤ 4 shown, oldest retired first, each gone after its duration ±1 tick plus the slide, input still reaches the game; the channel helper defaults to OFF).
  - Consumers: the update prompt (T4.4) and the optional notice channel on the "hotspot gone" warning (T3.6b). The mineshaft alert offers no notice channel in 2.0.0 (REQ-UI-21 "may"; mineshaft-alert is a chat alert only).
- [ ] **T2.4a feat(ui): config screen layout** (M, deps T2.1, T2.3b, T2.3c). Backdrop, panel, header (name, version, search slot, 1 px accent line), sidebar tabs with badges, category heading and description, section headers, cards, and scrolling per region. Drawing and hit-testing share one layout pass, which reruns on resize or GUI-scale change and keeps the screen state. Opened through a dev entry point for now.
  - Req: REQ-UI-01, REQ-UI-02, REQ-UI-03, REQ-UI-04, REQ-UI-05, REQ-UI-07, REQ-UI-17, REQ-XC-TOGGLE-01
  - Placement follows REQ-UI-04, which supersedes the research IA (no through-wall or "Refresh now" option). A click anywhere on a toggle card toggles it. When a feature is off, its sub-options are dimmed, not hidden.
  - Provides: the card's "unavailable" state used by REQ-BOB-08, REQ-HOT-13 and REQ-DRILL-07 (hook status comes from Phase 3).
  - Accept: AC-UI-01, AC-UI-02, AC-UI-03, AC-UI-04 (Phase 2 build), AC-XC-04 (screen part); EC-UI-01, EC-UI-04, EC-UI-07, EC-UI-12.
- [ ] **T2.4b feat(ui): search** (S, deps T2.4a). Search box wired to the T2.1 index: filters cards and sidebar, badges show hit counts, the screen switches to the first category with a hit, and no hit shows `No settings found for "<q>"`. Ctrl+F focuses the box, Esc leaves it, × clears it, and the query survives a category switch.
  - Req: REQ-UI-08, REQ-UI-22
  - Accept: AC-UI-07 [C]; AC-UI-05 [C] (E and the "Open settings" key do nothing while the box is focused); EC-UI-10.
- [ ] **T2.4c feat(ui): live-apply save model** (S, deps T2.4a). Changes apply immediately. Config-store's single save path (T1.7) writes on every close route (Esc, screen replaced, disconnect) and on discrete commits. A slider drag writes once, on release. Each module's rebuild hook runs once per close, and there is no "unsaved changes" prompt.
  - Req: REQ-UI-15, REQ-CFG-10
  - Accept: AC-UI-14 (counted on a write counter); EC-UI-02, EC-UI-14.
- [ ] **T2.4d feat(ui): General category** (S, deps T2.4c, T2.3d). Interface: accent swatch, notice position and duration. Keybinds: capture widgets for Open settings, Toggle Mob Highlighter and Toggle NPC Search. Updates: the existing "Check for updates (notify)" toggle (T4.4b adds the rest). "Edit HUD layout" comes with T2.8b.
  - Req: REQ-UI-04, REQ-UI-14, REQ-UI-16, REQ-UI-17, REQ-UI-21
  - Accept: AC-UI-16 [C] (accent `#FF5252` applies live and after a restart; destructive and error red unchanged); AC-UI-13; EC-UI-15; EC-UI-16 (the first open after a config backup shows one notice naming the backup file).
- [ ] **T2.5a feat(ui): rule lists** (M, deps T2.4c, T1.9). Collapsible rule cards for Mob Highlighter and NPC Search. The collapsed header shows the label and a colour dot. The expanded card has label, enabled, entity type, island (a dropdown of the location island list), match mode, pattern, colour and a destructive Delete; fixed-NPC coordinates are read-only. Later fields plug in (e.g. the T3.4 beam override), and rule labels and patterns join the search index.
  - Req: REQ-UI-09, REQ-UI-11, REQ-UI-12, REQ-UI-15, REQ-LOC-09, REQ-GLOW-10
  - Accept: AC-UI-08; AC-UI-11; AC-UI-10 (the edit, colour-only and delete parts survive a restart); AC-UI-15 [C] (rules show their original label, island, pattern and colour); EC-UI-07, EC-UI-09, EC-UI-17.
- [ ] **T2.5c feat(ui): database picker** (S, deps T2.5a). "Add from database" opens a search field, island folders and an Add button per entry, on the virtual list. Entries that already back a rule are hidden. If the gist fetch failed (the registry case follows in T3.8, AC-DATA-05 [B]), the picker shows "database unavailable" with Add disabled.
  - Req: REQ-UI-11, REQ-UI-20
  - Accept: AC-UI-10 (in full, incl. an added entry disappearing from the picker and no "Changes not saved" prompt); AC-UI-18 [C] (NPC picker scroll sequence); EC-UI-08 (gist-failure variant; the registry case is T3.8's). The invisible-by-design greying of REQ-UI-11 is built in T3.8c on T3.8b's flag (AC-NPCDB-07).
- [ ] **T2.5b refactor(ui): switch entry points, remove the modules' Cloth code** (M, deps T2.5c, T2.4b, T2.4d). `/ksu` and `/kskyblockutility` open the new screen on the next tick. `/ksu <text>` pre-fills the search box, unless `<text>` starts with a reserved word from one central list (`hud`, `debug`, `corpses`, `update`, `sbxp`, plus later ones). The "Open settings" keybind (unbound by default) and Mod Menu also open it, closing returns to the opener, and everything works without Mod Menu. Removes `Module.buildConfigScreen/onConfigScreenSaved` and both modules' Cloth code.
  - Req: REQ-UI-10, REQ-UI-23, REQ-UI-25
  - Accept: AC-UI-09 [B] (`/ksu hud` part in T2.8b); AC-UI-22 for the command code; EC-UI-03; no Cloth import outside the files T2.6 deletes.
- [ ] **T2.6 build(ui)!: remove Cloth Config** (M, deps T2.5b). Delete `SettingsScreenFactory`, `ButtonEntry`, `LiveTextFieldEntry`, `DirtyMarkerEntry`, `ColorWheelFieldEntry`; edit `build.gradle`, `gradle.properties`, `fabric.mod.json`.
  - Req: REQ-UI-23, REQ-UI-16, REQ-UI-14, REQ-CFG-03, REQ-CFG-13
  - Accept: AC-UI-20 [R] (`grep -r shedaniel src` is empty; no `cloth-config` dependency); AC-UI-15 [A] (open/close without edits keeps every key; `mobScanRangeBlocks` 200 stays 200, EC-UI-05; the three `options.txt` key lines are unchanged); AC-CFG-12.
- [ ] **T2.7 feat(hud): HUD framework** (M, deps T1.7, T1.18). Element contract, registry and `HudElementRegistry` hookup, with anchor-relative positions stored in `general.hud.positions`. Draws only enabled elements that have content.
  - Req: REQ-HUD-01, REQ-HUD-02, REQ-HUD-03, REQ-HUD-04, REQ-HUD-05, REQ-HUD-10, REQ-HUD-11, REQ-CFG-12
  - Position: 9-point anchor + offset + scale (0.5–3.0, stored to 0.01). Re-anchoring on drag end does not move the element; elements grow away from their anchor; the draw-time clamp never rewrites the stored entry.
  - Elements are hidden on F1, in the editor and under any screen except chat (R12). The draw path only reads precomputed state. The test element exists only in the gametest source set.
  - Accept: AC-HUD-01, AC-HUD-02, AC-HUD-03, AC-HUD-10 [R]; EC-HUD-04, EC-HUD-05, EC-HUD-09.
- [ ] **T2.7b feat(hud): multi-line text element + default-layout check** (S, deps T2.7). The feature supplies the lines and which of them show; bounds = drawn text + 2 px, updated when the content changes. A unit test checks that no two shipped default rectangles overlap (preview content, 1920×1080, GUI scale 2). Every later HUD consumer re-runs it.
  - Req: REQ-HUD-12, REQ-HUD-13
  - Accept: AC-HUD-11, AC-HUD-12; EC-HUD-10.
- [ ] **T2.8 feat(hud): HUD editor** (M, deps T2.7, T2.3a). Staged editor in the Phase 2 style. It draws every element once, using preview content when there is no live content. Buttons: Save, Cancel (reverts), Reset Selected, Reset All.
  - Req: REQ-HUD-02, REQ-HUD-05, REQ-HUD-07, REQ-HUD-08, REQ-HUD-09
  - Display: backdrop, instructions, status line; outlines for idle, hovered, selected and disabled; a name label (below the box in the top 48 px); disabled elements greyed with "(disabled)".
  - Input: drag without a jump; a click on empty space deselects; the scroll wheel scales in 0.1 steps; arrows nudge 1 px (Shift 10 px); elements stay inside the window. Esc and external closes act as Save (R12). The file is written only on Save, close or reset.
  - Accept: AC-HUD-04, AC-HUD-05, AC-HUD-07, AC-HUD-08, AC-HUD-13; EC-HUD-01, EC-HUD-02, EC-HUD-03, EC-HUD-06, EC-HUD-07, EC-HUD-08.
- [ ] **T2.8b feat(hud): editor entry points** (S, deps T2.8, T2.5b). `/ksu hud` (reserved word), the "Edit HUD layout" button in General › HUD, and an "Edit position" card action that opens the editor with that element selected. Closing returns to the opener. HUD features in Phases 3, 5 and 6 attach the card action.
  - Req: REQ-HUD-06, REQ-UI-04, REQ-UI-10
  - Accept: AC-HUD-06 (using the gametest element's card); AC-UI-09 (the `/ksu hud` part).

*Stretch, after G2 (optional per D-8 / REQ-UI-18. Nothing for 2.0.0 and no checkpoint waits on these. The multi-select dropdown moved to T6.7.)*

- [ ] **T2.9 feat(ui): open and category-switch animations** (S, stretch, deps T2.4a). The panel opens with a scale of 0.90 → 1.00 over 220 ms; switching category plays a 200 ms slide-and-fade.
  - Req: REQ-UI-18
  - Accept: AC-UI-02 and AC-UI-03 still pass during and after the animations.
- [ ] **T2.9b feat(ui): keyboard focus navigation** (M, stretch, deps T2.4b). Tab/Shift+Tab move focus, Space/Enter activate, and the arrow keys operate focused controls.
  - Req: REQ-UI-18
  - Accept: AC-UI-05 and AC-UI-07 still pass (Esc order per REQ-UI-08 and REQ-UI-22).
- [ ] **T2.9c feat(hud): snapping + guide lines in the HUD editor** (S, stretch, deps T2.8). Edges snap within 4 px to the screen edges, the centre lines and other elements, with 1 px guide lines; holding Alt disables snapping.
  - Req: REQ-HUD-14
  - Accept: AC-HUD-14.
- [ ] **T2.9d feat(ui): anti-aliased rounded corners** (S, stretch, deps T2.2). Uses public render APIs only (no accessor mixin, per REQ-UI-17 and AD-4). Fill-based corners remain the default and the fallback. Skipped if 26.2 offers no public path.
  - Req: REQ-UI-18
  - Accept: screenshot comparison at scales 1–4; AC-UI-02 still passes.

**Checkpoint G2:**
- [ ] tiers A–C green; per-commit worktree build since G1 (AC-XC-05)
- [ ] screenshots reviewed: AC-UI-01, AC-UI-02, AC-UI-16, AC-UI-17, AC-HUD-07, AC-HUD-13
- [ ] tier D boot with a copy of your mod set minus Cloth Config, once also without Mod Menu: AC-UI-09 [D], AC-UI-20 [D], AC-HUD-09 (no test element), EC-UI-13 (the backdrop and clicks still work next to the other GUI mods)
- [ ] 1.0.1 config migrates with no loss (AC-UI-15, AC-CFG-12)
- [ ] `code-reviewer` review, incl. clean room and local-only: AC-UI-21, AC-UI-22, AC-UI-04 [R], AC-HUD-10, AC-XC-01
- [ ] your quick look: AC-UI-23 (compare with AlpakaAddons in your instance; either it "feels similar", or you list the differences to fix)
- [ ] push `update/26.2` (D-10 checkpoint push) after the per-commit build; CI green on the pushed head
- [ ] Phase 2 report (AC-XC-07, AC-XC-10); stretch tasks T2.9–T2.9d are listed as not started or done, never as blocking

### Phase 3 — Features (each behind its own toggle)

*Shared game-state, data and rendering infrastructure comes first (T3.0*), then the NPC/mob data (T3.8*), then the features. Rare Drop Odds (T3.9*) is built last.*

- [ ] **T3.0a feat(skyblock): reader base + tab-widget and sidebar readers** (M, deps T1.9, T1.18). Shared passive-reader base, the tab reader (`getListedOnlinePlayers` / `getTabListDisplayName`, a regex per line, widgets grouped by header, independent of tab order) and the sidebar reader (title plus team prefix/suffix lines, filler characters stripped). It uses T1.9's location change events and no longer owns them.
  - Req: REQ-GS-01, REQ-GS-02, REQ-GS-03, REQ-GS-10, REQ-GS-11, REQ-GS-12, REQ-GS-14, REQ-GS-15, REQ-GS-16, REQ-XC-PRIVACY-01
  - Base rules for every reader:
    - parses only while an enabled consumer subscribes
    - reads game objects on the client thread only and hands out immutable snapshots
    - clears its caches on T1.9 change and disconnect events
    - logs an unknown shape once
    - never writes raw text to disk
    - shares one number parser: separators, Roman and Arabic numerals; `12.4k` is flagged approximate
  - Accept: AC-GS-01 (architecture test over all reader packages), AC-GS-02, AC-GS-03, AC-GS-10, AC-GS-11, AC-GS-14, AC-GS-16 (first bullet). Fixtures come from the G1 dumps, sanitised. Formats the dumps missed stay UNVERIFIED and are listed in the report. The [E] parts of AC-GS-02/-03 and AC-GS-12 are checked at the G3 mineshaft check.
- [ ] **T3.0g feat(skyblock): chat + action-bar listener** (S, deps T3.0a). One read-only `ALLOW_GAME` listener in a phase before Fabric's default, which always returns true, plus `GAME_CANCELED` as a fallback. Lines that other mods cancel or rewrite through Fabric's message events are still seen.
  - Req: REQ-GS-04, REQ-GS-05, REQ-GS-14, REQ-XC-RULES-02
  - Each line is delivered exactly once, with its overlay flag and the original server text. Lines shaped like player chat (a channel prefix or a `name:` sender) never reach server-event parsers. Hiding at packet or mixin level cannot be seen; the README documents this (EC-GS-04).
  - Accept: AC-GS-04 ([C] gametest with one cancelling and one rewriting test listener; the [D]/[E] part at G6), AC-GS-05.
- [ ] **T3.0h feat(skyblock): inventory counter by SkyBlock id** (S, deps T3.0a). Counts the player's own 36 main/hotbar slots plus the offhand, by the SkyBlock id in custom data. Sacks, ender chest, backpacks, storage, open containers and the cursor are never read (D-7). T3.2's key counts use it.
  - Req: REQ-GS-07
  - Accept: AC-GS-07 ([A]; [E] at the G3 mineshaft check).
- [ ] **T3.0c feat(data): shared bundled-data registry (runtime)** (M, deps T1.18). The "internal database" of D-26. One loader serves every bundled table, using the pinned REQ-DATA-02 envelope and `data/index.json` (sha256, row count, schemaVersion). No feature ships its own resource loader.
  - Req: REQ-DATA-01, REQ-DATA-02, REQ-DATA-07, REQ-DATA-08, REQ-DATA-09, REQ-DATA-11, REQ-DATA-13
  - Loading:
    - off-thread at client init; the client thread blocks at most 200 ms, and the time is logged
    - consumers get a "loading" state, then the data
    - one immutable instance per table
    - accepts BOM and CRLF
    - reads only from the mod's own jar (EC-DATA-02, EC-DATA-06)
  - Fail-safe:
    - a bad row is skipped with one WARN
    - a missing table, a bad checksum or an unknown schemaVersion disables only the dependent features, which show "data unavailable"
    - bundled data never crashes the game
  - Table ids, dataVersion and gameVersion are logged once and listed by `/ksu debug data` (production, read-only).
  - Accept: AC-DATA-05 ([A]; the [B] part is in T3.8), AC-DATA-06, AC-DATA-07, AC-DATA-09.
- [ ] **T3.0i build(data): table validator in `./gradlew check`** (M, deps T3.0c). One JSON Schema (draft 2020-12) per table; the schema library is a test-only dependency. It also runs on its own as `./gradlew validateData` for the update skill.
  - Req: REQ-DATA-02, REQ-DATA-03, REQ-DATA-04, REQ-DATA-06, REQ-DATA-12
  - Fails on:
    - a schema violation or duplicate keys
    - a dangling `table:id`
    - an index mismatch (sha256, row count, schemaVersion), an unlisted or missing file, or a duplicate table id
    - a `sources` list, a non-integer `dataVersion` or a licence outside the enum
    - `§`, or prose over 80 characters outside allowlisted fields
  - `./gradlew dataIndex` rewrites only `data/index.json` (sha256, row count, schemaVersion) and never edits a table. Whoever changes a table's content (the author, `sbxpData` or the update skill) bumps its `dataVersion` by exactly 1, then runs `dataIndex`. An optional guard outside `build`, `./gradlew validateData -PbaseRef=<ref>` (used by the skill; adding it to CI needs your OK, A-10), fails when a table that exists in `<ref>` and has changed has a `dataVersion` other than `<ref>`'s value + 1. A table absent from `<ref>` is exempt. The report prints bytes per table, and `build` fails if the jar's compressed data folder exceeds 1 MB.
  - Accept: AC-DATA-02, AC-DATA-04, AC-DATA-10.
- [ ] **T3.0j build(data): licence enforcement for bundled data** (S, deps T3.0i). Adds the REQ-DATA-05 rules to the validator, plus a jar step that copies each upstream's licence text into `META-INF/licenses/`.
  - Req: REQ-DATA-05, REQ-XC-LICENSE-03
  - Fails on:
    - a licence outside the allowlist
    - an MIT or Apache-2.0 table outside `data/thirdparty/<upstream>/`, or from an upstream not approved in §2
    - an upstream with no `THIRD_PARTY_NOTICES.md` entry or no licence text in the jar
    - CC BY-NC-SA material, including NEU `george.json`
    - a source record on a wiki, Fandom or hypixel.net host that is not a page cited by a person (D-16, D-28)
  - Accept: AC-DATA-03, AC-XC-03 (the validator part).
- [ ] **T3.0k feat(data): layered values** (S, deps T3.0c). Generic layering: bundled < live cache < user override. A live layer exists only for a consumer that declares an opt-in feed, and it never replaces or deletes the bundled table. T6.4 (prices) and T6.8 (rate, time and price overrides) use it.
  - Req: REQ-DATA-10, REQ-DATA-01
  - Accept: AC-DATA-08 (reports the winning layer and its timestamp; a failed live layer falls back to bundled silently).
- [ ] **T3.0m feat(skyblock): mineshaft state + shared widget hint** (M, deps T3.0a, T3.0i). Game-state now owns the shaft code and the Frozen Corpses list for T3.2, T3.3 and T3.9. Those features never parse them.
  - Req: REQ-GS-17, REQ-GS-18, REQ-GS-15, REQ-GS-16, REQ-GS-14, REQ-XC-VERIFY-02, REQ-LOC-08, REQ-GS-13
  - Shaft code:
    - `[A-Z]{4}_[12CL]`, read from the sidebar scoreboard data and searched for 15 s after mineshaft entry
    - the 34 display names live in a CC0 registry table, which T3.3's spot keys reference
    - an unknown code is shown raw; no code gives "Unknown shaft"
  - Frozen Corpses:
    - lines `<Type>: NOT LOOTED|LOOTED`; `UNLOOTED` counts as not looted
    - kept in listed order and never read from entities
    - reset on leaving the shaft, server switch and disconnect
    - a `/ksu corpses` hold keeps the state running while both features are OFF
  - One widget-hint translation key, with the command and menu path verified at G1 (Q-MSA-04). T3.2, T5.3 and Phase 6 use it.
  - Dev-only `/ksu debug shaft <code> [Type:STATE…]` simulates a shaft for the T3.2 and T3.3 gametests. It does not exist in production.
  - Accept: AC-GS-15, AC-GS-16 (second bullet), AC-GS-17, AC-GS-18 ([R]; the [E] result recorded at G1). The [E] part of AC-GS-17 is checked at the G3 mineshaft check, and AC-GS-13 [D] (no `/ksu debug shaft` in production) at the G3 tier D boot.
- [ ] **T3.0d feat(skyblock): profile service + per-profile store** (M, deps T3.0a, T3.0g, T1.7). Used by T3.9, T5.2 and Phase 6.
  - Req: REQ-GS-08, REQ-GS-09, REQ-GS-14
  - Profile identity:
    - from the tab `Profile:` widget and from profile chat lines, including cancelled ones (via T3.0g)
    - key = account UUID + normalised name (case-folded, trimmed, glyph removed); id and type attached when seen
    - "unknown" until identified; a change event fires on a profile or account switch
    - the reversed tab name in the Rift is ignored (EC-GS-10)
  - Store:
    - one folder per (account, profile), `config/k8bas_skyblock_utility/profiles/<playerUuid>/<profileKey>/`, with one file per consumer (`odds.json`, `bestiary.json`, `sbxp.json`), each on config-store's atomic facility (REQ-CFG-12, T1.7); archiving moves the whole folder
    - nothing is written while the profile is unknown
    - a known name that comes back with a new profile id archives the old state
  - Accept: AC-GS-08, AC-GS-09.
- [ ] **T3.0e feat(skyblock): read-only menu reader** (M, deps T3.0a, T0.4b). Moves T0.4b's stable-contents check into a reusable reader and switches the armed dump to it; the capture allowlist stays T0.4b's. Consumers (T3.9c, T3.9f, T5.2b, T6.5, T6.5b, T6.5e, T6.5f) register their own title patterns. It never clicks, pages, hovers or opens anything.
  - Req: REQ-GS-06, REQ-GS-13, REQ-GS-12, REQ-GS-15, REQ-GS-14
  - Snapshots:
    - each holds the title and the top-container slots: index, name, server lore from `DataComponents.LORE`, SkyBlock id
    - stable after the content arrives plus 2 quiet ticks; each page or content change is a new snapshot
    - close is signalled, and partial snapshots are never emitted
    - player-inventory slots are excluded
    - snapshots are dropped across a profile switch or a disconnect
  - Accept: AC-GS-06 (lore unchanged with a test tooltip modifier active), AC-GS-12 (60-min disarm on a fake clock), AC-GS-13 (dump output on the G1 captures unchanged).
- [ ] **T3.0b feat(render): world marker toolkit, labels + providers** (M, deps T1.3, T1.5, T1.9, T1.18). Extracts the T1.3 label renderer into the shared toolkit and switches NPC waypoints to it in the same task. The 1.0.1 style stays until T3.4.
  - Req: REQ-MARK-01, REQ-MARK-02, REQ-MARK-03, REQ-MARK-05, REQ-MARK-06, REQ-MARK-07, REQ-MARK-08, REQ-XC-RULES-03, REQ-XC-RULES-05
  - Labels:
    - 1–3 lines, a colour per line, optional background, facing the camera
    - natural size within 10 blocks, constant on-screen size beyond
    - see-through only for fixed-coordinate anchors; the API refuses see-through for entity anchors
    - the distance line is measured from the player, and only for fixed anchors
  - Each feature gets a marker provider gated by its own toggle and island. Markers outside the view are not submitted. All markers are dropped on world change, server switch and disconnect. Labels hide with F1.
  - Accept: AC-MARK-01 (label part), AC-MARK-02, AC-MARK-04, AC-MARK-05, AC-MARK-07.
- [ ] **T3.0n feat(render): beacon beams + rings** (M, deps T3.0b). A beam per marker: opaque marker colour (alpha 0 is drawn opaque), animated like a vanilla beacon, up to build height and clipped there, wider with distance, no beacon block needed. A horizontal ring: centre, radius, colour and alpha, drawn as outline, disc or both. Both are always depth-tested and need no mixin.
  - Req: REQ-MARK-01, REQ-MARK-02, REQ-MARK-04, REQ-MARK-07, REQ-MARK-09
  - Accept: AC-MARK-01 (beam visible above the obstacle), AC-MARK-03, AC-MARK-06 (150 label+beam markers average ≤ 1 ms over 600 frames). AC-MARK-08 is checked at the G3 field check (shaders on/off, next to Skyblocker) and in the tier D boot.

- [ ] **T3.0f build(mixin): optional cosmetic mixin config + hook status** (S, deps T1.10, T2.4a). Creates the one optional config `k8bas_skyblock_utility.mixins.json` (AD-9: `required: false`, `defaultRequire: 0`, package `com.k8bas.skyblockutility.mixin`, `k8bas$` handlers) that T3.5, T3.6 and T3.7 use. Adds a small hook-status registry that feature cards read.
  - Req: REQ-BOB-08, REQ-HOT-13, REQ-DRILL-07
  - A skipped injection logs one WARN and marks its feature "unavailable" on its card, and the game still starts. Only `@WrapOperation` / `@ModifyReturnValue` style hooks; never `@Redirect` / `@Overwrite`.
  - Accept: AC-BOB-07 (missing-target part): a test-only mixin with a missing target still boots, logs one WARN and shows "unavailable" (B). A review grep finds no `@Redirect`, `@Overwrite` or `cancellable = true` (AC-XC-01).

- [ ] **T3.8a docs(data): NPC & mob list audit** (S, deps T1.18). `docs/data-audit-npc-mob.md` with one table per list:
  - entries per island
  - duplicates and near-duplicates
  - invalid fields
  - invisible-by-design mobs
  - uncovered islands
  - gist revision count and dates
  - size in bytes
  - reliability findings for the current fetch
  - Req: REQ-NPCDB-01, REQ-NPCDB-02
  - One recommendation row per list (bundled, D-12), citing the measured revisions, size and failure modes. It records how each near-duplicate is resolved and lists the coverage gaps; v1 adds no new islands (R5).
  - Accept: AC-NPCDB-01.
- [ ] **T3.8 feat(data): bundle NPC & mob databases [brief 3.9]** (M, deps T3.0c, T3.0i, T3.0j, T3.8a, T1.9). Both lists become registry tables (AD-7 envelope), converted from the frozen gists. The gist fetch, any cache of it and the T1.12 entry validation are deleted. The T1.12 timeout helper goes with it; the update check has its own REQ-UPD-09 timeouts (T1.4a).
  - Req: REQ-NPCDB-03, REQ-NPCDB-04, REQ-NPCDB-06, REQ-NPCDB-09, REQ-NPCDB-10, REQ-DATA-01, REQ-DATA-13
  - Schemas and data tests check entry validity: unique ids, islands from T1.9's island list or the ungated allowlist, valid fixed coordinates, a matchText on moving entries.
  - Lookup by id follows aliases, so a rule linked to a DB entry resolves to the entry's current values; T3.4 uses this for waypoints. A missing id keeps the rule's stored values.
  - The gists stay frozen and the code never writes to them. You remove the 4 invisible mobs from the 1.0.x mob gist. T7.4 checks AC-NPCDB-09.
  - Accept: AC-NPCDB-02, AC-NPCDB-03, AC-NPCDB-05, AC-NPCDB-08, AC-DATA-01, AC-DATA-05 ([B]: with the mob table removed, only the mob picker shows "database unavailable" with Add disabled), EC-UI-08 (registry case).
- [ ] **T3.8b data(npcdb): NPC & mob data corrections** (S, deps T3.8). Applies the audit's outcomes; each content change bumps `dataVersion` by exactly 1 (by hand), then `dataIndex`.
  - Req: REQ-NPCDB-07, REQ-NPCDB-08
  - Changes:
    - NPCs standing in the Dungeon Hub get the island "Dungeon Hub"; Trinity, Tomioka and Duncan stay moving on "Catacombs" (D-2)
    - near-duplicates are resolved as the audit records
    - Glacite mobs are also listed under "Glacite Mineshafts"
    - merged or renamed entries keep their id or get an alias
    - Ghost, Fels, Sneaky Creeper and Invisibug are flagged invisible-by-design and kept
  - Accept: AC-NPCDB-06. No `entityType` data is added (R16).
- [ ] **T3.8c feat(ui): invisible-by-design mobs greyed in the picker** (S, deps T3.8b, T2.5c). Flagged mobs stay listed, greyed and not addable, labelled "never highlighted (Hypixel rules)" (R5). The picker marks an entry as already added when a rule's pattern and island match it (EC-NPCDB-02). A rule whose sourceId is gone shows as a custom rule (EC-NPCDB-01).
  - Req: REQ-NPCDB-08, REQ-NPCDB-09, REQ-UI-11, REQ-XC-RULES-04
  - Accept: AC-NPCDB-07 ([C] for Ghost, Fels, Sneaky Creeper and Invisibug).

- [ ] **T3.1 fix(highlight): verify Trinity/Tomioka/Duncan glow [brief 3.1]** (S, deps T1.9, T1.10, T1.11, T1.18). The name-line resolver skips action labels ("CLICK" etc.), empty lines and hidden-name lines, then takes the nearest remaining line. The three NPCs glow inside runs through their own NPC Search rules (R1).
  - Req: REQ-GLOW-09, REQ-GLOW-12, REQ-GLOW-13
  - Accept: AC-GLOW-08 (resolver table, plus the G1 entity dump at Mort or a Dungeon Hub NPC), AC-GLOW-11 (gametest: a player-type stand-in with "Trinity" + "CLICK", island forced to Catacombs; glows in view, not behind a wall), AC-GLOW-12. The rare-room check is opportunistic. If no rare room appears, the Phase 3 report says "not field-verified on the three NPCs".
- [ ] **T3.2 feat(mining): mineshaft entry alert [brief 3.2]** (M, deps T3.0m, T3.0h, T1.9). Posts one local `[KSU]` chat line per shaft entry, per server id and including party summons. It names the shaft, the corpses and the matching keys in your inventory. It uses game-state's mineshaft state (REQ-GS-17), inventory counts (REQ-GS-07) and widget hint (REQ-GS-18). It has no code parser of its own and never reads entities.
  - Req: REQ-MSA-01, REQ-MSA-02, REQ-MSA-03, REQ-MSA-04, REQ-MSA-05, REQ-MSA-06, REQ-MSA-07, REQ-MSA-08, REQ-MSA-09, REQ-MSA-10, REQ-MSA-11, REQ-MSA-12
  - Timing: the alert posts once the list has been stable for 1.5 s. One follow-up if the list grows within 30 s (R7). At 8 s with no list: "corpse list unavailable", plus the shared hint once per launch. Cancelled on leave, server switch or disconnect.
  - Message: order Lapis, Tungsten, Umber, Vanguard; "n in inventory" (36 slots + offhand, D-7); ⚠ + red when short; "(n looted)"; the hover says sacks and storage are not counted. The sack-keys fallback is a build constant set from the G1 sack check (Q-MSA-04).
  - Toggles (Mining): "Mineshaft entry alert" ON, "Show corpse key counts" ON; search keywords mineshaft, corpse, key.
  - Accept: AC-MSA-01, AC-MSA-02, AC-MSA-03, AC-MSA-04, AC-MSA-05, AC-MSA-06, AC-MSA-07, AC-MSA-08, AC-MSA-09, AC-MSA-13, AC-MSA-14 (A/R; fixtures stay UNVERIFIED until the G1 captures replace them). AC-MSA-10, AC-MSA-11 and AC-MSA-12 are checked at G3.
- [ ] **T3.3a feat(data): corpse spot table [brief 3.3]** (M, deps T3.0c). A registry table of possible corpse spots per shaft code (D-5), built from SkyHanni-REPO PR #759 @ `e2c8edb932` plus meowdding-repo @ `42d01278a8`. Normalised to Skyblocker's frame: 29 codes and 129 spots; `_C` codes share one list; 5 codes are marked "no spot data"; each spot records its sources.
  - Req: REQ-CORPSE-01, REQ-CORPSE-09, REQ-CORPSE-10, REQ-XC-LICENSE-03
  - MIT licence files go under `data/thirdparty/<upstream>/`, with THIRD_PARTY_NOTICES entries (hannibal2, meowdding, credit to GrowlingGrizzly). Table-specific validator rules run in `check`.
  - Accept: AC-CORPSE-01, AC-CORPSE-02 (a sanitised fixture of your 144 logged sightings, code and position only), AC-CORPSE-08.
- [ ] **T3.3 feat(mining): possible corpse spots [brief 3.3]** (M, deps T3.3a, T3.0m, T3.0b, T1.9). Draws a text-only "Possible corpse #n" label at every spot of the current shaft code: fixed coordinates, see-through, no box or beam (R7). Adds `/ksu corpses`. Default OFF, with the tooltip "community data, not detected corpses".
  - Req: REQ-CORPSE-02, REQ-CORPSE-03, REQ-CORPSE-04, REQ-CORPSE-05, REQ-CORPSE-06, REQ-CORPSE-07, REQ-CORPSE-08
  - The marker set depends only on the shaft code and on the widget's all-LOOTED state ("Hide spots when all corpses are looted", ON). It never depends on entities and makes no network call. Codes without data get one note per shaft.
  - `/ksu corpses` sorts spots by distance from the player (rounded half-up) and gives the special replies. It works with the markers and the alert OFF, because it subscribes to the mineshaft state (REQ-GS-16). "corpses" is added to the reserved subcommand list (REQ-UI-10).
  - Gametests drive T3.0m's dev-only shaft simulation (`/ksu debug shaft`); there is no second hook.
  - Accept: AC-CORPSE-03, AC-CORPSE-04, AC-CORPSE-05, AC-CORPSE-06, AC-CORPSE-07, AC-CORPSE-09. AC-CORPSE-10 is checked at G3.
- [ ] **T3.4 feat(waypoints): Skyblocker-style NPC waypoints [brief 3.4]** (M, deps T3.0b, T3.0n, T3.8, T1.9). Every enabled fixed NPC rule on the current island gets three parts:
  - a white label: no shadow or plate, centred on x+0.5 / z+0.5, 1.5 blocks above
  - an optional yellow "<d>m" line, measured from the player
  - an opaque beacon beam in its resolved colour

  Moving rules keep glow only.
  - Req: REQ-NPCWP-01, REQ-NPCWP-02, REQ-NPCWP-03, REQ-NPCWP-04, REQ-NPCWP-05, REQ-NPCWP-07, REQ-NPCWP-08, REQ-NPCWP-10, REQ-NPCWP-11, REQ-NPCWP-12
  - Position: the NPC DB entry's current coordinates via sourceId, otherwise the stored ones. Colour (D-15): the rule's `beamColor`, then the island colour (a map with documented defaults), then the global default. The glow colour stays separate.
  - Toggles: module ON, "Show beacon beams" ON, "Show distance" ON; an AMBER tooltip. Reimplemented; no Skyblocker code.
  - Accept: AC-NPCWP-01, AC-NPCWP-03, AC-NPCWP-04, AC-NPCWP-05, AC-NPCWP-06, AC-NPCWP-08, AC-NPCWP-09, AC-NPCWP-10, AC-NPCWP-12, AC-NPCWP-13. AC-NPCWP-11 is checked at G3.
- [ ] **T3.4b feat(waypoints): beam colour settings + migration** (M, deps T3.4, T2.5a, T2.3b). The Waypoints category gets per-island colour swatches (picker and hex), a per-rule beam colour with "Use island colour", and the beam and distance toggles. Changes apply within 1 s.
  - Req: REQ-NPCWP-05, REQ-NPCWP-06, REQ-NPCWP-09
  - Migration step, with the next free number (REQ-CFG-09, R8):
    - a fixed rule whose `color` ≠ `0x0AA351` gets that colour as its beam override
    - fixed rules on the old default green follow the island colour
    - moving rules keep `color` as their glow colour
  - Accept: AC-NPCWP-02, AC-NPCWP-07.
- [ ] **T3.5 feat(fishing): bobber rubber-band fix [brief 3.5]** (S, deps T3.0f, T1.9). Applies only to **your own** hook (R9) and only on SkyBlock (from the location snapshot; an unknown location behaves as vanilla). Two `@WrapOperation` hooks in the T3.0f config.
  - Req: REQ-BOB-01, REQ-BOB-02, REQ-BOB-03, REQ-BOB-04, REQ-BOB-05, REQ-BOB-06, REQ-BOB-07, REQ-BOB-08
  - `FishingHook.onSyncedDataUpdated` skips the client-side attach to Hypixel's timer stand (id + 1). Any other hooked entity stays vanilla. The entity data itself is applied unchanged (REQ-XC-RULES-02).
  - `FishingHook.tick` makes the hook float on lava as it does on water.
  - The toggle "Bobber Fix" (Fishing) is ON, with an AMBER tooltip, and applies from the next cast. The card shows "unavailable" if a hook was skipped. Courtesy credit to SkyOcean; no code copied.
  - Accept: AC-BOB-01, AC-BOB-02, AC-BOB-03, AC-BOB-06, AC-BOB-08. AC-BOB-07 (tier D), AC-BOB-04 and AC-BOB-05 are checked at G3.
- [ ] **T3.6 feat(fishing): hotspot detection + ring [brief 3.6]** (M, deps T3.0b, T3.0n, T3.0f, T1.9). Recognises hotspot stands whose name tag vanilla displays (R2) on every SkyBlock island, then reads the buff line and finds the liquid surface. Draws a depth-tested ring, with an optional fill, in the buff colour.
  - Req: REQ-HOT-01, REQ-HOT-02, REQ-HOT-03, REQ-HOT-04, REQ-HOT-05, REQ-HOT-06, REQ-HOT-12, REQ-HOT-13
  - Radius: the most frequent ring-particle distance, in 0.5-block steps, from a read-only `handleParticleEvent` observer (T3.0f). Capped per island, with a fallback radius otherwise. The buff patterns live in one place, and unknown text is logged once.
  - Lifecycle: 10-tick grace period; a re-sent stand within 4.0 blocks continues the same hotspot; everything clears on a world or server change.
  - Toggles: "Fishing Hotspots" ON, "Highlight ring" ON, "Filled area" ON, plus opacity. The card can show "radius estimation unavailable".
  - Accept: AC-HOT-01, AC-HOT-02, AC-HOT-06, AC-HOT-07 (observer part), AC-HOT-11 (ring and fill). AC-HOT-10 (tier D) and AC-HOT-08 are checked at G3.
- [ ] **T3.6b feat(fishing): "hotspot gone" warning** (M, deps T3.6, T2.3d). Fires once per hotspot, at the end of the grace period, under two conditions (no line-of-sight gate, R2):
  - you fished in it within the last 30 s: your own bobber was in its area, sampled at least every 10 ticks (R9)
  - you were within 40 blocks when its stand was removed
  - Req: REQ-HOT-07, REQ-HOT-08, REQ-HOT-09, REQ-HOT-10, REQ-HOT-11, REQ-HOT-12
  - Channels, all ON: the title "Hotspot gone!" with the buff as subtitle, for at least 2 s; one local sound (volume setting, short vanilla list); a local chat line. Toast OFF (R12, REQ-UI-21). Nothing is sent to the server.
  - Accept: AC-HOT-03, AC-HOT-04, AC-HOT-05, AC-HOT-07 (no sends), AC-HOT-11 (warning and channels). AC-HOT-09 is checked at G3, including the distance at which the stand disappears.
- [ ] **T3.7 feat(mining): drill re-equip fix [brief 3.8]** (S, deps T3.0f, T1.9). A `@ModifyReturnValue` on `ItemInHandRenderer.shouldInstantlyReplaceVisibleItem` skips the hand dip only when all of these hold (D-4 (a)):
  - both stacks are the same SkyBlock drill: same item, same component types, same id, same non-empty uuid, drill-fuel data present
  - the player is on SkyBlock and the toggle is on

  It never forces the dip.
  - Req: REQ-DRILL-01, REQ-DRILL-02, REQ-DRILL-03, REQ-DRILL-04, REQ-DRILL-05, REQ-DRILL-06, REQ-DRILL-07, REQ-DRILL-08
  - Card "Drill re-equip fix" (Mining), ON. Its tooltip names the Skyblocker and NoFrills equivalents. No block-breaking, game-mode or packet code. (B) is excluded and no fuel HUD is built (D-4).
  - Accept: AC-DRILL-01, AC-DRILL-02 (review plus CI grep), AC-DRILL-05, AC-DRILL-06. AC-DRILL-04 (tier D) and AC-DRILL-03 are checked at G3.

*T3.9 Rare Drop Odds [brief 3.7]: split into T3.9a–h and built last in Phase 3. D-3 = A (numeric odds, five cases), R10 = b. Reveal animations and the later candidates listed in SPEC rare-drop-odds are not in v1.*

- [ ] **T3.9a feat(odds): odds math + "1 in N (P%)" format** (S, deps T1.18). Pure Java; every value is computed in code: at least one success, expected attempts, luck percentile, MF/Pet Luck scaling (cap 900, the < 5 % rule), per-corpse chance from per-roll weights over uniform roll counts, RNG-meter boost, slayer base chance, and bosses until guaranteed.
  - Req: REQ-ODDS-06, REQ-ODDS-08
  - Format: thousands separators, 2 significant digits (minimum 0.0001 %), a basis label, "≈" for estimates, "?" for `verified: false` rows.
  - Accept: AC-ODDS-05, AC-ODDS-07.
- [ ] **T3.9g feat(odds): bundled drop tables (two MIT, one CC0)** (M, deps T3.0c, T3.0i, T3.0j). Three registry tables, each with a schema and an index entry:
  - MIT: the dungeon and slayer sections of NEU `constants/rngscore.json` (D-5), with notice files and a THIRD_PARTY_NOTICES entry
  - MIT: SkyOcean's `src/repo/vanguard.jsonc` weights (R6), with notice files and a THIRD_PARTY_NOTICES entry. Its assumed dye weight is replaced by a Frostbitten Dye row with its own `sourceRefs`/`license`, from a page you supply (REQ-ODDS-10)
  - CC0 `odds.supplied` under `data/` (not `thirdparty/`): the Scatha pet chances and the Lapis/Umber/Tungsten per-roll chances. Each row cites a source with `kind: wiki_crosscheck`, `access: user_supplied` (D-16 (a), D-28 (i)) and is `verified: false` unless a non-wiki origin agrees (REQ-ODDS-10). Rows are added only once you supply the pages; until then the table has no row for those items, so they show no number (REQ-ODDS-11). T3.9b and T3.9e add their rows; they create no table.
  - Req: REQ-ODDS-09, REQ-ODDS-10, REQ-XC-LICENSE-03
  - Each row holds the case id, SkyBlock id and display name; a weight plus the table total, or a base chance; roll min/max; `sourceRefs`, `license`, `verified` and `gameVersion`. Placeholder scores are flagged.
  - Accept: AC-ODDS-08 (all three tables; a wiki-only row with `verified: true` and no second non-wiki origin also fails): `check` rejects a row without `sourceRefs` or `license`, any per-corpse % field, SkyOcean's assumed dye weight, and an MIT table without a notice.
- [ ] **T3.9h feat(odds): Rare Drop Odds module** (M, deps T3.9a, T3.9g, T3.0a, T3.0g, T3.0d, T3.0m, T1.9, T2.4a). The "Rare Drop Odds" card in Odds & Trackers: master toggle OFF; five case toggles ON; chat-line toggle ON; rare threshold 2 % (range 0.01–10 %). "Gambling" appears only as a hidden search keyword.
  - Req: REQ-ODDS-01, REQ-ODDS-02, REQ-ODDS-03, REQ-ODDS-04, REQ-ODDS-07, REQ-ODDS-11, REQ-ODDS-12
  - Inputs are read-only, through game-state, and each event gets at most one local line. An unknown item or floor shows no number and is logged once. A missing table disables only its own case and shows a card notice.
  - Per-profile counters use the T3.0d store and are labelled "counted by K8bas since <date>". A "Reset odds counters" button resets the session counters.
  - Accept: AC-ODDS-01, AC-ODDS-02, AC-ODDS-09, AC-ODDS-10.
- [ ] **T3.9b feat(odds): Scatha case** (M, deps T3.9h, T2.8). A Crystal Hollows HUD panel showing:
  - the base and the MF + Pet Luck-adjusted pet chances: any, Rare, Epic, Legendary
  - kills this session and kills since the last pet
  - the chance of at least 1 pet by now, and the expected kills

  Posts one line on your own Scatha PET DROP, then resets the since-last-pet count.
  - Req: REQ-ODDS-05, REQ-ODDS-10, REQ-ODDS-13, REQ-ODDS-14, REQ-ODDS-15, REQ-ODDS-16
  - MF and Pet Luck come from the Stats widget ("tab") or from manual values ("manual"). A kill counts when a Scatha you hit dies within 30 s; it is not identified by maximum health alone. The pet chances are `odds.supplied` rows (T3.9g) from pages you supply (D-16 (a), D-28 (i)), with `verified: false` and "?".
  - Accept: AC-ODDS-04 (Scatha panel), AC-ODDS-11, AC-ODDS-12 (A, plus a gametest with simulated entities and injected chat). The Crystal Hollows check at G3 is opportunistic.
- [ ] **T3.9c feat(odds): Croesus / dungeon reward chest tooltips** (M, deps T3.9h, T3.0e). In end-of-run and Croesus chest screens, each RNG-meter item gets two tooltip lines (R10 = b):
  - "RNG-meter item · rarity rank k of n on <floor> · score S (relative)"
  - below it, "≈ 1 in N runs (P%, base, no bonuses)", with N = score / 300

  No HUD element and no chat line.
  - Req: REQ-ODDS-17, REQ-ODDS-18, REQ-ODDS-19
  - The floor comes from the title or the dungeon context; if neither gives it, no lines are shown. Lines are recomputed when the contents change (reroll). Placeholders are excluded, and the lines appear only in the Dungeon Hub or a dungeon. The chest titles are registered with the menu reader and the capture allowlist.
  - Accept: AC-ODDS-13 (a provisional UNVERIFIED chest fixture until a capture replaces it), AC-ODDS-14. The Croesus check is at G3.
- [ ] **T3.9d feat(odds): Frozen Corpse odds panel + loot parser, Vanguard** (M, deps T3.9h, T3.0m, T3.0g, T2.8). A mineshaft HUD panel per corpse type in the Frozen Corpses state (REQ-GS-17). It shows the N rarest items (default 3, range 1–10) with per-roll and per-corpse chance, plus the corpses looted this session. Falls back to "(widget off)" and never reads entities.
  - Req: REQ-ODDS-05, REQ-ODDS-10, REQ-ODDS-20, REQ-ODDS-21, REQ-ODDS-22
  - A parser for "<TYPE> CORPSE LOOT!" blocks ignores lines other mods insert and drops a block that is cut off for 2 s. It posts one line for the items at or below the threshold.
  - The Frostbitten Dye per-roll chance is the Vanguard table's supplied row (T3.9g), with `verified: false`. Per-corpse values carry the "no extra-roll perk" note.
  - Accept: AC-ODDS-03 (tier D at G3), AC-ODDS-04 (corpse panel), AC-ODDS-06, AC-ODDS-15 (Vanguard), AC-ODDS-16 (parser on all logged blocks).
- [ ] **T3.9e feat(odds): Lapis / Umber / Tungsten corpse case** (S, deps T3.9d). Adds case 4 to the T3.9d panel and parser. Per-roll chances are `odds.supplied` rows (T3.9g) from pages you supply (no MIT source exists), with `verified: false` and "?". Rolls: Lapis 3–6, Umber and Tungsten 4–7. Without the pages, the items show no number (REQ-ODDS-11).
  - Req: REQ-ODDS-10, REQ-ODDS-20, REQ-ODDS-21, REQ-ODDS-22
  - Accept: AC-ODDS-15 (Umber part), AC-ODDS-16 (92 Lapis, 38 Tungsten and 28 Umber blocks). The mineshaft check is at G3.
- [ ] **T3.9f feat(odds): slayer RNG meter case** (M, deps T3.9h, T2.8, T3.0e). A slayer HUD panel, shown during a quest and for 30 s after a meter line. It shows:
  - the selected item, from chat or from the "<slayer> RNG Meter" menu
  - stored and required XP, and the percentage
  - the base and the meter-boosted chance at the highest tier
  - bosses until guaranteed, once a per-boss gain has been seen

  Posts one line on a RARE / VERY RARE / CRAZY RARE DROP of an item in the table.
  - Req: REQ-ODDS-05, REQ-ODDS-23, REQ-ODDS-24
  - Accept: AC-ODDS-04 (slayer panel), AC-ODDS-17. The slayer check is at G3.

**Checkpoint G3:**
- [ ] tiers A–C green
- [ ] `code-reviewer` review:
  - the rules review (AC-XC-01): no packet cancelling, no sends, nothing see-through that comes from an entity
  - the module review items AC-MSA-08, AC-CORPSE-04, AC-NPCWP-12, AC-BOB-06, AC-HOT-07, AC-DRILL-02, AC-ODDS-02
  - licences and notices (AC-XC-03)
- [ ] tier D boot of the jar with a copy of your mod set and the mixin export: AC-BOB-07, AC-HOT-10, AC-DRILL-04, AC-MSA-12, AC-ODDS-03, AC-NPCWP-11, AC-MARK-08 (D); AC-GS-13 [D] for `/ksu debug shaft` (absent from the production jar, REQ-LOC-08). The injection order is recorded.
- [ ] your batched field checks (E, R17):
  - mineshaft: AC-MSA-10 (party summon opportunistic), AC-MSA-11, AC-MSA-12 [E], AC-CORPSE-10, corpse loot lines (AC-ODDS-16); AC-GS-02, AC-GS-03, AC-GS-07 and AC-GS-17 [E] (the reported code, corpse list and key counts equal a `/ksu debug dump tab|sidebar` taken there)
  - waypoints and markers: AC-NPCWP-11 and AC-MARK-08 [E] (Udel, Researcher Timmy and a hotspot ring, shaders on and off, next to Skyblocker waypoints)
  - fishing: AC-BOB-04 (2 min in water), AC-BOB-05 (30 s in lava), AC-HOT-08, AC-HOT-09 including the stand's tracking distance
  - mining: AC-DRILL-03 (50 blocks, with the Skyblocker and NoFrills options OFF)
  - dungeon: one Croesus chest (AC-ODDS-13, plus a chest capture); the rare-room glow (AC-GLOW-11) is opportunistic
  - opportunistic: Scatha (AC-ODDS-12), slayer (AC-ODDS-17), a Vanguard corpse
  - menus: AC-GS-06 [E] (one `/sblevels` page re-captured with the T3.0e dump equals its G1 capture)
  - privacy: AC-GS-12 [E] (after a session of at least 30 min with capture off, Claude checks that the config folder holds no raw chat, tab or sidebar text)
- [ ] you have removed Ghost, Fels, Sneaky Creeper and Invisibug from the 1.0.x mob gist (any time before this, e.g. together with T0.5); Claude checks that the raw URL still parses (AC-NPCDB-09 pre-check)
- [ ] every commit since G2, including fixture commits from the G3 captures, builds in a worktree (AC-XC-05); then push `update/26.2` (D-10 checkpoint push); CI green on the pushed head
- [ ] Phase 3 report:
  - opportunistic criteria not yet seen (§11)
  - UNVERIFIED fixtures (REQ-GS-14)
  - the G1 sack-key result (REQ-MSA-05)
  - the CHANGELOG entry for each feature

### Phase 4 — Auto-updater on GitHub Releases

*T1.4 already ships the release source and selection as v1.1.0's notify-only check (REQ-UPD-01, R4). Phase 4 completes that check and adds the confirmed, staged install (D-14). No Hypixel play is needed in this phase.*

- [ ] **T4.1 feat(update): check outcomes, manual checks and state** (M, deps T1.4, T1.18). Extends T1.4a's `GitHubReleaseSource` and T1.4b's `AssetSelector` into the REQ-UPD-08 outcome model, which the notices, the card and the install path all read.
  - Req: REQ-UPD-04, REQ-UPD-08, REQ-UPD-09, REQ-UPD-19, REQ-CFG-12
  - Hash availability (T4.2) and install eligibility (T4.3) plug in as hooks. Until those tasks land, the hooks answer notify-only.
  - Manual checks have a 60 s cooldown and respect rate-limit waits. Only one check or download runs at a time (the lock is shared with T4.2).
  - The state file on the REQ-CFG-12 facility gains `skippedVersion`, the pending-install record and the warned file names.
  - Accept: AC-UPD-04 manual rows (cooldown message, one check in flight); the manual-check row of AC-UPD-09; AC-UPD-07 second bullet (the manual "no build" text); AC-UPD-10 [A] manual part ("Couldn't reach GitHub" within 25 s); the state-file part of AC-UPD-20; the T1.4a/T1.4b tests re-run unchanged. Tests use recorded API JSON and a local mock server.
- [ ] **T4.2 feat(update): secure download + integrity + jar validation** (M, deps T4.1). Streams the confirmed asset to `.k8bas_update/*.part` (REQ-UPD-09 timeouts: connect 10 s, download 120 s), checks its SHA-256 and reads only its `fabric.mod.json`. It needs a consent token, which T4.4's confirmation issues.
  - Req: REQ-UPD-08, REQ-UPD-09, REQ-UPD-13, REQ-UPD-14, REQ-UPD-15, REQ-UPD-20
  - Hash source: the API `digest`, otherwise the `.sha256` sidecar. If neither exists, or the two disagree, the result goes to the notify-only or conflict row of REQ-UPD-08.
  - The selection → digest → jar-validator chain stays callable from tests for T7.4's draft check (AC-REL-09).
  - Accept: AC-UPD-14, AC-UPD-15, AC-UPD-16, the download-host part of AC-UPD-21; EC-UPD-03.
- [ ] **T4.3 feat(update): install eligibility + staged install via helper** (M, deps T4.2). Decides whether an install may be offered, stages the verified jar outside `mods/`, and has a JDK-only helper swap it in after the game exits.
  - Req: REQ-UPD-16, REQ-UPD-17
  - Each REQ-UPD-16 reason (dev run, origin not a single `.jar`, unwritable folder, non-SemVer version, launcher-managed instance, no SHA-256) is shown by name.
  - Helper: puts the new jar in before taking the old one out, re-checks SHA-256, uses bounded waits and writes `helper.log`. Across drives it uses a temp name (EC-UPD-07).
  - Accept: AC-UPD-17 ([A] tables), AC-UPD-19; helper state-machine tests on a temp folder; EC-UPD-04, EC-UPD-06.
- [ ] **T4.3b feat(update): launch reconciliation** (S, deps T4.3). Runs offline on every launch and finishes, re-arms, disarms or clears the staged update. Replaces the old "clean up stale copies / 1.0.x `deleteOnExit` leftovers" item (R13, R16).
  - Req: REQ-UPD-18
  - Deletes only the jar the updater replaced itself. Any other copy of our mod id is kept and warned about once (a WARN plus a local chat line; the file names are kept in the state file).
  - Two failed applies in a row disarm the update and point the user to a manual install.
  - Accept: unit tests for every REQ-UPD-18 branch; EC-UPD-05, EC-UPD-08, EC-UPD-15; runtime proof in AC-UPD-18 (T4.5).
- [ ] **T4.4 feat(update): notices, confirmation screen and `/ksu update`** (M, deps T4.3, T2.3d, T2.4a). Shows a found candidate and collects the one click that allows a download (D-14).
  - Req: REQ-UPD-10, REQ-UPD-11, REQ-UPD-12, REQ-UI-10, REQ-UI-21
  - A toast through the Phase 2 notice component (R12), plus the once-per-session local chat line. The updater never opens a screen or takes focus on its own.
  - The confirmation screen shows the REQ-UPD-11 fields. **Download & install on restart** issues a token for that version only (→ T4.2 → T4.3).
  - `/ksu update show | check | install | skip | cancel` is a reserved subcommand. Later, Skip (needs a second click) and Cancel pending work as REQ-UPD-12 says.
  - Accept: AC-UPD-11 ([C] screenshots), AC-UPD-12, the [A] part of AC-UPD-13; EC-UPD-10, EC-UPD-11, EC-UPD-13.
- [ ] **T4.4b feat(update): General → Updates card** (S, deps T4.4, T4.3b). A card over the same service, with status, channel and the four buttons of REQ-UPD-10.
  - Req: REQ-UPD-07, REQ-UPD-08, REQ-UPD-10, REQ-UPD-19, REQ-UI-04, REQ-XC-TOGGLE-02, REQ-REL-05
  - Shows T1.4's toggle and T1.4b's channel setting with no new defaults. The card writes `updateChannel` only on an explicit choice (REQ-UPD-07).
  - Shows each manual-check text from T4.1, a skipped candidate marked "skipped", and the pending state from T4.3b.
  - CHANGELOG `[Unreleased]`: the updater behaviour change.
  - Accept: the [C] part of AC-UPD-13, AC-UPD-20 end to end; card screenshots.
- [ ] **T4.5 test(update): Windows integration** (S, deps T4.4b). Runs the whole flow on a copy of the 26.2 instance, using a test build whose source is a local mock (the REQ-UPD-03 override).
  - Req: REQ-UPD-08, REQ-UPD-16, REQ-UPD-17, REQ-UPD-18, REQ-UPD-20
  - Scenarios AC-UPD-18 (a)–(e), plus a Prism-managed instance, a read-only `mods/`, a dev run and an offline start.
  - The update check stays OFF in the Prism test copy outside this run (EC-UPD-14).
  - Accept: AC-UPD-18, the [B]/[D] parts of AC-UPD-17, AC-UPD-10 and AC-UPD-21. No install without a click; exactly one k8bas jar after an applied update; a second copy placed by hand stays (R13).

**Checkpoint G4:**
- [ ] tiers A–D green, including the T4.5 run; per-commit build of every Phase 4 commit (AC-XC-05)
- [ ] `code-reviewer` review
- [ ] `security-auditor` review of REQ-UPD-03 to REQ-UPD-20, with no open High finding (AC-UPD-22)
- [ ] push `update/26.2` (D-10 checkpoint push) after the per-commit build; CI green on the pushed head
- [ ] Phase 4 report (AC-XC-07, AC-XC-10), including the review of the 1.0.x updater: how it checked, downloaded and verified updates, and U1–U20 each with a disposition (REQ-UPD-02, AC-UPD-02)

### Phase 5 — Bestiary Tracker HUD

bestiary-hud owns the family table (T5.1) and the Bestiary menu parser (T5.2b). Phase 6 reuses both and adds no second copy (REQ-BEST-10, REQ-BEST-15). Widget and menu fixtures stay UNVERIFIED until the G1 captures replace them (D-17, all captures at G1). Before T5.2 starts, the G1 widget capture decides between normal mode and the R11 fallback.

- [ ] **T5.1 feat(bestiary): family table + tier math** (M, deps T3.0c). The trimmed NEU `bestiary.json` (pinned commit `24564a1ad2`, 359 families) becomes a registry table under `data/thirdparty/` with its MIT notice [decided D-5]. Tier math gives kills to the next tier, kills to max, and "MAX".
  - Req: REQ-BEST-15, REQ-BEST-03, REQ-BEST-04, REQ-XC-LICENSE-03
  - Row: slug id, display name, island/category, bracket or bracket-set id, cap, max tier count, mob ids. A cap outside the bracket list ends on the cap. CRITTERS families use their own set. Phase 6 references families as `bestiary:<id>`.
  - Accept: AC-BEST-02, AC-BEST-14 (bestiary rule in the `check` validator).
- [ ] **T5.1b feat(bestiary): family name resolver** (XS, deps T5.1, T1.9). Maps names from the widget, menu and chat to table ids, using the name plus the current island or menu category.
  - Req: REQ-BEST-16
  - An unknown or ambiguous name keeps only the server's values (kills, tier, next threshold) and is logged once. Stoneworm stays one family.
  - Accept: AC-BEST-15.
- [ ] **T5.2a feat(bestiary): tab-widget and chat parsers** (S, deps T3.0a, T3.0g). Parses the `Bestiary:` widget lines (kills, tier, next threshold, "MAX") and the BESTIARY tier-up and milestone chat blocks. Blocks that other mods hide are read through the cancelled-message path.
  - Req: REQ-BEST-10, REQ-BEST-05, REQ-BEST-17, REQ-BEST-13
  - Number parsing (separators, k/M/B flagged approximate, Roman/Arabic) uses the shared game-state helper (EC-GS-14), not a copy in the bestiary package. Only server messages are matched.
  - Widget fixtures are provisional and marked UNVERIFIED until the G1 tab dump replaces them.
  - Accept: AC-BEST-09 (widget lines, "12.4k" parsed as approximate, all 378 logged tier-up blocks with 0 failures, Roman and Arabic milestones).
- [ ] **T5.2b feat(bestiary): Bestiary menu parser** (S, deps T3.0e). Registers the "Bestiary ➜ <area>" and "Fishing ➜ <sub>" title patterns with the menu reader. Parses each family item's kills, tier and "Progress to Tier …" lore, plus the milestone when shown. Fully passive: never opens /be, clicks or pages.
  - Req: REQ-BEST-10, REQ-BEST-05, REQ-BEST-13, REQ-BEST-17
  - Bestiary pages are told apart from the leveling "Fishing ➜" menus by their lore. Pages with "Overall Progress" shown or hidden both parse. T6.5b reads the Bestiary menus only through this parser, and T6.5 uses its bestiary-page predicate to skip those pages.
  - Fixtures come from the G1 armed dump of these pages, so the pages must be in the T0.4b allowlist. They stay UNVERIFIED until then.
  - Accept: AC-BEST-09 (menu-lore fixture "Progress to Tier XV: 57.1%").
- [ ] **T5.2 feat(bestiary): live session tracking** (M, deps T5.1b, T5.2a, T5.2b, T1.9, T3.0d). Merges the three sources per family: the newer value wins, and each value keeps its source and age. Counts live kills. Subscribes to the readers only while the toggle is ON.
  - Req: REQ-BEST-02, REQ-BEST-03, REQ-BEST-05, REQ-BEST-10, REQ-BEST-11, REQ-BEST-13, REQ-BEST-17
  - Live counting rules:
    - The first value is the baseline. Deltas of 1–50 count. A jump above 50 or a decrease resyncs without counting. Approximate values are skipped.
    - Baselines reset on a world/server change and on disconnect (T1.9 events); these resets do not end the session. A profile switch (T3.0d) resets the baselines and also ends the session (T5.2c, REQ-BEST-07).
  - The widget's next-tier threshold wins over the bundled one. The mismatch is logged once per family per session.
  - Milestone state comes only from menu or chat (10 tiers per milestone, minus later tier-ups) and is never computed from bundled totals.
  - R11 fallback, only if the G1 capture shows no usable live count: no live counting, rate and ETA show "—", state "live counting unavailable". No mob-death estimate in v1.
  - Accept: AC-BEST-03, AC-BEST-04, AC-BEST-10, AC-BEST-09 (the sequence 12,449 → "12.4k" → 12,452 gives +3), AC-BEST-17 (fallback case only).
- [ ] **T5.2c feat(bestiary): session stats + rate and ETA** (S, deps T5.2). Session kills per family and in total, duration counted only while on SkyBlock, and the session average. Rolling-window rate (W default 5 min, range 1–60) and ETA to the next tier and to max.
  - Req: REQ-BEST-06, REQ-BEST-07
  - A session ends on a manual reset, a profile switch or a restart. An optional "reset on island change" defaults to OFF [decided R11]. The rate shows "—" with under 60 s of data and for a family seen only as an approximate value.
  - Accept: AC-BEST-05, AC-BEST-06.
- [ ] **T5.2d feat(bestiary): per-profile last-known values** (S, deps T5.2, T3.0d). Stores the last exact kills and tier per family, and the milestone state, with timestamps in the per-profile store. After login they show with an age label until a live value replaces them.
  - Req: REQ-BEST-14, REQ-BEST-17
  - An approximate value is never stored as an exact count. Nothing is written while the profile is unknown.
  - Accept: AC-BEST-13, AC-BEST-09 (the stored value stays the last exact count).
- [ ] **T5.2e feat(bestiary): family selection** (XS, deps T5.2). Two modes: auto-follow (default) shows the family that changed most recently, and a pinned list holds up to 5 families. When two families change in the same update, the larger delta wins, then the name. An option hides maxed families.
  - Req: REQ-BEST-09
  - Accept: AC-BEST-08.
- [ ] **T5.3 feat(bestiary): HUD element + Bestiary Tracker card** (M, deps T2.4a, T2.7, T2.8, T5.2c, T5.2d, T5.2e). Adds a "Bestiary Tracker" card in Odds & Trackers (toggle default OFF, plus "Edit position") and a multi-line HUD element. The element is movable, scalable 0.5–3.0 and shows preview data in the editor.
  - Req: REQ-BEST-01, REQ-BEST-02, REQ-BEST-04, REQ-BEST-05, REQ-BEST-06, REQ-BEST-07, REQ-BEST-08, REQ-BEST-09, REQ-BEST-12, REQ-BEST-13, REQ-BEST-17, REQ-GS-18, REQ-HUD-13, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02
  - Lines:
    - Each line type can be shown or hidden. The order is configurable, and tiers show as Roman or Arabic. Defaults follow §12.H [decided R11].
    - The milestone line is drawn only while known. Approximate values and the values derived from them get "≈". "MAX" hides "to next".
  - Options: window W, reset on island change, pins, hide maxed, and a "Reset session" button.
  - With no `Bestiary:` section for 10 s, the shared REQ-GS-18 hint (T3.0m) replaces the live lines. It shows on the HUD only, is never repeated in chat, and nothing is sent. In the R11 fallback, the element shows "live counting unavailable".
  - The element's default position passes the T2.7 no-overlap check.
  - Accept: AC-BEST-01, AC-BEST-07, AC-BEST-11, AC-BEST-17 (display; fallback case only), AC-GS-18 (review: the hint key is referenced, no hard-coded command); preview screenshot.
- [ ] **T5.3b feat(bestiary): compact one-line layout** (XS, deps T5.3). An optional single-line layout of the enabled lines. It is a "may" under REQ-BEST-08: built last, and dropped if T5.3 ran long [decided R16].
  - Req: REQ-BEST-08
  - Accept: compact screenshot [C]; AC-BEST-07 still passes with compact OFF.

**Checkpoint G5:**
- [ ] tests green; every commit since G4 builds in its own worktree (AC-XC-05)
- [ ] review, including the rules review: no screen opened, no click, no command sent, no API call (AC-BEST-12 [R], AC-XC-01)
- [ ] production boot with your mod set: BESTIARY blocks that SkyHanni or Skyblocker hide are still parsed, with no new ERROR (AC-BEST-12 [D])
- [ ] your field check of the bestiary HUD: at least 50 kills of one family; session kills equal the widget's delta; the rate is non-zero; a tier-up updates the tier within 2 s (AC-BEST-16)
- [ ] push `update/26.2` (D-10 checkpoint push) after the per-commit build; CI green on the pushed head
- [ ] Phase 5 report (AC-XC-07): UNVERIFIED fixtures still open (REQ-GS-14), live or fallback mode (R11) and, if in fallback, the estimated-counter question

### Phase 6 — SkyBlock XP Optimizer (addendum)

Ranks every unlocked, not-yet-done SkyBlock XP task by XP per effective hour:
- `effective_h = time_h + coin_cost / coins_per_hour`
- `efficiency = xp / effective_h`

Around that: a Locked section showing what unlocks each task, a greedy "plan to target level" view, per-profile progress from the menus you open, and a patch-update skill. Calculation: AD-13. Data schema: §10 and `docs/sbxp/`. Modules: sbxp-optimizer (REQ-SBXP-*) and sbxp-update-skill (REQ-SKILL-*), decided by D-16–D-29, R14 and R15.
- **Gate:** no T6.1 code is written before T6.0 (your schema re-approval, REQ-SBXP-58).
- **Captures:** every `/sblevels` and component-menu capture is made at the G1 smoke (D-17). Nothing is captured before then. If a menu is missing from the G1 captures, its fixtures stay UNVERIFIED (REQ-GS-14) and I ask for that menu again at G6.
- **Reuses:** T1.9 (on SkyBlock), T2.3c/T2.4a (UI kit), T2.7/T2.8 (HUD), T3.0g (chat/action bar), T3.0c (registry), T3.0k (layered values), T3.0d (profile store), T3.0e (menu reader), T1.4a (back-off helper), T5.1 (bestiary table), T5.2b (Bestiary parser).

- [ ] **T6.0 docs(sbxp): align `tasks.schema.json` with the decisions + your re-approval** (S, deps T1.18). Update `docs/sbxp/tasks.schema.json`, `tasks.example.json` and §10 to the decided rules. Re-run the ajv checks and show you the diff.
  - Req: REQ-SBXP-58, REQ-XC-PLAN-01, REQ-SBXP-01, REQ-SBXP-02, REQ-SBXP-04, REQ-SBXP-06, REQ-DATA-02, REQ-DATA-03, REQ-DATA-06
  - Changes:
    - the envelope exactly as in REQ-DATA-02, with `license` fixed to `CC0-1.0`
    - `neu_repo`/`skyhanni_repo` values only as `table:id` refs into `ref.*` under `data/thirdparty/`
    - an unverified rate or drop chance may rest on one wiki page you supplied; `verified: true` needs 2 independent origins or one in-game capture (a `menu_capture` source, REQ-SBXP-04), and wiki + Fandom count as one
    - a `removed` change status with evidence (REQ-SKILL-04)
    - prose of at most 80 characters outside allowlisted fields
    - no computed fields
    - `ladder` documented as the structural form of one-time (REQ-SBXP-02)
  - Accept: AC-SBXP-48, AC-XC-09.
    - The example validates.
    - New broken variants are rejected: an MIT task table, a bare NEU value, `verified: true` backed only by the wiki, and `verified: true` backed by one non-capture origin. A variant backed by one `menu_capture` passes.
    - Your approval message comes before the first T6.1 commit.
- [ ] **T6.1 feat(sbxp): task model, loader, time and coin math** (M, deps T6.0, T3.0c). New packages `sbxp/model` and `sbxp/calc` (AD-13). Loads tasks and rates through the registry, expands generators, and computes time, coins and effective hours at runtime only.
  - Req: REQ-SBXP-01, REQ-SBXP-02, REQ-SBXP-03, REQ-SBXP-05, REQ-SBXP-06, REQ-SBXP-11, REQ-SBXP-12, REQ-SBXP-13, REQ-SBXP-14, REQ-SBXP-15, REQ-SBXP-16, REQ-DATA-13
  - Effort lines:
    - throughput, drop with pity (E/P50/P90), and fixed minutes
    - `/day` counts as a wall-clock wait only
  - Coins: gross only (D-19), from coin, item and convertible-currency lines plus running cost per hour.
  - Effective hours have a 1-minute floor.
  - Zero time and zero coins puts the task in Free / waiting (D-29). Any missing value means "no estimate".
  - Accept: AC-SBXP-03, AC-SBXP-07, AC-SBXP-08, AC-SBXP-09, AC-SBXP-10, AC-SBXP-11, AC-SBXP-12 (classification), AC-SBXP-13 (classification); EC-SBXP-01, EC-SBXP-12, EC-SBXP-15.
- [ ] **T6.1b feat(sbxp): options, cost classes, currencies, stage lookup, confidence** (M, deps T6.1).
  - Req: REQ-SBXP-17, REQ-SBXP-19, REQ-SBXP-20, REQ-SBXP-21, REQ-SBXP-22
  - Options and classes (R14):
    - Picks the cheapest option at the current coins/h and stage, after "exclude coin-only" has removed the coin-only options.
    - The class is set per option. A task's class follows from its options and never from coins/h.
  - Gems are never converted, and gem-only options are never chosen.
  - Stage lookup goes selected stage → `all` → nearest. A tie goes to the lower stage and forces low confidence.
  - Task confidence = the lowest of its components.
  - The suggested stage comes from your SkyBlock level and the data's `stages` thresholds; `mid` while the level is unknown.
  - Accept: AC-SBXP-14, AC-SBXP-16, AC-SBXP-17, AC-SBXP-18 ([A]), AC-SBXP-19 (lookup + suggestion), AC-SBXP-46 (option choice); EC-SBXP-02.
- [ ] **T6.1c feat(sbxp): XP crediting + override precedence** (S, deps T6.1b). Pure logic; overrides are stored per profile in T6.5c.
  - Req: REQ-SBXP-18, REQ-SBXP-23
  - XP crediting:
    - "best member counts" groups
    - derived XP (bestiary milestones) added to the steps that produce it
    - piecewise capped bands
  - Precedence for time: task time > task rate > rate-id > researched rate.
  - Precedence for coins: task coin > item price > live > bundled price (on top of REQ-DATA-10).
  - An override is flagged "outdated" only when the value it overrides changed or its id was removed. Each override can be reset on its own.
  - Accept: AC-SBXP-15, AC-SBXP-20; EC-SBXP-23.
- [ ] **T6.2 build(sbxp): data tool + skills and collections tables** (M, deps T3.0c). New `tools` source set with `./gradlew sbxpData [--offline]` (AD-12), Java only (D-23).
  - Req: REQ-SBXP-10, REQ-SBXP-01, REQ-SKILL-11, REQ-XC-LICENSE-05
  - Inputs: keyless Hypixel resources (skills, collections, items) and, for T6.2f, the pinned NEU MIT files.
  - Outputs here: `ref.skills` and `ref.collections`, each with its JSON Schema. T6.2e adds the other keyless-API tables, T6.2f the NEU-derived ones.
  - Bestiary data only through `bestiary:<id>` (T5.1); no second table. No script access to any wiki.
  - Recorded fixtures allow an offline replay.
  - Accept: AC-SBXP-06 for these tables. Two offline runs give byte-identical output, an online run equals the replay, and the recomputed maxima match (skills 8,710 · collections 3,160).
- [ ] **T6.2e data(sbxp): museum and minion reference tables** (M, deps T6.2). `ref.museum` (from the items resource: museum `donation_xp`) and `ref.minions` (generators), each with its JSON Schema, through the T6.2 harness.
  - Req: REQ-SBXP-10, REQ-SKILL-11
  - Accept: AC-SBXP-06 for these tables (byte-identical offline runs; maxima museum 3,647 · minions 3,165).
- [ ] **T6.2f data(sbxp): NEU-derived reference tables** (M, deps T6.2). `ref.essence_shops`, `ref.attributes` and `ref.abiphone` from the pinned NEU MIT files, under `data/thirdparty/<upstream>/` with notices and schemas.
  - Req: REQ-SBXP-10, REQ-XC-LICENSE-03, REQ-XC-LICENSE-05
  - Accept: AC-SBXP-06 for these tables (byte-identical offline runs); AC-XC-03 (notice and licence text present).
- [ ] **T6.2b build(sbxp): task-table validator in `./gradlew check`** (M, deps T6.0, T6.2, T6.2e, T6.2f). Implements the code rules of §10 on top of the registry check. Also runs on its own, for the skill.
  - Req: REQ-SBXP-07, REQ-SBXP-04, REQ-SBXP-03, REQ-XC-BUILD-01
  - Rules:
    - every reference resolves: rates, sources, tasks, steps, prereqs, `ref.*`, `bestiary:`
    - ids are unique after expansion; step numbers are contiguous
    - each effort unit matches its rate's numerator
    - no prerequisite cycle
    - the D-16 (a) source rules hold
    - no bare `neu`/`skyhanni` value in the CC0 table
    - each family maximum equals the recomputed one, or a conflict is recorded
    - no `§` or lore text
  - Accept: AC-SBXP-01.
    - One failing fixture per rule; check fails and names the offending id.
    - An unverified rate citing one wiki page you supplied passes; the same rate with `verified: true` fails.
- [ ] **T6.2c build(sbxp): bundled prices (`sbxpData --prices`)** (S, deps T6.2). Builds `sbxp/prices.json` (registry envelope; `dataVersion` = price snapshot version, with its date).
  - Req: REQ-SBXP-40, REQ-SBXP-43, REQ-SKILL-14, REQ-XC-PRIVACY-01
  - Input: a Bazaar `quick_status` snapshot plus one keyless `/v2/skyblock/auctions` scan (~58 MB). The tool asks before the scan (D-20).
  - NBT is decoded in memory. Only rounded per-item aggregates for referenced item ids are written.
  - No UUIDs or listing data are written; no third-party price API is used.
  - Accept: AC-SBXP-35 (forbidden-key scan of the price file and fixtures); AC-SKILL-09 (tool part: no auctions request without approval).
- [ ] **T6.2d feat(tools): update-diff report for the skill** (M, deps T6.1c, T6.2b). A deterministic Java report, so that every number comes from code and never from the model's own summaries (REQ-SKILL-11).
  - Req: REQ-SKILL-04, REQ-SKILL-05, REQ-SKILL-08, REQ-SKILL-11, REQ-SKILL-13
  - Lists added, removed, changed and rename-candidate families, rewards, caps and prereqs, one source per change.
  - Patch-note numbers are recorded as claims only.
  - Affected rate ids are listed as "re-check needed", with the recalculated task times.
  - Writes the changelog draft (old → new, with source).
  - Blocks the run on a licence change or a privacy hit.
  - Accept: AC-SKILL-05, AC-SKILL-06, AC-SKILL-08; EC-SKILL-01, EC-SKILL-06.
- [ ] **T6.3a data(sbxp): task table + research log, batch 1 (top 10 families)** (M, deps T6.1c, T6.2b, T6.2c, T5.1, G1 captures). Batch order follows D-18 (c): the better of the rank by your remaining XP and the rank by fresh-profile XP. Each family is finished before the next one starts. Expect a few minutes of your time per batch to paste pages (R17).
  - Req: REQ-SBXP-03, REQ-SBXP-04, REQ-SBXP-09, REQ-SBXP-19, REQ-SBXP-22, REQ-SBXP-28, REQ-SBXP-45, REQ-SBXP-46, REQ-SBXP-47, REQ-SBXP-48, REQ-SBXP-49, REQ-XC-LICENSE-05, REQ-XC-PRIVACY-01
  - Sources:
    - The table is built fresh from the API, NEU through `ref.*`, your G1 captures and your logs. The wiki-derived draft is not an input (D-25).
    - XP, maxima and structure need a non-wiki source (D-16 a). `maxTotalXp` comes from your capture, otherwise null (D-24).
  - Rates:
    - Each grind is amount + rate, drop + pity + attempts, or fixed minutes with assumptions. Values per stage where they differ.
    - Confidence: ≥ 2 consistent sources = high, one = medium, extrapolated = low.
    - Wiki, forum and reddit pages only when you paste or save them (D-28 i).
  - `docs/sbxp-research.md`:
    - one entry per family and per rate id
    - the coin-only / hybrid family list, with each option's class
  - Every known family is listed. Unknown values are `null` / `verified: false`.
  - Accept: AC-SBXP-01 (the shipped table passes), AC-SBXP-02, AC-SBXP-37 and AC-SBXP-47, for the families done so far.
- [ ] **T6.3b data(sbxp): batch 2 (next 20 families)** (M, deps T6.3a). Same rules as T6.3a.
  - Req: REQ-SBXP-03, REQ-SBXP-04, REQ-SBXP-09, REQ-SBXP-45, REQ-SBXP-46, REQ-SBXP-47, REQ-SBXP-48, REQ-SBXP-49
  - Accept: AC-SBXP-01, AC-SBXP-02, AC-SBXP-37, AC-SBXP-47 on the grown table.
- [ ] **T6.3c data(sbxp): batch 3 (the rest) + coverage report** (M, deps T6.3b). Same rules. Anything unfinished stays `null` / `verified: false`.
  - Req: REQ-SBXP-03, REQ-SBXP-09, REQ-SBXP-19, REQ-SBXP-48, REQ-SBXP-49
  - Accept: AC-SBXP-37 and AC-SBXP-47 in full.
    - A script confirms that every task id resolves to a research-log entry.
    - The report says where research stopped and lists every `verified: false` task and every row without an estimate.
- [ ] **T6.4 feat(sbxp): live Bazaar prices** (S, deps T6.1c, T6.2c, T3.0k, T1.4a). An optional live layer over the bundled prices (D-22).
  - Req: REQ-SBXP-41, REQ-SBXP-42, REQ-SBXP-43
  - Requests:
    - OFF by default; keyless Bazaar endpoint, Bazaar items only
    - at most 1 conditional request per 15 min, only while the screen is open
    - back-off on errors and on 429/503, using T1.4a's back-off helper
  - Disk cache: fresh under 15 min, stale up to 24 h.
  - Fallback is silent; a badge shows live / stale / bundled and the price date.
  - Price mode: instant buy (default) or buy order.
  - Accept: AC-SBXP-34, AC-SBXP-35 (host allowlist test + review); EC-SBXP-03, EC-SBXP-04.
- [ ] **T6.5 feat(sbxp): `/sblevels` menu parser** (M, deps T3.0e, T6.1, T5.2b, G1 captures). Registers the leveling-menu title patterns with the read-only menu reader.
  - Req: REQ-SBXP-33, REQ-SBXP-09, REQ-SBXP-39, REQ-XC-RULES-01, REQ-XC-RULES-02
  - Parses:
    - category totals, family earned/max, step states
    - completion lines, "✖ part (a/b)", your current level and XP
  - Pages are merged by item identity, never by title.
  - A family item with no data entry shows as "unlisted". Unmapped items are collected for the skill.
  - Snapshots that T5.2b's lore predicate marks as bestiary pages are skipped. The G1 bestiary "Fishing ➜" capture is a negative fixture: no "unlisted" entry and no unmapped item.
  - Hint "open <menu> to refresh" for families never seen, or not seen since a `gameVersion` change.
  - Accept: AC-SBXP-28 (your sanitised G1 captures; a recording stub shows no click, command or container packet), AC-SBXP-05 (parser part), AC-SBXP-33 (hint part); EC-SBXP-05, EC-SBXP-06, EC-SBXP-11, EC-SBXP-22.
- [ ] **T6.5b feat(sbxp): component-progress model + Skills and Collections parsers** (M, deps T6.5, T5.2b). The per-component level model (D-27) with tier inference and the manual "set level", plus read-only parsers for Skills and Collections. The Bestiary menu goes through T5.2b's parser (REQ-BEST-10); no second parser.
  - Req: REQ-SBXP-37, REQ-SBXP-27, REQ-XC-RULES-01
  - A tier is inferred from cumulative XP only where the answer is unique. Otherwise it is "unknown", with a manual "set level".
  - The same levels feed the prerequisite checks. Levels with no parsed menu use "set level".
  - Accept: AC-SBXP-31 for Skills and Collections (sanitised G1 captures); the recording stub shows no outgoing packet.
- [ ] **T6.5e feat(sbxp): Museum and HOTM/HOTF parsers** (M, deps T6.5b). Read-only parsers on the T6.5b model.
  - Req: REQ-SBXP-37, REQ-XC-RULES-01
  - Accept: AC-SBXP-31 for Museum, HOTM and HOTF (sanitised G1 captures; a menu missing from G1 stays UNVERIFIED); the recording stub shows no outgoing packet.
- [ ] **T6.5f feat(sbxp): Pets and Accessory Bag parsers** (M, deps T6.5b). Read-only parsers on the T6.5b model.
  - Req: REQ-SBXP-37, REQ-XC-RULES-01
  - Accept: AC-SBXP-31 for Pets and Accessory Bag (sanitised G1 captures); the recording stub shows no outgoing packet.
- [ ] **T6.5c feat(sbxp): per-profile optimizer state** (M, deps T3.0d, T6.1c, T6.5b). Stores `profiles/<playerUuid>/<profileKey>/sbxp.json` through T3.0d.
  - Req: REQ-SBXP-34, REQ-SBXP-35, REQ-SBXP-36, REQ-SBXP-08, REQ-SBXP-22, REQ-SBXP-23, REQ-SBXP-27
  - Contents:
    - progress and component levels
    - marks and check-offs
    - stage (labelled "suggested" until you pick one) and optional coins/h
    - overrides, each with its `dataVersion`
    - unmapped labels
  - Profiles:
    - Nothing is written until the profile is identified.
    - A new profile's state loads within 5 s.
    - If the Profile widget is missing, the shared hint is shown (REQ-GS-18).
  - Precedence: newer snapshot > earlier mark > live delta. A contradicting snapshot clears the mark and shows one conflict note.
  - Aliases carry state across renames. State of removed tasks is hidden, never deleted.
  - Accept: AC-SBXP-29 ([A]), AC-SBXP-30, AC-SBXP-04, AC-SBXP-19 (the "suggested" label); EC-SBXP-07, EC-SBXP-08, EC-SBXP-10, EC-SBXP-13, EC-SBXP-14, EC-SBXP-25.
- [ ] **T6.5d feat(sbxp): live progress deltas** (S, deps T3.0g, T6.5c). Updates progress provisionally between snapshots (R14, default ON). Sources: the action-bar line `+N SkyBlock XP (<label>) (x/100)` and chat reward lines, including messages other mods cancel or rewrite via Fabric's message events.
  - Req: REQ-SBXP-38, REQ-SBXP-39
  - A gain inside the 3 s window counts once. A snapshot replaces the provisional deltas.
  - An unknown label is stored once per profile, with a count.
  - Accept: AC-SBXP-32 ([A] on the 702 sanitised log events; [D] at G6), AC-SBXP-33 (label part); EC-SBXP-24.
- [ ] **T6.6 feat(sbxp): ranking, views + prerequisite resolver** (M, deps T6.4, T6.5c, T6.5e, T6.5f).
  - Req: REQ-SBXP-24, REQ-SBXP-25, REQ-SBXP-26, REQ-SBXP-27, REQ-SBXP-28, REQ-SBXP-29, REQ-SBXP-30, REQ-SBXP-09, REQ-SBXP-15, REQ-SBXP-16
  - Views: Ranked · Locked · Not yet estimated · Free / waiting. One row per instance's next undone step (D-21).
  - Ranked is sorted by efficiency, ties by id.
  - Prerequisites:
    - Each is met, unmet or unknown.
    - "?" badge on tasks whose only open prerequisites are unknown; the setting "treat unknown as locked" moves them to Locked.
    - Locked shows the producing step and the chain efficiency, or "n/a".
  - Event tasks show "next window in X"; mayor-gated tasks show "wait unknown".
  - Header: "ranking covers X of Y remaining XP" (kept, R16).
  - Recalculation runs off the render thread, at most 50 ms on it, on every input change.
  - Accept: AC-SBXP-21, AC-SBXP-22, AC-SBXP-23, AC-SBXP-24, AC-SBXP-05 (view), AC-SBXP-12 (view), AC-SBXP-25 ([B]: ≤ 50 ms median over 20 warm runs); EC-SBXP-21.
- [ ] **T6.6b feat(sbxp): plan to target level** (S, deps T6.6). A greedy planner that runs off the render thread.
  - Req: REQ-SBXP-31, REQ-SBXP-32
  - Picking:
    - Picks the most efficient eligible row and applies it: marks it done, unlocks dependents, advances ladders. Then recomputes.
    - Free / waiting tasks come first. Active filters apply.
  - Totals: play time, coins, effective hours, wall-clock waits, and the tasks left out for missing estimates.
  - Edge cases: "already reached", "short by N XP", a target above the max (D-24), an unknown current level.
  - Accept: AC-SBXP-26, AC-SBXP-27, AC-SBXP-12 (planner), AC-SBXP-13 (left-out total), AC-SBXP-46 (planner); EC-SBXP-16.
- [ ] **T6.6c test(sbxp): golden rankings + recalculation** (S, deps T6.6b, T6.3c, T6.5e, T6.5f).
  - Req: REQ-SBXP-24, REQ-SBXP-27, REQ-SBXP-30, REQ-SBXP-31, REQ-SBXP-33, REQ-SBXP-37
  - `docs/sbxp/golden-fresh.md` is hand-computed, not produced by the code under test.
  - The progressed golden list is built from the detector's output on your G1 captures.
  - Accept: AC-SBXP-43, AC-SBXP-44, AC-SBXP-45 ([A]); your reviews happen at G6 (R14).
- [ ] **T6.7 feat(ui): optimizer table + inputs** (M, deps T2.3c, T2.4a). Owns the multi-select widget that was moved here from T2.9 (R16).
  - Req: REQ-SBXP-50, REQ-SBXP-51, REQ-SBXP-54, REQ-UI-20
  - Table:
    - sortable virtual table with the 9 columns
    - stable sort, toggling desc/asc, task id as tie-breaker
    - low-priority columns hidden on narrow windows, their values shown in the row tooltip
  - `NumberField`:
    - accepts `1,000,000`, `1.5m`, `500k`, `2b`
    - rejects 0, negative or non-numeric input and keeps the last valid value
    - presets 1M / 5M / 15M / 40M / custom
  - Also: multi-select filter, view switcher, and a case-insensitive search over name, category and id.
  - Accept: AC-SBXP-38 ([A]), AC-SBXP-39 (sorting, search, 3,000-row scroll; screenshots at 854×480 GUI 1–2 and 1920×1080 GUI 1–4); EC-SBXP-18.
- [ ] **T6.8 feat(sbxp): optimizer screen + "SkyBlock XP" config category** (M, deps T6.4, T6.6b, T6.7). A full-screen view, because the 9 columns don't fit beside the 160 px sidebar. It opens via `/ksu sbxp` (reserved word), the card button, or a keybind (unbound by default, R16).
  - Req: REQ-SBXP-30, REQ-SBXP-44, REQ-SBXP-50, REQ-SBXP-51, REQ-SBXP-52, REQ-SBXP-54, REQ-SBXP-56, REQ-UI-04, REQ-XC-TOGGLE-01, REQ-XC-TOGGLE-02
  - Screen:
    - all 5 views
    - filters: category, coin-only, include locked, treat unknown as locked
    - text badge + colour for low confidence, unverified XP and outdated overrides
    - the non-affiliation line wherever prices are shown
    - selection and scroll kept by task id
  - Defaults:
    - module, passive reading and live deltas ON
    - HUD and live Bazaar OFF
    - coins/h 5,000,000 (global, optional per-profile value)
    - stage per profile; coin-only included, Locked shown
  - Accept: AC-SBXP-41, AC-SBXP-25 ([C]), AC-SBXP-36 (badges, non-affiliation line), AC-SBXP-38 ([C]), AC-SBXP-39 (views, screenshots), AC-SBXP-18 ([C]); EC-SBXP-17, EC-SBXP-20.
- [ ] **T6.8b feat(sbxp): row details + editors** (M, deps T6.8). Opens from the screen and from the config card's override button.
  - Req: REQ-SBXP-53, REQ-SBXP-23, REQ-SBXP-35, REQ-SBXP-27, REQ-SBXP-37
  - Details:
    - the chosen option
    - effort lines, with assumptions and sources
    - E / P50 / P90 for drops
    - cost lines, with price source and date
    - unmet prerequisites; the task's class and each option's class
    - gem-only options noted as not ranked
  - Editors:
    - time / rate / coin, rate-id and item-price overrides, each with "reset"
    - mark done / not done, "set level", and item / island / quest check-off
  - Accept: AC-SBXP-36 (row details: chosen option, E / P90, sources), AC-SBXP-20 (UI: reset, outdated marker); edits persist per profile.
- [ ] **T6.9 feat(hud): "next best task" HUD element** (S, deps T2.7, T2.8, T6.6). Shows the top ranked task with its XP/h and confidence. Own toggle, default OFF; movable and scalable in the HUD editor.
  - Req: REQ-SBXP-55, REQ-HUD-13
  - Visibility:
    - only on SkyBlock (T1.9) once a profile is identified
    - follows the T2.7 hide rules (R12)
  - Shows "no estimate yet" when nothing is ranked.
  - Its default position is checked for overlap with the other shipped HUD elements.
  - Accept: AC-SBXP-40 ([C]; [E] at G6), AC-HUD-12 (re-run); EC-SBXP-19.
- [ ] **T6.10 feat(skill): `.claude/skills/update-sbxp-table/`** (M, deps T6.2d, T6.3a). Built with skill-creator (D-23). The Agent Skills `SKILL.md` has `name` = the directory name, a description and a body under 500 lines. It adds `disable-model-invocation: true`, so it runs only via `/update-sbxp-table`.
  - Req: REQ-SKILL-01, REQ-SKILL-02, REQ-SKILL-03, REQ-SKILL-04, REQ-SKILL-05, REQ-SKILL-06, REQ-SKILL-07, REQ-SKILL-08, REQ-SKILL-09, REQ-SKILL-10, REQ-SKILL-12, REQ-SKILL-13, REQ-SKILL-14, REQ-SKILL-15, REQ-XC-GIT-02
  - Steps:
    1. Check the preconditions. Ask for the update post and patch notes, **pasted or saved to a file**; never fetch hypixel.net. Version not newer and no upstream change → "up to date". No notes → data-source diffs only, labelled "no patch notes supplied" (R15).
    2. Bump the NEU pin and run `sbxpData` + the T6.2d diff. Removed tasks are kept and marked; renames become aliases only after you confirm. `--prices` asks before the AH scan.
    3. For changed families with no API/NEU value, ask for a `/sblevels` capture; without one, `null` / `verified: false`.
    4. Re-check the affected rates (REQ-SBXP-45–47, D-28 i).
    5. Update the JSON (`dataVersion` +1) and the research log, run `dataIndex`, then validate (`validateData -PbaseRef=<the commit the data branch starts from>`, which is `main` only after 2.0.0 has merged), run the tests and `./gradlew build`. Any failure stops the run.
    6. Write the CHANGELOG entry: old → new value, with source.
    7. **Gate 1** before committing on `data/sbxp-<gameVersion>` (`-d<dataVersion>` added only if that branch exists). **Gate 2** before push + PR. Never `main`, never force-push or merge.
  - `--dry-run` runs every step in a temporary detached worktree outside the repo, then deletes it. The repository stays unchanged.
  - Accept: AC-SKILL-01, AC-SKILL-03, AC-SKILL-04, AC-SKILL-09; EC-SKILL-04, EC-SKILL-05, EC-SKILL-07, EC-SKILL-08, EC-SKILL-09, EC-SKILL-10.
- [ ] **T6.10b test(skill): recorded fixtures, dry run, eval loop, one real run** (S, deps T6.10).
  - Req: REQ-SKILL-01, REQ-SKILL-10, REQ-SKILL-11, REQ-SKILL-13
  - Fixtures (CC0):
    - one added, one changed, one removed and one renamed family
    - a nerf to a `rate.X` used by tasks A and B
  - Runs skill-creator's eval loop in a local venv (D-23).
  - Accept: AC-SKILL-02 (exact report; afterwards: clean `git status`, no new `data/sbxp-*` branch, no extra worktree), AC-SKILL-07 (one real local run with your OK, on a branch cut from `update/26.2`. Until T7.3 has merged, gate 2 is answered "no", so no data PR targets `main` with unreleased 2.0.0 code; the push and PR follow after the merge, at G7, with the branch unchanged).
- [ ] **T6.11 docs(sbxp): README, CHANGELOG, THIRD_PARTY_NOTICES** (S, deps T6.8b, T6.9, T6.10b).
  - Req: REQ-SBXP-57, REQ-REL-01, REQ-REL-05, REQ-XC-LICENSE-03
  - README optimizer section: how to open it, settings and defaults, views and planner, data sources and licences, the non-affiliation statement, its limitations, and a maintainer how-to for `/update-sbxp-table`.
  - A CHANGELOG `[Unreleased]` entry.
  - Notices for every `ref.*` MIT table.
  - Accept: AC-SBXP-42, AC-XC-03 (notices).

**Checkpoint G6 (your "done when"):**
- [ ] ranks correctly: AC-SBXP-43 and AC-SBXP-44 green, plus your review of `golden-fresh.md` and of the top 20 in game (R14)
- [ ] recalculates on coins/h and stage changes: AC-SBXP-45; AC-SBXP-25 [B] on the dev machine
- [ ] skill dry run end to end: AC-SKILL-02; the one real local run (AC-SKILL-07) only with your OK, stopping at gate 2 (no push or PR before the 2.0.0 merge)
- [ ] README + CHANGELOG: AC-SBXP-42
- [ ] tier D boot with your mod set: listener phase order recorded, and action-bar XP events seen with Skyblocker and SkyHanni active (AC-SBXP-32 [D], AC-GS-04 [D])
- [ ] your in-game look:
  - separate progress per profile (AC-SBXP-29 [E]), with the profile identified while Skyblocker's chat rules hide profile lines (AC-GS-04 [E])
  - the HUD (AC-SBXP-40 [E])
  - any D-17 menu missing from the G1 captures, captured now
- [ ] tiers A–C green; privacy scan (AC-XC-08)
- [ ] review: `code-reviewer`, plus a rules review of the readers and the network code (AC-XC-01, AC-SBXP-28 [R], AC-SBXP-35 [R])
- [ ] every commit since G5, including fixture commits from the G6 captures, builds in a worktree (AC-XC-05); then push `update/26.2` (D-10 checkpoint push); CI green on the pushed head
- [ ] Phase 6 report (REQ-XC-REPORT-01, AC-XC-07, AC-XC-10):
  - where research stopped
  - every `verified: false` task and every row without an estimate (REQ-SBXP-49)
  - the coin-only / hybrid family list, with each option's class (REQ-SBXP-19, AC-SBXP-47)
  - the UNVERIFIED fixtures still open

### Phase 7 — Repository & release

Ships 2.0.0 the same way v1.1.0 shipped after G1 (phase 1): PR → your approval → merge → your separate "ship" → tag → draft → checks → publish. Order: T7.1 → T7.1b → T7.2 → T7.2b → T7.2c → T7.3 → T7.4 → T7.4b.

- [ ] **T7.1 docs(readme): README for 2.0.0** (M, deps phase 6). Rewrites `README.md` for 26.2 and adds the optimizer section T6.11 drafted (REQ-SBXP-57). Replaces the stale "Modrinth-based self-updater" text.
  - Req: REQ-REL-01, REQ-REL-02, REQ-REL-03, REQ-REL-04, REQ-XC-TOGGLE-02, REQ-XC-RULES-07, REQ-GLOW-15, REQ-CORPSE-09
  - Supported version "Minecraft 26.2 (Fabric)": Java 25, minimum Loader, Fabric API, Hypixel Mod API; 26.1.x unsupported, 1.0.1 was the last 26.1 build.
  - Feature table (name, one line, category, default per §12.H; Rare Drop Odds names the Croesus "≈ 1 in N runs (P%, base, no bonuses)" line, R10 = b). Every user-facing command. One placeholder per visible feature, as a committed image or text marker, so nothing renders as a broken image.
  - Compliance (P1–P7 in plain words, the excluded behaviours, the EC-GS-04 limitation), the non-affiliation sentence, network access (GitHub hosts per REQ-UPD-20, plus `api.hypixel.net` only when opted in, each with its toggle), licensing (CC0; MIT data → THIRD_PARTY_NOTICES; Render Chest Apache-2.0; credits).
  - Upgrade note: 1.0.x and 1.1.0 users install by hand once; settings migrate with a backup; the six behaviour changes; updates need one click, and some cases are notify-only.
  - Accept: AC-REL-01, AC-REL-02, AC-REL-03 (README half), AC-XC-04 (README defaults = code defaults), AC-GLOW-14 (README part).
- [ ] **T7.1b docs(changelog): 2.0.0 section + THIRD_PARTY_NOTICES final pass** (S, deps T7.1). Turns `[Unreleased]` into a self-contained `## [2.0.0] - YYYY-MM-DD` section that works word for word as the release notes. Checks the notices against the bundled tables.
  - Req: REQ-REL-05, REQ-REL-04, REQ-REL-03, REQ-XC-LICENSE-03, REQ-CORPSE-09, REQ-DATA-05
  - The section holds requirements, the upgrade note, behaviour changes marked as such, every default (P6), the optimizer and the SBXP data tables. It uses only the standard subsections. It follows the `[1.1.0]` section from phase 1 (T1.14 started the file).
  - Accept: AC-REL-04 (T1.17's lint and extraction on `[2.0.0]`; re-run inside `verifyReleaseDraft`, T7.2b), AC-REL-03 (release-body half), AC-XC-03, AC-CORPSE-08 ([R] part).
- [ ] **T7.2 chore(release): version 2.0.0** (S, deps T7.1b). Gradle mod version `2.0.0` (D-9). This gives `fabric.mod.json` `2.0.0+26.2` and the jar `k8bas_skyblock_utility-2.0.0+26.2.jar`; the `+<mc>` naming itself landed with v1.1.0 (phase 1). The `fabric.mod.json` description gets a final check: no "Modrinth", no P7 word.
  - Req: REQ-REL-06, REQ-REL-07, REQ-REL-02
  - Accept: AC-REL-05 (jar matches the REQ-REL-07 pattern; `version` `2.0.0+26.2`, `depends.minecraft` `~26.2`; CHANGELOG heading `2.0.0`); AC-REL-01 (`fabric.mod.json` part).
- [ ] **T7.2b build(release): pre-publish checks through the updater's own code** (M, deps T4.1, T4.2, T7.1b). Adds a `verifyReleaseDraft` task in a source set that does not ship. It reads the release JSON that `gh api` saved, so Gradle never holds a token. T7.4 runs every draft check as one command.
  - Req: REQ-REL-07, REQ-REL-11, REQ-REL-05, REQ-UPD-03, REQ-DATA-05, REQ-XC-PRIVACY-01
  - Release checks: `AssetSelector` picks the jar; it is downloaded through the T4.2 hash and jar validator; API `digest` = local SHA-256 = sidecar; only jar + sidecar, both `state: uploaded`; the pre-release flag is set only if the version has a pre-release part; body = extracted CHANGELOG section.
  - Jar checks: no `modrinth` host string; the test-source override is inert in production; one licence file per upstream under `META-INF/licenses/`.
  - Reuses the v1.1.0 tooling: T1.15's BOM-free LF sidecar writer (EC-REL-06), T1.17's CHANGELOG lint + section extraction, and T1.17's range and PR-body privacy scan.
  - Accept: AC-REL-09 ([A] half: recorded draft-JSON fixture passes; a BOM/CRLF sidecar, a digest mismatch, a `state: open` asset, a second `.jar` and a wrong pre-release flag each fail), AC-REL-04 ([A] part), AC-UPD-03 (release-jar half), AC-DATA-03 ([R] jar part).
- [ ] **T7.2c docs(release): extend `RELEASING.md` (from T1.17) for 2.0.0** (S, deps T7.2b). The maintainer procedure, so later releases (including data-only ones) keep the updater working.
  - Req: REQ-REL-16, REQ-REL-06, REQ-REL-13
  - Covers: approvals (PR approval = merge only; a separate "ship"), draft → `verifyReleaseDraft` → publish, the REQ-REL-07 contract, the BOM/CRLF sidecar pitfall (PowerShell 5.1 `Out-File`), CHANGELOG extraction, `+` → `%2B` (EC-REL-08), "Latest" set explicitly (EC-REL-11).
  - Also covers PATCH releases for data-only and SBXP data PRs (EC-REL-15). Once releases are immutable, a defect means a new PATCH, never a replaced asset or a moved tag (EC-REL-10).
  - Accept: AC-REL-14 (the T1.17 dry-run procedure re-run for 2.0.0: no tag pushed; creating and deleting the throwaway draft each need your OK).
- [ ] **T7.3 chore(release): pull request `update/26.2` → `main`** (S, deps T7.1, T7.1b, T7.2, T7.2b, T7.2c, phase 6). Pushing the T7.1–T7.2c commits and opening the PR each need your OK; both come after the `code-reviewer` pass on T7.1–T7.2c. **Waits for your approval**, then merges with a merge commit (D-10).
  - Req: REQ-REL-08, REQ-REL-09, REQ-XC-GIT-01, REQ-XC-GIT-02, REQ-XC-BUILD-01, REQ-XC-PRIVACY-01
  - Body:
    - changes since v1.0.1 (`bc0f2f6`) per phase, with v1.1.0 items marked
    - evidence per tier A–E and field-check pass/fail
    - known issues, including open tier E checks (EC-REL-13: release only if you accept)
    - decisions D-1–D-29 and R1–R17, plus later changes
  - Before opening: per-commit worktree build since G6 green; `git log --format=%ae` shows only noreply addresses; the T7.2b privacy scan is clean; CI is green on the head.
  - Approval: your session message or your approving PR comment. Agent or workflow messages never count. A later commit voids it (EC-REL-01, EC-REL-12). If `main` moved, merge `main` in; no rebase or force-push (EC-REL-02).
  - Accept: AC-REL-06, AC-REL-07, AC-XC-05, AC-XC-12.
- [ ] **T7.4 chore(release): tag and publish v2.0.0 after your "ship"** (S, deps T7.3). Starts only after the merge **and** your separate "ship" message (R4). Stops and reports on any failure; a draft is never published as is.
  - Req: REQ-REL-10, REQ-REL-11, REQ-REL-07, REQ-REL-13, REQ-XC-GIT-02, REQ-NPCDB-10, REQ-HUD-10
  - Steps:
    1. Preflight: `gh` is authenticated with rights (EC-REL-04). `v2.0.0` exists neither locally nor on the remote; if it does, stop and ask (EC-REL-03). CHANGELOG has `[2.0.0]` (EC-REL-07).
    2. Annotated tag `v2.0.0` on the merge commit. `./gradlew clean build` from that commit with a clean tree; the build writes the sidecar (T1.15).
    3. Push the tag. `gh release create v2.0.0 --verify-tag --draft --title "K8bas Skyblock Utility v2.0.0" --notes-file <extracted section>` with the jar + `.sha256`.
    4. Draft checks:
       - `verifyReleaseDraft` on the authenticated `gh api` JSON
       - tier D boot of the exact jar with a copy of your mod set: no new ERROR lines against the G6 baseline, and the HUD editor lists only shipped elements
       - both gists still return a parseable array without the four invisible mobs

       Fix only inside the draft (`--clobber` on drafts only, EC-REL-05), then rerun all checks.
    5. With your OK, enable immutable releases via `gh api` before publishing.
    6. Publish (draft → public, `--latest` explicit, pre-release false), then run `gh release verify v2.0.0`. A wrong flag is fixed without touching the assets (EC-REL-09).
  - Accept: AC-REL-05, AC-REL-08, AC-REL-09 ([D] half), AC-REL-11, AC-NPCDB-09, AC-HUD-09.
- [ ] **T7.4b test(release): end-to-end update from a never-published test build** (S, deps T7.4). Uses a local `2.0.0-test.1+26.2`, built with a version override and never committed or published. The update check is ON for this test only (EC-UPD-14). No Hypixel login needed.
  - Req: REQ-REL-12
  - Accept: AC-REL-10 (toast + chat line show v2.0.0; after confirm and quit, the next launch shows "Updated to v2.0.0"; exactly one k8bas jar in `mods/`, with the release digest). Tier D (Claude, copy of the test instance) or E (you).

**Checkpoint G7:**
- [ ] `code-reviewer` review of T7.1–T7.2c, done before T7.3 opens the PR
- [ ] release verified end to end: the T7.4 draft checks plus T7.4b (AC-REL-08 to AC-REL-11)
- [ ] AC-SKILL-07 PR part, after the merge and your gate-2 OK: the G6 data branch is pushed unchanged (its base is in `update/26.2`, now an ancestor of `main`), so its PR to `main` holds only the data commit. If it conflicts with `main` (e.g. in the CHANGELOG), the skill is re-run from `main` instead of merging
- [ ] Phase 7 report (REQ-REL-17, REQ-XC-REPORT-01, REQ-XC-SKILLS-01): release URL, asset digests, the end-to-end result, any flag fixes and open items, and the skills used (`shipping-and-launch`, `git-workflow-and-versioning`) (AC-REL-15, AC-XC-07, AC-XC-10)

### Not built in 2.0.0

**Requirements deferred by a decision.** These have no task on purpose, so they are not gaps. Where one has an AC, that AC checks that the feature is absent.

- **REQ-DRILL-09**: Not built in 2.0.0: D-4 chose (a) only, so there is no Drill Fuel HUD. AC-DRILL-06 (in T3.7) checks it is absent.
- **REQ-DRILL-10**: Not built in 2.0.0: D-4 (c) not chosen, so there is no fuel-lore parser.
- **REQ-DRILL-11**: Not built in 2.0.0: D-4 (c) not chosen, so no drill HUD element is registered.
- **REQ-DRILL-12**: Not built in 2.0.0: D-4 (c) not chosen, so there is no low-fuel warning.
- **REQ-ODDS-25**: Not in v1: D-3 = A (numeric odds only); reveal animations are left to SkyCase or a later follow-up.
- **REQ-ODDS-26**: Not in v1: D-3 = A; it applies only if a later follow-up adds reveal animations (AC-ODDS-18 is not applicable).

**Optional stretch requirements.** Tasks exist (T2.9–T2.9d), but 2.0.0 does not need them and no checkpoint waits for them.

- **REQ-UI-18**: Optional stretch goals after G2 [decided D-8, Q-UI-04]: open/tab animations, keyboard focus navigation, anti-aliased corners. Planned as stretch tasks T2.9, T2.9b and T2.9d, but not required for 2.0.0, and no AC or checkpoint waits for them.
- **REQ-HUD-14**: An optional 'may' (HUD editor snapping and guide lines) [decided D-8], not required for 2.0.0. Planned as stretch task T2.9c; AC-HUD-14 applies only if it is built.

**Partial "may" items and module out-of-scope lines.** Listed so that nothing is dropped silently.

- npc-mob-data out of scope: entity types for mob entries: R16 drops the T3.8 entityType field. No data is filled in 2.0.0, and the decision is revisited only if the glow audit shows false matches.
- npc-mob-data out of scope: new NPC/mob coverage for Rift, Backwater Bayou, Farming Islands, Galatea, Glacite Mineshaft NPCs: R5: no new island coverage in v1. The gaps are listed in the T3.8a audit.
- REQ-NPCWP-08 (optional per-rule beam on/off only): A 'may' item that is not planned. The global beam toggle and the per-rule enabled flag cover REQ-NPCWP-08.
- REQ-HOT-05 (optional per-type colours only): A 'may' item that is not planned. The documented default colours and the configurable opacity are built in T3.6.
- REQ-UI-21 (optional notice channel on the mineshaft alert only): not built. The alert stays a chat line with the two REQ-MSA-11 toggles (mineshaft-alert out of scope: no non-chat versions).
- updater Out of scope: Mod Menu update badge: The updater spec lists it under Out of scope as 'possible later' (it would have to reuse the cached result and never send a second request). It is not built for 2.0.0. No REQ-UPD requirement is deferred: REQ-UPD-02 to REQ-UPD-20 all have a Phase 4 task or G4 item.
- Estimated kill counter from mob deaths (REQ-BEST-13 keeps it out of v1): R11: counts come only from server-shown numbers. If the G1 capture shows that the widget is unusable, only the menu + chat fallback ships (AC-BEST-17); an estimated counter would be a new decision after G1.
- Out of scope (sbxp-optimizer): resale / net cost mode, capital limits, Derpy tax: D-19: gross costs only in v1; resale mode later.
- Out of scope (sbxp-optimizer): reading AH screens for prices, client-side AH fetch, third-party lowest-BIN APIs, any API key: D-20: one keyless AH scan per data update at build time (T6.2c) plus overrides. AH-screen reading is a later option with no v1 task.
- Out of scope (sbxp-optimizer): crediting one grind to several tasks, optimal (non-greedy) planning, P90 'cautious' ranking, per-family stages, calibrating personal rates from chat logs: Listed as out of scope in SPEC sbxp-optimizer; the README states them as limitations (T6.11).
- Out of scope (sbxp-optimizer): Ironman/Stranded/Bingo cost models, merging co-op progress: SPEC out of scope. Only the EC-SBXP-20 notice (T6.8) and the EC-SBXP-10 'may be shared' mark (T6.5c) are built.
- Out of scope (sbxp-optimizer): paid carry services as an XP source: D-29: excluded.
- Out of scope (sbxp-optimizer): wiki-derived data files, scripted wiki access, the planning taxonomy draft as an input: D-16 (a, not c), D-25, D-28 (i). The optional D-16 (b) consent request and D-28 (ii) low-rate reading are not pursued in 2.0.0.
- Out of scope (sbxp-optimizer, sbxp-update-skill): Hypixel text in other languages: SPEC out of scope. EC-SBXP-22 only: parsers match nothing, one hint, no wrong marks (T6.5).
- Out of scope (sbxp-update-skill): scheduled or automatic runs, detecting patches by crawling hypixel.net or the wiki, WebFetch as a number source: SPEC out of scope (Hypixel ToS, Weird Gloop terms); D-23.
- Out of scope (sbxp-update-skill): merging the PR, pushing to main, releasing, changing Java code or the schema: SPEC out of scope. Shipping a data-only PATCH release after a skill PR belongs to the release module (REQ-REL-06, REQ-REL-16).
- release Out of scope: publishing on Modrinth, CurseForge or another platform: Out of scope per the release module and A-8: distribution is through GitHub Releases only in 2.0.0.
- release Out of scope: automated publishing from CI, build-provenance attestations, signing: Out of scope per the release module (possible later). Publishing stays a manual, approval-gated T7.4.
- release Out of scope: real screenshots: The brief asks for placeholders (REQ-REL-01); the user may replace them.
- release Out of scope: 26.1.x maintenance release (1.0.2 hotfix): Decided D-13: no 26.1.2 hotfix.
- EC-REL-14 (README names a 26.2.x point release): Only after a boot check on that point version, if one appears. Nothing to build for 2.0.0; `~26.2` already covers it.

## 8. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Runtime on 26.2 is so far verified only at compile/bytecode level | High | G1 gate (tiers B–E) before any UI or feature work |
| Users keep running rule-breaking 1.0.1 until a release ships | High | D-13 (early v1.1.0 after G1, plus a warning on the v1.0.1 release) |
| Render Chest coexistence or shader (Iris) issues; library only supports the two newest MC drops; first-wins callback shared with other mods | Med | Tier D boot with your stack; smoke with shaders on/off; glow isolated behind one `GlowHandler` |
| 26.3 is stable since 2026-09-15; SkyBlock mods still target 26.2 | Med | Submit-based rendering and accessor-free UI keep the next port small |
| Tab, sidebar and Bestiary widget formats unverified | Med | Dump capture at G1; provisional fixtures marked UNVERIFIED; menu/chat fallback for the bestiary |
| Config migration loses settings | High | Versioned migrations, backup on parse failure, tests with a 1.0.1-shaped fixture |
| Behaviour change (visible-only glow, invisible mobs ignored, feature 1 only in view) reported as a regression | Med | CHANGELOG, README and an in-game tooltip |
| UI rebuild is the largest chunk | Med | Fill-based corners, shared layout pass, screenshot gametests, stretch items deferred |
| Hypixel formats change | Med | Regex in one place per service, fixture tests, unknown formats logged once |
| Mixin conflicts on `ItemInHandRenderer` / `FishingHook` | Med | Optional mixin config; `@WrapOperation` / `@ModifyReturnValue` only; tier D injection export |
| Corpse data comes from an unmerged PR on a fork | Low | Commit pinned, notice plus credit, CC0 fallback dataset available (D-5) |
| Updater bricks an install | High | Notify-only fallbacks; new jar in before old jar out; hash checked before and after the move; reconciliation; Windows tests |
| Rules are interpreted by Hypixel staff, not by us | Med | Policies P1–P7, conservative defaults, every feature toggleable, AMBER tooltips |
| SBXP: ~41 % of XP has its count, maximum or structure only on the wiki (48 families with step values seen elsewhere, plus 25 wiki-only families, 1,550 XP) | High | Your `/sblevels` capture (D-17) and NEU (MIT) fill it; unfilled rows ship `null` / `verified: false` |
| SBXP: for big families the leveling menu shows only totals, so a progressed profile could be told to do finished steps | High | Component parsers (T6.5b, T6.5e, T6.5f; D-27), tier inference, manual "set level"; unknown state is badged, never assumed |
| SBXP: rate research needs forum, reddit and wiki pages that may not be fetched automatically | High | D-28: you supply pages; logs, MIT tool code and API formulas are primary; search snippets are leads only |
| SBXP: 0.27.2 (RC 2026-09-30) changes Pet Score right after the data freeze | Low | `changes[]` entry with status `release_candidate`; update-skill run once live |
| SBXP: research volume (127 families, hundreds of rates) exceeds one session | High | Priority order (D-18); batches T6.3a–c; "stopped at" report; unresearched rates show "no estimate", never a guess |
| SBXP: rates go stale after patches; community rates are sparse for early/mid | Med | Rates per stage with sources, dates and confidence; the update skill re-checks affected grinds; per-task overrides |
| SBXP: greedy plan is not optimal, and grinds that progress several tasks at once are not credited | Low | Documented limitation; time is conservatively over-estimated |
| SBXP: costs are gross; retention (Pet Score, Accessory Power) partly unverified | Low | Gross in v1 (D-19); `xpBasis` recorded for a later resale mode |
| SBXP: EU database right / host terms for wiki-derived data | Med | Wiki as human cross-check only (D-16); every shipped value has a non-wiki source; scratch copies deleted (D-25) |
| Live Bazaar fetch breaks Hypixel API policy | Low | Keyless endpoint only, cached, ≤ 1 request per 15 min while in use, opt-in; README non-affiliation statement |

---

## 9. Decisions (all decided on 2026-10-01)

**Record:** you approved every decision below with the recommended option ("Plan: approve with recommendations"). One timing change: D-17's "now" capture moved to G1. The spec-level questions R1–R17 are recorded in `SPEC.md` §12.2 (all recommended, except **R10 = b**: the Croesus tooltip adds "≈ 1 in N runs (base)"). The text below is kept as the record of what was decided.

- **D-1 Glow compliance:** switch to depth-tested glow (Render Chest, Apache-2.0, bundled), permanently ignore invisible entities, and offer **no** through-wall option. → *Yes.*
  - *Consequence for feature 1:* Trinity/Tomioka/Duncan then glow only once they are in view, so you can't find them through walls any more. Skyblocker's rare-room alert already tells you that the room exists.
- **D-2 Dungeon islands:** split `dungeon` → "Catacombs" and `dungeon_hub` → "Dungeon Hub" (with migration), or merge both? → *Split.* Merging would draw Dungeon Hub waypoints over every dungeon run.
- **D-3 "Gambling overlay":**
  - (A) numeric odds overlay
  - (B) reveal animations, code-drawn and without SkyOcean art
  - (C) both

  Also approve the T3.9 case list. Note that SkyCase (MIT, 26.2) already provides (B) for Croesus, Vanguard and Scatha. → *A, renamed "Rare Drop Odds"; B left to SkyCase or kept as a later follow-up.*
- **D-4 Drill fuel:** which symptom do you see? (a) the drill dips in your hand on every block, (b) mining progress resets, (c) the fuel number or bar is wrong or missing. → *Implement (a) only (toggle, default ON). (b) is excluded (packet suppression; Hypixel fixed it on 2026-09-17). (c) only if you want a fuel HUD.*
- **D-5 Third-party data:** bundle the MIT data with notices, or stay 100 % CC0 (ShaftUtils spots, ~16 fewer; hand-entered numbers)?
  - The MIT data is the merged corpse spots (SkyHanni-REPO **unmerged** PR #759 by GrowlingGrizzly + meowdding-repo) plus the NEU bestiary and RNG weights.
  - If you choose MIT: ship from the pinned PR commit now, or ask the maintainers or wait for the merge first?

  → *MIT with notices, pinned commit, ship now with credit.*
- **D-6 Defaults policy (§3):** P1–P7, plus these per-item defaults:
  - ON: distance line, hotspot ring, hotspot-gone warning, bobber fix, drill animation, the "You found X" title (current behaviour)
  - OFF: corpse spots, odds HUD, bestiary HUD
  - SkyBlock XP: module and passive `/sblevels` / action-bar reading ON, "next best task" HUD OFF, live Bazaar OFF (D-22)

  → *Accept.*
- **D-7 Corpse keys:** count from the inventory only (Hypixel uses keys from the inventory); an approximate sack count is a possible later extra. → *Inventory only.*
- **D-8 UI details:**
  - vanilla font
  - fill-based rounded corners
  - accent colour configurable, default teal `#29B6B2` (your Alpaka uses red `#FF5252`)
  - live-apply with save on close
  - remove Cloth Config
  - toasts for warnings and update prompts
  - T2.9 items as stretch goals

  → *Accept all.*
- **D-9 Version:** final release **2.0.0** (new UI, Cloth Config removed, rebuilt confirm-and-install updater). The 26.1 drop and the compliance fixes already ship in the minor release v1.1.0, because the MC version lives in the `+<mc>` build metadata (SPEC Q-REL-01 (a)). → *Accept.*
- **D-10 Git:**
  - **Commit identity:** none is set for this repo, so commits would use your global (work) address and make it public once pushed. Use `Kesuhi <110562470+Kesuhi@users.noreply.github.com>` as the repo-local identity?
  - May I push `update/26.2` to `origin` at checkpoints (CI)? Never `main`.
  - Merge commit for the final PR.

  → *Noreply · push at checkpoints · merge commit.*
- **D-11 Smoke tests:** will you create the Prism copy `26.2 Skyblock K8bas-test` (Prism → Copy instance), or may I copy the ~2.2 GB folder while Prism is closed? Can you do ~30–40 min at G1, then short batched checks? → *You create the copy.*
- **D-12 NPC/mob data:** bundle in the jar only, with updates through releases (gists frozen), or keep a remote refresh? → *Bundled only.*
- **D-13 Early release:**
  - Publish **v1.1.0+26.2** (port + compliance fixes) right after G1 as a normal GitHub release (manual install). This also tests the new asset format early.
  - Add a warning to the v1.0.1 release notes now.
  - Optional: a 1.0.2 hotfix for 26.1.2.

  → *v1.1.0 after G1 + warning on v1.0.1; no 26.1.2 hotfix.*
- **D-14 Updater scope:** (A) confirm + staged install, as your brief asks, or (B) notify + release-page link only (simpler; what SkyHanni and Skyblocker do)? → *A.*
- **D-15 Beam colours:** "category" = island, with an optional per-rule override, and glow colour kept separate. → *Accept.*

**SkyBlock XP Optimizer (Phase 6):**

- **D-16 Data sources & licensing:**
  - (a) The wiki is a **human cross-check only**. XP values, maxima and structure come from the Hypixel API, NEU (MIT), your menu captures and your logs. Patch notes you supply show *what* changed. Rates and drop chances may cite one human-read wiki page, but never as the only source of a `verified` value.
  - (b) Additionally ask Weird Gloop and the wiki staff for consent to automated use; I'd draft it, and you send it.
  - (c) Ship a wiki-derived BY-NC-SA file.

  Also: do you live in Germany or elsewhere in the EU (relevant to the database right)? → *(a), optionally (b); not (c).*
- **D-17 `/sblevels` capture:**
  - Now, with zero code: the SkyBlockAPI library inside your SkyBlockPv mod has a `chest_dumps` debug toggle (press S in a menu). About 16 screens, ~5 min, on your main profile.
  - At G1 with our armed dump: the same, plus the component menus of D-27 and an optional full step-level walk (~15–25 min).

  → *Family level now, if you're willing; the rest at G1.*
- **D-18 Research priority:** (a) by your *remaining* XP, which puts Hunting, whispers, attributes, composter, carnival, HOTF tier 8 and dungeons first; (b) by total XP for a fresh profile (Skill Level Up, Bestiary, Slayer, Museum first); (c) a blend that takes the better rank of both. → *(c).*
- **D-19 Cost model:** gross only in v1 (no resale assumed), or also a "resale mode" (net, only for families with verified XP retention)? → *Gross in v1; resale mode later.*
- **D-20 Auction House prices:** no client-side AH fetch. Instead:
  - bundled reference prices built by `sbxpData --prices` from **one** keyless `/v2/skyblock/auctions` scan per data update. That is ~58 MB; it asks first; aggregates only; player UUIDs never written.
  - your overrides

  Reading prices from AH screens you open is a later option (no v1 task). Third-party lowest-BIN APIs are not used in v1, neither at runtime nor at build time; adding one needs its operator's permission and an AD-7 amendment. → *Accept.*
- **D-21 Row granularity:** one row per instance's *next* step (~2,800 rows), every step expanded (~12,600), or one row per family (~127)? → *Next step per instance.*
- **D-22 Live Bazaar (exception to AD-7):** opt-in, default OFF; ≤ 1 request per 15 min while the optimizer is open; instant-buy price by default, buy-order as an option. → *Accept.*
- **D-23 Update skill:**
  - `disable-model-invocation: true`
  - you paste or save the patch notes and update post; it never fetches hypixel.net
  - data tool in Java (CI needs only the JDK)
  - gates before commit and before push/PR

  The skill-creator's formal eval loop needs Python + PyYAML + the `claude` CLI. May I use the Python in `~/.local/bin` with a local venv, or should a dry-run test suffice? → *Accept the design; venv OK if you agree.*
- **D-24 Maximum level shown:** data-driven from your menu capture; "unknown" until captured. The wiki's 620.39 is unconfirmed; the top 50 of a public leaderboard all sit at 620.16. → *Accept.*
- **D-25 Cleanup:** after you approve the plan, delete my scratch copies of wiki/Fandom pages **and everything parsed from them** (the taxonomy draft, revision timeline and summary tables). They are never an input to the shipped data. The raw AH samples with player UUIDs are already deleted. → *Accept.*
- **D-26 Structure:** the optimizer becomes Phase 6 and the release Phase 7. The addendum's "internal database from Phase 3.9" is the bundled database that brief Phase 3 item 9 (NPC & mob list review, T3.8 [brief 3.9]) recommends; T3.0c generalises it into the shared data layer (SPEC Q-DATA-02). → *Accept.*
- **D-27 Per-component progress:** the leveling menu shows only totals for skills, collections, museum, minions, bestiary, HOTM/HOTF, Catacombs and slayers.
  - (a) Add read-only parsers for the menus you open (Skills, Collections, Museum, Bestiary, HOTM, Pets, Accessory Bag), plus tier inference and a manual "set level" (T6.5b, T6.5e, T6.5f).
  - (b) Rank those families only at family level.

  → *(a).*
- **D-28 Research access for T6.3** (the forum, reddit and wiki sources your method names):
  - (i) Claude reads such a page only when you paste it or save it locally (`access: user_supplied`). Search-result snippets are leads, never the sole source. Primary rate sources are your own logs (personal rates), MIT tool code and official API data, with community pages as corroboration.
  - (ii) If consent is granted under D-16(b), Claude may read single wiki pages at a low rate.

  Expect a few minutes of your time per research batch to supply pages. → *(i) until (b) is answered.*
- **D-29 Zero-cost and service tasks:** Community Shop upgrades (free but waiting days, or Gems) go into the "free / waiting" group with their wait shown. Paid carry services are excluded. → *Accept.*

All decisions are recorded; changes to them go through `SPEC.md` first (living spec).

---

## 10. SkyBlock XP data schema (draft for review)

Files: **`docs/sbxp/tasks.schema.json`** (JSON Schema 2020-12, draft v1) and **`docs/sbxp/tasks.example.json`**. The example is six families covering every task shape: ladder, one_time with a drop, capped, repeatable/coin-only, and two generators. Its values come from the research, but it is an example, not the shipped data.

**Checks run (ajv, strict):**
- The example validates.
- **30** deliberately broken variants are all rejected, among them:
  - a value without a source, `verified: true` with one source, a wiki source licensed MIT or fetched via API, a forum page "fetched via API"
  - type mixing (`one_time` + steps), options together with effort, `${n}` in generator ids
  - untyped prerequisites (skill without level, lowercase item id)
- **22 real families**, encoded by a reviewer, validate after a mechanical migration: powder/whispers (decaying), essence shop, Abiphone, Museum, Pet Score, Attribute Levels, Slayer, dungeon floors, three event families, Fast Travel, consumables, crop milestones, Bestiary, Community Shop, chapters, Accessory Bag, Catacombs, HOTM tiers, Skill Level Up.

**Bundled task table `sbxp/tasks.json`:**

| Part | Content |
|---|---|
| Envelope | `table`, `schemaVersion`, `dataVersion` (+1 per change), `gameVersion` (`0.27.1`, live version from the update post), `generatedAt`, `license` (`CC0-1.0`) |
| `sources` | Registry: kind (official_api, patch_notes, menu_capture, user_log, neu_repo, skyhanni_repo, wiki_crosscheck, forum, reddit, tool, own_measurement), url, revision (revid / commit / API build), date, licence (`facts`, `official_api`, `MIT` + notice, `CC0-1.0`, `personal`), **`access`** (api, git, user_supplied, own_capture, search_snippet). Wiki and forum sources must be `user_supplied` or `search_snippet` |
| `constants` | `xpPerLevel` = 100; `maxTotalXp` (nullable until your capture) |
| `stages` | early / mid / late with the in-game SkyBlock Guide stages, used as the per-profile default |
| `currencies` | `via_item` (e.g. essence → Bazaar item), `via_rate` (e.g. copper/h), `real_money` (gems: never converted), `none` |
| `rates` | `id → {unit "x/h" or "x/day", what, byStage{all\|early\|mid\|late → {value\|null, assumes, sourceRefs, confidence, verified, range, coinsPerHour}}}`. Fully specified lookup (stage → all → nearest, ties lower, which forces low confidence; null counts as absent). A non-null value needs ≥ 1 source; high confidence needs ≥ 2; verified needs ≥ 2 independent origins or a `menu_capture` |
| `prereqProducers` | Which step produces a level prerequisite (e.g. `skill → core.skill_level_up.${id}#${level}`), for "what unlocks it" and chain efficiency |
| `families[]` | id, name, category/subcategory, `maxXp`, `xpBasis`, `coopScope`, default `availability`, `detect` (menu item name, `componentMenu` for D-27, action-bar labels and regex→field patterns, chat pattern ids, Hypixel task ids), `tasks[]` and/or `generators[]`, `changes[]` (live / release_candidate), `verification` |
| `task` | `type` = `one_time` \| `ladder` (ordered one-time steps, REQ-SBXP-02) \| `repeatable` (finite) \| `capped` (bands for decaying counters; `unitsFromTasks` for derived XP such as bestiary milestones). Types are mutually exclusive. Also: `xp`, `effort[]`, `cost[]` or `options[]` (alternatives: buy / grind / wait / gems), `group` (best member counts), `requires[]`, `availability`, `sourceRefs`, `verification` |
| `step` | Stable `id` (progress keys on `<taskId>#<id>`), `n`, `menuName`, `xp`, own effort/cost/options/availability, extra `requires` |
| `effort[]` | `throughput` {amount, unit, rateRef} · `drop` {chance (single, per stage, or null), pity, attemptRateRef} · `fixed` {minutes, assumes, sources}. **Absent = not researched; `[]` = none needed; any null = not researched** |
| `cost[]` | `coins` · `item` {itemId, qty, consumed} · `currency` — same absent / `[]` / null rule |
| `requires[]` | task (+ step), skill, slayer, catacombs, dungeon_class, collection, hotm, hotf, garden, sb_level, item (typed item id), island, quest, `any` {of: […]}, other (note). Required fields per kind; a null level means "requirement unknown" |
| `generators[]` | Expand a reference table into **one task per instance** (steps `#n`). Fields: `type`, `where` filter, XP from list / bands / field, per-instance `verifiedFrom`, amount, rate template, `effortFrom`, `costFrom`, `optionsFrom`, `requiresFrom`, fixed minutes with sources, `groupTemplate`, `availability`, `detect`, `verification` |
| `idAliases` | Renames for task and step ids, so progress and overrides survive |

**Separate files:**
- **`sbxp/prices.json`** (bundled, built by `sbxpData --prices`): registry envelope with its own `dataVersion` as the price snapshot version; per item {bazaar instantBuy/buyOrder, npc, ahReference, observedAt}.
- **Reference tables `ref.*`** (T6.2, T6.2e; the NEU-derived ones T6.2f): registry envelope. Tables generated from NEU files are MIT, live under `thirdparty/`, and carry a notice.
- **User state** is never bundled; it is saved atomically with migrations.
  - **Global** config section `sbxp_optimizer`: default coinsPerHour, filters, includeLocked, includeCoinOnly, priceMode, liveBazaar, unknownPrereqTreatment.
  - **Per profile** `profiles/<playerUuid>/<profileKey>/sbxp.json`:
    - stage (suggested from SkyBlock level until set), optional coinsPerHour (R14)
    - rate, time and coin overrides, each storing the `dataVersion` they were made against, so stale ones can be flagged
    - completed steps, partial counters, component levels (T6.5b), manual marks
    - menu observations with time and gameVersion, and unmapped labels

**Rules the validator enforces in code (beyond the schema):**
- **Sources:**
  - every non-null `xp`, `maxXp` and `xpPerStep` cites ≥ 1 source whose kind is not `wiki_crosscheck`; an *unverified* rate value, drop chance or pity may rest on one user-supplied wiki page (D-16 a)
  - `verified: true` needs ≥ 2 independent origins or a source of kind `menu_capture`; the community wiki and Fandom count as **one** origin, being one page history
- **References:**
  - every `rateRef`, `attemptRateRef`, `sourceRefs` entry, generator `from` and expanded `rateRefTemplate` resolves
  - `throughput.unit` matches the rate's unit numerator
- **Ids and steps:**
  - ids are unique after generator expansion, and hand-written and generated ids never collide
  - step `n` is contiguous 1..k
  - XP lists and bands cover every generated step
- **Value checks:** `p10 ≤ value ≤ p90`; `sbLevelRange` is ascending; family maxima recomputed from the reference tables (and capped bands) match `maxXp`, or a conflict is recorded.
- **Graph:** there are no prerequisite cycles.
- **Text and licensing:** strings carry no colour codes or lore; MIT tables sit under `thirdparty/` with a notice.

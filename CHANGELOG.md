# Changelog

All notable changes to K8bas Skyblock Utility are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **A new settings screen.** `/ksu`, the "Open settings" key and Mod Menu open the mod's own
  settings screen: categories down the side, a search box that finds every option and rule, rules
  as cards you open and edit in place, and **Add from database** with island folders and a search.
- **`/ksu <text>` opens the settings with `<text>` searched**, for example `/ksu glow`.
- **A little motion in the settings screen.** It opens with a short zoom, and switching category slides
  and fades the new page in. Both take about a fifth of a second, and clicks land where things are drawn
  while they play.
- **The settings screen works from the keyboard.** Tab and Shift+Tab move through the sidebar, the search
  box and every setting, which scrolls into view; Space or Enter switches, opens or presses it; the arrow keys
  move a slider, pick in a dropdown, open or close a rule and change the category; Delete resets a key
  binding. A ring in the accent colour shows where you are. Esc works as before.
- **HUD editor.** `/ksu hud`, **Edit HUD layout** in General › HUD, or **Edit position** on a feature's card
  opens it: drag the mod's on-screen elements to move them, scroll to scale them (0.5–3.0), use the arrow keys
  to nudge. Esc or Save keeps the layout; Cancel undoes the session. While you drag, an element snaps to the
  screen's centre lines and edges and to the other elements' edges when it comes within 4 px, and a thin line
  shows where; hold Alt to place it freely.
- **NPC waypoints get a beacon beam.** Each fixed NPC you add gets a beam from its block up to the
  top of the world, in its island's colour (each island has its own default, listed in SPEC.md,
  REQ-NPCWP-05). Like a vanilla beacon's, the beam is animated, terrain in front of it hides it,
  and it widens with distance so you can find it from far away (not while you look through a
  spyglass). New NPC Search options **Show beacon beams** and **Show distance** (both default ON).

### Changed

- **New requirement: [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).** The settings
  screen is now written in Kotlin, so the mod needs Fabric Language Kotlin 1.14.1 or newer; many SkyBlock
  mods (SkyHanni, for example) already need it. Without it, Fabric stops the game with a message naming it.
- **Cloth Config is no longer needed.** The settings screen is the mod's own, so the mod no longer
  depends on Cloth Config. You can remove it unless another mod needs it.
- **Behaviour change: settings apply as you change them.** Every change takes effect at once and is
  saved when you close the screen; there is no Save or Cancel button. *Reason:* you see the effect
  of a change while you make it. *Effect:* closing with Esc keeps your changes.
- **Behaviour change: waypoints look like Skyblocker's.** The label has no dark background any
  more and sits on the centre of the NPC's block, 1.5 blocks up. The label is always white and the
  distance line below it yellow; the "White waypoint labels" option is gone. *Reason:* the beam now
  carries the colour, and plain text over a beam reads better. *Effect:* your waypoints look
  different after the update; their position moves by half a block to the block's centre.
- **Behaviour change: NPCs have no colour of their own any more.** NPC Search draws everything in
  white: waypoint labels, the outline of moving NPCs, the dot on a rule's card and the "You found"
  title. *Reason:* the beam shows the island's colour, and one colour keeps the NPC list simple.
  *Effect:* NPCs you had coloured are white now; the colour stays in your settings file, unused.
- **Behaviour change: slimmer rule cards with a Remove button.** A mob or NPC you added from the
  database shows only what you can usefully change: on or off, and for a mob its entity type and
  colour. Its label, island, name match and name pattern come from the database. Every rule has a
  **Remove** button at the right end of its header, in place of the Delete button inside the card.
  *Reason:* the database already knows those fields, and removing a rule should not take a click to
  open it first. *Effect:* rules you wrote by hand (no database entry) still show every field.
- **Behaviour change: a waypoint from the NPC list follows its corrected position.** A waypoint you
  added from the NPC list uses that NPC's current coordinates from the list, so a corrected entry
  moves it; a waypoint whose NPC is no longer listed keeps its stored position. *Reason:* fixes to
  the list should reach waypoints you already added. *Effect:* a few waypoints may move to their
  NPC's corrected spot.

### Fixed

- `/ksu debug`, `/ksu debug dump` and `/ksu debug dump containers` on their own show how to go on;
  before, they were sent to the server.
- Waypoint labels behind water, stained glass or ice stay readable with **Improved Transparency**
  (the Fabulous graphics preset) on; before, water and ice covered them. Waypoint labels are now
  drawn over clouds and rain as well.

## [1.1.0] - 2026-10-02

This release ports the mod to Minecraft 26.2 and makes the outlines follow Hypixel's rules: only
what you can see is outlined, and invisible mobs never are.

**Requirements:** Minecraft 26.2 (Fabric) with Java 25, Fabric Loader 0.19.5 or newer, Fabric API
0.161.0 or newer, Cloth Config 26.2.155 or newer and Hypixel Mod API 1.0.2 or newer (its build
labelled `mc26.1` also runs on 26.2). Render Chest 1.0.3 is bundled. Minecraft 26.1.x is no
longer supported; 1.0.1 was the last build for it.

**Upgrading from 1.0.x:**
- 1.1.0 needs Minecraft 26.2, and 1.0.x runs on 26.1. Switch your instance to Minecraft 26.2 with
  Fabric Loader 0.19.5 or newer, replace Fabric API and Cloth Config with their 26.2 builds, and
  use Hypixel Mod API 1.0.2 or newer (see Requirements). On 26.1, Fabric Loader stops the game
  with a message about incompatible mods.
- Install this version by hand once. The updater in 1.0.x asks a Modrinth project that returns
  404, so it never finds this release. Download `k8bas_skyblock_utility-1.1.0+26.2.jar`, put it
  into the `mods` folder of your 26.2 instance and remove the 1.0.x jar.
- While you run 1.1.0, updates are installed by hand: its update check only notifies. One chat
  line per session tells you about a newer version, and nothing is downloaded or installed.
- Your settings are migrated on the first start, and a copy of the old file is kept as
  `config/k8bas_skyblock_utility.json.v0.bak`. Your rules, your keybinds and your choice for the
  update check are kept.
- What behaves differently (details, reasons and effects under "Changed"): the outline is
  visible-only; invisible mobs are never outlined; NPCs are outlined only while in view; the
  "You found \<NPC>" title appears after line of sight, once per run, and only for Trinity,
  Tomioka, Duncan, Xalx and Pete; the dungeon lobby is its own
  island, "Dungeon Hub", apart from "Catacombs"; waypoint labels are white by default; the update
  check is notify-only.

**Defaults in this version:**
- Mob Highlighter: ON, with the rules you add.
- NPC Search: ON, with the rules you add. A fresh install outlines no NPC.
- "You found" title: ON, for Trinity, Tomioka, Duncan, Xalx and Pete. It appears after line of
  sight, once per run.
- NPC waypoints: ON for each NPC you add (its own Enabled switch); labels show at their fixed
  coordinates, also behind blocks.
- White waypoint labels: ON (off draws each label in its NPC's colour).
- Mob scan range: 64 blocks (0 = unlimited, slider 0–128).
- Check for updates (notify): ON. Channel: STABLE, or BETA when you run a pre-release build.
- Keybinds (open settings, toggle Mob Highlighter, toggle NPC Search): unbound.

### Added

- NPC Search option **"You found" title** (default ON).
- NPC Search option **White waypoint labels** (default ON).
- Debug commands that write what the game already shows into the log, tagged `[K8BAS-DUMP]`, for
  bug reports: `/ksu debug dump tab`, `/ksu debug dump sidebar`, `/ksu debug dump entities` and
  `/ksu debug dump containers on|off` (container dumps switch themselves off after 60 minutes).
  Your own name and the names of the players in your tab list or near you are replaced by Self,
  Player1, Player2 and so on. Other names, for example in menu or sidebar text, stay as they are,
  so check a dump before you share it. Nothing is sent anywhere.
- Settings backups and recovery. A settings file with invalid content is kept as
  `k8bas_skyblock_utility.json.broken-<date>-<time>.bak` and defaults are used. A broken module section
  resets only that module. A settings file from a newer version is backed up before it is first
  saved. In each case a chat message tells you after you join a world.
- Each release has a `.sha256` file next to the jar, to check the download.
- `THIRD_PARTY_NOTICES.md` for the bundled Render Chest (Apache-2.0).

### Changed

**Not ported cleanly / behaviour changes**

- **Behaviour change: the outline is visible-only.** Mobs and NPCs are outlined only where you can
  see them. Blocks hide the outline, and a partly hidden mob is outlined only on its visible part.
  *Reason:* Hypixel's rules do not allow seeing around or over objects. *Effect:* you no longer
  see highlighted mobs behind blocks.
- **Behaviour change: invisible mobs are never outlined.** This holds even when a rule matches and
  even when the mob wears armour, and an invisible mob never triggers a title. *Reason:* Hypixel
  bans displaying invisible entities. *Effect:* an invisible mob shows nothing until it becomes
  visible again; then its outline returns within a second.
- **Behaviour change: NPCs are outlined only while in view; blocks hide the outline.** *Reason:*
  the same rule as for mobs. *Effect:* you find a moving NPC by walking until you see it, not by
  its outline behind blocks.
- **Behaviour change: the "You found \<NPC>" title appears after line of sight, once per run.** It
  shows the first time you have a clear line of sight to the NPC on each server, so again in the
  next dungeon run, and it has its own toggle. A title that comes up while a menu is open waits
  until the menu closes. It is now shown only for Trinity, Tomioka, Duncan (Catacombs), Xalx and
  Pete (Crystal Hollows); other NPCs your rules match are still outlined. *Reason:* alerts about
  entities may only fire after line of sight, and the title is meant for these rare NPCs.
  *Effect:* before, it showed once per game launch for every searched NPC, even when it was hidden.
- **Behaviour change: glow the server sets keeps its colour.** *Reason:* recolouring it would show
  your rule colour behind blocks. *Effect:* a mob that already glows (for example with the Glowing
  effect) keeps the server's colour instead of your rule colour.
- **Behaviour change: the first matching rule in list order wins.** This now also holds when a rule
  for any entity type is listed above a rule for one type. *Reason:* rules for one type were
  always tried first, which did not match the order you see. *Effect:* where two rules match the
  same mob, the colour can differ from 1.0.1.
- **Behaviour change: rules that cannot work are inactive.** An empty Contains or Exact pattern, an
  invalid regular expression, an unknown entity type, or the match mode None without an entity type
  makes the rule inactive. The rule editor marks it with ⚠ and says why. It stays in your
  settings and is logged once. *Reason:* an empty Contains pattern used to outline every entity,
  an empty Exact pattern every entity without a name tag, a type with invalid characters (such as
  capitals) matched every type, and None without a type would outline every entity, players
  included. *Effect:* such a rule outlines nothing until you fix it.
- **Behaviour change: players are not outlined by name rules.** You are never outlined, also in
  third person. Other players are not matched by rules that read a name; player-type NPCs still
  match. *Reason:* a player's own name is never read, so a name rule could only match a player
  through a nearby NPC's name tag, which was a false match. *Effect:* players standing next to a
  searched NPC no longer light up.
- **Behaviour change: the dungeon lobby is its own island, "Dungeon Hub".** On the first start,
  fixed NPC Search rules on "Catacombs" (such as Croesus) move to "Dungeon Hub". Rules for moving
  NPCs (Trinity, Tomioka, Duncan) and mob rules stay on "Catacombs" and stay enabled. *Reason:* the
  mod now tells the lobby from a run. *Effect:* lobby waypoints no longer show inside runs.
- **Behaviour change: waypoint labels are white by default.** The label and its distance line are
  white; the new option "White waypoint labels" in NPC Search switches back to each NPC's colour.
  *Reason:* white labels stay readable on any background and match the planned beacon beams, which
  will carry each NPC's colour. *Effect:*
  your waypoint labels turn white after the update, until you switch the option off.
- **Behaviour change: updates are notify-only.** When a newer version for your Minecraft version is
  out, one chat line per session says so, with [Changelog] and [Open release page]. Nothing is
  downloaded or installed. *Reason:* no build may replace the mod's jar without your confirmation
  until the full updater with a confirmation step is ready. *Effect:* to update, download the jar
  from the release page yourself.
- **Removed: the "Automatically download updates" option and the "Check Now" button.** *Reason:*
  the old updater behind them asked Modrinth, and until the full updater with a confirmation step
  is ready no build may download or replace the mod's jar. *Effect:* nothing is downloaded, and in
  1.1.0 you cannot start a check by hand; the automatic check still runs while "Check for updates
  (notify)" is ON. A manual check returns with the full updater.
- **Removed: Mod Menu's update lookup for this mod.** *Reason:* it asked Modrinth, and GitHub is
  now the only update source. *Effect:* Mod Menu shows no update notice for this mod; the chat line
  tells you about a newer version instead.

Also changed:
- Ported to Minecraft 26.2. Waypoint labels use the new renderer. They stay readable behind
  blocks, glass and water, and keep their 10-block size further away.
- The update check asks GitHub (`api.github.com`) instead of Modrinth, at most 4 times a day;
  after a successful check, not again for 6 hours (after a failed one it can retry sooner, with
  back-off when GitHub is busy). Its state is kept in
  `config/k8bas_skyblock_utility-update-state.json`.
- "Check for updates on startup" is now "Check for updates (notify)".
- Mob and NPC outlines are drawn by the bundled Render Chest library.
- Highlight matching runs once per game tick, and each name tag is read at most once per mob per
  tick, so busy areas cost less.
- The jar is named `k8bas_skyblock_utility-1.1.0+26.2.jar`, with the Minecraft version after the
  `+`, and the mod reports its version as `1.1.0+26.2`.

### Removed

- Support for Minecraft 26.1.x.
- The Modrinth updater, the "Automatically download updates" option and the "Check Now" button
  (reason and effect under "Not ported cleanly / behaviour changes" above).
- Mod Menu's own update lookup for this mod (it asked Modrinth); GitHub is the only update source.

### Fixed

- Rules restricted to "Catacombs" now work inside dungeon runs; the island names follow the modes
  Hypixel reports, and the island is updated on every server switch.
- The keybind category on the Controls screen shows its name instead of a raw translation key.
- Saving settings can no longer leave a half-written file: changes are written in one step, at most
  2 seconds after a change and on exit.
- The mob and NPC lists give up after 10 seconds without an answer, and one malformed entry no
  longer empties the whole list.
- A world query from the highlight code could run off the game thread next to EntityCulling; all
  matching now runs on the game thread.
- A settings file the game cannot open (for example while another program holds it) is no longer
  replaced by defaults: it is left as it is. For that session defaults are used, so your rules are
  not active, changes are not saved until the next start, and a chat message tells you after you
  join a world.
- A rule without a label in a hand-edited settings file no longer crashes the game.
- The "You found \<NPC>" title also shows when a Mob Highlighter rule outlines the same NPC.

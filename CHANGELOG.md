# Changelog

All notable changes to K8bas Skyblock Utility are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

**Requirements:** Minecraft 26.2 (Fabric) with Java 25, Fabric Loader 0.19.5 or newer, Fabric API
0.161.0 or newer, Cloth Config 26.2.155 or newer and Hypixel Mod API 1.0.2 or newer. Render Chest
1.0.3 is bundled. Minecraft 26.1.x is no longer supported; 1.0.1 was the last build for it.

**Upgrading from 1.0.1:** replace the jar. Your settings are migrated on the first start, and a
copy of the old file is kept as `config/k8bas_skyblock_utility.json.v0.bak`. Your rules, your
keybinds and your choice for the update check are kept.

**Defaults in this version:**
- Mob Highlighter: ON, with the rules you add.
- NPC Search: ON, with the rules you add. A fresh install outlines no NPC.
- "You found" title: ON. It appears after line of sight, once per run.
- Fixed NPC waypoint labels: shown at their fixed coordinates, also behind blocks.
- Mob scan range: 64 blocks (0 = unlimited, slider 0–128).
- Check for updates (notify): ON. Channel: STABLE, or BETA when you run a pre-release build.
- Keybinds (open settings, toggle Mob Highlighter, toggle NPC Search): unbound.

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
- **Behaviour change: the "You found <NPC>" title appears after line of sight, once per run.** It
  shows the first time you can see the NPC on each server, so again in the next dungeon run, and it
  has its own toggle. A title that comes up while a menu is open waits until the menu closes.
  *Reason:* alerts about entities may only fire once you can see them. *Effect:* before, it showed
  once per game launch, even when the NPC was hidden.
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
  settings and is logged once. *Reason:* an empty pattern used to outline every entity, a
  mistyped type matched every type, and None without a type would outline every entity, players
  included. *Effect:* such a rule outlines nothing until you fix it.
- **Behaviour change: players are not outlined by name rules.** You are never outlined, also in
  third person. Other players are not matched by rules that read a name, because their own name is
  never read, so a match could only come from a nearby NPC's name tag. Player-type NPCs still match.
  *Effect:* players standing next to a searched NPC no longer light up.
- **Behaviour change: the dungeon lobby is its own island, "Dungeon Hub".** On the first start,
  fixed NPC Search rules on "Catacombs" (such as Croesus) move to "Dungeon Hub". Rules for moving
  NPCs (Trinity, Tomioka, Duncan) and mob rules stay on "Catacombs" and stay enabled. *Reason:* the
  mod now tells the lobby from a run. *Effect:* lobby waypoints no longer show inside runs.
- **Behaviour change: updates are notify-only.** When a newer version for your Minecraft version is
  out, one chat line per session says so, with [Changelog] and [Open release page]. Nothing is
  downloaded or installed. *Reason:* no build may replace the mod's jar without your confirmation
  until the full updater with a confirmation step is ready. *Effect:* to update, download the jar
  from the release page yourself.

Also changed:
- Ported to Minecraft 26.2. Waypoint labels use the new renderer. They stay readable behind
  blocks, glass and water, and keep their 10-block size further away.
- The update check asks GitHub (`api.github.com`) instead of Modrinth, at most every 6 hours and
  at most 4 times a day, with back-off when GitHub is busy. Its state is kept in
  `config/k8bas_skyblock_utility-update-state.json`.
- "Check for updates on startup" is now "Check for updates (notify)".
- Mob and NPC outlines are drawn by the bundled Render Chest library.
- Highlight matching runs once per game tick, and each name tag is read at most once per mob per
  tick, so busy areas cost less.

### Added

- NPC Search option **"You found" title** (default ON).
- Debug commands that write what the game already shows into the log, tagged `[K8BAS-DUMP]`, for
  bug reports: `/ksu debug dump tab`, `/ksu debug dump sidebar`, `/ksu debug dump entities` and
  `/ksu debug dump containers on|off` (container dumps switch themselves off after 60 minutes).
  Player names in the dumps are replaced by Self, Player1, Player2 and so on. Nothing is sent
  anywhere.
- Settings backups and recovery. An unreadable settings file is kept as
  `k8bas_skyblock_utility.json.broken-<date>-<time>.bak` and defaults are used. A broken module section
  resets only that module. A settings file from a newer version is backed up before it is first
  saved. In each case a chat message tells you after you join a world.
- `THIRD_PARTY_NOTICES.md` for the bundled Render Chest (Apache-2.0).

### Removed

- Support for Minecraft 26.1.x.
- The Modrinth updater, the "Automatically download updates" option and the "Check Now" button.
  A manual check returns with the full updater.
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
- A settings file that cannot be read (for example while another program holds it) is no longer
  replaced by defaults: it is left as it is, and changes are not saved until the next start.
- A rule without a label in a hand-edited settings file no longer crashes the game.
- The "You found <NPC>" title also shows when a Mob Highlighter rule outlines the same NPC.

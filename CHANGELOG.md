# Changelog

All notable changes to K8bas Skyblock Utility are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Bestiary Tracker (Odds & Trackers, off by default): a HUD panel with your bestiary kills, tiers, kills per hour and milestone, read from the tab list, chat and the Bestiary menu and kept per profile; you pick its lines from one checklist, and a compact layout is optional.

## [1.3.1] - 2026-10-18

### Added

- Updates install on restart after one click: a notice shows a new version, and `/ksu update` shows it before it is downloaded.
- `/ksu update` commands: `show`, `check`, `install <version>`, `skip <version>` and `cancel`.
- General › Updates: your version, the latest one, the last check and the status, a Stable or Beta channel, and the update buttons.
- A chat line names a second copy of the mod in your mods folder, once; the copy is kept.

### Changed

- The update check runs only at startup and at the first server join of a session.
- The "Corpse ding" HUD element hides once the Frozen Corpses tab widget shows every corpse as looted.

## [1.3.0] - 2026-10-17

### Added

- "Animations" switch (General › Interface) to turn off motion in the mod's screens.
- Soft shadows under the settings window, dropdown lists, pickers and tooltips.
- The mouse pointer shows what a click does.
- Search matches are highlighted in the settings.
- A "Clear search" button when a search finds nothing.
- Scrolling areas fade at an edge with more to scroll to.
- Sliders with few values show a dot at each value.
- HUD editor: corner handles to scale an element.
- HUD editor: elements snap to other elements' centres.

### Changed

- "Smooth corners" is now ON by default.
- Knobs, switches, colour dots, icons, outlines and the colour wheel are sharp at every GUI scale.
- Smoother motion and scrolling.
- Slimmer scrollbars that widen on hover.
- The mouse wheel no longer changes sliders.
- Dropdown lists and dialogs fade in and out.
- One set of corner sizes; the settings window has rounded corners.
- Tooltips show after a short delay, beside their setting.
- The sidebar highlight slides between categories.
- Steadier sliders; their click sound plays only when the value changed.
- Rule cards open and close with a short motion.
- Switches that are off stand out more.
- HUD editor: toolbar at the top, rounded outlines and name labels; "Reset All" asks for a second click.
- Smaller checkerboard behind see-through colours.

### Fixed

- A line of a slider's fill showed across its knob at GUI scale 2 and above.
- A dimmed slider's knob showed the fill and track through it.
- Rounded buttons and fields lost part of their outline at GUI scale 4.

## [1.2.1] - 2026-10-15

### Added

- Mineshaft entry alert: the shaft's frozen corpses and your keys for them, with a [Take out n] button for keys in your sack.
- Possible corpse spots in Glacite Mineshafts, an Organ Donor "Corpse ding" HUD line and `/ksu corpses`.
- Beam colours per island and per fixed NPC.
- Bobber Fix: your own bobber floats smoothly, also on lava.
- Fishing Hotspots: rings in each hotspot's buff colour, and an option to hide Hypixel's hotspot particles.
- Hotspot gone warning when the hotspot you fish in disappears.
- `/ksu debug data` and `/ksu debug dump sounds on|off`.

### Changed

- The NPC and mob lists ship inside the mod instead of being downloaded.
- The hidden Jerries have their own "Hidden Jerries" folder.
- Ghost, Fels, Sneaky Creeper and Invisibug are no longer in the mob list.
- The pickers no longer offer an entry you already added as your own rule.
- A rule whose list entry is gone shows all its fields.
- A fixed NPC keeps a non-default colour from 1.0.x or 1.1.0 as its beam colour.

### Fixed

- Duplicate NPCs merged: Bramass Beastslayer, Hendrik and the Forge Foreman.
- Gemma's and Scoop's waypoint positions.
- NPC names are read past "CLICK" labels and empty lines.
- Glacite mobs are also listed under "Glacite Mineshafts".
- An NPC added under two names draws one waypoint.

## [1.2.0] - 2026-10-09

### Added

- The mod's own settings screen, with search, rule cards and "Add from database".
- `/ksu <text>` opens the settings with that search.
- Accent colour option.
- Notices in a corner of the screen, with position and duration options.
- Short open and category animations in the settings.
- Keyboard control of the settings screen.
- HUD editor (`/ksu hud`).
- Beacon beams for NPC waypoints, with "Show beacon beams" and "Show distance" options.
- Optional "Smooth corners".

### Changed

- Requires Fabric Language Kotlin 1.14.1 or newer; Cloth Config is no longer needed.
- Settings are grouped as General, Highlights and Waypoints.
- Settings apply as you change them.
- Waypoint labels are white with a yellow distance line, on the block's centre; "White waypoint labels" is gone.
- NPCs no longer have a colour of their own.
- Rules from the database show only the useful fields; every rule has a Remove button.
- Waypoints from the NPC list follow its corrected positions.

### Fixed

- `/ksu debug` subcommands without arguments show help instead of going to the server.
- Waypoint labels stay readable behind water, glass and ice with Improved Transparency.

## [1.1.0] - 2026-10-02

### Added

- "You found" title option.
- "White waypoint labels" option.
- Debug dump commands: `/ksu debug dump tab`, `sidebar`, `entities` and `containers on|off`.
- Backups and recovery for broken or newer settings files.
- A `.sha256` file next to each release jar.

### Changed

- Ported to Minecraft 26.2.
- The outline is visible-only.
- Invisible mobs are never outlined.
- NPCs are outlined only while in view; blocks hide the outline.
- The "You found \<NPC>" title appears after line of sight, once per run, and only for Trinity, Tomioka, Duncan, Xalx and Pete.
- Glow set by the server keeps its colour.
- The first matching rule in list order wins.
- Rules that cannot work are inactive and marked with ⚠.
- Players are not outlined by name rules.
- The dungeon lobby is its own island, "Dungeon Hub".
- The update check asks GitHub and only notifies.
- Outlines are drawn by the bundled Render Chest library.
- Highlight matching costs less in busy areas.

### Removed

- Support for Minecraft 26.1.x.
- The Modrinth updater, "Automatically download updates" and "Check Now".
- Mod Menu's update lookup for this mod.

### Fixed

- Rules for "Catacombs" work inside dungeon runs.
- The keybind category shows its name on the Controls screen.
- Saving can no longer leave a half-written settings file.
- The mob and NPC lists time out after 10 seconds, and one bad entry no longer empties a list.
- Highlight matching runs only on the game thread.
- An unreadable settings file is no longer replaced by defaults.
- A rule without a label no longer crashes the game.
- The "You found" title also shows when a Mob Highlighter rule outlines the same NPC.

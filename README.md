# K8bas Skyblock Utility

A modular, client-side Fabric mod for Hypixel Skyblock. Each feature is a self-contained
module that can be enabled or disabled independently from an in-game settings screen.

Modules so far:
- **Mob Highlighter** — draws a thin glowing outline (reusing vanilla's Glowing-effect
  render path) on entities matched by rules (entity type and/or custom-name
  substring/regex), each rule with its own color. Comes with a searchable, island-sorted
  mob database to add rules from without typing patterns by hand.
- **NPC Search** — tracks NPCs from a searchable, island-sorted database: NPCs with a
  known fixed location get a permanent floating waypoint (name + live distance), while
  NPCs without one get the same nametag-based highlighting as Mob Highlighter. Both only
  activate while you're actually on the matching island.

Also included: a Modrinth-based self-updater (off by default, opt-in in General) that
downloads and verifies new versions without ever touching the currently-running jar
while the game is open.

/ksu or /kskyblockutility to access the mod config.

The mod features some features I wanted but couldn´t find in any other mod, if you encounter any issues or want to request a custom feature dm me on discord (@disable.rx).

## Setup

For IDE setup instructions, see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

This project is based on the official Fabric example mod template (CC0). See [LICENSE](LICENSE).

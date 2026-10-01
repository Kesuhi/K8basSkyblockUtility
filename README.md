# K8bas Skyblock Utility

A client-side Fabric mod for Hypixel SkyBlock. It only changes what your own game displays: it
outlines the mobs and NPCs you search for, shows waypoints for NPCs that stand at a fixed spot, and
tells you in chat when a new version is out. Its in-game settings screen has a switch for each
feature that shows something in game.

K8bas Skyblock Utility is not affiliated with or endorsed by Hypixel.

This README describes version **1.1.0**.

## Supported version

**Minecraft 26.2 (Fabric)**

| Requirement | Version |
|---|---|
| Java | 25 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.161.0 or newer |
| Hypixel Mod API (the mod) | 1.0.2 or newer (the build labelled `mc26.1` also runs on 26.2) |
| Cloth Config | 26.2.155 or newer |
| Render Chest | 1.0.3, bundled inside the jar, nothing to install |

Mod Menu is optional. With it, the settings also open from the mod list.

Minecraft 26.1.x is no longer supported. 1.0.1 was the last build for 26.1. Versions 1.0.0 and
1.0.1 outline mobs behind blocks and outline invisible mobs, which Hypixel's rules do not allow. If
you have to stay on 26.1.x for now, switch off Mob Highlighter and NPC Search there.

## Installation

1. Install Fabric Loader 0.19.5 or newer for Minecraft 26.2 (see https://fabricmc.net/use/installer/).
2. Put these mods into your `mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0 or newer, the build for 26.2
   - [Cloth Config](https://modrinth.com/mod/cloth-config) 26.2.155 or newer, the Fabric build
   - [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) 1.0.2 or newer, the Fabric build;
     the build labelled `mc26.1` also runs on 26.2
   - optional: [Mod Menu](https://modrinth.com/mod/modmenu), the build for 26.2
3. Download `k8bas_skyblock_utility-1.1.0+26.2.jar` from the
   [releases page](https://github.com/Kesuhi/K8basSkyblockUtility/releases) and put it into your
   `mods` folder. Render Chest is bundled inside it.
4. Optional: check the download against the `.sha256` file published next to the jar, for example
   with `sha256sum -c k8bas_skyblock_utility-1.1.0+26.2.jar.sha256` in the download folder. On
   Windows, `certutil -hashfile k8bas_skyblock_utility-1.1.0+26.2.jar SHA256` prints the checksum
   to compare with the one in that file.
5. Start the game and type `/ksu` to open the settings.

## Upgrading from 1.0.x

- **Move your instance to Minecraft 26.2 first.** 1.0.x runs on Minecraft 26.1, and 1.1.0 needs
  26.2. Switch your instance (or make a new one) to Minecraft 26.2 with Fabric Loader 0.19.5 or
  newer, replace Fabric API and Cloth Config with their 26.2 builds, and use Hypixel Mod API 1.0.2
  or newer, as listed under [Installation](#installation). On 26.1, Fabric Loader stops the game
  with a message about incompatible mods.
- **Install 1.1.0 by hand once.** The updater in 1.0.x asks a Modrinth project that returns 404
  (not found), so it never finds this version. Download the jar as described above, put it into
  the `mods` folder of your 26.2 instance and remove the 1.0.x jar.
- **While you run 1.1.0, updates are installed by hand.** Its update check only notifies: when a
  newer version is out, one chat line per session says so, with [Changelog] and
  [Open release page] links. Nothing is downloaded or installed. To update, download the new jar
  from the release page, replace the old one and restart the game.
- **Your settings migrate automatically.** On the first start, the settings file is updated and a
  copy of the old file is kept as `config/k8bas_skyblock_utility.json.v0.bak`. Your rules, your
  keybinds and your choice for the update check are kept. The old "Automatically download updates"
  option is gone.
- **Behaviour changes:**
  - The outline is visible-only: mobs and NPCs are outlined only where you can see them, and blocks
    hide the outline.
  - Invisible mobs are never outlined, whatever your rules say.
  - The "You found \<NPC>" title appears only after you have line of sight to the NPC, once per run.
  - The dungeon lobby is its own island, "Dungeon Hub", apart from "Catacombs" (inside runs). Your
    NPC Search rules for NPCs at a fixed spot on "Catacombs" (such as Croesus) move to
    "Dungeon Hub". Rules for moving NPCs (Trinity, Tomioka, Duncan) and mob rules stay on
    "Catacombs".
  - The update check is notify-only and asks GitHub (`api.github.com`); nothing is downloaded.

  The [changelog](CHANGELOG.md) lists every change, each with its reason and the effect you see.

## Features

| Feature | What it does | Settings category | Default |
|---|---|---|---|
| Mob Highlighter | Outlines the mobs your rules match, in each rule's colour; a rule matches by entity type, by name (Contains, Exact, Regex) or both, and can be limited to one island. | Mob Highlighter | ON (a fresh install has no rules, so nothing is outlined until you add one) |
| Mob Database | A searchable, island-sorted list of mobs; "Add rule" creates a rule for a mob without typing a pattern. | Mob Highlighter, "Mob Database: Open" | no switch (the list loads at game start) |
| NPC Search | Outlines the moving NPCs your rules match, only while they are in view. Its switch and the "Toggle NPC Search" keybind also turn off the NPC waypoints and the "You found" title. | NPC Search | ON (a fresh install has no rules, so no NPC is outlined) |
| NPC waypoints | An NPC that stands at a fixed spot gets a floating label with its name and distance at its fixed coordinates, also visible behind blocks, while you are on its island (on every island if the rule has none). | NPC Search: the module's "Enabled" switch, and each NPC's own "Enabled" switch | ON (module), and ON for each NPC you add |
| NPC Database | A searchable, island-sorted list of NPCs; "Add" creates a waypoint or an outline rule. | NPC Search, "NPC Database: Open" | no switch (the list loads at game start) |
| "You found" title | Shows "You found \<NPC>" in the rule's colour the first time you have a clear line of sight to a searched NPC, once per run. | NPC Search | ON |
| Mob scan range | How far away mobs and moving NPCs are considered for outlines (0 = unlimited, slider 0–128). | General | 64 blocks |
| Check for updates (notify) | Asks GitHub for new releases at most 4 times a day; after a successful check, not again for 6 hours (after a failed one it can retry sooner). Tells you in chat once per session; nothing is downloaded. | General | ON (channel STABLE, or BETA when you run a pre-release build) |
| Keybinds | Open Settings, Toggle Mob Highlighter and Toggle NPC Search. | General (also Controls, "K8bas Skyblock Utility") | unbound |
| Rule warnings | A rule that cannot work (an empty Contains or Exact pattern, an invalid regular expression, an unknown entity type, or match mode None without an entity type) is marked with ⚠ and the reason, and stays inactive until you fix it. | Mob Highlighter, NPC Search | always on |
| Settings backups | Settings are saved to `config/k8bas_skyblock_utility.json`. A file with invalid content, or one from a newer version, is copied to a `.bak` file first. A file the game cannot open (for example while another program holds it) is left untouched: defaults are used for that session, and changes are not saved until the next start. In each case a chat message tells you after you join a world. | none | always on |
| Debug dumps | Write what the game already shows (tab list, sidebar, name tags, some menus) into the game log for bug reports. | none, see [Commands](#commands) | off until you run a command |

Later versions are planned to bring more features and a new settings screen; this README covers
only what 1.1.0 ships.

### Screenshots

> **[Screenshot placeholder: the settings screen, with the General, Mob Highlighter and NPC Search categories]**

> **[Screenshot placeholder: the Mob Database screen, with its search field and "Add rule" buttons]**

> **[Screenshot placeholder: a mob outlined by a Mob Highlighter rule]**

> **[Screenshot placeholder: the NPC Database screen, with its search field and "Add" buttons]**

> **[Screenshot placeholder: a moving NPC outlined by an NPC Search rule]**

> **[Screenshot placeholder: an NPC waypoint label with its name and distance]**

> **[Screenshot placeholder: the "You found \<NPC>" title]**

> **[Screenshot placeholder: a rule marked with ⚠ in the rule editor, with its red explanation line]**

> **[Screenshot placeholder: the update chat line with its [Changelog] and [Open release page] links]**

> **[Screenshot placeholder: the chat message about a settings backup]**

## Commands

| Command | What it does |
|---|---|
| `/ksu` or `/kskyblockutility` | Opens the settings screen. |
| `/ksu debug dump tab` | Writes the tab list to `logs/latest.log`. |
| `/ksu debug dump sidebar` | Writes the sidebar (scoreboard) to `logs/latest.log`. |
| `/ksu debug dump entities` | Writes the name tags shown within about 8 blocks of you to `logs/latest.log`. |
| `/ksu debug dump containers on` | While on, each supported SkyBlock menu you open yourself (such as SkyBlock Leveling, Skills, Collections, Museum or Bestiary) is written to `logs/latest.log`. Switches itself off after 60 minutes. |
| `/ksu debug dump containers off` | Switches the menu dump off. |

Every `/ksu debug` command also works as `/kskyblockutility debug`. The dumps are for bug reports:
each line is tagged `[K8BAS-DUMP]`, and nothing is sent anywhere. Your own name and the names of
the players in your tab list or near you are replaced by Self, Player1, Player2 and so on. Other
names, for example a player named in menu or sidebar text who is not in your tab list, stay as
they are, so check a dump before you share it. The menu dump only reads menus you open; it never
clicks, pages or opens anything.

## Compliance

The mod is client-side and display-only. It changes what your own game shows and never plays for
you. It follows these rules:

1. **No hidden entity is outlined.** This mod never outlines an entity behind blocks, and there is
   no option to turn that on. Glow that the server itself puts on an entity is left as the game
   shows it; the mod does not recolour it.
2. **Invisible entities are never highlighted or announced.** This is built in and has no setting.
3. **Alerts about entities wait for line of sight.** The "You found" title appears only the first
   time you have a clear line of sight to a searched NPC.
4. **Only fixed coordinates show behind blocks.** NPC waypoint labels sit at fixed, known
   coordinates; they are the only thing drawn behind blocks, and distance is shown only to them.
5. **Network packets are only read, never cancelled, delayed or changed.** The mod never sends chat
   messages or commands on its own.
6. **Every feature that shows something in game has its own switch, and features that need care
   carry a tooltip that explains them.** Every default is listed in this README and in the
   changelog. 1.1.0 does not do all of this yet: the NPC waypoint labels, which are drawn behind
   blocks, have no tooltip, and their distance line has no switch of its own (switch off that
   NPC's waypoint, or NPC Search, instead). In 1.1.0 only the "You found" title and the update
   check have a tooltip. Both gaps are planned to close in a later version.
7. **Feature names and descriptions use plain wording** and never suggest seeing hidden things,
   unfair advantages or betting.

What this means since 1.1.0:

- The outline is visible-only. Blocks hide it, and a partly hidden mob is outlined only on its
  visible part.
- Invisible mobs are never outlined, even when a rule matches and even when the mob wears armour.
- NPCs are outlined only while in view; blocks hide the outline.
- The "You found \<NPC>" title appears after line of sight, once per run.

These are left out on purpose, now and in later versions:

- a glow option for mobs out of line of sight
- showing invisible entities
- keeping drill mining progress by holding back the packets the game sends
- automatic party chat messages
- cancelling particle packets
- filtering possible corpse spots by entities you cannot see

## Network access

| Host | When | What for | How to stop it |
|---|---|---|---|
| `api.github.com` | at game start and while you play: at most 4 times a day, and after a successful check not again for 6 hours (after a failed one it can retry sooner, and it waits longer when GitHub is busy) | the update check: reads this repository's release list; it never downloads anything | General, "Check for updates (notify)" OFF |
| `gist.githubusercontent.com` | once at each game start; it gives up after 10 seconds without an answer | downloads the mob list and the NPC list used by the Mob Database and NPC Database | no switch in 1.1.0; the lists will be bundled with the mod in a later version |
| the Hypixel server, through the Hypixel Mod API | while you are connected to Hypixel | subscribes to the location updates Hypixel offers to mods (which server and mode you are on), over your normal game connection; no extra connection is opened | no switch; it is part of your game connection |

Nothing else is contacted. The requests carry a User-Agent that names the mod (for the update check
also its version) and nothing about you: no player name, no UUID, no telemetry. The [Changelog] and
[Open release page] links in the update message open `github.com` in your browser. While the
game's "Prompt on Links" chat setting is on (it is by default), the game asks you to confirm first.

## Licensing and credits

- This mod is released under **CC0-1.0**; see [LICENSE](LICENSE). It started from the official
  Fabric example mod template (CC0).
- The release jar bundles **Render Chest** 1.0.3 by AzureAaron, unmodified, under the
  **Apache License 2.0**. It keeps its own licence; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
  None of its code is copied into this repository.
- Thanks to the Skyblocker and SkyHanni projects, whose visible-only outlines, both drawn with
  Render Chest, showed how to keep highlights within Hypixel's rules. No code from either is used.

## Building from source

With JDK 25, run `./gradlew build`. It runs the unit tests and writes
`build/libs/k8bas_skyblock_utility-1.1.0+26.2.jar` and its `.sha256` file. For IDE setup, see the
[Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## Feedback

Found a problem or want a feature? Open an issue on
[GitHub](https://github.com/Kesuhi/K8basSkyblockUtility/issues) or send me a message on Discord
(@disable.rx). The debug dumps above help with bug reports.

<div align="center">

<img src="src/main/resources/assets/k8bas_skyblock_utility/icon.png" alt="K8bas Skyblock Utility" width="128">

# K8bas Skyblock Utility

**A client-side Fabric mod for Hypixel SkyBlock: outlines for the mobs and NPCs you are looking for,
waypoints with beacon beams for NPCs at fixed spots, and a settings screen that finds every option.**

[![Latest release](https://img.shields.io/github/v/release/Kesuhi/K8basSkyblockUtility?label=release&color=29B6B2)](https://github.com/Kesuhi/K8basSkyblockUtility/releases/latest)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-62B47A)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBB47E)
![Client-side](https://img.shields.io/badge/client--side-only-4C9A2A)
[![License CC0-1.0](https://img.shields.io/badge/license-CC0--1.0-lightgrey)](LICENSE)

</div>

K8bas Skyblock Utility is not affiliated with or endorsed by Hypixel. This README describes
version **1.2.0**.

## Getting Started

1. **Install** Minecraft 26.2 with [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or
   newer and Java 25. Put these mods into your `mods` folder:
   [Fabric API](https://modrinth.com/mod/fabric-api),
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) and
   [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) (the Fabric build).
   [Mod Menu](https://modrinth.com/mod/modmenu) is optional. Then put
   `k8bas_skyblock_utility-1.2.0+26.2.jar` from
   [Releases](https://github.com/Kesuhi/K8basSkyblockUtility/releases/latest) into the same folder.
2. **Check the download (optional):** `sha256sum -c k8bas_skyblock_utility-1.2.0+26.2.jar.sha256`,
   or on Windows `certutil -hashfile k8bas_skyblock_utility-1.2.0+26.2.jar SHA256` and compare with
   the `.sha256` file next to the jar.
3. **Open the settings** with `/ksu`, the "Open Settings" key or Mod Menu.
4. **Add a rule:** under **Highlights**, press **Add from database** on the Mob Highlighter card,
   search for a mob and press **Add**. For an NPC, do the same on the NPC Search card under
   **Waypoints**: an NPC at a fixed spot gets a waypoint, a moving NPC gets an outline rule. Changes
   apply at once and are saved when you close the screen; there is no Save button.

Coming from an older version? Read [Upgrading](#upgrading) first.

## Features and defaults

A fresh install has no rules, so nothing is outlined or marked until you add one. Every option is
on the settings screen; its search box (or `/ksu <text>`) finds options and rules by name.

| Feature | Where | Default | What it does |
|---|---|---|---|
| **Mob Highlighter** | Highlights | ON, no rules | Outlines the mobs your rules match, in each rule's colour, only where you can see them. |
| Mob scan range | Highlights | 64 blocks | How far mobs and moving NPCs are checked (0 = unlimited, 0–128). |
| **NPC Search** | Waypoints | ON, no rules | Outlines the moving NPCs your rules match while they are in view, and turns the NPC waypoints on. |
| NPC waypoints | Waypoints | ON for each NPC you add | A white name label with a yellow distance line at the NPC's fixed spot. |
| Show beacon beams | Waypoints | ON | A vanilla-style beam from each waypoint, in its island's colour; terrain hides it. |
| Show distance | Waypoints | ON | The distance line under each waypoint label. |
| "You found" title | Waypoints | ON | "You found \<NPC>" the first time you see Trinity, Tomioka, Duncan, Xalx or Pete, once per run, after you add them to NPC Search. |
| Accent colour | General › Interface | teal `#29B6B2` | The colour of selections, focus and section titles. |
| Smooth corners | General › Interface | OFF | Anti-aliased rounded corners on the mod's screens and notices. |
| Notice position, duration | General › Interface | top right, 5 s | Where the mod's notices appear and how long they stay. |
| Edit HUD layout | General › HUD | | Opens the HUD editor. 1.2.0 has no HUD elements yet, so it opens empty. |
| Keybinds | General › Keybinds | unbound | Open Settings, Toggle Mob Highlighter, Toggle NPC Search (also in Controls › Key Binds). |
| Check for updates (notify) | General › Updates | ON | One chat line when a newer version is out. Nothing is downloaded. |
| Rule warnings | every rule | always on | A rule that cannot work shows ⚠ and the reason on its card, and stays inactive. |
| Settings backups | none | always on | A broken or newer settings file is copied to a `.bak` file first; chat tells you. |
| Debug dumps | `/ksu debug` | off until run | Write what the game already shows into `logs/latest.log` for bug reports. |

Rules you add from a database show only what you can change (on/off, and type and colour for a
mob); **Remove** on the rule's header deletes it. Where two mob rules match, the first one wins. The
settings screen also works from the keyboard (Tab, arrows, Space/Enter, Esc).

> **[Screenshot placeholder: the settings screen]** · **[Screenshot placeholder: an outlined mob]** ·
> **[Screenshot placeholder: an NPC waypoint with its beacon beam]** ·
> **[Screenshot placeholder: the "You found \<NPC>" title]** ·
> **[Screenshot placeholder: the HUD editor]**

## Commands

| Command | What it does |
|---|---|
| `/ksu` (also `/kskyblockutility`) | Opens the settings. |
| `/ksu <text>` | Opens the settings with `<text>` searched, for example `/ksu beam`. Text starting with `hud`, `debug`, `update` or `sbxp` is read as a subcommand instead. |
| `/ksu hud` | Opens the HUD editor. |
| `/ksu debug dump tab` / `sidebar` / `entities` | Writes the tab list, the sidebar, or the name tags within about 8 blocks to `logs/latest.log`. |
| `/ksu debug dump containers on` / `off` | While on, each supported SkyBlock menu you open is written to `logs/latest.log`; it switches itself off after 60 minutes. |

Dump lines are tagged `[K8BAS-DUMP]` and nothing is sent anywhere. Your name and the names of the
players in your tab list or near you are replaced by Self, Player1, Player2 and so on; other names in
menu or sidebar text stay, so check a dump before you share it.

## Supported version

**Minecraft 26.2 (Fabric).** Java 25, Fabric Loader 0.19.5 or newer, Fabric API 0.161.0 or newer,
Fabric Language Kotlin 1.14.1 or newer, Hypixel Mod API 1.0.2 or newer (its build labelled `mc26.1`
also runs on 26.2), Mod Menu optional. Render Chest 1.0.3 is bundled inside the jar. Minecraft
26.1.x is no longer supported; 1.0.1 was the last build for it.

## Upgrading

**From 1.1.0:**
- Install [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.14.1 or
  newer; without it, Fabric stops the game with a message naming it. Cloth Config is no longer
  needed, so you can remove it unless another mod needs it.
- Install 1.2.0 by hand: 1.1.0's update check only tells you about it. Replace the jar and restart.
- Your settings and rules are kept. What behaves differently:
  - Settings apply as you change them and are saved when the screen closes, also with Esc.
  - The categories are General, Highlights and Waypoints; the mob scan range is on the Mob
    Highlighter card.
  - Waypoint labels are always white with a yellow distance line, without a dark background, on the
    centre of the NPC's block (up to half a block from before). "White waypoint labels" is gone.
  - NPCs have no colour of their own: their outlines, labels and the "You found" title are white.
    The beam carries the island's colour.
  - A waypoint added from the NPC list follows that NPC's corrected position in the list.
  - New rules come from **Add from database**; rules you wrote by hand keep every field.

**From 1.0.x:** move your instance to Minecraft 26.2 first (see [Supported version](#supported-version)),
with the mods listed there, then install 1.2.0 by hand: 1.0.x's updater asks a Modrinth project
that returns 404 (not found), so it never finds a newer version. Your
settings migrate on the first start, and the old file is kept as
`config/k8bas_skyblock_utility.json.v0.bak`. Everything under "From 1.1.0" applies to you too. Since
1.1.0, the outline is visible-only, invisible mobs are never outlined, the "You found" title waits for
line of sight and is shown only for Trinity, Tomioka, Duncan, Xalx and Pete, the dungeon lobby is its
own island ("Dungeon Hub"), and the update check only notifies. The [changelog](CHANGELOG.md) lists
every change with its reason.

## Fair play and compliance

K8bas Skyblock Utility is client-side and display-only: it changes what your own game shows and
never plays for you.

1. **No hidden entity is outlined.** Blocks hide every outline, and there is no option to change
   that. Glow the server itself adds is left as the game shows it.
2. **Invisible entities are never highlighted or announced**, whatever your rules say.
3. **Alerts about entities wait for line of sight.** The "You found" title appears only once you
   can see the NPC.
4. **Only fixed coordinates show behind blocks.** NPC waypoint labels and their distance sit at known,
   fixed spots; they are the only thing drawn behind blocks. Terrain hides the beams.
5. **Network packets are only read**, never cancelled, delayed or changed. The mod never sends chat
   messages or commands on its own.
6. **Every feature that shows something has its own switch**, and the ones that need care explain
   themselves in a tooltip. Every default is listed above.
7. **Plain wording:** no feature name suggests seeing hidden things, unfair advantages or betting.

Left out on purpose, now and later: a glow option for mobs out of line of sight, showing invisible
entities, keeping drill mining progress by holding back the packets the game sends, automatic party
chat messages, cancelling particle packets, and filtering possible corpse spots by entities you
cannot see.

## Network access

| Host | When | What for | How to stop it |
|---|---|---|---|
| `api.github.com` | at game start and while you play: at most 4 times a day, and not again for 6 hours after a successful check | the update check: reads this repository's release list; nothing is downloaded | General › Updates, "Check for updates (notify)" OFF |
| `gist.githubusercontent.com` | once per game start; gives up after 10 seconds | downloads the mob and NPC lists behind **Add from database** | no switch yet; the lists will be bundled with the mod later |
| the Hypixel server, through the Hypixel Mod API | while you are on Hypixel | reads which server and mode you are on, over your normal game connection | no switch; part of your game connection |

Nothing else is contacted. The requests carry a User-Agent naming the mod (for the update check also
its version) and nothing about you: no player name, no UUID, no telemetry. The links in the update
message open `github.com` in your browser; while the game's "Prompt on Links" chat setting is on (the
default), it asks you to confirm first.

## Licensing and credits

- This mod is released under **CC0-1.0**; see [LICENSE](LICENSE). It started from the official
  Fabric example mod template (CC0).
- The jar bundles **Render Chest** 1.0.3 by AzureAaron, unmodified, under the **Apache License 2.0**;
  see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Thanks to the Skyblocker and SkyHanni projects: their visible-only outlines showed how to keep
  highlights within Hypixel's rules, and the waypoints look like Skyblocker's. No code from either is
  used.

## Feedback and Bugs

Open an [issue](https://github.com/Kesuhi/K8basSkyblockUtility/issues) or message me on Discord
(@disable.rx). Say which version you run, and if the game crashed, attach the crash report or
`logs/latest.log`. For display problems, a [debug dump](#commands) helps.

## Building

```bash
./gradlew build
```

It needs JDK 25, runs the unit tests and writes `build/libs/k8bas_skyblock_utility-1.2.0+26.2.jar`
and its `.sha256` file. For IDE setup, see the
[Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

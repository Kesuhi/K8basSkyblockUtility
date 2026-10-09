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

## Settings

The settings screen has a category list down the side and a search box at the top that finds every
option and rule (`/ksu <text>` opens it with the text searched). Each category shows its sections as
cards, in this order. A fresh install has no rules, so nothing is outlined or marked until you add one.

### General

*Look of this screen, notices, keybinds and updates.*

**Interface**

| Setting | Default | What it does |
|---|---|---|
| Accent colour | teal `#29B6B2` | The colour of selections, focus and section titles. |
| Smooth corners | OFF | Draws rounded corners with soft edges, on this screen, the HUD editor and notices. |
| Notice position | Top right | Where the mod's notices appear on the screen. |
| Notice duration | 5 s | How long a notice stays on screen (1–15 s). |

**HUD**

| Setting | Default | What it does |
|---|---|---|
| Edit HUD layout | | Opens the HUD editor (also `/ksu hud`). 1.2.0 has no HUD elements yet, so it opens empty. |

**Keybinds**

| Setting | Default | What it does |
|---|---|---|
| Open Settings Key | unbound | Opens this screen. |
| Toggle Mob Highlighter Key | unbound | Turns Mob Highlighter on or off. |
| Toggle NPC Search Key | unbound | Turns NPC Search on or off. |

The keys are also in Minecraft's Options › Controls › Key Binds, under "K8bas Skyblock Utility".

**Updates**

| Setting | Default | What it does |
|---|---|---|
| Check for updates (notify) | ON | Tells you in chat when a newer version is out. Nothing is downloaded or installed. |

### Highlights

*Outlines for the mobs you choose, seen only where they are in view.*

**Mob Highlighter**

| Setting | Default | What it does |
|---|---|---|
| Mob Highlighter | ON | Outlines mobs that match your rules while you can see them, in each rule's colour. |
| Mob scan range | 64 blocks | How far around you mobs are checked (0 = unlimited, 0–128). |
| Add from database | | Pick a mob from the Mob Database; its rule joins the list below. |

Each rule is a card you open to edit: on/off, and for a mob from the database its entity type and
colour. **Remove** on the rule's header deletes it. Where two rules match the same mob, the first one
wins. A rule that cannot work shows ⚠ and the reason on its card, and stays inactive.

### Waypoints

*Find NPCs: waypoints at fixed spots, outlines on NPCs by name, and the "You found" title.*

**NPC Search**

| Setting | Default | What it does |
|---|---|---|
| NPC Search | ON | Outlines the moving NPCs from your list while you can see them, and marks fixed NPC positions with waypoints. |
| "You found" title | ON | A title the first time you see Trinity, Tomioka, Duncan, Xalx or Pete, once per run, after you add them here. |
| Show beacon beams | ON | A beacon beam rises from each waypoint, in its island's colour; terrain hides it. |
| Show distance | ON | A second line under each waypoint label with your distance in metres. |
| Add from database | | Pick an NPC from the NPC Database; its rule joins the list below. |

An NPC at a fixed spot gets a waypoint: a white name label with a yellow distance line at its spot,
and the beam. A moving NPC gets a white outline. Each NPC's card has its own on/off switch and a
**Remove** button.

The settings screen also works from the keyboard: Tab and Shift+Tab move through it, Space or Enter
switches or opens, the arrow keys change sliders, dropdowns and categories, and Esc closes it. Your
settings are saved to `config/k8bas_skyblock_utility.json`; a broken file is copied to a `.bak`
file first, and a chat message tells you.

## Commands

| Command | What it does |
|---|---|
| `/ksu` (also `/kskyblockutility`) | Opens the settings. |
| `/ksu <text>` | Opens the settings with `<text>` searched, for example `/ksu beam`. |
| `/ksu hud` | Opens the HUD editor. |

## Supported version

**Minecraft 26.2 (Fabric).** Java 25, Fabric Loader 0.19.5 or newer, Fabric API 0.161.0 or newer,
Fabric Language Kotlin 1.14.1 or newer, Hypixel Mod API 1.0.2 or newer (its build labelled `mc26.1`
also runs on 26.2), Mod Menu optional. Render Chest 1.0.3 is bundled inside the jar. Minecraft
26.1.x is no longer supported; 1.0.1 was the last build for it.

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
`logs/latest.log`.

## Building

```bash
./gradlew build
```

It needs JDK 25, runs the unit tests and writes `build/libs/k8bas_skyblock_utility-1.2.0+26.2.jar`
and its `.sha256` file. For IDE setup, see the
[Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

<div align="center">

<img src="src/main/resources/assets/k8bas_skyblock_utility/icon.png" alt="K8bas Skyblock Utility" width="128">

# K8bas Skyblock Utility

**A client-side Fabric mod for Hypixel SkyBlock: outlines for the mobs and NPCs you are looking for,
waypoints with beacon beams for NPCs at fixed spots, helpers for Glacite Mineshafts and fishing
hotspots, and a settings screen that finds every option.**

[![Latest release](https://img.shields.io/github/v/release/Kesuhi/K8basSkyblockUtility?label=release&color=29B6B2)](https://github.com/Kesuhi/K8basSkyblockUtility/releases/latest)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-62B47A)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBB47E)
![Client-side](https://img.shields.io/badge/client--side-only-4C9A2A)
[![License CC0-1.0](https://img.shields.io/badge/license-CC0--1.0-lightgrey)](LICENSE)

</div>

K8bas Skyblock Utility is not affiliated with or endorsed by Hypixel. This README describes
version **1.2.1**.

## Getting Started

1. **Install** Minecraft 26.2 with [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or
   newer and Java 25. Put these mods into your `mods` folder:
   [Fabric API](https://modrinth.com/mod/fabric-api),
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) and
   [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) (the Fabric build).
   [Mod Menu](https://modrinth.com/mod/modmenu) is optional. Then put
   `k8bas_skyblock_utility-1.2.1+26.2.jar` from
   [Releases](https://github.com/Kesuhi/K8basSkyblockUtility/releases/latest) into the same folder.
2. **Check the download (optional):** `sha256sum -c k8bas_skyblock_utility-1.2.1+26.2.jar.sha256`,
   or on Windows `certutil -hashfile k8bas_skyblock_utility-1.2.1+26.2.jar SHA256` and compare with
   the `.sha256` file next to the jar.
3. **Open the settings** with `/ksu`, the "Open Settings" key or Mod Menu.
4. **Add a rule:** under **Highlights**, press **Add from database** on the Mob Highlighter card,
   search for a mob and press **Add**. For an NPC, do the same on the NPC Search card under
   **Waypoints**: an NPC at a fixed spot gets a waypoint, a moving NPC gets an outline rule. Changes
   apply at once and are saved when you close the screen; there is no Save button.

## Settings

The settings screen has a category list down the side and a search box at the top that finds every
option and rule (`/ksu <text>` opens it with the text searched). Each category shows its sections as
cards, in this order. A fresh install has no rules, so nothing is outlined or marked under Highlights
and Waypoints until you add one; the Mining and Fishing features work without rules.

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
| Edit HUD layout | | Opens the HUD editor (also `/ksu hud`): drag an element to move it, scroll over it to scale it. In 1.2.1 the only element is the "Corpse ding" line (see Mining). |

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
| Show beacon beams | ON | A beacon beam rises from each waypoint, in the NPC's own beam colour if you gave it one, otherwise in its island's colour; terrain hides it. |
| Show distance | ON | A second line under each waypoint label with your distance in metres. |
| Add from database | | Pick an NPC from the NPC Database; its rule joins the list below. |

An NPC at a fixed spot gets a waypoint: a white name label with a yellow distance line at its spot,
and the beam. A moving NPC gets a white outline. Each NPC's card has its own on/off switch and a
**Remove** button. A fixed NPC's card also has **Beam colour**, its own beam colour (without one it
follows its island's), and **Use island colour**, which drops its own colour again.

**Island beam colours**

| Setting | Default | What it does |
|---|---|---|
| One colour per island (Hub, Private Island, …) | each island's own, for example red on the Crimson Isle | The beam colour of waypoints on that island, with a colour picker and hex input, unless the NPC has its own beam colour. |

A waypoint shown on every island has a green beam (`#0AA351`). Colour changes show on the beams at
once.

### Mining

*Alerts and fixes for mining.*

**Mineshaft entry alert**

| Setting | Default | What it does |
|---|---|---|
| Mineshaft entry alert | ON | On entering a Glacite Mineshaft: one chat line that only you see, with the shaft, its frozen corpses (Lapis, Tungsten, Umber, Vanguard, and how many are looted) and your keys. |
| Show corpse key counts | ON | How many of each needed key (Tungsten Key, Umber Key, Skeleton Key) are in your inventory and offhand; ⚠ in red when you have too few. |

The alert reads Hypixel's Frozen Corpses tab widget, never the corpses themselves. If the widget is
off or cut off, the line says so and tells you once how to turn it on. A corpse added by a Dead Man's
Chest within 30 s of entering gives one update. A key in a sack or in storage does not open a corpse,
so it is not counted. When you are short and your Dwarven Mines Sack held keys the last time you
opened it, the line says how many are there and adds a **[Take out n]** button: clicking it runs
`/getfromsacks` for just the missing keys. The mod never runs it by itself.

**Possible corpse spots**

| Setting | Default | What it does |
|---|---|---|
| Possible corpse spots | ON | In a Glacite Mineshaft, a "Possible corpse #n" label at every place a frozen corpse can spawn in that layout, shown even behind blocks. |
| Hide spots when all corpses are looted | ON | Once the Frozen Corpses tab widget lists every corpse as looted, the labels go. Without the widget they stay until you leave the shaft. |
| Organ Donor ding hint | ON | While your Organ Donor dings, a "Corpse ding" HUD line says "far", "close" or "very close" from how high the ding sounds; it goes 2 s after the last ding. |
| Remove ruled-out spots | ON | With the ding hint: once it has dinged in this shaft, a spot you spent 4 s within 16 blocks of without a ding is removed. |
| Edit position | | Opens the HUD editor to move and scale the "Corpse ding" line (top centre, below the boss bar, by default). |

The labels are known spawn spots from community data, not detected corpses: a label never tells you
whether a corpse is there. Spots are known for 29 of the 34 shaft types; in the others, a chat line
says "No spot data for" and the shaft's code. The ding plays for every unlooted corpse within 20
blocks, which is why a silent spot is ruled out. Removed spots come back when the widget lists a new
corpse, or in another shaft. `/ksu corpses` lists the spots by distance and marks removed ones
"ruled out".

### Fishing

*Fixes and helpers for fishing.*

**Bobber Fix**

| Setting | Default | What it does |
|---|---|---|
| Bobber Fix | ON | On SkyBlock, your own bobber floats smoothly instead of jumping back every moment, and it floats on lava as it does on water. |

Only how your own bobber is drawn changes: the server still decides where it is, when a fish bites
and what you catch. It applies from your next cast. Other players' bobbers and other servers stay
unchanged. If a game update or another mod blocks it, the game still starts and the card says it is
unavailable.

**Fishing Hotspots**

| Setting | Default | What it does |
|---|---|---|
| Fishing Hotspots | ON | A ring on the water or lava around each fishing hotspot whose name you can see, in its buff's colour; walls hide it. |
| Highlight ring | ON | The outline of each hotspot's area. |
| Filled area | ON | A see-through disc over each hotspot's area. |
| Fill opacity | 25 % | How strongly the filled area is drawn (0–100 %). 0 % leaves only the ring. |
| Hide Hypixel's hotspot particles | ON | Hypixel's pink (or smoke) particle ring is not drawn around a hotspot whose ring or filled area is drawn. |
| Hotspot gone warning | ON | Tells you when the hotspot you are fishing in disappears. |
| Warning title | ON | "Hotspot gone!" on screen for a few seconds, with the buff. |
| Warning sound | ON | A short sound that only you hear. |
| Sound | Note block pling | Which sound the warning plays: Note block pling, Bell, Chime, Experience orb or Anvil. |
| Sound volume | 100 % | How loud the warning sound is (0–100 %). |
| Warning chat message | ON | A chat line that only you see. |
| Also show as a notice | OFF | Also shows a notice in the corner of the screen, placed and timed under General › Interface. |

The ring colours by buff: Sea Creature Chance dark aqua, Fishing Speed aqua, Double Hook Chance blue,
Treasure and Trophy Fish gold, Shards yellow. A ring's size comes from the hotspot's own particles;
hidden particles still reach the game and are still measured. If the mod cannot read them, rings use
each island's usual size and the card says so. Rings also show with Iris shaderpacks. The warning
only counts a hotspot your bobber was in during the last 30 seconds while you are within 40 blocks
of it, so leaving the island or the server is not a warning.

The settings screen also works from the keyboard: Tab and Shift+Tab move through it, Space or Enter
switches or opens, the arrow keys change sliders, dropdowns and categories, and Esc closes it. Your
settings are saved to `config/k8bas_skyblock_utility.json`; a broken file is copied to a `.bak`
file first, and a chat message tells you.

## Commands

| Command | What it does |
|---|---|
| `/ksu` (also `/kskyblockutility`) | Opens the settings. |
| `/ksu <text>` | Opens the settings with `<text>` searched, for example `/ksu beam`. Text starting with `hud`, `debug`, `update`, `sbxp` or `corpses` runs that subcommand instead. |
| `/ksu hud` | Opens the HUD editor. |
| `/ksu corpses` | In a Glacite Mineshaft, lists the possible corpse spots by distance; ruled-out spots are marked "ruled out". |

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
- The NPC list is built from **NotEnoughUpdates-REPO** (MIT). The possible corpse spots merge data
  from **SkyHanni-REPO** (MIT; its pull request #759), **meowdding-repo** (MIT) and **ShaftUtils**
  (CC0-1.0, credited with thanks). The data ships inside the jar; the MIT notices and licence texts
  are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), `licenses/` and the jar's
  `META-INF/licenses/`.
- Thanks to **SkyOcean** for finding why the bobber jumps back: Bobber Fix follows its approach,
  written anew. Nothing of SkyOcean is bundled.
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

It needs JDK 25, runs the unit tests and writes `build/libs/k8bas_skyblock_utility-1.2.1+26.2.jar`
and its `.sha256` file. For IDE setup, see the
[Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

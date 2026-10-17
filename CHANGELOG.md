# Changelog

All notable changes to K8bas Skyblock Utility are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.3.0] - 2026-10-17

This release reworks how the settings screen, its dialogs and the HUD editor look and feel: sharp
round knobs, icons, outlines and colour wheel at every GUI scale, soft shadows, smooth scrolling and
motion, a new "Animations" switch, pointer shapes, calmer tooltips, steadier sliders, search
highlighting, and a HUD editor with its toolbar at the top and corner handles for scaling. The
features themselves work as in 1.2.1.

**Requirements:** Minecraft 26.2 (Fabric) with Java 25, Fabric Loader 0.19.5 or newer, Fabric API
0.161.0 or newer, Fabric Language Kotlin 1.14.1 or newer and Hypixel Mod API 1.0.2 or newer (its
build labelled `mc26.1` also runs on 26.2), the same as for 1.2.1. Render Chest 1.0.3 is bundled.
Mod Menu is optional.

**Upgrading from 1.2.1:**
- Install this version by hand: 1.2.1's update check only notifies. Replace
  `k8bas_skyblock_utility-1.2.1+26.2.jar` with `k8bas_skyblock_utility-1.3.0+26.2.jar` and restart.
  No other mod needs to change.
- Your settings, rules and keybinds are kept.
- "Smooth corners" is now ON unless you switched it off yourself; a choice you made stays.
- The mouse wheel no longer changes a slider: over a slider it scrolls the page. Drag the slider, click
  its track, or Tab to it and use the arrow keys.
- The new "Animations" switch (General › Interface) is ON; switch it off to see every change at once.
- The HUD editor's buttons moved from the bottom of the screen to a card at the top, and "Reset All"
  now asks for a second click.
- If you go back to 1.2.1 later, a "Smooth corners" you never picked reads as OFF there again, as it
  did in 1.2.1.

**Defaults in this version:**
- Smooth corners (General › Interface): ON.
- Animations (General › Interface): ON.
- Every other option keeps its 1.2.1 default.

**Known issues:**
- In the HUD editor, the "Corpse ding" line's default place (top centre) lies under the new toolbar
  card: its top edge is behind the buttons, and its name label is under the instructions until they
  fade. Click the part of the line that shows, or drag it out from under the card; where it shows in
  the game is unchanged.

### Added

- Soft shadows under the settings window, open dropdown lists, the colour picker, the database picker
  and tooltips, so they stand out from what lies behind them.
- "Animations" (General › Interface, ON by default): switched off, this mod's screens and notices show
  every change at once, without sliding or fading: the settings opening, a category switch, hover and
  press, switches, scrolling, lists and dialogs opening and closing, rule cards, the HUD editor's
  toolbar and hints, and notices. Tooltips still wait the same time before they show, then appear at
  once. The change applies at once, also in an open settings screen.
- The mouse pointer shows what a click does in this mod's screens: a hand over buttons, switches,
  toggle cards, the sidebar's categories, dropdowns and their entries, colour swatches, key binding
  buttons, rule headers, sliders, scrollbars, the colour picker's wheel and presets, and the database
  picker's folders and Add buttons; a text cursor over text fields; a sideways arrow while you drag a
  slider and an up-and-down arrow while you drag a scrollbar; a move arrow over the HUD editor's
  elements and a crosshair over their corner handles. Over a control that is unavailable it stays the
  normal arrow. It follows the game's "Allow Cursor Changes" option.
- While you search the settings, the matched text in each setting's name and description, and in a rule's
  name, sits on a soft box in the accent colour, so you see at once why a setting is listed.
- Cards that switch a feature on or off, rule headers and switches darken a little while you hold the mouse
  button on them, and fade back when you let go.
- A search that finds nothing shows a search glass above its message and a "Clear search" button below
  it, and a rule list without rules (Mob Highlighter, NPC Search) says where to add one.
- Scrolling areas (the settings page, the database picker, long dropdown lists) fade their content softly
  at an edge where there is more to scroll to, so you can tell there is more above or below.
- A slider with only a few values (the notice duration) shows a small dot at each of them.
- In the HUD editor, the selected element has a round handle at each corner: drag one to scale the element
  while the opposite corner stays put. It stops at 0.5 and 3.0 and at the edge of the window.
- In the HUD editor, an element's centre also lines up with other elements' centres while you drag it,
  with a guide line, unless you hold Alt.

### Changed

- "Smooth corners" (General › Interface) is now ON by default, so rounded corners have soft edges. The
  option changes only how the corners of boxes, buttons, fields and outlines are drawn: round knobs and
  filled pill shapes (switches, slider tracks, scrollbars) are always smooth now. If you switched it
  off before, it stays off.
- Slider and toggle knobs, toggle switches, slider tracks, colour dots and the colour picker's marker are
  drawn as true circles and pill shapes, with evenly soft edges at every GUI scale, odd sizes included.
  The colour picker's marker was a square before.
- The colour picker's wheel is drawn sharp at every GUI scale, with an evenly soft edge, instead of a
  small image stretched to fit. The colours you pick are the same.
- Icons are drawn sharp at every GUI scale: the search box's × and its new search glass, the arrows of
  dropdowns, rule headers and the database picker's folders, and a new check mark beside the selected
  entry of an open dropdown list.
- Outlines and thin lines are fine lines on the screen's own pixels: 1 pixel thick at GUI scales 1 and 2,
  2 at 3 and 4, 3 at 5 and 6, instead of growing with the GUI scale. This covers buttons, text fields,
  dropdowns and their lists, key binding buttons, colour swatches, the text cursor, the colour picker and
  the database picker, tooltips, notices, the settings' section lines, and the HUD editor's outlines and
  snap guides. The keyboard focus ring stays as thick as before, so it is easy to see.
- Switch knobs, slider knobs, the category switch's slide and notices move in steps of the screen's own
  pixels instead of whole GUI pixels, so they glide more smoothly at GUI scale 2 and above. Where you
  click is unchanged.
- Scrolling glides: the settings page, the sidebar, the database picker and open dropdown lists ease to
  where the mouse wheel or the keyboard sends them instead of jumping a notch at a time. Dragging the
  scrollbar still follows the mouse at once, and a click during a glide stops it. With "Animations" off
  they jump as before.
- Scrollbars are slim rounded bars set in from the edge, and widen over a faint track when you point at
  or drag them. The area you can grab takes in the widened bar, and a dropdown list's bar can be dragged too.
- The mouse wheel no longer changes sliders. Over a slider it scrolls the page or list like anywhere
  else, so scrolling never changes a setting by accident, and while you drag a slider the wheel does
  nothing. Drag a slider, click its track, or use the arrow keys after Tab to change it.
- Dropdown lists, the colour picker and the database picker fade and grow in quickly when they open and
  fade out quickly when they close, and the dimmed background behind a dialog fades with them. You can
  click them as soon as they open; what closed them (a choice, Save, Cancel, Esc) takes effect at once,
  and the next click already reaches what lies under them. With "Animations" off they appear and
  disappear at once.
- One set of corner sizes: the settings window now has rounded corners, the sidebar tabs match the
  other controls, dropdown lists have a little room above their first and below their last entry, and
  the colour picker's sliders are rounded with the same round knob as the other sliders.
- Tooltips wait until the pointer has rested on a setting for 0.4 seconds, then fade in, so they no
  longer flash up while you move across the screen. Moving on to the next setting within 0.3 seconds
  shows its tooltip at once. A tooltip now sits beside its setting, a list's entry or a button instead
  of following the pointer, so it never covers the slider or value it describes, and it hides while
  you hold the mouse button and when the page scrolls.
- The settings sidebar, the database picker and open dropdown lists light up the entry under the
  pointer smoothly, as cards and buttons already did. When you pick another category, the highlight
  with its accent bar slides from the old tab to the new one. With "Animations" off both are instant.
- The settings' sliders feel steadier: a knob no longer jumps under the pointer when you grab it off its
  middle, it grows a little while you point at or drag it, and the value next to it shows in the accent
  colour while you drag. A click elsewhere on the track still moves the knob there.
- Rule cards (Mob Highlighter, NPC Search) grow open and shrink closed in a short motion when you click
  their header or use the keys, and the arrow turns with them. Rules a search opens still open at once.
  With "Animations" off they open and close at once.
- Switches that are off stand out more from their cards.
- A slider plays its click sound on release only when you changed its value.
- In the HUD editor, elements have rounded outlines, the selected one is tinted in the accent colour and the
  one under the pointer lightly, and each element's name sits on a small dark label.
- The HUD editor's buttons and status line sit in a card at the top of the screen, so the hotbar stays
  free. The instructions show below it for a few seconds or until you first move or scale something, and
  come back while you point at the card. The card fades while you drag an element over it, and an element
  under it can still be picked up. With "Animations" off these fades happen at once.
- "Reset All" in the HUD editor asks for a second click ("Confirm reset") within 3 seconds before it
  resets every element.
- The checkerboard behind see-through colours (colour swatches, the colour picker) has smaller cells, keeps
  a swatch's rounded corners and dims with a setting that is switched off.

### Fixed

- A slider no longer shows a thin line of its coloured fill across the knob at GUI scale 2 and above,
  and the knob of a dimmed slider no longer shows the fill and the track through it. The rounded
  corners of buttons and fields keep their whole outline at GUI scale 4.

## [1.2.1] - 2026-10-15

This release brings helpers for mining in Glacite Mineshafts (an entry alert with your corpse keys,
possible corpse spots and an Organ Donor ding hint) and for fishing (hotspot rings with a warning when
yours disappears, and a smoother bobber), and beam colours you can change.

**Requirements:** Minecraft 26.2 (Fabric) with Java 25, Fabric Loader 0.19.5 or newer, Fabric API
0.161.0 or newer, Fabric Language Kotlin 1.14.1 or newer and Hypixel Mod API 1.0.2 or newer (its
build labelled `mc26.1` also runs on 26.2), the same as for 1.2.0. Render Chest 1.0.3 is bundled.
Mod Menu is optional.

**Upgrading from 1.2.0:**
- Install this version by hand: 1.2.0's update check only notifies. Replace
  `k8bas_skyblock_utility-1.2.0+26.2.jar` with `k8bas_skyblock_utility-1.2.1+26.2.jar` and restart.
  No other mod needs to change.
- Your settings, rules and keybinds are kept. The new features start ON (see the defaults below);
  they live in the new Mining and Fishing categories of the settings, where each can be turned off.
- On the first start, a fixed NPC whose colour from 1.0.x or 1.1.0 was not the default green gets
  that colour back as its beam colour. "Use island colour" on its card undoes it.
- What behaves differently (details under "Changed"): the NPC and mob lists ship inside the mod and
  are no longer downloaded; the hidden Jerries have their own "Hidden Jerries" folder; Ghost, Fels,
  Sneaky Creeper and Invisibug are no longer in the mob list; the pickers no longer offer an entry
  you already made a rule for yourself; a rule whose list entry is gone shows all its fields.
- If you go back to 1.2.0 later, it first copies the newer settings file to a `.bak` file and tells
  you in chat.

**Defaults in this version:**
- Mineshaft entry alert (Mining): ON. Show corpse key counts: ON.
- Possible corpse spots (Mining): ON. Hide spots when all corpses are looted: ON. Organ Donor ding
  hint: ON, with its "Corpse ding" HUD line at the top centre, below the boss bar. Remove ruled-out
  spots: ON.
- Bobber Fix (Fishing): ON.
- Fishing Hotspots (Fishing): ON. Highlight ring: ON. Filled area: ON. Fill opacity: 25 % (0–100 %).
  Hide Hypixel's hotspot particles: ON.
- Hotspot gone warning: ON. Warning title: ON. Warning sound: ON, with Sound "Note block pling" at
  Sound volume 100 %. Warning chat message: ON. Also show as a notice: OFF.
- Island beam colours: each island's own, as in 1.2.0 (for example red on the Crimson Isle), and
  green `#0AA351` for a waypoint shown on every island. A fixed NPC's beam follows its island's
  colour until you give it its own (or it kept one from 1.0.x or 1.1.0, see above).
- Every option that 1.2.0 had keeps its 1.2.0 default.

### Added

- Mineshaft entry alert (Mining, ON by default): when you enter a Glacite Mineshaft, one chat line
  that only you see names the shaft, lists its frozen corpses (Lapis, Tungsten, Umber, Vanguard, with
  any already looted) and shows how many of each needed key are in your inventory and offhand, with ⚠
  in red when you have too few. A corpse added by a Dead Man's Chest within 30 s gives one update. It
  reads Hypixel's Frozen Corpses tab widget; if the widget is off or cut off, the line says so and
  tells you once how to turn it on. A key in a sack or in storage does not open a corpse, so it is
  not counted; when you are short and your Dwarven Mines Sack held keys the last time you opened it,
  the line adds how many to take out and a [Take out n] button: clicking it runs /getfromsacks for
  just the missing keys. The mod never runs it by itself. "Show corpse key counts" (ON) turns the key
  part off.
- Possible corpse spots (Mining, ON by default): in a Glacite Mineshaft, a "Possible corpse #n"
  label at every place a frozen corpse can spawn in that layout (spots are known for 29 of the 34
  shaft types), shown even behind blocks. These are known spawn spots from community data
  (SkyHanni-REPO, meowdding-repo, ShaftUtils), not detected corpses. "Hide spots when all corpses are
  looted" (ON) removes them once the Frozen Corpses tab widget lists every corpse as looted. `/ksu
  corpses` lists the spots by distance. With an Organ Donor accessory, a "Corpse ding" HUD line
  (movable in the HUD editor) says "far", "close" or "very close" from how high its ding sounds
  ("Organ Donor ding hint", ON). Once it has dinged in a shaft, a spot you spent 4 s within 16 blocks
  of without a ding is removed, since the ding plays for every unlooted corpse within 20 blocks;
  removed spots come back when the widget lists a new corpse or in another shaft, and `/ksu corpses`
  marks them "ruled out" ("Remove ruled-out spots", ON).
- Beam colours you can change: Waypoints › "Island beam colours" has a colour per island (picker
  and hex), and a fixed NPC's card has its own "Beam colour" and "Use island colour". Changes show
  on the beams at once.
- Bobber Fix (Fishing, ON by default): on SkyBlock, your own bobber floats smoothly instead of
  jumping back every moment, and it floats on lava as it does on water. Only how your bobber is
  drawn changes; the server still decides bites and catches. Other players' bobbers and other
  servers are unchanged. If a game update or another mod blocks it, the game still starts and the
  card says it is unavailable. Thanks to SkyOcean for finding the cause.
- `/ksu corpses` is a command now: text after `/ksu` that starts with `corpses` runs it instead of
  searching the settings; search `corpse` to find the corpse settings.
- `/ksu debug data`: lists the data tables bundled in the mod and their versions, in chat only.
- `/ksu debug dump sounds on|off`: for 10 minutes, every sound the game plays is written to the log
  with its name, place, volume and pitch, to identify sounds such as the Organ Donor ding. It only
  listens; no sound is changed and nothing is sent.
- Fishing Hotspots (Fishing, ON by default): on every SkyBlock island, each fishing hotspot whose
  name you can see gets a ring on the water or lava in its buff's colour (Sea Creature Chance dark
  aqua, Fishing Speed aqua, Double Hook Chance blue, Treasure and Trophy Fish gold, Shards yellow),
  with a see-through filled area. Walls hide the ring. Its size comes from the hotspot's own
  particles. "Highlight ring", "Filled area" and "Fill opacity" change the look. Rings also show with
  Iris shaderpacks. "Hide Hypixel's hotspot particles" (ON) stops drawing Hypixel's own pink or smoke
  particle ring around a hotspot with a ring; the game still receives them, and the ring's size is
  still measured from them.
- Hotspot gone warning (ON): when the hotspot you are fishing in disappears, you get "Hotspot
  gone!" on screen with its buff, a sound (choose one and its volume) and a chat line, each with
  its own switch; a notice can be added. It only counts a hotspot your bobber was in during the
  last 30 seconds while you are within 40 blocks, so leaving the island or flying away is not a
  warning. Only you see or hear it.

### Changed

- A fixed NPC whose colour from 1.0.x or 1.1.0 was not the default green now uses that colour for
  its beam (in 1.2.0 its beam had the island colour), so no colour you picked is lost; NPCs on the
  default green keep their island's colour. "Use island colour" on its card undoes it.
- The NPC and mob lists for "Add from database" now ship inside the mod. They are no longer
  downloaded at start, so both pickers work offline and on the first launch, and the mod makes no
  request to GitHub gists any more. Apart from the corrections in this section (merged duplicates,
  updated positions, the Hidden Jerries folder, the mineshaft entries and the removed invisible mobs),
  they hold the same entries as the lists 1.2.0 downloaded.
- The four hidden Jerries (Blue, Green, Purple and Golden Jerry) are now in a mob folder called
  "Hidden Jerries" instead of "Jerry", which is also the name of Jerry's Workshop. Rules you made
  from them keep working on every island.
- The NPC list is built from NotEnoughUpdates-REPO (MIT); `THIRD_PARTY_NOTICES.md` and the jar's
  `META-INF/licenses/` now carry its notice and licence.
- Ghost, Fels, Sneaky Creeper and Invisibug are no longer in the mob list: they are invisible by
  design, and the mod never outlines invisible mobs, so a rule for them would do nothing.
- The pickers treat an entry as already added when one of your own rules has the same name pattern
  and island (for example a Trinity rule you made yourself), and no longer offer it again.
- A rule whose list entry no longer exists shows all its fields again, like a rule you made yourself;
  it keeps working with the values it has.

### Fixed

- NPCs listed twice in the NPC list are merged: Bramass Beastslayer (also listed as "Bestiary"),
  Hendrik, the Fishing Merchant (an old spot from before the Hub rebuild) and the Forge Foreman (also
  listed as "Fred"). Waypoints you added from either entry keep working and now point at the right
  block; searching the picker for the old name still finds the NPC.
- Gemma's and Scoop's waypoints point at their current positions.
- When the mod reads a mob's or NPC's name, it skips "CLICK" labels, empty lines and hidden names, so an
  NPC is matched by its name even where its "CLICK" label floats nearer than the name.
- Glacite Bowman, Caver, Mage, Mutt and Littlefoot are also listed under "Glacite Mineshafts", as
  "Glacite Bowman (Mineshafts)" and so on, so a rule added from there works inside a mineshaft.
- If you added an NPC twice under two of its names (for example "Fred" and "Forge Foreman"), its
  waypoint is drawn once. Both rules stay in your settings.

## [1.2.0] - 2026-10-09

This release brings the mod's own settings screen, with a search that finds every option and rule,
and beacon beams for NPC waypoints.

**Requirements:** Minecraft 26.2 (Fabric) with Java 25, Fabric Loader 0.19.5 or newer, Fabric API
0.161.0 or newer, Fabric Language Kotlin 1.14.1 or newer (new) and Hypixel Mod API 1.0.2 or newer
(its build labelled `mc26.1` also runs on 26.2). Cloth Config is no longer needed. Render Chest
1.0.3 is bundled. Mod Menu is optional.

**Upgrading from 1.1.0:**
- Install Fabric Language Kotlin 1.14.1 or newer. You can remove Cloth Config unless another mod
  needs it.
- Install this version by hand: 1.1.0's update check only notifies. Replace
  `k8bas_skyblock_utility-1.1.0+26.2.jar` with `k8bas_skyblock_utility-1.2.0+26.2.jar` and restart.
- Your settings, rules and keybinds are kept.
- What behaves differently (details, reasons and effects under "Changed"): settings apply as you
  change them; the categories are General, Highlights and Waypoints; waypoint labels are always
  white with a yellow distance line and sit on the block's centre; NPCs have no colour of their own;
  rule cards from a database are slimmer and have a Remove button; waypoints from the NPC list follow
  its corrected positions.
- Coming from 1.0.x: move to Minecraft 26.2 with the requirements above first; the README's
  "Upgrading" section has the steps.

**Defaults in this version:**
- Mob Highlighter: ON, with the rules you add. Mob scan range: 64 blocks (0 = unlimited, 0–128).
- NPC Search: ON, with the rules you add. NPC waypoints: ON for each NPC you add.
- Show beacon beams: ON. Show distance: ON. "You found" title: ON (Trinity, Tomioka, Duncan, Xalx
  and Pete once you add them to NPC Search, after line of sight, once per run).
- Beam colour: each island's own (for example red on the Crimson Isle), green for a waypoint shown
  on every island. It cannot be changed in game yet.
- Accent colour: teal `#29B6B2`. Smooth corners: OFF. Notice position: top right. Notice duration:
  5 s.
- Check for updates (notify): ON. Channel: STABLE, or BETA when you run a pre-release build.
- Keybinds (Open Settings, Toggle Mob Highlighter, Toggle NPC Search): unbound.

### Added

- **A new settings screen.** `/ksu`, the "Open Settings" key and Mod Menu open the mod's own
  settings screen: categories down the side, a search box that finds every option and rule, rules
  as cards you open and edit in place, and **Add from database** with island folders and a search
  (mobs and NPCs you already added are not offered again).
- **`/ksu <text>` opens the settings with `<text>` searched**, for example `/ksu glow`. Text that starts
  with `hud`, `debug`, `update` or `sbxp` is read as a subcommand instead; search `updates` for the update
  check.
- **Look of the settings screen:** an **Accent colour** (default teal `#29B6B2`) in General ›
  Interface colours selections, focus and section titles.
- **Notices:** short messages in a corner of the screen, placed by **Notice position** (default top
  right) and shown for **Notice duration** (default 5 s), both in General › Interface. In this
  version, a notice tells you when the settings file was backed up as you open the settings.
- **A little motion in the settings screen.** It opens with a short zoom, and switching category slides
  and fades the new page in. Both take about a fifth of a second, and clicks land where things are drawn
  while they play.
- **The settings screen works from the keyboard.** Tab and Shift+Tab move through the sidebar, the search
  box and every setting, which scrolls into view; Space or Enter switches, opens or presses it; the arrow keys
  move a slider, pick in a dropdown, open or close a rule and change the category; Delete resets a key
  binding. A ring in the accent colour shows where you are. Esc works as before.
- **HUD editor**, for the on-screen elements of later versions. `/ksu hud` or **Edit HUD layout** in
  General › HUD opens it: drag an element to move it, scroll to scale it (0.5–3.0), use the arrow keys to
  nudge. Esc or Save keeps the layout; Cancel undoes the session. While you drag, an element snaps to the
  screen's centre lines and edges and to the other elements' edges when it comes within 4 px, and a thin line
  shows where; hold Alt to place it freely. This version has no HUD elements yet, so the editor opens empty.
- **NPC waypoints get a beacon beam.** Each fixed NPC you add gets a beam from its block up to the
  top of the world, in its island's colour (each island has its own; green for a waypoint shown on
  every island). Like a vanilla beacon's, the beam is animated, terrain in front of it hides it,
  and it widens with distance so you can find it from far away (not while you look through a
  spyglass). New NPC Search options **Show beacon beams** and **Show distance** (both default ON).
- **Smooth corners (optional).** A new **Smooth corners** setting in General › Interface (off by
  default) draws the rounded corners of the settings screen, the HUD editor and the notices with soft,
  anti-aliased edges instead of whole pixels.

### Changed

- **New requirement: [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).** The settings
  screen is now written in Kotlin, so the mod needs Fabric Language Kotlin 1.14.1 or newer; many SkyBlock
  mods (SkyHanni, for example) already need it. Without it, Fabric stops the game with a message naming it.
- **Cloth Config is no longer needed.** The settings screen is the mod's own, so the mod no longer
  depends on Cloth Config. You can remove it unless another mod needs it.
- **Behaviour change: new categories.** The settings are grouped as General, Highlights (Mob Highlighter)
  and Waypoints (NPC Search), and the mob scan range moved from General to the Mob Highlighter card.
  *Reason:* the screen groups features by what they help you do. *Effect:* the same settings, in new
  places; the search box finds each of them.
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
  open it first. *Effect:* rules you wrote by hand (no database entry) still show every field; new
  rules are added with **Add from database**.
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

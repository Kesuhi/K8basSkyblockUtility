# G1 captures, 2026-10-01

Sanitised format captures from the Checkpoint G1 smoke (PLAN.md), for parser tests (REQ-GS-13, REQ-GS-14).
Use them in place of the provisional fixtures marked UNVERIFIED wherever they cover the same format.

## Provenance

- **Source:** the `[K8BAS-DUMP]` lines of the maintainer's `latest.log` from the G1 smoke on 2026-10-01, written between 23:11 and 23:22 local time.
- **Setup:** Minecraft 26.2, Fabric Loader 0.19.5, K8bas Skyblock Utility 1.1.0+26.2, on Hypixel SkyBlock. The captures came from `/ksu debug dump entities`, `/ksu debug dump tab`, `/ksu debug dump sidebar` and `/ksu debug dump containers on`.
- **What was copied:** only lines tagged `[K8BAS-DUMP]`. Chat, other mods' output and connection lines were not copied, and the raw log is not committed.
- **File names:** the mod's `Location:` log lines (island and mode only) and the `Area:` tab row say where each capture was taken.

## Format

Each file holds one dump line per line, in UTF-8, exactly as the mod logged it. Only the logger prefix `[HH:MM:SS] [Render thread/INFO]: ` was removed, apart from the replacements listed under [Sanitisation](#sanitisation). These are kept as logged:

- `§` formatting codes,
- Hypixel's private-use glyphs, such as the stat icons and the location marker,
- trailing spaces.

| Kind | Line shape |
|---|---|
| tab | `[K8BAS-DUMP] tab <idx> order=<n> name=<profile name> \| <displayed text>` |
| sidebar | `[K8BAS-DUMP] sidebar title \| <title>`, then `[K8BAS-DUMP] sidebar <idx> score=<n> \| <prefix + entry + suffix>`, top to bottom |
| entities | `[K8BAS-DUMP] entities <idx> type=<id> invisible=<bool> pos=<x>,<y>,<z> below=<id or -> dy=<dy or -> \| <name tag>`, nearest first |
| container | `[K8BAS-DUMP] container <capture> title=<title> slots=<n> state=<id>`, then per non-empty slot `… slot <i> item=<id> sbid=<SkyBlock id or -> count=<n> \| <name>` and `… slot <i> lore <j> \| <lore line>` |

Notes on reading the lines:

- **Tab rows** are in the client's list order, and every row has `order=0`. Hypixel's widget rows use the fake profile names `!A-a` … `!D-t`: sorted by that name, they give the four tab columns top to bottom.
- **Tab entry count:** both tab captures have exactly 80 widget rows. Each real player adds one row with an empty text (`name=Self`, `name=Player17`, …).
- **§ codes:** tab and container texts are `Component.getString()` output and carry no `§` codes. Only the sidebar has them, from Hypixel's team prefixes and suffixes, which also split words (for example `§bGlacite M§q§bineshafts`).
- **Menu titles:** Hypixel cuts long menu titles, here after 31 or 32 characters (for example `Event ➜ Fishing Festival Perk S`), and some cut titles end in a space. The file names follow the logged title.

## Files

Fixture files: 87, with 16,777 lines and 1,158,625 bytes (about 1.1 MB). This README is not included in those totals.

### Tab, sidebar and entities

| File | Lines | Taken in | Contents |
|---|---|---|---|
| `entities-dungeon-hub.txt` | 5 | Dungeon Hub, next to Mort | name tags within 8 blocks: `CLICK`, `Mort`, `GATE KEEPER` over the NPC body (`below=minecraft:player`), `Adventurer Saul`, `CLICK` |
| `tab-dwarven-mines.txt` | 103 | Dwarven Mines, 23 players | widgets Info (Area, Server, Gems, Fairy Souls), Profile, Pet, Daily Quests, Forges, Commissions, Pickaxe Ability, Powders, Skills, Event Trackers, Bestiary |
| `tab-mineshaft.txt` | 81 | Glacite Mineshaft (shaft `UMBE_1`), alone | widgets Info (Area, Server, Gems, Scrap), Profile, Pet, Stats, Commissions, Powders, Frozen Corpses, Crystals, Bestiary, Pickaxe Ability |
| `sidebar-mineshaft.txt` | 11 | the same shaft | date, server and shaft code line, Cold, Event Bonus |
| `sidebar-dungeon-hub.txt` | 14 | Dungeon Hub | date and server line, Purse, Bits, Objective |

### Menus (`containers/`)

All menus were opened in the Dungeon Hub. Each file starts with the header line of the first capture that had this content, and it keeps that capture's number and state id.

| File | Logged title | Lines | Note |
|---|---|---|---|
| `skyblock-leveling-a.txt` | SkyBlock Leveling | 142 | slot 48 is a glass pane with no "Go Back", as after `/sblevels` (the first capture) |
| `skyblock-leveling-b.txt` | SkyBlock Leveling | 143 | slot 48 is "Go Back" with the lore "To SkyBlock Menu" |
| `ways-to-level-up-a.txt` … `-j.txt` | Ways to Level Up | 344–363 each | 10 files, identical except the "Recently Viewed ➜" row (slots 38–42, the last five task pages opened) |
| `skill-related-tasks.txt` | Skill Related Tasks | 125 | Mining, Farming, Fishing, Foraging Tasks |
| `tasks-core.txt` | Tasks ➜ Core | 224 | |
| `tasks-event.txt` | Tasks ➜ Event | 293 | |
| `tasks-dungeon.txt` | Tasks ➜ Dungeon | 105 | |
| `tasks-essence-shop.txt` | Tasks ➜ Essence Shop | 271 | |
| `tasks-slaying.txt` | Tasks ➜ Slaying | 222 | |
| `tasks-mining.txt` | Tasks ➜ Mining | 214 | |
| `tasks-farming.txt` | Tasks ➜ Farming | 255 | |
| `tasks-fishing.txt` | Tasks ➜ Fishing | 155 | |
| `tasks-foraging.txt` | Tasks ➜ Foraging | 143 | |
| `tasks-miscellaneous.txt` | Tasks ➜ Miscellaneous | 348 | |
| `tasks-story.txt` | Tasks ➜ Story | 119 | |
| `tasks-consumables-page-1.txt` | Tasks ➜ Consumables | 352 | page 1 of 2 |
| `tasks-consumables-page-2.txt` | Tasks ➜ Consumables | 113 | page 2 of 2 |
| `core-bank-upgrades.txt` | Core ➜ Bank Upgrades | 97 | |
| `core-fast-travels-unlocked-page-1.txt` | Core ➜ Fast Travels Unlocked | 197 | page 1; page 2 not captured |
| `event-spooky-festival.txt` | Event ➜ Spooky Festival | 97 | |
| `event-hoppitys-hunt.txt` | Event ➜ Hoppity's Hunt | 94 | |
| `event-jacobs-farming-contest.txt` | Event ➜ Jacob's Farming Contest | 155 | |
| `event-fishing-festival-perk-s.txt` | Event ➜ Fishing Festival Perk S | 114 | |
| `event-mining-fiesta-perk-shop.txt` | Event ➜ Mining Fiesta Perk Shop | 114 | |
| `event-mythological-ritual-per.txt` | Event ➜ Mythological Ritual Per | 114 | |
| `event-spooky-festival-perk-sh.txt` | Event ➜ Spooky Festival Perk Sh | 114 | |
| `event-season-of-jerry-perk-sh.txt` | Event ➜ Season of Jerry Perk Sh | 114 | |
| `event-harvest-feast-perk-shop.txt` | Event ➜ Harvest Feast Perk Shop | 114 | |
| `event-year-of-the-witch.txt` | Event ➜ Year of the Witch | 196 | |
| `dungeon-complete-dungeons.txt` | Dungeon ➜ Complete Dungeons | 92 | |
| `essence-shop-dragon-essence-s.txt` | Essence Shop ➜ Dragon Essence S | 207 | |
| `essence-shop-ice-essence-shop.txt` | Essence Shop ➜ Ice Essence Shop | 136 | |
| `essence-shop-spider-essence-s.txt` | Essence Shop ➜ Spider Essence S | 138 | |
| `essence-shop-undead-essence-s.txt` | Essence Shop ➜ Undead Essence S | 187 | |
| `essence-shop-wither-essence-s.txt` | Essence Shop ➜ Wither Essence S | 169 | |
| `essence-shop-crimson-essence.txt` | Essence Shop ➜ Crimson Essence␣ | 193 | the title ends in a space |
| `essence-shop-gold-essence-shop.txt` | Essence Shop ➜ Gold Essence Shop | 159 | |
| `essence-shop-diamond-essence.txt` | Essence Shop ➜ Diamond Essence␣ | 162 | the title ends in a space |
| `essence-shop-forest-essence-s.txt` | Essence Shop ➜ Forest Essence S | 155 | |
| `essence-shop-fossil-essence-s.txt` | Essence Shop ➜ Fossil Essence S | 162 | |
| `essence-shop-safari-essence-s.txt` | Essence Shop ➜ Safari Essence S | 256 | |
| `slaying-slay-dragons.txt` | Slaying ➜ Slay Dragons | 104 | |
| `slaying-defeat-slayers.txt` | Slaying ➜ Defeat Slayers | 148 | |
| `slaying-defeat-arachne.txt` | Slaying ➜ Defeat Arachne | 69 | |
| `mining-rock-milestones.txt` | Mining ➜ Rock Milestones | 90 | |
| `fishing-trophy-fish.txt` | Fishing ➜ Trophy Fish | 322 | `/sblevels` page, not the bestiary |
| `fishing-trophy-frogs.txt` | Fishing ➜ Trophy Frogs | 244 | `/sblevels` page |
| `fishing-dolphin-milestones.txt` | Fishing ➜ Dolphin Milestones | 90 | `/sblevels` page |
| `fishing-ship-parts.txt` | Fishing ➜ Ship Parts | 94 | `/sblevels` page |
| `fishing-ship-crew.txt` | Fishing ➜ Ship Crew | 82 | `/sblevels` page |
| `foraging-safari-milestones.txt` | Foraging ➜ Safari Milestones | 139 | |
| `miscellaneous-carrolyns-expo.txt` | Miscellaneous ➜ Carrolyn's Expo | 128 | |
| `miscellaneous-the-dojo.txt` | Miscellaneous ➜ The Dojo | 97 | |
| `miscellaneous-harp-songs.txt` | Miscellaneous ➜ Harp Songs | 259 | |
| `miscellaneous-abiphone-contac-page-1.txt` … `-page-4.txt` | Miscellaneous ➜ Abiphone Contac | 197–198 each | pages 1 to 4 |
| `miscellaneous-community-shop.txt` | Miscellaneous ➜ Community Shop␣ | 111 | the title ends in a space |
| `miscellaneous-personal-bank-u.txt` | Miscellaneous ➜ Personal Bank U | 76 | |
| `miscellaneous-mage-reputation.txt` | Miscellaneous ➜ Mage Reputation | 83 | |
| `miscellaneous-barbarian-reput.txt` | Miscellaneous ➜ Barbarian Reput | 83 | |
| `story-complete-objectives-page-1.txt` … `-page-3.txt` | Story ➜ Complete Objectives | 242, 240, 87 | pages 1 to 3. In the only page 2 capture, slot 44 (Next Page) is empty because the player had just clicked it |
| `story-chapters-a.txt` | Story ➜ Chapters | 111 | "Go Back" lore "To Tasks ➜ Story" |
| `story-chapters-b.txt` | Story ➜ Chapters | 111 | "Go Back" lore "To Ways to Level Up" (opened from Recently Viewed) |
| `your-skills.txt` | Your Skills | 185 | the Skills menu |
| `collections.txt` | Collections | 129 | top level only |
| `bestiary.txt` | Bestiary | 315 | top level only |
| `heart-of-the-mountain.txt` | Heart of the Mountain | 435 | the default view (Tier 1–5, "Scroll Up") |
| `heart-of-the-forest.txt` | Heart of the Forest | 365 | the default view (Tier 1–5, "Scroll Up") |
| `pets-page-1-of-3.txt` | (1/3) Pets | 1025 | page 1 of 3; pages 2 and 3 not captured |

### How the 470 menu captures became 82 files

1. **Identical captures were merged.** Captures with the same rows apart from the capture number and the `state=` id became one file. This left 219 distinct contents, 3.25 MB in all.
2. **Click artefacts were dropped,** which kept the folder well under 3 MB. 137 captures were the same menu as a kept capture, except that one or more slots were empty. The empty slot was the one the player had just clicked (a category, a page arrow or "Go Back"), captured while the next menu loaded. Each dropped capture's other slots match a kept file exactly, so no slot content is lost.
3. **Distinct pages and states were kept:**
   - Paged menus keep one file per page, named `-page-<n>`, with the page number taken from the "Page N" lore of the arrows.
   - Other menus with more than one distinct content are named `-a`, `-b`, …; the table above gives the difference.

## Sanitisation

| Kind | Rule | Replacements |
|---|---|---|
| Player names | The mod masked real names when it wrote the lines (REQ-GS-13, T0.4d): `Self` is the player, and `Player17` … `Player38` are that session's placeholders, which is why they do not start at 1. Every line was checked against the player names that appear elsewhere in the log (the account name and chat senders). It was also checked around rank prefixes and around Co-op, Guild, Party, Owner, Friends, Visiting and Profile. No unmasked name was found. NPC, item, mob and island names and Hypixel's fake tab rows (`!A-a` …) are kept. | 0 |
| Server ids | Every server id becomes `m000XX`, checked on the text with `§` codes stripped. Where Hypixel split the id with `§` codes, the codes stay in place: `§8m000§v§8XX` reads `m000XX` once stripped. | 4 (both sidebar date lines, both tab `Server:` rows) |
| UUIDs, profile, island and co-op ids | The rule would replace them with `00000000-0000-0000-0000-000000000000`, but the dumps contain none. | 0 |
| Leaderboard position | The `Global Ranking: #…` lore line of "Your SkyBlock Level Ranking" points at one player, so its number becomes the placeholder `#1,234`. | 2 (`skyblock-leveling-a.txt`, `-b.txt`) |
| Guild names, Discord tags, e-mail addresses, local paths, IPs | None in the dumps. | 0 |

Some values are kept on purpose: money, stats, levels and progress values, the profile's fruit name, the in-game date, and entity coordinates in the Dungeon Hub. None of these identify the player.

Two checks were run:

- Every file here passes `scripts/privacy-scan.sh --body <file>`.
- With `§` codes stripped, no file contains a server id (`(m|M|mini|mega)` + 1–4 digits + 1–3 capitals) other than `m000XX`.

## What the captures confirm (REQ-GS-14)

"Captured" means a parser test can use the named file in place of the provisional fixture. Anything marked not captured stays UNVERIFIED until a later capture (PLAN: asked again at G6).

| Provisional format (where it is marked UNVERIFIED) | Status |
|---|---|
| Frozen Corpses tab widget lines (PLAN §5, REQ-GS-17, AC-MSA-01) | **captured:** `tab-mineshaft.txt`. The header is `Frozen Corpses:`, then ` Lapis: NOT LOOTED` and ` Lapis: LOOTED` (twice); each row starts with a space, as in AC-MSA-01. `UNLOOTED` was not seen. Whether the widget is on by default cannot be told from a dump, so Q-MSA-04 and REQ-GS-18 stay open. |
| Mineshaft shaft-code sidebar line (PLAN §5, REQ-GS-17) | **captured:** `sidebar-mineshaft.txt` line `00`. Date, server id and code share one line: `§710/01/26 §8m000§v§8XX UMBE_1`. |
| Bestiary tab widget lines (PLAN §5, T5.2, SPEC bestiary "Ghost 15: 12,449/12,500") | **captured:** `tab-dwarven-mines.txt` and `tab-mineshaft.txt`. The shape is ` <Mob> <tier>: <kills>/<next>`, with an Arabic tier and comma groups: ` Glacite Walker 9: 2,346/3,000`, ` Bal 11: 71/80`, ` Glacite Mage 13: 541/750`. The `MAX`, Roman-tier and `12.4k` forms were not seen and stay UNVERIFIED. |
| Profile widget | **captured:** both tab files. Rows: `Profile: <fruit>`, ` SB Level: [535] 28/100 XP`, ` Bank: 1B`, ` Interest: 17 Hours (913k)`. |
| Stats widget (AC-ODDS-11) | **captured (format):** `tab-mineshaft.txt`, which shows only Mining Speed, Mining Fortune and Cold Resistance. A private-use icon glyph comes before each value, which the AC-ODDS-11 sample lines lack. `Magic Find` and `Pet Luck` rows were not captured and stay UNVERIFIED. |
| Skills widget | **captured:** `tab-dwarven-mines.txt` (` Mining 60: MAX`, ` Foraging 53: 100%`). |
| `/sblevels` layout (PLAN D-17: "the wikis show 9 tabs") | **captured:** `ways-to-level-up-*.txt`. There are 9 categories: Core, Event, Dungeon, Essence Shop, Slaying, Skill Related, Miscellaneous, Story, Consumables. Mining, Farming, Fishing and Foraging sit under `skill-related-tasks.txt`. |
| SkyBlock Leveling | **captured:** `containers/skyblock-leveling-a.txt`, `-b.txt` |
| Ways to Level Up | **captured:** `containers/ways-to-level-up-a.txt` … `-j.txt` |
| Tasks ➜ … | **captured:** `containers/tasks-*.txt`, covering Core, Event, Dungeon, Essence Shop, Slaying, Mining, Farming, Fishing, Foraging, Miscellaneous, Story and Consumables (2 pages). "Tasks ➜ Consumables" is not among the T0.4b leveling parents; it was captured through "Tasks ➜ *". |
| `<X> ➜ …` step pages | **captured (partial walk):** 50 files across 44 titles under Core, Event, Dungeon, Essence Shop, Slaying, Mining, Fishing, Foraging, Miscellaneous and Story. The full step-level walk was optional and was not done. |
| Skills menu | **captured:** `containers/your-skills.txt` (the title is "Your Skills") |
| Collections menu | **captured:** `containers/collections.txt` (top level; no category or item pages) |
| Museum menu | not captured |
| Bestiary menu | **captured:** `containers/bestiary.txt` (top level) |
| "Bestiary ➜ …" page | not captured |
| Bestiary "Fishing ➜ …" page | not captured. The five `fishing-*.txt` files are `/sblevels` pages. |
| Heart of the Mountain | **captured:** `containers/heart-of-the-mountain.txt` (default view only) |
| Heart of the Forest | **captured:** `containers/heart-of-the-forest.txt` (default view only) |
| Pets | **captured:** `containers/pets-page-1-of-3.txt` (page 1 of 3 only) |
| Accessory Bag | not captured |
| Croesus and dungeon reward chests (AC-ODDS-13) | not captured |
| "* RNG Meter" menu | not captured. The HOTM menu only has a "Crystal Nucleus RNG Meter" item. |
| Name tags over NPCs (AC-GLOW-08, T3.1) | **captured:** `entities-dungeon-hub.txt` |
| PET DROP chat format (EC-ODDS-07) | not captured (the dumps do not include chat) |
| Tab list with more than 80 entries (EC-MSA-05) | **partly:** `tab-dwarven-mines.txt` lists 103 entries, which are the 80 widget rows plus 23 player rows. The widget grid ends at `!D-t`, the second Bestiary row, so a widget at the end of column D may be cut short there. What the vanilla 80-entry tab display hides is not shown by a dump. |

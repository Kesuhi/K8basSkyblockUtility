# G3 captures, 2026-10-15

Sanitised format captures from the Checkpoint G3 field check (PLAN.md), for parser tests (REQ-GS-13, REQ-GS-14).
They add the dungeon reward screens and the sack menu to the [G1 captures](../g1-2026-10-01/README.md), and they re-capture some G1 menus with the T3.0e dump (AC-GS-06 [E]).

## Provenance

- **Source:** the `[K8BAS-DUMP]` lines of the maintainer's game logs from 2026-10-15, local time:
  - the rotated log of the first G3 session: the tab and sidebar dumps at 11:53:13, in Glacite Mineshaft `ONYX_1`, then the menu captures from 12:08:28 to 12:08:43;
  - `latest.log` of the second session: the Dwarven Mines Sack captures from 14:51:14 to 14:51:53.
- **Setup:** Minecraft 26.2, Fabric Loader 0.19.5, K8bas Skyblock Utility 1.2.0+26.2 (a build of branch `update/26.2`), on Hypixel SkyBlock. The captures came from `/ksu debug dump tab`, `/ksu debug dump sidebar` and `/ksu debug dump containers on`.
- **What was copied:** only lines tagged `[K8BAS-DUMP]`, without the sound dump lines of the second session (T3.3c, not a parser fixture). Chat, guild messages, other mods' output and connection lines were not copied, and no raw log is committed.
- **File names:** the `Area:` tab row and the shaft code on the sidebar say where the tab and sidebar were taken. The mod's `Location:` log lines (island and mode only) place the first session's menus in the Dungeon Hub, just after an F1 run, and the sack captures in a Glacite Mineshaft.

## Format

The same as G1: each file holds one dump line per line, in UTF-8, exactly as the mod logged it. Only the logger prefix `[HH:MM:SS] [Render thread/INFO]: ` was removed, apart from the replacements listed under [Sanitisation](#sanitisation). `§` codes, Hypixel's private-use glyphs and trailing spaces are kept. The line shapes are in the [G1 README](../g1-2026-10-01/README.md#format).

The tab capture has the 80 widget rows (`!A-a` … `!D-t`) and one player row (`name=Self`, alone in the shaft).

## Files

Fixture files: 12, with 1,877 lines and 124,878 bytes with LF line ends (about 122 KB). This README is not included in those totals.

### Tab and sidebar

| File | Lines | Taken in | Contents |
|---|---|---|---|
| `tab-mineshaft-onyx.txt` | 81 | Glacite Mineshaft (shaft `ONYX_1`), alone | widgets Info (Area, Server, Gems, Scrap), Profile, Pet, Stats, Commissions, Powders, Frozen Corpses (three Lapis, not looted), Crystals, Bestiary, Pickaxe Ability |
| `sidebar-mineshaft-onyx.txt` | 10 | the same shaft | date, server and shaft code line, season date, time, Glacite Mineshafts, Cold |

### Menus (`containers/`)

Each file starts with the header line of the first capture that had this content, and it keeps that capture's number and state id. The menus of the first session are numbered 1–12 and those of the second 1–5.

| File | Logged title | Lines | Note |
|---|---|---|---|
| `croesus.txt` | Croesus | 64 | one unopened F1 run (slot 10, "The Catacombs", lore `Floor I`) |
| `catacombs-floor-i.txt` | Catacombs - Floor I | 81 | that run's chests: player heads Wood (slot 11), Gold (13) and Diamond (15), each with its contents in the lore between `Contents` and the blank line before `Cost` |
| `skyblock-leveling.txt` | SkyBlock Leveling | 143 | slot 48 is "Go Back" (the state of G1's `skyblock-leveling-b.txt`) |
| `ways-to-level-up.txt` | Ways to Level Up | 360 | |
| `skill-related-tasks.txt` | Skill Related Tasks | 125 | |
| `tasks-mining.txt` | Tasks ➜ Mining | 214 | |
| `heart-of-the-mountain.txt` | Heart of the Mountain | 435 | the default view (Tier 1–5) |
| `dwarven-mines-sack-a.txt` | Dwarven Mines Sack | 120 | a Tungsten Key in the sack (`Stored: 1/22.2k`), `Total items: 9` |
| `dwarven-mines-sack-b.txt` | Dwarven Mines Sack | 125 | the key taken out: `Stored: 0/22.2k` and `Empty sack!`, `Total items: 8`, and slot 33 "Insert inventory" (`Items: 1`) |
| `dwarven-mines-sack-c.txt` | Dwarven Mines Sack | 119 | as `-b`, without "Insert inventory" (slot 33 is a glass pane) |

### How the 17 menu captures became 10 files

1. **Identical captures were merged:** two "Tasks ➜ Mining" captures and two sack captures were the same apart from the capture number and the `state=` id.
2. **Click artefacts were dropped,** as at G1: five captures were a kept capture with one slot empty, the slot the player had just clicked, captured while the next menu loaded. They are the second Croesus capture (slot 10, the run), the second "Catacombs - Floor I" capture (slot 11, the Wood chest), the second "SkyBlock Leveling" capture (slot 16, "Ways to Level Up"), the second "Skill Related Tasks" capture (slot 20, "Mining Tasks") and the third sack capture (slot 22, the Tungsten Key). Each dropped capture's other slots match a kept file exactly.
3. **Distinct states were kept** and named `-a`, `-b`, `-c`; the table above gives the difference.

## Sanitisation

The G1 rules, applied the same way.

| Kind | Rule | Replacements |
|---|---|---|
| Player names | The mod masked real names when it wrote the lines (REQ-GS-13, T0.4d): `Self` is the player (the tab row `[445] Self`, the tab player row and the Croesus lore ` Self: Archer (35)`). Every line was checked against the player names that appear elsewhere in both logs (the account name and the chat senders, also case-insensitively, and against every name-like chat token with a digit, an underscore or inner capitals). It was also checked around rank prefixes and around Co-op, Guild, Party, Owner, Friends, Visiting and Profile. No unmasked name was found; the only hits were game words such as `Archer`, `Jacob`, `Hoppity` and `Old-School`. | 0 |
| Server ids | Every server id becomes `m000XX`, checked on the text with `§` codes stripped. Where Hypixel split the id with `§` codes, the codes stay in place: here the split came after three characters, so the sidebar reads `§8m00§u§80XX`, which is `m000XX` once stripped. | 2 (the sidebar date line, the tab `Server:` row) |
| UUIDs, profile, island and co-op ids | The dumps contain none. | 0 |
| Leaderboard position | The `Global Ranking: #…` lore line of "Your SkyBlock Level Ranking" becomes `#1,234`, and the "top …%" line below it becomes `0.01%`, as at G1. The player total (`15,249,542 Classic players!`) is kept, as at G1. | 2 (`skyblock-leveling.txt`) |
| SkyBlock level and XP | Shifted as at G1: every level is 100 lower (`Your level`, the five `Level …` panes, the tab rows `[…] Self` and `SB Level: […]`) and the XP total is 10,000 lower. The progress within the level (`35/100 XP`, `34/100 XP` on the tab, captured 15 minutes earlier) is kept. The G1 and G3 values stay comparable: G1 shows level 435 and 43,528 XP, G3 level 445 and 44,535 XP. | 9 (7 in `skyblock-leveling.txt`, 2 in the tab file) |
| Guild names, Discord tags, e-mail addresses, local paths, IPs | None in the dumps. | 0 |

Kept on purpose, as at G1: money, stats, skill, collection and HOTM levels, progress values, powder amounts, sack contents, the run's chest contents and costs, the profile's fruit name and the in-game date.

Two checks were run:

- Every file here passes `scripts/privacy-scan.sh --body <file>` ("clean" for all 12).
- With `§` codes stripped, no file contains a server id (`(m|M|mini|mega)` + 1–4 digits + 1–3 capitals) other than `m000XX` (found twice: the sidebar and the tab).

## What the captures confirm (REQ-GS-14)

Compared with the [G1 captures](../g1-2026-10-01/README.md#what-the-captures-confirm-req-gs-14). "Captured" means a parser test can use the named file.

| Format | Status |
|---|---|
| `/sblevels` page re-captured with the T3.0e dump (AC-GS-06 [E]) | **confirmed:** `skyblock-leveling.txt` read through the same code as G1's `skyblock-leveling-b.txt` gives the same snapshot except the level, the XP total, the share of tasks done, the player total and the progress to the next level. One text change is Hypixel's: the stat reward lines lost the space before the stat glyph (` +1 <glyph> Strength` at G1, ` +1<glyph> Strength` at G3). `skill-related-tasks.txt`, `tasks-mining.txt` and `heart-of-the-mountain.txt` match their G1 files apart from numbers. Test: `MenuRecaptureTest`. |
| Croesus (AC-ODDS-13) | **captured:** `croesus.txt`. A run is a player head "The Catacombs" with the floor (`Floor I`), the players, `Completed: …`, `No chests opened yet!`, the expiry and the available modifiers. |
| A run's chest menu opened from Croesus (AC-ODDS-13) | **captured (F1 only):** `catacombs-floor-i.txt`. The title is `Catacombs - Floor I`, without "The" or "Master Mode". The chests are not item slots but player heads (Wood, Gold, Diamond) whose lore lists the contents: books as `Enchanted Book (Infinite Quiver VI)`, essence as `Undead Essence x11`. The book names match the shipped F1 RNG-meter display names (`Rejuvenate I`, `Infinite Quiver VI`, `Feather Falling VI`). Test: `CroesusCaptureTest`. |
| An opened reward chest screen (e.g. "Bedrock Chest"), end-of-run chests, Master Mode floors, the M7 Dark Claymore example of AC-ODDS-13, a Kismet reroll | not captured, UNVERIFIED |
| Mineshaft shaft-code sidebar line | **captured again:** `sidebar-mineshaft-onyx.txt` line `00`, `§710/15/26 §8m11§u§84DK ONYX_1` before sanitising: the server id is split at a different place than at G1. |
| Frozen Corpses tab widget lines | **captured again:** `tab-mineshaft-onyx.txt`, ` Lapis: NOT LOOTED` three times, as at G1. `UNLOOTED` was still not seen. |
| Stats widget | **captured again (format):** Mining Speed, Mining Fortune and Cold Resistance, each value after a private-use glyph, as at G1. `Magic Find` and `Pet Luck` were still not seen and stay UNVERIFIED. |
| Bestiary tab widget lines | **captured again:** ` Glacite Mage 13: 541/750` and the same shape for the other rows. |
| Commissions tab widget | **captured:** ` Mineshaft Explorer: DONE`, ` Maniac Slayer: 0%`, ` Scrap Collector: 0%`, ` Aquamarine Gemstone Collector: 27.8%`. |
| Sack menu (T3.2b, REQ-MSA-05) | **captured:** `dwarven-mines-sack-*.txt`. The title is `Dwarven Mines Sack`. Each item has the sack name as lore 0 and `Stored: <n>/22.2k` as lore 2, then `Empty sack!` or `Right-Click for stack!` and `Click to pickup!`. The Tungsten Key (`TUNGSTEN_KEY`) and Umber Key (`UMBER_KEY`) are items of this sack. "Go Back" leads `To Sack of Sacks`. Other sack titles ("Sacks", "Mining Sack", …) stay UNVERIFIED. |

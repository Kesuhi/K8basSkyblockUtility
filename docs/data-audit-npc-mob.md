# NPC and mob list audit (T3.8a)

This audit covers the two lists that v1.0.x and v1.1.0 fetch at start-up from your gists. It records what they hold, how reliable the fetch is, and what T3.8 should do with each list (REQ-NPCDB-01, REQ-NPCDB-02, AC-NPCDB-01). Everything here was measured on 2026-10-09 against the live gist files.

How the numbers were made:
- **Entry checks:** a script ran over the downloaded files, using the rules of REQ-NPCDB-06 and the 24 island names in `location/Islands.kt`.
- **Revisions:** read through the GitHub gist API (read-only).
- **Fetch timings:** five requests per list.
- **Fetch behaviour:** read from the code (`NpcDatabase.kt`, `MobDatabase.kt`, `SharedHttpClient.java`, `JsonEntries.kt`).
- **Positions and near-duplicates:** checked against two of the MIT upstreams in `data-schemas/upstreams.json`:
  - NotEnoughUpdates-REPO at c4f7d475 (2026-10-09), `items/*_NPC.json`;
  - SkyHanni-REPO at 4f9c9acc (2026-10-09), `constants/island_graphs`.

  NPCs were matched by name, position and the skin stored in NEU's `nbttag`. The wiki, Fandom, hypixel.net and Reddit were not used.

## NPC list

| Field | Finding |
|---|---|
| Source | Gist `Kesuhi/7234bbe0e2f587abd2ada774bb88104f`, file `npc_database.json`. The gist also holds `npc_database_stub.json` (`[]`), which no client reads. |
| Revision history | 5 gist revisions on 2026-08-21, 00:35:30–01:04:22 UTC (see the table below). The first holds only the stub file. `npc_database.json` was created at 00:40:37 and changed 3 times, the last at 01:04:22. No change since. |
| File size | 72,759 bytes (SHA-256 `cd00a9c2…7fe476bb`). UTF-8 with a BOM, CRLF line endings. |
| Entries | 367: 362 fixed (with a position) and 5 moving (found by name: `trinity`, `tomioka` and `duncan` on Catacombs, `xalx` and `pete` on Crystal Hollows). |
| Entries per island | See [NPCs per island](#npcs-per-island). 16 of the 24 islands have entries. |
| Duplicates | No repeated id. |
| Near-duplicates | 3 pairs with the same name (`bramass_beastslayer`/`_2`, `hendrik`/`_2`, `fishing_merchant`/`_2`). 1 pair at the same position (`bestiary`/`bramass_beastslayer_2`). 8 other pairs within 2 blocks of each other in straight-line distance, plus 1 more within 2 blocks on every axis. 15 pairs where one name begins with the other. Each is resolved in [NPC near-duplicates](#npc-near-duplicates). |
| Missing or invalid fields | None against REQ-NPCDB-06: every id, name and island is set, and every island is a tracker island. Every fixed entry has integer coordinates with y between 6 and 225, none at (0,0,0). Every moving entry has a match text. No field has leading or trailing spaces or § codes. Every id is `[a-z0-9_]+`. |
| Wrong positions | 359 of the 362 fixed NPCs match a same-named NEU entry exactly. The other 3: `bramass_beastslayer` (resolved below), `gemma` (one block off) and `scoop` (moved). See [NPC positions](#npc-positions). |
| Invisible-by-design | Not applicable (NPCs). |
| Islands with no coverage | Private Island, The Farming Islands, Glacite Mineshafts, Backwater Bayou, Kuudra, The Rift, Dark Auction. "Dungeon Hub" also shows no entries, but only because its 6 NPCs are filed under "Catacombs" (see below). |
| Reliability of the current fetch | See [Reliability of the current fetch](#reliability-of-the-current-fetch). |
| **Recommendation** | **Bundle it (D-12).** It was written on 2026-08-21 (00:40:37–01:04:22 UTC, 3 edits after creation) and has not changed in the 7 weeks since. At 72,759 bytes it is small. The fetch makes one attempt per game start, with no retry, no offline copy and no size cap. So without network at start-up, on any launch, the picker has no entries (EC-NPCDB-04), and fixed-NPC waypoints fall back to the positions stored in their rules. A bundled table has none of these failure modes, and updates come with releases. |

### NPC revisions

| Revision | Committed (UTC) | Lines +/− | Size of `npc_database.json` | Entries |
|---|---|---|---|---|
| 3129aca2 (current) | 2026-08-21 01:04:22 | +3 / −3 | 72,759 bytes | 367 |
| 0fc23883 | 2026-08-21 01:03:35 | +21 / −0 | 72,747 bytes | 367 |
| b759c5de | 2026-08-21 00:58:03 | +14 / −0 | 72,252 bytes | 364 |
| 9a1195ca | 2026-08-21 00:40:37 | +3,260 / −0 | 71,918 bytes | 362 |
| c12117d8 | 2026-08-21 00:35:30 | +1 / −0 | (only the stub file) | – |

### NPCs per island

| Island | Entries | | Island | Entries |
|---|---|---|---|---|
| Hub | 113 | | Lotus Atoll | 9 |
| Crimson Isle | 81 | | Catacombs | 9 |
| Dwarven Mines | 52 | | Crystal Hollows | 6 |
| Moonglade Marsh | 28 | | The End | 5 |
| Torrhus Canyon | 19 | | Deep Caverns | 4 |
| Jerry | 12 | | Gold Mine | 4 |
| Spider's Den | 11 | | Critter Safari | 2 |
| The Park | 10 | | Garden | 2 |

"Catacombs" holds 9 entries:
- **6 fixed NPCs that stand in the Dungeon Hub:** `adventurer_saul`, `croesus`, `guildford`, `malik`, `mort` and `ophelia`. The loader turns a fixed "Catacombs" entry into "Dungeon Hub" (REQ-LOC-03). T3.8b also files them under "Dungeon Hub" in the data (REQ-NPCDB-07(a)). The load-time normalisation stays, as REQ-LOC-03 and AC-LOC-03 still require it.
- **3 moving dungeon NPCs:** `trinity`, `tomioka` and `duncan`, which stay on "Catacombs" (D-2).

The five "You found" ids (`trinity`, `tomioka`, `duncan`, `xalx`, `pete`, R20) are all present and must keep their ids.

### NPC near-duplicates

Resolution keys:
- **Merged:** one entry stays; the other id becomes an alias of it, so rules linked to either id keep their link (REQ-NPCDB-07(d)).
- **Distinct:** both entries stay.
- **G3:** confirmed in the batched field check at G3.

| Entries (our position) | Upstream evidence | Outcome | Confidence |
|---|---|---|---|
| `bramass_beastslayer` (-271,114,-196), `bramass_beastslayer_2` (-272,113,-197) | NEU moved `BRAMASS_BEASTSLAYER_NPC` from -271 114 -196 to -272 113 -197 and deleted the misspelled `BRRASMASS_BEASTSLAYER_NPC`, which stood on that spot (#3123, 2026-09-24). SkyHanni has one "Bramass Beastslayer" node. | **Merged** into `bramass_beastslayer` at **(-272,113,-197)**; `bramass_beastslayer_2` becomes an alias. | High |
| `bestiary` (-272,113,-197), on the same block as `bramass_beastslayer_2` | NEU `BESTIARY_NPC` stands on the same block, with the **same skin** as Bramass. SkyHanni has no Bestiary node. | **Merged** into `bramass_beastslayer` as an alias. It is the same figure; "Bestiary" is most likely a second name line on it (G3). The picker should still find Bramass when the user searches for "Bestiary". | Medium-high |
| `hendrik` (17,63,-30), `hendrik_2` (16,62,-31) | NEU has both: the misspelled `HENDRICK_NPC` (17 63 -30, old 2.1.1 export, still present) and `HENDRIK_NPC` (16 62 -31), added by #2731 ("fix: NPC Coords on Jerry Workshop", 2026-06-20). Both have the same skin. SkyHanni (Garden visitors) has 16:62:-30. | **Merged** into `hendrik` at **(16,62,-31)**, the newer NEU position; `hendrik_2` becomes an alias. | High |
| `fishing_merchant` (112,71,-45), `fishing_merchant_2` (52,70,-83) | NEU moved `FISHING_MERCHANT_NPC` to 112 71 -45 in the Hub rebuild (#2335, 2026-02-24). `FISH_MERCHANT_NPC` (52 70 -83, from 2022) has the same skin and was not updated. SkyHanni had two nodes until its own Hub rebuild (#615, 2026-02-24) and has one since, at 112:72:-43. The old spot is now the wheat farm. | **Merged** into `fishing_merchant` at (112,71,-45); `fishing_merchant_2` becomes an alias, so a rule on the old spot moves to the current one. | High |
| `arba` (-3,94,-820), `arbadak` (0,94,-824) | Different skins in NEU; two nodes in SkyHanni. | **Distinct.** | High |
| `forge_foreman` (-3,149,-69), `fred` (-2,150,-68) | NEU `FRED_NPC` (old export, 2024) has the **same skin** as `FORGE_FOREMAN_NPC` (2025), exactly +1,+1,+1 from it. SkyHanni has only "Forge Foreman". | **Merged** into `forge_foreman`; `fred` becomes an alias. Neither source says how the two names relate (G3). | Medium |
| `bobby_joe` (63,72,-115), `farm_merchant` (63,72,-114) | Different skins. NEU moved Farm Merchant onto this spot in the Hub rebuild but did not update Bobby Joe (2024). SkyHanni dropped Bobby Joe in its rebuild. | **Distinct.** Bobby Joe's position predates the Hub rebuild and may be outdated (G3). | High (distinct), low (Bobby Joe's position) |
| `lukas_the_aquarist` (16,71,-9), `pig_shop` (15,71,-9) | Different skins. NEU describes each as appearing only during its yearly event ("Year of the Seal", "Year of the Pig"). They share the event stall. | **Distinct.** They never stand there at the same time. | High |
| `grelius` (-131,102,-753), `krevius` (-131,102,-755) | Different skins; two SkyHanni nodes. | **Distinct.** | High |
| `keran` (-562,98,-688), `prie` (-562,98,-690) | Different skins. NEU (2025 export) agrees with our positions. SkyHanni (2024) puts them about 50 blocks away (-612:109:-669 and -612:109:-662). | **Distinct.** Our positions follow the newer source (G3). | High (distinct), low (position) |
| `fear_mongerer` (-33,69,6), `oringo` (-35,69,5) | Within 2 blocks on every axis (2.24 apart); different NPCs on the Hub. | **Distinct.** | High |
| Name-prefix pairs: `an`/`andrea`/`angler_angus`/`anita`/`ant`, `arch`/`archaeologist`/`archie`, `farmer`/`farmer_rigby`, `fisherman`/`fisherman_gerald`, `fred`/`freddy`, `geo`/`geonathan_greatforge`/`george`, `jacob`/`jacobus`, `master_tactician`/`master_tactician_funk`, `wizard`/`wizard_s_assistant` (and `arba`/`arbadak` above) | Different islands, or positions far apart. | **Distinct**, apart from `fred`, which is merged above. | High |

### NPC positions

Older NEU entries, exported by NEU 2.1.x, are often exactly +1 on x, y and z compared with newer exports of the same NPC. NEU's fix commits moved several by −1,−1,−1:
- Einary, Frosty and St. Jerry in #2731;
- Bramass in #3123;
- Gemma in #3168.

Not every fix runs that way: #2731 raised Ike and Michael by +1 on y.

Of our 362 fixed positions, 359 match NEU's current entry for the same name exactly, and 145 of those come from 2.1.x-era entries. Those may still be one block off. A waypoint marks a spot, so this is cosmetic, and the G3 field check samples a few. Two positions are behind NEU today:

| Entry | Ours | NEU at c4f7d475 | Outcome |
|---|---|---|---|
| `gemma` (Crystal Hollows) | 476,107,514 | 475,106,513 (#3168, 2026-10-08: −1,−1,−1) | Correct to NEU's position in T3.8b. |
| `scoop` (Hub) | 5,183,25 (the 2022 position) | -54,169,70 (#3160, 2026-10-06: moved) | Correct to NEU's position in T3.8b (G3). |

## Mob list

| Field | Finding |
|---|---|
| Source | Gist `Kesuhi/f68f11d96e15342f36c2402fde8d5ac1`, file `mob_database.json`. |
| Revision history | 3 revisions on 2026-08-20, 17:31:44–18:05:45 UTC (see the table below). No change since. The current revision restructured the list (see below). |
| File size | 50,610 bytes (SHA-256 `458cd464…bc2cc18`). UTF-8 with a BOM, CRLF line endings. |
| Entries | 308, each with id, name, island and match text; 65 also have a subfolder (all under "Fishing"). |
| Entries per island | See [Mobs per island](#mobs-per-island). 15 of the 24 islands have entries, plus 3 cross-island groups. |
| Duplicates | No repeated id, name or match text. |
| Near-duplicates | No two entries share a name or a match text. 26 pairs from 18 match texts overlap: a rule's text also appears in another mob's name. 14 of those pairs are on the same island. See [Overlapping match texts](#overlapping-match-texts). |
| Missing or invalid fields | None against REQ-NPCDB-06: every id, name and match text is set and not blank. Every island is a tracker island or an ungated group ("Fishing", "Mythological Creatures", "Spooky Festival"), and every subfolder is set where present. No field has leading or trailing spaces or § codes. Every id is `[a-z0-9_]+`. |
| Invisible-by-design | The 4 that REQ-NPCDB-08 names are all present: `ghost` (Dwarven Mines), `fels` (Catacombs), `sneaky_creeper` (Deep Caverns) and `invisibug` (Moonglade Marsh). The mod never outlines an invisible entity, so a rule for them never shows anything. T3.8b flags them, and T3.8c showed them greyed and not addable; since G3 (R38, 2026-10-15) they are left out of the bundled table instead (REQ-NPCDB-08), and the frozen 1.0.x gist stays as it is. **Not flagged:** `shadow_assassin` (Catacombs). Shadow Assassins turn invisible only at times (EC-GLOW-01) and can be outlined while they are visible, which REQ-LOC-10 relies on. It stays addable. |
| Islands with no coverage | Private Island, The Farming Islands, Gold Mine, Glacite Mineshafts, Backwater Bayou, Critter Safari, The Rift, Dark Auction, Dungeon Hub. Backwater Bayou is partly covered by the 6 sea creatures under "Fishing / Backwater Bayou". The 5 Glacite mobs are filed under "Dwarven Mines" only. The Rift had 13 mobs until the current revision removed them. |
| Reliability of the current fetch | The same code path as the NPC list, without the NPC list's load listener. See [Reliability of the current fetch](#reliability-of-the-current-fetch). |
| **Recommendation** | **Bundle it (D-12).** It was written on 2026-08-20 (17:31:44–18:05:45 UTC, 3 revisions) and has not changed in the 7 weeks since. At 50,610 bytes it is small. It shares every failure mode of the NPC fetch (one attempt per start, no retry, no offline copy, no size cap). Bundling also lets T3.8b flag the invisible-by-design mobs and add the Glacite Mineshafts group in the data, which the frozen 1.0.x gist cannot. |

### Mob revisions

| Revision | Committed (UTC) | Lines +/− | Size | Entries | Format |
|---|---|---|---|---|---|
| 3a71b95b (current) | 2026-08-20 18:05:45 | +1,468 / −829 | 50,610 bytes | 308 | BOM, CRLF |
| c9a03b94 | 2026-08-20 17:42:29 | +1,276 / −219 | 34,694 bytes | 207 | BOM, CRLF |
| 90dc0351 | 2026-08-20 17:31:44 | +219 / −0 | 22,837 bytes | 207 | no BOM, LF |

The first two revisions hold the same 207 entries; c9a03b94 changed only the formatting. They used the island values "Dungeon (Catacombs)", "Winter Island", "Events" and "The Rift".

The current revision restructured the list:
- removed 115 of the 207 ids, among them all 13 The Rift mobs, 37 dungeon entries, slayer bosses and dragons;
- added 216 ids;
- renamed the island values: "Dungeon (Catacombs)" became "Catacombs", and "Winter Island" and "Events" became "Fishing", "Spooky Festival" and "Mythological Creatures".

A 1.0.x rule made from the first two revisions (published for about 34 minutes) may therefore carry a `sourceId` that no longer exists (EC-NPCDB-01: it keeps its stored values) or an island that is not a tracker island.

### Mobs per island

| Island or group | Entries | | Island or group | Entries |
|---|---|---|---|---|
| Fishing (ungated) | 65 | | Mythological Creatures (ungated) | 12 |
| Catacombs | 35 | | Spider's Den | 12 |
| Moonglade Marsh | 33 | | Lotus Atoll | 11 |
| Torrhus Canyon | 30 | | Crystal Hollows | 10 |
| Crimson Isle | 20 | | The End | 9 |
| Garden | 17 | | Hub | 7 |
| Dwarven Mines | 14 | | Spooky Festival (ungated) | 7 |
| Kuudra | 13 | | Deep Caverns | 6 |
| Jerry (ungated in code) | 4 | | The Park | 3 |

The 65 "Fishing" mobs are split into subfolders:

| Subfolder | Entries |
|---|---|
| General | 24 |
| Lava Fishing | 19 |
| Backwater Bayou | 6 |
| Spooky Festival Fishing | 6 |
| Winter Fishing | 6 |
| Fishing Festival | 4 |

The 4 "Jerry" mobs are Blue, Green, Purple and Golden Jerry: the hidden Jerries that appear on whatever island the player is on. The code treats "Jerry" as ungated (`MobHighlighterModule.UNGATED_ISLANDS`), and that is right for them. Gating them to the island "Jerry" (Jerry's Workshop) would make their rules fire almost never. SPEC's examples of ungated groups (REQ-NPCDB-06) do not include "Jerry", and the group name collides with the island name. So T3.8 should keep these mobs ungated, under a group name that is not an island, e.g. "Hidden Jerries". The group's code comment, which mentions Jerry's Workshop, should change with it.

### Overlapping match texts

A mob rule matches a name tag that contains its match text, case-sensitively, after § codes are stripped (`NameMatcher`, CONTAINS). So a short text also outlines a longer name that contains it. The data has 26 such pairs from 18 match texts.

**On the same island (14 pairs).** All 14 are one mob family, so the wider match is arguably intended:

| Match text (id) | Also matches |
|---|---|
| Goblin (`goblin`) | Diamond Goblin, Goblin Raiders, Golden Goblin |
| Blaze (`blaze`) | Millennia-Aged Blaze, Smoldering Blaze |
| Arachne (`arachne`) | Arachne's Brood, Arachne's Keeper |
| Ant (`ant`) | Queen Ant |
| Moth (`moth`) | Lunar Moth |
| Wolf (`wolf`) | Old Wolf |
| Tank Zombie (`tank_zombie`) | Super Tank Zombie |
| Undead (`undead`) | Undead Skeleton |
| Magma Cube (`magma_cube`) | Magma Cube Rider |
| Water Worm (`water_worm`) | Poisoned Water Worm |

**Across islands (12 pairs).** A rule made from the picker is gated to its mob's island, so most of these never meet. Two can, because the longer-named mob can appear on the shorter one's island:

| Match text (island) | Also matches (island or group) | Can they meet? |
|---|---|---|
| Blaze (Crimson Isle) | Lava Blaze (Fishing / Lava Fishing) | **Yes:** lava fishing on the Crimson Isle. Not the same family. |
| Tiki (Torrhus Canyon) | Wiki Tiki (Fishing / General) | **Possibly:** Wiki Tiki is a water-hotspot sea creature, if fished on Torrhus Canyon (G3). |
| Blaze (Crimson Isle) | Wandering Blaze (Kuudra) | No (different islands). |
| Magma Cube (Crimson Isle) | Inferno Magma Cube (Kuudra) | No. |
| Ghast (Crimson Isle) | Powder Ghast (Dwarven Mines) | No. |
| Golem (Catacombs) | Blazing Golem (Kuudra) | No. |
| Grunt (Crystal Hollows) | Skeleton Grunt, Zombie Grunt (Catacombs) | No. |
| Sludge (Crystal Hollows) | Bayou Sludge (Fishing / Backwater Bayou) | No. |
| Dragon (The End) | Dragonfly (Garden) | No. |
| Beetle (Garden) | Stag Beetle (Moonglade Marsh) | No. |
| Slug (Garden) | Magma Slug (Fishing / Lava Fishing) | No. |

There are also 22 near-hits that do **not** match, only because the case differs, e.g. Ant in "Giant Isopod", Fly in "Dragonfly", "Firefly" and "Butterfly", and Squid in "Lumisquid". They would match if matching ever became case-insensitive.

## Reliability of the current fetch

Both lists use the same path: `NpcDatabase` and `MobDatabase` call `SharedHttpClient` (T1.12) and parse with `JsonEntries`.

| Aspect | Finding |
|---|---|
| When | Once per game start, on a virtual thread, from each module's registration. Opening the picker does not retry, because the started flag is never reset. |
| Timeouts | 10 s to connect and 10 s for the request (REQ-NPCDB-05; tested with a server that never answers). Not tested: whether a server that sends headers and then trickles the body is bounded by the request timeout. |
| Measured fetch | All 10 requests returned HTTP 200 with the full file in 25–38 ms, all CDN cache hits. GitHub serves the raw URL with `Cache-Control: max-age=300`, so an edit can take up to about 5 minutes to reach clients. |
| Caching | In memory for the session only; nothing on disk, by design. |
| Fallback | None: no bundled copy and no disk cache. Without network at start-up, on any launch, both pickers show "Database unavailable: it could not be fetched" for the whole session, and nothing can be added. Existing rules keep working from their stored values, and fixed-NPC waypoints use the rule's own coordinates. |
| Validation | Entries are bound one by one; a malformed entry is skipped, with one WARN giving the count (not the ids). Checked: id, name and island are not null; a fixed NPC has finite x, y and z; a mob or moving NPC has a match text that is not blank; elements that are not objects, or have fields of the wrong type, are skipped. Not checked: blank ids or names, unknown or mis-cased islands, non-integer or out-of-range coordinates, and repeated ids (a repeated NPC id resolves to the first entry). |
| Size | No cap: the whole body is read into memory. |
| Failure modes seen in the code | (1) One failed request at start leaves the list unavailable until the next launch. (2) Nothing works offline (EC-NPCDB-04 not met). (3) A file with some entries skipped shows the rest with no hint in the picker, so the list is silently incomplete. If every entry is skipped, or the body is `[]`, empty or `null`, the picker says "The database is empty", not "unavailable". (4) Only a `RuntimeException` is caught while parsing. A real `Error`, such as an `OutOfMemoryError` from a very large body (there is no size cap), leaves the list on "Loading the database..." for the session. Deeply nested JSON is not such a case: Gson stops at a depth of 255 and throws a `JsonSyntaxException`, so the list becomes unavailable with one WARN. (5) Redirects are not followed, so a 3xx counts as a failure. (6) An island with different spelling or case gives a rule that never activates. |
| Observed failures | None during this audit. Neither gist has changed since August, so the fetch has served the same bytes for 7 weeks. |

All of these failure modes go away when T3.8 bundles both lists: there is no request and no cache, and the data validator checks the entries at build time (REQ-NPCDB-03, REQ-NPCDB-04, AC-NPCDB-05).

## Coverage gaps

v1 adds no NPCs or mobs for islands without coverage today (R5). The corrections in T3.8b (REQ-NPCDB-07) still close two gaps:
- the Glacite Mineshafts mob group (AC-NPCDB-06);
- the Dungeon Hub NPCs, refiled from "Catacombs".

The others stay open.

| Island | NPCs | Mobs |
|---|---|---|
| Private Island | none | none |
| The Farming Islands | none | none |
| Glacite Mineshafts | none | none; T3.8b lists the Glacite mobs that spawn there here too (Bowman, Caver, Mage, Mutt and Littlefoot; NEU tags them `mineshaft`, Littlefoot through "Littlefoot's Den"; Glacite Walker is the Great Ice Wall's Ice Walker and stays under Dwarven Mines only) |
| Backwater Bayou | none | only its 6 sea creatures, under "Fishing" |
| The Rift | none | none (13 until the current mob revision) |
| Dark Auction | none | none |
| Kuudra | none | 13 |
| Gold Mine | 4 | none |
| Critter Safari | 2 | none |
| Dungeon Hub | 6, filed under "Catacombs" until T3.8b | none (the mobs fight in "Catacombs") |

The out-of-scope lines in SPEC (npc-mob-data out of scope) and PLAN (§7 "Not built in 2.0.0") list Galatea among the gaps. The data already covers it: Moonglade Marsh and Torrhus Canyon, the two Galatea islands, have 28 NPCs and 33 mobs, and 19 NPCs and 30 mobs. Galatea is not a gap, and those two lines should be corrected.

## Follow-ups

- **T3.8:**
  - Convert both lists from the frozen gists into registry tables.
  - Keep the hidden Jerries ungated, under a group name that is not an island (above).
  - The NPC "Unknown" island is not used by the data and needs no allowlist.
  - Correct the Galatea entry in the SPEC and PLAN out-of-scope lines.
- **T3.8b:**
  - Apply the near-duplicate outcomes above, with aliases.
  - Correct `gemma` and `scoop`.
  - Move the 6 Dungeon Hub NPCs to "Dungeon Hub".
  - Add the Glacite Mineshafts group.
  - Flag the 4 invisible-by-design mobs.
  - Decide on the two cross-island overlaps that can meet (Blaze/Lava Blaze, Tiki/Wiki Tiki).
- **G3 field check** (in game, by you):
  - the positions of Bobby Joe, Keran, Prie and Scoop;
  - whether "Bestiary" and "Fred" are second names of Bramass and the Forge Foreman;
  - whether Wiki Tiki can be fished on Torrhus Canyon;
  - a sample of 2.1.x-era NPC positions, for the one-block offset.
- **Done with this audit:** `data-schemas/upstreams.json` named the SkyHanni-REPO owner as "hannibal2" (the author's display name). The repository has always been `hannibal002/SkyHanni-REPO` (MIT), and the URL is corrected.
- **Frozen gists:** the NPC gist's extra `npc_database_stub.json` is harmless to 1.0.x clients and can stay.

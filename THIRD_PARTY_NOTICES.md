# Third-party notices

K8bas Skyblock Utility is released under CC0-1.0. The release jar bundles the
following third-party material. Each keeps its own licence. None of their code is copied into
this repository.

## Render Chest

- Version: 1.0.3+26.2
- Author: AzureAaron
- Source: https://github.com/AzureAaron/RenderChest
- Licence: Apache License 2.0
- Use: draws the depth-tested glow for Mob Highlighter and NPC Search.
- Distribution: bundled unmodified as a nested jar
  (`META-INF/jars/render-chest-1.0.3+26.2.jar`). Its licence text is the
  `LICENCE_render-chest` file inside that jar.

## NotEnoughUpdates-REPO

- Copyright (c) 2020 Moulberry
- Source: https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO
- Licence: MIT
- Use: the NPC list for NPC Search (names, islands and positions) was compiled from its
  `items/*_NPC.json` files in August 2026, and cross-checked against revision
  c4f7d4757641f3a11c74d3d77cec76c100abaf98 in `docs/data-audit-npc-mob.md`.
- Use (Rare Drop Odds): the RNG meter scores per dungeon floor and slayer boss from
  `constants/rngscore.json`, and the item display names from `items/*.json`, at revision
  c4f7d4757641f3a11c74d3d77cec76c100abaf98. Every chance is computed from them in code.
- Distribution: the tables `thirdparty/neu-repo/npcdb/npcs.json` and
  `thirdparty/neu-repo/odds/rngscore.json`, and the display names in
  `thirdparty/skyocean/odds/vanguard.json`, in the jar's bundled data
  (`assets/k8bas_skyblock_utility/data/`). The licence text is
  `META-INF/licenses/neu-repo.txt` in the jar and `licenses/neu-repo.txt` in this repository.

## SkyOcean

- Authors: meowdding
- Source: https://github.com/meowdding/SkyOcean (`src/repo/vanguard.jsonc`, revision
  762ec36eb5b6d9e881e783126952cc7175712465), also published unchanged in
  https://github.com/meowdding/meowdding-repo (`repo/skyocean/vanguard.jsonc`, revision
  d010ae02cf87fc6ca619430e33d176672ff43789)
- Licence: MIT for what SkyOcean License v1 calls code (its `.java`, `.kt` and `.kts` files,
  shaders, `src/repo/**` and `buildSrc/**`); everything else of SkyOcean is all rights reserved.
- Use (Rare Drop Odds): the Vanguard corpse loot weights per roll, from the MIT file above.
  SkyOcean's assumed weight for the Frostbitten Dye is not used.
- Approach (Bobber Fix): the fix follows SkyOcean's `FishingHookMixin`, with the same two hook
  points in `FishingHook` and the same rule that Hypixel's bite-timer stand has the hook's id + 1.
  It is written anew here around this mod's own decision code; no SkyOcean file is copied, and no
  art, sound or text of SkyOcean is used.
- Distribution: the table `thirdparty/skyocean/odds/vanguard.json` in the jar's bundled data.
  The licence text is `META-INF/licenses/skyocean.txt` in the jar and `licenses/skyocean.txt`
  in this repository.

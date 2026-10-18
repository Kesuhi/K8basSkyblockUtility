# Third-party notices

K8bas Skyblock Utility is released under CC0-1.0. The release jar bundles the
following third-party material. Each keeps its own licence. None of their code is copied into
this repository. The SkyOcean section is a credit only: nothing of SkyOcean is bundled.

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
  c4f7d4757641f3a11c74d3d77cec76c100abaf98 in `docs/data-audit-npc-mob.md`. The bestiary
  families for the Bestiary Tracker (kill brackets, caps, categories and mob ids) are its
  `constants/bestiary.json` at revision 24564a1ad22fdb0ca985d78710c401898eac586b, trimmed
  and reshaped: icons and colour codes removed; the families flattened into rows with an id,
  their category and subcategory and a derived tier count; the CRITTERS bracket set renamed
  `critters_1` to `critters_5`; ten families' bracket or cap corrected from game observations and other projects'
  published values, each such row keeping NEU's values under `neu`.
- Distribution: the tables `thirdparty/neu-repo/npcdb/npcs.json` and
  `thirdparty/neu-repo/bestiary/families.json` in the jar's bundled data
  (`assets/k8bas_skyblock_utility/data/`). The licence text is
  `META-INF/licenses/neu-repo.txt` in the jar and `licenses/neu-repo.txt` in this repository.

## SkyOcean

- Authors: meowdding
- Source: https://github.com/meowdding/SkyOcean
- Licence: MIT for what SkyOcean License v1 calls code (its `.java`, `.kt` and `.kts` files,
  shaders, `src/repo/**` and `buildSrc/**`); everything else of SkyOcean is all rights reserved.
- Approach (Bobber Fix): the fix follows SkyOcean's `FishingHookMixin`, with the same two hook
  points in `FishingHook` and the same rule that Hypixel's bite-timer stand has the hook's id + 1.
  It is written anew here around this mod's own decision code; no SkyOcean file is copied, and no
  art, sound or text of SkyOcean is used.
- Distribution: nothing of SkyOcean is bundled. This section is the credit for the approach.

## SkyHanni-REPO

- Copyright (c) 2022 hannibal2
- Source: https://github.com/hannibal002/SkyHanni-REPO. The corpse spots come from its open pull
  request #759 by GrowlingGrizzly (`constants/FrozenCorpses.json` in the fork
  https://github.com/GrowlingGrizzly/SkyHanni-REPO, revision
  e2c8edb93240b0852d9b42e614f3c80a5476916c).
- Licence: MIT
- Use (Possible corpse spots): the possible Frozen Corpse spots per Glacite Mineshaft layout, merged
  with the meowdding-repo and ShaftUtils spots below into one table.
- Distribution: the table `thirdparty/skyhanni-repo/mining/corpse_spots.json` in the jar's bundled
  data (`assets/k8bas_skyblock_utility/data/`). The licence text is
  `META-INF/licenses/skyhanni-repo.txt` in the jar and `licenses/skyhanni-repo.txt` in this
  repository.

## meowdding-repo

- Copyright (c) 2026 meowdding
- Source: https://github.com/meowdding/meowdding-repo (`repo/mining/mineshaft_corpses/`, revision
  20524eb3255da52653957dfd39b22a02e56b82ad; the data is unchanged since
  42d01278a862cf41ff4b5041d8ca3bf3aa1ab8f5)
- Licence: MIT
- Use (Possible corpse spots): its corpse spots, one block higher to match the frame above, merged
  into `thirdparty/skyhanni-repo/mining/corpse_spots.json`.
- Distribution: in that table. The licence text is `META-INF/licenses/meowdding-repo.txt` in the
  jar and `licenses/meowdding-repo.txt` in this repository.

## ShaftUtils

- Author: DeveloperGr3y; its added spots are credited to Michelzz
- Source: https://github.com/DeveloperGr3y/ShaftUtils
  (`src/main/resources/assets/shaftutils/corpse_spawns.json`, revision
  c9a38786f61533e63d9fef20f320c70b02072c4f)
- Licence: CC0-1.0 (no notice required; credited with thanks)
- Use (Possible corpse spots): its corpse spots, one block higher to match the frame above, merged
  into `thirdparty/skyhanni-repo/mining/corpse_spots.json`.

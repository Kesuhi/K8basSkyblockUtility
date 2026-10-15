package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-GS-06's [E] capture made repeatable [A] (T3.10, REQ-GS-06, REQ-GS-14): the `/sblevels` pages re-captured at G3
 * (fixtures/captures/g3-2026-10-15/containers, 2026-10-15, written by the T3.0e dump on the menu reader) read through
 * the same reader code as their G1 captures (fixtures/captures/g1-2026-10-01/containers, 2026-10-01), which is
 * {@link CaptureFixtures#menu}, give the same snapshot apart from what changed in play between the two sessions.
 *
 * <p>"The same snapshot" means: the same title and size, the same slot indices, and per slot the same item id,
 * SkyBlock id, count and name, and the same number of lore lines, each lore line equal to its G1 line once every
 * number in both is read as "#". Numbers are what legitimately changed (levels, XP, progress, powder, timers).
 * The state id and capture number are not compared: they belong to the session.
 *
 * <p>SkyBlock Leveling (G3 `skyblock-leveling.txt` against G1 `skyblock-leveling-b.txt`, both opened with "Go Back" in
 * slot 48) is pinned more closely: only slot 4 (the ranking item) and slots 19–23 (the level panes) differ at all.
 * Slot 4 differs in lore 2 (level), 3 (XP total), 5 (share of tasks done) and 10 (player total); the ranking lines
 * 8 and 9 are the sanitised placeholders in both captures. The panes are ten levels higher (both captures are
 * sanitised to level − 100, so 435 → 445 here). Slot 20's lore 6 is the progress to the next level. One change is
 * Hypixel's own, not a number: the stat reward lines lost the space before the stat glyph (` +1 <glyph> Strength`
 * at G1, ` +1<glyph> Strength` at G3), so the comparison drops a space before a private-use character.
 *
 * <p>Not compared: "Ways to Level Up" (its "Recently Viewed ➜" row lists the last pages opened, so its names differ
 * by design), Croesus and the Dwarven Mines Sack (no G1 capture).
 */
class MenuRecaptureTest {
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.]*");
	private static final Pattern SPACE_BEFORE_GLYPH = Pattern.compile(" (?=\\p{Co})");
	private static final Pattern LEVEL_PANE = Pattern.compile("Level (\\d+)");

	private static MenuSnapshot g1(String file) {
		return CaptureFixtures.menu(CaptureFixtures.G1, "containers/" + file).menu();
	}

	private static MenuSnapshot g3(String file) {
		return CaptureFixtures.menu(CaptureFixtures.G3, "containers/" + file).menu();
	}

	private static String shape(String text) {
		return NUMBER.matcher(SPACE_BEFORE_GLYPH.matcher(text).replaceAll("")).replaceAll("#");
	}

	private static Map<Integer, MenuSlot> bySlot(MenuSnapshot menu) {
		return menu.getSlots().stream().collect(Collectors.toMap(MenuSlot::getIndex, Function.identity()));
	}

	/** Asserts the same snapshot up to numbers (see the class doc) and returns the slots whose name or lore differ. */
	private static Set<Integer> sameUpToNumbers(MenuSnapshot before, MenuSnapshot after) {
		assertEquals(before.getTitle(), after.getTitle());
		assertEquals(before.getSize(), after.getSize(), before.getTitle());
		Map<Integer, MenuSlot> old = bySlot(before);
		Map<Integer, MenuSlot> now = bySlot(after);
		assertEquals(old.keySet(), now.keySet(), before.getTitle() + ": slot indices");
		Set<Integer> changed = new TreeSet<>();
		for (int index : old.keySet()) {
			MenuSlot a = old.get(index);
			MenuSlot b = now.get(index);
			String where = before.getTitle() + " slot " + index;
			assertEquals(a.getItem(), b.getItem(), where);
			assertEquals(a.getSkyblockId(), b.getSkyblockId(), where);
			assertEquals(a.getCount(), b.getCount(), where);
			assertEquals(shape(a.getName()), shape(b.getName()), where);
			assertEquals(a.getLore().size(), b.getLore().size(), where + ": lore lines");
			for (int i = 0; i < a.getLore().size(); i++) {
				assertEquals(shape(a.getLore().get(i)), shape(b.getLore().get(i)), where + " lore " + i);
			}
			if (!a.getName().equals(b.getName()) || !a.getLore().equals(b.getLore())) {
				changed.add(index);
			}
		}
		return changed;
	}

	private static List<Integer> changedLore(MenuSlot a, MenuSlot b) {
		List<Integer> lines = new ArrayList<>();
		for (int i = 0; i < a.getLore().size(); i++) {
			if (!a.getLore().get(i).equals(b.getLore().get(i))) {
				lines.add(i);
			}
		}
		return lines;
	}

	private static int paneLevel(MenuSlot pane) {
		Matcher m = LEVEL_PANE.matcher(pane.getName());
		assertTrue(m.matches(), pane.getName());
		return Integer.parseInt(m.group(1));
	}

	@Test
	void theSkyBlockLevelingPageEqualsItsG1CaptureApartFromTheLevelAndXp() {
		MenuSnapshot before = g1("skyblock-leveling-b.txt");
		MenuSnapshot after = g3("skyblock-leveling.txt");
		assertEquals(Set.of(4, 19, 20, 21, 22, 23), sameUpToNumbers(before, after));

		Map<Integer, MenuSlot> old = bySlot(before);
		Map<Integer, MenuSlot> now = bySlot(after);
		assertEquals(old.get(4).getName(), now.get(4).getName());
		assertEquals(List.of(2, 3, 5, 10), changedLore(old.get(4), now.get(4)));
		assertEquals("Your level: 445", now.get(4).getLore().get(2));
		assertEquals("Global Ranking: #1,234", now.get(4).getLore().get(8), "sanitised as at G1");
		for (int slot = 19; slot <= 23; slot++) {
			MenuSlot a = old.get(slot);
			MenuSlot b = now.get(slot);
			assertEquals(paneLevel(a) + 10, paneLevel(b), "slot " + slot);
			assertEquals(445 + slot - 19, paneLevel(b), "the panes start at the player's level");
			List<String> loreA = a.getLore().stream().map(line -> SPACE_BEFORE_GLYPH.matcher(line).replaceAll("")).toList();
			List<String> loreB = b.getLore().stream().map(line -> SPACE_BEFORE_GLYPH.matcher(line).replaceAll("")).toList();
			for (int i = 0; i < loreA.size(); i++) {
				boolean progress = slot == 20 && i == 6;
				assertEquals(!progress, loreA.get(i).equals(loreB.get(i)), "slot " + slot + " lore " + i);
			}
		}
		assertEquals("Progress to Level Up:", now.get(20).getLore().get(5));
	}

	@Test
	void theOtherRecapturedPagesKeepTheirLayoutAndTexts() {
		// The slots whose numbers changed: category progress; task progress, powder totals and shares; perk levels,
		// their values, the daily reset timers and the powder amounts.
		Map<String, Set<Integer>> changed = Map.of(
				"skill-related-tasks.txt", Set.of(20, 21, 24),
				"tasks-mining.txt", Set.of(4, 19, 22),
				"heart-of-the-mountain.txt", Set.of(2, 6, 12, 13, 14, 15, 16, 22, 24, 31, 40, 49));
		for (Map.Entry<String, Set<Integer>> page : changed.entrySet()) {
			assertEquals(page.getValue(), sameUpToNumbers(g1(page.getKey()), g3(page.getKey())), page.getKey());
		}
	}
}

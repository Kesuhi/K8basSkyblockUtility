package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.skyblock.CaptureFixtures;
import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import com.k8bas.skyblockutility.skyblock.SbNumber;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-BEST-09 [A], the menu part (T5.2b, REQ-BEST-10, REQ-BEST-05, REQ-BEST-17, EC-BEST-14): the Bestiary menu pages the
 * player opens parse passively. Real data: the G1 capture of the top Bestiary page (its milestone item) and the G1
 * leveling pages that share the `Fishing ➜` titles, which are no Bestiary pages. The family pages were never captured,
 * so their lore here follows the research (other mods' parsers) and is UNVERIFIED until a capture replaces it
 * (REQ-GS-14): the menu-lore line "Progress to Tier XV: 57.1%", Overall Progress shown and hidden, a maxed family, a
 * locked one.
 */
class BestiaryMenuTest {
	private static final String BAR = " ".repeat(26);

	private static SbNumber exact(long value) {
		return new SbNumber(value, false);
	}

	private static MenuSlot slot(int index, String item, String name, String... lore) {
		return new MenuSlot(index, item, 1, name, List.of(lore), null);
	}

	/** The page's own items as on the G1 top page; that area pages show them too is UNVERIFIED. */
	private static List<MenuSlot> navigation(String toggle) {
		return List.of(slot(48, "minecraft:arrow", "Go Back", "To Bestiary"), slot(49, "minecraft:barrier", "Close"),
				slot(50, "minecraft:oak_sign", "Search Bestiary", "Search for a Family!"),
				slot(51, "minecraft:player_head", "Bestiary Milestone CCCLI", "Total Tiers Unlocked: 3,518", "",
						"Progress to Milestone CCCLII: 80%", BAR + "8/10"),
				slot(52, "minecraft:ender_eye", "Toggle Families Completed Display", "Overall Progress: " + toggle));
	}

	private static MenuSnapshot page(String title, String toggle, MenuSlot... families) {
		List<MenuSlot> slots = new ArrayList<>(List.of(families));
		slots.addAll(navigation(toggle));
		return new MenuSnapshot(title, 7, 54, List.copyOf(slots));
	}

	@Test
	void theCapturedTopPageGivesTheMilestone() {
		MenuSnapshot top = CaptureFixtures.menu(CaptureFixtures.G1, "containers/bestiary.txt").menu();
		assertTrue(BestiaryMenu.matchesTitle(top.getTitle()));
		assertTrue(BestiaryMenu.isBestiaryPage(top));
		BestiaryPage page = BestiaryMenu.parse(top);
		assertNotNull(page);
		assertEquals("Bestiary", page.parent());
		assertEquals("", page.area());
		assertEquals(List.of(), page.families(), "area items are no families");
		// "Bestiary Milestone CCCLI", "Total Tiers Unlocked: 3,518", "Progress to Milestone CCCLII: 80%", "8/10".
		assertEquals(new MenuMilestone(351, 2, 3_518), page.milestone());
	}

	@Test
	void theLevelingPagesThatShareATitleAreNoBestiaryPages() {
		for (String file : List.of("fishing-trophy-fish.txt", "fishing-trophy-frogs.txt", "fishing-dolphin-milestones.txt",
				"fishing-ship-parts.txt", "fishing-ship-crew.txt", "foraging-safari-milestones.txt", "tasks-fishing.txt")) {
			MenuSnapshot menu = CaptureFixtures.menu(CaptureFixtures.G1, "containers/" + file).menu();
			assertFalse(BestiaryMenu.isBestiaryPage(menu), file);
			assertNull(BestiaryMenu.parse(menu), file);
		}
		assertTrue(BestiaryMenu.matchesTitle("Fishing ➜ Trophy Fish"), "a title alone cannot tell, so the reader reads it");
		assertFalse(BestiaryMenu.matchesTitle("Foraging ➜ Safari Milestones"));
		assertFalse(BestiaryMenu.matchesTitle("Collections"));
	}

	@Test
	void anAreaPageGivesItsFamilies() {
		// UNVERIFIED lore (research): Kills, Deaths, the tier progress with its bar, the Overall Progress with its bar.
		MenuSnapshot menu = page("Bestiary ➜ Dwarven Mines", "SHOWN",
				slot(10, "minecraft:player_head", "Ghost XIV", "Kills: 8,928", "Deaths: 3", "", "Progress to Tier XV: 57.1%",
						BAR + "1,428/2,500", "", "Overall Progress: 35.7%", BAR + "8,928/25,000", "", "Click to view!"),
				slot(11, "minecraft:player_head", "Glacite Walker IX", "Kills: 2,346", "Deaths: 0", "", "Progress to Tier X: 34.6%",
						BAR + "346/1,000", "", "Overall Progress: 78.2%", BAR + "2,346/3,000", "", "Click to view!"),
				slot(12, "minecraft:player_head", "Stoneworm XV", "Kills: 250", "Deaths: 1", "", "Overall Progress: 100% (MAX!)",
						BAR + "250/250", "", "Click to view!"),
				slot(13, "minecraft:gray_dye", "Goblin", "You haven't unlocked this Family yet!"),
				slot(4, "minecraft:writable_book", "Dwarven Mines", "Families Found: 100%", BAR + "14/14"));
		assertTrue(BestiaryMenu.matchesTitle(menu.getTitle()));
		assertTrue(BestiaryMenu.isBestiaryPage(menu));
		BestiaryPage page = BestiaryMenu.parse(menu);
		assertNotNull(page);
		assertEquals("Bestiary", page.parent());
		assertEquals("Dwarven Mines", page.area());
		assertEquals(List.of(
				new MenuFamily("Ghost", 14, exact(8_928), 57.1, exact(25_000), false),
				new MenuFamily("Glacite Walker", 9, exact(2_346), 34.6, exact(3_000), false),
				new MenuFamily("Stoneworm", 15, exact(250), null, exact(250), true)), page.families(), "the locked family and the area item are left out");
		// A family with a progress line is not maxed; its tier's cap is not shown, so max is known false.
		assertEquals(false, page.families().get(0).max());
		assertEquals(new MenuMilestone(351, 2, 3_518), page.milestone());
	}

	@Test
	void theOverallProgressHiddenParsesToo() {
		// EC-BEST-14: without the Overall line there is no cap, and a maxed family shows no progress line at all.
		MenuSnapshot menu = page("Bestiary ➜ Dwarven Mines", "HIDDEN",
				slot(10, "minecraft:player_head", "Ghost XIV", "Kills: 8,928", "Deaths: 3", "", "Progress to Tier XV: 57.1%",
						BAR + "1,428/2,500", "", "Click to view!"),
				slot(12, "minecraft:player_head", "Stoneworm XV", "Kills: 250", "Deaths: 1", "", "Click to view!"));
		BestiaryPage page = BestiaryMenu.parse(menu);
		assertNotNull(page);
		// Without a progress line the family may be maxed, but the page does not say so: max is unknown, never false.
		assertEquals(List.of(new MenuFamily("Ghost", 14, exact(8_928), 57.1, null, false),
				new MenuFamily("Stoneworm", 15, exact(250), null, null, null)), page.families());
	}

	@Test
	void everyOtherCapturedMenuIsNoBestiaryPage() {
		// All container captures of G1 and G3: only the top Bestiary page is one, and no slot anywhere is a family.
		int read = 0;
		for (String folder : List.of(CaptureFixtures.G1 + "containers/", CaptureFixtures.G3 + "containers/")) {
			for (String file : files(folder)) {
				MenuSnapshot menu = CaptureFixtures.menu(folder, file).menu();
				assertEquals(file.equals("bestiary.txt"), BestiaryMenu.isBestiaryPage(menu), folder + file);
				assertEquals(List.of(), menu.getSlots().stream().map(BestiaryMenu::family).filter(java.util.Objects::nonNull).toList(), folder + file);
				read++;
			}
		}
		assertTrue(read >= 90, read + " captures");
	}

	private static List<String> files(String folder) {
		try (java.util.stream.Stream<java.nio.file.Path> list = java.nio.file.Files.list(java.nio.file.Path.of(
				BestiaryMenuTest.class.getResource(folder).toURI()))) {
			return list.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".txt")).sorted().toList();
		} catch (java.io.IOException | java.net.URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void aSubPageIsKnownByItsFamiliesAlone() {
		// UNVERIFIED: a Fishing sub-page might show none of the top page's own items; its families' lore still tells.
		MenuSnapshot deaths = new MenuSnapshot("Fishing ➜ Lava", 7, 54, List.of(
				slot(10, "minecraft:player_head", "Lava Blaze IV", "Kills: 40", "Deaths: 2")));
		MenuSnapshot progress = new MenuSnapshot("Fishing ➜ Lava", 7, 54, List.of(
				slot(10, "minecraft:player_head", "Lava Blaze IV", "Kills: 40", "Progress to Tier V: 20%", BAR + "4/20")));
		MenuSnapshot leveling = new MenuSnapshot("Fishing ➜ Lava", 7, 54, List.of(
				slot(10, "minecraft:player_head", "Lava Blaze IV", "Kills: 40", "Total Progress: 20%")));
		assertTrue(BestiaryMenu.isBestiaryPage(deaths));
		assertTrue(BestiaryMenu.isBestiaryPage(progress));
		assertFalse(BestiaryMenu.isBestiaryPage(leveling), "a Kills line alone is no Bestiary page");
	}

	@Test
	void aTierZeroNameKeepsItsLastWord() {
		// UNVERIFIED: a family at tier 0 shows no numeral; the progress line to tier I says the tier is 0.
		assertEquals(new MenuFamily("Barbarian Duke X", 0, exact(0), 0.0, null, false), BestiaryMenu.family(
				slot(10, "minecraft:player_head", "Barbarian Duke X", "Kills: 0", "Deaths: 0", "", "Progress to Tier I: 0%", BAR + "0/1")));
		assertEquals(new MenuFamily("Ghost", 0, exact(2), 40.0, null, false), BestiaryMenu.family(
				slot(10, "minecraft:player_head", "Ghost", "Kills: 2", "Deaths: 0", "", "Progress to Tier I: 40%", BAR + "2/5")));
		// A locked family is no family, whatever its lore shows.
		assertNull(BestiaryMenu.family(slot(13, "minecraft:gray_dye", "Barbarian Duke X", "Kills: 0", "You haven't unlocked this Family yet!")));
		// A Kills line with more after the number (a form the game's strings contain) still gives the count.
		MenuFamily maxed = BestiaryMenu.family(slot(12, "minecraft:player_head", "Stoneworm XV", "Kills: 250 MAXED OUT!", "Deaths: 1"));
		assertNotNull(maxed);
		assertEquals(exact(250), maxed.kills());
	}

	@Test
	void namesNumeralsAndPages() {
		// A name ending in a numeral keeps it: the tier is the next tier of the progress line minus one.
		assertEquals(new MenuFamily("Barbarian Duke X", 5, exact(40), 10.0, null, false), BestiaryMenu.family(
				slot(10, "minecraft:player_head", "Barbarian Duke X V", "Kills: 40", "Deaths: 0", "", "Progress to Tier VI: 10%", BAR + "1/10")));
		// Arabic numerals (a player setting) and an abbreviated count, which stays approximate (REQ-BEST-17).
		MenuFamily arabic = BestiaryMenu.family(slot(10, "minecraft:player_head", "Treasure Hoarder 7", "Kills: 12.4k", "Deaths: 0", "",
				"Progress to Tier 8: 50%", BAR + "6.2k/12.4k"));
		assertNotNull(arabic);
		assertEquals("Treasure Hoarder", arabic.name());
		assertEquals(7, arabic.tier());
		assertTrue(arabic.kills().getApproximate());
		assertNull(arabic.kills().exact());
		// A page prefix, a Fishing sub-page and a Critter Safari biome.
		for (String[] title : new String[][]{{"(1/2) Bestiary ➜ The Catacombs", "Bestiary", "The Catacombs"}, {"Fishing ➜ Lava", "Fishing", "Lava"},
				{"Critter Safari ➜ Icy Biome", "Critter Safari", "Icy Biome"}}) {
			MenuSnapshot menu = page(title[0], "SHOWN", slot(10, "minecraft:player_head", "Bat III", "Kills: 20", "Deaths: 0", "",
					"Progress to Tier IV: 40%", BAR + "2/5"));
			assertTrue(BestiaryMenu.matchesTitle(title[0]), title[0]);
			BestiaryPage page = BestiaryMenu.parse(menu);
			assertNotNull(page, title[0]);
			assertEquals(List.of(title[1], title[2]), List.of(page.parent(), page.area()));
			assertEquals(1, page.families().size());
		}
		// The milestone item alone; a milestone item in Arabic form.
		assertEquals(new MenuMilestone(320, 6, null), BestiaryMenu.milestone(slot(51, "minecraft:player_head", "Bestiary Milestone 320",
				"Progress to Milestone 321: 40%", BAR + "4/10")));
		assertNull(BestiaryMenu.milestone(slot(52, "minecraft:ender_eye", "Toggle Families Completed Display", "Overall Progress: SHOWN")));
	}
}

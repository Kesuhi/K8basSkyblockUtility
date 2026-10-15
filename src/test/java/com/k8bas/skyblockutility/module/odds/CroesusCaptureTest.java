package com.k8bas.skyblockutility.module.odds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.debug.CaptureAllowlist;
import com.k8bas.skyblockutility.skyblock.CaptureFixtures;
import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-ODDS-13, fixture part (T3.10, REQ-GS-14): the real Croesus captures from G3
 * (fixtures/captures/g3-2026-10-15/containers/croesus.txt and catacombs-floor-i.txt, `/ksu debug dump containers on`
 * on Hypixel, 2026-10-15, after an F1 run; see the folder's README). No provisional chest fixture existed (T3.9c,
 * which reads these screens, is not built yet), so these are the first chest fixtures, and T3.9c's tests start from
 * them. They are not the M7 Bedrock chest of AC-ODDS-13's example; its Dark Claymore score stays covered by
 * OddsTablesTest.
 *
 * <p>What the captures pin, read through {@link CaptureFixtures#menu}:
 * <ul>
 *   <li>the titles are "Croesus" and, for one run's chests, "Catacombs - Floor I" (no "The "), both on the capture
 *   allowlist;</li>
 *   <li>the run's menu shows each chest as a player head named after it (Wood, Gold, Diamond) whose lore lists the
 *   contents between "Contents" and the blank line before "Cost": books as "Enchanted Book (&lt;name&gt;)", essence as
 *   "&lt;name&gt; xN";</li>
 *   <li>with "Enchanted Book (…)" unwrapped, every RNG-meter item in those lists is on the shipped F1 table under its
 *   display name, and the essences are not (no line for a non-meter item).</li>
 * </ul>
 */
class CroesusCaptureTest {
	private static final Path RNG_SCORES = Path.of(System.getProperty("k8bas.projectDir", "."))
			.resolve("src/main/resources/assets/k8bas_skyblock_utility/data/thirdparty/neu-repo/odds/rngscore.json");
	private static final Pattern BOOK = Pattern.compile("Enchanted Book \\((.+)\\)");
	private static final Pattern STACK = Pattern.compile("(.+) x\\d+");

	private static MenuSnapshot capture(String file) {
		return CaptureFixtures.menu(CaptureFixtures.G3, "containers/" + file).menu();
	}

	/** The shipped F1 RNG-meter scores by display name (odds.rngscore). */
	private static Map<String, Long> floorOneScores() throws IOException {
		JsonObject table = JsonParser.parseString(Files.readString(RNG_SCORES)).getAsJsonObject();
		Map<String, Long> scores = new LinkedHashMap<>();
		for (JsonElement element : table.getAsJsonArray("rows")) {
			JsonObject row = element.getAsJsonObject();
			if (row.get("case").getAsString().equals("catacombs") && row.get("group").getAsString().equals("F1")) {
				scores.put(row.get("displayName").getAsString(), row.get("score").getAsLong());
			}
		}
		return scores;
	}

	/** The lines between "Contents" and the next blank lore line. */
	private static List<String> contents(MenuSlot chest) {
		List<String> lore = chest.getLore();
		int start = lore.indexOf("Contents") + 1;
		assertTrue(start > 0, chest.getName() + ": " + lore);
		return lore.subList(start, lore.subList(start, lore.size()).indexOf("") + start);
	}

	private static String itemName(String line) {
		Matcher book = BOOK.matcher(line);
		if (book.matches()) {
			return book.group(1);
		}
		Matcher stack = STACK.matcher(line);
		return stack.matches() ? stack.group(1) : line;
	}

	@Test
	void theCapturedTitlesAreOnTheAllowlist() {
		MenuSnapshot croesus = capture("croesus.txt");
		MenuSnapshot run = capture("catacombs-floor-i.txt");
		assertEquals("Croesus", croesus.getTitle());
		assertEquals("Catacombs - Floor I", run.getTitle());
		assertTrue(CaptureAllowlist.matches(croesus.getTitle()));
		assertTrue(CaptureAllowlist.matches(run.getTitle()));
		// Croesus lists the run with its floor; the player in it is the masked "Self".
		MenuSlot runItem = croesus.getSlots().stream().filter(slot -> slot.getName().equals("The Catacombs")).findFirst().orElseThrow();
		assertEquals("Floor I", runItem.getLore().get(0));
		assertTrue(runItem.getLore().contains(" Self: Archer (35)"), runItem.getLore().toString());
	}

	@Test
	void theRunsChestsListTheirContentsInTheLore() {
		Map<String, List<String>> chests = new LinkedHashMap<>();
		for (MenuSlot slot : capture("catacombs-floor-i.txt").getSlots()) {
			if (slot.getItem().equals("minecraft:player_head")) {
				chests.put(slot.getName(), contents(slot));
			}
		}
		assertEquals(Map.of(
				"Wood", List.of("Enchanted Book (Rejuvenate I)", "Undead Essence x5", "Wither Essence x8"),
				"Gold", List.of("Enchanted Book (Infinite Quiver VI)", "Enchanted Book (Rejuvenate I)", "Undead Essence x11", "Wither Essence x6"),
				"Diamond", List.of("Enchanted Book (Feather Falling VI)", "Enchanted Book (Infinite Quiver VI)", "Undead Essence x16", "Wither Essence x11")),
				chests);
	}

	@Test
	void theMeterItemsInTheCapturedChestsAreOnTheShippedFloorOneTable() throws IOException {
		Map<String, Long> scores = floorOneScores();
		Map<String, Map<String, Long>> meterItems = new LinkedHashMap<>();
		for (MenuSlot slot : capture("catacombs-floor-i.txt").getSlots()) {
			if (slot.getItem().equals("minecraft:player_head")) {
				Map<String, Long> found = new LinkedHashMap<>();
				for (String line : contents(slot)) {
					String name = itemName(line);
					if (scores.containsKey(name)) {
						found.put(name, scores.get(name));
					}
				}
				meterItems.put(slot.getName(), found);
			}
		}
		assertEquals(Map.of(
				"Wood", Map.of("Rejuvenate I", 1_272L),
				"Gold", Map.of("Infinite Quiver VI", 3_975L, "Rejuvenate I", 1_272L),
				"Diamond", Map.of("Feather Falling VI", 3_975L, "Infinite Quiver VI", 3_975L)),
				meterItems);
		assertTrue(!scores.containsKey("Undead Essence") && !scores.containsKey("Wither Essence"), "no line for a non-meter item");
	}
}

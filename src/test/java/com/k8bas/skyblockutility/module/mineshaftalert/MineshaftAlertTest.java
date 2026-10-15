package com.k8bas.skyblockutility.module.mineshaftalert;

import com.k8bas.skyblockutility.skyblock.Corpse;
import com.k8bas.skyblockutility.skyblock.CorpseList;
import com.k8bas.skyblockutility.skyblock.InventoryCounts;
import com.k8bas.skyblockutility.skyblock.MenuSlot;
import com.k8bas.skyblockutility.skyblock.MenuSnapshot;
import com.k8bas.skyblockutility.skyblock.MineshaftSnapshot;
import com.k8bas.skyblockutility.skyblock.SlotItem;
import com.k8bas.skyblockutility.skyblock.WidgetPresence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mineshaft entry alert (T3.2, REQ-MSA-01..REQ-MSA-12; AC-MSA-01, AC-MSA-03..AC-MSA-09, AC-MSA-13, AC-MSA-14 [A]).
 * The widget lines and the sidebar code follow the G1 captures (tab-mineshaft.txt, sidebar-mineshaft.txt); the
 * key ids and the inventory-only rule are SPEC's (D-7, restored by R41); the sack reminder follows the G3 capture of the
 * Dwarven Mines Sack (SackKeysTest).
 */
class MineshaftAlertTest {
	private static final String HINT = "TEST HINT";

	private static Corpse corpse(String type, boolean looted) {
		return new Corpse(type, looted);
	}

	private static MineshaftSnapshot shaft(String name, Corpse... corpses) {
		return shaft(corpses.length == 0 ? WidgetPresence.ABSENT : WidgetPresence.PRESENT, name, corpses);
	}

	private static MineshaftSnapshot shaft(WidgetPresence presence, String name, Corpse... corpses) {
		CorpseList list = presence == WidgetPresence.ABSENT ? CorpseList.ABSENT : new CorpseList(presence, List.of(corpses));
		return new MineshaftSnapshot(true, name == null ? null : "OPAL_1", name, name == null, list);
	}

	private static InventoryCounts keys(Map<String, Integer> counts) {
		return new InventoryCounts(counts);
	}

	private static String text(Component message) {
		return message.getString();
	}

	// --- The message (REQ-MSA-05, REQ-MSA-06, REQ-MSA-09)

	@Test
	void theLineListsTheShaftCorpsesAndKeys() {
		// AC-MSA-01.
		Component line = MineshaftAlertText.alert("Opal 1", List.of(corpse("Lapis", false), corpse("Tungsten", false), corpse("Umber", false)),
				keys(Map.of("TUNGSTEN_KEY", 2)), true);
		assertEquals("Opal 1 · 1 Lapis, 1 Tungsten, 1 Umber · Tungsten Key: 2 in inventory · Umber Key: 0 in inventory ⚠", text(line));
	}

	@Test
	void typesAreInTheFixedOrderWithLootedOnesNoted() {
		Component line = MineshaftAlertText.alert("Vanguard", List.of(corpse("Vanguard", false), corpse("Umber", true), corpse("Lapis", false),
				corpse("Umber", false)), keys(Map.of("UMBER_KEY", 5, "SKELETON_KEY", 1)), true);
		assertEquals("Vanguard · 1 Lapis, 2 Umber (1 looted), 1 Vanguard · Umber Key: 5 in inventory · Skeleton Key: 1 in inventory", text(line));
	}

	@Test
	void theShortageMarkerFollowsTheKeysNeeded() {
		// AC-MSA-04.
		Component short1 = MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", false), corpse("Tungsten", false)),
				keys(Map.of("TUNGSTEN_KEY", 1)), true);
		assertTrue(text(short1).endsWith("Tungsten Key: 1 in inventory ⚠"), text(short1));
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), countColour(short1));
		Component enough = MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", false), corpse("Tungsten", false)),
				keys(Map.of("TUNGSTEN_KEY", 2)), true);
		assertTrue(text(enough).endsWith("Tungsten Key: 2 in inventory"), text(enough));
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), countColour(enough));
		Component halfLooted = MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", true), corpse("Tungsten", false)),
				keys(Map.of("TUNGSTEN_KEY", 1)), true);
		assertEquals("Tungsten 1 · 2 Tungsten (1 looted) · Tungsten Key: 1 in inventory", text(halfLooted));
		Component lapis = MineshaftAlertText.alert("Opal 1", List.of(corpse("Lapis", false)), InventoryCounts.EMPTY, true);
		assertEquals("Opal 1 · 1 Lapis · no keys needed", text(lapis));
	}

	@Test
	void anUnknownTypeIsListedByNameAfterTheKnownOnesWithNoKeyEntry() {
		// EC-MSA-07: a type not known yet comes after Lapis..Vanguard, by its raw name, with no key entry.
		Component mixed = MineshaftAlertText.alert("Opal 1", List.of(corpse("Glacite", false), corpse("Umber", false), corpse("Glacite", true)),
				keys(Map.of("UMBER_KEY", 1)), true);
		assertEquals("Opal 1 · 1 Umber, 2 Glacite (1 looted) · Umber Key: 1 in inventory", text(mixed));
		Component only = MineshaftAlertText.alert("Opal 1", List.of(corpse("Glacite", false)), InventoryCounts.EMPTY, true);
		assertEquals("Opal 1 · 1 Glacite", text(only), "no 'no keys needed' for a type that may need one");
		Component withLapis = MineshaftAlertText.alert("Opal 1", List.of(corpse("Glacite", false), corpse("Lapis", false)), InventoryCounts.EMPTY, true);
		assertEquals("Opal 1 · 1 Lapis, 1 Glacite", text(withLapis));
		assertEquals(List.of("Glacite"), MineshaftAlertText.unknownTypes(List.of(corpse("Lapis", false), corpse("Glacite", false), corpse("Glacite", true))));
	}

	@Test
	void theBodyIsYellowSoTheGoldTagStandsApart() {
		// The chat helper's [KSU] tag is gold; a body with no colour of its own would inherit it.
		Component line = MineshaftAlertText.alert("Opal 1", List.of(corpse("Lapis", false), corpse("Umber", false)), InventoryCounts.EMPTY, true);
		Component wrapped = Component.literal("[KSU] ").withStyle(ChatFormatting.GOLD).append(line);
		TextColor yellow = TextColor.fromLegacyFormat(ChatFormatting.YELLOW);
		for (Component part : List.of(wrapped, Component.literal("[KSU] ").withStyle(ChatFormatting.GOLD).append(MineshaftAlertText.unavailable("Opal 1")),
				Component.literal("[KSU] ").withStyle(ChatFormatting.GOLD).append(MineshaftAlertText.hint(widget -> Component.literal(HINT))))) {
			part.visit((style, text) -> {
				if (!text.startsWith("[KSU]") && !text.contains("in inventory")) {
					assertEquals(yellow, style.getColor(), "'" + text + "' in " + part.getString());
				}
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
		}
	}

	/** The colour of the first key entry's count. */
	private static TextColor countColour(Component line) {
		List<TextColor> colours = new ArrayList<>();
		line.visit((style, part) -> {
			if (part.contains("in inventory")) {
				colours.add(style.getColor());
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		assertFalse(colours.isEmpty(), "a key count in " + line.getString());
		return colours.get(0);
	}

	@Test
	void hoveringAKeyEntrySaysSackKeysDoNotOpenACorpse() {
		Component line = MineshaftAlertText.alert("Opal 1", List.of(corpse("Umber", false)), InventoryCounts.EMPTY, true);
		List<String> hovers = new ArrayList<>();
		line.visit((style, part) -> {
			if (style.getHoverEvent() instanceof HoverEvent.ShowText show) {
				hovers.add(show.value().getString());
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		assertTrue(hovers.stream().anyMatch(hover -> hover.contains("sack") && hover.contains("storage") && hover.contains("does not open a corpse")), hovers.toString());
	}

	@Test
	void withoutKeyCountsTheLineHasNoKeyPart() {
		// AC-MSA-09: "Show corpse key counts" OFF.
		Component line = MineshaftAlertText.alert("Opal 1", List.of(corpse("Umber", false)), InventoryCounts.EMPTY, false);
		assertEquals("Opal 1 · 1 Umber", text(line));
	}

	@Test
	void aShortEntryNamesTheKeysInTheSack() {
		// AC-MSA-13 (R41 (a)): the sack as last seen in the Dwarven Mines Sack menu: 3 Tungsten Keys, no Umber Key slot.
		SackKeys sack = new SackKeys();
		sack.menu(new MenuSnapshot("Dwarven Mines Sack", 58, 36, List.of(new MenuSlot(22, "minecraft:paper", 1, "Tungsten Key",
				List.of("Dwarven Mines Sack", "", "Stored: 3/22.2k"), "TUNGSTEN_KEY"))));
		Component line = MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", false), corpse("Umber", false)), InventoryCounts.EMPTY, true, sack);
		assertEquals("Tungsten 1 · 1 Tungsten, 1 Umber · Tungsten Key: 0 in inventory ⚠ (3 in your sack: take them out) · Umber Key: 0 in inventory ⚠",
				text(line), "a known 0 in the sack adds nothing");
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), countColour(line), "the reminder keeps the shortage marker");
		// No sack seen yet: no reminder.
		assertEquals("Tungsten 1 · 1 Tungsten · Tungsten Key: 0 in inventory ⚠",
				text(MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", false)), InventoryCounts.EMPTY, true, SackKeys.read(null))));
		// Enough in the inventory: no reminder.
		List<SlotItem> own = new ArrayList<>(List.of(new SlotItem("TUNGSTEN_KEY", 1)));
		while (own.size() < InventoryCounts.SLOTS) {
			own.add(SlotItem.EMPTY);
		}
		InventoryCounts held = InventoryCounts.of(own);
		Component covered = MineshaftAlertText.alert("Tungsten 1", List.of(corpse("Tungsten", false)), held, true, sack);
		assertEquals("Tungsten 1 · 1 Tungsten · Tungsten Key: 1 in inventory", text(covered));
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), countColour(covered));
		// The Skeleton Key fits no sack: never a reminder.
		assertEquals("Vanguard · 1 Vanguard · Skeleton Key: 0 in inventory ⚠",
				text(MineshaftAlertText.alert("Vanguard", List.of(corpse("Vanguard", false)), InventoryCounts.EMPTY, true, sack)));
	}

	@Test
	void theUnavailableLineCarriesTheSharedHint() {
		// AC-MSA-14: the hint is the shared widget-hint string, replaced here by a test value.
		assertEquals("Opal 1 · corpse list unavailable", text(MineshaftAlertText.unavailable("Opal 1")));
		assertEquals("Opal 1 · no corpses listed", text(MineshaftAlertText.unavailable("Opal 1", true)));
		assertTrue(text(MineshaftAlertText.hint(widget -> Component.literal(HINT + " " + widget))).contains(HINT + " Frozen Corpses"));
	}

	@Test
	void keysAreCountedByIdInTheInventoryOnly() {
		// AC-MSA-03: 3 in the hotbar, 2 in the main inventory, 1 in the offhand (the reader's own slots); a renamed
		// item with another id and the open chest's 4 never reach the counts (InventoryReader reads only these slots).
		List<SlotItem> own = new ArrayList<>(List.of(new SlotItem("UMBER_KEY", 3), new SlotItem("UMBER_KEY", 2), new SlotItem("DIRT", 1)));
		while (own.size() < InventoryCounts.SLOTS - 1) {
			own.add(SlotItem.EMPTY);
		}
		own.add(new SlotItem("UMBER_KEY", 1)); // the offhand, the last slot
		InventoryCounts counts = InventoryCounts.of(own);
		Component line = MineshaftAlertText.alert("Umber 1", List.of(corpse("Umber", false)), counts, true);
		assertTrue(text(line).endsWith("Umber Key: 6 in inventory"), text(line));
	}

	// --- The toggles (REQ-MSA-11)

	@Test
	void bothTogglesAreOnInAFreshConfigAndOffMeansNoMessage() {
		// AC-MSA-09.
		MineshaftAlertConfig config = new MineshaftAlertConfig();
		assertTrue(config.enabled && config.showKeys, "both ON in a fresh config");
		MineshaftAlert.Event alertEvent = new MineshaftAlert.Event.Alert("Opal 1", List.of(corpse("Umber", false)), false);
		assertEquals(List.of("Opal 1 · 1 Umber · Umber Key: 0 in inventory ⚠"),
				MineshaftAlertModule.lines(alertEvent, config, InventoryCounts.EMPTY, widget -> Component.literal(HINT)).stream().map(Component::getString).toList());
		config.showKeys = false;
		assertEquals(List.of("Opal 1 · 1 Umber"),
				MineshaftAlertModule.lines(alertEvent, config, InventoryCounts.EMPTY, widget -> Component.literal(HINT)).stream().map(Component::getString).toList());
		config.enabled = false;
		assertEquals(List.of(), MineshaftAlertModule.lines(alertEvent, config, InventoryCounts.EMPTY, widget -> Component.literal(HINT)), "no message is built");
		config.enabled = true;
		assertEquals(List.of("Opal 1 · corpse list unavailable", HINT),
				MineshaftAlertModule.lines(new MineshaftAlert.Event.Unavailable("Opal 1", true, false), config, InventoryCounts.EMPTY, widget -> Component.literal(HINT))
						.stream().map(Component::getString).toList());
	}

	@Test
	void theTogglesAreOnTheMiningCardAndFoundBySearch() {
		var cards = MineshaftAlertModule.cards(new MineshaftAlertConfig(), enabled -> { });
		assertEquals(1, cards.size());
		var card = cards.get(0);
		assertEquals(com.k8bas.skyblockutility.ui.option.Category.MINING, card.category());
		assertEquals("Mineshaft entry alert", card.toggle().text().title());
		assertEquals("Show corpse key counts", card.options().get(0).text().title());
		for (String word : List.of("mineshaft", "corpse", "key")) {
			assertTrue(card.toggle().text().keywords().contains(word) && card.options().get(0).text().keywords().contains(word), word);
		}
	}

	// --- The state machine (REQ-MSA-01, -04, -07, -08, -12)

	private final List<String> posted = new ArrayList<>();
	private final MineshaftAlert alert = new MineshaftAlert(event -> posted.add(describe(event)));

	private static String describe(MineshaftAlert.Event event) {
		return switch (event) {
			case MineshaftAlert.Event.Alert a -> (a.getFollowUp() ? "follow-up " : "alert ") + a.getName() + " " + a.getCorpses().size();
			case MineshaftAlert.Event.Unavailable u -> (u.getEmpty() ? "no corpses " : "unavailable ") + u.getName() + (u.getHint() ? " +hint" : "");
			default -> event.toString();
		};
	}

	/** Ticks every 50 ms from {@code from} to {@code to} seconds with the snapshot. */
	private void run(double from, double to, MineshaftSnapshot snapshot) {
		for (long ms = Math.round(from * 1000); ms < Math.round(to * 1000); ms += 50) {
			alert.tick(snapshot, ms);
			if (!posted.isEmpty() && posted.get(posted.size() - 1).startsWith("@")) {
				break;
			}
		}
	}

	private int at(double seconds) {
		return (int) Math.round(seconds * 1000);
	}

	@Test
	void theAlertWaitsForAStableList() {
		// AC-MSA-05 (a): empty until 2.0 s, 2 corpses at 2.0 s, 3 at 3.0 s: one alert at 4.5 s listing 3.
		alert.enter("m1", 0);
		run(0, 2.0, shaft("Opal 1"));
		run(2.0, 3.0, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		assertEquals(List.of(), posted);
		run(3.0, 4.45, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of(), posted, "not before 4.5 s");
		run(4.45, 10, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 3"), posted);
		assertEquals(at(4.5), alert.lastPostedAt());
	}

	@Test
	void aLateCorpseGivesOneFollowUp() {
		// AC-MSA-05 (b), (c), (d).
		alert.enter("m1", 0);
		run(0, 2.0, shaft("Opal 1"));
		run(2.0, 4.5, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2"), posted);
		assertEquals(at(3.5), alert.lastPostedAt());
		run(4.5, 7, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2", "follow-up Opal 1 3"), posted);
		assertEquals(at(6.0), alert.lastPostedAt());
		// (d) a corpse looted: no message.
		run(7, 20, shaft("Opal 1", corpse("Lapis", true), corpse("Umber", false), corpse("Umber", false)));
		// (c) growth after 30 s: none.
		run(31, 40, shaft("Opal 1", corpse("Lapis", true), corpse("Umber", false), corpse("Umber", false), corpse("Tungsten", false)));
		assertEquals(2, posted.size(), posted.toString());
	}

	@Test
	void noWidgetGivesTheUnavailableLineAndTheHintOncePerLaunch() {
		// AC-MSA-06.
		alert.enter("m1", 0);
		run(0, 7.95, shaft("Opal 1"));
		assertEquals(List.of(), posted);
		run(7.95, 12, shaft("Opal 1"));
		assertEquals(List.of("unavailable Opal 1 +hint"), posted);
		run(12, 15, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("unavailable Opal 1 +hint", "follow-up Opal 1 1"), posted);
		assertEquals(at(13.5), alert.lastPostedAt());
		alert.enter("m2", 100_000);
		for (long ms = 100_000; ms < 110_000; ms += 50) {
			alert.tick(shaft("Topaz 1"), ms);
		}
		assertEquals("unavailable Topaz 1", posted.get(posted.size() - 1), "no second hint in the same launch");
	}

	@Test
	void oneAlertPerServerAndNoneAfterLeaving() {
		// AC-MSA-07.
		alert.enter("m1", 0);
		run(0, 5, shaft("Opal 1", corpse("Lapis", false)));
		alert.enter("m1", 5000);
		run(5, 12, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("alert Opal 1 1"), posted, "mineshaft reported twice for one server: one alert");
		alert.enter("m2", 20_000);
		for (long ms = 20_000; ms < 25_000; ms += 50) {
			alert.tick(shaft("Jade 1", corpse("Umber", false)), ms);
		}
		assertEquals(List.of("alert Opal 1 1", "alert Jade 1 1"), posted, "a new server id: a new alert");
		alert.enter("m3", 30_000);
		alert.tick(shaft("Ruby 1", corpse("Umber", false)), 30_500);
		alert.leave();
		for (long ms = 31_000; ms < 40_000; ms += 50) {
			alert.tick(shaft("Ruby 1", corpse("Umber", false)), ms);
		}
		assertEquals(2, posted.size(), "left at 1.0 s, before the list settled: no alert");
	}

	@Test
	void anEmptyWidgetSaysNoCorpsesListedAndKeepsTheHint() {
		// EC-MSA-06: the header is shown with no lines, so the widget is on; no hint, and the launch's hint stays unused.
		alert.enter("m1", 0);
		run(0, 12, shaft(WidgetPresence.EMPTY, "Opal 1"));
		assertEquals(List.of("no corpses Opal 1"), posted);
		assertEquals(at(8.0), alert.lastPostedAt());
		run(12, 15, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("no corpses Opal 1", "follow-up Opal 1 1"), posted, "a list that comes later is the follow-up");
		alert.enter("m2", 100_000);
		for (long ms = 100_000; ms < 110_000; ms += 50) {
			alert.tick(shaft("Topaz 1"), ms);
		}
		assertEquals("unavailable Topaz 1 +hint", posted.get(posted.size() - 1), "the hint is still there for a widget that is off");
	}

	@Test
	void aCutOffListIsNoList() {
		// REQ-GS-10, EC-MSA-05: a PARTIAL widget never gives counts or a green key verdict; it is handled as missing.
		alert.enter("m1", 0);
		run(0, 12, shaft(WidgetPresence.PARTIAL, "Opal 1", corpse("Umber", false)));
		assertEquals(List.of("unavailable Opal 1 +hint"), posted);
		run(12, 15, shaft("Opal 1", corpse("Umber", false), corpse("Tungsten", false)));
		assertEquals(List.of("unavailable Opal 1 +hint", "follow-up Opal 1 2"), posted, "the whole list, once it is whole");
	}

	@Test
	void reEnteringBeforeAnythingWasPostedStartsOver() {
		// REQ-MSA-12: left at 1 s before the list settled, back on the same server at 60 s (a party warp back).
		alert.enter("m1", 0);
		run(0, 1, shaft("Opal 1"));
		alert.leave();
		alert.enter("m1", 60_000);
		run(60, 61, shaft("Opal 1"));
		assertEquals(List.of(), posted, "no stale 'corpse list unavailable'");
		run(61, 65, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("alert Opal 1 1"), posted);
		assertEquals(at(62.5), alert.lastPostedAt());
	}

	@Test
	void aLateListCanStillHaveADeadMansChestFollowUp() {
		// REQ-MSA-01: the REQ-MSA-08 follow-up and the REQ-MSA-07 follow-up are separate.
		alert.enter("m1", 0);
		run(0, 10, shaft("Opal 1"));
		run(10, 20, shaft("Opal 1", corpse("Lapis", false)));
		run(20, 25, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		assertEquals(List.of("unavailable Opal 1 +hint", "follow-up Opal 1 1", "follow-up Opal 1 2"), posted);
		assertEquals(at(21.5), alert.lastPostedAt());
		run(25, 28, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(3, posted.size(), "one growth follow-up only: " + posted);
	}

	@Test
	void growthAfterThirtySecondsIsIgnored() {
		// AC-MSA-05 (c), with no follow-up before it: the window alone stops it. At 30.0 s it still counts.
		alert.enter("m1", 0);
		run(0, 2.0, shaft("Opal 1"));
		run(2.0, 30.05, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		run(30.05, 40, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2"), posted);
		alert.enter("m2", 100_000);
		run(100, 102, shaft("Opal 1"));
		run(102, 130, shaft("Opal 1", corpse("Lapis", false)));
		run(130, 135, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2", "alert Opal 1 1", "follow-up Opal 1 2"), posted, "growth at exactly 30.0 s counts");
	}

	@Test
	void aListAfterThirtySecondsGivesNothingAfterTheUnavailableLine() {
		// REQ-MSA-08: only a list within the 30 s window gives the follow-up.
		alert.enter("m1", 0);
		run(0, 30.05, shaft("Opal 1"));
		run(30.05, 40, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("unavailable Opal 1 +hint"), posted);
	}

	@Test
	void theWindowSurvivesATickWithoutAWholeList() {
		// A corpse at 29.9 s, then the widget is cut off for two ticks while the tab rows shift: still the follow-up.
		alert.enter("m1", 0);
		run(0, 2.0, shaft("Opal 1"));
		run(2.0, 29.9, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		run(29.9, 30.0, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		run(30.0, 30.1, shaft(WidgetPresence.PARTIAL, "Opal 1", corpse("Lapis", false)));
		run(30.1, 35, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2", "follow-up Opal 1 3"), posted);
	}

	@Test
	void reEnteringAfterOnlyTheUnavailableLineGivesNoSecondOne() {
		// REQ-MSA-01: the unavailable line is the first alert for this server id.
		alert.enter("m1", 0);
		run(0, 10, shaft("Opal 1"));
		alert.leave();
		alert.enter("m1", 20_000);
		run(20, 40, shaft("Opal 1"));
		assertEquals(List.of("unavailable Opal 1 +hint"), posted);
	}

	@Test
	void theWindowCountsFromTheGrowthNotFromALaterLoot() {
		// REQ-MSA-07: a corpse at 29.5 s, one looted at 30.2 s before it settled: the follow-up still comes.
		alert.enter("m1", 0);
		run(0, 2.0, shaft("Opal 1"));
		run(2.0, 29.5, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false)));
		run(29.5, 30.2, shaft("Opal 1", corpse("Lapis", false), corpse("Umber", false), corpse("Umber", false)));
		run(30.2, 35, shaft("Opal 1", corpse("Lapis", true), corpse("Umber", false), corpse("Umber", false)));
		assertEquals(List.of("alert Opal 1 2", "follow-up Opal 1 3"), posted);
		assertEquals(at(31.7), alert.lastPostedAt());
	}

	@Test
	void unknownShaftOnlyAfterTheCodeSearch() {
		// REQ-MSA-02: no widget and no code yet at 8 s: the unavailable line waits for the name.
		alert.enter("m1", 0);
		run(0, 9, shaft(null));
		assertEquals(List.of(), posted, "not 'Unknown shaft' while the code search runs");
		run(9, 10, shaft("Opal 1"));
		assertEquals(List.of("unavailable Opal 1 +hint"), posted, "the code came at 9 s");
		assertEquals(at(9.0), alert.lastPostedAt());
	}

	@Test
	void theAlertWaitsForTheShaftName() {
		// The name comes from the mineshaft state; while its code search runs, the alert waits for it.
		alert.enter("m1", 0);
		run(0, 3, shaft(null, corpse("Lapis", false)));
		assertEquals(List.of(), posted);
		run(3, 5, shaft("Opal 1", corpse("Lapis", false)));
		assertEquals(List.of("alert Opal 1 1"), posted);
	}

	@Test
	void theModuleSendsNothingAndReadsNoEntity() throws IOException {
		// AC-MSA-08 [R], AC-MSA-02 [R], AC-MSA-14 [R]: no server-bound chat, command or click, no entity access, no
		// shaft-code parser and no hard-coded widget command in this module's sources.
		Path dir = Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("src/main/kotlin/com/k8bas/skyblockutility/module/mineshaftalert");
		List<String> hits = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String code = Files.readString(file);
				for (String forbidden : List.of("sendCommand", "sendChat", "connection.send", "handleInventory", "closeContainer", "getEntities",
						"ArmorStand", "[A-Z]{4}_", "/tablist", "/widget", "/widgets")) {
					if (code.contains(forbidden)) {
						hits.add(file.getFileName() + ": " + forbidden);
					}
				}
			}
		}
		assertEquals(List.of(), hits);
	}
}

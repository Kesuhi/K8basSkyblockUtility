package com.k8bas.skyblockutility.module.bestiary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T5.3, the HUD's lines on the layout model [A]: AC-BEST-07 (a fresh config shows exactly the default ON lines, the
 * milestone among them once known; hiding a line type removes exactly that line; a changed order is rendered in that
 * order; the Roman/Arabic setting switches "XV" and "15"), REQ-BEST-04 ("MAX"), REQ-BEST-05 (the milestone only while
 * known), REQ-BEST-17 ("≈" on an abbreviated value and what follows from it), EC-BEST-02, and AC-BEST-11 (the widget
 * hint after 10 s without a Bestiary section, never in the Dungeon Hub or while the island is unknown).
 */
class BestiaryHudLinesTest {
	private static final String DWARVEN = "Dwarven Mines";
	private static final long MINUTE = 60_000;

	private final BestiaryTracker tracker = new BestiaryTracker(BestiaryTables.shipped(), line -> { });

	/** Ghost at 12,449 kills after 100 kills over 5 minutes, and milestone 262 with 7 tiers to the next. */
	private void ghostSession() {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12,349/12,500")), DWARVEN, 0);
		for (int i = 1; i <= 10; i++) {
			tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: " + String.format(java.util.Locale.ROOT, "%,d", 12_349 + 10 * i) + "/12,500")),
					DWARVEN, i * 30_000L);
		}
		tracker.chat(new BestiaryChatEvent.Milestone(261, 262, false), DWARVEN, 5 * MINUTE);
		for (int i = 0; i < 3; i++) {
			tracker.chat(new BestiaryChatEvent.TierUp("Glacite Walker", 6 + i, 7 + i), DWARVEN, 5 * MINUTE);
		}
	}

	private List<String> lines(BestiaryLayout layout, long now) {
		return BestiaryHudLines.lines(List.of(tracker.family("ghost")), key -> tracker.rate(key, now), tracker.session(), tracker.milestone(), layout, now);
	}

	@Test
	void theTexts() {
		assertEquals("XV", BestiaryFormat.roman(15));
		assertEquals("CCLXII", BestiaryFormat.roman(262));
		assertEquals("0", BestiaryFormat.roman(0), "tier 0 has no Roman numeral");
		assertEquals("12,449", BestiaryFormat.grouped(12_449));
		assertEquals("12.4k", BestiaryFormat.compact(12_400));
		assertEquals("1.5M", BestiaryFormat.compact(1_500_000));
		assertEquals("950", BestiaryFormat.compact(950));
		assertEquals("1.0M", BestiaryFormat.compact(999_950), "never 1000.0k");
		assertEquals("2m33s", BestiaryFormat.duration(153_000), "AC-BEST-05");
		assertEquals("45s", BestiaryFormat.duration(45_000));
		assertEquals("1h02m", BestiaryFormat.duration(3_720_000));
		assertEquals("just now", BestiaryFormat.age(30_000));
		assertEquals("12 min ago", BestiaryFormat.age(12 * MINUTE));
		assertEquals("3 h ago", BestiaryFormat.age(3 * 60 * MINUTE));
		assertEquals("4 days ago", BestiaryFormat.age(4 * 24 * 60 * MINUTE));
	}

	@Test
	void aFreshConfigShowsExactlyTheDefaultLines() {
		ghostSession();
		assertEquals(EnumSet.of(BestiaryLine.FAMILY, BestiaryLine.KILLS, BestiaryLine.TO_NEXT, BestiaryLine.MILESTONE, BestiaryLine.RATE,
				BestiaryLine.SESSION_KILLS), BestiaryLayout.DEFAULT.shown(), "§12.H");
		assertEquals(List.of(
				"§6Ghost §e15§7/20",
				"§7Kills: §f12,449",
				"§7To tier 16: §f51",
				"§7Milestone: §f262 §8(7 tiers to next)",
				"§7Rate: §f1,200/h",
				"§7Session: §f+100"), lines(BestiaryLayout.DEFAULT, 5 * MINUTE));
	}

	@Test
	void everyLineTypeAndItsHiding() {
		ghostSession();
		BestiaryLayout all = new BestiaryLayout(EnumSet.allOf(BestiaryLine.class), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC);
		List<String> every = lines(all, 5 * MINUTE);
		assertEquals(List.of(
				"§6Ghost §e15§7/20",
				"§7Kills: §f12,449",
				"§7To tier 16: §f51",
				"§7To max: §f12,551",
				"§7Milestone: §f262 §8(7 tiers to next)",
				"§7Rate: §f1,200/h",
				"§7ETA: §f2m33s §8(max 10h28m)",
				"§7Session: §f+100",
				"§7All families: §f+100",
				"§8widget, just now"), every);
		// Hiding one line type removes exactly that line.
		for (BestiaryLine hidden : BestiaryLine.values()) {
			Set<BestiaryLine> shown = EnumSet.allOf(BestiaryLine.class);
			shown.remove(hidden);
			List<String> expected = new ArrayList<>(every);
			expected.remove(LineOrder.PROGRESS_FIRST.getLines().indexOf(hidden));
			assertEquals(expected, lines(new BestiaryLayout(shown, LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC), 5 * MINUTE), hidden.name());
		}
	}

	@Test
	void aChangedOrderIsRenderedInThatOrder() {
		ghostSession();
		List<BestiaryLine> order = List.of(BestiaryLine.SESSION_KILLS, BestiaryLine.MILESTONE, BestiaryLine.FAMILY, BestiaryLine.RATE,
				BestiaryLine.KILLS, BestiaryLine.TO_NEXT);
		assertEquals(List.of("§7Session: §f+100", "§7Milestone: §f262 §8(7 tiers to next)", "§6Ghost §e15§7/20", "§7Rate: §f1,200/h",
				"§7Kills: §f12,449", "§7To tier 16: §f51"), lines(new BestiaryLayout(BestiaryLayout.DEFAULT.shown(), order, TierNumerals.ARABIC), 5 * MINUTE));
		// Every preset holds every line type once.
		for (LineOrder preset : LineOrder.values()) {
			assertEquals(EnumSet.allOf(BestiaryLine.class), EnumSet.copyOf(preset.getLines()), preset.name());
			assertEquals(BestiaryLine.values().length, preset.getLines().size(), preset.name());
		}
		assertEquals(LineOrder.PROGRESS_FIRST.getLines(), BestiaryLayout.DEFAULT.order());
	}

	@Test
	void romanOrArabic() {
		ghostSession();
		List<String> roman = lines(new BestiaryLayout(BestiaryLayout.DEFAULT.shown(), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ROMAN), 5 * MINUTE);
		assertEquals("§6Ghost §eXV§7/XX", roman.get(0));
		assertEquals("§7To tier XVI: §f51", roman.get(2));
		assertEquals("§7Milestone: §fCCLXII §8(7 tiers to next)", roman.get(3));
		assertEquals("§6Ghost §e15§7/20", lines(BestiaryLayout.DEFAULT, 5 * MINUTE).get(0));
	}

	@Test
	void theMilestoneOnlyWhileKnown() {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12,449/12,500")), DWARVEN, 0);
		assertFalse(lines(BestiaryLayout.DEFAULT, 1_000).stream().anyMatch(line -> line.contains("Milestone")));
		// With no family seen, the milestone alone; with nothing known, no line at all.
		BestiaryTracker empty = new BestiaryTracker(BestiaryTables.shipped(), line -> { });
		assertEquals(List.of(), BestiaryHudLines.lines(List.of(), key -> empty.rate(key, 0), empty.session(), null, BestiaryLayout.DEFAULT, 0));
		assertEquals(List.of("§7Milestone: §f351 §8(2 tiers to next)"), BestiaryHudLines.lines(List.of(), key -> empty.rate(key, 0), empty.session(),
				new MilestoneView(351, 2, BestiarySource.MENU, 0), BestiaryLayout.DEFAULT, 0));
		assertEquals(List.of("§7Milestone: §f265"), BestiaryHudLines.lines(List.of(), key -> empty.rate(key, 0), empty.session(),
				new MilestoneView(265, null, BestiarySource.CHAT, 0), BestiaryLayout.DEFAULT, 0), "its tiers unknown after a claim");
	}

	@Test
	void anAbbreviatedValueAndWhatFollowsFromItGetTheSign() {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: 12.4k/12,500")), DWARVEN, 0);
		BestiaryLayout all = new BestiaryLayout(EnumSet.allOf(BestiaryLine.class), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC);
		List<String> shown = lines(all, 2 * MINUTE);
		assertTrue(shown.contains("§7Kills: §f≈12.4k"), shown.toString());
		assertTrue(shown.contains("§7To tier 16: §f≈100"), shown.toString());
		assertTrue(shown.contains("§7To max: §f≈12,600"), shown.toString());
		assertTrue(shown.contains("§7Rate: §f—"), "no rate from an abbreviated count (EC-BEST-17): " + shown);
		assertTrue(shown.contains("§7ETA: §f—"), shown.toString());
	}

	@Test
	void maxHidesToNext() {
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 15: 24,990/25,000")), DWARVEN, 0);
		tracker.widget(List.of(BestiaryWidget.parseLine("Ghost 20: MAX")), DWARVEN, 1_000);
		BestiaryLayout all = new BestiaryLayout(EnumSet.allOf(BestiaryLine.class), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC);
		List<String> shown = lines(all, 2_000);
		assertEquals("§6Ghost §e20 §aMAX", shown.get(0));
		assertFalse(shown.stream().anyMatch(line -> line.startsWith("§7To tier")), shown.toString());
		assertTrue(shown.contains("§7To max: §aMAX"), shown.toString());
	}

	@Test
	void severalFamiliesShowEachTheirLinesThenTheSharedOnes() {
		ghostSession();
		tracker.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Goblin", 15, new com.k8bas.skyblockutility.skyblock.SbNumber(1_000, false),
				10.0, null, false)), null), 4 * MINUTE);
		BestiaryLayout layout = new BestiaryLayout(EnumSet.of(BestiaryLine.FAMILY, BestiaryLine.KILLS, BestiaryLine.MILESTONE, BestiaryLine.SOURCE_AGE),
				List.of(BestiaryLine.MILESTONE, BestiaryLine.FAMILY, BestiaryLine.KILLS, BestiaryLine.SOURCE_AGE), TierNumerals.ARABIC);
		long now = 16 * MINUTE;
		List<String> shown = BestiaryHudLines.lines(List.of(tracker.family("ghost"), tracker.family("goblin")), key -> tracker.rate(key, now), tracker.session(),
				tracker.milestone(), layout, now);
		// The milestone comes first, as its order says: shared lines before the first family line go above the families.
		assertEquals(List.of("§7Milestone: §f262 §8(7 tiers to next)",
				"§6Ghost §e15§7/20", "§7Kills: §f12,449", "§8widget, 11 min ago",
				"§6Goblin §e15§7/20", "§7Kills: §f1,000", "§8menu, 12 min ago"), shown);
		BestiaryLayout milestoneLast = new BestiaryLayout(layout.shown(), List.of(BestiaryLine.FAMILY, BestiaryLine.KILLS, BestiaryLine.SOURCE_AGE,
				BestiaryLine.MILESTONE), TierNumerals.ARABIC);
		List<String> last = BestiaryHudLines.lines(List.of(tracker.family("ghost"), tracker.family("goblin")), key -> tracker.rate(key, now), tracker.session(),
				tracker.milestone(), milestoneLast, now);
		assertEquals("§7Milestone: §f262 §8(7 tiers to next)", last.get(last.size() - 1));
		// A pinned family not seen yet: its name, that it was not seen, and the shared lines all the same.
		FamilyView unseen = tracker.familyOrUnseen("stoneworm");
		assertEquals(List.of("§6Stoneworm", "§8not seen yet", "§7Milestone: §f262 §8(7 tiers to next)"), BestiaryHudLines.lines(List.of(unseen),
				key -> tracker.rate(key, now), tracker.session(), tracker.milestone(), BestiaryLayout.DEFAULT, now));
	}

	@Test
	void theSessionTotalAndATierPastTheTable() {
		ghostSession();
		BestiaryLayout total = new BestiaryLayout(EnumSet.of(BestiaryLine.SESSION_AVERAGE), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC);
		assertEquals(List.of("§7All families: §f+100"), lines(total, 5 * MINUTE), "the total across families, no average under a minute");
		for (long t = 0; t <= 2 * MINUTE; t += 50) {
			tracker.tick(true, t);
		}
		assertEquals(List.of("§7All families: §f+100, 3,000/h §8(2m00s)"), lines(total, 5 * MINUTE));
		// The widget past the table's last tier (EC-BEST-09): no "9/8".
		tracker.widget(List.of(BestiaryWidget.parseLine("Oasis Rabbit 9: 210/300")), null, 6 * MINUTE);
		assertEquals("§6Oasis Rabbit §e9", BestiaryHudLines.lines(List.of(tracker.family("oasis_rabbit")), key -> tracker.rate(key, 0), tracker.session(), null,
				BestiaryLayout.DEFAULT, 6 * MINUTE).get(0));
		assertEquals(List.of("§7Milestone: §f262 §8(1 tier to next)"), BestiaryHudLines.lines(List.of(), key -> tracker.rate(key, 0), tracker.session(),
				new MilestoneView(262, 1, BestiarySource.CHAT, 0), BestiaryLayout.DEFAULT, 0));
	}

	@Test
	void theHintIsWrappedToStayOffTheCrosshair() {
		String hint = "The Bestiary widget is off in your tab list. Turn it on with /widgets.";
		List<String> wrapped = BestiaryHudLines.wrap(hint, 34);
		assertEquals(hint, String.join(" ", wrapped), "no word lost");
		assertTrue(wrapped.size() > 1);
		for (String line : wrapped) {
			assertTrue(line.length() <= 34, line);
		}
		assertEquals(List.of("short"), BestiaryHudLines.wrap("short", 34));
	}

	@Test
	void theSettingsGiveTheirLayout() {
		BestiaryConfig config = new BestiaryConfig();
		assertEquals(BestiaryLayout.DEFAULT, config.layout());
		// Each line field shows exactly its own line.
		for (BestiaryLine line : BestiaryLine.values()) {
			BestiaryConfig one = new BestiaryConfig();
			for (java.lang.reflect.Field field : BestiaryConfig.class.getFields()) {
				if (field.getName().startsWith("line") && field.getType() == boolean.class) {
					try {
						field.setBoolean(one, false);
					} catch (IllegalAccessException e) {
						throw new AssertionError(e);
					}
				}
			}
			String name = "line" + java.util.Arrays.stream(line.name().toLowerCase(java.util.Locale.ROOT).split("_"))
					.map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).reduce("", String::concat);
			try {
				BestiaryConfig.class.getField(name).setBoolean(one, true);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(name, e);
			}
			assertEquals(Set.of(line), one.layout().shown(), name);
		}
		config.lineOrder = LineOrder.SESSION_FIRST;
		config.numerals = TierNumerals.ROMAN;
		assertEquals(LineOrder.SESSION_FIRST.getLines(), config.layout().order());
		assertEquals(TierNumerals.ROMAN, config.layout().numerals());
	}

	@Test
	void theEditorPreviewFollowsTheSettings() {
		// AC-BEST-01: preview data in the editor; with the settings' lines, order and numerals (AC-BEST-07).
		assertEquals(List.of("§6Ghost §e15§7/20", "§7Kills: §f12,449", "§7To tier 16: §f51", "§7Milestone: §f262 §8(7 tiers to next)",
				"§7Rate: §f1,200/h", "§7Session: §f+100"), BestiaryHud.previewLines(BestiaryLayout.DEFAULT, BestiaryTables.shipped()));
		BestiaryLayout changed = new BestiaryLayout(EnumSet.of(BestiaryLine.FAMILY, BestiaryLine.SESSION_KILLS), LineOrder.SESSION_FIRST.getLines(),
				TierNumerals.ROMAN);
		assertEquals(List.of("§6Ghost §eXV§7/XX", "§7Session: §f+100"), BestiaryHud.previewLines(changed, BestiaryTables.shipped()));
		assertEquals("§6Ghost §e15", BestiaryHud.previewLines(BestiaryLayout.DEFAULT, null).get(0), "without the table: no last tier");
	}

	@Test
	void theWidgetHintAfterTenSecondsWithoutTheWidget() {
		// AC-BEST-11: no Bestiary section for 10 s shows the hint on any island but the Dungeon Hub, not while unknown.
		BestiaryWidgetWatch watch = new BestiaryWidgetWatch();
		watch.restart(0);
		assertFalse(watch.hint("Hub", 9_999));
		assertTrue(watch.hint("Hub", 10_000));
		assertTrue(watch.hint("Crystal Hollows", 60_000));
		assertFalse(watch.hint("Dungeon Hub", 60_000), "the Dungeon Hub has no Bestiary section (R24)");
		assertFalse(watch.hint(null, 60_000), "an unknown island (a transfer)");
		watch.seen(60_000);
		assertFalse(watch.hint("Hub", 69_999));
		assertTrue(watch.hint("Hub", 70_000));
		// A new world starts a fresh 10 s.
		watch.restart(80_000);
		assertFalse(watch.hint("Hub", 89_999));
	}

	@Test
	void theCompactLayoutIsOneLine() {
		// T5.3b (REQ-BEST-08 "may"): the enabled lines joined; with compact OFF nothing changes (AC-BEST-07 above).
		ghostSession();
		BestiaryLayout compact = new BestiaryLayout(BestiaryLayout.DEFAULT.shown(), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ARABIC, true);
		// Joined at the separators into lines of at most 60 visible characters, so nothing runs off the screen at GUI scale 4.
		assertEquals(List.of("§6Ghost §e15§7/20 §8· §7Kills: §f12,449 §8· §7To tier 16: §f51",
				"§7Milestone: §f262 §8(7 tiers to next) §8· §7Rate: §f1,200/h", "§7Session: §f+100"), lines(compact, 5 * MINUTE));
		BestiaryLayout everything = new BestiaryLayout(EnumSet.allOf(BestiaryLine.class), LineOrder.PROGRESS_FIRST.getLines(), TierNumerals.ROMAN, true);
		for (String line : lines(everything, 5 * MINUTE)) {
			assertTrue(com.k8bas.skyblockutility.skyblock.GameText.strip(line).length() <= 60, line);
		}
		// Short groups stay one line; an unseen family and the shared lines alone too.
		assertEquals(List.of("§6Stoneworm §8· §8not seen yet", "§7Milestone: §f262 §8(7 tiers to next)"), BestiaryHudLines.lines(
				List.of(tracker.familyOrUnseen("stoneworm")), key -> tracker.rate(key, 0), tracker.session(), tracker.milestone(), compact, 5 * MINUTE));
		assertEquals(List.of("§7Milestone: §f262 §8(7 tiers to next)"), BestiaryHudLines.lines(List.of(), key -> tracker.rate(key, 0), tracker.session(),
				tracker.milestone(), compact, 5 * MINUTE));
		assertFalse(BestiaryLayout.DEFAULT.compact(), "OFF by default");
		// Several families: a line each, and one for the shared lines.
		tracker.menu(new BestiaryPage("Bestiary", DWARVEN, List.of(new MenuFamily("Goblin", 15, new com.k8bas.skyblockutility.skyblock.SbNumber(1_000, false),
				10.0, null, false)), null), 4 * MINUTE);
		BestiaryLayout two = new BestiaryLayout(EnumSet.of(BestiaryLine.FAMILY, BestiaryLine.KILLS, BestiaryLine.MILESTONE), LineOrder.PROGRESS_FIRST.getLines(),
				TierNumerals.ARABIC, true);
		assertEquals(List.of("§6Ghost §e15§7/20 §8· §7Kills: §f12,449", "§6Goblin §e15§7/20 §8· §7Kills: §f1,000", "§7Milestone: §f262 §8(7 tiers to next)"),
				BestiaryHudLines.lines(List.of(tracker.family("ghost"), tracker.family("goblin")), key -> tracker.rate(key, 0), tracker.session(), tracker.milestone(),
						two, 5 * MINUTE));
		// The setting gives it.
		BestiaryConfig config = new BestiaryConfig();
		assertFalse(config.layout().compact());
		config.compact = true;
		assertTrue(config.layout().compact());
	}
}

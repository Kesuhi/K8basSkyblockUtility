package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterOptions;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRuleCards;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRuleCards;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchOptions;
import com.k8bas.skyblockutility.settings.RuleFields;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import com.k8bas.skyblockutility.ui.option.TextOption;
import com.k8bas.skyblockutility.ui.widget.Widget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-09, REQ-UI-11, REQ-UI-12, REQ-LOC-09, EC-UI-07/09/17 (T2.5a): the rule cards of both lists. */
class RuleCardsTest {
	private final MobHighlighterConfig mobs = new MobHighlighterConfig();
	private final NpcSearchConfig npcs = new NpcSearchConfig();
	private int changes;
	/** The regex part of the real check (HighlightManager.inertReason needs the game's entity registry). */
	private static String problem(HighlightRule rule) {
		if (rule.nameMatchMode == NameMatchMode.REGEX) {
			try {
				Pattern.compile(rule.namePattern);
			} catch (PatternSyntaxException e) {
				return "the regular expression is invalid (" + e.getDescription() + ")";
			}
		}
		return rule.namePattern == null || rule.namePattern.isEmpty() ? "the name pattern is empty" : null;
	}

	private final MobRuleCards mobCards = new MobRuleCards(mobs, RuleCardsTest::problem, () -> changes++);
	private final NpcRuleCards npcCards = new NpcRuleCards(npcs, rule -> null, rule -> "12, 70, -34", () -> changes++);

	private static HighlightRule mob(String id, String label, String pattern) {
		HighlightRule rule = new HighlightRule();
		rule.id = id;
		rule.label = label;
		rule.namePattern = pattern;
		return rule;
	}

	private static NpcRule npc(String id, String label, boolean fixed) {
		NpcRule rule = new NpcRule();
		rule.id = id;
		rule.label = label;
		rule.fixed = fixed;
		rule.namePattern = label;
		return rule;
	}

	private List<Card> cards() {
		List<Card> cards = new ArrayList<>(MobHighlighterOptions.cards(mobs, new GeneralConfig(), enabled -> { }, mobCards::groups));
		cards.addAll(NpcSearchOptions.cards(npcs, enabled -> { }, () -> { }, npcCards::groups));
		return cards;
	}

	private static <T extends Option> T field(RuleGroup rule, Class<T> kind, String suffix) {
		return rule.fields().stream().filter(kind::isInstance).map(kind::cast).filter(option -> option.id().endsWith(suffix)).findFirst()
				.orElseThrow(() -> new AssertionError("no " + suffix + " in " + rule.id()));
	}

	/** REQ-UI-11: a header per rule under its feature; an open rule shows every field, indented, and a fixed NPC its position. */
	@Test
	void rulesAreCollapsibleCardsUnderTheirFeature() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		mobs.rules.add(mob("mob-2", "Voidgloom", "Voidgloom"));
		List<RuleGroup> groups = mobCards.groups();
		ConfigLayout.Page closed = ConfigLayout.page(cards(), Category.HIGHLIGHTS, 500, text -> text.length() * 6, ConfigLayout.Filter.of(o -> true));
		List<ConfigLayout.RuleHeader> headers = closed.rows().stream().filter(row -> row instanceof ConfigLayout.RuleHeader)
				.map(row -> (ConfigLayout.RuleHeader) row).toList();
		assertEquals(List.of(groups.get(0), groups.get(1)), headers.stream().map(ConfigLayout.RuleHeader::rule).toList(), "in list order");
		assertTrue(headers.stream().noneMatch(ConfigLayout.RuleHeader::expanded));

		ConfigLayout.Page open = ConfigLayout.page(cards(), Category.HIGHLIGHTS, 500, text -> text.length() * 6, new ConfigLayout.Filter() {
			@Override
			public boolean shows(Option option) {
				return true;
			}

			@Override
			public boolean expanded(RuleGroup rule) {
				return rule == groups.get(0);
			}
		});
		List<String> fields = open.rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && option.indent() > 0)
				.map(row -> ((ConfigLayout.OptionRow) row).option().id().replace("mob_highlighter.rule.mob-1.", "")).toList();
		assertEquals(List.of("enabled", "label", "type", "island", "mode", "pattern", "colour", "delete"), fields);

		npcs.rules.add(npc("npc-1", "Trinity", false));
		npcs.rules.add(npc("npc-2", "Dungeon Hub Mort", true));
		RuleGroup fixed = npcCards.groups().get(1);
		assertEquals("12, 70, -34", field(fixed, InfoOption.class, ".position").value().get(), "a fixed NPC shows its position, read-only");
		assertTrue(fixed.fields().stream().noneMatch(option -> option.id().endsWith(".pattern") || option.id().endsWith(".mode")));
	}

	/** Every edit is stored on the rule at once and applied (REQ-UI-15); Delete removes the rule. */
	@Test
	void editsAreStoredAndAppliedAtOnce() {
		HighlightRule rule = mob("mob-1", "Zealot", "Zealot");
		mobs.rules.add(rule);
		RuleGroup group = mobCards.groups().get(0);
		field(group, TextOption.class, ".label").binding().set("Zealot Bruiser");
		field(group, TextOption.class, ".pattern").binding().set("Bruiser");
		field(group, ColorOption.class, ".colour").binding().set(0x123456);
		field(group, TextOption.class, ".type").binding().set("  ");
		assertEquals("Zealot Bruiser", rule.label);
		assertEquals("Bruiser", rule.namePattern);
		assertEquals(0x123456, rule.color);
		assertNull(rule.entityTypeId, "a blank type is any type");
		assertEquals(4, changes, "each edit applied at once");
		field(group, ActionOption.class, ".delete").action().run();
		assertTrue(mobs.rules.isEmpty());
		assertTrue(mobCards.groups().isEmpty());
		assertEquals(5, changes);
	}

	/** REQ-LOC-09: the island comes from the list, "Any island" is none, and a stored name off the list is kept. */
	@Test
	void theIslandIsPickedFromTheList() {
		HighlightRule rule = mob("mob-1", "Zealot", "Zealot");
		rule.island = "Old Island";
		mobs.rules.add(rule);
		@SuppressWarnings("unchecked")
		Choice<String> island = field(mobCards.groups().get(0), Choice.class, ".island");
		assertTrue(island.values().contains("The End") && island.values().contains("Catacombs"));
		assertEquals(RuleFields.ANY_ISLAND, island.values().get(0));
		assertEquals("Old Island", island.binding().get(), "a hand-edited island is kept");
		island.binding().set(RuleFields.ANY_ISLAND);
		assertNull(rule.island);
		island.binding().set("The End");
		assertEquals("The End", rule.island);
	}

	/** AC-UI-11 [A], REQ-UI-12: "([" as a regex marks the pattern field and says why; fixing it clears both. */
	@Test
	void anInvalidRegexIsMarkedUntilItIsFixed() {
		HighlightRule rule = mob("mob-1", "Broken", "([");
		rule.nameMatchMode = NameMatchMode.REGEX;
		mobs.rules.add(rule);
		RuleGroup group = mobCards.groups().get(0);
		TextOption pattern = field(group, TextOption.class, ".pattern");
		assertTrue(group.problem().get().contains("regular expression is invalid"));
		assertTrue(pattern.invalid().getAsBoolean());
		assertTrue(ConfigLayout.problemText(group.problem().get()).startsWith("⚠ This rule can't be used: "));
		pattern.binding().set("(\\w+)");
		assertNull(group.problem().get());
		assertFalse(pattern.invalid().getAsBoolean());
	}

	/** REQ-UI-09, EC-UI-09: rules are found by label and pattern; two with one label are both found. */
	@Test
	void rulesAreFoundByLabelAndPattern() {
		npcs.rules.add(npc("npc-1", "Trinity", false));
		npcs.rules.add(npc("npc-2", "Twin", false));
		npcs.rules.add(npc("npc-3", "Twin", false));
		mobs.rules.add(mob("mob-1", "Slayer boss", "Voidgloom"));
		SearchIndex index = SearchIndex.of(cards());
		SearchView trini = SearchView.of(index, List.of(Category.HIGHLIGHTS, Category.WAYPOINTS), "trini", Category.HIGHLIGHTS);
		RuleGroup trinity = npcCards.groups().get(0);
		assertTrue(trini.shows(trinity));
		assertEquals(Category.WAYPOINTS, trini.selected(), "its category is shown");
		assertTrue(trini.badge(Category.WAYPOINTS) >= 1);
		SearchView twins = SearchView.of(index, List.of(Category.HIGHLIGHTS, Category.WAYPOINTS), "twin", Category.WAYPOINTS);
		assertTrue(twins.shows(npcCards.groups().get(1)) && twins.shows(npcCards.groups().get(2)), "both rules named Twin");
		assertTrue(SearchView.of(index, List.of(Category.HIGHLIGHTS, Category.WAYPOINTS), "voidgloom", Category.WAYPOINTS)
				.shows(mobCards.groups().get(0)), "by pattern");
	}

	/** A click on a header opens the rule; its fields are then on the page and reach their controls; closed, they are gone. */
	@Test
	void aHeaderClickOpensTheRule() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		RuleGroup group = mobCards.groups().get(0);
		List<RuleGroup> toggled = new ArrayList<>();
		boolean[] open = {false};
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public boolean press(double mouseX, double mouseY, int button) {
				return contains(mouseX, mouseY);
			}

			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		}, rule -> {
			toggled.add(rule);
			open[0] = !open[0];
		});
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		ConfigLayout.Filter filter = new ConfigLayout.Filter() {
			@Override
			public boolean shows(Option option) {
				return true;
			}

			@Override
			public boolean expanded(RuleGroup rule) {
				return open[0];
			}
		};
		page.layout(frame, 0, text -> text.length() * 6, filter);
		RuleHeaderWidget header = page.header(group);
		assertTrue(page.widgets().contains(header));
		assertTrue(header.press(header.x() + 30, header.y() + 5, 0));
		assertEquals(List.of(group), toggled);
		page.layout(frame, 0, text -> text.length() * 6, filter);
		Option label = group.fields().get(1);
		assertTrue(page.widgets().contains(page.control(label)), "open: its fields are placed");
		Widget control = page.control(label);
		assertTrue(control.press(control.x() + 2, control.y() + 2, 0));
		header.press(header.x() + 30, header.y() + 5, 0);
		page.layout(frame, 0, text -> text.length() * 6, filter);
		assertFalse(page.widgets().contains(page.control(label)), "closed: gone from the page");
		assertFalse(page.control(label).showing(), "and hidden");
	}

	/** EC-UI-07: an empty label reads "(unnamed)", a long one is cut by the header, never past its width. */
	@Test
	void anEmptyLabelIsUnnamed() {
		HighlightRule empty = mob("mob-1", "  ", "x");
		mobs.rules.add(empty);
		RuleHeaderWidget header = new RuleHeaderWidget(mobCards.groups().get(0), () -> { });
		assertEquals(RuleHeaderWidget.UNNAMED, header.title());
		empty.label = "x".repeat(100);
		assertEquals(100, header.title().length(), "kept whole here; the header cuts it to its width when drawn");
	}

	/** EC-UI-17: 300 rules lay out quickly, a header each while closed. */
	@Test
	void threeHundredRulesLayOutQuickly() {
		for (int i = 0; i < 300; i++) {
			mobs.rules.add(mob("mob-" + i, "Rule " + i, "pattern " + i));
		}
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		});
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, text -> text.length() * 6);
		long start = System.nanoTime();
		for (int frameCount = 0; frameCount < 20; frameCount++) {
			page.layout(frame, frameCount * 10, text -> text.length() * 6);
		}
		long perFrame = (System.nanoTime() - start) / 20;
		assertEquals(300, page.widgets().stream().filter(widget -> widget instanceof RuleHeaderWidget).count());
		assertTrue(perFrame < 5_000_000, "a layout of 300 rules takes " + perFrame / 1000 + " us");
		assertSame(mobCards.groups().get(299), mobCards.groups().get(299), "a rule keeps its card");
	}

	/** NPC rules have no entity type, so "Any name" could never work for them: not offered, but kept where stored. */
	@Test
	void npcRulesDoNotOfferAnyName() {
		NpcRule plain = npc("npc-1", "Trinity", false);
		NpcRule stored = npc("npc-2", "Old", false);
		stored.nameMatchMode = NameMatchMode.NONE;
		npcs.rules.add(plain);
		npcs.rules.add(stored);
		@SuppressWarnings("unchecked")
		Choice<NameMatchMode> modes = field(npcCards.groups().get(0), Choice.class, ".mode");
		assertFalse(modes.values().contains(NameMatchMode.NONE));
		@SuppressWarnings("unchecked")
		Choice<NameMatchMode> kept = field(npcCards.groups().get(1), Choice.class, ".mode");
		assertTrue(kept.values().contains(NameMatchMode.NONE), "a stored Any name is kept");
	}

	/** REQ-UI-20, EC-UI-17: of 300 rules only the headers in view can draw or be clicked, at any scroll. */
	@Test
	void onlyTheRulesInViewShow() {
		for (int i = 0; i < 300; i++) {
			mobs.rules.add(mob("mob-" + i, "Rule " + i, "pattern " + i));
		}
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		});
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		int inView = frame.content().h() / (ConfigLayout.RULE_HEADER + ConfigLayout.RULE_GAP) + 2;
		for (int scroll : new int[] {0, 500, 4000, 8000}) {
			page.layout(frame, scroll, text -> text.length() * 6);
			long showing = page.widgets().stream().filter(widget -> widget instanceof RuleHeaderWidget && widget.showing()).count();
			assertTrue(showing > 0 && showing <= inView, "scroll " + scroll + ": " + showing + " headers can draw, at most " + inView);
			double y = frame.content().bottom() + 5;
			assertTrue(page.widgets().stream().noneMatch(widget -> widget instanceof RuleHeaderWidget && widget.inClip(frame.content().x() + 30, y)
					&& widget.contains(frame.content().x() + 30, y)), "none below the viewport can be clicked");
		}
	}
}

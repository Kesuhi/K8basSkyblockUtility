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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
		// A hand-made rule keeps every field; Remove is in its header, not among them (R31).
		assertEquals(List.of("enabled", "label", "type", "island", "mode", "pattern", "colour"), fields);

		npcs.rules.add(npc("npc-1", "Trinity", false));
		npcs.rules.add(npc("npc-2", "Dungeon Hub Mort", true));
		RuleGroup fixed = npcCards.groups().get(1);
		assertEquals("12, 70, -34", field(fixed, InfoOption.class, ".position").value().get(), "a fixed NPC shows its position, read-only");
		assertTrue(fixed.fields().stream().noneMatch(option -> option.id().endsWith(".pattern") || option.id().endsWith(".mode")));
		assertEquals(List.of("enabled", "label", "island", "mode", "pattern"), suffixes(npcCards.groups().get(0)), "no NPC colour (R31)");
		// T3.4b (REQ-NPCWP-06): a fixed NPC's beam colour and "Use island colour".
		assertEquals(List.of("enabled", "label", "island", "position", "beam_color", "use_island_color"), suffixes(fixed));
	}

	private static List<String> suffixes(RuleGroup group) {
		return group.fields().stream().map(option -> option.id().substring(group.id().length() + 1)).toList();
	}

	/** R31: a rule from the Mob or NPC Database shows only what is useful to change; the database owns the rest. */
	@Test
	void databaseRulesShowASlimCard() {
		HighlightRule zealot = mob("mob-1", "Zealot", "Zealot");
		zealot.sourceId = "zealot";
		mobs.rules.add(zealot);
		assertEquals(List.of("enabled", "type", "colour"), suffixes(mobCards.groups().get(0)));

		NpcRule trinity = npc("npc-1", "Trinity", false);
		trinity.sourceId = "trinity";
		NpcRule udel = npc("npc-2", "Udel", true);
		udel.sourceId = "udel";
		npcs.rules.add(trinity);
		npcs.rules.add(udel);
		assertEquals(List.of("enabled"), suffixes(npcCards.groups().get(0)), "a moving NPC from the list");
		assertEquals(List.of("enabled", "position", "beam_color", "use_island_color"), suffixes(npcCards.groups().get(1)), "a fixed NPC from the list");
		assertEquals("Udel", npcCards.groups().get(1).label().get(), "the header still names it");

		// One that already does nothing (edited before 1.2.0) keeps its full card, so it can be fixed.
		HighlightRule broken = mob("mob-2", "Voidgloom", "([");
		broken.sourceId = "voidgloom";
		broken.nameMatchMode = NameMatchMode.REGEX;
		mobs.rules.add(broken);
		assertEquals(List.of("enabled", "label", "type", "island", "mode", "pattern", "colour"), suffixes(mobCards.groups().get(1)));
	}

	/** EC-NPCDB-01 (T3.8c): a rule whose database entry is gone keeps its values and shows as a custom rule. */
	@Test
	void aRuleWhoseEntryIsGoneShowsAsACustomRule() {
		MobRuleCards cards = new MobRuleCards(mobs, RuleCardsTest::problem, () -> changes++, id -> !id.equals("removed"));
		HighlightRule gone = mob("mob-1", "Old Mob", "Old Mob");
		gone.sourceId = "removed";
		HighlightRule kept = mob("mob-2", "Zealot", "Zealot");
		kept.sourceId = "zealot";
		mobs.rules.add(gone);
		mobs.rules.add(kept);
		assertEquals(List.of("enabled", "label", "type", "island", "mode", "pattern", "colour"), suffixes(cards.groups().get(0)));
		assertEquals(List.of("enabled", "type", "colour"), suffixes(cards.groups().get(1)));

		NpcRuleCards npcCards = new NpcRuleCards(npcs, rule -> null, rule -> "12, 70, -34", () -> changes++, id -> !id.equals("removed"));
		NpcRule npc = npc("npc-1", "Old NPC", false);
		npc.sourceId = "removed";
		npcs.rules.add(npc);
		assertTrue(suffixes(npcCards.groups().get(0)).contains("label"), "the NPC rule's full card: " + suffixes(npcCards.groups().get(0)));
	}

	/** R31: every rule has a destructive Remove in its header; it deletes the rule at once. */
	@Test
	void everyRuleHasARemoveButton() {
		HighlightRule zealot = mob("mob-1", "Zealot", "Zealot");
		zealot.sourceId = "zealot";
		mobs.rules.add(zealot);
		npcs.rules.add(npc("npc-1", "Udel", true));
		for (RuleGroup group : List.of(mobCards.groups().get(0), npcCards.groups().get(0))) {
			ActionOption remove = group.remove();
			assertEquals("Remove", remove.buttonLabel(), group.id());
			assertTrue(remove.destructive(), group.id());
			// The only destructive button is Remove in the header; "Use island colour" (T3.4b) is a plain action.
			assertTrue(group.fields().stream().noneMatch(option -> option instanceof ActionOption action && action.destructive()),
					"no Delete field any more: " + group.id());
		}
		mobCards.groups().get(0).remove().action().run();
		npcCards.groups().get(0).remove().action().run();
		assertTrue(mobs.rules.isEmpty() && npcs.rules.isEmpty());
		assertEquals(2, changes, "each removal applied at once");
	}

	/** The widget a click at the point reaches: the topmost placed one in its clip that takes the press (as WidgetScreen does). */
	private static Widget routed(ConfigPage page, double x, double y) {
		List<Widget> widgets = page.widgets();
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (widget.inClip(x, y) && widget.press(x, y, 0)) {
				return widget;
			}
		}
		return null;
	}

	/** R31: over the Remove button the header neither lights up nor shows its warning; the button has its own tooltip. */
	@Test
	void theRemoveAreaBelongsToTheButton() {
		HighlightRule broken = mob("mob-1", "Broken", "([");
		broken.nameMatchMode = NameMatchMode.REGEX;
		mobs.rules.add(broken);
		RuleGroup group = mobCards.groups().get(0);
		RuleHeaderWidget header = new RuleHeaderWidget(group, () -> { });
		header.setBounds(0, 0, 400, ConfigLayout.RULE_HEADER);
		ConfigLayout.Rect action = ConfigLayout.headerAction(new ConfigLayout.Rect(0, 0, 400, ConfigLayout.RULE_HEADER));
		header.setActionRoom(400 - action.x());
		assertTrue(header.tooltipAt(30, 5).startsWith("⚠ "), "the warning left of the button");
		assertEquals("", header.tooltipAt(action.x() + 2, 5), "none over the button");
		assertEquals("Removes this rule from the list.", group.remove().text().description());
	}

	/** R31: NPC rules have no colour of their own; their header dot is white whatever the stored colour. */
	@Test
	void npcRulesAreWhite() {
		NpcRule coloured = npc("npc-1", "Trinity", false);
		coloured.color = 0xFF5555;
		npcs.rules.add(coloured);
		RuleGroup group = npcCards.groups().get(0);
		assertEquals(0xFFFFFF, group.colour().getAsInt());
		assertTrue(group.fields().stream().noneMatch(option -> option instanceof ColorOption));
	}

	/**
	 * AC-UI-56 (REQ-UI-52, T3.45): a rule card half open is laid out at its eased height rounded to whole GUI px: its rows
	 * are clipped there, rows that would lie wholly past it are not placed, and the next rule's header follows at once, so a
	 * click on it reaches it; closed, nothing of its body is placed; open, nothing is clipped.
	 */
	@Test
	void aRuleCardPartlyOpenIsLaidOutAtItsEasedHeight() {
		// A rule that does nothing (a broken pattern), so its card has the reason above its fields.
		HighlightRule broken = mob("mob-1", "Zealot", "([");
		broken.nameMatchMode = NameMatchMode.REGEX;
		mobs.rules.add(broken);
		mobs.rules.add(mob("mob-2", "Voidgloom", "Voidgloom"));
		RuleGroup first = mobCards.groups().get(0);
		RuleGroup second = mobCards.groups().get(1);
		float[] reveal = {1F};
		ConfigLayout.Filter filter = new ConfigLayout.Filter() {
			@Override
			public boolean shows(Option option) {
				return true;
			}

			@Override
			public boolean expanded(RuleGroup rule) {
				return rule == first;
			}

			@Override
			public float reveal(RuleGroup rule) {
				return rule == first ? reveal[0] : 0F;
			}
		};
		java.util.function.ToIntFunction<String> measure = text -> text.length() * 6;
		ConfigLayout.Page open = ConfigLayout.page(cards(), Category.HIGHLIGHTS, 600, measure, filter);
		int bodyStart = headerRow(open, first).y() + ConfigLayout.RULE_HEADER + ConfigLayout.RULE_GAP;
		int full = headerRow(open, second).y() - bodyStart;
		List<ConfigLayout.OptionRow> openBody = body(open, first);
		assertTrue(openBody.size() >= 2 && openBody.stream().allMatch(row -> row.clipBottom() == Integer.MAX_VALUE), "open: nothing clipped");

		reveal[0] = 0.3F;
		ConfigLayout.Page partly = ConfigLayout.page(cards(), Category.HIGHLIGHTS, 600, measure, filter);
		int shown = Math.round(0.3F * full);
		assertEquals(bodyStart + shown, headerRow(partly, second).y(), "the next header follows the eased height");
		List<ConfigLayout.OptionRow> partlyBody = body(partly, first);
		assertTrue(!partlyBody.isEmpty() && partlyBody.size() < openBody.size(), "only the rows that show: " + partlyBody.size());
		for (ConfigLayout.OptionRow row : partlyBody) {
			assertEquals(bodyStart + shown, row.clipBottom(), row + " is clipped at the eased height");
			assertTrue(row.y() < bodyStart + shown, row + " shows in part at least");
		}
		ConfigLayout.RuleProblem problem = partly.rows().stream().filter(row -> row instanceof ConfigLayout.RuleProblem)
				.map(row -> (ConfigLayout.RuleProblem) row).findFirst().orElseThrow();
		assertEquals(bodyStart + shown, problem.clipBottom(), "the reason the rule does nothing is clipped there too");
		assertEquals(0.3F, headerRow(partly, first).reveal(), "the header knows how far its card is open, for the chevron");

		reveal[0] = 0F;
		ConfigLayout.Page closed = ConfigLayout.page(cards(), Category.HIGHLIGHTS, 600, measure, filter);
		assertTrue(body(closed, first).isEmpty(), "closed: no body");
		assertEquals(bodyStart, headerRow(closed, second).y());

		// Placed, a field of the card is clipped there, and a click on the next header reaches that header.
		reveal[0] = 0.3F;
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public boolean press(double mouseX, double mouseY, int button) {
				return contains(mouseX, mouseY);
			}

			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		});
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, measure, filter);
		RuleHeaderWidget next = page.header(second);
		assertEquals(frame.content().y() + bodyStart + shown, next.y(), "the next header where it is drawn");
		assertSame(next, routed(page, next.x() + 30, next.y() + 5), "a click on the next header reaches it");
		assertEquals(0.3F, page.header(first).reveal(), "its chevron turns with it");
		Option option = partlyBody.get(partlyBody.size() - 1).option();
		Widget field = page.card(option);
		Widget control = page.control(option);
		int clipY = frame.content().y() + bodyStart + shown;
		assertEquals(clipY, field.clip().bottom(), "the field is clipped at the eased height");
		assertEquals(clipY, control.clip().bottom(), "and so is its control");
		// Input only where it shows: inside the field's box, above the clip it is reached, below it neither field nor control.
		assertTrue(field.y() + field.height() > clipY + 2, "the field reaches past the clip");
		assertTrue(field.inClip(field.x() + 4, clipY - 2));
		assertFalse(field.inClip(field.x() + 4, clipY + 2), "below the clip the field takes no input");
		assertFalse(control.inClip(control.x() + 2, clipY + 2), "nor its control");

		// Scrolled, the clip moves with the rows.
		page.layout(frame, 30, measure, filter);
		assertEquals(clipY - 30, page.card(option).clip().bottom(), "scrolled by 30: the clip 30 higher");
		assertEquals(clipY - 30, page.header(second).y(), "and the next header with it");
	}

	private static ConfigLayout.RuleHeader headerRow(ConfigLayout.Page page, RuleGroup rule) {
		return page.rows().stream().filter(row -> row instanceof ConfigLayout.RuleHeader header && header.rule() == rule)
				.map(row -> (ConfigLayout.RuleHeader) row).findFirst().orElseThrow();
	}

	/** The rows of a rule's fields on the page, in order. */
	private static List<ConfigLayout.OptionRow> body(ConfigLayout.Page page, RuleGroup rule) {
		return page.rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && rule.fields().contains(option.option()))
				.map(row -> (ConfigLayout.OptionRow) row).toList();
	}

	/** R31: the Remove button sits at the header's right end, over the header (so it gets the click), clear of the label. */
	@Test
	void theRemoveButtonSitsAtTheHeadersRightEnd() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		RuleGroup group = mobCards.groups().get(0);
		List<RuleGroup> toggled = new ArrayList<>();
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public boolean press(double mouseX, double mouseY, int button) {
				return contains(mouseX, mouseY);
			}

			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		}, toggled::add);
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, text -> text.length() * 6);
		RuleHeaderWidget header = page.header(group);
		Widget remove = page.control(group.remove());
		assertTrue(page.widgets().contains(remove), "placed while the rule is closed");
		assertTrue(page.widgets().indexOf(remove) > page.widgets().indexOf(header), "over the header");
		assertTrue(remove.x() > header.x() + header.width() / 2 && remove.x() + remove.width() <= header.x() + header.width(), "at the right end");
		assertTrue(remove.y() >= header.y() && remove.y() + remove.height() <= header.y() + header.height(), "inside the header");
		assertTrue(header.textRight() <= remove.x(), "the label stops before it");
		// Routed as WidgetScreen routes a click: the topmost placed widget that takes it.
		assertSame(remove, routed(page, remove.x() + 2, remove.y() + 2), "a click on Remove reaches Remove");
		assertTrue(toggled.isEmpty(), "and does not open or close the rule");
		assertFalse(header.press(remove.x() + 2, remove.y() + 2, 0), "the header never takes a press in the button's area");
		assertSame(header, routed(page, header.x() + 30, header.y() + 5), "left of it, the header takes the click");
		assertEquals(List.of(group), toggled);

		// Removed, the rule's header and button leave the page.
		group.remove().action().run();
		page.layout(frame, 0, text -> text.length() * 6);
		assertFalse(page.widgets().contains(remove));
	}

	/** Every edit is stored on the rule at once and applied (REQ-UI-15); Remove deletes the rule. */
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
		group.remove().action().run();
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

	/** AC-UI-44 [A] (REQ-UI-43, T3.33): a rule header shows the hand; over its Remove button that button decides. */
	@Test
	void aRuleHeaderShowsTheHand() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		RuleHeaderWidget header = new RuleHeaderWidget(mobCards.groups().get(0), () -> { });
		header.setBounds(0, 0, 400, ConfigLayout.RULE_HEADER);
		header.setActionRoom(70);
		assertEquals(com.k8bas.skyblockutility.ui.widget.PointerShape.HAND, header.cursorAt(100, 5), "the header");
		assertNull(header.cursorAt(350, 5), "over its Remove button: that button decides");
	}

	/** REQ-UI-18 (T2.9b): Enter opens or closes a rule; Right opens a closed one, Left closes an open one; the ring stops before Remove. */
	@Test
	void aRuleHeaderTakesTheKeyboard() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		int[] toggles = {0};
		RuleHeaderWidget header = new RuleHeaderWidget(mobCards.groups().get(0), () -> toggles[0]++);
		assertTrue(header.focusable());
		assertTrue(header.activate());
		assertEquals(1, toggles[0]);
		assertTrue(header.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.RIGHT, false));
		assertEquals(2, toggles[0], "closed: Right opens");
		header.setExpanded(true);
		assertTrue(header.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.RIGHT, false));
		assertEquals(2, toggles[0], "open: Right does nothing more");
		assertTrue(header.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.LEFT, false));
		assertEquals(3, toggles[0], "open: Left closes");
		header.setExpanded(false);
		assertTrue(header.navKey(com.k8bas.skyblockutility.ui.widget.NavKey.LEFT, false));
		assertEquals(3, toggles[0], "closed: Left does nothing more");
		header.setBounds(0, 0, 400, ConfigLayout.RULE_HEADER);
		header.setActionRoom(70);
		int[] ring = new int[4];
		header.focusRing(ring);
		assertArrayEquals(new int[] {-2, -2, 400 - 70 + 4, ConfigLayout.RULE_HEADER + 4}, ring, "around the header, not its Remove");
	}

	/** REQ-UI-18 (T2.9b): Tab goes through a page in reading order: each card's control, then each rule's header, Remove and (open) fields. */
	@Test
	void theFocusOrderFollowsTheRows() {
		mobs.rules.add(mob("mob-1", "Zealot", "Zealot"));
		mobs.rules.add(mob("mob-2", "Voidgloom", "Voidgloom"));
		List<RuleGroup> groups = mobCards.groups();
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards(), option -> new Widget() {
			@Override
			public void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
			}
		});
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, text -> text.length() * 6, new ConfigLayout.Filter() {
			@Override
			public boolean shows(Option option) {
				return true;
			}

			@Override
			public boolean expanded(RuleGroup rule) {
				return rule == groups.get(0);
			}
		});
		List<Widget> expected = new ArrayList<>();
		for (ConfigLayout.Row row : page.page().rows()) {
			if (row instanceof ConfigLayout.OptionRow option) {
				expected.add(page.control(option.option()));
			} else if (row instanceof ConfigLayout.RuleHeader header) {
				expected.add(page.header(header.rule()));
				expected.add(page.control(header.rule().remove()));
			}
		}
		List<Widget> order = new ArrayList<>();
		page.focusOrder(order);
		assertEquals(expected, order);
		int firstHeader = order.indexOf(page.header(groups.get(0)));
		assertSame(page.control(groups.get(0).remove()), order.get(firstHeader + 1), "the header, then its Remove");
		assertSame(page.control(groups.get(0).fields().get(0)), order.get(firstHeader + 2), "then the open rule's fields");
		assertSame(page.header(groups.get(1)), order.get(firstHeader + 2 + groups.get(0).fields().size()), "then the next rule");

		// A control, a header and a Remove each find their row, so the screen can scroll it into view.
		Option scanRange = page.page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option
				&& option.option() instanceof com.k8bas.skyblockutility.ui.option.IntSlider).map(row -> ((ConfigLayout.OptionRow) row).option())
				.findFirst().orElseThrow();
		ConfigLayout.Row optionRow = page.rowOf(page.control(scanRange));
		assertTrue(optionRow instanceof ConfigLayout.OptionRow row && row.option() == scanRange);
		assertTrue(page.rowOf(page.header(groups.get(1))) instanceof ConfigLayout.RuleHeader header && header.rule() == groups.get(1));
		assertTrue(page.rowOf(page.control(groups.get(1).remove())) instanceof ConfigLayout.RuleHeader header && header.rule() == groups.get(1));
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

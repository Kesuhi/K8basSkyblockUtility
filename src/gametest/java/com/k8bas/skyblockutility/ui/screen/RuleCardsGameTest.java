package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.IconProbe;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.migration.ConfigMigrations;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.mobhighlighter.MobDatabase;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRulesForTests;
import com.k8bas.skyblockutility.module.npcsearch.NpcDatabase;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRulesForTests;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.ColorPickerOverlay;
import com.k8bas.skyblockutility.ui.widget.TextField;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * T2.5a, the rule cards in a real client:
 * <ul>
 *   <li>AC-UI-15 [C]: rules shaped like a 1.0.1 config show their label and the fields of their kind (R31);</li>
 *   <li>AC-UI-11 [C]: the regex "([" shows the warning sign, the reason and the marked field; fixing it clears all three;</li>
 *   <li>AC-UI-10 (T2.5a part): a label and pattern edit, a colour-only change through the picker and a Remove are
 *       in the file after the screen closes;</li>
 *   <li>AC-UI-08 [C]: "trini" lists Waypoints with a badge and shows Trinity's card open;</li>
 *   <li>REQ-GLOW-10 [C] (decision 2026-10-01, S-6): a rule that ignores names and has no entity type has the
 *       warning sign and, open, says why; a valid rule next to it has neither.</li>
 * </ul>
 */
public class RuleCardsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		List<HighlightRule> mobsBefore = context.computeOnClient(client -> MobRulesForTests.rules());
		List<NpcRule> npcsBefore = context.computeOnClient(client -> NpcRulesForTests.rules());
		try {
			context.runOnClient(client -> client.options.guiScale().set(2));
			fixtureRules(context);
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				MobRulesForTests.use(List.of(mob("mob-1", "Zealot", "Zealot", "The End", 0xAA00FF), mob("mob-2", "Voidgloom", "Voidgloom", null, 0x00FF00),
						regex("mob-3", "Broken regex", "(["), mob("mob-4", "Delete me", "Old", null, 0xFF0000)));
				NpcRulesForTests.use(List.of(npc("npc-1", "Trinity", "Catacombs")));
			});
			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
			context.waitTicks(3);
			ruleHeaderChevron(context);
			originalValues(context);
			invalidRegex(context);
			editsSurviveTheClose(context);
			searchOpensTheRule(context);
			searchMarksItsMatches(context);
			anyNameWithoutATypeWarns(context);
		} finally {
			context.runOnClient(client -> {
				MobRulesForTests.use(mobsBefore);
				NpcRulesForTests.use(npcsBefore);
			});
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				ConfigManager.save();
				ConfigManager.flush();
			});
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/**
	 * AC-UI-15 [C] on the 1.0.1-shaped fixture, migrated as the game migrates it: every rule's header names
	 * it; a hand-made rule's card shows its label, island and pattern (a fixed NPC its position instead of a
	 * pattern), a rule from a database shows none of them, and only mob rules have a colour (R31). A pattern
	 * longer than the field's usual limit is shown whole.
	 */
	private static void fixtureRules(ClientGameTestContext context) {
		JsonObject modules = migratedFixture(context).getAsJsonObject("modules");
		Gson gson = new Gson();
		List<HighlightRule> mobs = new java.util.ArrayList<>(List.of(gson.fromJson(modules.getAsJsonObject("mob_highlighter").get("rules"), HighlightRule[].class)));
		List<NpcRule> npcs = List.of(gson.fromJson(modules.getAsJsonObject("npc_search").get("rules"), NpcRule[].class));
		mobs.forEach(HighlightRule::normalize);
		npcs.forEach(NpcRule::normalize);
		HighlightRule longPattern = mob("mob-long", "Long", "x".repeat(300), null, 0x123456);
		mobs.add(longPattern);
		context.runOnClient(client -> {
			MobRulesForTests.use(mobs);
			NpcRulesForTests.use(npcs);
		});
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		int checked = 0;
		for (Category category : List.of(Category.HIGHLIGHTS, Category.WAYPOINTS)) {
			context.runOnClient(client -> screen(client).select(category));
			context.waitTicks(2);
			// Open every rule, then compare each card's fields with its rule.
			context.runOnClient(client -> headers(screen(client)).forEach(header -> screen(client).toggleRule(header.rule())));
			context.waitTicks(2);
			String prefix = category == Category.HIGHLIGHTS ? "mob_highlighter.rule." : "npc_search.rule.";
			for (HighlightRule rule : category == Category.HIGHLIGHTS ? mobs : List.<HighlightRule>of()) {
				// A rule from the list shows the slim card while its entry is still listed; the fixture's "special_zealot"
				// is not in the bundled mob list, so that rule shows as a custom rule (EC-NPCDB-01, T3.8c).
				boolean listed = rule.sourceId != null && context.computeOnClient(client -> MobDatabase.byId(rule.sourceId) != null);
				expectCard(context, prefix + rule.id, rule.label, rule.island, rule.namePattern, rule.color, listed);
				checked++;
			}
			for (NpcRule rule : category == Category.WAYPOINTS ? npcs : List.<NpcRule>of()) {
				boolean listed = rule.sourceId != null && context.computeOnClient(client -> NpcDatabase.byId(rule.sourceId) != null);
				expectCard(context, prefix + rule.id, rule.label, rule.island, rule.fixed ? null : rule.namePattern, null, listed);
				if (rule.fixed) {
					String position = context.computeOnClient(client -> ((com.k8bas.skyblockutility.ui.option.InfoOption) option(screen(client),
							prefix + rule.id + ".position")).value().get());
					check(!position.isBlank(), rule.label + ": its position is shown: " + position);
				}
				checked++;
			}
			context.takeScreenshot("t2.5a-fixture-" + category.id());
			context.runOnClient(client -> headers(screen(client)).forEach(header -> screen(client).toggleRule(header.rule())));
		}
		check(checked == mobs.size() + npcs.size(), "every rule was checked: " + checked);
		LOGGER.info("rule cards: all {} rules of the 1.0.1 fixture show their label and the fields of their kind (R31); a 300-character pattern "
				+ "is shown whole", checked);
	}

	/** @param colour the mob rule's colour, or null for an NPC rule, which has no colour field (R31) */
	private static void expectCard(ClientGameTestContext context, String id, String label, String island, String pattern, Integer colour,
			boolean fromDatabase) {
		String title = context.computeOnClient(client -> screen(client).page().header(ruleHeader(screen(client), id).rule()).title());
		check(title.equals(label == null || label.isBlank() ? RuleHeaderWidget.UNNAMED : label.strip()), id + ": header " + title);
		check(context.computeOnClient(client -> shown(screen(client), id + ".colour")) == (colour != null), id + ": a colour field only for a mob");
		if (colour != null) {
			int shownColour = context.computeOnClient(client -> ((ColorOption) option(screen(client), id + ".colour")).binding().get());
			check(shownColour == (colour & 0xFFFFFF), id + ": colour " + Integer.toHexString(shownColour));
		}
		if (fromDatabase) {
			for (String field : List.of(".label", ".island", ".mode", ".pattern")) {
				check(!context.computeOnClient(client -> shown(screen(client), id + field)), id + ": a rule from a database shows no " + field + " field");
			}
			return;
		}
		check(fieldText(context, id + ".label").equals(label == null ? "" : label), id + ": label " + fieldText(context, id + ".label"));
		String shownIsland = context.computeOnClient(client -> ((com.k8bas.skyblockutility.ui.option.Choice<?>) option(screen(client), id + ".island"))
				.binding().get().toString());
		String expectedIsland = island == null ? com.k8bas.skyblockutility.settings.RuleFields.ANY_ISLAND : island;
		check(shownIsland.equals(expectedIsland), id + ": island " + shownIsland + ", expected " + expectedIsland);
		if (pattern != null) {
			check(fieldText(context, id + ".pattern").equals(pattern), id + ": pattern " + fieldText(context, id + ".pattern"));
		}
	}

	private static boolean shown(ConfigScreen screen, String id) {
		return screen.page().page().rows().stream().anyMatch(row -> row instanceof ConfigLayout.OptionRow option && option.option().id().equals(id));
	}

	/**
	 * AC-UI-33 [C] (T3.21): at GUI scale 2 the first rule header's chevron is the kit's, right while the rule is closed and
	 * down while open, drawn 1:1 with anti-aliased edges in the secondary text colour (at 45 % on a dimmed card).
	 */
	private static void ruleHeaderChevron(ClientGameTestContext context) {
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		check(scale == 2, "the rule header chevron is checked at GUI scale 2: " + scale);
		Path shot = context.takeScreenshot("t3.21-rule-header-scale-2");
		int[] at = context.computeOnClient(client -> {
			RuleHeaderWidget header = (RuleHeaderWidget) screen(client).page().header(headers(screen(client)).get(0).rule());
			return new int[] {header.x(), header.y(), header.height(), header.expanded() ? 1 : 0, header.isDimmed() ? 1 : 0};
		});
		// A dimmed widget draws at 45 % of its alpha (Widget.faded).
		int colour = at[4] == 1 ? (0xFF * 45 / 100) << 24 | Theme.TEXT_SECONDARY & 0xFFFFFF : Theme.TEXT_SECONDARY;
		String chevron = IconProbe.check(shot, at[3] == 1 ? IconShapes.Icon.CHEVRON_DOWN : IconShapes.Icon.CHEVRON_RIGHT, at[0] + 6, at[1] + at[2] / 2 - 4, 8,
				scale, colour);
		check(chevron.isEmpty(), "the rule header's chevron: " + chevron);
	}

	/** AC-UI-15 [C]: the original label, dot colour, island, pattern and colour. */
	private static void originalValues(ClientGameTestContext context) {
		List<String> titles = context.computeOnClient(client -> headers(screen(client)).stream()
				.map(header -> screen(client).page().header(header.rule()).title()).toList());
		check(titles.equals(List.of("Zealot", "Voidgloom", "Broken regex", "Delete me")), "a header per rule, in order: " + titles);
		Path closed = context.takeScreenshot("t2.5a-rules-closed");
		int[] dot = context.computeOnClient(client -> {
			Widget header = screen(client).page().header(headers(screen(client)).get(0).rule());
			return new int[] {header.x() + 22 + 4, header.y() + header.height() / 2};
		});
		int dotColour = pixel(context, closed, dot);
		check(dotColour == 0xAA00FF, "Zealot's dot has its colour: " + Integer.toHexString(dotColour));
		click(context, headerPoint(context, "Zealot"));
		check(fieldText(context, "mob_highlighter.rule.mob-1.label").equals("Zealot"), "the label field");
		check(fieldText(context, "mob_highlighter.rule.mob-1.pattern").equals("Zealot"), "the pattern field");
		String island = context.computeOnClient(client -> {
			Option option = option(screen(client), "mob_highlighter.rule.mob-1.island");
			return ((com.k8bas.skyblockutility.ui.option.Choice<?>) option).binding().get().toString();
		});
		check("The End".equals(island), "the island: " + island);
		int colour = context.computeOnClient(client -> ((ColorOption) option(screen(client), "mob_highlighter.rule.mob-1.colour")).binding().get());
		check(colour == 0xAA00FF, "the colour");
		context.takeScreenshot("t2.5a-rule-open");
		click(context, headerPoint(context, "Zealot"));
		LOGGER.info("rule cards: headers {}, Zealot's dot {}, island {}", titles, Integer.toHexString(dotColour), island);
	}

	/** AC-UI-11 [C]. */
	private static void invalidRegex(ClientGameTestContext context) {
		boolean warned = context.computeOnClient(client -> headers(screen(client)).get(2).rule().problem().get() != null);
		check(warned, "\"([\" is reported inactive");
		click(context, headerPoint(context, "Broken regex"));
		check(problemRows(context) == 1, "open, it says why");
		check(context.computeOnClient(client -> fieldMarked(screen(client), "mob_highlighter.rule.mob-3.pattern")), "and marks the pattern");
		context.takeScreenshot("t2.5a-invalid-regex");
		replaceText(context, "mob_highlighter.rule.mob-3.pattern", "(\\w+)");
		check(problemRows(context) == 0, "fixed: the reason is gone");
		check(context.computeOnClient(client -> headers(screen(client)).get(2).rule().problem().get() == null), "and the warning sign");
		check(context.computeOnClient(client -> MobRulesForTests.rules().get(2).namePattern).equals("(\\w+)"), "the fix is on the rule at once");
		click(context, headerPoint(context, "Broken regex"));
		LOGGER.info("rule cards: \"([\" warned with its reason and a marked field; \"(\\\\w+)\" cleared both");
	}

	/** AC-UI-10 (T2.5a part): an edit, a colour-only change and a Remove are in the file after the close. */
	private static void editsSurviveTheClose(ClientGameTestContext context) {
		click(context, headerPoint(context, "Zealot"));
		replaceText(context, "mob_highlighter.rule.mob-1.label", "Zealot Bruiser");
		click(context, headerPoint(context, "Zealot Bruiser"));
		// Only the colour of the second rule, through the picker.
		click(context, headerPoint(context, "Voidgloom"));
		pickColour(context, "mob_highlighter.rule.mob-2.colour", "#123456");
		click(context, headerPoint(context, "Voidgloom"));
		// Remove the fourth with its header's button, without opening it: written at once (R31).
		int beforeDelete = context.computeOnClient(client -> ConfigManager.saveRequests());
		int badgeBefore = context.computeOnClient(client -> screen(client).view().badge(Category.HIGHLIGHTS));
		click(context, removeCentre(context, "mob_highlighter.rule.mob-4"));
		check(context.computeOnClient(client -> MobRulesForTests.rules().size()) == 3, "Remove deletes the rule");
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) - beforeDelete == 1, "and writes at once");
		context.waitTicks(2);
		int badgeAfter = context.computeOnClient(client -> screen(client).view().badge(Category.HIGHLIGHTS));
		check(badgeAfter == badgeBefore - 1, "the badge counts one rule less: " + badgeBefore + " -> " + badgeAfter);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.runOnClient(client -> ConfigManager.flush());
		String file = configFile(context).replaceAll("\\s", "");
		check(file.contains("\"label\":\"ZealotBruiser\""), "the label edit is in the file");
		check(file.contains("\"color\":" + 0x123456), "the colour-only change is in the file");
		check(!file.contains("\"label\":\"Deleteme\""), "the deleted rule is gone from the file");
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		LOGGER.info("rule cards: the edit, the colour-only change and the Remove are in the file after the close");
	}

	/** AC-UI-08 [C]. */
	private static void searchOpensTheRule(ClientGameTestContext context) {
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		// Laid out once, so the search box has its place.
		context.waitTicks(2);
		int[] box = context.computeOnClient(client -> {
			ConfigLayout.Rect search = screen(client).frame().search();
			return new int[] {search.x() + 20, search.y() + search.h() / 2};
		});
		click(context, box);
		context.getInput().typeChars("trini");
		context.waitTicks(3);
		Category shown = context.computeOnClient(client -> screen(client).selected());
		int badge = context.computeOnClient(client -> screen(client).view().badge(Category.WAYPOINTS));
		check(shown == Category.WAYPOINTS && badge >= 1, "\"trini\" shows Waypoints with a badge: " + shown + ", " + badge);
		List<ConfigLayout.RuleHeader> headers = context.computeOnClient(client -> headers(screen(client)));
		check(headers.size() == 1 && headers.get(0).expanded(), "Trinity's card is shown open: " + headers);
		context.takeScreenshot("t2.5a-search-trini");
		LOGGER.info("rule cards: \"trini\" showed Waypoints with badge {} and Trinity open", badge);
	}

	/** The 1.0.1-shaped fixture of the unit tests, migrated as the game migrates a file it loads. */
	static JsonObject migratedFixture(ClientGameTestContext context) {
		JsonObject root;
		try {
			Path fixture = context.computeOnClient(client -> FabricLoader.getInstance().getGameDir())
					.resolve("../../../src/test/resources/fixtures/config-1.0.1-shaped.json").normalize();
			root = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
		} catch (IOException e) {
			throw new AssertionError("cannot read the 1.0.1 fixture", e);
		}
		ConfigMigrations.MIGRATOR.migrate(root);
		return root;
	}

	/** REQ-GLOW-10 [C]: "Any name" with no entity type would outline everything, so it does nothing and says so. */
	private static void anyNameWithoutATypeWarns(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			HighlightRule everything = mob("mob-any", "Everything", null, null, 0xFF0000);
			everything.nameMatchMode = NameMatchMode.NONE;
			MobRulesForTests.use(List.of(everything, mob("mob-ok", "Zealot", "Zealot", null, 0xAA00FF)));
		});
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(3);
		String warning = context.computeOnClient(client -> headers(screen(client)).get(0).rule().problem().get());
		check(warning != null && warning.contains("needs an entity type"), "the rule that ignores names is marked, with the reason: " + warning);
		check(context.computeOnClient(client -> headers(screen(client)).get(1).rule().problem().get()) == null, "the valid rule is not");
		click(context, headerPoint(context, "Everything"));
		List<String> lines = context.computeOnClient(client -> screen(client).page().page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.RuleProblem).flatMap(row -> ((ConfigLayout.RuleProblem) row).lines().stream()).toList());
		check(String.join(" ", lines).contains("needs an entity type"), "open, it says why: " + lines);
		context.takeScreenshot("s6-inert-rule-warning");
		LOGGER.info("rule cards: \"Any name\" without an entity type warned \"{}\"; the valid rule did not", warning);
	}

	static HighlightRule mob(String id, String label, String pattern, String island, int colour) {
		HighlightRule rule = new HighlightRule();
		rule.id = id;
		rule.label = label;
		rule.namePattern = pattern;
		rule.island = island;
		rule.color = colour;
		return rule;
	}

	private static HighlightRule regex(String id, String label, String pattern) {
		HighlightRule rule = mob(id, label, pattern, null, 0xFFAA00);
		rule.nameMatchMode = NameMatchMode.REGEX;
		return rule;
	}

	private static NpcRule npc(String id, String label, String island) {
		NpcRule rule = new NpcRule();
		rule.id = id;
		rule.label = label;
		rule.namePattern = label;
		rule.island = island;
		return rule;
	}

	/**
	 * AC-UI-55 [C] (T3.44) at GUI scale 2, after {@link #searchOpensTheRule}: "trini" puts a tinted box behind "Trini" of
	 * Trinity's label; "mob" behind "Mob" of the scan range's title and "mob" of its description. In each box the row above
	 * the glyphs is tinted toward the accent against the card just above it, and its brightest glyph pixel is the line's
	 * text colour as outside the box; a cleared query leaves no box.
	 */
	private static void searchMarksItsMatches(ClientGameTestContext context) {
		check(context.computeOnClient(client -> client.getWindow().getGuiScale()) == 2, "the search marks are checked at GUI scale 2");
		// "trini": one box, as wide as "Trini" in the game's font, behind the start of Trinity's label.
		List<int[]> label = context.computeOnClient(client -> screen(client).page().header(headerRule(screen(client), "Trinity")).marksForTests());
		int trini = context.computeOnClient(client -> client.font.width("Trini"));
		Path triniShot = context.takeScreenshot("t3.44-search-marks-trini");
		int headerRight = context.computeOnClient(client -> {
			Widget widget = screen(client).page().header(headerRule(screen(client), "Trinity"));
			return widget.x() + widget.width();
		});
		check(label.size() == 1 && label.get(0)[2] == trini, "\"trini\" marks \"Trini\" of Trinity's label, " + trini + " GUI px: "
				+ label.stream().map(java.util.Arrays::toString).toList());
		String log = markProbe(triniShot, label.get(0), headerRight, "Trinity's label");
		// "mob": two boxes on the scan range's card, "Mob" on its title row and "mob" of "mobs" on a description row.
		replaceQuery(context, "mob");
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(3);
		List<int[]> card = context.computeOnClient(client -> screen(client).page().card(option(screen(client), "mob_highlighter.scan_range")).marksForTests());
		int[] cardBox = context.computeOnClient(client -> {
			Widget widget = screen(client).page().card(option(screen(client), "mob_highlighter.scan_range"));
			return new int[] {widget.x() + widget.width(), widget.y()};
		});
		int[] widths = context.computeOnClient(client -> new int[] {client.font.width("Mob"), client.font.width("mob")});
		Path mob = context.takeScreenshot("t3.44-search-marks-mob");
		check(card.size() == 2, "\"mob\" marks the scan range's title and description, one box each: " + card.size() + " boxes");
		check(card.get(0)[1] == cardBox[1] + 7 && card.get(0)[2] == widths[0], "\"Mob\" on the title row (card y + 8, less 1), "
				+ widths[0] + " GUI px wide: " + java.util.Arrays.toString(card.get(0)));
		check((card.get(1)[1] == cardBox[1] + 19 || card.get(1)[1] == cardBox[1] + 29) && card.get(1)[2] == widths[1],
				"\"mob\" on a description row, " + widths[1] + " GUI px wide: " + java.util.Arrays.toString(card.get(1)));
		for (int[] box : card) {
			log += "; " + markProbe(mob, box, cardBox[0], "the scan range card");
		}
		// "radius" is one of the scan range's keywords, not in its text: the card is shown, with no box.
		focusSearch(context);
		replaceQuery(context, "radius");
		context.runOnClient(client -> screen(client).select(Category.HIGHLIGHTS));
		context.waitTicks(3);
		boolean shown = context.computeOnClient(client -> screen(client).page().page().rows().stream()
				.anyMatch(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals("mob_highlighter.scan_range")));
		int keyword = context.computeOnClient(client -> screen(client).page().card(option(screen(client), "mob_highlighter.scan_range")).marksForTests().size());
		check(shown && keyword == 0, "a match only in a keyword shows the card with no box: shown " + shown + ", " + keyword + " boxes");
		// Cleared with the keys, as a player clears it: in the next frame the card has no box.
		focusSearch(context);
		replaceQuery(context, "");
		String cleared = context.computeOnClient(client -> screen(client).query());
		int left = context.computeOnClient(client -> screen(client).page().card(option(screen(client), "mob_highlighter.scan_range")).marksForTests().size());
		check(cleared.isEmpty() && left == 0, "a cleared query leaves no box: \"" + cleared + "\", " + left + " boxes");
		LOGGER.info("search marks: {}", log);
	}

	/** Clicks into the search box, so the keys reach it. */
	private static void focusSearch(ClientGameTestContext context) {
		int[] box = context.computeOnClient(client -> {
			ConfigLayout.Rect search = screen(client).frame().search();
			return new int[] {search.x() + 20, search.y() + search.h() / 2};
		});
		click(context, box);
	}

	/** The rule whose header shows {@code label} on the shown page. */
	private static com.k8bas.skyblockutility.ui.option.RuleGroup headerRule(ConfigScreen screen, String label) {
		return headers(screen).stream().map(ConfigLayout.RuleHeader::rule).filter(rule -> label.equals(rule.label().get())).findFirst().orElseThrow();
	}

	/** Checks one match box at GUI scale 2 in the screenshot, its line running to {@code right}; returns a log line. */
	private static String markProbe(Path shot, int[] box, int right, String what) {
		java.awt.image.BufferedImage image;
		try {
			image = javax.imageio.ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
		int scale = 2;
		// The box's top row lies above the glyphs; the row above the box is the card.
		int inside = image.getRGB((box[0] + box[2] / 2) * scale, box[1] * scale) & 0xFFFFFF;
		int above = image.getRGB((box[0] + box[2] / 2) * scale, box[1] * scale - 1) & 0xFFFFFF;
		int accent = Theme.current().accent() & 0xFFFFFF;
		check(distance(inside, accent) + 20 < distance(above, accent),
				what + ": the box's top row " + Integer.toHexString(inside) + " is tinted toward the accent against " + Integer.toHexString(above));
		int brightInside = brightest(image, box[0] * scale, (box[0] + box[2]) * scale, box[1] * scale, (box[1] + box[3]) * scale);
		// The glyphs just after the box on the same line (each box here is followed by text): not the colour dot or a button.
		int after = box[0] + box[2];
		int brightOutside = brightest(image, after * scale, Math.min(right, after + 12) * scale, box[1] * scale, (box[1] + box[3]) * scale);
		check(distance(brightInside, brightOutside) <= 6,
				what + ": the text in the box keeps its colour: " + Integer.toHexString(brightInside) + " against " + Integer.toHexString(brightOutside));
		return what + " box " + box[0] + "," + box[1] + " " + box[2] + "x" + box[3] + " top " + Integer.toHexString(inside) + " over "
				+ Integer.toHexString(above) + ", text " + Integer.toHexString(brightInside);
	}

	/** The brightest pixel (by r + g + b) in the physical-pixel rectangle; 0 for an empty one. */
	private static int brightest(java.awt.image.BufferedImage image, int x0, int x1, int y0, int y1) {
		int best = 0;
		for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
				int rgb = image.getRGB(x, y) & 0xFFFFFF;
				if (sum(rgb) > sum(best)) {
					best = rgb;
				}
			}
		}
		return best;
	}

	private static int sum(int rgb) {
		return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
	}

	private static int distance(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}

	private static void replaceQuery(ClientGameTestContext context, String query) {
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		if (query.isEmpty()) {
			context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
		} else {
			context.getInput().typeChars(query);
		}
		context.waitTicks(2);
	}

	private static List<ConfigLayout.RuleHeader> headers(ConfigScreen screen) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.RuleHeader).map(row -> (ConfigLayout.RuleHeader) row)
				.toList();
	}

	private static int problemRows(ClientGameTestContext context) {
		return context.computeOnClient(client -> (int) screen(client).page().page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.RuleProblem).count());
	}

	static int[] headerPoint(ClientGameTestContext context, String title) {
		// Scrolled into view first; the header's place is read after the next layout.
		context.runOnClient(client -> {
			ConfigLayout.RuleHeader row = headerRow(screen(client), title);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		return context.computeOnClient(client -> {
			RuleHeaderWidget header = screen(client).page().header(headerRow(screen(client), title).rule());
			return new int[] {header.x() + 60, header.y() + header.height() / 2};
		});
	}

	private static ConfigLayout.RuleHeader headerRow(ConfigScreen screen, String title) {
		for (ConfigLayout.RuleHeader row : headers(screen)) {
			if (screen.page().header(row.rule()).title().equals(title)) {
				return row;
			}
		}
		throw new AssertionError("FAILED: no rule " + title);
	}

	private static Option option(ConfigScreen screen, String id) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow option && option.option().id().equals(id))
				.map(row -> ((ConfigLayout.OptionRow) row).option()).findFirst().orElseThrow(() -> new AssertionError("FAILED: not shown: " + id));
	}

	private static String fieldText(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> ((TextField) screen(client).page().control(option(screen(client), id))).model().text());
	}

	private static boolean fieldMarked(ConfigScreen screen, String id) {
		Option option = option(screen, id);
		return ((com.k8bas.skyblockutility.ui.option.TextOption) option).invalid().getAsBoolean();
	}

	/** The centre of a rule's Remove button, at the right end of its header (R31); the header is scrolled into view first. */
	static int[] removeCentre(ClientGameTestContext context, String ruleId) {
		context.runOnClient(client -> {
			ConfigLayout.RuleHeader row = ruleHeader(screen(client), ruleId);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		return context.computeOnClient(client -> centreOf(screen(client).page().control(ruleHeader(screen(client), ruleId).rule().remove())));
	}

	private static ConfigLayout.RuleHeader ruleHeader(ConfigScreen screen, String ruleId) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.RuleHeader header && header.rule().id().equals(ruleId))
				.map(row -> (ConfigLayout.RuleHeader) row).findFirst().orElseThrow(() -> new AssertionError("FAILED: no rule header " + ruleId));
	}

	static int[] controlCentre(ClientGameTestContext context, String id) {
		// Scrolled into view first; the control's place is read after the next layout.
		context.runOnClient(client -> {
			ConfigScreen screen = screen(client);
			Option option = option(screen, id);
			ConfigLayout.OptionRow row = screen.page().page().rows().stream()
					.filter(r -> r instanceof ConfigLayout.OptionRow o && o.option() == option).map(r -> (ConfigLayout.OptionRow) r).findFirst()
					.orElseThrow();
			screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		return context.computeOnClient(client -> centreOf(screen(client).page().control(option(screen(client), id))));
	}

	static void replaceText(ClientGameTestContext context, String id, String text) {
		click(context, controlCentre(context, id));
		check(context.computeOnClient(client -> screen(client).keyboardFocus() == screen(client).page().control(option(screen(client), id))),
				"the click focused " + id);
		context.getInput().holdControl();
		context.getInput().pressKey(GLFW.GLFW_KEY_A);
		context.getInput().releaseControl();
		context.getInput().typeChars(text);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTick();
	}

	/** A colour set through the colour picker: its hex field, then Save. */
	static void pickColour(ClientGameTestContext context, String id, String hex) {
		click(context, controlCentre(context, id));
		int[] field = context.computeOnClient(client -> centreOf(((ColorPickerOverlay) screen(client).overlay()).widgets().stream()
				.filter(widget -> widget instanceof TextField).findFirst().orElseThrow()));
		click(context, field);
		context.getInput().typeChars(hex);
		context.waitTicks(2);
		int[] save = context.computeOnClient(client -> {
			List<Widget> buttons = ((ColorPickerOverlay) screen(client).overlay()).widgets().stream().filter(widget -> widget instanceof Button).toList();
			return centreOf(buttons.get(buttons.size() - 1));
		});
		click(context, save);
	}

	private static int[] centreOf(Widget widget) {
		return new int[] {widget.x() + widget.width() / 2, widget.y() + widget.height() / 2};
	}

	static void click(ClientGameTestContext context, int[] gui) {
		// One frame first, so a scroll asked for while finding the point has been laid out.
		context.waitTick();
		int[] window = context.computeOnClient(client -> window(gui[0], gui[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static int[] window(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	private static int pixel(ClientGameTestContext context, Path screenshot, int[] gui) {
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			return image.getRGB((int) Math.floor((gui[0] + 0.5) * image.getWidth() / size[0]), (int) Math.floor((gui[1] + 0.5) * image.getHeight() / size[1]))
					& 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	static String configFile(ClientGameTestContext context) {
		Path file = context.computeOnClient(client -> FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json"));
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static ConfigScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ConfigScreen screen)) {
			throw new AssertionError("FAILED: the settings screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}

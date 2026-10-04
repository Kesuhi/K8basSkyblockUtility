package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.module.mobhighlighter.MobDatabaseEntry;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRulesForTests;
import com.k8bas.skyblockutility.module.npcsearch.NpcDatabaseEntry;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRulesForTests;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.5c, the database picker in a real client (the test client has no network, so the lists are
 * given to the databases directly):
 * <ul>
 *   <li>AC-UI-10 (in full): an entry added from the picker becomes a rule at once, is written at once
 *       and leaves the picker; Done closes it with no "Changes not saved" prompt; edits to it, a colour-only
 *       change of a second rule and a Delete of a third are in the file, and after a restart (the rules read
 *       back from the file) the entry is still gone from the picker;</li>
 *   <li>AC-UI-18 [C]: the NPC picker with 150 entries scrolls from start to end, in screenshots;</li>
 *   <li>EC-UI-08: when the fetch failed, the picker says the database is unavailable and offers nothing.</li>
 * </ul>
 */
public class DatabasePickerGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		List<HighlightRule> mobsBefore = context.computeOnClient(client -> MobRulesForTests.rules());
		List<NpcRule> npcsBefore = context.computeOnClient(client -> NpcRulesForTests.rules());
		RuleDatabase.State mobState = context.computeOnClient(client -> MobRulesForTests.databaseState());
		RuleDatabase.State npcState = context.computeOnClient(client -> NpcRulesForTests.databaseState());
		List<MobDatabaseEntry> mobEntries = context.computeOnClient(client -> MobRulesForTests.useDatabase(mobs(), RuleDatabase.State.READY));
		List<NpcDatabaseEntry> npcEntries = context.computeOnClient(client -> NpcRulesForTests.useDatabase(npcs(150), RuleDatabase.State.READY));
		try {
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				MobRulesForTests.use(List.of(RuleCardsGameTest.mob("keep", "Voidgloom", "Voidgloom", null, 0xFFAA00),
						RuleCardsGameTest.mob("drop", "Delete me", "Delete me", null, 0x00FF00)));
				NpcRulesForTests.use(List.of());
			});
			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			addFromThePicker(context);
			scrollSequence(context);
			unavailable(context);
		} finally {
			context.runOnClient(client -> {
				MobRulesForTests.useDatabase(mobEntries, mobState);
				NpcRulesForTests.useDatabase(npcEntries, npcState);
				MobRulesForTests.use(mobsBefore);
				NpcRulesForTests.use(npcsBefore);
				ConfigManager.save();
				ConfigManager.flush();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** AC-UI-10 in full. */
	private static void addFromThePicker(ClientGameTestContext context) {
		openPicker(context, Category.HIGHLIGHTS, "mob_highlighter.add_from_database");
		List<String> folders = context.computeOnClient(client -> picker(client).rows().stream().map(PickerRows.Row::label).toList());
		check(folders.equals(List.of("Dwarven Mines", "Hub", "The End")), "the islands, as closed folders: " + folders);
		context.takeScreenshot("t2.5c-picker-folders");
		int[] field = context.computeOnClient(client -> centreOf(picker(client).searchField()));
		click(context, field);
		context.getInput().typeChars("zeal");
		context.waitTicks(2);
		List<String> found = context.computeOnClient(client -> picker(client).rows().stream().map(PickerRows.Row::label).toList());
		check(found.equals(List.of("The End", "Special Zealot", "Zealot")), "\"zeal\" finds both Zealots in their open folder: " + found);
		context.takeScreenshot("t2.5c-picker-search");
		int before = context.computeOnClient(client -> ConfigManager.saveRequests());
		click(context, addButton(context, "Zealot"));
		List<HighlightRule> rules = context.computeOnClient(client -> MobRulesForTests.rules());
		check(rules.size() == 3 && "zealot".equals(rules.get(2).sourceId) && "Zealot".equals(rules.get(2).label), "Add made the rule at once: " + rules);
		String added = rules.get(2).id;
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) - before == 1, "and wrote it at once");
		context.waitTicks(2);
		List<String> after = context.computeOnClient(client -> picker(client).rows().stream().map(PickerRows.Row::label).toList());
		check(after.equals(List.of("The End", "Special Zealot")), "the added entry left the picker: " + after);
		click(context, context.computeOnClient(client -> centreOf(picker(client).doneButton())));
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "Done closes the picker");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "with no prompt; the settings stay open");
		List<String> cards = context.computeOnClient(client -> screen(client).page().page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.RuleHeader).map(row -> screen(client).page().header(((ConfigLayout.RuleHeader) row).rule()).title())
				.toList());
		check(cards.equals(List.of("Voidgloom", "Delete me", "Zealot")), "its card is in the list: " + cards);

		// The added rule edited, only the colour of the second changed, the third deleted; then a restart.
		String rule = "mob_highlighter.rule.";
		RuleCardsGameTest.click(context, RuleCardsGameTest.headerPoint(context, "Zealot"));
		RuleCardsGameTest.replaceText(context, rule + added + ".label", "Zealot Hunter");
		RuleCardsGameTest.replaceText(context, rule + added + ".pattern", "Zealot Hunter");
		RuleCardsGameTest.pickColour(context, rule + added + ".colour", "#00AAFF");
		RuleCardsGameTest.click(context, RuleCardsGameTest.headerPoint(context, "Voidgloom"));
		RuleCardsGameTest.pickColour(context, rule + "keep.colour", "#123456");
		RuleCardsGameTest.click(context, RuleCardsGameTest.headerPoint(context, "Delete me"));
		RuleCardsGameTest.click(context, RuleCardsGameTest.controlCentre(context, rule + "drop.delete"));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen), "Esc closed the settings");
		context.runOnClient(client -> ConfigManager.flush());
		List<HighlightRule> restarted = context.computeOnClient(client -> MobRulesForTests.useRulesFromFile());
		check(restarted.size() == 2, "the file holds the kept and the added rule: " + restarted.stream().map(r -> r.label).toList());
		HighlightRule keep = restarted.get(0);
		HighlightRule zealot = restarted.get(1);
		check("keep".equals(keep.id) && keep.color == 0x123456 && "Voidgloom".equals(keep.label) && "Voidgloom".equals(keep.namePattern),
				"only the colour of the second rule changed: " + keep.label + " " + Integer.toHexString(keep.color));
		check("zealot".equals(zealot.sourceId) && "Zealot Hunter".equals(zealot.label) && "Zealot Hunter".equals(zealot.namePattern)
				&& zealot.color == 0x00AAFF, "the added rule kept its edits and its source: " + zealot.label + " " + zealot.namePattern + " "
						+ Integer.toHexString(zealot.color) + " " + zealot.sourceId);
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		openPicker(context, Category.HIGHLIGHTS, "mob_highlighter.add_from_database");
		click(context, context.computeOnClient(client -> centreOf(picker(client).searchField())));
		context.getInput().typeChars("zeal");
		context.waitTicks(2);
		List<String> reopened = context.computeOnClient(client -> picker(client).rows().stream().map(PickerRows.Row::label).toList());
		check(reopened.equals(List.of("The End", "Special Zealot")), "after the restart the added entry is still gone from the picker: " + reopened);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "the picker is closed");
		LOGGER.info("database picker: folders {}, \"zeal\" found {}, Zealot added and written at once, gone from the picker; its edits, a "
				+ "colour-only change and a Delete survived a restart, and Zealot stayed out of the picker", folders, found);
	}

	/** AC-UI-18 [C]: the NPC picker scrolls from start to end. */
	private static void scrollSequence(ClientGameTestContext context) {
		openPicker(context, Category.WAYPOINTS, "npc_search.add_from_database");
		// Every folder open: a search that every entry matches.
		click(context, context.computeOnClient(client -> centreOf(picker(client).searchField())));
		context.getInput().typeChars("npc");
		context.waitTicks(2);
		int total = context.computeOnClient(client -> picker(client).rows().size());
		check(total > 150, "every folder and NPC: " + total + " rows");
		context.takeScreenshot("t2.5c-npc-picker-start");
		int[] middle = context.computeOnClient(client -> centreOf(picker(client).list()));
		int[] window = context.computeOnClient(client -> window(middle[0], middle[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		int shots = 0;
		for (int notch = 0; notch < 200; notch++) {
			context.getInput().scroll(-1.0);
			context.waitTick();
			if (notch == 25) {
				context.takeScreenshot("t2.5c-npc-picker-middle");
				shots++;
			}
			boolean atEnd = context.computeOnClient(client -> picker(client).list().rows().scroll() == picker(client).list().rows().maxScroll());
			if (atEnd) {
				break;
			}
		}
		context.takeScreenshot("t2.5c-npc-picker-end");
		int last = context.computeOnClient(client -> picker(client).list().rows().visible().end() - 1);
		check(last == total - 1, "scrolled to the last row: " + last + " of " + (total - 1));
		// The first Esc leaves the search field (REQ-UI-22), the second closes the picker.
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, "the first Esc only leaves the field");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "the second closes the picker");
		LOGGER.info("database picker: {} NPC rows scrolled from start to end ({} screenshot in between)", total, shots);
	}

	/** EC-UI-08. */
	private static void unavailable(ClientGameTestContext context) {
		context.runOnClient(client -> MobRulesForTests.useDatabase(mobs(), RuleDatabase.State.UNAVAILABLE));
		openPicker(context, Category.HIGHLIGHTS, "mob_highlighter.add_from_database");
		String message = context.computeOnClient(client -> picker(client).message());
		check(message != null && message.contains("unavailable") && context.computeOnClient(client -> picker(client).rows().isEmpty()),
				"a failed fetch: the picker says so and offers nothing: " + message);
		context.takeScreenshot("t2.5c-picker-unavailable");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		LOGGER.info("database picker: a failed fetch shows \"{}\" and nothing to add", message);
	}

	private static void openPicker(ClientGameTestContext context, Category category, String optionId) {
		context.runOnClient(client -> screen(client).select(category));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		// Scrolled into view first; the button's place is read after the next layout.
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = optionRow(screen(client), optionId);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		int[] button = context.computeOnClient(client -> centreOf(screen(client).page().control(optionRow(screen(client), optionId).option())));
		click(context, button);
		check(context.computeOnClient(client -> screen(client).overlay()) instanceof DatabasePickerOverlay, "the button opens the picker");
		context.waitTicks(2);
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, String optionId) {
		return screen.page().page().rows().stream().filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId))
				.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow(() -> new AssertionError("FAILED: no " + optionId));
	}

	/** The middle of an entry's Add button, in GUI pixels. */
	private static int[] addButton(ClientGameTestContext context, String name) {
		return context.computeOnClient(client -> {
			DatabasePickerOverlay picker = picker(client);
			List<PickerRows.Row> rows = picker.rows();
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).entry() != null && rows.get(i).label().equals(name)) {
					Widget list = picker.list();
					int[] add = DatabasePickerOverlay.addButton(list.width() - (picker.list().rows().overflows() ? 6 : 0), DatabasePickerOverlay.ROW_HEIGHT);
					return new int[] {list.x() + add[0] + add[2] / 2, list.y() + picker.list().rows().rowTop(i) + add[1] + add[3] / 2};
				}
			}
			throw new AssertionError("FAILED: no row " + name);
		});
	}

	private static List<MobDatabaseEntry> mobs() {
		List<MobDatabaseEntry> entries = new ArrayList<>();
		entries.add(mob("zealot", "Zealot", "The End", null));
		entries.add(mob("special_zealot", "Special Zealot", "The End", null));
		entries.add(mob("trick_or_treater", "Trick or Treater", "Hub", "Spooky Festival"));
		entries.add(mob("golden_goblin", "Golden Goblin", "Dwarven Mines", null));
		return entries;
	}

	private static MobDatabaseEntry mob(String id, String name, String island, String subfolder) {
		MobDatabaseEntry entry = new MobDatabaseEntry();
		entry.id = id;
		entry.displayName = name;
		entry.matchText = name;
		entry.island = island;
		entry.subfolder = subfolder;
		return entry;
	}

	private static List<NpcDatabaseEntry> npcs(int count) {
		List<NpcDatabaseEntry> entries = new ArrayList<>();
		String[] islands = {"Hub", "The End", "Dwarven Mines", "Crimson Isle", "The Park"};
		for (int i = 0; i < count; i++) {
			NpcDatabaseEntry entry = new NpcDatabaseEntry();
			entry.id = "npc_" + i;
			entry.displayName = String.format("NPC %03d", i);
			entry.matchText = entry.displayName;
			entry.island = islands[i % islands.length];
			entries.add(entry);
		}
		return entries;
	}

	private static DatabasePickerOverlay picker(Minecraft client) {
		if (!(screen(client).overlay() instanceof DatabasePickerOverlay picker)) {
			throw new AssertionError("FAILED: the picker is not open");
		}
		return picker;
	}

	private static int[] centreOf(Widget widget) {
		return new int[] {widget.x() + widget.width() / 2, widget.y() + widget.height() / 2};
	}

	private static void click(ClientGameTestContext context, int[] gui) {
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

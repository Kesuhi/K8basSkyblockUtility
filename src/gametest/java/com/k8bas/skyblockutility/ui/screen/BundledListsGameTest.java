package com.k8bas.skyblockutility.ui.screen;

import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.data.BundledData;
import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;
import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.module.mobhighlighter.MobDatabase;
import com.k8bas.skyblockutility.module.mobhighlighter.MobList;
import com.k8bas.skyblockutility.module.mobhighlighter.MobRulesForTests;
import com.k8bas.skyblockutility.module.mobhighlighter.MobTable;
import com.k8bas.skyblockutility.module.npcsearch.NpcDatabase;
import com.k8bas.skyblockutility.module.npcsearch.NpcList;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRulesForTests;
import com.k8bas.skyblockutility.module.npcsearch.NpcTable;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The bundled NPC and mob lists in the dev client (T3.8, AC-NPCDB-02 [B], AC-NPCDB-03 [B], AC-DATA-05 [B],
 * EC-UI-08). With no network and no rules, each picker lists exactly its table's row count from the data
 * index, in island folders, and nothing asks a gist or leaves a cache in the config folder. A registry without
 * the mob table makes only the mob picker unavailable; the NPC picker still lists its entries.
 */
public class BundledListsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String MOB_PICKER = "mob_highlighter.add_from_database";
	private static final String NPC_PICKER = "npc_search.add_from_database";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		List<HighlightRule> mobsBefore = context.computeOnClient(client -> MobRulesForTests.rules());
		List<NpcRule> npcsBefore = context.computeOnClient(client -> NpcRulesForTests.rules());
		try {
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				MobDatabase.useForTest(null, null);
				NpcDatabase.useForTest(null, null);
				// No rule hides an entry, so every row of the table shows.
				MobRulesForTests.use(List.of());
				NpcRulesForTests.use(List.of());
			});
			context.waitFor(client -> MobDatabase.state() == RuleDatabase.State.READY && NpcDatabase.state() == RuleDatabase.State.READY, 200);
			int mobRows = indexRows("npcdb.mobs");
			int npcRows = indexRows("npcdb.npcs");
			check(context.computeOnClient(client -> MobDatabase.entries().size()) == mobRows, "the mob list is the table");
			check(context.computeOnClient(client -> NpcDatabase.entries().size()) == npcRows, "the NPC list is the table");

			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			checkPickerLists(context, Category.HIGHLIGHTS, MOB_PICKER, mobRows);
			checkPickerLists(context, Category.WAYPOINTS, NPC_PICKER, npcRows);

			// AC-NPCDB-02, AC-NPCDB-03: both lists came from the bundled tables, and none of the gist fetch's log lines
			// ("<list> database fetch ...", "Loaded <n> <list> database entries") nor a gist host appears.
			List<String> described = context.computeOnClient(client -> BundledData.describe());
			for (String table : List.of("npcdb.npcs", "npcdb.mobs")) {
				check(described.stream().anyMatch(line -> line.startsWith(table + ":") && line.endsWith(", loaded")), table + " loaded from the bundle: " + described);
			}
			String log = read(FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log"));
			check(!log.contains("gist.githubusercontent.com") && !log.contains("database fetch")
					&& !Pattern.compile("Loaded \\d+ (NPC|mob) database entries").matcher(log).find(), "no request to a gist");
			try (Stream<Path> files = Files.walk(FabricLoader.getInstance().getConfigDir())) {
				List<String> cached = files.map(path -> path.getFileName().toString().toLowerCase())
						.filter(name -> name.contains("npc_database") || name.contains("mob_database") || name.contains("gist")).toList();
				check(cached.isEmpty(), "no NPC or mob data cached in the config folder: " + cached);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			// AC-DATA-05 [B], EC-UI-08: both lists read one registry that lacks the mob table; only the mob picker is
			// unavailable, and the NPC list still loads from that same registry.
			context.runOnClient(client -> {
				DataRegistry registry = new DataRegistry(path -> path.equals("npcdb/mobs.json") ? null : resource("data/" + path),
						Runnable::run, Runnable::run, message -> LOGGER.info("bundled lists: {}", message));
				TableHandle<MobList> mobs = registry.register(MobTable.SPEC);
				TableHandle<NpcList> npcs = registry.register(NpcTable.SPEC);
				registry.start();
				MobDatabase.useTableForTest(mobs);
				NpcDatabase.useTableForTest(npcs);
			});
			check(context.computeOnClient(client -> MobDatabase.state()) == RuleDatabase.State.UNAVAILABLE, "the mob list is unavailable");
			check(context.computeOnClient(client -> NpcDatabase.state()) == RuleDatabase.State.READY, "the NPC list from the same registry loads");
			openPicker(context, Category.HIGHLIGHTS, MOB_PICKER);
			String message = context.computeOnClient(client -> picker(client).message());
			check(message != null && message.contains("unavailable") && context.computeOnClient(client -> picker(client).rows().isEmpty()),
					"the mob picker says so and offers nothing: " + message);
			closePicker(context);
			checkPickerLists(context, Category.WAYPOINTS, NPC_PICKER, npcRows);
			LOGGER.info("bundled lists: {} mobs and {} NPCs offline; without the mob table only its picker says \"{}\"", mobRows, npcRows, message);
		} finally {
			context.runOnClient(client -> {
				MobDatabase.useTableForTest(null);
				NpcDatabase.useTableForTest(null);
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

	/** The picker's island folders together hold exactly {@code rows} entries. */
	private static void checkPickerLists(ClientGameTestContext context, Category category, String optionId, int rows) {
		openPicker(context, category, optionId);
		int listed = context.computeOnClient(client -> picker(client).rows().stream()
				.filter(row -> row.depth() == 0 && row.entry() == null).mapToInt(PickerRows.Row::count).sum());
		int folders = context.computeOnClient(client -> (int) picker(client).rows().stream().filter(row -> row.depth() == 0).count());
		check(listed == rows && folders > 1, optionId + ": " + listed + " entries in " + folders + " island folders, the table has " + rows);
		closePicker(context);
	}

	private static int indexRows(String table) {
		return JsonParser.parseString(new String(resource("data/index.json"), StandardCharsets.UTF_8)).getAsJsonObject()
				.getAsJsonObject("tables").getAsJsonObject(table).get("rows").getAsInt();
	}

	private static byte[] resource(String path) {
		try (InputStream in = BundledListsGameTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void openPicker(ClientGameTestContext context, Category category, String optionId) {
		context.runOnClient(client -> screen(client).select(category));
		context.runOnClient(client -> screen(client).scrollTo(0));
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = optionRow(screen(client), optionId);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		int[] button = context.computeOnClient(client -> centreOf(screen(client).page().control(optionRow(screen(client), optionId).option())));
		int[] window = context.computeOnClient(client -> window(button[0], button[1]));
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) instanceof DatabasePickerOverlay, "the button opens the picker");
	}

	private static void closePicker(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		if (context.computeOnClient(client -> screen(client).overlay()) != null) {
			// The first Esc may only leave the search field.
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
		}
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "the picker closes");
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, String optionId) {
		return screen.page().page().rows().stream().filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId))
				.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow(() -> new AssertionError("FAILED: no " + optionId));
	}

	private static DatabasePickerOverlay picker(Minecraft client) {
		if (!(screen(client).overlay() instanceof DatabasePickerOverlay picker)) {
			throw new AssertionError("FAILED: the picker is not open");
		}
		return picker;
	}

	private static ConfigScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ConfigScreen screen)) {
			throw new AssertionError("FAILED: the settings screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static int[] centreOf(Widget widget) {
		return new int[] {widget.x() + widget.width() / 2, widget.y() + widget.height() / 2};
	}

	private static int[] window(int guiX, int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}

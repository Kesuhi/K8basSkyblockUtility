package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.gametest.ModuleStates;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.bestiary.BestiaryConfig;
import com.k8bas.skyblockutility.module.bestiary.BestiaryHud;
import com.k8bas.skyblockutility.module.bestiary.BestiaryModule;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Checklist;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.Theme;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * T5.3c, AC-UI-67 [C] (REQ-UI-61, R60) at GUI scale 2 on the Bestiary Tracker card, whose ten line toggles are one
 * checklist: its button reads "6/10" with the default lines; a click opens the list under the card, one check box per line,
 * filled in the accent when on and in the field colour when off (screenshot t5.3c-checklist-open); a click on the "To max"
 * row turns that line on in the config and in the HUD preview (AC-BEST-07) and the button reads "7/10"; Space on the
 * row turns it off again, and the preview leaves it out; a second click on the button closes the list; the search "session
 * kills" shows it open with the match marked (screenshot t5.3c-checklist-search), clearing the search closes it again, and a
 * query only in the card's title ("lines shown") shows the card with the list closed.
 */
public class ChecklistGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		ModuleStates states = context.computeOnClient(client -> ModuleStates.capture());
		BestiaryModule module = context.computeOnClient(client -> ModuleManager.modules().stream()
				.filter(BestiaryModule.class::isInstance).map(BestiaryModule.class::cast).findFirst().orElseThrow());
		BestiaryConfig settings = module.configForTest();
		// The card's own items, bound to the live config: they read the lines as they are, and put them back after.
		List<Toggle> items = lines(BestiaryModule.cards(settings, enabled -> { })).items();
		boolean[] was = context.computeOnClient(client -> {
			boolean[] values = new boolean[items.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = items.get(i).binding().get();
			}
			return values;
		});
		try {
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				items.forEach(item -> item.binding().set(item.defaultValue()));
				// ON, so the rows are not dimmed and their colours can be read off the screenshot.
				module.setEnabled(true);
			});
			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.runOnClient(client -> screen(client).select(Category.ODDS_TRACKERS));
			context.waitTicks(3);
			String label = context.computeOnClient(client -> button(screen(client)).label());
			check(label.equals("6/10"), "the button reads the default lines: " + label);
			check(checkRows(context).isEmpty(), "closed: no rows");

			RuleCardsGameTest.click(context, RuleCardsGameTest.controlCentre(context, "bestiary.lines"));
			List<ConfigLayout.CheckRow> rows = checkRows(context);
			check(context.computeOnClient(client -> button(screen(client)).isOpen()) && rows.size() == 10,
					"a click opens the list, one row per line: " + rows.size());
			// The card and its whole list in view.
			context.runOnClient(client -> {
				ConfigScreen screen = screen(client);
				ConfigLayout.OptionRow card = optionRow(screen, "bestiary.lines");
				ConfigLayout.CheckRow last = rows.get(rows.size() - 1);
				screen.scrollArea().ensureVisible(card.y(), last.y() + last.height());
			});
			// The pointer off the page, so the review image shows no hover.
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
			Path open = context.takeScreenshot("t5.3c-checklist-open");
			int accent = context.computeOnClient(client -> Theme.current().accent() & 0xFFFFFF);
			int on = boxColour(context, open, "bestiary.line_family");
			int off = boxColour(context, open, "bestiary.line_to_max");
			check(on == accent, "\"Family and tier\" is on: its box in the accent " + hex(accent) + ": " + hex(on));
			check(off == (Theme.SIDEBAR & 0xFFFFFF), "\"To max\" is off: its box in the field colour " + hex(Theme.SIDEBAR & 0xFFFFFF) + ": " + hex(off));
			// Outlined: the middle of its left edge is nearer the off switch's grey than the field colour.
			int edge = edgeColour(context, open, "bestiary.line_to_max");
			check(distance(edge, Theme.TOGGLE_OFF) < distance(edge, Theme.SIDEBAR), "\"To max\" has its outline, " + hex(Theme.TOGGLE_OFF & 0xFFFFFF) + ": "
					+ hex(edge));

			RuleCardsGameTest.click(context, rowCentre(context, "bestiary.line_to_max"));
			context.waitTicks(2);
			label = context.computeOnClient(client -> button(screen(client)).label());
			List<String> preview = context.computeOnClient(client -> ((HudText) BestiaryHud.INSTANCE.preview()).lines());
			check(settings.lineToMax && label.equals("7/10"), "a click on the row turns To max on: " + settings.lineToMax + ", " + label);
			check(preview.stream().anyMatch(line -> line.startsWith("§7To max: ")), "the HUD preview shows it (AC-BEST-07): " + preview);

			// Space acts on the row the click left the keyboard's place on.
			context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
			// As a real Space press: its character follows the key, and the screen types it nowhere.
			context.getInput().typeChar(' ');
			context.waitTicks(2);
			label = context.computeOnClient(client -> button(screen(client)).label());
			check(!settings.lineToMax && label.equals("6/10"), "Space on the focused row turns it off: " + settings.lineToMax + ", " + label);
			List<String> hidden = context.computeOnClient(client -> ((HudText) BestiaryHud.INSTANCE.preview()).lines());
			check(hidden.stream().noneMatch(line -> line.startsWith("§7To max: ")), "the HUD preview leaves it out again (AC-BEST-07): " + hidden);

			RuleCardsGameTest.click(context, RuleCardsGameTest.controlCentre(context, "bestiary.lines"));
			check(checkRows(context).isEmpty() && !context.computeOnClient(client -> button(screen(client)).isOpen()), "a second click closes it");

			// The search: an item's label shows the card with the list open and the match marked.
			int[] box = context.computeOnClient(client -> {
				ConfigLayout.Rect search = screen(client).frame().search();
				return new int[] {search.x() + 20, search.y() + search.h() / 2};
			});
			RuleCardsGameTest.click(context, box);
			context.getInput().typeChars("session kills");
			context.waitTicks(3);
			Category shown = context.computeOnClient(client -> screen(client).selected());
			String state = context.computeOnClient(client -> "query \"" + screen(client).query() + "\", rows " + screen(client).page().page().rows().size()
					+ ": " + screen(client).page().page().rows().stream().map(row -> row instanceof ConfigLayout.OptionRow o ? o.option().id() : row.getClass().getSimpleName()).toList());
			check(shown == Category.ODDS_TRACKERS && checkRows(context).size() == 10, "\"session kills\" shows the list open: " + shown + ", " + state);
			context.runOnClient(client -> {
				ConfigScreen screen = screen(client);
				ConfigLayout.CheckRow row = checkRow(screen, "bestiary.line_session_kills");
				screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
			});
			context.waitTicks(3);
			List<int[]> marks = context.computeOnClient(client -> screen(client).page().checkRow(item(screen(client), "bestiary.line_session_kills")).marksForTests());
			int width = context.computeOnClient(client -> client.font.width("Session kills"));
			context.takeScreenshot("t5.3c-checklist-search");
			check(marks.size() == 1 && marks.get(0)[2] == width, "the label's match is marked, " + width + " GUI px: "
					+ marks.stream().map(java.util.Arrays::toString).toList());
			context.getInput().holdControl();
			context.getInput().pressKey(GLFW.GLFW_KEY_A);
			context.getInput().releaseControl();
			context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
			context.waitTicks(3);
			check(checkRows(context).isEmpty(), "the search cleared, the list it opened is closed again");
			// A query only in the card's own title shows the card, with the list closed.
			RuleCardsGameTest.click(context, box);
			context.getInput().typeChars("lines shown");
			context.waitTicks(3);
			boolean card = context.computeOnClient(client -> screen(client).page().page().rows().stream()
					.anyMatch(row -> row instanceof ConfigLayout.OptionRow o && o.option().id().equals("bestiary.lines")));
			check(card && checkRows(context).isEmpty(), "\"lines shown\" shows the card with the list closed: " + card + ", " + checkRows(context).size() + " rows");
			LOGGER.info("checklist: 6/10 -> 7/10 -> 6/10, boxes on {} off {}, search mark {} GUI px", hex(on), hex(off), width);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				for (int i = 0; i < was.length; i++) {
					items.get(i).binding().set(was[i]);
				}
				states.restore();
				ConfigManager.putModuleSection(BestiaryModule.ID, settings);
				ConfigManager.save();
				ConfigManager.flush();
			});
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static Checklist lines(List<com.k8bas.skyblockutility.ui.option.Card> cards) {
		return cards.get(0).all().stream().filter(option -> option instanceof Checklist).map(option -> (Checklist) option).findFirst()
				.orElseThrow(() -> new AssertionError("FAILED: no checklist on the Bestiary Tracker card"));
	}

	private static ChecklistButton button(ConfigScreen screen) {
		return (ChecklistButton) screen.page().control(optionRow(screen, "bestiary.lines").option());
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, String id) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow o && o.option().id().equals(id))
				.map(row -> (ConfigLayout.OptionRow) row).findFirst().orElseThrow(() -> new AssertionError("FAILED: not shown: " + id));
	}

	private static List<ConfigLayout.CheckRow> checkRows(ClientGameTestContext context) {
		return context.computeOnClient(client -> screen(client).page().page().rows().stream().filter(row -> row instanceof ConfigLayout.CheckRow)
				.map(row -> (ConfigLayout.CheckRow) row).toList());
	}

	private static ConfigLayout.CheckRow checkRow(ConfigScreen screen, String id) {
		return screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.CheckRow c && c.item().id().equals(id))
				.map(row -> (ConfigLayout.CheckRow) row).findFirst().orElseThrow(() -> new AssertionError("FAILED: no row " + id));
	}

	private static Toggle item(ConfigScreen screen, String id) {
		return checkRow(screen, id).item();
	}

	private static int[] rowCentre(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> {
			CheckRowWidget row = screen(client).page().checkRow(item(screen(client), id));
			return new int[] {row.x() + row.width() / 2, row.y() + row.height() / 2};
		});
	}

	/** The check box's colour 2 GUI px in from its top-left corner, away from the check mark's strokes. */
	private static int boxColour(ClientGameTestContext context, Path screenshot, String id) {
		int[] box = context.computeOnClient(client -> screen(client).page().checkRow(item(screen(client), id)).boxForTests());
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			return image.getRGB((int) Math.floor((box[0] + 2.5) * image.getWidth() / size[0]), (int) Math.floor((box[1] + 2.5) * image.getHeight() / size[1]))
					& 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	/** The middle of the check box's left edge, where its outline runs. */
	private static int edgeColour(ClientGameTestContext context, Path screenshot, String id) {
		int[] box = context.computeOnClient(client -> screen(client).page().checkRow(item(screen(client), id)).boxForTests());
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			return image.getRGB((int) Math.floor((double) box[0] * image.getWidth() / size[0]), (int) Math.floor((box[1] + box[2] / 2.0) * image.getHeight() / size[1]))
					& 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	private static int distance(int rgb, int argb) {
		return Math.abs((rgb >> 16 & 0xFF) - (argb >> 16 & 0xFF)) + Math.abs((rgb >> 8 & 0xFF) - (argb >> 8 & 0xFF)) + Math.abs((rgb & 0xFF) - (argb & 0xFF));
	}

	private static String hex(int rgb) {
		return String.format("%06x", rgb);
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

package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.HudConfig;
import com.k8bas.skyblockutility.hud.HudAnchor;
import com.k8bas.skyblockutility.hud.HudContent;
import com.k8bas.skyblockutility.hud.HudElement;
import com.k8bas.skyblockutility.hud.HudForTests;
import com.k8bas.skyblockutility.hud.HudPosition;
import com.k8bas.skyblockutility.hud.HudPositions;
import com.k8bas.skyblockutility.hud.HudRect;
import com.k8bas.skyblockutility.hud.HudSnap;
import com.k8bas.skyblockutility.hud.HudRegistry;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.hud.TextMeasure;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * T2.8, the HUD editor with three gametest-only elements (REQ-HUD-10), at GUI scale 2 on 1920×1080:
 * <ul>
 *   <li>AC-HUD-08: while it is open each element is drawn once, by the editor, and not by the HUD;</li>
 *   <li>AC-HUD-07, AC-HUD-13 [C]: previews, outlines, a disabled element greyed with "(disabled)", a label below
 *       a box in the top 48 px, buttons in the Phase 2 style (screenshots);</li>
 *   <li>AC-HUD-04 [C] + AC-HUD-05: a real mouse drag of (+40, +25) moves it by as much over 100 move events with
 *       no write, then Esc writes once; three wheel notches give 1.30; arrows nudge 1 and, with Shift, 10 px;
 *       Cancel puts every element back; Reset Selected and Reset All;</li>
 *   <li>AC-HUD-14 [C] (T2.9c): dragged to 3 px from the vertical centre line it snaps there with its guide; with
 *       Alt held it follows the mouse and shows no guide;</li>
 *   <li>EC-HUD-07: another screen replacing the editor saves once, as Esc; EC-HUD-08: it works from the title
 *       screen with previews; EC-HUD-01: a resize keeps the selection and the changes.</li>
 * </ul>
 */
public class HudEditorGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String A = "k8bas_gametest_a";
	private static final String B = "k8bas_gametest_b";
	private static final String C = "k8bas_gametest_c";
	private static final HudPosition A_DEFAULT = new HudPosition(HudAnchor.TOP_LEFT, 100, 80, 1);
	private static final HudPosition B_DEFAULT = new HudPosition(HudAnchor.TOP_LEFT, 300, 12, 1);
	private static final HudPosition C_DEFAULT = new HudPosition(HudAnchor.BOTTOM_RIGHT, -8, -40, 1);

	@Override
	public void runTest(ClientGameTestContext context) {
		HudConfig hudBefore = context.computeOnClient(client -> ConfigManager.general().hud);
		context.getInput().resizeWindow(1920, 1080);
		try {
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				ConfigManager.general().hud = null;
				HudRegistry.register(new Element(A, "Box A", true, new Box(60, 30, 0xFFC03030), A_DEFAULT));
				HudRegistry.register(new Element(B, "Box B", false, null, B_DEFAULT));
				HudRegistry.register(new Element(C, "Text C", true, null, C_DEFAULT));
			});
			try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
				singleplayer.getConnection().waitForChunksRender();
				drawnOnceByTheEditor(context);
				dragNudgeScaleAndSave(context);
				cancelAndResets(context);
				snapsToTheCentreLine(context);
				externalCloseSaves(context);
				untouchedEntriesStay(context);
				entryPoints(context, singleplayer);
			}
			fromTheTitleScreen(context);
		} finally {
			context.runOnClient(client -> {
				HudRegistry.unregister(A);
				HudRegistry.unregister(B);
				HudRegistry.unregister(C);
				ConfigManager.general().hud = hudBefore;
				ConfigManager.save();
				ConfigManager.flush();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** AC-HUD-08, AC-HUD-07, AC-HUD-13. */
	private static void drawnOnceByTheEditor(ClientGameTestContext context) {
		open(context, null);
		context.runOnClient(client -> HudForTests.forgetDrawn());
		context.waitTicks(2);
		List<String> drawn = context.computeOnClient(client -> editor(client).drawnLastFrame());
		check(drawn.equals(List.of(A, B, C)), "the editor draws each element once: " + drawn);
		check(context.computeOnClient(client -> HudForTests.lastDrawn(A) == null && HudForTests.lastDrawn(C) == null),
				"and the HUD draws none of them while it is open");
		HudRect b = rect(context, B);
		check(b.y() < HudEditorScreen.LABEL_FLIP, "box B is in the top 48 px, so its label is below it: " + b);
		check(context.computeOnClient(client -> editor(client).status()).equals(HudEditorScreen.NOTHING_SELECTED), "nothing selected yet");
		context.takeScreenshot("t2.8-editor");
		LOGGER.info("hud editor: {} drawn once each by the editor, none by the HUD", drawn);
	}

	/** AC-HUD-04 [C], AC-HUD-05, REQ-HUD-05. */
	private static void dragNudgeScaleAndSave(ClientGameTestContext context) {
		HudRect before = rect(context, A);
		int[] start = {(int) (before.x() + 20), (int) (before.y() + 10)};
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		cursor(context, start[0], start[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		for (int i = 1; i <= 100; i++) {
			cursorExact(context, start[0] + 40.0 * i / 100, start[1] + 25.0 * i / 100);
			if (i % 10 == 0) {
				context.waitTick();
			}
		}
		context.waitTick();
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		HudRect after = rect(context, A);
		check(after.x() == before.x() + 40 && after.y() == before.y() + 25, "the drag moved it by (+40, +25): " + before + " -> " + after);
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves, "100 moves wrote nothing");
		String status = context.computeOnClient(client -> editor(client).status());
		check(status.startsWith("Box A") && status.contains("X " + (int) after.x()) && status.contains("Scale 1.00"), "the status line: " + status);

		for (int i = 0; i < 3; i++) {
			context.getInput().scroll(1);
			context.waitTick();
		}
		double scale = context.computeOnClient(client -> editor(client).model().positions().get(A).scale());
		check(scale == 1.3, "three notches up from 1.00: " + scale);

		HudRect beforeNudge = rect(context, A);
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.waitTick();
		check(rect(context, A).x() == beforeNudge.x() + 1, "an arrow nudges 1 px");
		context.getInput().holdShift();
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.getInput().releaseShift();
		context.waitTick();
		check(rect(context, A).y() == beforeNudge.y() + 10, "Shift+arrow 10 px");
		context.takeScreenshot("t2.8-editor-selected");
		HudPosition edited = context.computeOnClient(client -> editor(client).model().positions().get(A));
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves, "still nothing written");

		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Esc closes it");
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "and writes once");
		check(context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT)).equals(edited), "with the edited place: " + edited);
		check(context.computeOnClient(client -> HudPositions.LIVE.has(B)), "B never moved: no entry", false);
		context.runOnClient(client -> HudForTests.forgetDrawn());
		context.waitTicks(2);
		HudRect inGame = context.computeOnClient(client -> HudForTests.lastDrawn(A));
		check(inGame != null && inGame.x() == rect(context, A, edited).x() && inGame.y() == rect(context, A, edited).y(),
				"the HUD draws it where it was left: " + inGame);
		LOGGER.info("hud editor: dragged (+40, +25) over 100 moves without a write, scaled to 1.30, nudged 1 and 10 px, Esc wrote once");
	}

	/** AC-HUD-04: Cancel, Reset Selected, Reset All; EC-HUD-01. */
	private static void cancelAndResets(ClientGameTestContext context) {
		Map<String, HudPosition> stored = context.computeOnClient(client -> Map.of(A, HudPositions.LIVE.get(A, A_DEFAULT), B,
				HudPositions.LIVE.get(B, B_DEFAULT), C, HudPositions.LIVE.get(C, C_DEFAULT)));
		open(context, null);
		click(context, centre(rect(context, C)));
		context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
		context.getInput().scroll(-1);
		click(context, centre(rect(context, B)));
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		HudPosition bMoved = context.computeOnClient(client -> editor(client).model().positions().get(B));
		HudPosition cMoved = context.computeOnClient(client -> editor(client).model().positions().get(C));
		check(!bMoved.equals(stored.get(B)), "B was nudged: " + bMoved);
		check(!cMoved.equals(stored.get(C)) && cMoved.scale() == 0.9, "C was nudged and scaled down: " + cMoved);
		// EC-HUD-01: a resize keeps the selection and the changes, and the status line follows.
		context.getInput().resizeWindow(1600, 900);
		context.waitTicks(3);
		check(B.equals(context.computeOnClient(client -> editor(client).model().selected())), "a resize keeps the selection");
		check(bMoved.equals(context.computeOnClient(client -> editor(client).model().positions().get(B))), "and the changes");
		HudRect bResized = rect(context, B);
		String status = context.computeOnClient(client -> editor(client).status());
		check(status.contains("X " + (int) bResized.x() + " ") && status.contains("Y " + (int) bResized.y() + " "), "the status line follows: " + status);
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(3);
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		Map<String, HudPosition> afterCancel = context.computeOnClient(client -> Map.of(A, HudPositions.LIVE.get(A, A_DEFAULT), B,
				HudPositions.LIVE.get(B, B_DEFAULT), C, HudPositions.LIVE.get(C, C_DEFAULT)));
		check(afterCancel.equals(stored), "Cancel put every element back: " + afterCancel + " vs " + stored);

		open(context, null);
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		click(context, centre(rect(context, A)));
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).resetSelectedButton()))));
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "Reset Selected writes");
		check(context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT)).equals(A_DEFAULT), "A is back at its default");
		check(context.computeOnClient(client -> HudPositions.LIVE.get(C, C_DEFAULT)).equals(stored.get(C)), "C is as it was");
		context.runOnClient(client -> editor(client).model().positions().put(C, new HudPosition(HudAnchor.CENTER, 0, 0, 2)));
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).resetAllButton()))));
		check(context.computeOnClient(client -> editor(client).model().positions()).equals(Map.of(A, A_DEFAULT, B, B_DEFAULT, C, C_DEFAULT)),
				"Reset All: every element at its default");
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).saveButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Save closes it");
		LOGGER.info("hud editor: Cancel restored {} entries; Reset Selected and Reset All put defaults back; a resize kept the selection", stored.size());
	}

	/**
	 * AC-HUD-14 [C] (T2.9c): A dragged to 3 px from the vertical centre line snaps there and shows the guide; with Alt
	 * held it follows the mouse and no guide shows; dropped, no guide stays. Cancelled, so nothing is kept.
	 */
	private static void snapsToTheCentreLine(ClientGameTestContext context) {
		open(context, null);
		HudRect before = rect(context, A);
		double startX = Math.floor(before.x() + 20) + 0.5;
		double startY = Math.floor(before.y() + 10) + 0.5;
		cursorExact(context, startX, startY);
		context.waitTicks(2);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		// To a left edge of 447: the centre (477) 3 px from the line at 480.
		double targetX = startX + (447 - before.x());
		for (int i = 1; i <= 20; i++) {
			cursorExact(context, startX + (targetX - startX) * i / 20, startY);
			context.waitTick();
		}
		context.waitTick();
		HudRect snapped = rect(context, A);
		check(snapped.x() == 450 && snapped.y() == before.y(), "snapped to the centre line: " + snapped);
		check(context.computeOnClient(client -> editor(client).model().guides(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()))
				.contains(new HudSnap.Guide(true, 480)), "with its guide");
		context.takeScreenshot("t2.9c-snap-centre");
		// Alt held: a move off and back to the same place, and the element follows the mouse.
		context.getInput().holdAlt();
		cursorExact(context, targetX + 1, startY);
		context.waitTick();
		cursorExact(context, targetX, startY);
		context.waitTicks(2);
		HudRect free = rect(context, A);
		check(free.x() == 447, "with Alt it does not snap: " + free);
		check(context.computeOnClient(client -> editor(client).model().guides(client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight())).isEmpty(), "and shows no guide");
		context.takeScreenshot("t2.9c-snap-alt-off");
		context.getInput().releaseAlt();
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		check(context.computeOnClient(client -> editor(client).model().guides(client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight())).isEmpty(), "no guide after the drop");
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		LOGGER.info("hud editor: A snapped to the centre line with its guide, followed the mouse with Alt held, no guide after the drop");
	}

	/** EC-HUD-07: another screen replacing the editor saves as Esc does. */
	private static void externalCloseSaves(ClientGameTestContext context) {
		open(context, null);
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.runOnClient(client -> {
			editor(client).model().positions().put(A, new HudPosition(HudAnchor.CENTER, 12, 34, 1.5));
			client.gui.setScreen(null);
		});
		context.waitTicks(2);
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "replaced, it wrote once");
		check(context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT)).equals(new HudPosition(HudAnchor.CENTER, 12, 34, 1.5)),
				"the change is kept");
		LOGGER.info("hud editor: replaced by another screen, it saved once");
	}

	/**
	 * EC-HUD-05 through the editor: an entry left alone stays exactly as written (a malformed one included), on
	 * Cancel and on Save; Cancel after a reset puts the reset entry back. REQ-CFG-10: a game quitting with the
	 * editor open saves its changes before the shutdown flush (SavesOnClose), and the removal writes nothing more.
	 */
	private static void untouchedEntriesStay(ClientGameTestContext context) {
		com.google.gson.JsonElement malformed = com.google.gson.JsonParser.parseString("{\"anchor\":\"NOWHERE\",\"x\":1}");
		context.runOnClient(client -> HudPositions.LIVE.restore(B, malformed));
		HudPosition aStored = context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT));
		com.google.gson.JsonElement cBefore = context.computeOnClient(client -> HudPositions.LIVE.raw(C));

		open(context, null);
		click(context, centre(rect(context, A)));
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		click(context, centre(rect(context, C)));
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).resetSelectedButton()))));
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(malformed.equals(context.computeOnClient(client -> HudPositions.LIVE.raw(B))), "Cancel leaves the malformed entry as written");
		check(aStored.equals(context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT))), "A is back");
		check(java.util.Objects.equals(cBefore, context.computeOnClient(client -> HudPositions.LIVE.raw(C))), "and C's entry is as before its reset");

		open(context, null);
		click(context, centre(rect(context, A)));
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(malformed.equals(context.computeOnClient(client -> HudPositions.LIVE.raw(B))), "Save leaves an entry it did not change as written");

		open(context, null);
		click(context, centre(rect(context, A)));
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		HudPosition edited = context.computeOnClient(client -> editor(client).model().positions().get(A));
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		context.runOnClient(client -> editor(client).saveBeforeShutdown());
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "quitting saves before the flush");
		check(edited.equals(context.computeOnClient(client -> HudPositions.LIVE.get(A, A_DEFAULT))), "with the change");
		context.setScreen(() -> null);
		check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves + 1, "and the removal writes nothing more");
		context.runOnClient(client -> HudPositions.LIVE.restore(B, null));
		LOGGER.info("hud editor: a malformed entry survived Cancel and Save, Cancel undid a reset, quitting saved once");
	}

	/**
	 * AC-HUD-06 [C]: `/ksu hud` opens the editor; "Edit HUD layout" in General opens it; "Edit position" on a HUD
	 * feature's card (a gametest card for element A) opens it with A selected and its name in the status line;
	 * closing returns to the settings screen.
	 */
	private static void entryPoints(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> client.player.connection.sendCommand("ksu hud"));
		context.waitForScreen(HudEditorScreen.class);
		check(context.computeOnClient(client -> editor(client).model().selected()) == null, "/ksu hud opens it with nothing selected");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen()) == null, "and Esc returns to the game");

		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		ConfigScreen settings = context.computeOnClient(client -> (ConfigScreen) client.gui.screen());
		pressAction(context, com.k8bas.skyblockutility.ui.option.Category.GENERAL, "hud.edit_layout");
		check(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen, "\"Edit HUD layout\" opens it");
		context.takeScreenshot("t2.8b-editor-from-general");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen()) == settings, "closing returns to the settings screen");
		context.setScreen(() -> null);

		com.k8bas.skyblockutility.ui.option.Card card = new com.k8bas.skyblockutility.ui.option.Card("gametest_hud_feature",
				com.k8bas.skyblockutility.ui.option.Category.GENERAL, "Gametest HUD feature", null,
				List.of(com.k8bas.skyblockutility.settings.HudOptions.editPosition("gametest_hud_feature", A)));
		context.setScreen(() -> new ConfigScreen(null, List.of(card)));
		context.waitForScreen(ConfigScreen.class);
		ConfigScreen withCard = context.computeOnClient(client -> (ConfigScreen) client.gui.screen());
		pressAction(context, com.k8bas.skyblockutility.ui.option.Category.GENERAL, "gametest_hud_feature.edit_position");
		check(A.equals(context.computeOnClient(client -> editor(client).model().selected())), "\"Edit position\" opens it with that element selected");
		String status = context.computeOnClient(client -> editor(client).status());
		check(status.startsWith("Box A"), "and its name in the status line: " + status);
		context.takeScreenshot("t2.8b-editor-edit-position");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen()) == withCard, "closing returns to the settings screen");
		context.setScreen(() -> null);
		LOGGER.info("hud editor: opened by /ksu hud, \"Edit HUD layout\" and \"Edit position\" (A selected); closing returned to the opener");
	}

	/** Clicks an action button of the settings screen, scrolled into view first. */
	private static void pressAction(ClientGameTestContext context, com.k8bas.skyblockutility.ui.option.Category category, String optionId) {
		context.runOnClient(client -> ((ConfigScreen) client.gui.screen()).select(category));
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigScreen screen = (ConfigScreen) client.gui.screen();
			ConfigLayout.OptionRow row = optionRow(screen, optionId);
			screen.scrollArea().ensureVisible(row.y(), row.y() + row.height());
		});
		context.waitTicks(2);
		HudRect button = context.computeOnClient(client -> {
			ConfigScreen screen = (ConfigScreen) client.gui.screen();
			return bounds(screen.page().control(optionRow(screen, optionId).option()));
		});
		click(context, centre(button));
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, String optionId) {
		return screen.page().page().rows().stream().filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId))
				.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow(() -> new AssertionError("FAILED: no " + optionId));
	}

	/** EC-HUD-08: from the title screen, previews, and Esc back to it. */
	private static void fromTheTitleScreen(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.setScreen(() -> new HudEditorScreen(new TitleScreen()));
		context.waitForScreen(HudEditorScreen.class);
		context.waitTicks(2);
		List<String> drawn = context.computeOnClient(client -> editor(client).drawnLastFrame());
		check(drawn.equals(List.of(A, B, C)), "with no world every element is drawn: " + drawn);
		context.takeScreenshot("t2.8-editor-title-screen");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
		LOGGER.info("hud editor: opened from the title screen with previews, Esc returned to it");
	}

	private static void open(ClientGameTestContext context, String select) {
		context.setScreen(() -> new HudEditorScreen(null, select));
		context.waitForScreen(HudEditorScreen.class);
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
	}

	private static HudEditorScreen editor(Minecraft client) {
		if (!(client.gui.screen() instanceof HudEditorScreen editor)) {
			throw new AssertionError("FAILED: the HUD editor is not open: " + client.gui.screen());
		}
		return editor;
	}

	private static HudRect rect(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> editor(client).model().rect(id, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()));
	}

	/** Where the HUD draws {@code position} for element A (60×30 at scale 1). */
	private static HudRect rect(ClientGameTestContext context, String id, HudPosition position) {
		return context.computeOnClient(client -> {
			double w = client.getWindow().getGuiScaledWidth();
			double h = client.getWindow().getGuiScaledHeight();
			return HudRect.onPixels(position.place(w, h, 60 * position.scale(), 30 * position.scale()), w, h);
		});
	}

	private static HudRect bounds(Widget widget) {
		return new HudRect(widget.x(), widget.y(), widget.width(), widget.height());
	}

	private static double[] centre(HudRect rect) {
		return new double[] {rect.x() + rect.w() / 2, rect.y() + rect.h() / 2};
	}

	private static void click(ClientGameTestContext context, double[] gui) {
		cursor(context, gui[0], gui[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** The cursor on the middle of GUI pixel ({@code x}, {@code y}). */
	private static void cursor(ClientGameTestContext context, double x, double y) {
		cursorExact(context, Math.floor(x) + 0.5, Math.floor(y) + 0.5);
		context.waitTicks(2);
	}

	private static void cursorExact(ClientGameTestContext context, double x, double y) {
		double[] window = context.computeOnClient(client -> {
			var w = client.getWindow();
			return new double[] {x * w.getScreenWidth() / w.getGuiScaledWidth(), y * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(window[0], window[1]);
	}

	private record Box(int width, int height, int argb) implements HudContent {
		@Override
		public int width(TextMeasure text) {
			return width;
		}

		@Override
		public int height(TextMeasure text) {
			return height;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font) {
			graphics.fill(0, 0, width, height, argb);
		}
	}

	/** A gametest-only element: B is disabled, B and C have no live content (only a preview). */
	private record Element(String id, String displayName, boolean enabled, HudContent content, HudPosition defaultPosition) implements HudElement {
		@Override
		public HudContent preview() {
			return HudText.of(List.of(displayName + " preview", "second line"), line -> true);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}

	private static void check(boolean value, String what, boolean expected) {
		check(value == expected, what);
	}
}

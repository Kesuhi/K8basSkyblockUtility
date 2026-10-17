package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.HairlineProbe;
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
import com.k8bas.skyblockutility.hud.ShippedHud;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.hud.TextMeasure;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.ui.widget.Button;
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
				// Only this test's elements: the shipped ones (the corpse ding) are put back afterwards.
				ShippedHud.elements().forEach(element -> HudRegistry.unregister(element.id()));
				HudRegistry.register(new Element(A, "Box A", true, new Box(60, 30, 0xFFC03030), A_DEFAULT));
				HudRegistry.register(new Element(B, "Box B", false, null, B_DEFAULT));
				HudRegistry.register(new Element(C, "Text C", true, null, C_DEFAULT));
			});
			try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
				singleplayer.getConnection().waitForChunksRender();
				drawnOnceByTheEditor(context);
				editorLook(context);
				pointerShapes(context);
				dragNudgeScaleAndSave(context);
				cancelAndResets(context);
				toolbar(context);
				hintGoesAtTheFirstDrag(context);
				hintGoesAtOnceWithAnimationsOff(context);
				aHandleDragOverTheCardFadesIt(context);
				snapsToTheCentreLine(context);
				cornerHandles(context);
				centresSnapToEachOther(context);
				externalCloseSaves(context);
				untouchedEntriesStay(context);
				entryPoints(context, singleplayer);
			}
			fromTheTitleScreen(context);
			noElements(context);
		} finally {
			context.runOnClient(client -> {
				HudRegistry.unregister(A);
				HudRegistry.unregister(B);
				HudRegistry.unregister(C);
				ShippedHud.register();
				ConfigManager.general().hud = hudBefore;
				ConfigManager.save();
				ConfigManager.flush();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/**
	 * AC-UI-58 [C] (REQ-UI-54, T3.47): with no element registered (in play the shipped corpse ding always is), the editor
	 * shows a card at the screen's centre saying so, and its status line stays empty. From the title screen.
	 */
	private static void noElements(ClientGameTestContext context) {
		context.runOnClient(client -> {
			HudRegistry.unregister(A);
			HudRegistry.unregister(B);
			HudRegistry.unregister(C);
		});
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitForScreen(HudEditorScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("t3.47-hud-editor-no-elements");
		String status = context.computeOnClient(client -> ((HudEditorScreen) client.gui.screen()).status());
		int[] card = context.computeOnClient(client -> ((HudEditorScreen) client.gui.screen()).emptyCardForTests());
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		check(status.isEmpty(), "with no element the status line is empty: \"" + status + "\"");
		check(card != null && Math.abs(card[0] + card[2] / 2 - size[0] / 2) <= 1 && Math.abs(card[1] + card[3] / 2 - size[1] / 2) <= 1,
				"the card says so at the screen's centre: " + (card == null ? "none" : java.util.Arrays.toString(card)) + " in " + size[0] + "x" + size[1]);
		context.setScreen(() -> null);
		LOGGER.info("HUD editor with no element: the card at {}", java.util.Arrays.toString(card));
	}

	/**
	 * AC-HUD-15 [C] first bullet (REQ-HUD-07, T3.52) at GUI scale 2: box A's outline is rounded (its top edge is drawn, the
	 * pixel just past its corner is not); text element C, hovered, is drawn brighter in its left margin (the white fill
	 * under its content) and, selected, nearer the accent there (the accent fill); C's name sits on a chip 3 GUI px wider than
	 * its text at each side, inside the window.
	 */
	private static void editorLook(ClientGameTestContext context) {
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		HudRect a = rect(context, A);
		HudRect c = rect(context, C);
		java.awt.image.BufferedImage plain = HairlineProbe.read(context.takeScreenshot("t3.52-editor-plain"));
		// A's outline: its top edge's middle, one physical row above the box, against the pixel just past its top-left corner.
		int top = plain.getRGB((int) ((a.x() + a.w() / 2) * scale), (int) (a.y() * scale) - 1) & 0xFFFFFF;
		int corner = plain.getRGB((int) (a.x() * scale) - 1, (int) (a.y() * scale) - 1) & 0xFFFFFF;
		int idle = HudEditorScreen.OUTLINE_IDLE & 0xFFFFFF;
		check(distance(top, idle) <= 12, "box A's outline runs along its top: " + Integer.toHexString(top));
		check(distance(corner, idle) > 12, "and is rounded at its corner: " + Integer.toHexString(corner));
		// A's name chip (above A, on the sky): inside its left padding it is near the panel colour, unlike 2 GUI px left of it.
		int[] aChip = context.computeOnClient(client -> ((HudEditorScreen) client.gui.screen()).labelChipForTests(A));
		int panel = HudEditorScreen.PANEL & 0xFFFFFF;
		int onChip = plain.getRGB((aChip[0] + 1) * scale, (aChip[1] + aChip[3] / 2) * scale) & 0xFFFFFF;
		int besideChip = plain.getRGB((aChip[0] - 2) * scale, (aChip[1] + aChip[3] / 2) * scale) & 0xFFFFFF;
		check(distance(onChip, panel) + 20 < distance(besideChip, panel), "A's name is on a dark chip: " + Integer.toHexString(onChip) + " beside "
				+ Integer.toHexString(besideChip));
		// Box B (disabled): greyed inside its left margin, unlike 3 GUI px left of it; its corner pixel is not grey (rounded).
		HudRect b = rect(context, B);
		int grey = HudEditorScreen.GREY & 0xFFFFFF;
		int inB = plain.getRGB((int) ((b.x() + 1) * scale), (int) ((b.y() + b.h() / 2) * scale)) & 0xFFFFFF;
		int besideB = plain.getRGB((int) ((b.x() - 3) * scale), (int) ((b.y() + b.h() / 2) * scale)) & 0xFFFFFF;
		check(distance(inB, grey) + 20 < distance(besideB, grey), "box B is greyed: " + Integer.toHexString(inB) + " beside " + Integer.toHexString(besideB));
		int bCorner = plain.getRGB((int) (b.x() * scale), (int) (b.y() * scale)) & 0xFFFFFF;
		int pastBCorner = plain.getRGB((int) (b.x() * scale) - 2, (int) (b.y() * scale) - 2) & 0xFFFFFF;
		check(distance(bCorner, pastBCorner) <= 12, "and its grey is rounded with its outline: corner " + Integer.toHexString(bCorner) + " past it "
				+ Integer.toHexString(pastBCorner));
		// The middle of C's left margin (its text starts 2 GUI px in): inside its fill, clear of the rounded corners and the outline.
		int[] probe = {(int) ((c.x() + 1) * scale), (int) ((c.y() + c.h() / 2) * scale)};
		int before = plain.getRGB(probe[0], probe[1]) & 0xFFFFFF;
		cursor(context, c.x() + c.w() / 2, c.y() + c.h() / 2);
		int hovered = HairlineProbe.read(context.takeScreenshot("t3.52-editor-hovered")).getRGB(probe[0], probe[1]) & 0xFFFFFF;
		check(sum(hovered) > sum(before) + 12, "hovered, C is drawn on white at 6 %: " + Integer.toHexString(hovered) + " over " + Integer.toHexString(before));
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		int accent = context.computeOnClient(client -> Theme.current().accent()) & 0xFFFFFF;
		int selected = HairlineProbe.read(context.takeScreenshot("t3.52-editor-selected")).getRGB(probe[0], probe[1]) & 0xFFFFFF;
		check(distance(selected, accent) + 20 < distance(before, accent), "selected, C is drawn on the accent at 15 %: " + Integer.toHexString(selected)
				+ " over " + Integer.toHexString(before));
		int[] chip = context.computeOnClient(client -> ((HudEditorScreen) client.gui.screen()).labelChipForTests(C));
		int[] text = context.computeOnClient(client -> new int[] {client.font.width("Text C"), client.font.lineHeight, client.getWindow().getGuiScaledWidth()});
		check(chip != null && chip[2] == text[0] + 2 * HudEditorScreen.CHIP_PAD && chip[3] == text[1] + 2 && chip[0] >= 2 && chip[0] + chip[2] <= text[2] - 2,
				"C's name on a chip 3 GUI px wider at each side, inside the window: " + (chip == null ? "none" : java.util.Arrays.toString(chip)));
		context.runOnClient(client -> ((HudEditorScreen) client.gui.screen()).selectForTests(null));
		context.waitTick();
		LOGGER.info("hud editor look: A's top {} and corner {}; C {} plain, {} hovered, {} selected; chip {}", Integer.toHexString(top),
				Integer.toHexString(corner), Integer.toHexString(before), Integer.toHexString(hovered), Integer.toHexString(selected),
				java.util.Arrays.toString(chip));
	}

	private static int sum(int rgb) {
		return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
	}

	private static int distance(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
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
		int[] bChip = context.computeOnClient(client -> editor(client).labelChipForTests(B));
		check(bChip != null && bChip[1] >= b.y() + b.h(), "B's name chip lies below its box: " + java.util.Arrays.toString(bChip) + " under " + b);
		check(context.computeOnClient(client -> editor(client).status()).equals(HudEditorScreen.NOTHING_SELECTED), "nothing selected yet");
		java.awt.image.BufferedImage editorShot = HairlineProbe.read(context.takeScreenshot("t2.8-editor"));
		// AC-UI-34 [C] (T3.23): box B's outline is a hairline just outside its box, measured leftwards from its left edge at
		// mid-height (its bottom edge lies under the toolbar card's buttons since T3.53).
		int editorScale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int bx = (int) b.x();
		int by = (int) b.y() + (int) Math.ceil(b.h()) / 2;
		// B is registered disabled and nothing hovers it, so its outline has the disabled text colour.
		String outline = HairlineProbe.check(editorShot, "box B's outline", editorScale, bx * editorScale - 1, by * editorScale, -1, 0, Theme.TEXT_DISABLED);
		check(outline.isEmpty(), outline);
		LOGGER.info("hud editor: {} drawn once each by the editor, none by the HUD", drawn);
	}

	/**
	 * AC-UI-44 [C] (REQ-UI-43, T3.33): the move pointer over an element and while it is dragged, the hand over a button, the arrow
	 * over empty space. The drag is cancelled, and the editor opened again as it was.
	 */
	private static void pointerShapes(ClientGameTestContext context) {
		HudRect start = rect(context, A);
		double[] a = centre(start);
		com.k8bas.skyblockutility.ui.widget.PointerProbe.expect(context, a[0], a[1], com.k8bas.skyblockutility.ui.widget.PointerShape.MOVE, "a HUD element");
		double[] save = centre(context.computeOnClient(client -> bounds(editor(client).saveButton())));
		com.k8bas.skyblockutility.ui.widget.PointerProbe.expect(context, save[0], save[1], com.k8bas.skyblockutility.ui.widget.PointerShape.HAND, "the Save button");
		// Empty space: the first point down the middle column with no element and no button.
		double[] empty = context.computeOnClient(client -> {
			HudEditorScreen editor = editor(client);
			double w = client.getWindow().getGuiScaledWidth();
			double h = client.getWindow().getGuiScaledHeight();
			for (double y = 10; y < h - 40; y += 10) {
				double x = w / 2 + 0.5;
				if (editor.model().at(x, y + 0.5, w, h) == null && !editor.saveButton().contains(x, y) && !editor.cancelButton().contains(x, y)
						&& !editor.resetAllButton().contains(x, y) && !editor.resetSelectedButton().contains(x, y)) {
					return new double[] {x, y};
				}
			}
			throw new AssertionError("FAILED: no empty point in the editor's middle column");
		});
		com.k8bas.skyblockutility.ui.widget.PointerProbe.expect(context, empty[0], empty[1], com.k8bas.skyblockutility.ui.widget.PointerShape.DEFAULT,
				"empty space in the editor");
		cursor(context, a[0], a[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		try {
			context.waitTicks(2);
			com.k8bas.skyblockutility.ui.widget.PointerProbe.check(context, com.k8bas.skyblockutility.ui.widget.PointerShape.MOVE, "a HUD element, grabbed");
			// Dragged onto the Save button, which lies over the canvas: the drag's move pointer, not the button's hand.
			cursor(context, save[0], save[1]);
			com.k8bas.skyblockutility.ui.widget.PointerProbe.check(context, com.k8bas.skyblockutility.ui.widget.PointerShape.MOVE,
					"a HUD element, dragged onto the Save button");
		} finally {
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
		}
		check(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen, "the release over Save did not press it");
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closed the editor");
		open(context, null);
		HudRect after = rect(context, A);
		check(after.x() == start.x() && after.y() == start.y(), "the cancelled drag left element A where it was: " + start + " -> " + after);
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
		Map<String, HudPosition> beforeReset = context.computeOnClient(client -> Map.copyOf(editor(client).model().positions()));
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).resetAllButton()))));
		check(context.computeOnClient(client -> editor(client).model().positions()).equals(beforeReset), "Reset All's first click changes nothing");
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).resetAllButton()))));
		check(context.computeOnClient(client -> editor(client).model().positions()).equals(Map.of(A, A_DEFAULT, B, B_DEFAULT, C, C_DEFAULT)),
				"Reset All's second click: every element at its default");
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).saveButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Save closes it");
		LOGGER.info("hud editor: Cancel restored {} entries; Reset Selected and Reset All put defaults back; a resize kept the selection", stored.size());
	}

	/**
	 * AC-HUD-15 [C] + [A], its other bullets, and EC-HUD-11 (REQ-HUD-08, T3.53), at GUI scale 2 on a test clock with motion
	 * on: the toolbar card lies centred 6 GUI px from the top with its four buttons, and neither it nor the hint panel lies
	 * over the hotbar; the hint shows at 0 ms, fades from 4 s and is gone at 4.3 s, and comes back while the pointer rests on
	 * the card; box B, whose default place reaches under the card, is selected by a press there outside the buttons; A put
	 * under the card is drawn above its background, and a press on a button over A presses the button; Reset All asks for a
	 * second click within 3 s; the card fades to 30 % while A is dragged over it. Cancelled, so nothing is kept.
	 */
	private static void toolbar(ClientGameTestContext context) {
		java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(100_000);
		context.setScreen(() -> new HudEditorScreen(null, null, UiTime.of(clock::get, () -> true)));
		context.waitForScreen(HudEditorScreen.class);
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		int[] card = context.computeOnClient(client -> editor(client).toolbarCardForTests());
		int[] hint = context.computeOnClient(client -> editor(client).hintPanelForTests());
		check(card[1] == 6 && Math.abs(2 * card[0] + card[2] - gui[0]) <= 1, "the card is centred, 6 GUI px from the top: "
				+ java.util.Arrays.toString(card));
		for (Button button : context.computeOnClient(client -> List.of(editor(client).resetSelectedButton(), editor(client).resetAllButton(),
				editor(client).cancelButton(), editor(client).saveButton()))) {
			check(button.x() >= card[0] && button.y() >= card[1] && button.x() + button.width() <= card[0] + card[2]
					&& button.y() + button.height() <= card[1] + card[3], "its button " + button.label() + " lies on it");
		}
		// The hotbar: 182 x 22 GUI px, centred at the bottom.
		int[] hotbar = {gui[0] / 2 - 91, gui[1] - 22, 182, 22};
		check(!overlaps(card, hotbar) && !overlaps(hint, hotbar) && hint[1] >= card[1] + card[3], "neither the card nor the hint panel "
				+ java.util.Arrays.toString(hint) + " lies over the hotbar");
		java.awt.image.BufferedImage toolbarShot = HairlineProbe.read(context.takeScreenshot("t3.53-toolbar"));
		check(hintAlpha(context) == 1F, "the hint shows at 0 ms");
		clock.addAndGet(3_999);
		check(hintAlpha(context) == 1F, "and at 3.999 s");
		clock.addAndGet(151);
		float fading = hintAlpha(context);
		check(fading > 0F && fading < 1F, "at 4.15 s it fades: " + fading);
		clock.addAndGet(150);
		check(hintAlpha(context) == 0F, "at 4.3 s it is gone");
		cursor(context, card[0] + 3, card[1] + 3);
		clock.addAndGet(300);
		check(hintAlpha(context) == 1F, "the pointer on the card brings it back over 300 ms");
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		clock.addAndGet(300);
		check(hintAlpha(context) == 0F, "and it goes when the pointer leaves");

		// EC-HUD-11: B's default place reaches under the card; a press there outside the buttons selects it.
		HudRect b = rect(context, B);
		double[] underCard = {Math.max(b.x(), card[0]) + 2, Math.max(b.y(), card[1]) + 2};
		check(underCard[0] < b.x() + b.w() && underCard[1] < b.y() + b.h() && underCard[0] < card[0] + card[2] && underCard[1] < card[1] + card[3]
				&& !onAButton(context, underCard), "a point of B under the card and off the buttons: " + java.util.Arrays.toString(underCard));
		click(context, underCard);
		check(B.equals(context.computeOnClient(client -> editor(client).model().selected())), "a press there selects B");
		// A press on the card's padding, beside its buttons and clear of every element, keeps the selection.
		double[] padding = {card[0] + 3, card[1] + card[3] - 3};
		check(!onAButton(context, padding) && context.computeOnClient(client -> editor(client).model().at(padding[0], padding[1], gui[0], gui[1])) == null,
				"a point of the card's padding: " + java.util.Arrays.toString(padding));
		click(context, padding);
		check(B.equals(context.computeOnClient(client -> editor(client).model().selected())), "a press on the card's padding keeps B selected");

		// A under the card, across the gap between Reset Selected and Reset All: drawn above the card's background.
		HudRect resetAll = context.computeOnClient(client -> bounds(editor(client).resetAllButton()));
		HudPosition underButtons = new HudPosition(HudAnchor.TOP_LEFT, (int) resetAll.x() - 10, (int) resetAll.y() - 4, 1);
		context.runOnClient(client -> editor(client).model().positions().put(A, underButtons));
		context.waitTicks(2);
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		java.awt.image.BufferedImage shot = HairlineProbe.read(context.takeScreenshot("t3.53-under-card"));
		int gap = shot.getRGB((int) ((resetAll.x() - 2) * scale), (int) ((resetAll.y() + 9) * scale)) & 0xFFFFFF;
		check(distance(gap, 0xC03030) <= 30, "A shows over the card between its buttons: " + Integer.toHexString(gap));
		// A press on Reset All, over A, presses the button: nothing is selected anew and nothing moves.
		Map<String, HudPosition> before = context.computeOnClient(client -> Map.copyOf(editor(client).model().positions()));
		click(context, centre(resetAll));
		check(B.equals(context.computeOnClient(client -> editor(client).model().selected())), "a press on a button over A does not select A");
		check(context.computeOnClient(client -> editor(client).model().positions()).equals(before), "Reset All's first click changes nothing");
		check(context.computeOnClient(client -> editor(client).resetAllButton().label()).equals(HudEditorScreen.CONFIRM_RESET)
				&& context.computeOnClient(client -> editor(client).resetAllButton().style()) == Button.Style.DESTRUCTIVE,
				"and it asks \"" + HudEditorScreen.CONFIRM_RESET + "\" in the destructive style");
		check(context.computeOnClient(client -> client.font.width(HudEditorScreen.CONFIRM_RESET)) <= resetAll.w() - 8, "\"Confirm reset\" fits its button whole");
		clock.addAndGet(2_000);
		click(context, centre(resetAll));
		check(context.computeOnClient(client -> editor(client).model().positions()).equals(Map.of(A, A_DEFAULT, B, B_DEFAULT, C, C_DEFAULT)),
				"a second click within 3 s resets every element");
		check(context.computeOnClient(client -> editor(client).resetAllButton().label()).equals(HudEditorScreen.RESET_ALL), "and it is back");
		context.runOnClient(client -> editor(client).model().positions().put(A, underButtons));
		click(context, centre(resetAll));
		clock.addAndGet(3_000);
		context.waitTicks(2);
		check(context.computeOnClient(client -> editor(client).resetAllButton().label()).equals(HudEditorScreen.RESET_ALL), "3 s later it is back");
		click(context, centre(resetAll));
		check(context.computeOnClient(client -> editor(client).model().positions().get(A)).equals(underButtons), "so one click changes nothing");

		// The card fades to 30 % over 150 ms while A is dragged over it, and back after the drag.
		check(cardAlpha(context) == 1F, "the card before the drag");
		double[] grab = {resetAll.x() - 2, resetAll.y() + 9};
		check(!onAButton(context, grab), "A is grabbed between the buttons");
		cursor(context, grab[0], grab[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		clock.addAndGet(150);
		check(cardAlpha(context) == 1F, "a press on A that has not moved it yet leaves the card as it is");
		cursor(context, grab[0], grab[1] + 4);
		clock.addAndGet(150);
		float dimmed = cardAlpha(context);
		// The hint, which the pointer resting on the card started to bring back, goes once A is dragged across it.
		clock.addAndGet(150);
		check(hintAlpha(context) == 0F, "the hint goes while A is dragged across the card");
		java.awt.image.BufferedImage dimmedShot = HairlineProbe.read(context.takeScreenshot("t3.53-card-dimmed"));
		// The Save button, drawn at the card's 30 %: its fill near its left end is much darker than at full opacity.
		HudRect saveAt = context.computeOnClient(client -> bounds(editor(client).saveButton()));
		int[] savePixel = {(int) ((saveAt.x() + 3) * scale), (int) ((saveAt.y() + 9) * scale)};
		int saveFull = toolbarShot.getRGB(savePixel[0], savePixel[1]) & 0xFFFFFF;
		int saveDimmed = dimmedShot.getRGB(savePixel[0], savePixel[1]) & 0xFFFFFF;
		check(sum(saveDimmed) + 60 < sum(saveFull), "the Save button fades with the card: " + Integer.toHexString(saveDimmed) + " from "
				+ Integer.toHexString(saveFull));
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		clock.addAndGet(150);
		float back = cardAlpha(context);
		check(Math.abs(dimmed - 0.3F) < 1e-4 && back == 1F, "the card at 30 % during the drag, then back: " + dimmed + ", " + back);
		// EC-HUD-11: A, under the card, can be dragged out of it.
		cursor(context, grab[0], grab[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		cursor(context, grab[0], grab[1] + 70);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		HudRect out = rect(context, A);
		check(out.y() >= card[1] + card[3], "A was dragged out from under the card: " + out);
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		LOGGER.info("hud editor toolbar: card {} at the top, hint {} faded from 4 s ({} at 4.15 s), A {} over the card, Reset All in two clicks, "
				+ "card {} under a drag", java.util.Arrays.toString(card), java.util.Arrays.toString(hint), fading, Integer.toHexString(gap), dimmed);
	}

	/** AC-HUD-15 [A]: on a test clock with motion on, the hint is gone 300 ms after a first drag, long before its 4 s. */
	private static void hintGoesAtTheFirstDrag(ClientGameTestContext context) {
		java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(200_000);
		context.setScreen(() -> new HudEditorScreen(null, null, UiTime.of(clock::get, () -> true)));
		context.waitForScreen(HudEditorScreen.class);
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		clock.addAndGet(1_000);
		HudRect a = rect(context, A);
		cursor(context, a.x() + 20, a.y() + 10);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		cursor(context, a.x() + 26, a.y() + 10);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		float atDrag = hintAlpha(context);
		clock.addAndGet(300);
		check(atDrag == 1F && hintAlpha(context) == 0F, "the hint fades from the first drag and is gone 300 ms later: " + atDrag);
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
	}

	/**
	 * AC-HUD-15 [A], REQ-UI-39: with Animations OFF (a test clock with motion off), the hint is gone at once after a first nudge
	 * by an arrow key, and in another editor after a first scale by the wheel; a click that only selects keeps it.
	 */
	private static void hintGoesAtOnceWithAnimationsOff(ClientGameTestContext context) {
		// Early on this clock, before the game's own time, so a nudge read on the game's clock would leave the hint.
		java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000);
		for (boolean byKey : new boolean[] {true, false}) {
			context.setScreen(() -> new HudEditorScreen(null, null, UiTime.of(clock::get, () -> false)));
			context.waitForScreen(HudEditorScreen.class);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(2);
			click(context, centre(rect(context, A)));
			check(hintAlpha(context) == 1F, "a click that only selects keeps the hint");
			if (byKey) {
				context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
			} else {
				cursor(context, centre(rect(context, A))[0], centre(rect(context, A))[1]);
				context.getInput().scroll(1);
			}
			check(hintAlpha(context) == 0F, (byKey ? "a first nudge" : "a first scale") + " hides the hint at once");
			click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
			check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		}
	}

	/**
	 * AC-HUD-15 [A] with REQ-HUD-15 (T3.54 follow-up): on a test clock with motion on, A under the card is selected and its
	 * bottom-right handle, just below the card, dragged up-left over it: the card fades to 30 % as for a move, and is back
	 * 150 ms after the release. Cancelled, so nothing is kept.
	 */
	private static void aHandleDragOverTheCardFadesIt(ClientGameTestContext context) {
		java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(400_000);
		context.setScreen(() -> new HudEditorScreen(null, null, UiTime.of(clock::get, () -> true)));
		context.waitForScreen(HudEditorScreen.class);
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		HudRect resetAll = context.computeOnClient(client -> bounds(editor(client).resetAllButton()));
		context.runOnClient(client -> editor(client).model().positions().put(A, new HudPosition(HudAnchor.TOP_LEFT, (int) resetAll.x() - 10,
				(int) resetAll.y() - 4, 1)));
		context.waitTicks(2);
		double[] gap = {resetAll.x() - 2, resetAll.y() + 9};
		check(!onAButton(context, gap), "A is selected between the buttons");
		click(context, gap);
		check(A.equals(context.computeOnClient(client -> editor(client).model().selected())), "A is selected");
		double[] handle = context.computeOnClient(client -> {
			double[] centres = editor(client).model().handleCentres(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
			return new double[] {centres[6], centres[7]};
		});
		check(!onAButton(context, handle), "its bottom-right handle is off the buttons: " + java.util.Arrays.toString(handle));
		cursorExact(context, handle[0], handle[1]);
		context.waitTicks(2);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(context.computeOnClient(client -> editor(client).model().scaling()), "the press took the handle");
		cursorExact(context, handle[0] - 10, handle[1] - 5);
		context.waitTicks(2);
		clock.addAndGet(150);
		float dimmed = cardAlpha(context);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		clock.addAndGet(150);
		float back = cardAlpha(context);
		check(Math.abs(dimmed - 0.3F) < 1e-4 && back == 1F, "a handle drag over the card fades it to 30 %, then back: " + dimmed + ", " + back);
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
	}

	private static float hintAlpha(ClientGameTestContext context) {
		context.waitTicks(2);
		return context.computeOnClient(client -> editor(client).hintAlphaForTests());
	}

	private static float cardAlpha(ClientGameTestContext context) {
		context.waitTicks(2);
		return context.computeOnClient(client -> editor(client).cardAlphaForTests());
	}

	private static boolean overlaps(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
	}

	private static boolean onAButton(ClientGameTestContext context, double[] at) {
		return context.computeOnClient(client -> {
			HudEditorScreen editor = editor(client);
			return editor.resetSelectedButton().contains(at[0], at[1]) || editor.resetAllButton().contains(at[0], at[1])
					|| editor.cancelButton().contains(at[0], at[1]) || editor.saveButton().contains(at[0], at[1]);
		});
	}

	/**
	 * AC-HUD-16 [A] + [C] (REQ-HUD-15, T3.54): selected, A (60 x 30 at 1.0) shows a handle at each corner, in the accent; the
	 * pointer over one and while dragging it is the crosshair; the bottom-right handle dragged by (+30, +15) gives 1.5 with the
	 * top-left corner where it was, and the top-left one by (+15, +6) gives 0.8 with the bottom-right corner where it was; no
	 * write before Save; screenshots with the handles at GUI scales 2 and 3. Cancelled, so nothing is kept.
	 */
	private static void cornerHandles(ClientGameTestContext context) {
		int saves = context.computeOnClient(client -> ConfigManager.saveRequests());
		for (int guiScale : new int[] {2, 3}) {
			context.runOnClient(client -> client.options.guiScale().set(guiScale));
			context.waitTicks(2);
			open(context, A);
			int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			check(scale == guiScale, "GUI scale " + guiScale + ": " + scale);
			HudRect before = rect(context, A);
			double[] handles = context.computeOnClient(client -> editor(client).model().handleCentres(client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight()));
			check(handles != null && handles[0] == before.x() && handles[1] == before.y() && handles[6] == before.x() + before.w()
					&& handles[7] == before.y() + before.h(), "a handle at each corner of A " + before + ": " + java.util.Arrays.toString(handles));
			java.awt.image.BufferedImage shot = HairlineProbe.read(context.takeScreenshot("t3.54-handles-scale" + guiScale));
			int accent = context.computeOnClient(client -> Theme.current().accent()) & 0xFFFFFF;
			for (int corner = 0; corner < 4; corner++) {
				int at = shot.getRGB((int) Math.floor(handles[2 * corner] * scale), (int) Math.floor(handles[2 * corner + 1] * scale)) & 0xFFFFFF;
				check(distance(at, accent) <= 24, "scale " + guiScale + ": handle " + corner + " is an accent disc: " + Integer.toHexString(at));
			}
			// The bottom-right handle, rightwards from its centre: its dark rim (the darkest pixel 2 to 3.5 GUI px out; at GUI scale 3
			// the disc's edge falls inside a pixel), then the backdrop at 4.
			int rim = 0xFFFFFF;
			int row = (int) Math.floor(handles[7] * scale);
			for (int x = (int) Math.floor((handles[6] + 2) * scale); x <= (int) Math.floor((handles[6] + 3.5) * scale); x++) {
				int pixel = shot.getRGB(x, row) & 0xFFFFFF;
				rim = sum(pixel) < sum(rim) ? pixel : rim;
			}
			int past = shot.getRGB((int) Math.floor((handles[6] + 4) * scale), (int) Math.floor(handles[7] * scale)) & 0xFFFFFF;
			check(distance(past, accent) > 60 && sum(rim) + 30 < sum(past) && distance(rim, accent) > 60, "scale " + guiScale
					+ ": the disc ends in a dark rim within 3 GUI px: rim " + Integer.toHexString(rim) + ", past " + Integer.toHexString(past));
			if (guiScale == 3) {
				click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
				break;
			}
			com.k8bas.skyblockutility.ui.widget.PointerProbe.expect(context, handles[6], handles[7], com.k8bas.skyblockutility.ui.widget.PointerShape.CROSSHAIR,
					"the bottom-right handle");
			cursorExact(context, handles[6], handles[7]);
			context.waitTicks(2);
			context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			for (int i = 1; i <= 10; i++) {
				cursorExact(context, handles[6] + 3.0 * i, handles[7] + 1.5 * i);
				context.waitTick();
			}
			context.waitTick();
			// Far past the largest scale, away from every handle and from A: still the crosshair, as the drag's own pointer.
			cursorExact(context, handles[6] + 200, handles[7] + 100);
			context.waitTicks(2);
			check(context.computeOnClient(client -> editor(client).model().positions().get(A).scale()) == 3.0, "dragged far out: 3.0 at most");
			com.k8bas.skyblockutility.ui.widget.PointerProbe.check(context, com.k8bas.skyblockutility.ui.widget.PointerShape.CROSSHAIR,
					"a handle while it is dragged, away from every handle");
			cursorExact(context, handles[6] + 30, handles[7] + 15);
			context.waitTicks(2);
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			HudRect grown = rect(context, A);
			check(context.computeOnClient(client -> editor(client).model().positions().get(A).scale()) == 1.5 && grown.x() == before.x()
					&& grown.y() == before.y() && grown.w() == 90 && grown.h() == 45, "the bottom-right handle by (+30, +15): 1.5 about the top-left " + grown);
			// Back to 1.0, then the top-left handle by (+15, +6).
			context.runOnClient(client -> editor(client).model().positions().put(A, editor(client).model().opened(A)));
			context.waitTicks(2);
			double[] topLeft = {before.x(), before.y()};
			cursorExact(context, topLeft[0], topLeft[1]);
			context.waitTicks(2);
			context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			for (int i = 1; i <= 3; i++) {
				cursorExact(context, topLeft[0] + 5.0 * i, topLeft[1] + 2.0 * i);
				context.waitTick();
			}
			context.waitTick();
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			HudRect shrunk = rect(context, A);
			check(context.computeOnClient(client -> editor(client).model().positions().get(A).scale()) == 0.8
					&& shrunk.x() + shrunk.w() == before.x() + before.w() && shrunk.y() + shrunk.h() == before.y() + before.h(),
					"the top-left handle by (+15, +6): 0.8 about the bottom-right " + shrunk);
			check(context.computeOnClient(client -> ConfigManager.saveRequests()) == saves, "nothing written before Save");
			click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
			check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		}
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.waitTicks(2);
		LOGGER.info("hud editor handles: four accent discs at GUI scales 2 and 3, the crosshair over and on one, 1.5 and 0.8 about the opposite corner");
	}

	/**
	 * AC-HUD-16 [C] (REQ-HUD-14 as amended by R47, T3.54): C, scaled to 2.0 and placed at (600, 300), is far wider than A, so
	 * when A is dragged with its centre 3 px right of C's, only the centres are within 4 px: A snaps onto C's centre line with
	 * a guide there; with Alt held it follows the mouse.
	 */
	private static void centresSnapToEachOther(ClientGameTestContext context) {
		open(context, null);
		context.runOnClient(client -> editor(client).model().positions().put(C, new HudPosition(HudAnchor.TOP_LEFT, 600, 300, 2)));
		context.waitTicks(2);
		HudRect c = rect(context, C);
		HudRect a = rect(context, A);
		double cCentre = c.x() + c.w() / 2;
		check(c.w() / 2 - a.w() / 2 > 4 + 3, "C is wide enough that A's edges stay away from C's: " + a + ", " + c);
		double startX = Math.floor(a.x() + 20) + 0.5;
		double startY = Math.floor(a.y() + 10) + 0.5;
		cursorExact(context, startX, startY);
		context.waitTicks(2);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		double targetX = startX + (cCentre + 3 - a.w() / 2 - a.x());
		for (int i = 1; i <= 20; i++) {
			cursorExact(context, startX + (targetX - startX) * i / 20, startY);
			context.waitTick();
		}
		context.waitTick();
		HudRect snapped = rect(context, A);
		check(Math.abs(snapped.x() + snapped.w() / 2 - cCentre) <= 0.5 && snapped.y() == a.y(), "A's centre snapped to C's centre line " + cCentre + ": "
				+ snapped);
		check(context.computeOnClient(client -> editor(client).model().guides(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()))
				.contains(new HudSnap.Guide(true, (int) Math.floor(cCentre))), "with its guide");
		context.takeScreenshot("t3.54-snap-centres");
		context.getInput().holdAlt();
		cursorExact(context, targetX + 1, startY);
		context.waitTick();
		cursorExact(context, targetX, startY);
		context.waitTicks(2);
		HudRect free = rect(context, A);
		check(Math.abs(free.x() + free.w() / 2 - (cCentre + 3)) <= 0.5, "with Alt it does not snap: " + free);
		context.getInput().releaseAlt();
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		click(context, centre(context.computeOnClient(client -> bounds(editor(client).cancelButton()))));
		check(!(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen), "Cancel closes it");
		LOGGER.info("hud editor: A's centre snapped to C's centre line at {}, and followed the mouse with Alt held", cCentre);
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
		java.awt.image.BufferedImage snapShot = HairlineProbe.read(context.takeScreenshot("t2.9c-snap-centre"));
		// AC-UI-34 [C] (T3.23): the guide is a hairline, measured across it at GUI y 100, and nothing of it lies left of 480.
		int guideScale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int guideColour = Theme.current().accent();
		String guide = HairlineProbe.check(snapShot, "the snap guide", guideScale, 480 * guideScale, 100 * guideScale, 1, 0, guideColour);
		check(guide.isEmpty() && HairlineProbe.run(snapShot, 480 * guideScale - 1, 100 * guideScale, -1, 0, guideColour) == 0,
				"the snap guide starts at x 480: " + guide);
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

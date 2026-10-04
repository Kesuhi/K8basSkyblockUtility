package com.k8bas.skyblockutility.hud;

import com.google.gson.JsonElement;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.HudConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * T2.7, the HUD framework in a singleplayer world, with a test element that exists only in the gametest
 * source set (REQ-HUD-10):
 * <ul>
 *   <li>AC-HUD-01 [C]: drawn at its stored position (read from the screenshot); F1, its feature off, no
 *       content (EC-HUD-09) and the inventory hide it; chat does not (R12);</li>
 *   <li>AC-HUD-03 [C] + [A]: placed at the bottom right at GUI scale 2 on 1920×1080, it stays fully visible
 *       at the bottom right at scales 3 and 4 and on 854×480; one that would be off the small window is
 *       drawn inside it; back at scale 2 and 1920×1080 the stored entries are as before.</li>
 * </ul>
 */
public class HudGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String OTHER = "k8bas_gametest_far";

	@Override
	public void runTest(ClientGameTestContext context) {
		TestHudElement element = new TestHudElement();
		HudConfig hudBefore = context.computeOnClient(client -> ConfigManager.general().hud);
		context.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				HudRegistry.register(element);
				HudPositions.LIVE.set(TestHudElement.ID, new HudPosition(HudAnchor.TOP_LEFT, 100, 60, 2));
			});
			context.waitTicks(3);
			shownAndHidden(context, element);
			survivesScaleAndWindow(context, element);
		} finally {
			context.runOnClient(client -> {
				HudRegistry.unregister(TestHudElement.ID);
				HudRegistry.unregister(OTHER);
				ConfigManager.general().hud = hudBefore;
				if (hudBefore != null) {
					hudBefore.positions.remove(TestHudElement.ID);
					hudBefore.positions.remove(OTHER);
				}
			});
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** AC-HUD-01 [C], EC-HUD-09, R12. */
	private static void shownAndHidden(ClientGameTestContext context, TestHudElement element) {
		HudRect drawn = drawn(context, TestHudElement.ID);
		check(drawn != null && drawn.x() == 100 && drawn.y() == 60 && drawn.w() == 80 && drawn.h() == 40,
				"drawn at its stored position, twice its size: " + drawn);
		Path shown = context.takeScreenshot("t2.7-hud-shown");
		check(pixel(context, shown, 140, 80) == 0xFF00FF, "the screenshot has it there: " + Integer.toHexString(pixel(context, shown, 140, 80)));

		setHudHidden(context, true);
		check(drawn(context, TestHudElement.ID) == null, "F1 hides it");
		Path hidden = context.takeScreenshot("t2.7-hud-f1");
		check(pixel(context, hidden, 140, 80) != 0xFF00FF, "and the screenshot has no box");
		setHudHidden(context, false);

		element.enabled = false;
		check(drawn(context, TestHudElement.ID) == null, "its feature off hides it");
		element.enabled = true;
		HudContent content = element.content;
		element.content = null;
		check(drawn(context, TestHudElement.ID) == null, "no content, nothing drawn (no empty box)");
		element.content = content;

		context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
		check(drawn(context, TestHudElement.ID) == null, "the inventory hides it");
		context.takeScreenshot("t2.7-hud-inventory");
		context.runOnClient(client -> client.gui.setScreen(null));
		check(drawn(context, TestHudElement.ID) != null, "closing it shows it again");
		context.runOnClient(client -> client.gui.setScreen(new ChatScreen("", false)));
		check(drawn(context, TestHudElement.ID) != null, "chat keeps it (R12)");
		context.runOnClient(client -> client.gui.setScreen(null));
		LOGGER.info("hud: drawn at {}, hidden by F1, its feature off, no content and the inventory; shown over chat", drawn);
	}

	/** AC-HUD-03 [C] + [A], REQ-HUD-04. */
	private static void survivesScaleAndWindow(ClientGameTestContext context, TestHudElement element) {
		// A fractional scaled size (51.25×28.75), so the far edges are rounded inward, never past the window.
		element.content = new TestHudElement.Box(41, 23, 0xFFFF00FF);
		context.runOnClient(client -> {
			HudRegistry.register(new FarElement());
			HudPositions.LIVE.set(TestHudElement.ID, new HudPosition(HudAnchor.BOTTOM_RIGHT, -4, -4, 1.25));
			// Inside 960×540 (scale 2 on 1920×1080), off the 480×270 and 427×240 GUIs.
			HudPositions.LIVE.set(OTHER, new HudPosition(HudAnchor.TOP_LEFT, 400, 300, 1));
		});
		JsonElement storedBox = context.computeOnClient(client -> ConfigManager.general().hud.positions.get(TestHudElement.ID).deepCopy());
		JsonElement storedFar = context.computeOnClient(client -> ConfigManager.general().hud.positions.get(OTHER).deepCopy());
		// On 854×480 the game allows at most GUI scale 2 (427×240).
		int[][] cases = {{2, 1920, 1080}, {3, 1920, 1080}, {4, 1920, 1080}, {2, 854, 480}};
		for (int[] c : cases) {
			context.getInput().resizeWindow(c[1], c[2]);
			context.runOnClient(client -> client.options.guiScale().set(c[0]));
			context.waitTicks(3);
			int[] gui = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
			HudRect box = drawn(context, TestHudElement.ID);
			String where = "scale " + c[0] + " on " + c[1] + "×" + c[2] + " (" + gui[0] + "×" + gui[1] + ")";
			// Flush with its offset to within the pixel rounding, and fully inside.
			check(box != null && Math.abs(box.x() + box.w() - (gui[0] - 4)) < 1 && Math.abs(box.y() + box.h() - (gui[1] - 4)) < 1
					&& box.x() + box.w() <= gui[0] && box.y() + box.h() <= gui[1] && box.x() == Math.rint(box.x()) && box.y() == Math.rint(box.y()),
					"bottom right on whole pixels, fully visible at " + where + ": " + box);
			HudRect far = drawn(context, OTHER);
			check(far != null && far.x() >= 0 && far.y() >= 0 && far.x() + far.w() <= gui[0] && far.y() + far.h() <= gui[1],
					"the far one is drawn inside the window at " + where + ": " + far);
			context.takeScreenshot("t2.7-hud-" + c[1] + "x" + c[2] + "-gui" + gui[0] + "x" + gui[1]);
		}
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.waitTicks(3);
		check(context.computeOnClient(client -> ConfigManager.general().hud.positions.get(TestHudElement.ID)).equals(storedBox),
				"the box's stored entry is as before");
		check(context.computeOnClient(client -> ConfigManager.general().hud.positions.get(OTHER)).equals(storedFar),
				"and the far one's: the clamp never rewrote it");
		HudRect far = drawn(context, OTHER);
		check(far != null && far.x() == 400 && far.y() == 300, "back on the large window it is where it was stored: " + far);
		LOGGER.info("hud: bottom right at GUI scales 2-4 on 1920×1080 and at 2 on 854×480; the far element clamped inside and back at (400, 300); "
				+ "entries unchanged");
	}

	/** Another element, for the clamp. */
	private static final class FarElement implements HudElement {
		@Override
		public String id() {
			return OTHER;
		}

		@Override
		public String displayName() {
			return "Far box";
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public HudContent content() {
			return new TestHudElement.Box(50, 25, 0xFF00FFFF);
		}

		@Override
		public HudContent preview() {
			return content();
		}

		@Override
		public HudPosition defaultPosition() {
			return new HudPosition(HudAnchor.TOP_LEFT, 4, 40, 1);
		}
	}

	/** Where the element was drawn in a frame after the change. */
	private static HudRect drawn(ClientGameTestContext context, String id) {
		context.runOnClient(client -> HudRenderer.forgetDrawn());
		context.waitTicks(2);
		return context.computeOnClient(client -> HudRenderer.lastDrawn(id));
	}

	/** 26.2 replaces Options.hideGui with Hud.toggle(). */
	private static void setHudHidden(ClientGameTestContext context, boolean hidden) {
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != hidden) {
				client.gui.hud.toggle();
			}
		});
	}

	private static int pixel(ClientGameTestContext context, Path screenshot, int guiX, int guiY) {
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()});
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			return image.getRGB((int) Math.floor((guiX + 0.5) * image.getWidth() / size[0]), (int) Math.floor((guiY + 0.5) * image.getHeight() / size[1]))
					& 0xFFFFFF;
		} catch (IOException e) {
			throw new AssertionError("cannot read " + screenshot, e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}

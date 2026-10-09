package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.widget.ListTooltipTestScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * T2.3d, AC-UI-19 [C] in a real client:
 * <ul>
 *   <li>5 notices posted within a second over a screen: 4 shown, the oldest retired; drawn over the
 *       screen (read from the screenshot), and a click under them still reaches the screen;</li>
 *   <li>a notice goes after its configured duration (3 s, not a bound of the range) plus the slide time, to within a tick;</li>
 *   <li>in a world with no screen they are drawn over the HUD, and the inventory key still opens the
 *       inventory; F1 hides them, as it hides vanilla toasts.</li>
 * </ul>
 */
public class NoticeGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 960);
		try {
			context.runOnClient(client -> client.options.guiScale().set(2));
			overAScreen(context);
			lifetime(context);
			inAWorld(context);
		} finally {
			context.runOnClient(client -> {
				Notices.clear();
				ConfigManager.general().noticePosition = null;
				ConfigManager.general().noticeSeconds = null;
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void overAScreen(ClientGameTestContext context) {
		context.setScreen(ListTooltipTestScreen::new);
		context.waitForScreen(ListTooltipTestScreen.class);
		context.getInput().setCursorPos(1100, 900);
		context.runOnClient(client -> {
			ConfigManager.general().noticePosition = NoticePosition.TOP_LEFT;
			ConfigManager.general().noticeSeconds = 10;
		});
		for (int i = 1; i <= 5; i++) {
			int n = i;
			context.runOnClient(client -> Notices.post(new Notice("Notice " + n, List.of("Posted " + n + " of 5", "Within one second"))));
			waitMillis(context, 150);
		}
		List<String> titles = context.computeOnClient(client -> Notices.visible().stream().map(entry -> entry.notice().title()).toList());
		check(titles.equals(List.of("Notice 2", "Notice 3", "Notice 4", "Notice 5")), "4 shown, the oldest retired: " + titles);
		waitMillis(context, NoticeQueue.SLIDE_TIME_MS);
		Path shot = context.takeScreenshot("t2.3d-notices-over-a-screen");
		NoticeLayout.Placed first = context.computeOnClient(client -> Notices.placed(client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight()).get(0));
		int inside = pixel(shot, window(context, first.x() + first.width() - 4, first.y() + 2));
		check(inside == (Theme.HEADER & 0xFFFFFF), "the notice is drawn over the screen: " + Integer.toHexString(inside) + " at " + first);

		// The list's first row lies under the first notice; a click there still reaches the screen.
		int[] row = window(context, first.x() + 40, 8 + 5);
		check(row[1] < window(context, 0, first.y() + first.height())[1], "the click point is under the notice");
		context.getInput().setCursorPos(row[0], row[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		int clicked = context.computeOnClient(client -> ((ListTooltipTestScreen) client.gui.screen()).clicked());
		check(clicked == 0, "a click under a notice reaches the screen: row " + clicked);
		context.runOnClient(client -> Notices.clear());
		context.setScreen(() -> null);
		LOGGER.info("notices over a screen: {} shown, drawn at {}, a click under them picked row {}", titles, first, clicked);
	}

	/** Gone after its duration plus the slide time, to within a client tick (50 ms) and a frame. */
	private static void lifetime(ClientGameTestContext context) {
		context.runOnClient(client -> ConfigManager.general().noticeSeconds = 3);
		long posted = context.computeOnClient(client -> {
			Notices.post(new Notice("Three seconds", List.of()));
			return Util.getMillis();
		});
		long expected = 3000 + NoticeQueue.SLIDE_TIME_MS;
		long gone = -1;
		for (int tick = 0; tick < 1000 && gone < 0; tick++) {
			context.waitTick();
			gone = context.computeOnClient(client -> Notices.visible().isEmpty() ? Util.getMillis() : -1L);
		}
		long lifetime = gone - posted;
		check(gone > 0 && lifetime >= expected && lifetime <= expected + 100, "gone after " + lifetime + " ms, expected " + expected);
		LOGGER.info("notice lifetime: {} ms for 3 s plus {} ms of slide", lifetime, NoticeQueue.SLIDE_TIME_MS);
	}

	private static void inAWorld(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				ConfigManager.general().noticePosition = NoticePosition.TOP_RIGHT;
				ConfigManager.general().noticeSeconds = 10;
				Notices.post(new Notice("In a world", List.of("Over the HUD")));
			});
			waitMillis(context, NoticeQueue.SLIDE_TIME_MS);
			Path shot = context.takeScreenshot("t2.3d-notice-in-a-world");
			NoticeLayout.Placed box = context.computeOnClient(client -> Notices.placed(client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight()).get(0));
			int inside = pixel(shot, window(context, box.x() + box.width() - 4, box.y() + 2));
			check(inside == (Theme.HEADER & 0xFFFFFF), "drawn over the HUD: " + Integer.toHexString(inside));

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(3);
			check(context.computeOnClient(client -> client.gui.screen()) instanceof InventoryScreen, "the inventory key still opens the inventory");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null, 40);

			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTicks(2);
			Path hidden = context.takeScreenshot("t2.3d-notice-f1");
			int underF1 = pixel(hidden, window(context, box.x() + box.width() - 4, box.y() + 2));
			check(underF1 != (Theme.HEADER & 0xFFFFFF), "F1 hides it: " + Integer.toHexString(underF1));
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTicks(2);
			LOGGER.info("notice in a world: drawn over the HUD, the inventory key opened the inventory, F1 hid it");
		}
	}

	private static void waitMillis(ClientGameTestContext context, long millis) {
		long until = context.computeOnClient(client -> Util.getMillis()) + millis;
		while (context.computeOnClient(client -> Util.getMillis()) < until) {
			context.waitTick();
		}
	}

	private static int[] window(ClientGameTestContext context, int guiX, int guiY) {
		return context.computeOnClient(client -> {
			var window = Minecraft.getInstance().getWindow();
			return new int[] {(int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
					(int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
		});
	}

	private static int pixel(Path screenshot, int[] point) {
		try {
			return javax.imageio.ImageIO.read(screenshot.toFile()).getRGB(point[0], point[1]) & 0xFFFFFF;
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

package com.k8bas.skyblockutility.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.ButtonEntry;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AC-PORT-05 and AC-PORT-07 on 26.2: the settings screens, both pickers and the module toggle keys
 * work in a singleplayer world, and nothing from com.k8bas throws.
 */
public class SettingsScreensGameTest implements FabricClientGameTest {
	private static final String SETTINGS_TITLE = "K8bas Skyblock Utility";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			for (String command : List.of("ksu", "kskyblockutility")) {
				context.runOnClient(client -> client.player.connection.sendCommand(command));
				waitForTitle(context, SETTINGS_TITLE);
				closeWithEscape(context, null);
			}

			withKeyBound(context, SettingsKeybind.OPEN_SETTINGS_KEY, GLFW.GLFW_KEY_KP_7, () -> {
				context.getInput().pressKey(SettingsKeybind.OPEN_SETTINGS_KEY);
				waitForTitle(context, SETTINGS_TITLE);
			});
			closeWithEscape(context, null);

			openPickerAndReturn(context, "Mob Database");
			openPickerAndReturn(context, "NPC Database");

			for (Module module : ModuleManager.modules()) {
				toggleKeyFlipsAndSaves(context, module);
			}

			String log = latestLog();
			check(!log.contains("at com.k8bas") && !log.contains("at knot//com.k8bas"), "no exception from com.k8bas in latest.log");
		}
	}

	/** Opens a picker from the settings screen; its save button (the picker's way back) returns to
	 *  the settings screen, and Esc then closes that. */
	private static void openPickerAndReturn(ClientGameTestContext context, String buttonField) {
		context.runOnClient(client -> client.player.connection.sendCommand("ksu"));
		waitForTitle(context, SETTINGS_TITLE);
		context.runOnClient(client -> {
			ClothConfigScreen settings = (ClothConfigScreen) client.gui.screen();
			ButtonEntry button = findButton(settings, buttonField);
			check(button != null, "button '" + buttonField + "' on the settings screen");
			button.press();
		});
		waitForTitle(context, buttonField);
		context.takeScreenshot("t1.2-picker-" + buttonField.toLowerCase().replace(' ', '-'));
		context.clickScreenButton("text.cloth-config.save_and_done");
		waitForTitle(context, SETTINGS_TITLE);
		closeWithEscape(context, null);
	}

	private static ButtonEntry findButton(ClothConfigScreen screen, String fieldName) {
		for (List<AbstractConfigEntry<?>> entries : screen.getCategorizedEntries().values()) {
			for (AbstractConfigEntry<?> entry : entries) {
				if (entry instanceof ButtonEntry button && fieldName.equals(button.getFieldName().getString())) {
					return button;
				}
			}
		}
		return null;
	}

	/** AC-PORT-07: the toggle key flips the module, and the config file has the new value within 2 s. */
	private static void toggleKeyFlipsAndSaves(ClientGameTestContext context, Module module) {
		KeyMapping key = toggleKey(module);
		boolean before = context.computeOnClient(client -> module.isEnabled());
		withKeyBound(context, key, GLFW.GLFW_KEY_KP_8, () -> {
			context.getInput().pressKey(key);
			context.waitFor(client -> module.isEnabled() != before, 20);
			int ticks = context.waitFor(client -> savedEnabled(module.id()) == !before, 40);
			check(ticks <= 40, module.id() + " saved within 2 s");
			context.getInput().pressKey(key);
			context.waitFor(client -> module.isEnabled() == before, 20);
		});
	}

	private static KeyMapping toggleKey(Module module) {
		return switch (module.id()) {
			case "mob_highlighter" -> com.k8bas.skyblockutility.module.mobhighlighter.ModKeybinds.TOGGLE_KEY;
			case "npc_search" -> com.k8bas.skyblockutility.module.npcsearch.ModKeybinds.TOGGLE_KEY;
			default -> throw new AssertionError("no toggle key known for " + module.id());
		};
	}

	private static Boolean savedEnabled(String moduleId) {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json");
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return root.getAsJsonObject("modules").getAsJsonObject(moduleId).get("enabled").getAsBoolean();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Binds the key for the duration of the body. Numpad keys: 26.2 binds O to its Friends List. */
	private static void withKeyBound(ClientGameTestContext context, KeyMapping key, int glfwKey, Runnable body) {
		context.runOnClient(client -> {
			key.setKey(InputConstants.Type.KEYSYM.getOrCreate(glfwKey));
			KeyMapping.resetMapping();
		});
		try {
			body.run();
		} finally {
			context.runOnClient(client -> {
				key.setKey(InputConstants.UNKNOWN);
				KeyMapping.resetMapping();
			});
		}
	}

	private static void waitForTitle(ClientGameTestContext context, String title) {
		context.waitFor(client -> {
			Screen screen = client.gui.screen();
			return screen != null && title.equals(screen.getTitle().getString());
		});
	}

	/** Esc closes the current screen; the result is the screen with the given title, or none. */
	private static void closeWithEscape(ClientGameTestContext context, String expectedTitle) {
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		if (expectedTitle == null) {
			context.waitFor(client -> client.gui.screen() == null);
		} else {
			waitForTitle(context, expectedTitle);
		}
	}

	private static String latestLog() {
		try {
			return Files.readString(FabricLoader.getInstance().getGameDir().resolve(Path.of("logs", "latest.log")));
		} catch (IOException e) {
			throw new AssertionError("cannot read latest.log", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}

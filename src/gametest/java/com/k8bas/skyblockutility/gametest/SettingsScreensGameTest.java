package com.k8bas.skyblockutility.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import com.k8bas.skyblockutility.ui.screen.ConfigScreensForTests;
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * In a singleplayer world: the settings screen's entry points (REQ-UI-10: `/ksu`, `/kskyblockutility`,
 * `/ksu <text>` typed in chat, a reserved word, the "Open settings" key, and closing back to the opener;
 * AC-UI-09 except Mod Menu, which is not in the test client, and `/ksu hud`'s editor, T2.8b; EC-UI-03),
 * the module toggle keys (AC-PORT-07) and the key names (AC-PORT-11); nothing from com.k8bas throws.
 * The database pickers are DatabasePickerGameTest's.
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
				check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "/" + command + " opens the settings screen");
				closeWithEscape(context, null);
			}
			searchFromChat(context);
			reservedWordIsNoSearch(context);
			everyPrefixRunsLocally(context);

			withKeyBound(context, SettingsKeybind.OPEN_SETTINGS_KEY, GLFW.GLFW_KEY_KP_7, () -> {
				context.getInput().pressKey(SettingsKeybind.OPEN_SETTINGS_KEY);
				waitForTitle(context, SETTINGS_TITLE);
			});
			check(context.computeOnClient(client -> client.gui.screen()) instanceof ConfigScreen, "the Open settings key opens the settings screen");
			closeWithEscape(context, null);
			closingReturnsToTheOpener(context);

			for (Module module : ModuleManager.modules()) {
				toggleKeyFlipsAndSaves(context, module);
			}

			keybindNamesAreTranslated(context);

			String log = latestLog();
			check(!log.contains("at com.k8bas") && !log.contains("at knot//com.k8bas"), "no exception from com.k8bas in latest.log");
		}
	}

	/** EC-UI-03, AC-UI-09: `/ksu glow` typed in chat and sent with Enter opens the screen on the next tick with
	 *  "glow" in the search box, and the Enter does not close it. */
	private static void searchFromChat(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitFor(client -> client.gui.screen() instanceof ChatScreen);
		context.waitTicks(2);
		context.getInput().typeChars("/ksu glow");
		context.waitTicks(2);
		context.takeScreenshot("t2.5b-chat-typed");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		waitForTitle(context, SETTINGS_TITLE);
		context.waitTicks(10);
		String query = context.computeOnClient(client -> ConfigScreensForTests.query(client.gui.screen()));
		check("glow".equals(query), "/ksu glow from chat opens the settings with \"glow\" searched, and it stays open: " + query);
		context.takeScreenshot("t2.5b-ksu-glow");
		closeWithEscape(context, null);
	}

	/** A reserved word runs its subcommand (`/ksu hud`, AC-UI-09); one without a subcommand yet (`sbxp`, T6.8) opens no search. */
	private static void reservedWordIsNoSearch(ClientGameTestContext context) {
		// Typed in chat and sent with Enter, so the closing chat would close an editor opened at once (EC-UI-03).
		context.getInput().pressKey(options -> options.keyChat);
		context.waitFor(client -> client.gui.screen() instanceof ChatScreen);
		context.waitTicks(2);
		context.getInput().typeChars("/ksu hud");
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		waitForTitle(context, HudEditorScreen.TITLE);
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.gui.screen()) instanceof HudEditorScreen, "/ksu hud from chat opens the HUD editor, and it stays open");
		closeWithEscape(context, null);
		context.runOnClient(client -> client.player.connection.sendCommand("ksu sbxp"));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gui.screen()) == null, "/ksu sbxp is not a search for \"sbxp\"");
		context.runOnClient(client -> client.player.connection.sendCommand("ksu hudson"));
		waitForTitle(context, SETTINGS_TITLE);
		check("hudson".equals(context.computeOnClient(client -> ConfigScreensForTests.query(client.gui.screen()))), "a word that only begins like one is");
		closeWithEscape(context, null);
	}

	/** REQ-UI-25: every prefix of /ksu and /kskyblockutility has a command here; one without would go to the server. */
	private static void everyPrefixRunsLocally(ClientGameTestContext context) {
		List<String> missing = context.computeOnClient(client -> {
			List<String> found = new java.util.ArrayList<>();
			var root = ClientCommands.getActiveDispatcher().getRoot();
			for (String name : List.of("ksu", "kskyblockutility")) {
				collectWithoutCommand(root.getChild(name), "/" + name, found);
			}
			return found;
		});
		check(missing.isEmpty(), "every /ksu prefix runs on the client: " + missing);
		context.runOnClient(client -> client.player.connection.sendCommand("ksu debug"));
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.gui.screen()) == null, "/ksu debug opens nothing and says how to go on");
	}

	private static void collectWithoutCommand(CommandNode<?> node, String path, List<String> out) {
		if (node.getCommand() == null) {
			out.add(path);
		}
		for (CommandNode<?> child : node.getChildren()) {
			collectWithoutCommand(child, path + " " + child.getName(), out);
		}
	}

	/** REQ-UI-10: closing the settings returns to the screen they were opened from (as from Mod Menu's list). */
	private static void closingReturnsToTheOpener(ClientGameTestContext context) {
		Screen[] opener = new Screen[1];
		context.runOnClient(client -> {
			opener[0] = new KeyBindsScreen(null, client.options);
			client.gui.setScreen(opener[0]);
			client.gui.setScreen(new ConfigScreen(opener[0]));
		});
		waitForTitle(context, SETTINGS_TITLE);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen()) == opener[0], "Esc returns to the opener");
		context.setScreen(() -> null);
	}

	/** AC-PORT-11: the keybind category and every key name resolve to a translation (26.x looks up
	 *  key.category.*), shown on the Controls screen. The key names themselves stay unchanged, so
	 *  bindings stored in options.txt keep working (AC-CFG-12). */
	private static void keybindNamesAreTranslated(ClientGameTestContext context) {
		context.runOnClient(client -> {
			String category = K8basSkyblockUtilityClient.KEY_CATEGORY.label().getString();
			check("K8bas Skyblock Utility".equals(category), "keybind category translated: " + category);
			for (KeyMapping key : List.of(SettingsKeybind.OPEN_SETTINGS_KEY,
					com.k8bas.skyblockutility.module.mobhighlighter.ModKeybinds.TOGGLE_KEY,
					com.k8bas.skyblockutility.module.npcsearch.ModKeybinds.TOGGLE_KEY)) {
				check(key.getName().startsWith("key.k8bas_skyblock_utility."), "unchanged key name " + key.getName());
				String label = Component.translatable(key.getName()).getString();
				check(!label.equals(key.getName()), "key name translated: " + key.getName());
			}
		});
		context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
		context.waitForScreen(KeyBindsScreen.class);
		context.getInput().setCursorPos(400, 200);
		context.getInput().scroll(-500);
		context.waitTicks(5);
		context.takeScreenshot("t1.13-key-binds");
		context.setScreen(() -> null);
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

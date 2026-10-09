package com.k8bas.skyblockutility;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.data.BundledData;
import com.k8bas.skyblockutility.debug.ContainerDump;
import com.k8bas.skyblockutility.debug.DebugCommand;
import com.k8bas.skyblockutility.hook.HookStatus;
import com.k8bas.skyblockutility.highlight.GlowHandler;
import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterModule;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchModule;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import com.k8bas.skyblockutility.settings.SettingsCommand;
import com.k8bas.skyblockutility.settings.SettingsKeybind;
import com.k8bas.skyblockutility.skyblock.GameStateReaders;
import com.k8bas.skyblockutility.hud.HudRenderer;
import com.k8bas.skyblockutility.hud.ShippedHud;
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen;
import com.k8bas.skyblockutility.ui.widget.SavesOnClose;
import net.minecraft.client.Minecraft;
import com.k8bas.skyblockutility.ui.notice.NoticeHooks;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import com.k8bas.skyblockutility.update.Updates;
import com.k8bas.skyblockutility.util.ChatUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8basSkyblockUtilityClient implements ClientModInitializer {
	public static final String MOD_ID = "k8bas_skyblock_utility";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared by every keybind this mod registers — a Category identifier can only be registered once. */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "general"));

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		// Queued like the load notices, so a failure before joining a world is still shown.
		ConfigManager.setSaveFailureNotice(ConfigManager::queueNotice);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// The game removes an open screen only after this event: a screen that saves on close saves first.
			if (client.gui.screen() instanceof SavesOnClose screen) {
				screen.saveBeforeShutdown();
			}
			ConfigManager.flush();
		});
		// Backup notices from loading are shown once the player is in a world (REQ-CFG-06).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && ConfigManager.hasNotices()) {
				ConfigManager.drainNotices().forEach(ChatUtils::chat);
			}
		});
		IslandTracker.register();
		// Read-only tab and sidebar readers; they read only while an enabled feature subscribes.
		GameStateReaders.register();
		// Rule matching runs once per tick on the client thread; rendering only reads the results.
		ClientTickEvents.END_CLIENT_TICK.register(HighlightManager::tick);
		GlowHandler.register();
		// The world marker toolkit; features add their marker providers as they register.
		WorldMarkers.register();

		ModuleManager.register(new MobHighlighterModule());
		ModuleManager.register(new NpcSearchModule());
		// Future modules get registered here, one line each.

		SettingsKeybind.register();
		SettingsCommand.register();
		DebugCommand.register();
		ContainerDump.register();
		Updates.register();
		NoticeHooks.register();
		ShippedHud.register();
		HudRenderer.register();
		HudRenderer.setEditorOpen(() -> Minecraft.getInstance().gui.screen() instanceof HudEditorScreen);

		// The optional cosmetic hooks (AD-9): their target classes are loaded now, so each card knows whether its
		// hook went in; one warning per hook that did not.
		HookStatus.settle();
		// Bundled tables load off the client thread, once every feature has registered its tables, so the startup
		// log lists what they use; a table registered later still loads.
		BundledData.start();
		LOGGER.info("K8bas Skyblock Utility initialized with {} module(s)", ModuleManager.modules().size());
	}
}

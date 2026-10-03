package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws notices over the HUD and every screen without a mixin (REQ-UI-21, AD-9): right after any open
 * screen has drawn (title screen and our modals included), and as a HUD element while no screen is
 * open. In both cases vanilla toasts and the F3 overlay, drawn later by the game, stay on top. They hide
 * with F1, as vanilla toasts do. Neither hook is part of any screen's input.
 */
public final class NoticeHooks {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(K8basSkyblockUtilityClient.MOD_ID, "notices");

	private NoticeHooks() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
				ScreenEvents.afterExtract(screen).register((drawn, graphics, mouseX, mouseY, tickProgress) -> draw(graphics)));
		// After the player list, inside the HUD pass (not the deferred subtitle pass), so in a world notices
		// sit under vanilla toasts and F3 exactly as they do over a screen.
		HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST, HUD_ID, (graphics, deltaTracker) -> {
			// With a screen open the screen hook draws them, above the screen.
			if (Minecraft.getInstance().gui.screen() == null) {
				draw(graphics);
			}
		});
	}

	private static void draw(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.hud.isHidden()) {
			return;
		}
		graphics.nextStratum();
		Notices.draw(graphics, client.font, graphics.guiWidth(), graphics.guiHeight());
	}
}

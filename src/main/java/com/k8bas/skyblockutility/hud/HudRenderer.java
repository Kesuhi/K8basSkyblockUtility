package com.k8bas.skyblockutility.hud;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Draws the registered HUD elements in the game's HUD pass, under the chat (REQ-HUD-01, REQ-HUD-02): each
 * enabled element that has content, at its stored position and scale, shifted inside the window when it
 * would stick out (REQ-HUD-04). Nothing is drawn with F1, under any screen but chat, or while the HUD
 * editor is open. It only reads: the elements' content and the parsed positions (REQ-HUD-11).
 */
public final class HudRenderer {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(K8basSkyblockUtilityClient.MOD_ID, "hud_elements");
	/** Whether the HUD editor is open (it draws the elements itself); set by the editor. */
	private static volatile BooleanSupplier editorOpen = () -> false;
	/** The rectangles drawn since the last {@link #forgetDrawn}, by element id, in GUI pixels (for tests; with F1 the HUD pass does not run at all). */
	private static final Map<String, HudRect> LAST_DRAWN = new HashMap<>();

	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, (graphics, deltaTracker) -> draw(graphics));
	}

	public static void setEditorOpen(BooleanSupplier open) {
		editorOpen = open;
	}

	static HudRect lastDrawn(String id) {
		return LAST_DRAWN.get(id);
	}

	static void forgetDrawn() {
		LAST_DRAWN.clear();
	}

	private static void draw(GuiGraphicsExtractor graphics) {
		LAST_DRAWN.clear();
		Minecraft client = Minecraft.getInstance();
		Screen screen = client.gui.screen();
		HudVisibility.Screen open = screen == null ? HudVisibility.Screen.NONE
				: screen instanceof ChatScreen ? HudVisibility.Screen.CHAT : HudVisibility.Screen.OTHER;
		if (!HudVisibility.drawn(client.gui.hud.isHidden(), open, editorOpen.getAsBoolean())) {
			return;
		}
		for (HudElement element : HudRegistry.elements()) {
			if (!element.enabled()) {
				continue;
			}
			HudContent content = element.content();
			if (content != null) {
				LAST_DRAWN.put(element.id(), drawAt(graphics, client.font, content, HudPositions.LIVE.get(element.id(), element.defaultPosition())));
			}
		}
	}

	/**
	 * Draws {@code content} at {@code position} on this frame's GUI, clamped inside it, and returns where
	 * it went. Also for the HUD editor, which draws every element itself.
	 */
	public static HudRect drawAt(GuiGraphicsExtractor graphics, Font font, HudContent content, HudPosition position) {
		double scale = position.scale();
		HudRect placed = position.place(graphics.guiWidth(), graphics.guiHeight(), content.width(font) * scale, content.height(font) * scale);
		HudRect rect = HudRect.onPixels(placed, graphics.guiWidth(), graphics.guiHeight());
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate((float) rect.x(), (float) rect.y());
		pose.scale((float) scale, (float) scale);
		content.draw(graphics, font);
		pose.popMatrix();
		return rect;
	}
}

package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Rects and rounded rects from plain fills (REQ-UI-17, D-8; no direct GL, REQ-PORT-12). Corners are cut in
 * screen pixels, so at GUI scale 3 or 4 they are as round as at scale 1. With "Smooth corners" on (REQ-UI-18,
 * T2.9d, R32) each corner is instead a tinted quad of an anti-aliased mask texture and the straight parts stay
 * fills; a shape a mask cannot draw keeps the fill-based corners. Written from SPEC REQ-UI-17 and REQ-UI-18; no
 * AlpakaAddons code (REQ-UI-24).
 */
public final class Shapes {
	/** Corner radii in GUI pixels: cards, tabs, and small controls. */
	public static final int RADIUS_CARD = 6;
	public static final int RADIUS_TAB = 5;
	public static final int RADIUS_CONTROL = 4;

	private Shapes() {
	}

	public static void rect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int argb) {
		if (w > 0 && h > 0) {
			graphics.fill(x, y, x + w, y + h, argb);
		}
	}

	/** A 1 px square outline just inside (x, y, w, h). */
	public static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int argb) {
		graphics.fill(x, y, x + w, y + 1, argb);
		graphics.fill(x, y + h - 1, x + w, y + h, argb);
		graphics.fill(x, y + 1, x + 1, y + h - 1, argb);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int argb) {
		draw(graphics, x, y, w, h, radius, 0, argb);
	}

	/** A rounded outline, {@code thickness} GUI pixels wide, inside the w×h box. */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int thickness, int argb) {
		if (thickness > 0) {
			draw(graphics, x, y, w, h, radius, thickness, argb);
		}
	}

	/**
	 * A rounded shape as one GUI element, or two back to back with identical bounds (REQ-UI-27, {@link ShapePlan}):
	 * its pieces in screen pixels under a 1/scale pose, so corners are as round at GUI scale 4 as at 1. With
	 * "Smooth corners" the corners are tinted quads of an anti-aliased mask; without a mask the fill-based corners.
	 */
	private static void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int thickness, int argb) {
		int scale = guiScale();
		Matrix3x2fStack outer = graphics.pose();
		ScreenRectangle scissor = graphics.scissorStack.peek();
		int[] clip = scissor == null ? null : new int[] {scissor.left(), scissor.top(), scissor.right(), scissor.bottom()};
		ShapePlan.Result plan = ShapePlan.of(outer, x, y, w, h, radius, thickness, scale, smooth(), clip);
		Identifier mask = null;
		if (plan.maskRadius() >= 0) {
			mask = CornerTextures.mask(scale, plan.maskRadius(), plan.maskThickness());
			if (mask == null) {
				plan = ShapePlan.of(outer, x, y, w, h, radius, thickness, scale, false, clip);
			}
		}
		if (plan.elements().isEmpty()) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(outer).translate(x, y).scale(1F / scale, 1F / scale);
		for (ShapePlan.Element element : plan.elements()) {
			ScreenRectangle bounds = new ScreenRectangle(element.left(), element.top(), element.right() - element.left(),
					element.bottom() - element.top());
			graphics.guiRenderState.addGuiElement(element.textured()
					? new ShapeRenderState(RenderPipelines.GUI_TEXTURED, texture(mask), pose, element.quads(), element.uvs(), argb, scissor, bounds)
					: new ShapeRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), pose, element.quads(), null, argb, scissor, bounds));
		}
	}

	/** The mask texture as the game's blit sets it up. */
	private static TextureSetup texture(Identifier mask) {
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(mask);
		return TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
	}

	/** "Smooth corners" (General › Interface, R32), read on each draw so a change applies at once. */
	private static boolean smooth() {
		return ConfigManager.general().smoothCorners();
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}
}

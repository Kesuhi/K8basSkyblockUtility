package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
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
 * fills; a shape a mask cannot draw keeps the fill-based corners. Discs, pills and one-cap pills always take their
 * round ends from a disc mask (REQ-UI-30, T3.18). A texture baked at its size times the GUI scale (the colour wheel,
 * T3.19) is drawn 1:1 as one element by {@link #image}. Every mask and baked texture is sampled linear and clamped to its
 * edge (REQ-UI-31). Written from SPEC REQ-UI-17, REQ-UI-18, REQ-UI-30 and REQ-UI-31; no AlpakaAddons code (REQ-UI-24).
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
		int scale = guiScale();
		draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 0, scale), 0, Corners.ALL, argb);
	}

	/**
	 * A horizontal bar of a track (REQ-UI-28, T3.16): from GUI x0 to x1, both snapped to the nearest screen pixel, h
	 * GUI pixels high at y, with radius h/2 at the given {@link Corners} corners and square ends elsewhere.
	 */
	public static void bar(GuiGraphicsExtractor graphics, float x0, float x1, int y, int h, int corners, int argb) {
		int scale = guiScale();
		int[] span = ShapePlan.barSpan(x0, x1, scale);
		if (span != null) {
			draw(graphics, (float) span[0] / scale, y, span[1], h * scale, ShapePlan.barRadius(h, scale), 0, corners, argb);
		}
	}

	/** A rounded outline, {@code thickness} GUI pixels wide, inside the w×h box. */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int thickness, int argb) {
		if (thickness > 0) {
			int scale = guiScale();
			draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, thickness, scale), thickness * scale, Corners.ALL, argb);
		}
	}

	/**
	 * A rounded shape, w×h with radius and thickness in screen pixels at GUI (x, y), as one GUI element, or two back to
	 * back with identical bounds (REQ-UI-27, {@link ShapePlan}): its pieces in screen pixels under a 1/scale pose, so
	 * corners are as round at GUI scale 4 as at 1. With "Smooth corners", and always for discs and pills, the round parts
	 * are tinted quads of an anti-aliased mask; without a mask the fill spans. Only the {@link Corners} {@code corners}
	 * are round.
	 */
	private static void draw(GuiGraphicsExtractor graphics, float x, float y, int w, int h, int radius, int thickness, int corners, int argb) {
		int scale = guiScale();
		Matrix3x2fStack outer = graphics.pose();
		ScreenRectangle scissor = graphics.scissorStack.peek();
		boolean smooth = ShapePlan.alwaysSmooth(w, h, radius, thickness, corners) || smooth();
		ShapePlan.Drawn<Identifier> drawn = ShapePlan.resolve(outer, x, y, w, h, radius, thickness, corners, scale, smooth, clip(scissor),
				key -> CornerTextures.mask(scale, key));
		ShapePlan.Result plan = drawn.plan();
		if (plan.elements().isEmpty()) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(outer).translate(x, y).scale(1F / scale, 1F / scale);
		for (ShapePlan.Element element : plan.elements()) {
			graphics.guiRenderState.addGuiElement(element.textured()
					? new ShapeRenderState(RenderPipelines.GUI_TEXTURED, texture(drawn.mask()), pose, element.quads(), element.uvs(), argb, scissor,
							bounds(element))
					: new ShapeRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), pose, element.quads(), null, argb, scissor,
							bounds(element)));
		}
	}

	/**
	 * A texture of sw×sh texels at GUI (x, y), one texel per screen pixel, tinted {@code argb} and sampled linear, clamped
	 * to its edge (REQ-UI-31): the colour wheel, baked at its size times the GUI scale (T3.19). One element with the image's
	 * bounds rounded outward (REQ-UI-27), so what is drawn after it and overlaps it lands above it.
	 */
	public static void image(GuiGraphicsExtractor graphics, Identifier texture, float x, float y, int sw, int sh, int argb) {
		int scale = guiScale();
		Matrix3x2fStack outer = graphics.pose();
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ShapePlan.Element element = ShapePlan.image(outer, x, y, sw, sh, scale, clip(scissor));
		if (element != null) {
			Matrix3x2f pose = new Matrix3x2f(outer).translate(x, y).scale(1F / scale, 1F / scale);
			graphics.guiRenderState.addGuiElement(new ShapeRenderState(RenderPipelines.GUI_TEXTURED, texture(texture), pose, element.quads(),
					element.uvs(), argb, scissor, bounds(element)));
		}
	}

	/** The scissor as {left, top, right, bottom} for {@link ShapePlan}, or null for none. */
	private static int[] clip(ScreenRectangle scissor) {
		return scissor == null ? null : new int[] {scissor.left(), scissor.top(), scissor.right(), scissor.bottom()};
	}

	private static ScreenRectangle bounds(ShapePlan.Element element) {
		return new ScreenRectangle(element.left(), element.top(), element.right() - element.left(), element.bottom() - element.top());
	}

	private static TextureSetup texture(Identifier id) {
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
		return maskTexture(texture.getTextureView(), RenderSystem.getSamplerCache());
	}

	/**
	 * A mask's or baked texture's setup (REQ-UI-31): its view with the sampler cache's shared linear, clamp-to-edge sampler,
	 * not the texture's own nearest, repeating one (no texture subclass), so it resamples smoothly at a fractional position or
	 * under the open animation's scale and never reads the opposite edge. Drawn 1:1, it reads each texel exactly.
	 */
	static TextureSetup maskTexture(GpuTextureView view, SamplerCache samplers) {
		return TextureSetup.singleTexture(view, samplers.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR,
				FilterMode.LINEAR, false));
	}

	/**
	 * "Smooth corners" (General › Interface, REQ-UI-29), read on each draw so a change applies at once; asked only for
	 * the corners of rounded rectangles and outlines ({@link ShapePlan#alwaysSmooth}).
	 */
	private static boolean smooth() {
		return ConfigManager.general().smoothCorners();
	}

	/** The GUI scale shapes are drawn at: screen pixels per GUI pixel, at least 1. */
	public static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}
}

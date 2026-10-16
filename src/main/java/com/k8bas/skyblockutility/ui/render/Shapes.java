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
 * T3.19, and the icons, T3.20) is drawn 1:1 as one element by {@link #image}. Every mask and baked texture is sampled linear and clamped to its
 * edge (REQ-UI-31). Outlines and thin lines are hairlines in screen pixels, ceil(guiScale / 2) thick (REQ-UI-33, T3.22).
 * Written from SPEC REQ-UI-17, REQ-UI-18, REQ-UI-30, REQ-UI-31 and REQ-UI-33; no AlpakaAddons code (REQ-UI-24).
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
			graphics.fill(x, y, x + w, y + h, UiFade.apply(argb));
		}
	}

	/** A plain fill from (x0, y0) to (x1, y1) as the game draws it, at the kit's fade (REQ-UI-38). */
	public static void plain(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int argb) {
		graphics.fill(x0, y0, x1, y1, UiFade.apply(argb));
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int argb) {
		int scale = guiScale();
		draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 0, scale), 0, Corners.ALL, argb);
	}

	/**
	 * As {@link #roundedRect(GuiGraphicsExtractor, int, int, int, int, int, int)} at a GUI position snapped to the nearest
	 * screen pixel (REQ-UI-34): a moving knob drawn on the physical-pixel grid rather than in whole GUI px.
	 */
	public static void roundedRect(GuiGraphicsExtractor graphics, float x, float y, int w, int h, int radius, int argb) {
		int scale = guiScale();
		draw(graphics, snap(x, scale), snap(y, scale), w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 0, scale), 0, Corners.ALL, argb);
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
	 * A hairline's thickness in screen pixels at this GUI scale (REQ-UI-33): ceil(guiScale / 2), so 1 at scales 1 and 2, 2 at
	 * 3 and 4, 3 at 5 and 6. Outlines and thin lines are this thick; a focus ring is not ({@link #focusRingPx}).
	 */
	public static int hairlinePx(int scale) {
		return (scale + 1) / 2;
	}

	/** A focus ring's thickness in screen pixels (REQ-UI-33): 1 GUI px, so keyboard focus stays easy to see. */
	public static int focusRingPx(int scale) {
		return scale;
	}

	/** A GUI position snapped to the nearest screen pixel at this GUI scale, a multiple of 1/scale. */
	public static float snap(float v, int scale) {
		return (float) Math.round(v * scale) / scale;
	}

	/** A rounded outline one hairline thick (REQ-UI-33) inside the w×h box: the outline of a control. */
	public static void roundedHairline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int argb) {
		int scale = guiScale();
		draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 1, scale), hairlinePx(scale), Corners.ALL, argb);
	}

	/** A square outline one hairline thick (REQ-UI-33) inside the w×h box. */
	public static void hairlineRect(GuiGraphicsExtractor graphics, float x, float y, int w, int h, int argb) {
		int scale = guiScale();
		draw(graphics, snap(x, scale), snap(y, scale), w * scale, h * scale, 0, hairlinePx(scale), Corners.ALL, argb);
	}

	/** A horizontal hairline (REQ-UI-33) w GUI px long, its top edge at GUI y, snapped to the screen pixel grid. */
	public static void hairlineH(GuiGraphicsExtractor graphics, float x, float y, int w, int argb) {
		int scale = guiScale();
		draw(graphics, snap(x, scale), snap(y, scale), w * scale, hairlinePx(scale), 0, 0, Corners.ALL, argb);
	}

	/** A vertical hairline (REQ-UI-33) h GUI px long, its left edge at GUI x, snapped to the screen pixel grid: the text caret. */
	public static void hairlineV(GuiGraphicsExtractor graphics, float x, float y, int h, int argb) {
		int scale = guiScale();
		draw(graphics, snap(x, scale), snap(y, scale), hairlinePx(scale), h * scale, 0, 0, Corners.ALL, argb);
	}

	/**
	 * A hairline wrapped flush around the w×h content box at GUI (x, y), whose corners have radius {@code radius} GUI px
	 * (REQ-UI-33): the line lies just outside the box, so no background shows between them at any GUI scale. Radius 0 keeps
	 * it square.
	 */
	public static void hairlineAround(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int argb) {
		int scale = guiScale();
		int t = hairlinePx(scale);
		float off = (float) t / scale;
		draw(graphics, x - off, y - off, w * scale + 2 * t, h * scale + 2 * t, radius == 0 ? 0 : radius * scale + t, t, Corners.ALL, argb);
	}

	/** A focus ring (REQ-UI-33): a rounded outline {@link #focusRingPx} thick, 1 GUI px at every GUI scale. */
	public static void focusRing(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int argb) {
		int scale = guiScale();
		draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 1, scale), focusRingPx(scale), Corners.ALL, argb);
	}

	/** One hairline in GUI pixels at this GUI scale: how far a fill sits inside a hairline outline to meet it flush. */
	public static float hairlineGui(int scale) {
		return (float) hairlinePx(scale) / scale;
	}

	/** A plain fill from GUI (x0, y0) to (x1, y1), its edges snapped to the screen pixel grid, so it can meet a hairline flush. */
	public static void fill(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int argb) {
		int scale = guiScale();
		int left = Math.round(x0 * scale);
		int top = Math.round(y0 * scale);
		int right = Math.round(x1 * scale);
		int bottom = Math.round(y1 * scale);
		if (right > left && bottom > top) {
			draw(graphics, (float) left / scale, (float) top / scale, right - left, bottom - top, 0, 0, Corners.ALL, argb);
		}
	}

	/**
	 * A vertical pill from GUI (x0, y0) to (x1, y1), its edges snapped to the screen pixel grid and its ends round (REQ-UI-30):
	 * a scrollbar's thumb or track (REQ-UI-36), smooth whatever the setting, as one GUI element.
	 */
	public static void pillV(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int argb) {
		int scale = guiScale();
		int left = Math.round(x0 * scale);
		int top = Math.round(y0 * scale);
		int w = Math.round(x1 * scale) - left;
		int h = Math.round(y1 * scale) - top;
		if (w > 0 && h > 0) {
			draw(graphics, (float) left / scale, (float) top / scale, w, h, (Math.min(w, h) + 1) / 2, 0, Corners.ALL, argb);
		}
	}

	/**
	 * A rounded shape, w×h with radius and thickness in screen pixels at GUI (x, y), as one GUI element, or two back to
	 * back with identical bounds (REQ-UI-27, {@link ShapePlan}): its pieces in screen pixels under a 1/scale pose, so
	 * corners are as round at GUI scale 4 as at 1. With "Smooth corners", and always for discs and pills, the round parts
	 * are tinted quads of an anti-aliased mask; without a mask the fill spans. Only the {@link Corners} {@code corners}
	 * are round.
	 */
	private static void draw(GuiGraphicsExtractor graphics, float x, float y, int w, int h, int radius, int thickness, int corners, int colour) {
		int argb = UiFade.apply(colour);
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
	 * to its edge (REQ-UI-31): the colour wheel and the icons, baked at their size times the GUI scale. One element with the image's
	 * bounds rounded outward (REQ-UI-27), so what is drawn after it and overlaps it lands above it.
	 */
	public static void image(GuiGraphicsExtractor graphics, Identifier texture, float x, float y, int sw, int sh, int colour) {
		int argb = UiFade.apply(colour);
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

	/**
	 * An icon of the kit (REQ-UI-32, T3.20), size × size GUI pixels at GUI (x, y), in the colour {@code argb}: rasterised at its
	 * size times the GUI scale and drawn 1:1. Where no texture can be had (REQ-UI-47), its half-covered pixels as fill spans,
	 * still one element.
	 */
	public static void icon(GuiGraphicsExtractor graphics, IconShapes.Icon icon, float x, float y, int size, int argb) {
		if (size <= 0) {
			return;
		}
		int scale = guiScale();
		int px = size * scale;
		Identifier texture = IconTextures.texture(scale, icon, px);
		if (texture != null) {
			image(graphics, texture, x, y, px, px, argb);
			return;
		}
		Matrix3x2fStack outer = graphics.pose();
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ShapePlan.Element box = ShapePlan.image(outer, x, y, px, px, scale, clip(scissor));
		int[] spans = IconTextures.spans(scale, icon, px);
		if (box != null && spans.length > 0) {
			Matrix3x2f pose = new Matrix3x2f(outer).translate(x, y).scale(1F / scale, 1F / scale);
			graphics.guiRenderState.addGuiElement(new ShapeRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), pose, spans, null, UiFade.apply(argb),
					scissor, bounds(box)));
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

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
import org.joml.Vector2f;

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
	/**
	 * The one corner scale (REQ-UI-40), in GUI pixels: the settings panel, cards, and controls (tabs, fields, buttons, the
	 * dropdown list, tooltips); a pill takes half its height.
	 */
	public static final int RADIUS_PANEL = 8;
	public static final int RADIUS_CARD = 6;
	public static final int RADIUS_CONTROL = 4;
	/**
	 * A soft shadow (REQ-UI-41): how far it blurs out and how far down it lies, in GUI px, and its black's alpha. Its reach,
	 * the blur and the offset together, stays within the 4 GUI px a panel keeps from the window (REQ-UI-02, EC-UI-19), so it
	 * is never cut at the window's edge.
	 */
	public static final int SHADOW_BLUR = 3;
	public static final int SHADOW_OFFSET = 1;
	public static final int SHADOW_ALPHA = 0x50;

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
		roundedRect(graphics, x, y, w, h, radius, Corners.ALL, argb);
	}

	/** A rectangle rounded only at the {@link Corners} {@code corners}: a strip that follows a panel's corners (REQ-UI-40). */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int corners, int argb) {
		int scale = guiScale();
		draw(graphics, x, y, w * scale, h * scale, ShapePlan.pixelRadius(w, h, radius, 0, scale), 0, corners, argb);
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
	 * A disc {@code diameter} GUI px across centred on ({@code cx}, {@code cy}), its size rounded to whole screen pixels and its
	 * box on the physical-pixel grid (REQ-UI-34): a knob that grows between whole GUI sizes (REQ-UI-46). One mask quad, as every
	 * disc (REQ-UI-30).
	 */
	public static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float diameter, int argb) {
		disc(graphics, cx, cy, diameter, 0, argb);
	}

	/** As above, drawn from the disc mask {@code maskPx} screen px across when that is above 0 (REQ-UI-47, {@link ShapePlan}). */
	public static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float diameter, int maskPx, int argb) {
		int scale = guiScale();
		int d = Math.max(1, Math.round(diameter * scale));
		float left = Math.round(cx * scale - d / 2F) / (float) scale;
		float top = Math.round(cy * scale - d / 2F) / (float) scale;
		draw(graphics, left, top, d, d, (d + 1) / 2, 0, Corners.ALL, maskPx, argb);
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
		pillV(graphics, x0, y0, x1, y1, 0, argb);
	}

	/** As above, drawn from the disc mask {@code maskPx} screen px across when that is above 0 (REQ-UI-47, {@link ShapePlan}). */
	public static void pillV(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int maskPx, int argb) {
		int scale = guiScale();
		int left = Math.round(x0 * scale);
		int top = Math.round(y0 * scale);
		int w = Math.round(x1 * scale) - left;
		int h = Math.round(y1 * scale) - top;
		if (w > 0 && h > 0) {
			draw(graphics, (float) left / scale, (float) top / scale, w, h, (Math.min(w, h) + 1) / 2, 0, Corners.ALL, maskPx, argb);
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
		draw(graphics, x, y, w, h, radius, thickness, corners, 0, colour);
	}

	/** As above; a disc or pill is drawn from the disc mask {@code discMask} screen px across when that is above 0. */
	private static void draw(GuiGraphicsExtractor graphics, float x, float y, int w, int h, int radius, int thickness, int corners, int discMask,
			int colour) {
		int argb = UiFade.apply(colour);
		int scale = guiScale();
		Matrix3x2fStack outer = graphics.pose();
		if (cornerSquares != null && radius > 0 && !ShapePlan.alwaysSmooth(w, h, radius, thickness, corners)) {
			recordCorners(outer, x, y, w, h, radius, corners, scale);
		}
		ScreenRectangle scissor = graphics.scissorStack.peek();
		boolean smooth = ShapePlan.alwaysSmooth(w, h, radius, thickness, corners) || smooth();
		ShapePlan.Drawn<Identifier> drawn = ShapePlan.resolve(outer, x, y, w, h, radius, thickness, corners, scale, smooth, discMask, clip(scissor),
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
	 * For gametests (AC-UI-30): while recording, the screen-pixel squares (x, y, side) of the round corners of rectangles and
	 * outlines, the only places "Smooth corners" changes; discs and pills, always smooth, are not recorded.
	 */
	private static java.util.List<int[]> cornerSquares;

	/** For gametests (AC-UI-30): starts recording the corner squares drawn from now, or stops it. */
	public static void recordCornersForTests(boolean on) {
		cornerSquares = on ? new java.util.ArrayList<>() : null;
	}

	/** For gametests (AC-UI-30): the corner squares recorded so far. */
	public static java.util.List<int[]> recordedCornersForTests() {
		return cornerSquares == null ? java.util.List.of() : java.util.List.copyOf(cornerSquares);
	}

	private static void recordCorners(Matrix3x2fStack pose, float x, float y, int w, int h, int radius, int corners, int scale) {
		Vector2f at = pose.transformPosition(x, y, new Vector2f());
		int left = Math.round(at.x * scale);
		int top = Math.round(at.y * scale);
		if ((corners & Corners.TOP_LEFT) != 0) {
			cornerSquares.add(new int[] {left, top, radius});
		}
		if ((corners & Corners.TOP_RIGHT) != 0) {
			cornerSquares.add(new int[] {left + w - radius, top, radius});
		}
		if ((corners & Corners.BOTTOM_LEFT) != 0) {
			cornerSquares.add(new int[] {left, top + h - radius, radius});
		}
		if ((corners & Corners.BOTTOM_RIGHT) != 0) {
			cornerSquares.add(new int[] {left + w - radius, top + h - radius, radius});
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
	 * A soft drop shadow (REQ-UI-41, T3.31) under a w×h element at GUI (x, y) whose corners have {@code radius} GUI px: the
	 * baked mask's 9-slice, tinted black at {@link #SHADOW_ALPHA}, {@link #SHADOW_OFFSET} down and blurring
	 * {@link #SHADOW_BLUR} out, drawn before the element so it lies under it. Left out when its mask cannot be had
	 * (REQ-UI-47).
	 */
	public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius) {
		int scale = guiScale();
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2)) * scale;
		int blur = SHADOW_BLUR * scale;
		Identifier mask = CornerTextures.mask(scale, MaskKey.shadow(r, blur));
		if (mask == null) {
			return;
		}
		Matrix3x2fStack outer = graphics.pose();
		ScreenRectangle scissor = graphics.scissorStack.peek();
		float top = y + SHADOW_OFFSET;
		// Cut to the scissor, or to the window (REQ-UI-41: it stays inside it).
		int[] cut = scissor != null ? clip(scissor) : new int[] {0, 0, graphics.guiWidth(), graphics.guiHeight()};
		ShapePlan.Element element = ShapePlan.shadow(outer, x, top, w * scale, h * scale, r + blur, blur, scale, cut);
		if (element != null) {
			Matrix3x2f pose = new Matrix3x2f(outer).translate(x, top).scale(1F / scale, 1F / scale);
			graphics.guiRenderState.addGuiElement(new ShapeRenderState(RenderPipelines.GUI_TEXTURED, texture(mask), pose, element.quads(),
					element.uvs(), UiFade.apply(SHADOW_ALPHA << 24), scissor, bounds(element)));
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

package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

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

	/** The smooth shape's pieces, reused: drawing runs on the render thread only. */
	private static final int[] PIECES = new int[CornerPieces.MAX_PIECES * CornerPieces.STRIDE];

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
		if (smooth() && drawSmooth(graphics, x, y, scale, w * scale, h * scale, radius * scale, 0, argb)) {
			return;
		}
		drawSpans(graphics, x, y, scale, Corners.fill(w * scale, h * scale, radius * scale), argb);
	}

	/** A rounded outline, {@code thickness} GUI pixels wide, inside the w×h box. */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int thickness, int argb) {
		int scale = guiScale();
		if (thickness > 0 && smooth() && drawSmooth(graphics, x, y, scale, w * scale, h * scale, radius * scale, thickness * scale, argb)) {
			return;
		}
		drawSpans(graphics, x, y, scale, Corners.ring(w * scale, h * scale, radius * scale, thickness * scale), argb);
	}

	/** Spans in screen pixels, drawn at (x, y) in GUI pixels. */
	private static void drawSpans(GuiGraphicsExtractor graphics, int x, int y, int scale, List<Corners.Span> spans, int argb) {
		if (spans.isEmpty()) {
			return;
		}
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(1F / scale, 1F / scale);
		for (Corners.Span span : spans) {
			graphics.fill(span.x0(), span.y0(), span.x1(), span.y1(), argb);
		}
		pose.popMatrix();
	}

	/**
	 * The smooth shape, w×h with radius r and outline thickness t (0 = filled) in screen pixels, drawn at (x, y)
	 * in GUI pixels: one texel per screen pixel, tinted with the colour, so a faded colour fades the edge too.
	 *
	 * @return false if a mask cannot draw it, so the caller draws the fill-based shape
	 */
	private static boolean drawSmooth(GuiGraphicsExtractor graphics, int x, int y, int scale, int w, int h, int r, int t, int argb) {
		if (w <= 0 || h <= 0) {
			return true;
		}
		int radius = CornerPieces.clampRadius(w, h, r);
		int n = t == 0 ? CornerPieces.fill(w, h, radius, PIECES) : CornerPieces.ring(w, h, radius, t, PIECES);
		if (n < 0) {
			return false;
		}
		Identifier mask = CornerTextures.mask(scale, radius, t);
		if (mask == null) {
			return false;
		}
		int size = 2 * radius;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(1F / scale, 1F / scale);
		for (int i = 0; i < n; i++) {
			int o = i * CornerPieces.STRIDE;
			int x0 = PIECES[o + 1];
			int y0 = PIECES[o + 2];
			if (PIECES[o] == CornerPieces.KIND_MASK) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, mask, x0, y0, PIECES[o + 5], PIECES[o + 6], radius, radius, size, size, argb);
			} else {
				graphics.fill(x0, y0, PIECES[o + 3], PIECES[o + 4], argb);
			}
		}
		pose.popMatrix();
		return true;
	}

	/** "Smooth corners" (General › Interface, R32), read on each draw so a change applies at once. */
	private static boolean smooth() {
		return ConfigManager.general().smoothCorners();
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}
}

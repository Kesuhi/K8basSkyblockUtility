package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Rects and rounded rects from plain fills (REQ-UI-17, D-8; no texture, no direct GL, REQ-PORT-12).
 * Corners are cut in screen pixels, so at GUI scale 3 or 4 they are as round as at scale 1. Written
 * from SPEC REQ-UI-17; no AlpakaAddons code (REQ-UI-24).
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
		drawSpans(graphics, x, y, scale, Corners.fill(w * scale, h * scale, radius * scale), argb);
	}

	/** A rounded outline, {@code thickness} GUI pixels wide, inside the w×h box. */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int thickness, int argb) {
		int scale = guiScale();
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

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}
}

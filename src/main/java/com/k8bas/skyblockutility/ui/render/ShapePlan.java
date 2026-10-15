package com.k8bas.skyblockutility.ui.render;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.util.List;

/**
 * A rounded shape as the GUI elements it is submitted as (REQ-UI-27, T3.15): one element, or two back to back with
 * identical bounds (a smooth shape's mask corners, then its straight parts). The game orders the elements of a layer
 * by their bounds only, so every piece of a shape shares the bounds of the whole shape: the shape's GUI rectangle
 * under the outer pose, rounded outward. A shape drawn over another one then always overlaps its bounds and lands
 * above it, also at fractional positions and scales; piece by piece, a span starting half-way into a GUI pixel lost
 * up to a pixel of its bounds and slid under (the 1.2.1 slider-knob line). Pure, so it is tested without a game.
 */
public final class ShapePlan {
	/**
	 * One element: {@code quads} holds x0, y0, x1, y1 per quad in screen pixels relative to the shape's corner (x1
	 * and y1 exclusive), {@code uvs} the texture's u0, v0, u1, v1 per quad for a textured element, else null. The
	 * bounds are in GUI pixels, right and bottom exclusive.
	 */
	public record Element(boolean textured, int[] quads, float[] uvs, int left, int top, int right, int bottom) {
	}

	/** The elements, and the mask they sample (radius and thickness in screen pixels; -1 when none). */
	public record Result(List<Element> elements, int maskRadius, int maskThickness) {
		static final Result EMPTY = new Result(List.of(), -1, -1);
	}

	private ShapePlan() {
	}

	/**
	 * A w×h shape at (x, y) in GUI pixels with corners of {@code radius} GUI pixels, filled ({@code thickness} 0) or
	 * outlined, at the given GUI scale. With {@code smooth} the corners come from a mask where one can draw them.
	 * The bounds are clipped to {@code scissor} ({left, top, right, bottom} in GUI pixels, or null for none), as the
	 * game clips its own elements; a shape wholly outside it gives no element.
	 */
	public static Result of(Matrix3x2fc outer, int x, int y, int w, int h, int radius, int thickness, int scale, boolean smooth, int[] scissor) {
		if (w <= 0 || h <= 0) {
			return Result.EMPTY;
		}
		int[] box = clip(bounds(outer, x, y, x + w, y + h), scissor);
		if (box == null) {
			return Result.EMPTY;
		}
		int sw = w * scale;
		int sh = h * scale;
		int sr = radius * scale;
		int st = thickness * scale;
		if (smooth) {
			Result masked = masked(box, sw, sh, sr, st);
			if (masked != null) {
				return masked;
			}
		}
		List<Corners.Span> spans = st == 0 ? Corners.fill(sw, sh, sr) : Corners.ring(sw, sh, sr, st);
		if (spans.isEmpty()) {
			return Result.EMPTY;
		}
		int[] quads = new int[spans.size() * 4];
		for (int i = 0; i < spans.size(); i++) {
			Corners.Span span = spans.get(i);
			quads[i * 4] = span.x0();
			quads[i * 4 + 1] = span.y0();
			quads[i * 4 + 2] = span.x1();
			quads[i * 4 + 3] = span.y1();
		}
		return new Result(List.of(element(false, quads, null, box)), -1, -1);
	}

	/** The smooth shape (REQ-UI-18), or null where a mask cannot draw it (the fill-based corners do). */
	private static Result masked(int[] box, int w, int h, int r, int t) {
		int radius = CornerPieces.clampRadius(w, h, r);
		int[] pieces = new int[CornerPieces.MAX_PIECES * CornerPieces.STRIDE];
		int n = t == 0 ? CornerPieces.fill(w, h, radius, pieces) : CornerPieces.ring(w, h, radius, t, pieces);
		if (n < 0) {
			return null;
		}
		int masks = 0;
		for (int i = 0; i < n; i++) {
			if (pieces[i * CornerPieces.STRIDE] == CornerPieces.KIND_MASK) {
				masks++;
			}
		}
		int[] maskQuads = new int[masks * 4];
		float[] uvs = new float[masks * 4];
		int[] fillQuads = new int[(n - masks) * 4];
		float size = 2F * radius;
		int m = 0;
		int f = 0;
		for (int i = 0; i < n; i++) {
			int o = i * CornerPieces.STRIDE;
			int[] target = pieces[o] == CornerPieces.KIND_MASK ? maskQuads : fillQuads;
			int at = pieces[o] == CornerPieces.KIND_MASK ? m++ * 4 : f++ * 4;
			System.arraycopy(pieces, o + 1, target, at, 4);
			if (pieces[o] == CornerPieces.KIND_MASK) {
				uvs[at] = pieces[o + 5] / size;
				uvs[at + 1] = pieces[o + 6] / size;
				uvs[at + 2] = (pieces[o + 5] + radius) / size;
				uvs[at + 3] = (pieces[o + 6] + radius) / size;
			}
		}
		Element corners = element(true, maskQuads, uvs, box);
		List<Element> elements = fillQuads.length == 0 ? List.of(corners) : List.of(corners, element(false, fillQuads, null, box));
		return new Result(elements, radius, t);
	}

	private static Element element(boolean textured, int[] quads, float[] uvs, int[] box) {
		return new Element(textured, quads, uvs, box[0], box[1], box[2], box[3]);
	}

	/** {@code box} cut to {@code scissor} (null for none), or null when nothing of it is left. */
	static int[] clip(int[] box, int[] scissor) {
		if (box == null || scissor == null) {
			return box;
		}
		int left = Math.max(box[0], scissor[0]);
		int top = Math.max(box[1], scissor[1]);
		int right = Math.min(box[2], scissor[2]);
		int bottom = Math.min(box[3], scissor[3]);
		return right > left && bottom > top ? new int[] {left, top, right, bottom} : null;
	}

	/**
	 * The rectangle (x0, y0)-(x1, y1) under {@code pose} rounded outward: floor of the minimum and ceiling of the
	 * maximum of its four transformed corners, as {left, top, right, bottom}; null when it covers no pixel. Not the
	 * game's own rounding, which takes the ceiling of the size and so can come out a pixel short.
	 */
	public static int[] bounds(Matrix3x2fc pose, float x0, float y0, float x1, float y1) {
		Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
		Vector2f b = pose.transformPosition(x1, y0, new Vector2f());
		Vector2f c = pose.transformPosition(x0, y1, new Vector2f());
		Vector2f d = pose.transformPosition(x1, y1, new Vector2f());
		int left = (int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
		int top = (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
		int right = (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
		int bottom = (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));
		return right > left && bottom > top ? new int[] {left, top, right, bottom} : null;
	}
}

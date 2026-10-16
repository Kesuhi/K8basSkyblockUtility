package com.k8bas.skyblockutility.ui.render;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.util.List;
import java.util.function.IntFunction;

/**
 * A rounded shape as the GUI elements it is submitted as (REQ-UI-27, T3.15): one element, or two back to back with
 * identical bounds (a smooth shape's mask quads, then its straight parts). The game orders the elements of a layer
 * by their bounds only, so every piece of a shape shares the bounds of the whole shape: the shape's GUI rectangle
 * under the outer pose, rounded outward. A shape drawn over another one then always overlaps its bounds and lands
 * above it, also at fractional positions and scales; piece by piece, a span starting half-way into a GUI pixel lost
 * up to a pixel of its bounds and slid under (the 1.2.1 slider-knob line). Discs and pills take their round ends from
 * one disc mask per diameter (REQ-UI-30, T3.18). {@link #image} plans a baked texture drawn 1:1 as one element with the
 * same outward-rounded bounds (REQ-UI-31, T3.19). Pure, so it is tested without a game.
 */
public final class ShapePlan {
	/**
	 * One element: {@code quads} holds x0, y0, x1, y1 per quad in screen pixels relative to the shape's corner (x1
	 * and y1 exclusive), {@code uvs} the texture's u0, v0, u1, v1 per quad for a textured element, else null. The
	 * bounds are in GUI pixels, right and bottom exclusive.
	 */
	public record Element(boolean textured, int[] quads, float[] uvs, int left, int top, int right, int bottom) {
	}

	/** The elements, and the {@link MaskKey} of the mask they sample (-1 when none). */
	public record Result(List<Element> elements, int maskKey) {
		static final Result EMPTY = new Result(List.of(), -1);
	}

	/** A plan and the mask (the texture) it samples: null when it needs none, or when none came back and it fell back to fill spans. */
	public record Drawn<V>(Result plan, V mask) {
	}

	/** A GUI pixel fraction below anything a 1/scale grid can produce. */
	private static final float NOISE = 1e-4F;

	private ShapePlan() {
	}

	/**
	 * A w×h shape at (x, y) in GUI pixels with corners of {@code radius} GUI pixels, filled ({@code thickness} 0) or
	 * outlined, at the given GUI scale. With {@code smooth} the corners come from a mask where one can draw them.
	 * The bounds are clipped to {@code scissor} ({left, top, right, bottom} in GUI pixels, or null for none), as the
	 * game clips its own elements; a shape wholly outside it gives no element.
	 */
	public static Result of(Matrix3x2fc outer, int x, int y, int w, int h, int radius, int thickness, int scale, boolean smooth, int[] scissor) {
		return ofPixels(outer, x, y, w * scale, h * scale, pixelRadius(w, h, radius, thickness, scale), thickness * scale, Corners.ALL, scale,
				smooth, scissor);
	}

	/**
	 * The radius in screen pixels of a w×h GUI shape with a radius of {@code radius} GUI pixels. A filled shape whose radius
	 * is at least half its shorter side (an odd side's h/2 too) is a disc or a pill (REQ-UI-30): its radius is half that side
	 * in screen pixels, rounded up, so an odd side is round as well. Otherwise the radius times the scale.
	 */
	public static int pixelRadius(int w, int h, int radius, int thickness, int scale) {
		int side = Math.min(w, h);
		return thickness == 0 && radius >= 1 && radius >= side / 2 ? (side * scale + 1) / 2 : radius * scale;
	}

	/** The radius in screen pixels of a bar h GUI pixels high (REQ-UI-28): half its height, rounded up, so it is a one-cap pill. */
	public static int barRadius(int h, int scale) {
		return (h * scale + 1) / 2;
	}

	/**
	 * A bar from GUI x0 to x1 with both ends snapped to the nearest screen pixel (REQ-UI-28): its start and width in screen
	 * pixels, or null when both ends snap to the same pixel edge or it runs backwards.
	 */
	public static int[] barSpan(float x0, float x1, int scale) {
		int from = Math.round(x0 * scale);
		int to = Math.round(x1 * scale);
		return to > from ? new int[] {from, to - from} : null;
	}

	/**
	 * As {@link #of}, in screen pixels: an sw×sh shape with radius sr and outline thickness st (0 = filled) whose
	 * corner sits at GUI (x, y), a multiple of 1/scale; for a filled shape only the {@link Corners} {@code corners} are
	 * round (an outline rounds all four).
	 */
	public static Result ofPixels(Matrix3x2fc outer, float x, float y, int sw, int sh, int sr, int st, int corners, int scale, boolean smooth,
			int[] scissor) {
		if (sw <= 0 || sh <= 0) {
			return Result.EMPTY;
		}
		int[] box = clip(bounds(outer, x, y, x + (float) sw / scale, y + (float) sh / scale), scissor);
		if (box == null) {
			return Result.EMPTY;
		}
		if (smooth) {
			Result masked = st == 0 ? round(box, sw, sh, sr, corners) : null;
			if (masked == null) {
				masked = masked(box, sw, sh, sr, st, corners);
			}
			if (masked != null) {
				return masked;
			}
		}
		List<Corners.Span> spans = st == 0 ? Corners.fill(sw, sh, sr, corners) : Corners.ring(sw, sh, sr, st);
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
		return new Result(List.of(element(false, quads, null, box)), -1);
	}

	/**
	 * A texture of sw×sh texels with its corner at GUI (x, y), a multiple of 1/scale (REQ-UI-31, T3.19): one textured element
	 * showing the whole texture with one texel per screen pixel, its bounds the image's GUI rectangle rounded outward and cut
	 * to {@code scissor}; null when nothing of it is left.
	 */
	public static Element image(Matrix3x2fc outer, float x, float y, int sw, int sh, int scale, int[] scissor) {
		if (sw <= 0 || sh <= 0) {
			return null;
		}
		int[] box = clip(bounds(outer, x, y, x + (float) sw / scale, y + (float) sh / scale), scissor);
		return box == null ? null : element(true, new int[] {0, 0, sw, sh}, new float[] {0, 0, 1, 1}, box);
	}

	/**
	 * {@link #ofPixels} with its mask looked up in {@code masks} by {@link MaskKey}. Where none comes back (the texture cannot
	 * be made, or the cache already holds its most masks), the shape is planned again from fill spans (REQ-UI-47).
	 */
	public static <V> Drawn<V> resolve(Matrix3x2fc outer, float x, float y, int sw, int sh, int sr, int st, int corners, int scale,
			boolean smooth, int[] scissor, IntFunction<V> masks) {
		Result plan = ofPixels(outer, x, y, sw, sh, sr, st, corners, scale, smooth, scissor);
		if (plan.maskKey() < 0) {
			return new Drawn<>(plan, null);
		}
		V mask = masks.apply(plan.maskKey());
		return mask != null ? new Drawn<>(plan, mask) : new Drawn<>(ofPixels(outer, x, y, sw, sh, sr, st, corners, scale, false, scissor), null);
	}

	/**
	 * A disc, pill or one-cap pill (REQ-UI-30) from the disc mask of its diameter, the shorter side (a one-cap pill's height),
	 * or null for any other shape. A disc is one quad showing the whole mask. A pill is its two caps, each the outer half of
	 * the mask (an odd diameter's middle column goes to the straight part), then the straight middle. A one-cap pill is its
	 * cap, or the part of the cap before its square end, then the rest.
	 */
	private static Result round(int[] box, int w, int h, int r, int corners) {
		boolean oneCap = Corners.oneCap(corners);
		int d = oneCap ? h : Math.min(w, h);
		if ((!oneCap && corners != Corners.ALL) || 2 * r < d || d < 2 || d > 2 * CornerMask.MAX_RADIUS) {
			return null;
		}
		int cap = d / 2;
		float size = d;
		int key = MaskKey.disc(d);
		if (oneCap) {
			int shown = Math.min(cap, w);
			int[] rest = w > shown ? new int[] {corners == Corners.LEFT ? shown : 0, 0, corners == Corners.LEFT ? w : w - shown, d} : new int[0];
			return corners == Corners.LEFT
					? result(box, new int[] {0, 0, shown, d}, new float[] {0, 0, shown / size, 1}, rest, key)
					: result(box, new int[] {w - shown, 0, w, d}, new float[] {(d - shown) / size, 0, 1, 1}, rest, key);
		}
		if (w == h) {
			return result(box, new int[] {0, 0, d, d}, new float[] {0, 0, 1, 1}, new int[0], key);
		}
		if (w > h) {
			return result(box, new int[] {0, 0, cap, d, w - cap, 0, w, d}, new float[] {0, 0, cap / size, 1, (d - cap) / size, 0, 1, 1},
					new int[] {cap, 0, w - cap, d}, key);
		}
		return result(box, new int[] {0, 0, d, cap, 0, h - cap, d, h}, new float[] {0, 0, 1, cap / size, 0, (d - cap) / size, 1, 1},
				new int[] {0, cap, d, h - cap}, key);
	}

	/** The smooth shape (REQ-UI-18), or null where a mask cannot draw it (the fill-based corners do). */
	private static Result masked(int[] box, int w, int h, int r, int t, int corners) {
		// round() draws one-cap pills from the disc mask (REQ-UI-30). A one-cap shape gets here only with a height under 2 or
		// over 512 px, or a radius under half its height: its radius stays up to h/2, and one shorter than two corners is
		// drawn from fills.
		int radius = Corners.oneCap(corners) ? Math.max(0, Math.min(r, h / 2)) : CornerPieces.clampRadius(w, h, r);
		int[] pieces = new int[CornerPieces.MAX_PIECES * CornerPieces.STRIDE];
		int n = t == 0 ? CornerPieces.fill(w, h, radius, corners, pieces) : CornerPieces.ring(w, h, radius, t, pieces);
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
		if (masks == 0) {
			return new Result(List.of(element(false, fillQuads, null, box)), -1);
		}
		return result(box, maskQuads, uvs, fillQuads, MaskKey.corner(radius, t));
	}

	/** The mask quads as one textured element, then the straight parts, if any, as a plain one with the same bounds. */
	private static Result result(int[] box, int[] maskQuads, float[] uvs, int[] fillQuads, int mask) {
		Element round = element(true, maskQuads, uvs, box);
		return new Result(fillQuads.length == 0 ? List.of(round) : List.of(round, element(false, fillQuads, null, box)), mask);
	}

	private static Element element(boolean textured, int[] quads, float[] uvs, int[] box) {
		return new Element(textured, quads, uvs, box[0], box[1], box[2], box[3]);
	}

	/**
	 * Whether a shape is smooth whatever "Smooth corners" says (REQ-UI-29): the setting decides only the corners of
	 * rounded rectangles and outlines, so a filled disc, pill (radius half its shorter side) and one-cap pill always are.
	 * An outline ({@code t} > 0) always follows the setting, also one shaped like a pill (the toggle's focus ring).
	 */
	public static boolean alwaysSmooth(int w, int h, int r, int t, int corners) {
		return t == 0 && (corners != Corners.ALL || 2 * r >= Math.min(w, h));
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
	 * game's own rounding, which takes the ceiling of the size and so can come out a pixel short. Float noise below
	 * {@link #NOISE} (1/3 is not exact) does not round a whole pixel up.
	 */
	public static int[] bounds(Matrix3x2fc pose, float x0, float y0, float x1, float y1) {
		Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
		Vector2f b = pose.transformPosition(x1, y0, new Vector2f());
		Vector2f c = pose.transformPosition(x0, y1, new Vector2f());
		Vector2f d = pose.transformPosition(x1, y1, new Vector2f());
		int left = (int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)) + NOISE);
		int top = (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)) + NOISE);
		int right = (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)) - NOISE);
		int bottom = (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)) - NOISE);
		return right > left && bottom > top ? new int[] {left, top, right, bottom} : null;
	}
}

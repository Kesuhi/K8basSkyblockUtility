package com.k8bas.skyblockutility.ui.render;

import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-31 (REQ-UI-30, REQ-UI-31, T3.18): a disc is one quad of a disc mask baked per diameter in screen pixels, a pill its
 * two caps and a straight middle, a one-cap pill one cap and the rest, as one element or two back to back with identical
 * bounds (REQ-UI-27); the masks are sampled linear and clamped to the edge. Without a mask they are fill spans (AC-UI-48).
 */
class DiscsAndPillsTest {
	private static final Matrix3x2f AT = new Matrix3x2f().translate(37, 21);
	/** A pixel's coverage within this of the exact area (AC-UI-31, the disc mask's bound). */
	private static final double CLOSE = 0.05;

	/** A square whose radius is at least half its side (h/2, or more) is one textured quad showing the whole disc mask. */
	@Test
	void aDiscIsOneTexturedQuad() {
		for (int scale = 1; scale <= 4; scale++) {
			for (int size = 2; size <= 16; size++) {
				for (int radius : new int[] {size / 2, size / 2 + 3}) {
					int d = size * scale;
					String what = "scale " + scale + ", " + size + " px, radius " + radius;
					int sr = ShapePlan.pixelRadius(size, size, radius, 0, scale);
					assertTrue(ShapePlan.alwaysSmooth(d, d, sr, 0, Corners.ALL), what + ": smooth whatever the setting");
					ShapePlan.Result plan = ShapePlan.of(AT, 3, 5, size, size, radius, 0, scale, true, null);
					assertEquals(1, plan.elements().size(), what);
					ShapePlan.Element disc = plan.elements().get(0);
					assertTrue(disc.textured(), what);
					assertArrayEquals(new int[] {0, 0, d, d}, disc.quads(), what + ": one quad");
					assertArrayEquals(new float[] {0, 0, 1, 1}, disc.uvs(), what + ": the whole mask");
					assertEquals(MaskKey.disc(d), plan.maskKey(), what + ": the disc mask of its diameter in screen pixels");
				}
			}
		}
	}

	/** A wider (or taller) shape with radius half its shorter side: two caps of the disc mask, then the middle. */
	@Test
	void aPillIsTwoCapsAndTheMiddle() {
		ShapePlan.Result toggle = ShapePlan.of(AT, 3, 5, 20, 8, 4, 0, 1, true, null);
		assertEquals(2, toggle.elements().size());
		ShapePlan.Element caps = toggle.elements().get(0);
		ShapePlan.Element middle = toggle.elements().get(1);
		assertTrue(caps.textured() && !middle.textured(), "the caps first, then the straight middle");
		assertArrayEquals(new int[] {0, 0, 4, 8, 16, 0, 20, 8}, caps.quads());
		assertArrayEquals(new float[] {0, 0, 0.5F, 1, 0.5F, 0, 1, 1}, caps.uvs(), "the left and the right half of the disc");
		assertArrayEquals(new int[] {4, 0, 16, 8}, middle.quads());
		assertEquals(MaskKey.disc(8), toggle.maskKey());
		ShapePlan.Result thumb = ShapePlan.of(AT, 3, 5, 3, 20, 1, 0, 2, true, null);
		assertArrayEquals(new int[] {0, 0, 6, 3, 0, 37, 6, 40}, thumb.elements().get(0).quads(), "a standing pill: caps at the top and bottom");
		assertArrayEquals(new float[] {0, 0, 1, 0.5F, 0, 0.5F, 1, 1}, thumb.elements().get(0).uvs());
		assertArrayEquals(new int[] {0, 3, 6, 37}, thumb.elements().get(1).quads());
		for (int scale = 1; scale <= 4; scale++) {
			for (int h = 2; h <= 14; h++) {
				for (int w : new int[] {h + 1, h + 2, h + 5, 3 * h}) {
					for (boolean standing : new boolean[] {false, true}) {
						int gw = standing ? h : w;
						int gh = standing ? w : h;
						String what = "scale " + scale + ", " + gw + "x" + gh;
						ShapePlan.Result plan = ShapePlan.of(AT, 3, 5, gw, gh, h / 2, 0, scale, true, null);
						int d = h * scale;
						assertEquals(MaskKey.disc(d), plan.maskKey(), what);
						assertEquals(2, plan.elements().size(), what);
						assertTrue(plan.elements().get(0).textured() && !plan.elements().get(1).textured(), what);
						assertEquals(8, plan.elements().get(0).quads().length, what + ": two caps");
						assertEquals(4, plan.elements().get(1).quads().length, what + ": one middle");
						assertArrayEquals(box(plan.elements().get(0)), box(plan.elements().get(1)), what + ": identical bounds");
						double[][] drawn = draw(plan, gw * scale, gh * scale);
						for (int y = 0; y < gh * scale; y++) {
							for (int x = 0; x < gw * scale; x++) {
								assertTrue(drawn[y][x] >= 0, what + ": pixel " + x + "," + y + " drawn once");
							}
						}
					}
				}
			}
		}
	}

	/**
	 * A pill's coverage has no step at the cap seams: every pixel is within {@link #CLOSE} of the exact stadium's area, the
	 * middle is fully covered, and each row only rises from an end to the middle. A standing pill (the scrollbar thumb's
	 * shape) is the same transposed: x runs along its length, y across it.
	 */
	@Test
	void aPillsCoverageHasNoStepAtTheCapSeams() {
		for (int d = 4; d <= 40; d++) {
			for (int w : new int[] {d + 1, d + 2, d + 3, d + 8, 3 * d}) {
				for (boolean standing : new boolean[] {false, true}) {
					ShapePlan.Result plan = standing
							? ShapePlan.ofPixels(AT, 3, 5, d, w, (d + 1) / 2, 0, Corners.ALL, 1, true, null)
							: ShapePlan.ofPixels(AT, 3, 5, w, d, (d + 1) / 2, 0, Corners.ALL, 1, true, null);
					double[][] drawn = standing ? draw(plan, d, w) : draw(plan, w, d);
					String what = "diameter " + d + ", length " + w + (standing ? ", standing" : "");
					for (int y = 0; y < d; y++) {
						for (int x = 0; x < w; x++) {
							double here = standing ? drawn[x][y] : drawn[y][x];
							double exact = stadiumArea(w, d, x, y, true, true);
							assertEquals(exact, here, CLOSE, what + ", pixel " + x + "," + y);
							if (x >= (d + 1) / 2 && x < w - (d + 1) / 2) {
								assertEquals(1.0, here, 1e-9, what + ", pixel " + x + "," + y + ": the middle is full");
							}
							if (x > 0 && 2 * x < w) {
								assertTrue(here >= (standing ? drawn[x - 1][y] : drawn[y][x - 1]), what + ", row " + y + " falls at " + x);
							}
							if (x < w - 1 && 2 * x >= w) {
								assertTrue(here >= (standing ? drawn[x + 1][y] : drawn[y][x + 1]), what + ", row " + y + " falls at " + x);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * A one-cap pill (REQ-UI-28's fill and track) is its cap of the disc mask and the rest; one shorter than its cap shows only
	 * the part of the cap before its square end. Either way its pixels are those of a long one-cap pill cut at the square end.
	 */
	@Test
	void aOneCapPillIsOneCapAndTheRest() {
		for (int d : new int[] {2, 3, 4, 5, 8, 9, 12, 16, 17}) {
			int cap = d / 2;
			for (int corners : new int[] {Corners.LEFT, Corners.RIGHT}) {
				boolean left = corners == Corners.LEFT;
				for (int w = 1; w <= 3 * d; w++) {
					String what = (left ? "LEFT" : "RIGHT") + " diameter " + d + ", width " + w;
					ShapePlan.Result plan = ShapePlan.ofPixels(AT, 3, 5, w, d, (d + 1) / 2, 0, corners, 1, true, null);
					assertEquals(MaskKey.disc(d), plan.maskKey(), what);
					ShapePlan.Element round = plan.elements().get(0);
					assertTrue(round.textured(), what + ": the cap comes first");
					assertEquals(4, round.quads().length, what + ": one cap");
					int shown = Math.min(cap, w);
					if (w <= cap) {
						assertEquals(1, plan.elements().size(), what + ": only the cap");
					} else {
						assertEquals(2, plan.elements().size(), what + ": the cap and the rest");
						assertArrayEquals(box(round), box(plan.elements().get(1)), what + ": identical bounds");
					}
					if (left) {
						assertArrayEquals(new int[] {0, 0, shown, d}, round.quads(), what);
						assertArrayEquals(new float[] {0, 0, (float) shown / d, 1}, round.uvs(), what + ": the disc's first columns");
					} else {
						assertArrayEquals(new int[] {w - shown, 0, w, d}, round.quads(), what);
						assertArrayEquals(new float[] {(float) (d - shown) / d, 0, 1, 1}, round.uvs(), what + ": the disc's last columns");
					}
					double[][] drawn = draw(plan, w, d);
					int longW = 4 * d;
					for (int y = 0; y < d; y++) {
						for (int x = 0; x < w; x++) {
							double exact = left ? stadiumArea(longW, d, x, y, true, false) : stadiumArea(longW, d, longW - w + x, y, false, true);
							assertEquals(exact, drawn[y][x], CLOSE, what + ", pixel " + x + "," + y);
						}
						if (w > cap) {
							assertEquals(1.0, drawn[y][left ? w - 1 : 0], 1e-9, what + ", row " + y + ": the square end is full");
						}
					}
				}
			}
		}
	}

	/** Stepping a slider's fill and the rest of its track through every width bakes one mask per diameter, none per width. */
	@Test
	void steppingTheFillBakesOneMaskPerDiameter() {
		List<Integer> baked = new ArrayList<>();
		MaskCache<Integer> cache = new MaskCache<>(key -> {
			baked.add(key);
			return key;
		}, key -> {
		}, (message, cause) -> {
		});
		for (int scale = 1; scale <= 4; scale++) {
			int before = baked.size();
			int h = 4 * scale;
			int trackFrom = 20 * scale;
			int trackTo = 140 * scale;
			for (int end = trackFrom + 1; end <= trackTo; end++) {
				for (int[] bar : new int[][] {{trackFrom, end, Corners.LEFT}, {end - 1, trackTo, Corners.RIGHT}}) {
					int[] span = ShapePlan.barSpan((float) bar[0] / scale, (float) bar[1] / scale, scale);
					if (span == null) {
						continue;
					}
					ShapePlan.Result plan = ShapePlan.ofPixels(AT, (float) span[0] / scale, 5, span[1], h, ShapePlan.barRadius(4, scale), 0, bar[2],
							scale, true, null);
					assertEquals(MaskKey.disc(h), plan.maskKey(), "scale " + scale + ", bar " + span[0] + "+" + span[1]);
					assertNotNull(cache.get(scale, plan.maskKey()));
				}
			}
			assertEquals(List.of(MaskKey.disc(h)), baked.subList(before, baked.size()), "scale " + scale + ": one mask");
		}
	}

	/** A bar's ends are snapped to the nearest screen pixel, not floored or ceiled. */
	@Test
	void barEndsSnapToTheNearestScreenPixel() {
		assertArrayEquals(new int[] {21, 3}, ShapePlan.barSpan(10.4F, 11.8F, 2), "20.8 -> 21, 23.6 -> 24");
		assertArrayEquals(new int[] {10, 2}, ShapePlan.barSpan(10.4F, 11.6F, 1));
		assertNull(ShapePlan.barSpan(10.6F, 10.9F, 1), "both ends snap to the same pixel edge: nothing");
		assertNull(ShapePlan.barSpan(12F, 10F, 3), "backwards: nothing");
		assertEquals(8, ShapePlan.barRadius(4, 4), "half the height");
		assertEquals(3, ShapePlan.barRadius(5, 1), "rounded up, so an odd height is still a pill");
	}

	/**
	 * AC-UI-48 [A], REQ-UI-47: where no mask comes back (texture creation fails, or the 65th mask at one scale), discs, pills and
	 * one-cap pills are fill spans, as the fill-based corners draw them.
	 */
	@Test
	void withoutAMaskDiscsAndPillsAreFillSpans() {
		List<String> logged = new ArrayList<>();
		MaskCache<String> failing = new MaskCache<>(key -> {
			throw new IllegalStateException("no texture");
		}, mask -> {
		}, (message, cause) -> logged.add(message));
		MaskCache<String> full = new MaskCache<>(key -> "mask " + key, mask -> {
		}, (message, cause) -> logged.add(message));
		for (int d = 100; d < 100 + MaskCache.MAX_MASKS; d++) {
			full.get(2, MaskKey.disc(d));
		}
		int[][] shapes = {{20, 20, 10, Corners.ALL}, {48, 24, 12, Corners.ALL}, {9, 40, 5, Corners.ALL}, {30, 8, 4, Corners.LEFT},
				{3, 8, 4, Corners.RIGHT}};
		for (MaskCache<String> cache : List.of(failing, full)) {
			for (int[] s : shapes) {
				String what = (cache == failing ? "failed" : "full") + ", " + s[0] + "x" + s[1] + " radius " + s[2] + " corners " + s[3];
				ShapePlan.Drawn<String> drawn = ShapePlan.resolve(AT, 3, 5, s[0], s[1], s[2], 0, s[3], 2, true, null, key -> cache.get(2, key));
				assertNull(drawn.mask(), what);
				assertEquals(-1, drawn.plan().maskKey(), what);
				assertEquals(1, drawn.plan().elements().size(), what);
				ShapePlan.Element element = drawn.plan().elements().get(0);
				assertFalse(element.textured(), what);
				List<Corners.Span> spans = Corners.fill(s[0], s[1], s[2], s[3]);
				int[] expected = new int[spans.size() * 4];
				for (int i = 0; i < spans.size(); i++) {
					Corners.Span span = spans.get(i);
					System.arraycopy(new int[] {span.x0(), span.y0(), span.x1(), span.y1()}, 0, expected, i * 4, 4);
				}
				assertArrayEquals(expected, element.quads(), what + ": the fill spans");
			}
		}
		assertEquals(2, logged.size(), "one line each: " + logged);
		ShapePlan.Drawn<String> made = ShapePlan.resolve(AT, 3, 5, 20, 20, 10, 0, Corners.ALL, 2, true, null, key -> "mask " + key);
		assertEquals("mask " + MaskKey.disc(20), made.mask(), "a mask that comes back is drawn");
		assertTrue(made.plan().elements().get(0).textured());
	}

	/**
	 * REQ-UI-47: a disc or pill can borrow the disc mask of another diameter (a knob or thumb between its two sizes): the same
	 * quads and texture coordinates, the borrowed mask's key; a shape that is no disc or pill ignores it.
	 */
	@Test
	void aDiscOrPillCanBorrowAnotherDiametersMask() {
		int[][] shapes = {{21, 21, 11, Corners.ALL}, {9, 40, 5, Corners.ALL}, {48, 24, 12, Corners.ALL}, {30, 8, 4, Corners.LEFT}};
		for (int[] s : shapes) {
			String what = s[0] + "x" + s[1] + " radius " + s[2] + " corners " + s[3];
			ShapePlan.Drawn<String> own = ShapePlan.resolve(AT, 3, 5, s[0], s[1], s[2], 0, s[3], 2, true, null, key -> "mask " + key);
			ShapePlan.Drawn<String> borrowed = ShapePlan.resolve(AT, 3, 5, s[0], s[1], s[2], 0, s[3], 2, true, 60, null, key -> "mask " + key);
			assertEquals(MaskKey.disc(60), borrowed.plan().maskKey(), what);
			assertEquals("mask " + MaskKey.disc(60), borrowed.mask(), what);
			assertEquals(own.plan().elements().size(), borrowed.plan().elements().size(), what);
			for (int i = 0; i < own.plan().elements().size(); i++) {
				assertArrayEquals(own.plan().elements().get(i).quads(), borrowed.plan().elements().get(i).quads(), what);
				assertArrayEquals(own.plan().elements().get(i).uvs(), borrowed.plan().elements().get(i).uvs(), what);
			}
		}
		ShapePlan.Drawn<String> card = ShapePlan.resolve(AT, 3, 5, 40, 30, 6, 0, Corners.ALL, 2, true, 60, null, key -> "mask " + key);
		assertEquals(MaskKey.corner(6, 0), card.plan().maskKey(), "a rounded rectangle keeps its corner mask");
		ShapePlan.Drawn<String> zero = ShapePlan.resolve(AT, 3, 5, 21, 21, 11, 0, Corners.ALL, 2, true, 0, null, key -> "mask " + key);
		assertEquals(MaskKey.disc(21), zero.plan().maskKey(), "0 borrows nothing");
	}

	/** AC-UI-31: the masks' texture setup samples linear and clamped to the edge (REQ-UI-31), the sampler cache's shared one. */
	@Test
	void masksSampleLinearAndClampedToTheEdge() {
		List<List<Object>> asked = new ArrayList<>();
		GpuSampler shared = new FakeSampler();
		SamplerCache samplers = new SamplerCache() {
			@Override
			public GpuSampler getSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, boolean mipmaps) {
				asked.add(List.of(u, v, min, mag, mipmaps));
				return shared;
			}
		};
		TextureSetup setup = Shapes.maskTexture(null, samplers);
		assertSame(shared, setup.sampler0(), "the cache's own sampler, not the texture's");
		assertEquals(List.of(List.of(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false)), asked);
	}

	/** Mask quads show their texels 1:1, so at whole screen pixels linear sampling reads each texel exactly. */
	@Test
	void maskQuadsAreDrawnOneTexelPerPixel() {
		Set<Integer> sizes = new HashSet<>();
		for (int scale = 1; scale <= 4; scale++) {
			for (int[] s : new int[][] {{10, 10, 5}, {11, 11, 5}, {24, 12, 6}, {7, 20, 3}, {40, 24, 6}}) {
				ShapePlan.Result plan = ShapePlan.of(AT, 3, 5, s[0], s[1], s[2], 0, scale, true, null);
				ShapePlan.Element round = plan.elements().get(0);
				int size = MaskKey.isDisc(plan.maskKey()) ? MaskKey.diameter(plan.maskKey()) : 2 * MaskKey.radius(plan.maskKey());
				sizes.add(size);
				for (int i = 0; i < round.quads().length; i += 4) {
					assertEquals(round.quads()[i + 2] - round.quads()[i], (round.uvs()[i + 2] - round.uvs()[i]) * size, 1e-3);
					assertEquals(round.quads()[i + 3] - round.quads()[i + 1], (round.uvs()[i + 3] - round.uvs()[i + 1]) * size, 1e-3);
					assertEquals(Math.round(round.uvs()[i] * size), round.uvs()[i] * size, 1e-3, "starts on a texel edge");
				}
			}
		}
		assertTrue(sizes.size() > 5, "the sweep ran: " + sizes);
	}

	/**
	 * Each pixel's coverage as drawn (0-1): a mask quad's texel of the baked mask (1:1), 1 under a plain quad; -1 where nothing
	 * is drawn. A pixel drawn twice fails, since a translucent colour would blend twice there.
	 */
	private static double[][] draw(ShapePlan.Result plan, int w, int h) {
		double[][] alpha = new double[h][w];
		for (double[] row : alpha) {
			java.util.Arrays.fill(row, -1);
		}
		int d = MaskKey.isDisc(plan.maskKey()) ? MaskKey.diameter(plan.maskKey()) : -1;
		int[] mask = d > 0 ? CornerMask.bakeDisc(d) : null;
		for (ShapePlan.Element element : plan.elements()) {
			int[] q = element.quads();
			for (int i = 0; i < q.length; i += 4) {
				for (int y = q[i + 1]; y < q[i + 3]; y++) {
					for (int x = q[i]; x < q[i + 2]; x++) {
						assertEquals(-1, alpha[y][x], 0, "pixel " + x + "," + y + " drawn twice");
						if (element.textured()) {
							int tx = Math.round(element.uvs()[i] * d) + x - q[i];
							int ty = Math.round(element.uvs()[i + 1] * d) + y - q[i + 1];
							alpha[y][x] = mask[ty * d + tx] / 255.0;
						} else {
							alpha[y][x] = 1;
						}
					}
				}
			}
		}
		return alpha;
	}

	/**
	 * The exact area of pixel (x, y) inside a w×d stadium (a pill: radius d/2, its caps' centres d/2 from either end), with a
	 * square end where a side is not round. Thin vertical strips, independent of the mask's formula.
	 */
	private static double stadiumArea(int w, int d, int x, int y, boolean roundLeft, boolean roundRight) {
		double r = d / 2.0;
		int strips = 1000;
		double sum = 0;
		for (int i = 0; i < strips; i++) {
			double sx = x + (i + 0.5) / strips;
			double top = 0;
			double bottom = d;
			double centre = sx < r && roundLeft ? r : sx > w - r && roundRight ? w - r : Double.NaN;
			if (!Double.isNaN(centre)) {
				double dx = sx - centre;
				double half = Math.abs(dx) < r ? Math.sqrt(r * r - dx * dx) : 0;
				top = r - half;
				bottom = r + half;
			}
			sum += Math.max(0, Math.min(y + 1, bottom) - Math.max(y, top));
		}
		return sum / strips;
	}

	private static int[] box(ShapePlan.Element element) {
		return new int[] {element.left(), element.top(), element.right(), element.bottom()};
	}

	/** A sampler that is never bound: only its identity is checked. */
	private static final class FakeSampler extends GpuSampler {
		@Override
		public AddressMode getAddressModeU() {
			return AddressMode.CLAMP_TO_EDGE;
		}

		@Override
		public AddressMode getAddressModeV() {
			return AddressMode.CLAMP_TO_EDGE;
		}

		@Override
		public FilterMode getMinFilter() {
			return FilterMode.LINEAR;
		}

		@Override
		public FilterMode getMagFilter() {
			return FilterMode.LINEAR;
		}

		@Override
		public int getMaxAnisotropy() {
			return 1;
		}

		@Override
		public OptionalDouble getMaxLod() {
			return OptionalDouble.of(0);
		}

		@Override
		public void close() {
		}
	}
}

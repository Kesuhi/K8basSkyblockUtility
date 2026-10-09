package com.k8bas.skyblockutility.render.marker;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** REQ-MARK-03, -06, -08 (T3.0b): the providers' markers, and the label size math. */
class WorldMarkersTest {
	/** A provider with a fixed list, as a feature with its own toggle would have. */
	private static final class ListProvider implements MarkerProvider {
		final List<Marker> markers = new ArrayList<>();
		boolean active = true;

		ListProvider(String text) {
			markers.add(new Marker(MarkerAnchor.fixed(0, 64, 0), MarkerLabel.of(new MarkerLabel.Line(Component.literal(text), 0xFFFFFF))));
		}

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public void collect(Consumer<Marker> out) {
			markers.forEach(out);
		}

		@Override
		public void reset() {
			markers.clear();
		}
	}

	/** AC-MARK-05 [A]: turning one feature off never hides another's markers. */
	@Test
	void eachProviderFollowsOnlyItsOwnToggle() {
		ListProvider waypoints = new ListProvider("Croesus");
		ListProvider second = new ListProvider("Hotspot");
		assertEquals(2, WorldMarkers.collect(List.of(waypoints, second)).size());

		waypoints.active = false;
		List<Marker> drawn = WorldMarkers.collect(List.of(waypoints, second));
		assertEquals(List.copyOf(second.markers), drawn);
	}

	/** AC-MARK-07 [A]: after a world change, the first frame has no marker from the old world. */
	@Test
	void aWorldChangeDropsEveryProvidersMarkers() {
		ListProvider waypoints = new ListProvider("Croesus");
		ListProvider second = new ListProvider("Hotspot");
		second.active = false;
		WorldMarkers.reset(List.of(waypoints, second));
		assertEquals(List.of(), WorldMarkers.collect(List.of(waypoints, second)));
		assertEquals(List.of(), second.markers, "an inactive provider is reset as well");
	}

	/** EC-MARK-09: two markers at one position are both drawn; deduplication is the provider's job. */
	@Test
	void duplicatesAreKept() {
		ListProvider provider = new ListProvider("Croesus");
		provider.markers.add(provider.markers.get(0));
		assertEquals(2, WorldMarkers.collect(List.of(provider)).size());
	}

	/** A provider that throws is skipped (logged once); the others still draw and reset. */
	@Test
	void aFailingProviderDoesNotStopTheOthers() {
		MarkerProvider broken = new MarkerProvider() {
			@Override
			public boolean isActive() {
				return true;
			}

			@Override
			public void collect(Consumer<Marker> out) {
				throw new IllegalStateException("broken provider");
			}

			@Override
			public void reset() {
				throw new IllegalStateException("broken provider");
			}
		};
		ListProvider second = new ListProvider("Hotspot");
		assertEquals(List.copyOf(second.markers), WorldMarkers.collect(List.of(broken, second)));
		WorldMarkers.reset(List.of(broken, second));
		assertEquals(List.of(), second.markers);
	}

	/**
	 * REQ-MARK-02 / REQ-MARK-03: a depth-tested label is not pulled in (it would then pass the depth test
	 * through anything between 10 blocks and its anchor); it grows with the distance instead, which gives
	 * the same on-screen size as a pulled see-through label.
	 */
	@Test
	void depthTestedLabelsGrowInsteadOfBeingPulledIn() {
		assertEquals(0.025F, WorldMarkers.labelScale(30, true));
		assertEquals(0.025F, WorldMarkers.labelScale(5, false));
		assertEquals(0.075F, WorldMarkers.labelScale(30, false), 1e-6);
		// Pulled in to 10 blocks at the base scale, or grown at the true distance: the same angular size.
		double pulled = 0.025 / (30 * (1 + WorldMarkers.pullFactor(30)));
		double grown = WorldMarkers.labelScale(30, false) / 30.0;
		assertEquals(pulled, grown, 1e-9);
	}

	/** EC-PORT-06 / EC-MARK-01: no pull within 10 blocks. */
	@Test
	void noPullUpToTenBlocks() {
		assertEquals(0.0, WorldMarkers.pullFactor(0));
		assertEquals(0.0, WorldMarkers.pullFactor(5));
		assertEquals(0.0, WorldMarkers.pullFactor(9.999));
	}

	/** REQ-MARK-03: beyond 10 blocks a label is drawn pulled in to 10 blocks, so its on-screen size stays constant. */
	@Test
	void beyondTenBlocksTheLabelIsPulledToTenBlocks() {
		assertEquals(0.0, WorldMarkers.pullFactor(10), 0.0); // -0.0 at exactly 10, as in 1.0.1
		for (double distance : new double[] {20, 30, 60}) {
			assertEquals(10.0, distance * (1 + WorldMarkers.pullFactor(distance)), 1e-9, "distance " + distance);
		}
	}

	/** EC-MARK-01: no NaN at any distance, including 0. */
	@Test
	void neverNaN() {
		for (double distance : new double[] {0, 1e-12, 10, 1e6, Double.MIN_VALUE}) {
			assertFalse(Double.isNaN(WorldMarkers.pullFactor(distance)), "distance " + distance);
		}
	}
}

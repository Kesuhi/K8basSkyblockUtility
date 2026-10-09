package com.k8bas.skyblockutility.module.hotspot;

import com.k8bas.skyblockutility.render.marker.MarkerRing;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hotspot ring's look, the particle filter, the card (REQ-HOT-05, REQ-HOT-12) and AC-HOT-07 [R] (T3.6). */
class HotspotModuleTest {
	@Test
	void theRingFollowsTheRingFillAndOpacitySettings() {
		HotspotConfig config = new HotspotConfig();
		MarkerRing both = HotspotModule.ring(config, 0x00AAAA, 3.0);
		assertEquals(MarkerRing.Style.BOTH, both.style());
		assertEquals(Math.round(25 * 255 / 100f), both.argb() >>> 24, "25 % by default");
		assertEquals(0x00AAAA, both.argb() & 0xFFFFFF);
		assertEquals(3.0, both.radius());

		config.fillOpacity = 0;
		assertEquals(MarkerRing.Style.OUTLINE, HotspotModule.ring(config, 0x00AAAA, 3.0).style(), "0 % leaves only the ring, never a disc at alpha 0");
		config.ring = false;
		assertNull(HotspotModule.ring(config, 0x00AAAA, 3.0), "no ring and no visible fill: nothing");
		config.fillOpacity = 100;
		MarkerRing disc = HotspotModule.ring(config, 0x00AAAA, 3.0);
		assertEquals(MarkerRing.Style.DISC, disc.style());
		assertEquals(255, disc.argb() >>> 24);
		config.fill = false;
		assertNull(HotspotModule.ring(config, 0x00AAAA, 3.0));
		config.ring = true;
		assertEquals(MarkerRing.Style.OUTLINE, HotspotModule.ring(config, 0x00AAAA, 3.0).style());
	}

	@Test
	void pinkDustAnywhereAndSmokeOnlyOnTheCrimsonIsleAreRingParticles() {
		assertTrue(HotspotParticles.isRing(HotspotParticles.Kind.DUST, 1.0f, 0.41f, 0.71f, "Hub"), "pink dust");
		assertFalse(HotspotParticles.isRing(HotspotParticles.Kind.DUST, 0.2f, 0.8f, 0.2f, "Hub"), "green dust");
		assertFalse(HotspotParticles.isRing(HotspotParticles.Kind.DUST, 1.0f, 1.0f, 1.0f, "Hub"), "white dust");
		assertTrue(HotspotParticles.isRing(HotspotParticles.Kind.SMOKE, 0, 0, 0, "Crimson Isle"));
		assertFalse(HotspotParticles.isRing(HotspotParticles.Kind.SMOKE, 0, 0, 0, "Hub"), "smoke off the lava island");
		assertFalse(HotspotParticles.isRing(HotspotParticles.Kind.OTHER, 1.0f, 0.41f, 0.71f, "Crimson Isle"));
	}

	@Test
	void theCardIsInFishingWithItsTogglesOnAndTheOpacitySlider() {
		HotspotConfig config = new HotspotConfig();
		int[] changes = {0};
		var cards = HotspotModule.cards(config, value -> config.enabled = value, () -> com.k8bas.skyblockutility.ui.option.OptionStatus.OK, () -> changes[0]++);
		assertEquals(1, cards.size());
		var card = cards.getFirst();
		assertEquals(Category.FISHING, card.category());
		assertEquals("Fishing Hotspots", card.toggle().text().title());
		assertEquals(List.of("Highlight ring", "Filled area", "Fill opacity"), card.options().stream().map(option -> option.text().title()).toList());
		IntSlider opacity = (IntSlider) card.options().get(2);
		assertEquals(0, opacity.min());
		assertEquals(100, opacity.max());
		opacity.binding().set(60);
		assertEquals(60, config.fillOpacity);
		assertEquals(1, changes[0], "a change is stored at once");
		for (Option option : card.all()) {
			assertTrue(option.text().keywords().contains("hotspot"), option.id());
		}
	}

	@Test
	void theFeatureNeverCancelsChangesOrSendsAPacket() throws IOException {
		// AC-HOT-07 [R], REQ-HOT-11.
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		List<Path> files = new ArrayList<>(List.of(project.resolve("src/main/java/com/k8bas/skyblockutility/mixin/ParticleObserverMixin.java")));
		try (Stream<Path> list = Files.list(project.resolve("src/main/kotlin/com/k8bas/skyblockutility/module/hotspot"))) {
			list.forEach(files::add);
		}
		List<String> hits = new ArrayList<>();
		for (Path file : files) {
			String code = Files.readString(file);
			for (String forbidden : List.of(".cancel(", "cancellable", "setReturnValue", "send(", "sendCommand", "sendChat", "connection.", "ChatUtils", "@ModifyArg",
					"@ModifyVariable", "@WrapOperation", "@Redirect")) {
				if (code.contains(forbidden)) {
					hits.add(file.getFileName() + ": " + forbidden);
				}
			}
		}
		assertEquals(List.of(), hits);
	}
}

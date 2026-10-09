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
		assertEquals(List.of("Highlight ring", "Filled area", "Fill opacity", "Hotspot gone warning", "Warning title", "Warning sound", "Sound",
				"Sound volume", "Warning chat message", "Also show as a notice"), card.options().stream().map(option -> option.text().title()).toList());
		// REQ-HOT-12: warning, title, sound and chat ON (D-6), the notice OFF (R12).
		assertTrue(config.warning && config.warnTitle && config.warnSound && config.warnChat);
		assertFalse(config.warnNotice);
		IntSlider opacity = (IntSlider) card.options().get(2);
		assertEquals(0, opacity.min());
		assertEquals(100, opacity.max());
		opacity.binding().set(60);
		assertEquals(60, config.fillOpacity);
		assertEquals(1, changes[0], "a change is stored at once");
		for (Option option : card.all()) {
			// The shared notice channel has its own keywords (notice, toast, popup).
			assertTrue(option.text().keywords().contains(option.id().endsWith("warn_notice") ? "toast" : "hotspot"), option.id());
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
			for (String forbidden : List.of(".cancel(", "cancellable", "setReturnValue", "send(", "sendCommand", "sendChat", "connection.", "@ModifyArg",
					"@ModifyVariable", "@WrapOperation", "@Redirect")) {
				if (code.contains(forbidden)) {
					hits.add(file.getFileName() + ": " + forbidden);
				}
			}
		}
		assertEquals(List.of(), hits);
		// The chat channel is the local chat helper's display line only (REQ-HOT-11).
		String module = Files.readString(project.resolve("src/main/kotlin/com/k8bas/skyblockutility/module/hotspot/HotspotModule.kt"));
		assertEquals(List.of("ChatUtils.chat(line)"), java.util.regex.Pattern.compile("ChatUtils\\.\\w+\\([^)]*\\)").matcher(module).results()
				.map(java.util.regex.MatchResult::group).toList());
	}

	/** AC-HOT-11 [A part]: each channel's toggle on the card switches only that channel, and is stored at once. */
	@Test
	void eachChannelToggleSwitchesOnlyItsChannel() {
		for (String id : List.of("warn_title", "warn_sound", "warn_chat", "warn_notice")) {
			HotspotConfig config = new HotspotConfig();
			int[] changes = {0};
			var card = HotspotModule.cards(config, value -> { }, () -> com.k8bas.skyblockutility.ui.option.OptionStatus.OK, () -> changes[0]++).getFirst();
			com.k8bas.skyblockutility.ui.option.Toggle toggle = (com.k8bas.skyblockutility.ui.option.Toggle) card.options().stream()
					.filter(option -> option.id().equals("fishing_hotspots." + id)).findFirst().orElseThrow();
			boolean was = toggle.binding().get();
			List<String> before = channels(config);
			toggle.binding().set(!was);
			assertEquals(1, changes[0], id + " is stored at once");
			List<String> after = channels(config);
			List<String> changed = new ArrayList<>(before);
			changed.removeAll(after);
			List<String> added = new ArrayList<>(after);
			added.removeAll(before);
			assertEquals(1, changed.size() + added.size(), id + " switches exactly one channel: " + before + " -> " + after);
		}
	}

	/** The channels a warning reaches with [config]. */
	private static List<String> channels(HotspotConfig config) {
		List<String> calls = new ArrayList<>();
		HotspotAlert.fire(HotspotText.Buff.SHARD, config, new HotspotAlert.Output() {
			@Override
			public void title(String title, String subtitle) {
				calls.add("title");
			}

			@Override
			public void sound(HotspotSound sound, float volume) {
				calls.add("sound");
			}

			@Override
			public void chat(String line) {
				calls.add("chat");
			}

			@Override
			public void notice(String title, String body) {
				calls.add("notice");
			}
		});
		return calls;
	}

	/** AC-HOT-05 [A]: a recording output; title OFF and the notice at its default OFF call only sound and chat. */
	@Test
	void theWarningGoesOnlyToTheChannelsThatAreOn() {
		List<String> calls = new ArrayList<>();
		HotspotAlert.Output output = new HotspotAlert.Output() {
			@Override
			public void title(String title, String subtitle) {
				calls.add("title " + title + " / " + subtitle);
			}

			@Override
			public void sound(HotspotSound sound, float volume) {
				calls.add("sound " + sound + " " + volume);
			}

			@Override
			public void chat(String line) {
				calls.add("chat " + line);
			}

			@Override
			public void notice(String title, String body) {
				calls.add("notice " + title + " / " + body);
			}
		};
		HotspotConfig config = new HotspotConfig();
		config.warnTitle = false;
		HotspotAlert.fire(HotspotText.Buff.SEA_CREATURE_CHANCE, config, output);
		assertEquals(List.of("sound PLING 1.0", "chat Sea Creature Chance hotspot is gone."), calls);
		calls.clear();
		config.warnTitle = true;
		config.warnNotice = true;
		config.sound = HotspotSound.BELL;
		config.soundVolume = 40;
		HotspotAlert.fire(HotspotText.Buff.UNKNOWN, config, output);
		assertEquals(List.of("title Hotspot gone! / Fishing hotspot", "sound BELL 0.4", "chat Fishing hotspot is gone.",
				"notice Hotspot gone! / Fishing hotspot"), calls);
		calls.clear();
		config.sound = null;
		config.warnTitle = config.warnChat = config.warnNotice = false;
		HotspotAlert.fire(HotspotText.Buff.SHARD, config, output);
		assertEquals(List.of("sound PLING 0.4"), calls, "an unreadable sound name plays the default");
	}
}

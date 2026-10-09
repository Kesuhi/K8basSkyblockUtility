package com.k8bas.skyblockutility.module.bobberfix;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bobber fix's two decisions (T3.5): AC-BOB-01 and AC-BOB-02 [A]. */
class BobberFixTest {
	private static final int HOOK = 100;

	private enum Linked {
		TIMER_STAND(true, HOOK + 1),
		OTHER_STAND(true, HOOK + 7),
		ZOMBIE(false, HOOK + 1),
		NONE(false, -1);

		final boolean armorStand;
		final int id;

		Linked(boolean armorStand, int id) {
			this.armorStand = armorStand;
			this.id = id;
		}
	}

	@Test
	void theLinkIsIgnoredOnlyForTheOwnHookOnTheTimerStandOnSkyBlockWithTheToggleOn() {
		// AC-BOB-01: {on SkyBlock} x {toggle} x {owner} x {linked entity}.
		List<String> ignored = new ArrayList<>();
		for (boolean skyBlock : new boolean[] {true, false}) {
			for (boolean toggle : new boolean[] {true, false}) {
				for (boolean own : new boolean[] {true, false}) {
					for (Linked linked : Linked.values()) {
						boolean present = linked != Linked.NONE;
						if (BobberFix.ignoresLink(skyBlock, toggle, own, HOOK, present, linked.id, linked.armorStand)) {
							ignored.add(skyBlock + "/" + toggle + "/" + own + "/" + linked);
						}
					}
				}
			}
		}
		assertEquals(List.of("true/true/true/TIMER_STAND"), ignored);
	}

	@Test
	void lavaFloatsOnlyForTheOwnHookOnSkyBlockWithTheToggleOn() {
		// AC-BOB-02: water always floats, lava only when all three hold, any other fluid never.
		for (boolean skyBlock : new boolean[] {true, false}) {
			for (boolean toggle : new boolean[] {true, false}) {
				for (boolean own : new boolean[] {true, false}) {
					assertTrue(BobberFix.floats(BobberFix.Fluid.WATER, skyBlock, toggle, own));
					assertEquals(skyBlock && toggle && own, BobberFix.floats(BobberFix.Fluid.LAVA, skyBlock, toggle, own),
							skyBlock + "/" + toggle + "/" + own);
					assertFalse(BobberFix.floats(BobberFix.Fluid.OTHER, skyBlock, toggle, own));
				}
			}
		}
	}

	@Test
	void theCardIsInFishingOnByDefaultAmberAndShowsTheHookStatus() {
		BobberFixConfig config = new BobberFixConfig();
		assertTrue(config.enabled, "ON in a fresh config (D-6)");
		var unavailable = new com.k8bas.skyblockutility.ui.option.OptionStatus(com.k8bas.skyblockutility.ui.option.OptionStatus.State.UNAVAILABLE, "x");
		var cards = BobberFixModule.cards(config, value -> config.enabled = value, () -> unavailable);
		assertEquals(1, cards.size());
		var toggle = cards.get(0).toggle();
		assertEquals(com.k8bas.skyblockutility.ui.option.Category.FISHING, cards.get(0).category());
		assertEquals("Bobber Fix", toggle.text().title());
		assertTrue(toggle.amber(), "AMBER (P6)");
		assertEquals(unavailable, toggle.status(), "a skipped hook shows on the card (REQ-BOB-08)");
		toggle.binding().set(false);
		assertFalse(config.enabled);
	}

	@Test
	void theFixSendsNothingAndTouchesNoPacket() throws java.io.IOException {
		// AC-BOB-06 [R]: no packet class, no send or cancel; only the hook's tick and its reaction to applied data.
		java.nio.file.Path project = java.nio.file.Path.of(System.getProperty("k8bas.projectDir", "."));
		List<java.nio.file.Path> files = new ArrayList<>(List.of(project.resolve("src/main/java/com/k8bas/skyblockutility/mixin/FishingHookMixin.java")));
		try (var walk = java.nio.file.Files.list(project.resolve("src/main/kotlin/com/k8bas/skyblockutility/module/bobberfix"))) {
			walk.forEach(files::add);
		}
		List<String> hits = new ArrayList<>();
		for (java.nio.file.Path file : files) {
			String code = java.nio.file.Files.readString(file);
			for (String forbidden : List.of("Packet", "send(", "sendCommand", "sendChat", "connection.", "cancel", "CallbackInfo", "@Inject")) {
				if (code.contains(forbidden)) {
					hits.add(file.getFileName() + ": " + forbidden);
				}
			}
		}
		assertEquals(List.of(), hits);
		String mixin = java.nio.file.Files.readString(files.get(0));
		assertEquals(List.of("onSyncedDataUpdated", "tick"), java.util.regex.Pattern.compile("method = \"(\\w+)\\(").matcher(mixin).results()
				.map(match -> match.group(1)).toList(), "only these two methods");
	}

	@Test
	void anIntOverflowIsNoTimerStand() {
		// EC-BOB-01: the id rule is the only signal; a hook at Integer.MAX_VALUE has no id + 1.
		assertFalse(BobberFix.ignoresLink(true, true, true, Integer.MAX_VALUE, true, Integer.MIN_VALUE, true));
	}
}

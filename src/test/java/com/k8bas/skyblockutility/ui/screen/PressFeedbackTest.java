package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-57 (REQ-UI-53, T3.46), on a test clock: a whole-card toggle, a rule header and a toggle switch look pressed while the
 * left button is held on them (their colour mixed 12 % toward black, at once), and ease back over 150 ms after the release
 * or when the pointer leaves them; a disabled one never does; with Animations OFF the look goes at once.
 */
class PressFeedbackTest {
	private final long[] now = {1_000};
	private final boolean[] motion = {true};
	private UiTime previous;

	@BeforeEach
	void enter() {
		previous = UiClock.enter(UiTime.of(() -> now[0], () -> motion[0]));
	}

	@AfterEach
	void leave() {
		UiClock.leave(previous);
	}

	/** The pressed colour: red, green and blue times 0.88 at full press, its alpha kept; none at 0. */
	@Test
	void thePressedColourIsMixed12PercentTowardBlack() {
		assertEquals(0xFF000000 | Math.round(0x30 * 0.88F) << 16 | Math.round(0x35 * 0.88F) << 8 | Math.round(0x3B * 0.88F),
				Widget.pressed(0xFF30353B, 1F));
		assertEquals(0x8030353B, Widget.pressed(0x8030353B, 0F));
		assertEquals(0x80 << 24, Widget.pressed(0x8030353B, 1F) & 0xFF000000, "the alpha is kept");
	}

	@Test
	void aToggleCardARuleHeaderAndASwitchLookPressedWhileHeld() {
		ToggleSwitch toggleSwitch = new ToggleSwitch(toggle().binding(), () -> { }, UiClock.SOURCE);
		toggleSwitch.setBounds(200, 10, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		OptionCard card = new OptionCard(toggle(), toggleSwitch);
		card.setBounds(0, 0, 300, 40);
		RuleHeaderWidget header = new RuleHeaderWidget(rule(), () -> { });
		header.setBounds(0, 50, 300, 24);
		check(card, 20, 20, "the toggle card");
		check(header, 20, 60, "the rule header");
		check(toggleSwitch, 205, 15, "the switch");
	}

	private void check(Widget widget, int x, int y, String what) {
		assertEquals(0F, widget.pressLook(x, y), what + ": not pressed");
		assertTrue(widget.press(x, y, 0), what + " takes the press");
		assertEquals(1F, widget.pressLook(x, y), what + ": pressed at once");
		now[0] += 400;
		assertEquals(1F, widget.pressLook(x, y), what + ": while held");
		widget.release(x, y);
		assertEquals(1F, widget.pressLook(x, y), what + ": 0 ms after the release");
		now[0] += 75;
		float half = widget.pressLook(x, y);
		assertTrue(half > 0F && half < 1F, what + ": 75 ms after: " + half);
		now[0] += 75;
		assertEquals(0F, widget.pressLook(x, y), what + ": 150 ms after");
		// Held, then the pointer leaves it: the look eases away as after a release.
		widget.press(x, y, 0);
		widget.pressLook(x, y);
		assertEquals(1F, widget.pressLook(x, y));
		widget.pressLook(-50, -50);
		now[0] += 75;
		float leaving = widget.pressLook(-50, -50);
		assertTrue(leaving > 0F && leaving < 1F, what + ": easing away 75 ms after the pointer left: " + leaving);
		now[0] += 75;
		assertEquals(0F, widget.pressLook(-50, -50), what + ": gone 150 ms after the pointer left");
		widget.release(-50, -50);
	}

	@Test
	void aDisabledControlNeverLooksPressed() {
		ToggleSwitch toggleSwitch = new ToggleSwitch(toggle().binding(), () -> { }, UiClock.SOURCE);
		toggleSwitch.setBounds(200, 10, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		toggleSwitch.setEnabled(false);
		toggleSwitch.press(205, 15, 0);
		assertEquals(0F, toggleSwitch.pressLook(205, 15));
		OptionCard card = new OptionCard(toggle(), toggleSwitch);
		card.setBounds(0, 0, 300, 40);
		card.setEnabled(false);
		card.press(20, 20, 0);
		assertEquals(0F, card.pressLook(20, 20));
		// Held while enabled, then disabled: the look goes as after a release.
		RuleHeaderWidget header = new RuleHeaderWidget(rule(), () -> { });
		header.setBounds(0, 50, 300, 24);
		header.press(20, 60, 0);
		assertEquals(1F, header.pressLook(20, 60));
		header.setEnabled(false);
		// The next frame draws it disabled; its look eases away from there.
		header.pressLook(20, 60);
		now[0] += 150;
		assertEquals(0F, header.pressLook(20, 60), "disabled during the hold");
	}

	@Test
	void withAnimationsOffTheLookGoesAtOnce() {
		motion[0] = false;
		RuleHeaderWidget header = new RuleHeaderWidget(rule(), () -> { });
		header.setBounds(0, 50, 300, 24);
		header.press(20, 60, 0);
		assertEquals(1F, header.pressLook(20, 60));
		header.release(20, 60);
		assertEquals(0F, header.pressLook(20, 60), "0 ms after the release");
	}

	private static List<Option> options() {
		return OptionCatalog.build(new GeneralConfig(), new MobHighlighterConfig(), new NpcSearchConfig(), enabled -> { }, enabled -> { })
				.stream().flatMap(card -> card.options().stream()).toList();
	}

	private static Toggle toggle() {
		return options().stream().filter(Toggle.class::isInstance).map(Toggle.class::cast).findFirst().orElseThrow();
	}

	private static RuleGroup rule() {
		MobHighlighterConfig mobs = new MobHighlighterConfig();
		com.k8bas.skyblockutility.highlight.HighlightRule zealot = new com.k8bas.skyblockutility.highlight.HighlightRule();
		zealot.id = "mob-1";
		zealot.label = "Zealot";
		zealot.namePattern = "Zealot";
		mobs.rules.add(zealot);
		return new com.k8bas.skyblockutility.module.mobhighlighter.MobRuleCards(mobs, rule -> null, () -> { }).groups().get(0);
	}
}

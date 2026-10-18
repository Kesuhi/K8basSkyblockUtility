package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.update.UpdateChannel;
import com.k8bas.skyblockutility.update.UpdatesPanel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.4b (REQ-UPD-07, REQ-UPD-10, REQ-UPD-19, REQ-XC-TOGGLE-02): the General › Updates card. T1.4's toggle stays its
 * header with no new default; it shows the version, the latest known one, the last check and a status, the channel, and
 * the buttons Check now, Install, Open release page and Cancel pending update. The channel is written only on a choice.
 */
class UpdatesCardOptionsTest {
	private final List<String> calls = new ArrayList<>();

	private final UpdatesPanel panel = new UpdatesPanel() {
		@Override
		public String version() {
			return "v1.3.1";
		}

		@Override
		public String latest() {
			return "v1.4.0 (skipped)";
		}

		@Override
		public String lastCheck() {
			return "Today, 15:30";
		}

		@Override
		public String status() {
			return "v1.4.0 is available (skipped)";
		}

		@Override
		public UpdateChannel defaultChannel() {
			return UpdateChannel.BETA;
		}

		@Override
		public void checkNow() {
			calls.add("check");
		}

		@Override
		public void install() {
			calls.add("install");
		}

		@Override
		public void openReleasePage() {
			calls.add("page");
		}

		@Override
		public void cancelPending() {
			calls.add("cancel");
		}

		@Override
		public void channelChanged() {
			calls.add("channel");
		}
	};

	private Card card(GeneralConfig config) {
		return GeneralOptions.cards(config, () -> { }, panel).stream().filter(card -> card.id().equals("updates")).findFirst().orElseThrow();
	}

	private static Option option(Card card, String id) {
		return card.options().stream().filter(option -> option.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void theCardsRowsInOrder() {
		Card card = card(new GeneralConfig());
		assertEquals("updates.check", card.toggle().id(), "T1.4's toggle stays the card's switch");
		assertEquals(true, card.toggle().defaultValue(), "no new default (REQ-XC-TOGGLE-02)");
		assertEquals(List.of("updates.version", "updates.latest", "updates.last_check", "updates.status", "updates.channel", "updates.check_now",
				"updates.install", "updates.release_page", "updates.cancel"), card.options().stream().map(Option::id).toList());
		assertEquals("v1.3.1", ((InfoOption) option(card, "updates.version")).value().get());
		assertEquals("v1.4.0 (skipped)", ((InfoOption) option(card, "updates.latest")).value().get(), "AC-UPD-13 [C]: the card still lists it");
		assertEquals("Today, 15:30", ((InfoOption) option(card, "updates.last_check")).value().get());
		InfoOption status = (InfoOption) option(card, "updates.status");
		assertEquals("v1.4.0 is available (skipped)", status.value().get());
		assertTrue(status.wide(), "the status takes the card's width, so a check's text fits");
		assertFalse(((InfoOption) option(card, "updates.version")).wide());
	}

	/** REQ-UPD-07: an untouched channel reads as its default and is not written; a choice is. */
	@Test
	void theChannelIsWrittenOnlyOnAChoice() {
		GeneralConfig config = new GeneralConfig();
		@SuppressWarnings("unchecked")
		Choice<UpdateChannel> channel = (Choice<UpdateChannel>) option(card(config), "updates.channel");
		assertEquals(UpdateChannel.BETA, channel.defaultValue(), "the default follows the build (REQ-UPD-07)");
		assertEquals(UpdateChannel.BETA, channel.binding().get());
		assertNull(config.updateChannel, "reading wrote nothing");
		assertEquals(List.of(), calls);
		channel.binding().set(UpdateChannel.STABLE);
		assertEquals(UpdateChannel.STABLE, config.updateChannel);
		assertEquals(List.of("channel"), calls, "a pick has the card read the offer again");
		assertEquals(UpdateChannel.STABLE, channel.binding().get());
		assertEquals(List.of(UpdateChannel.STABLE, UpdateChannel.BETA), channel.values());
	}

	/** REQ-UPD-10: the four buttons; cancelling is the destructive one. */
	@Test
	void theFourButtons() {
		Card card = card(new GeneralConfig());
		for (String id : List.of("updates.check_now", "updates.install", "updates.release_page", "updates.cancel")) {
			((ActionOption) option(card, id)).action().run();
		}
		assertEquals(List.of("check", "install", "page", "cancel"), calls);
		assertEquals(List.of("Check now", "Install", "Open", "Cancel"), card.options().stream().filter(option -> option instanceof ActionOption)
				.map(option -> ((ActionOption) option).buttonLabel()).toList());
		assertTrue(((ActionOption) option(card, "updates.cancel")).destructive());
		assertEquals("Open release page", option(card, "updates.release_page").text().title());
		assertEquals("Cancel pending update", option(card, "updates.cancel").text().title());
	}

	/** The toggle's text says what it does now: notices, and an install only after a click. */
	@Test
	void theToggleSaysAnInstallNeedsAClick() {
		Card card = card(new GeneralConfig());
		assertEquals("Check for updates (notify)", card.toggle().text().title());
		assertTrue(card.toggle().text().description().contains("confirm"), card.toggle().text().description());
		assertTrue(card.toggle().text().tooltip().contains("first server join"), card.toggle().text().tooltip());
	}
}

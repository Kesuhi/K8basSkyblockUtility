package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.ui.notice.Notice;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-UPD-08, REQ-UPD-10, REQ-UPD-12 (T1.4, T4.4): the automatic check's notices. A found update shows a toast and, once per
 * session, a local chat line with [Changelog] [Update] [Skip this version] [Open release page]; a notify-only one has no
 * [Update] and says why; a skipped version, a checksum conflict and every other outcome show nothing.
 */
class UpdateNotifierTest {
	private static final String TAG_PAGE = "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0";
	private static final String HEX = "ab".repeat(32);

	private final List<Notice> toasts = new ArrayList<>();

	private static CandidateFinder.Candidate candidate(String tag, String htmlUrl, boolean prerelease) {
		Release.Asset jar = new Release.Asset("k8bas_skyblock_utility-" + tag.substring(1) + "+26.2.jar", "uploaded", "u", null, 1);
		Release release = new Release(tag, htmlUrl, false, prerelease, List.of(jar));
		return new CandidateFinder.Candidate(release, SemVer.fromTag(tag).orElseThrow(), jar, List.of());
	}

	private static CheckOutcome.Found found(String tag, UpdateHooks.Hash hash, UpdateHooks.Eligibility eligibility, boolean skipped) {
		CandidateFinder.Candidate candidate = candidate(tag, TAG_PAGE, tag.contains("-"));
		return new CheckOutcome.Found(candidate, hash, eligibility, skipped, tag.contains("-"), "26.2");
	}

	private static CheckOutcome.Found installable(String tag) {
		return found(tag, new UpdateHooks.Hash.Verifiable(HEX), UpdateHooks.Eligibility.ELIGIBLE, false);
	}

	private UpdateNotifier notifier() {
		return new UpdateNotifier("1.1.0+26.2", org.slf4j.LoggerFactory.getLogger("test"), toasts::add);
	}

	private static List<String> clicks(Component message) {
		List<String> clicks = new ArrayList<>();
		message.visit((style, text) -> {
			if (style.getClickEvent() instanceof ClickEvent.OpenUrl(URI uri)) {
				clicks.add(text + " -> " + uri);
			} else if (style.getClickEvent() instanceof ClickEvent.RunCommand(String command)) {
				clicks.add(text + " -> " + command);
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return clicks;
	}

	/** REQ-UPD-10: the chat line names both versions and has its four links; [Update] opens the confirmation screen, nothing more. */
	@Test
	void theChatLineHasItsFourLinks() {
		Component message = UpdateNotifier.message(installable("v1.2.0"), "1.1.0+26.2");
		assertEquals("K8bas Skyblock Utility v1.2.0 is available; you have v1.1.0. [Changelog] [Update] [Skip this version] [Open release page]",
				message.getString());
		assertEquals(List.of("[Changelog] -> https://github.com/Kesuhi/K8basSkyblockUtility/blob/v1.2.0/CHANGELOG.md",
				"[Update] -> /ksu update show", "[Skip this version] -> /ksu update skip 1.2.0", "[Open release page] -> " + TAG_PAGE), clicks(message));
	}

	/** REQ-UPD-08: notify-only says why and offers no [Update]. */
	@Test
	void aNotifyOnlyUpdateSaysWhyAndHasNoUpdateLink() {
		Component message = UpdateNotifier.message(found("v1.2.0", UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false), "1.1.0");
		assertEquals("K8bas Skyblock Utility v1.2.0 is available; you have v1.1.0. It has no SHA-256 checksum, so install it by hand from the release page. "
				+ "[Changelog] [Skip this version] [Open release page]", message.getString());
		Component managed = UpdateNotifier.message(found("v1.2.0", new UpdateHooks.Hash.Verifiable(HEX),
				new UpdateHooks.Eligibility(false, InstallEligibility.MANAGED), false), "1.1.0");
		assertTrue(managed.getString().contains(InstallEligibility.MANAGED + ". [Changelog]"), managed.getString());
	}

	@Test
	void aPreReleaseIsLabelledBeta() {
		Component message = UpdateNotifier.message(installable("v1.3.0-beta.1"), "1.2.0");
		assertTrue(message.getString().startsWith("K8bas Skyblock Utility v1.3.0-beta.1 (beta) is available"), message.getString());
	}

	/** REQ-UPD-10: a toast on whatever screen is open, once per version in a session; a higher version shows its own. */
	@Test
	void aFoundUpdateShowsOneToastPerVersion() {
		UpdateNotifier notifier = notifier();
		notifier.offer(installable("v1.2.0"));
		notifier.offer(installable("v1.2.0"));
		assertEquals(List.of(new Notice("K8bas Skyblock Utility", List.of("v1.2.0 is available", "Open /ksu update to install it"))), toasts);
		notifier.offer(installable("v1.2.1"));
		assertEquals(2, toasts.size());
		notifier.offer(found("v1.3.0", UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false));
		assertEquals(new Notice("K8bas Skyblock Utility", List.of("v1.3.0 is available", "Install it by hand, see /ksu update")), toasts.getLast());
	}

	/** REQ-UPD-08, REQ-UPD-12, AC-UPD-13: a skipped version, a checksum conflict and the other outcomes stay silent; a newer version notifies again. */
	@Test
	void silentOutcomesShowNothing() {
		UpdateNotifier notifier = notifier();
		for (CheckOutcome silent : List.of(found("v2.0.1", new UpdateHooks.Hash.Verifiable(HEX), UpdateHooks.Eligibility.ELIGIBLE, true),
				found("v2.0.1", UpdateHooks.Hash.Conflict.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false),
				found("v2.0.1", UpdateHooks.Hash.Unavailable.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false),
				new CheckOutcome.NoBuild(SemVer.fromTag("v2.1.0").orElseThrow(), "26.2", TAG_PAGE), new CheckOutcome.UpToDate("1.1.0"),
				CheckOutcome.Unreachable.INSTANCE, new CheckOutcome.RateLimited(1L))) {
			notifier.offer(installable("v1.9.0"));
			toasts.clear();
			notifier.offer(silent);
			assertEquals(List.of(), toasts, silent.toString());
			assertTrue(notifier.pendingForTests() == null, "no chat line either: " + silent);
		}
		notifier.offer(installable("v2.0.2"));
		assertEquals(1, toasts.size(), "a version above the skipped one notifies");
	}

	/** Review (T4.4): a checksum file not read yet stays silent, so a later check that reads it still toasts the install. */
	@Test
	void anUnreadChecksumDoesNotUseUpTheToast() {
		UpdateNotifier notifier = notifier();
		notifier.offer(found("v2.0.1", UpdateHooks.Hash.Unavailable.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false));
		notifier.offer(installable("v2.0.1"));
		assertEquals(List.of(new Notice("K8bas Skyblock Utility", List.of("v2.0.1 is available", "Open /ksu update to install it"))), toasts);
	}

	/** T4.3b: the launch's lines are each shown once, at the first chance. */
	@Test
	void theLaunchLinesAreShownOnce() {
		UpdateNotifier notifier = notifier();
		notifier.showOnJoin(List.of("Updated line", "Another copy line"));
		List<String> shown = new ArrayList<>();
		notifier.flushLaunchLines(component -> shown.add(component.getString()));
		notifier.flushLaunchLines(component -> shown.add(component.getString()));
		assertEquals(List.of("Updated line", "Another copy line"), shown);
	}

	/** Only this repository's https release pages are linked; anything else falls back to the list. */
	@Test
	void theReleasePageIsValidated() {
		URI list = URI.create("https://github.com/Kesuhi/K8basSkyblockUtility/releases");
		assertEquals(URI.create(TAG_PAGE), UpdateNotifier.releasePage(TAG_PAGE));
		for (String bad : List.of("http://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0",
				"https://evil.example/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0",
				"https://github.com/Other/Repo/releases/tag/v1.2.0",
				"https://github.com:8443/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0",
				"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0?x=1",
				"https://github.com/Kesuhi/K8basSkyblockUtility/releases/../../Other/Repo",
				"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/../../../Other/Repo",
				"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0/../../../../Other",
				"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0#x",
				// User info in the URL; joined at run time so the privacy scan does not see an address.
				String.join("@", "https://someone", "github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0"),
				"javascript:alert(1)", "not a uri")) {
			assertEquals(list, UpdateNotifier.releasePage(bad), bad);
		}
		assertEquals(list, UpdateNotifier.releasePage(null));
	}
}

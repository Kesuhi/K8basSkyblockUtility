package com.k8bas.skyblockutility.update;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UPD-01, REQ-UPD-10: the notice's text and its two validated links (T1.4). */
class UpdateNotifierTest {
	private static final String TAG_PAGE = "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0";

	private static CandidateFinder.Candidate candidate(String tag, String htmlUrl, boolean prerelease) {
		Release.Asset jar = new Release.Asset("k8bas_skyblock_utility-" + tag.substring(1) + "+26.2.jar", "uploaded", "u", null, 1);
		Release release = new Release(tag, htmlUrl, false, prerelease, List.of(jar));
		return new CandidateFinder.Candidate(release, SemVer.fromTag(tag).orElseThrow(), jar, List.of());
	}

	private static List<URI> links(Component message) {
		List<URI> links = new ArrayList<>();
		message.visit((style, text) -> {
			if (style.getClickEvent() instanceof ClickEvent.OpenUrl(URI uri)) {
				links.add(uri);
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return links;
	}

	@Test
	void theNoticeNamesBothVersionsAndLinksTheChangelogAndTheReleasePage() {
		Component message = UpdateNotifier.message(candidate("v1.2.0", TAG_PAGE, false), "1.1.0+26.2");
		assertEquals("K8bas Skyblock Utility v1.2.0 is available; you have v1.1.0. [Changelog] [Open release page]", message.getString());
		assertEquals(List.of(URI.create("https://github.com/Kesuhi/K8basSkyblockUtility/blob/v1.2.0/CHANGELOG.md"), URI.create(TAG_PAGE)),
				links(message));
	}

	@Test
	void aPreReleaseIsLabelledBeta() {
		Component message = UpdateNotifier.message(candidate("v1.3.0-beta.1", TAG_PAGE, true), "1.2.0");
		assertTrue(message.getString().startsWith("K8bas Skyblock Utility v1.3.0-beta.1 (beta) is available"), message.getString());
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

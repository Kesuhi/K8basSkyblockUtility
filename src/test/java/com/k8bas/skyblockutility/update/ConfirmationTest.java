package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T4.4 (REQ-UPD-11, AC-UPD-11 fields): what the confirmation screen shows. */
class ConfirmationTest {
	private static final String HEX = "0123456789abcdef".repeat(4);

	private static CheckOutcome.Found found(String tag, long size, String body, UpdateHooks.Hash hash, UpdateHooks.Eligibility eligibility, boolean prerelease) {
		Release.Asset jar = new Release.Asset("k8bas_skyblock_utility-" + tag.substring(1) + "+26.2.jar", "uploaded", "u", null, size);
		Release release = new Release(tag, "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/" + tag, false, prerelease, List.of(jar), body);
		CandidateFinder.Candidate candidate = new CandidateFinder.Candidate(release, SemVer.fromTag(tag).orElseThrow(), jar, List.of());
		return new CheckOutcome.Found(candidate, hash, eligibility, false, prerelease, "26.2");
	}

	/** REQ-UPD-11: running → new version and the channel, the asset name and size, the first 12 hex of the SHA-256, the notes as plain text. */
	@Test
	void everyFieldOfTheConfirmation() {
		Confirmation confirmation = Confirmation.of(found("v2.0.1", 1_234_567, "## Fixed\n- **A** fix §cnow", new UpdateHooks.Hash.Verifiable(HEX),
				UpdateHooks.Eligibility.ELIGIBLE, false), "2.0.0+26.2", null);
		assertEquals("v2.0.0 → v2.0.1", confirmation.getVersions());
		assertEquals("Stable", confirmation.getChannel());
		assertEquals("k8bas_skyblock_utility-2.0.1+26.2.jar", confirmation.getAsset());
		assertEquals("1.2 MB", confirmation.getSize());
		assertEquals("0123456789ab", confirmation.getChecksum());
		assertEquals("Fixed\n• A fix now", confirmation.getNotes());
		assertTrue(confirmation.getInstallOffered());
		assertNull(confirmation.getReason());
		assertEquals(new Consent("2.0.1", HEX), confirmation.consent(), "the click carries the version and the checksum shown");
		assertEquals(URI.create("https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v2.0.1"), confirmation.getReleasePage());
	}

	/** The notes are wrapped to the box: at spaces, an over-long word by characters, blank lines kept. */
	@Test
	void notesWrapToTheBox() {
		java.util.function.ToIntFunction<String> chars = String::length;
		assertEquals(List.of("one two", "three", "", "abcdefg", "hijk"), Confirmation.wrap("one two three\n\nabcdefghijk", 7, chars));
		assertEquals(List.of("a", "b", "c"), Confirmation.wrap("abc", 0, chars), "a box too narrow still takes one character per line");
	}

	/** Review (T4.4): one 4,000-character token wraps at once, and never splits a character made of two chars. */
	@Test
	void aLongTokenWrapsQuicklyAndWhole() {
		java.util.concurrent.atomic.AtomicLong measured = new java.util.concurrent.atomic.AtomicLong();
		java.util.function.ToIntFunction<String> counting = text -> {
			measured.addAndGet(text.length());
			return text.codePointCount(0, text.length());
		};
		List<String> lines = Confirmation.wrap("x".repeat(4_000), 50, counting);
		assertEquals(80, lines.size());
		assertTrue(measured.get() < 50_000, "linear: " + measured.get() + " characters measured");
		List<String> emoji = Confirmation.wrap("😀".repeat(5), 2, counting);
		assertEquals(List.of("😀😀", "😀😀", "😀"), emoji);
	}

	@Test
	void sizesReadAsPeopleReadThem() {
		assertEquals("950 bytes", Confirmation.size(950));
		assertEquals("12 KB", Confirmation.size(12_300));
		assertEquals("1.2 MB", Confirmation.size(1_234_567));
	}

	/** A beta, a notify-only update, and no notes. */
	@Test
	void betaNotifyOnlyAndEmptyNotes() {
		Confirmation beta = Confirmation.of(found("v2.1.0-beta.1", 10, null, UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, true),
				"2.0.0", UpdateChannel.BETA);
		assertEquals("Beta", beta.getChannel());
		assertFalse(beta.getInstallOffered());
		assertNull(beta.getChecksum());
		assertEquals("It has no SHA-256 checksum, so install it by hand from the release page", beta.getReason());
		assertEquals("No release notes", beta.getNotes());
		assertNull(beta.consent(), "nothing to confirm");
	}
}

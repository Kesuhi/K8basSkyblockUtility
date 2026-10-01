package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UPD-05 to AC-UPD-08, EC-UPD-01, EC-UPD-09 [A] (T1.4b). */
class CandidateFinderTest {
	private static final String PREFIX = "k8bas_skyblock_utility-";

	private static Release.Asset jar(String name) {
		return new Release.Asset(name, "uploaded", "https://github.com/x/" + name, "sha256:" + "0".repeat(64), 1000);
	}

	private static Release release(String tag, boolean prerelease, Release.Asset... assets) {
		return new Release(tag, "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/" + tag, false, prerelease, List.of(assets));
	}

	private static Release release(String tag, Release.Asset... assets) {
		return release(tag, false, assets);
	}

	private static SemVer v(String text) {
		return SemVer.parse(text).orElseThrow();
	}

	@Test
	void theJarPatternAcceptsAndRejects() {
		SemVer tag = v("2.0.0");
		assertTrue(AssetSelector.select(tag, List.of(jar(PREFIX + "2.0.0+26.2.jar")), "26.2").isPresent());
		assertTrue(AssetSelector.select(v("2.1.0-beta.1"), List.of(jar(PREFIX + "2.1.0-beta.1+26.2.jar")), "26.2").isPresent());
		for (String rejected : List.of(PREFIX + "2.0.0+26.2-sources.jar", PREFIX + "2.0.0%2B26.2.jar", PREFIX + "v2.0.0+26.2.jar",
				PREFIX + "2.0.0.jar", PREFIX + "2.0.1+26.2.jar", "other-2.0.0+26.2.jar", PREFIX + "2.0.0+26.2.jar.sha256")) {
			assertEquals(Optional.empty(), AssetSelector.select(tag, List.of(jar(rejected)), "26.2"), rejected);
		}
		// EC-UPD-01: a jar still uploading is ignored for now.
		Release.Asset open = new Release.Asset(PREFIX + "2.0.0+26.2.jar", "open", "u", null, 0);
		assertEquals(Optional.empty(), AssetSelector.select(tag, List.of(open), "26.2"));
	}

	@Test
	void anExactMinecraftMatchBeatsAPrefix() {
		Release.Asset prefix = jar(PREFIX + "2.0.0+26.2.jar");
		Release.Asset exact = jar(PREFIX + "2.0.0+26.2.1.jar");
		assertEquals(exact, AssetSelector.select(v("2.0.0"), List.of(prefix, exact), "26.2.1").orElseThrow());
		assertEquals(prefix, AssetSelector.select(v("2.0.0"), List.of(prefix), "26.2.1").orElseThrow());
		assertEquals(Optional.empty(), AssetSelector.select(v("2.0.0"), List.of(exact), "26.2"));
		assertEquals(Optional.empty(), AssetSelector.select(v("2.0.0"), List.of(jar(PREFIX + "2.0.0+26.20.jar")), "26.2"));
	}

	@Test
	void aNewerReleaseIsACandidate() {
		CandidateFinder.Outcome outcome = CandidateFinder.find(List.of(release("v1.1.0", jar(PREFIX + "1.1.0+26.2.jar"))),
				"1.0.1", "26.2", null, line -> { });
		assertEquals(v("1.1.0"), assertInstanceOf(CandidateFinder.Candidate.class, outcome).version());
	}

	@Test
	void equalAndOlderVersionsAreNotOffered() {
		List<Release> same = List.of(release("v1.1.0", jar(PREFIX + "1.1.0+26.2.jar")));
		assertInstanceOf(CandidateFinder.UpToDate.class, CandidateFinder.find(same, "1.1.0+26.1.2", "26.2", null, line -> { }));
		List<Release> older = List.of(release("v1.9.9", jar(PREFIX + "1.9.9+26.2.jar")));
		assertInstanceOf(CandidateFinder.UpToDate.class, CandidateFinder.find(older, "2.0.0", "26.2", null, line -> { }));
	}

	@Test
	void anUnparsableTagIsSkippedAndLogged() {
		List<String> log = new ArrayList<>();
		CandidateFinder.Outcome outcome = CandidateFinder.find(List.of(release("vfoo", jar(PREFIX + "9.0.0+26.2.jar"))),
				"1.0.1", "26.2", null, log::add);
		assertInstanceOf(CandidateFinder.UpToDate.class, outcome);
		assertEquals(1, log.size());
		assertTrue(log.getFirst().contains("vfoo"));
	}

	@Test
	void aRunningVersionThatIsNotSemVerIsOfferedNothing() {
		CandidateFinder.Outcome outcome = CandidateFinder.find(List.of(release("v1.1.0", jar(PREFIX + "1.1.0+26.2.jar"))),
				"1.0", "26.2", null, line -> { });
		assertEquals(v("1.1.0"), assertInstanceOf(CandidateFinder.RunningNotSemVer.class, outcome).newest());
		assertNull(assertInstanceOf(CandidateFinder.RunningNotSemVer.class,
				CandidateFinder.find(List.of(), "dev", "26.2", null, line -> { })).newest());
	}

	/** AC-UPD-07: the newest release without a jar for this MC is skipped (one log line); an older newer one is offered. */
	@Test
	void theNewestReleaseWithoutAJarIsSkipped() {
		List<String> log = new ArrayList<>();
		List<Release> releases = List.of(release("v2.0.1", jar(PREFIX + "2.0.1+26.2.jar")), release("v2.1.0", jar(PREFIX + "2.1.0+26.3.jar")));
		CandidateFinder.Candidate candidate = assertInstanceOf(CandidateFinder.Candidate.class,
				CandidateFinder.find(releases, "2.0.0", "26.2", null, log::add));
		assertEquals(v("2.0.1"), candidate.version());
		assertEquals(List.of(v("2.1.0")), candidate.skippedWithoutJar());
		assertEquals(List.of("v2.1.0 has no build for Minecraft 26.2"), log);

		CandidateFinder.NoJar noJar = assertInstanceOf(CandidateFinder.NoJar.class,
				CandidateFinder.find(List.of(release("v2.1.0", jar(PREFIX + "2.1.0+26.3.jar"))), "2.0.0", "26.2", null, line -> { }));
		assertEquals(v("2.1.0"), noJar.newest());
	}

	/** AC-UPD-08. */
	@Test
	void channels() {
		Release flagged = release("v2.2.0", true, jar(PREFIX + "2.2.0+26.2.jar"));
		Release rc = release("v2.3.0-rc.1", false, jar(PREFIX + "2.3.0-rc.1+26.2.jar"));
		Release stable = release("v2.1.0", jar(PREFIX + "2.1.0+26.2.jar"));
		List<Release> releases = List.of(flagged, rc, stable);

		assertEquals(v("2.1.0"), ((CandidateFinder.Candidate) CandidateFinder.find(releases, "2.0.0", "26.2", null, l -> { })).version());
		assertEquals(v("2.1.0"), ((CandidateFinder.Candidate) CandidateFinder.find(releases, "2.0.0", "26.2", UpdateChannel.STABLE, l -> { })).version());
		assertEquals(v("2.3.0-rc.1"), ((CandidateFinder.Candidate) CandidateFinder.find(releases, "2.0.0", "26.2", UpdateChannel.BETA, l -> { })).version());

		// A pre-release build defaults to BETA and is offered a stable release; a stored STABLE stays STABLE.
		assertEquals(UpdateChannel.BETA, UpdateChannel.effective(null, v("2.1.0-beta.1")));
		assertEquals(UpdateChannel.STABLE, UpdateChannel.effective(UpdateChannel.STABLE, v("2.1.0-beta.1")));
		assertEquals(v("2.1.0"), ((CandidateFinder.Candidate) CandidateFinder.find(List.of(stable), "2.1.0-beta.1", "26.2", null, l -> { })).version());
		// A stable 1.2.0 beats 1.2.0-beta.3 on BETA.
		List<Release> both = List.of(release("v1.2.0-beta.3", true, jar(PREFIX + "1.2.0-beta.3+26.2.jar")), release("v1.2.0", jar(PREFIX + "1.2.0+26.2.jar")));
		assertEquals(v("1.2.0"), ((CandidateFinder.Candidate) CandidateFinder.find(both, "1.1.0", "26.2", UpdateChannel.BETA, l -> { })).version());
	}

	/** EC-UPD-09 and drafts. */
	@Test
	void listOrderDoesNotMatterAndDraftsAreIgnored() {
		Release draft = new Release("v9.0.0", "u", true, false, List.of(jar(PREFIX + "9.0.0+26.2.jar")));
		List<Release> shuffled = List.of(release("v1.2.0", jar(PREFIX + "1.2.0+26.2.jar")), draft,
				release("v1.4.0", jar(PREFIX + "1.4.0+26.2.jar")), release("v1.3.0", jar(PREFIX + "1.3.0+26.2.jar")));
		assertEquals(v("1.4.0"), ((CandidateFinder.Candidate) CandidateFinder.find(shuffled, "1.1.0", "26.2", null, l -> { })).version());
	}
}

package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UPD-05 [A] (T1.4b). */
class SemVerTest {
	private static SemVer v(String text) {
		return SemVer.parse(text).orElseThrow();
	}

	@Test
	void aTagLosesOneLeadingV() {
		assertEquals(v("1.1.0"), SemVer.fromTag("v1.1.0").orElseThrow());
		assertEquals(v("1.1.0"), SemVer.fromTag("1.1.0").orElseThrow());
		assertEquals(Optional.empty(), SemVer.fromTag("vv1.1.0"));
		assertEquals(Optional.empty(), SemVer.fromTag("vfoo"));
	}

	@Test
	void newerOlderAndEqual() {
		assertTrue(SemVer.fromTag("v1.1.0").orElseThrow().compareTo(v("1.0.1")) > 0);
		assertEquals(0, v("1.1.0+26.2").compareTo(v("1.1.0+26.1.2")));
		assertEquals(v("1.1.0+26.2"), v("1.1.0+26.1.2"));
		assertTrue(v("1.9.9").compareTo(v("2.0.0")) < 0);
	}

	@Test
	void parsingIsStrict() {
		for (String bad : List.of("1.0", "1", "01.0.0", "1.0.0-", "1.0.0-01", "1.0.0+", "1.0.0 ", " 1.0.0", "1.0.0-a..b", "99999999999.0.0")) {
			assertEquals(Optional.empty(), SemVer.parse(bad), bad);
		}
		assertTrue(SemVer.parse("1.0.0-0.3.7").isPresent());
		assertTrue(SemVer.parse("1.0.0-x-y-z.--").isPresent());
		assertTrue(SemVer.parse("2.1.0-beta.1+26.2").isPresent());
	}

	/** The precedence example from semver.org §11. */
	@Test
	void preReleasesOrderAsTheSpecSays() {
		List<String> ascending = List.of("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
				"1.0.0-beta.11", "1.0.0-rc.1", "1.0.0");
		for (int i = 1; i < ascending.size(); i++) {
			assertTrue(v(ascending.get(i - 1)).compareTo(v(ascending.get(i))) < 0, ascending.get(i - 1) + " < " + ascending.get(i));
		}
		assertTrue(v("1.2.0").compareTo(v("1.2.0-beta.3")) > 0);
		assertTrue(v("2.1.0-beta.1").isPreRelease());
		assertFalse(v("2.1.0+26.2").isPreRelease());
		assertEquals("2.1.0-beta.1", v("2.1.0-beta.1+26.2").toString());
	}
}

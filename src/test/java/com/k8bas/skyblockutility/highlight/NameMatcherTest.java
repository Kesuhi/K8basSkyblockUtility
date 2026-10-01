package com.k8bas.skyblockutility.highlight;

import org.junit.jupiter.api.Test;

import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization of the 1.0.1 name matching (AC-GLOW-01): these results must stay identical after the port. */
class NameMatcherTest {
	private static final String ZEALOT = "[Lv55] Zealot 13k/13k❤";

	private static CompiledRule rule(NameMatchMode mode, String pattern) {
		HighlightRule rule = new HighlightRule();
		rule.nameMatchMode = mode;
		rule.namePattern = pattern;
		return new CompiledRule(rule);
	}

	@Test
	void noneMatchesEveryNameAndNeedsNoLookup() {
		CompiledRule none = rule(NameMatchMode.NONE, "");
		assertFalse(NameMatcher.needsName(none));
		assertTrue(NameMatcher.matches(none, ""));
		assertTrue(NameMatcher.matches(none, ZEALOT));
	}

	@Test
	void containsIsACaseSensitiveSubstringTest() {
		CompiledRule contains = rule(NameMatchMode.CONTAINS, "Zealot");
		assertTrue(NameMatcher.needsName(contains));
		assertTrue(NameMatcher.matches(contains, ZEALOT));
		assertFalse(NameMatcher.matches(contains, "[Lv55] zealot 13k/13k❤"));
		assertFalse(NameMatcher.matches(contains, "Enderman"));
	}

	/** Changed on purpose by T1.8b (REQ-GLOW-10): in 1.0.1 an empty CONTAINS pattern matched every name. */
	@Test
	void anEmptyPatternNeverMatches() {
		for (NameMatchMode mode : new NameMatchMode[]{NameMatchMode.CONTAINS, NameMatchMode.EXACT}) {
			CompiledRule empty = rule(mode, "");
			assertFalse(NameMatcher.needsName(empty), mode.name());
			assertFalse(NameMatcher.matches(empty, ZEALOT), mode.name());
			assertFalse(NameMatcher.matches(empty, ""), mode.name());
		}
	}

	@Test
	void exactNeedsTheWholeName() {
		CompiledRule exact = rule(NameMatchMode.EXACT, "Trinity");
		assertTrue(NameMatcher.matches(exact, "Trinity"));
		assertFalse(NameMatcher.matches(exact, "Trinity "));
		assertFalse(NameMatcher.matches(exact, "[NPC] Trinity"));
	}

	@Test
	void regexFindsAMatchAnywhereUnlessAnchored() {
		assertTrue(NameMatcher.matches(rule(NameMatchMode.REGEX, "Zea.ot"), ZEALOT));
		assertFalse(NameMatcher.matches(rule(NameMatchMode.REGEX, "^Zealot$"), ZEALOT));
		assertTrue(NameMatcher.matches(rule(NameMatchMode.REGEX, "\\d+k/\\d+k"), ZEALOT));
	}

	@Test
	void missingPatternsNeverMatchAndNeedNoLookup() {
		for (NameMatchMode mode : new NameMatchMode[]{NameMatchMode.CONTAINS, NameMatchMode.EXACT, NameMatchMode.REGEX}) {
			CompiledRule nullPattern = rule(mode, null);
			assertFalse(NameMatcher.needsName(nullPattern), mode.name());
			assertFalse(NameMatcher.matches(nullPattern, ZEALOT), mode.name());
		}
		CompiledRule emptyRegex = rule(NameMatchMode.REGEX, "");
		assertFalse(NameMatcher.needsName(emptyRegex));
		assertFalse(NameMatcher.matches(emptyRegex, ZEALOT));
	}

	@Test
	void invalidRegexFailsToCompile() {
		assertThrows(PatternSyntaxException.class, () -> rule(NameMatchMode.REGEX, "(["));
	}

	@Test
	void stripsLowercaseColorAndFormatCodes() {
		assertEquals("[Lv55] Zealot 13k/13k❤", NameMatcher.stripColorCodes("§8[§7Lv55§8] §c§lZealot§r §a13k§f/§a13k§c❤"));
		assertEquals("Trinity", NameMatcher.stripColorCodes("§e§k§r§eTrinity"));
	}

	@Test
	void keepsUppercaseAndUnknownCodes() {
		assertEquals("§CZealot", NameMatcher.stripColorCodes("§CZealot"));
		assertEquals("§xZealot", NameMatcher.stripColorCodes("§xZealot"));
	}

	@Test
	void returnsTheSameStringWhenThereIsNoCode() {
		String plain = "Zealot";
		assertSame(plain, NameMatcher.stripColorCodes(plain));
	}
}

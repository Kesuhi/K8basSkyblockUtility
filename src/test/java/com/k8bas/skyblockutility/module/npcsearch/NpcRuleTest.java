package com.k8bas.skyblockutility.module.npcsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Review S-4 (G1): a hand-edited rule with null text fields is repaired on load. */
class NpcRuleTest {
	@Test
	void nullTextFieldsAreRepaired() {
		NpcRule rule = new NpcRule();
		rule.label = null;
		rule.namePattern = null;
		assertTrue(rule.normalize());
		assertEquals("New NPC", rule.label);
		assertEquals("", rule.namePattern);
		assertFalse(rule.normalize(), "a repaired rule needs no further change");
	}
}

package com.k8bas.skyblockutility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R50 (REQ-UPD-04): the game's wiring starts the automatic update check only through SessionChecks, once when the client
 * has started and once at the first server join; the client tick only shows notices. SessionChecksTest covers the class;
 * this scan covers that nothing else in the wiring triggers a check, so no periodic check can come back unnoticed.
 */
class UpdateScheduleScanTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));

	@Test
	void theCheckRunsOnlyAtStartupAndTheFirstJoin() throws IOException {
		String code = SendScan.withoutComments(Files.readString(PROJECT.resolve("src/main/kotlin/com/k8bas/skyblockutility/update/Updates.kt")));
		assertEquals(2, code.split("triggerAutomatic", -1).length, "one call, inside SessionChecks");
		assertTrue(code.contains("SessionChecks { service!!.triggerAutomatic() }"), "the one call is SessionChecks'");
		assertTrue(code.contains("checks.started()"), "after the launch's reconciliation");
		assertTrue(code.contains("ClientPlayConnectionEvents.JOIN.register { _, _, _ -> checks.joined() }"), "at a join");
		assertTrue(code.contains("ClientTickEvents.END_CLIENT_TICK.register { client -> notifier!!.tick(client) }"), "the tick only shows notices");
		assertEquals(2, code.split("checks\\.started\\(\\)", -1).length, "started() once");
		assertEquals(2, code.split("checks\\.joined\\(\\)", -1).length, "joined() once");
	}
}

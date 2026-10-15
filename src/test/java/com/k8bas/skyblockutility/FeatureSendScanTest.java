package com.k8bas.skyblockutility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The review items AC-MSA-08, AC-NPCWP-12, AC-BOB-06, AC-HOT-07 and AC-ODDS-02 [R] (AC-DRILL-02 went with the drill
 * fix, R37), as one scan (G3): no feature module and no mixin clicks, closes or opens a menu, or sends a command, chat
 * or packet, in the 26.2 names.
 * Each module's own test checks what is particular to it (its hooks, its packet reads).
 */
class FeatureSendScanTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));

	@Test
	void noFeatureOrMixinSendsOrClicksAnything() throws IOException {
		List<Path> files = SendScan.sources(PROJECT,
				"src/main/kotlin/com/k8bas/skyblockutility/module/mineshaftalert", "src/main/kotlin/com/k8bas/skyblockutility/module/npcsearch",
				"src/main/java/com/k8bas/skyblockutility/module/npcsearch", "src/main/kotlin/com/k8bas/skyblockutility/module/bobberfix",
				"src/main/kotlin/com/k8bas/skyblockutility/module/hotspot", "src/main/kotlin/com/k8bas/skyblockutility/module/corpsespots",
				"src/main/kotlin/com/k8bas/skyblockutility/module/odds", "src/main/java/com/k8bas/skyblockutility/mixin");
		assertTrue(files.size() >= 25, "the scan sees the feature sources: " + files.size());
		assertEquals(List.of(), SendScan.violations(PROJECT, files, SendScan.SENDS));
	}

	@Test
	void theScanCatchesThe262Names() throws IOException {
		// A pattern that names no real 26.2 call would let one through.
		for (String call : List.of("client.gameMode.handleContainerInput(id, slot, 0, input, player)", "connection.sendUnsignedCommand(\"warp hub\")",
				"connection.sendChat(\"hi\")", "connection.send(new ServerboundChatPacket())", "player.closeContainer()", "client.setScreen(null)")) {
			Path file = java.nio.file.Files.createTempFile("scan", ".kt");
			java.nio.file.Files.writeString(file, "fun x() {\n\t" + call + "\n}\n");
			assertFalse(SendScan.violations(file.getParent(), List.of(file), SendScan.SENDS).isEmpty(), call);
			java.nio.file.Files.delete(file);
		}
		Path comment = java.nio.file.Files.createTempFile("scan", ".kt");
		java.nio.file.Files.writeString(comment, "/** It never calls sendCommand. */\nfun x() {}\n");
		assertEquals(List.of(), SendScan.violations(comment.getParent(), List.of(comment), Map.<String, Pattern>of("send", Pattern.compile("sendCommand"))),
				"a comment may say what the code never does");
		java.nio.file.Files.delete(comment);
	}
}

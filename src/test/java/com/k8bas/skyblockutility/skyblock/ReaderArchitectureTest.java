package com.k8bas.skyblockutility.skyblock;

import com.k8bas.skyblockutility.SendScan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-GS-01 [A] (REQ-GS-01) and REQ-GS-12, made repeatable: the reader packages (`skyblock` and below, in both
 * source trees) never click, close or open a menu, send a command, chat or packet, cancel anything, or write
 * to disk. A reader only reads what the server already shows.
 */
class ReaderArchitectureTest {
	/** The shared send scan (the 26.2 names, SendScan), plus cancelling anything and writing to disk. */
	private static final Map<String, Pattern> FORBIDDEN = new java.util.LinkedHashMap<>(SendScan.SENDS);

	static {
		// ALLOW_GAME is allowed only in ChatReader, whose listener always lets the message through (below).
		FORBIDDEN.put("cancellation", Pattern.compile("CallbackInfo|\\.cancel\\(|@Inject|@WrapOperation|@Redirect|MODIFY_GAME|MODIFY_CHAT|ALLOW_CHAT"));
		FORBIDDEN.put("disk write", Pattern.compile("Files\\.write|Files\\.newBufferedWriter|Files\\.newOutputStream|FileWriter|FileOutputStream|AtomicFileStore|PrintWriter"));
	}

	@Test
	void readerPackagesOnlyRead() throws IOException {
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		List<String> violations = new ArrayList<>();
		int files = 0;
		for (String tree : List.of("src/main/java", "src/main/kotlin")) {
			Path dir = project.resolve(tree).resolve("com/k8bas/skyblockutility/skyblock");
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path file : walk.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt")).toList()) {
					files++;
					String source = withoutComments(Files.readString(file));
					FORBIDDEN.forEach((what, pattern) -> {
						// REQ-GS-12's one exception: the parsed per-profile results features need (T3.0d).
						if (what.equals("disk write") && file.getFileName().toString().equals("ProfileStore.kt")) {
							return;
						}
						Matcher m = pattern.matcher(source);
						if (m.find()) {
							violations.add(project.relativize(file) + ": " + what + " (" + m.group() + ")");
						}
					});
				}
			}
		}
		assertTrue(files >= 8, "the scan found the reader sources: " + files);
		assertEquals(List.of(), violations);
	}

	/**
	 * REQ-XC-RULES-02: the one ALLOW_GAME listener (T3.0g) is ChatReader's, and it returns what
	 * MessageDispatch.allow returns, which is always true (MessageDispatchTest).
	 */
	@Test
	void theOnlyAllowGameListenerLetsEverythingThrough() throws IOException {
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		List<String> users = new ArrayList<>();
		for (String tree : List.of("src/main/java", "src/main/kotlin")) {
			Path dir = project.resolve(tree).resolve("com/k8bas/skyblockutility/skyblock");
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path file : walk.filter(Files::isRegularFile).toList()) {
					if (withoutComments(Files.readString(file)).contains("ALLOW_GAME")) {
						users.add(file.getFileName().toString());
					}
				}
			}
		}
		assertEquals(List.of("ChatReader.kt"), users);
		String reader = withoutComments(Files.readString(project.resolve("src/main/kotlin/com/k8bas/skyblockutility/skyblock/ChatReader.kt")));
		assertTrue(onlyAllowingListener(reader), "exactly one ALLOW_GAME listener, whose result is dispatch.allow(...)");
		// The check itself catches a second, cancelling registration.
		assertTrue(!onlyAllowingListener(reader + "\nClientReceiveMessageEvents.ALLOW_GAME.register { _, _ -> false }"));
	}

	/** One addPhaseOrdering and one register on ALLOW_GAME, and that register returns dispatch.allow(...). */
	private static boolean onlyAllowingListener(String reader) {
		int uses = reader.split("ALLOW_GAME", -1).length - 1;
		int registrations = reader.split("ALLOW_GAME\\.register", -1).length - 1;
		Matcher listener = Pattern.compile("ALLOW_GAME\\.register\\(EARLY_PHASE, ClientReceiveMessageEvents\\.AllowGame \\{ message, overlay ->"
				+ "\\s*GameStateReaders\\.CLIENT_THREAD\\.run\\(\\)\\s*dispatch\\.allow\\(message\\.string, overlay\\)\\s*}\\)").matcher(reader);
		return uses == 2 && registrations == 1 && listener.find();
	}

	@Test
	void theScanCatchesEachKindOfViolation() {
		// One real 26.2 call per pattern, so a pattern that names no existing API fails here.
		Map<String, List<String>> samples = Map.of(
				"slot click", List.of("client.gameMode?.handleInventoryMouseClick(menu.containerId, 0, 0, input, player)",
						"gameMode.handleInventoryButtonClick(menu.containerId, 1);", "ServerboundContainerClickPacket(id)"),
				"container close", List.of("player.clientSideCloseContainer()", "player.closeContainer();", "client.setScreen(null)"),
				"command or chat send", List.of("connection.sendUnattendedCommand(\"warp mines\", null)",
						"connection.sendCommand(\"pc hi\")", "connection.sendChat(\"hi\");"),
				"packet send", List.of("connection.send(packet)", "ServerboundPlayerCommandPacket(player, action)"),
				"cancellation", List.of("ci.cancel()", "ClientReceiveMessageEvents.MODIFY_GAME.register { message, _ -> message }",
						"ClientReceiveMessageEvents.ALLOW_CHAT.register { _, _, _, _, _ -> false }"),
				"disk write", List.of("Files.writeString(path, text)", "FileOutputStream(file)", "AtomicFileStore.write(path, bytes)"));
		assertEquals(FORBIDDEN.keySet(), samples.keySet(), "every pattern has samples");
		samples.forEach((what, calls) -> {
			for (String call : calls) {
				assertTrue(FORBIDDEN.get(what).matcher(withoutComments(call)).find(), what + " misses " + call);
			}
		});
		assertTrue(!FORBIDDEN.get("command or chat send").matcher(withoutComments("// sendCommand is never used")).find(),
				"a comment that names an API is no violation");
	}

	/** Drops line and block comments, so KDoc that explains what is never done does not trip the scan. */
	private static String withoutComments(String source) {
		return SendScan.withoutComments(source);
	}
}

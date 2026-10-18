package com.k8bas.skyblockutility.update.helper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T4.3 (REQ-UPD-17, EC-UPD-06, EC-UPD-07): the helper that swaps the jar after the game exits, on a temp folder. The new
 * jar goes in before the old one goes, so the folder never holds zero copies and never a partial jar; every wait and
 * retry is bounded; every step is logged. Tests that need a file held open run on Windows only, where an open file
 * cannot be deleted (CI's Linux deletes it).
 */
class UpdateHelperTest {
	private static final String OLD = "k8bas_skyblock_utility-2.0.0+26.2.jar";
	private static final String NEW = "k8bas_skyblock_utility-2.0.1+26.2.jar";
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	@TempDir
	Path dir;

	private Path mods;
	private Path staged;
	private Path log;
	private byte[] newJar;
	private final List<Process> processes = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		mods = Files.createDirectories(dir.resolve("mods"));
		Files.write(mods.resolve(OLD), "old jar".getBytes(StandardCharsets.UTF_8));
		Files.writeString(mods.resolve("fabric-api.jar"), "other mod");
		newJar = "new jar".getBytes(StandardCharsets.UTF_8);
		staged = Files.write(Files.createDirectories(dir.resolve(".k8bas_update").resolve("staged")).resolve(NEW), newJar);
		log = dir.resolve(".k8bas_update").resolve("logs").resolve("helper.log");
	}

	@AfterEach
	void tearDown() {
		processes.forEach(Process::destroyForcibly);
	}

	/** A stand-in for the game: a JVM that lives until its input closes. */
	private Process game() throws IOException {
		Path source = dir.resolve("Game.java");
		if (!Files.exists(source)) {
			Files.writeString(source, "class Game { public static void main(String[] a) throws Exception { System.in.read(); } }");
		}
		Process process = new ProcessBuilder(java(), source.toString()).start();
		processes.add(process);
		return process;
	}

	private static String java() {
		return Path.of(System.getProperty("java.home"), "bin", "java").toString();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private String[] args(long pid, Path dest, String... extra) {
		List<String> args = new ArrayList<>(List.of("--parent-pid", Long.toString(pid), "--staged", staged.toString(), "--dest", dest.toString(),
				"--old", mods.resolve(OLD).toString(), "--sha256", sha256(newJar), "--log", log.toString(), "--retry-ms", "500"));
		args.addAll(List.of(extra));
		return args.toArray(String[]::new);
	}

	private Map<String, String> folder(Path folder) throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> list = Files.list(folder)) {
			for (Path file : list.toList()) {
				files.put(file.getFileName().toString(), Files.isDirectory(file) ? "dir" : sha256(Files.readAllBytes(file)));
			}
		}
		return files;
	}

	private List<String> logLines() throws IOException {
		return Files.readAllLines(log, StandardCharsets.UTF_8);
	}

	private boolean logged(String part) throws IOException {
		return logLines().stream().anyMatch(line -> line.contains(part));
	}

	private static int indexOf(List<String> lines, String part) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).contains(part)) {
				return i;
			}
		}
		return -1;
	}

	/** AC-UPD-18 (a) in miniature: nothing moves while the game lives; then the new jar goes in, is checked, and only then the old one goes. */
	@Test
	void nothingMovesWhileTheGameRunsThenTheNewJarGoesInFirst() throws Exception {
		Process game = game();
		Map<String, String> before = folder(mods);
		CompletableFuture<Integer> helper = CompletableFuture.supplyAsync(() -> UpdateHelper.run(args(game.pid(), mods.resolve(NEW))));
		Thread.sleep(800);
		assertFalse(helper.isDone(), "still waiting for the game");
		assertEquals(before, folder(mods), "mods/ untouched while the game runs");
		assertTrue(Files.exists(staged));
		game.getOutputStream().close();
		assertEquals(UpdateHelper.APPLIED, helper.get(30, TimeUnit.SECONDS));
		assertEquals(Map.of(NEW, sha256(newJar), "fabric-api.jar", sha256("other mod".getBytes(StandardCharsets.UTF_8))), folder(mods));
		assertFalse(Files.exists(staged));
		List<String> lines = logLines();
		int ended = indexOf(lines, "the game's process ended");
		int moved = indexOf(lines, "moved in: ");
		int deleted = indexOf(lines, "deleted the old jar");
		assertTrue(indexOf(lines, "helper started") == 0 && ended > 0 && ended < moved && moved < deleted, lines.toString());
	}

	/** EC-UPD-06: two games share the mods folder; the helper waits only for its own. */
	@Test
	void itWaitsOnlyForItsOwnGame() throws Exception {
		Process own = game();
		Process other = game();
		CompletableFuture<Integer> helper = CompletableFuture.supplyAsync(() -> UpdateHelper.run(args(own.pid(), mods.resolve(NEW))));
		Thread.sleep(300);
		own.getOutputStream().close();
		assertEquals(UpdateHelper.APPLIED, helper.get(30, TimeUnit.SECONDS));
		assertTrue(other.isAlive(), "the other game still runs");
	}

	/** A later process that got the game's PID is not waited for: the start time tells them apart. */
	@Test
	void aReusedPidIsNotWaitedFor() throws Exception {
		Process stranger = game();
		long start = stranger.info().startInstant().orElseThrow().toEpochMilli();
		long begin = System.nanoTime();
		assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(args(stranger.pid(), mods.resolve(NEW), "--parent-start", Long.toString(start - 60_000), "--wait-ms", "3000")));
		assertTrue((System.nanoTime() - begin) / 1_000_000 < 5_000);
		assertTrue(logged("the game's process has already ended"), logLines().toString());
	}

	/** AC-UPD-18 (c): the old jar held by another process past the retry time stays; the new one is in. Never zero copies. */
	@Test
	void aLockedOldJarStaysAndTheNewOneIsIn() throws Exception {
		assumeTrue(WINDOWS, "an open file blocks a delete on Windows only");
		try (RandomAccessFile lock = new RandomAccessFile(mods.resolve(OLD).toFile(), "r")) {
			assertEquals(UpdateHelper.OLD_JAR_STAYS, UpdateHelper.run(args(-1, mods.resolve(NEW))));
			assertTrue(Files.exists(mods.resolve(NEW)));
			assertTrue(Files.exists(mods.resolve(OLD)), "held open, so it stays for the next launch");
		}
		assertTrue(logged("the old jar stays; the next launch deletes it"), logLines().toString());
	}

	/** REQ-UPD-17: a delete is retried, so a lock that ends within the retry time does not keep the old jar. */
	@Test
	void aShortLockOnTheOldJarIsWaitedOut() throws Exception {
		assumeTrue(WINDOWS, "an open file blocks a delete on Windows only");
		RandomAccessFile lock = new RandomAccessFile(mods.resolve(OLD).toFile(), "r");
		Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(400);
				lock.close();
			} catch (InterruptedException | IOException ignored) {
				// the test fails on its own
			}
		});
		assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW), "--retry-ms", "5000")));
		assertFalse(Files.exists(mods.resolve(OLD)));
		assertTrue(logLines().stream().anyMatch(line -> line.matches(".*deleted " + java.util.regex.Pattern.quote(OLD) + " on attempt [1-9].*")), logLines().toString());
	}

	/** EC-UPD-07: across drives the jar is copied to a name Fabric ignores, then renamed; nothing temporary is left. */
	@Test
	void anotherDriveGoesThroughATemporaryName() throws Exception {
		assertFalse(UpdateHelper.TEMPORARY_SUFFIX.endsWith(".jar"));
		assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW), "--copy")));
		assertEquals(List.of("fabric-api.jar", NEW), List.copyOf(folder(mods).keySet()));
		assertFalse(Files.exists(staged));
		assertTrue(logged("copying the staged jar through " + NEW + UpdateHelper.TEMPORARY_SUFFIX), logLines().toString());
	}

	/** EC-UPD-07: a copy that keeps failing leaves the old jar, no new jar and no temporary file named like a jar. */
	@Test
	void aFailingCopyLeavesTheOldJar() throws Exception {
		Path blocker = Files.createDirectories(mods.resolve(NEW + UpdateHelper.TEMPORARY_SUFFIX));
		Files.writeString(blocker.resolve("x"), "keeps the copy from replacing it");
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW), "--copy")));
		assertTrue(Files.exists(mods.resolve(OLD)), "never zero copies");
		assertFalse(Files.exists(mods.resolve(NEW)));
		assertTrue(Files.exists(staged), "kept for the next launch");
		assertTrue(logged("could not move the new jar in within 500 ms"), logLines().toString());
	}

	/** Review (T4.3): a staged copy that cannot be deleted after the copy does not turn a done swap into "not applied". */
	@Test
	void aStagedCopyThatCannotBeDeletedStillApplies() throws Exception {
		assumeTrue(WINDOWS, "an open file blocks a delete on Windows only");
		try (RandomAccessFile lock = new RandomAccessFile(staged.toFile(), "r")) {
			assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW), "--copy")));
		}
		assertEquals(List.of("fabric-api.jar", NEW), List.copyOf(folder(mods).keySet()));
		assertTrue(logged("the staged copy remains"), logLines().toString());
	}

	/** REQ-UPD-17: a staged jar whose SHA-256 changed since it was checked is not even moved. */
	@Test
	void aChangedStagedJarIsNotMoved() throws Exception {
		Map<String, String> before = folder(mods);
		Files.writeString(staged, "tampered");
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW))));
		assertEquals(before, folder(mods));
		assertTrue(Files.exists(staged));
		assertTrue(logged("the staged jar's SHA-256 is not"), logLines().toString());
		assertFalse(logged("moved"), logLines().toString());
	}

	/** REQ-UPD-17: the moved jar is checked again before the old one goes; a bad one is removed and the old jar stays. */
	@Test
	void aMovedJarThatFailsItsCheckIsRemovedAndTheOldOneStays() throws Exception {
		int code = UpdateHelper.run(args(-1, mods.resolve(NEW)), () -> {
			try {
				Files.writeString(mods.resolve(NEW), "damaged on the way");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
		assertEquals(UpdateHelper.NOT_APPLIED, code);
		assertTrue(Files.exists(mods.resolve(OLD)));
		assertFalse(Files.exists(mods.resolve(NEW)));
		assertTrue(logged("removed the moved jar again"), logLines().toString());
	}

	/** REQ-UPD-17, EC-UPD-06: it waits only as long as it is told; then the old jar stays. */
	@Test
	void aGameThatDoesNotEndInTimeChangesNothing() throws Exception {
		Map<String, String> before = folder(mods);
		Process game = game();
		long start = System.nanoTime();
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(game.pid(), mods.resolve(NEW), "--wait-ms", "400")));
		assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000);
		assertEquals(before, folder(mods));
		assertTrue(Files.exists(staged), "kept for the next launch to re-arm");
		assertTrue(logged("did not end within 400 ms"), logLines().toString());
	}

	/** REQ-UPD-17's bounds: at most 120 s for the game to exit, at most 60 s per move or delete, logged at the start. */
	@Test
	void theWaitsAreBounded() throws Exception {
		assertEquals(120_000, UpdateHelper.MAX_WAIT_MS);
		assertEquals(60_000, UpdateHelper.MAX_RETRY_MS);
		UpdateHelper.run(new String[] {"--parent-pid", "-1", "--staged", staged.toString(), "--dest", dir.resolve(NEW).toString(),
				"--old", mods.resolve(OLD).toString(), "--sha256", sha256(newJar), "--log", log.toString()});
		assertTrue(logged("waits at most 120000 ms, retries at most 60000 ms"), logLines().toString());
	}

	/** AC-UPD-19 at the helper: each rule refuses on its own, by its reason, and nothing changes. */
	@Test
	void pathsOutsideTheRulesAreRefusedByReason() throws Exception {
		Files.createDirectories(mods.resolve("sub"));
		Map<String, String> before = folder(mods);
		Map<Path, String> cases = new java.util.LinkedHashMap<>();
		cases.put(dir.resolve(NEW), "the new jar would not go into the old jar's folder");
		cases.put(mods.resolve("sub").resolve(NEW), "the new jar would not go into the old jar's folder");
		cases.put(mods.resolve("k8bas.zip"), "the new jar's name k8bas.zip is not a jar name");
		cases.put(mods.resolve(".k8bas.jar"), "the new jar's name .k8bas.jar is not a jar name");
		cases.put(mods.resolve(OLD), "the new jar would replace the running jar in place");
		cases.put(mods.resolve(OLD.replace("k8bas", "K8BAS")), "the new jar would replace the running jar in place");
		for (Map.Entry<Path, String> refused : cases.entrySet()) {
			assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(-1, refused.getKey())), refused.getKey().toString());
			assertTrue(logLines().getLast().contains("refused: " + refused.getValue()), logLines().getLast());
		}
		assertEquals(before, folder(mods));
		assertTrue(Files.exists(staged));
		Files.delete(staged);
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW))), "no staged jar");
		assertTrue(logLines().getLast().contains("refused: the staged jar is missing"), logLines().getLast());
		assertEquals(before, folder(mods));
	}

	/** Another file already under the new name is never overwritten; the same jar already there just finishes the swap. */
	@Test
	void anExistingFileUnderTheNewNameIsNotOverwritten() throws Exception {
		Files.writeString(mods.resolve(NEW), "somebody else's file");
		Map<String, String> before = folder(mods);
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW))));
		assertEquals(before, folder(mods));
		assertTrue(logged("another file already has the name"), logLines().toString());
		Files.write(mods.resolve(NEW), newJar);
		assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(args(-1, mods.resolve(NEW))));
		assertEquals(List.of("fabric-api.jar", NEW), List.copyOf(folder(mods).keySet()));
		assertTrue(logged("the new jar is already in place"), logLines().toString());
		assertTrue(Files.exists(staged), "not moved; the next launch clears it");
	}

	/** T4.3b: a replaced jar that was locked at launch is deleted after the game exits; nothing else is touched. */
	@Test
	void aDeleteOnlyRunRemovesJustTheOldJar() throws Exception {
		Map<String, String> before = folder(mods);
		assertEquals(UpdateHelper.APPLIED, UpdateHelper.run(new String[] {"--delete-only", "--parent-pid", "-1", "--old", mods.resolve(OLD).toString(),
				"--log", log.toString(), "--retry-ms", "500"}));
		before.remove(OLD);
		assertEquals(before, folder(mods));
		assertTrue(Files.exists(staged));
		assertTrue(logged("deleted the old jar"), logLines().toString());
		assertEquals(UpdateHelper.NOT_APPLIED, UpdateHelper.run(new String[] {"--delete-only", "--parent-pid", "-1",
				"--old", dir.resolve("notes.txt").toString(), "--log", log.toString()}), "only a jar");
		assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[] {"--delete-only", "--log", log.toString()}));
	}

	@Test
	void badArgumentsChangeNothing() throws Exception {
		Map<String, String> before = folder(mods);
		assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[] {"--staged"}));
		assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(new String[] {"--unknown", "x"}));
		assertEquals(UpdateHelper.BAD_ARGUMENTS, UpdateHelper.run(args(-1, mods.resolve(NEW), "--sha256", "abc")));
		assertEquals(before, folder(mods));
	}

	/** REQ-UPD-17: the helper is one class file with no nested class, and runs from that file alone in its own JVM. */
	@Test
	void itRunsAloneFromItsOneClassFile() throws Exception {
		Path compiled = compiledClass();
		try (Stream<Path> siblings = Files.list(compiled.getParent())) {
			assertEquals(List.of("UpdateHelper.class"), siblings.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("UpdateHelper")).toList());
		}
		Path helperDir = dir.resolve("helper");
		Path copy = helperDir.resolve("com/k8bas/skyblockutility/update/helper/UpdateHelper.class");
		Files.createDirectories(copy.getParent());
		Files.copy(compiled, copy);
		Process game = game();
		List<String> command = new ArrayList<>(List.of(java(), "-cp", "helper", UpdateHelper.class.getName()));
		command.addAll(List.of(args(game.pid(), mods.resolve(NEW))));
		Process helper = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
		processes.add(helper);
		Thread.sleep(300);
		game.getOutputStream().close();
		assertTrue(helper.waitFor(30, TimeUnit.SECONDS));
		assertEquals(UpdateHelper.APPLIED, helper.exitValue(), new String(helper.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		assertEquals(List.of("fabric-api.jar", NEW), List.copyOf(folder(mods).keySet()));
	}

	private static Path compiledClass() throws URISyntaxException {
		return Path.of(UpdateHelper.class.getResource("UpdateHelper.class").toURI());
	}
}

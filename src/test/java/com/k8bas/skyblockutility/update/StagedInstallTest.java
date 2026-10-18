package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.update.helper.UpdateHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.3 (REQ-UPD-17, AC-UPD-19): arming a staged update. The new jar's place is checked (it stays in the running jar's
 * folder, under a jar name of this mod, never the running jar's own name); arming copies the helper out of the mod and
 * registers one shutdown hook; at exit the hook re-checks the staged jar and starts the helper, and a disarmed update
 * starts nothing. Nothing is written into the mods folder while the game runs.
 */
class StagedInstallTest {
	private static final String OLD = "k8bas_skyblock_utility-2.0.0+26.2.jar";
	private static final String NEW = "k8bas_skyblock_utility-2.0.1+26.2.jar";

	@TempDir
	Path dir;

	private Path mods;
	private Path root;
	private Path runningJar;
	private Path staged;
	private String sha256;
	private final List<List<String>> started = new CopyOnWriteArrayList<>();
	private final List<Thread> hooks = new ArrayList<>();
	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private final List<Process> games = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		mods = Files.createDirectories(dir.resolve("mods"));
		runningJar = Files.write(mods.resolve(OLD), TestJars.mod("2.0.0+26.2"));
		root = dir.resolve(".k8bas_update");
		byte[] jar = TestJars.mod("2.0.1+26.2");
		staged = Files.write(Files.createDirectories(root.resolve("staged")).resolve(NEW), jar);
		sha256 = TestJars.sha256(jar);
	}

	@AfterEach
	void tearDown() {
		games.forEach(Process::destroyForcibly);
	}

	private StagedInstall install(Path running) {
		return new StagedInstall(root, () -> running, (command, directory) -> started.add(command), hooks::add, warnings::add);
	}

	/** AC-UPD-19: an asset name with "..\", an absolute path, or the running jar's own name is refused. */
	@Test
	void theNewJarStaysBesideTheRunningOneUnderItsOwnName() {
		StagedInstall install = install(runningJar);
		assertNull(StagedInstall.refusal(NEW, runningJar));
		for (String name : List.of("..\\" + NEW, "../" + NEW, mods.resolve(NEW).toString(), "C:\\mods\\" + NEW, "sub/" + NEW, "evil.jar", "")) {
			String refusal = StagedInstall.refusal(name, runningJar);
			assertEquals("the asset name " + name + " is not a jar of this mod", refusal, name);
		}
		assertEquals("the new jar would replace the running jar", StagedInstall.refusal(OLD, runningJar));
		assertEquals("the new jar would replace the running jar", StagedInstall.refusal(OLD, mods.resolve(OLD.replace("k8bas", "K8BAS"))),
				"NTFS compares names without case");
		assertEquals("the running jar is not known", StagedInstall.refusal(NEW, null));
		StagedInstall.Armed refused = install.arm(staged, OLD, sha256);
		assertEquals("the new jar would replace the running jar", refused.refusal());
		assertTrue(hooks.isEmpty(), "nothing armed");
	}

	/** Arming copies the helper's one class file out of the mod and registers one shutdown hook, however often it is armed. */
	@Test
	void armingCopiesTheHelperAndRegistersOneHook() throws Exception {
		StagedInstall install = install(runningJar);
		StagedInstall.Armed armed = install.arm(staged, NEW, sha256);
		assertNull(armed.refusal());
		assertEquals(mods.resolve(NEW), armed.dest());
		install.arm(staged, NEW, sha256);
		assertEquals(1, hooks.size());
		Path helper = root.resolve("helper/com/k8bas/skyblockutility/update/helper/UpdateHelper.class");
		try (var compiled = UpdateHelper.class.getResourceAsStream("UpdateHelper.class")) {
			assertArrayEquals(compiled.readAllBytes(), Files.readAllBytes(helper));
		}
		assertEquals(List.of(OLD), List.of(mods.toFile().list()), "nothing written into mods/ while the game runs");
	}

	/** At exit: the staged jar re-checked, an attempt marker for the next launch, then the helper with the plan. */
	@Test
	void atExitTheHelperStartsWithThePlan() throws Exception {
		StagedInstall install = install(runningJar);
		install.arm(staged, NEW, sha256);
		hooks.getFirst().run();
		assertEquals(1, started.size());
		List<String> command = started.getFirst();
		assertEquals(StagedInstall.javaLauncher(), command.getFirst());
		long start = ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
		// The class path is relative to .k8bas_update, where the helper starts, so a ';' in the game path cannot split it.
		assertEquals(List.of("-Xmx32m", "-cp", "helper", UpdateHelper.class.getName(),
				"--parent-pid", Long.toString(ProcessHandle.current().pid()), "--parent-start", Long.toString(start), "--staged", staged.toString(), "--dest", mods.resolve(NEW).toString(),
				"--old", runningJar.toString(), "--sha256", sha256, "--log", root.resolve("logs").resolve("helper.log").toString()),
				command.subList(1, command.size()));
		assertTrue(Files.exists(root.resolve(StagedInstall.ATTEMPT_MARKER)), "the next launch knows the helper was started");
	}

	/** A second confirmation in the same session replaces the first: its staged jar goes, the newer one is applied. */
	@Test
	void aSecondArmReplacesTheFirst() throws Exception {
		StagedInstall install = install(runningJar);
		install.arm(staged, NEW, sha256);
		String newer = "k8bas_skyblock_utility-2.0.2+26.2.jar";
		byte[] jar = TestJars.mod("2.0.2+26.2");
		Path staged2 = Files.write(root.resolve("staged").resolve(newer), jar);
		install.arm(staged2, newer, TestJars.sha256(jar));
		assertFalse(Files.exists(staged), "the first staged jar is deleted");
		hooks.getFirst().run();
		List<String> command = started.getFirst();
		assertEquals(staged2.toString(), command.get(command.indexOf("--staged") + 1));
		assertEquals(mods.resolve(newer).toString(), command.get(command.indexOf("--dest") + 1));
	}

	/** AC-UPD-13 (cancel), REQ-UPD-12: a disarmed update starts no helper at exit. */
	@Test
	void aDisarmedUpdateStartsNothing() {
		StagedInstall install = install(runningJar);
		install.arm(staged, NEW, sha256);
		install.disarm();
		hooks.getFirst().run();
		assertTrue(started.isEmpty());
		assertFalse(Files.exists(root.resolve(StagedInstall.ATTEMPT_MARKER)));
	}

	/** REQ-UPD-17: a staged jar that changed after it was checked is not handed to the helper. */
	@Test
	void aStagedJarChangedBeforeExitStartsNothing() throws IOException {
		StagedInstall install = install(runningJar);
		install.arm(staged, NEW, sha256);
		Files.writeString(staged, "changed");
		hooks.getFirst().run();
		assertTrue(started.isEmpty());
		assertEquals(1, warnings.size(), warnings.toString());
		assertFalse(Files.exists(root.resolve(StagedInstall.ATTEMPT_MARKER)), "no attempt to count");
	}

	/** The command the hook builds really runs: with a short-lived stand-in for the game, the helper swaps the jar. */
	@Test
	void theCommandRunsTheHelper() throws Exception {
		StagedInstall install = install(runningJar);
		install.arm(staged, NEW, sha256);
		hooks.getFirst().run();
		List<String> command = new ArrayList<>(started.getFirst());
		Path source = Files.writeString(dir.resolve("Game.java"),
				"class Game { public static void main(String[] a) throws Exception { Thread.sleep(500); } }");
		Process game = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), source.toString()).start();
		games.add(game);
		command.set(command.indexOf("--parent-pid") + 1, Long.toString(game.pid()));
		Process helper = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
		games.add(helper);
		assertTrue(helper.waitFor(30, TimeUnit.SECONDS));
		assertEquals(UpdateHelper.APPLIED, helper.exitValue(), Files.readString(root.resolve("logs").resolve("helper.log"), StandardCharsets.UTF_8));
		assertEquals(List.of(NEW), List.of(mods.toFile().list()), "exactly one k8bas jar");
	}
}

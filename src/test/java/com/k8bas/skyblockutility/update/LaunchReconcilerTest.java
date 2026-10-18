package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T4.3b (REQ-UPD-18, EC-UPD-05, EC-UPD-08, EC-UPD-15, R13): every branch of the launch reconciliation, on a temp game
 * folder. It runs locally, finishes an applied update, re-arms one that was not applied, disarms one that failed twice,
 * clears orphans, deletes only the jar its own helper replaced, never trusts a path from the state file, and names
 * every other copy of the mod once, keeping it.
 */
class LaunchReconcilerTest {
	private static final String V200 = "k8bas_skyblock_utility-2.0.0+26.2.jar";
	private static final String V201 = "k8bas_skyblock_utility-2.0.1+26.2.jar";
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

	@TempDir
	Path dir;

	private Path mods;
	private Path root;
	private Path staged;
	private final List<List<String>> started = new CopyOnWriteArrayList<>();
	private final List<Thread> hooks = new ArrayList<>();
	private final List<String> info = new CopyOnWriteArrayList<>();
	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private UpdateHooks.Eligibility eligibility = UpdateHooks.Eligibility.ELIGIBLE;

	@BeforeEach
	void setUp() throws IOException {
		mods = Files.createDirectories(dir.resolve("mods"));
		Files.writeString(mods.resolve("fabric-api.jar"), "not a zip");
		root = dir.resolve(".k8bas_update");
		staged = Files.createDirectories(root.resolve("staged"));
		Files.createDirectories(root.resolve("logs"));
		Files.writeString(root.resolve("logs").resolve("helper.log"), "earlier steps\n");
	}

	/** One launch: reconcile, then (as the service does after saving) consume the marker. */
	private LaunchReconciler.Report launch(String running, Path runningJar, UpdateState state) {
		StagedInstall install = new StagedInstall(root, () -> runningJar, (command, directory) -> started.add(command), hooks::add, warnings::add);
		LaunchReconciler reconciler = new LaunchReconciler(running, runningJar, root, install, () -> eligibility, info::add, warnings::add);
		LaunchReconciler.Report report = reconciler.reconcile(state);
		reconciler.finish();
		return report;
	}

	private void helperRan() throws IOException {
		Files.writeString(root.resolve(StagedInstall.ATTEMPT_MARKER), "1");
	}

	private static PendingInstall pending(Path old, int failures, String sha256) {
		return new PendingInstall("2.0.1", V201, sha256, 1L, "2.0.0+26.2", old.toString(), failures);
	}

	private List<String> stagedFiles() {
		return List.of(staged.toFile().list());
	}

	/** REQ-UPD-18 "update applied", EC-UPD-05 (a crash exit runs the hook too): one "Updated to vX", the staged files and the replaced jar gone, the helper log kept. */
	@Test
	void anAppliedUpdateIsFinished() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path old = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		Files.writeString(staged.resolve("leftover.part"), "x");
		helperRan();
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(old, 0, "ab".repeat(32));
		LaunchReconciler.Report report = launch("2.0.1+26.2", running, state);
		assertEquals("Updated to v2.0.1", report.getToast());
		assertNull(state.pendingInstall);
		assertFalse(Files.exists(old), "the jar the updater's helper replaced is deleted");
		assertTrue(Files.exists(running));
		assertEquals(List.of(), stagedFiles());
		assertTrue(Files.exists(root.resolve("logs").resolve("helper.log")), "the helper log is kept");
		assertFalse(Files.exists(root.resolve(StagedInstall.ATTEMPT_MARKER)));
		assertEquals(List.of(), report.getChat());
	}

	/** R13: the target runs but the helper never ran (a hand install after a kill): the old jar is not the updater's, so it is named and kept. */
	@Test
	void anUpdateAppliedByHandKeepsTheOldJar() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path old = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(old, 0, "ab".repeat(32));
		LaunchReconciler.Report report = launch("2.0.1+26.2", running, state);
		assertEquals("Updated to v2.0.1", report.getToast());
		assertTrue(Files.exists(old));
		assertEquals(List.of("Another copy of K8bas Skyblock Utility is in your mods folder: " + V200), report.getChat());
	}

	/** The state file is not trusted for paths: whatever oldJar or leftoverJar names, only a jar of this mod beside the running one may go. */
	@Test
	void aPathFromTheStateFileDeletesNothingElse() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path outside = Files.write(Files.createDirectories(dir.resolve("outside")).resolve(V200), TestJars.mod("2.0.0+26.2"));
		Path sub = Files.write(Files.createDirectories(mods.resolve("sub")).resolve(V200), TestJars.mod("2.0.0+26.2"));
		Path otherMod = Files.write(mods.resolve("other.jar"), TestJars.jar(TestJars.modJson("other_mod", "1.0.0", "~26.2", ">=0.19.5", "client")));
		Path notAJar = Files.write(mods.resolve("x.txt"), TestJars.mod("2.0.0+26.2"));
		for (Path target : List.of(running, outside, sub, otherMod, notAJar)) {
			helperRan();
			UpdateState state = new UpdateState();
			state.pendingInstall = pending(target, 0, "ab".repeat(32));
			launch("2.0.1+26.2", running, state);
			assertTrue(Files.exists(target), "oldJar " + target);
			assertNull(state.leftoverJar);
			UpdateState leftover = new UpdateState();
			leftover.leftoverJar = target.toString();
			launch("2.0.1+26.2", running, leftover);
			assertTrue(Files.exists(target), "leftoverJar " + target);
			assertNull(leftover.leftoverJar, "dropped, not kept for later");
			assertTrue(hooks.isEmpty(), "no delete at exit either");
		}
		UpdateState garbage = new UpdateState();
		garbage.leftoverJar = "\u0000:bad";
		launch("2.0.1+26.2", running, garbage);
		assertNull(garbage.leftoverJar);
	}

	/** AC-UPD-18 (c): the replaced jar still locked: kept for now, not named as another copy, its delete handed to the helper at exit, then the next launch deletes it. */
	@Test
	void aLockedReplacedJarGoesLater() throws IOException {
		assumeTrue(WINDOWS, "an open file blocks a delete on Windows only");
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path old = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		helperRan();
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(old, 0, "ab".repeat(32));
		try (RandomAccessFile lock = new RandomAccessFile(old.toFile(), "r")) {
			LaunchReconciler.Report first = launch("2.0.1+26.2", running, state);
			assertTrue(Files.exists(old));
			assertEquals(old.toString(), state.leftoverJar);
			assertEquals(List.of(), first.getChat(), "the updater's own jar is not another copy");
			assertFalse(state.warnedFiles.contains(V200));
			assertEquals(1, hooks.size(), "its delete waits for the exit");
			hooks.getFirst().run();
			List<String> command = started.getFirst();
			assertTrue(command.contains("--delete-only"), command.toString());
			assertEquals(old.toString(), command.get(command.indexOf("--old") + 1));
		}
		LaunchReconciler.Report next = launch("2.0.1+26.2", running, state);
		assertFalse(Files.exists(old), "the next launch deletes it");
		assertNull(state.leftoverJar);
		assertEquals(List.of(), next.getChat());
	}

	/** The leftover path on any system: an unlocked leftover goes at launch; a missing one is forgotten. */
	@Test
	void aLeftoverJarIsRetriedAtLaunch() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path old = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		UpdateState state = new UpdateState();
		state.leftoverJar = old.toString();
		LaunchReconciler.Report report = launch("2.0.1+26.2", running, state);
		assertFalse(Files.exists(old));
		assertNull(state.leftoverJar);
		assertEquals(List.of(), report.getChat());
		state.leftoverJar = mods.resolve("gone.jar").toString();
		launch("2.0.1+26.2", running, state);
		assertNull(state.leftoverJar);
	}

	/** AC-UPD-18 (b), REQ-UPD-18 "not applied yet": a hard kill skipped the hook; re-checked, re-armed, "ready, restart"; no failure counted. */
	@Test
	void anUpdateNotAppliedAfterAHardKillIsReArmed() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		byte[] jar = TestJars.mod("2.0.1+26.2");
		Files.write(staged.resolve(V201), jar);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(mods.resolve("renamed-since.jar"), 0, TestJars.sha256(jar));
		LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
		assertEquals("Update ready, restart Minecraft to apply", report.getToast());
		assertEquals(List.of(), report.getChat());
		assertEquals(pending(running, 0, TestJars.sha256(jar)), state.pendingInstall, "the jar that runs now is the one to replace");
		assertEquals(1, hooks.size(), "re-armed");
		hooks.getFirst().run();
		List<String> command = started.getFirst();
		assertEquals(mods.resolve(V201).toString(), command.get(command.indexOf("--dest") + 1));
	}

	/** EC-UPD-08: the helper ran but the old version still runs: reported, one more try; two in a row disarm with "install manually". */
	@Test
	void aFailedApplyIsReportedAndTwoInARowDisarm() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		byte[] jar = TestJars.mod("2.0.1+26.2");
		Files.write(staged.resolve(V201), jar);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, TestJars.sha256(jar));
		helperRan();
		LaunchReconciler.Report first = launch("2.0.0+26.2", running, state);
		assertEquals("Update ready, restart Minecraft to apply", first.getToast());
		assertEquals(List.of("The update to v2.0.1 couldn't be installed when you quit; it tries once more next time"), first.getChat());
		assertEquals(1, state.pendingInstall.failedApplies());
		helperRan();
		LaunchReconciler.Report second = launch("2.0.0+26.2", running, state);
		assertNull(state.pendingInstall);
		assertNull(second.getToast());
		assertEquals(List.of("The update to v2.0.1 could not be installed, so install it manually from the release page"), second.getChat());
		assertEquals(List.of(), stagedFiles());
		assertTrue(Files.exists(running), "the running jar is untouched");
	}

	/** A marker with no record is consumed, so a later update does not start with a failure it never had. */
	@Test
	void aStaleMarkerCountsForNothing() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		helperRan();
		launch("2.0.0+26.2", running, new UpdateState());
		assertFalse(Files.exists(root.resolve(StagedInstall.ATTEMPT_MARKER)));
		byte[] jar = TestJars.mod("2.0.1+26.2");
		Files.write(staged.resolve(V201), jar);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, TestJars.sha256(jar));
		launch("2.0.0+26.2", running, state);
		assertEquals(0, state.pendingInstall.failedApplies());
	}

	/** REQ-UPD-18: a staged jar that fails its re-check is deleted and reported; nothing is armed. */
	@Test
	void aStagedJarThatFailsItsCheckIsDeleted() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		Files.writeString(staged.resolve(V201), "tampered");
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, "ab".repeat(32));
		LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
		assertNull(state.pendingInstall);
		assertTrue(hooks.isEmpty());
		assertEquals(List.of(), stagedFiles());
		assertEquals(List.of("The downloaded update v2.0.1 failed its check and was deleted; nothing changed"), report.getChat());
	}

	/** REQ-UPD-16 again at re-arm: an instance that became launcher-managed or read-only is not re-armed. */
	@Test
	void anUpdateTheGameCanNoLongerInstallIsCleared() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		byte[] jar = TestJars.mod("2.0.1+26.2");
		Files.write(staged.resolve(V201), jar);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, TestJars.sha256(jar));
		eligibility = new UpdateHooks.Eligibility(false, InstallEligibility.MANAGED);
		LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
		assertNull(state.pendingInstall);
		assertTrue(hooks.isEmpty());
		assertNull(report.getToast());
		assertEquals(List.of("The update to v2.0.1 can't be installed here now: " + InstallEligibility.MANAGED), report.getChat());
		assertEquals(List.of(), stagedFiles());
	}

	/** A re-arm the installer refuses (the running jar renamed to the new jar's name) clears the record quietly. */
	@Test
	void aRefusedReArmIsCleared() throws IOException {
		Path running = Files.write(mods.resolve(V201.replace("k8bas", "K8BAS")), TestJars.mod("2.0.0+26.2"));
		byte[] jar = TestJars.mod("2.0.1+26.2");
		Files.write(staged.resolve(V201), jar);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, TestJars.sha256(jar));
		LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
		assertNull(state.pendingInstall);
		assertTrue(hooks.isEmpty());
		assertNull(report.getToast());
		assertEquals(List.of(), stagedFiles());
	}

	/** A record whose file name is no jar of this mod is never joined into a path; it is cleared. */
	@Test
	void aRecordNamingNoJarOfThisModIsCleared() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		for (String name : List.of("../../mods/fabric-api.jar", "notes.txt")) {
			Files.writeString(staged.resolve("stray.jar"), "x");
			helperRan();
			UpdateState state = new UpdateState();
			state.pendingInstall = new PendingInstall("2.0.1", name, "ab".repeat(32), 1L, "2.0.0+26.2", running.toString(), 0);
			LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
			assertNull(state.pendingInstall, name);
			assertTrue(hooks.isEmpty());
			assertNull(report.getToast());
			assertEquals(List.of(), stagedFiles());
			assertTrue(warnings.stream().anyMatch(line -> line.contains("names no jar of this mod")), warnings.toString());
		}
		assertTrue(Files.exists(mods.resolve("fabric-api.jar")));
	}

	/** REQ-UPD-18 orphans: a record without its staged jar, and staged files without a record, are cleared and logged quietly. */
	@Test
	void orphansAreClearedWithoutADialog() throws IOException {
		Path running = Files.write(mods.resolve(V200), TestJars.mod("2.0.0+26.2"));
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(running, 0, "ab".repeat(32));
		LaunchReconciler.Report report = launch("2.0.0+26.2", running, state);
		assertNull(state.pendingInstall);
		assertNull(report.getToast());
		assertEquals(List.of(), report.getChat());
		Files.writeString(staged.resolve(V201), "stray");
		launch("2.0.0+26.2", running, state);
		assertEquals(List.of(), stagedFiles());
		assertTrue(info.stream().anyMatch(line -> line.contains(V201)), info.toString());
	}

	/** A record that fits neither the running version nor the one that staged it (a jar swapped by hand) is cleared. */
	@Test
	void aRecordForAnotherVersionIsCleared() throws IOException {
		Path running = Files.write(mods.resolve("k8bas_skyblock_utility-2.0.5+26.2.jar"), TestJars.mod("2.0.5+26.2"));
		Files.write(staged.resolve(V201), TestJars.mod("2.0.1+26.2"));
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(mods.resolve(V200), 0, "ab".repeat(32));
		launch("2.0.5+26.2", running, state);
		assertNull(state.pendingInstall);
		assertEquals(List.of(), stagedFiles());
		assertTrue(hooks.isEmpty());
	}

	/** AC-UPD-18 (e), EC-UPD-15, R13: a second copy placed by hand is kept over two launches and named once, in the first. */
	@Test
	void anotherCopyIsKeptAndNamedOnce() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Files.write(mods.resolve("my-old-k8bas.jar"), TestJars.mod("1.3.0+26.2"));
		Files.write(mods.resolve("other.jar"), TestJars.jar(TestJars.modJson("other_mod", "1.0.0", "~26.2", ">=0.19.5", "client")));
		UpdateState state = new UpdateState();
		LaunchReconciler.Report first = launch("2.0.1+26.2", running, state);
		assertEquals(List.of("Another copy of K8bas Skyblock Utility is in your mods folder: my-old-k8bas.jar"), first.getChat());
		assertEquals(1, warnings.stream().filter(line -> line.contains("my-old-k8bas.jar")).count());
		LaunchReconciler.Report second = launch("2.0.1+26.2", running, state);
		assertEquals(List.of(), second.getChat());
		assertEquals(1, warnings.stream().filter(line -> line.contains("my-old-k8bas.jar")).count(), "one WARN over both launches");
		assertTrue(Files.exists(mods.resolve("my-old-k8bas.jar")));
		assertEquals(List.of("my-old-k8bas.jar"), state.warnedFiles);
	}

	/** A temporary copy a killed helper left in the mods folder never stays there; Fabric ignores it, but it is ours. */
	@Test
	void aLeftoverTemporaryCopyIsSwept() throws IOException {
		Path running = Files.write(mods.resolve(V201), TestJars.mod("2.0.1+26.2"));
		Path temporary = Files.writeString(mods.resolve(V201 + com.k8bas.skyblockutility.update.helper.UpdateHelper.TEMPORARY_SUFFIX), "half");
		launch("2.0.1+26.2", running, new UpdateState());
		assertFalse(Files.exists(temporary));
		assertTrue(Files.exists(mods.resolve("fabric-api.jar")));
	}

	/** A development run, or a mod nested in another jar, has no running jar: nothing in a mods folder is touched or scanned. */
	@Test
	void withoutARunningJarNoModsFolderIsTouched() throws IOException {
		Files.write(mods.resolve("my-old-k8bas.jar"), TestJars.mod("1.3.0+26.2"));
		UpdateState state = new UpdateState();
		LaunchReconciler.Report report = launch("2.0.1+26.2", null, state);
		assertEquals(List.of(), report.getChat());
		assertTrue(Files.exists(mods.resolve("my-old-k8bas.jar")));
	}

	/** The counts and the leftover survive a save, so the disarm after two failures works across launches; a T4.3 record reads 0. */
	@Test
	void theNewFieldsSurviveTheStateFile() throws IOException {
		Path file = dir.resolve(UpdateStateStore.FILE_NAME);
		UpdateStateStore store = new UpdateStateStore(file, warnings::add);
		UpdateState state = new UpdateState();
		state.pendingInstall = pending(mods.resolve(V200), 1, "ab".repeat(32));
		state.leftoverJar = mods.resolve("old.jar").toString();
		store.save(state);
		store.flush();
		UpdateState loaded = new UpdateStateStore(file, warnings::add).load();
		assertEquals(state.pendingInstall, loaded.pendingInstall);
		assertEquals(state.leftoverJar, loaded.leftoverJar);
		String older = Files.readString(file).replaceAll(",\\s*\"failedApplies\":\\s*1", "");
		assertFalse(older.contains("failedApplies"), older);
		Files.writeString(file, older);
		assertEquals(0, new UpdateStateStore(file, warnings::add).load().pendingInstall.failedApplies());
	}
}

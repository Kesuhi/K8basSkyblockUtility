package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.3 (REQ-UPD-12, REQ-UPD-16, REQ-UPD-17, AC-UPD-17, EC-UPD-04): a confirmed, checked download is armed for the next
 * exit and recorded as the pending install; a development run downloads nothing; cancelling disarms it and deletes the
 * staged files; a newer release found after arming does not replace the confirmed one.
 */
class UpdateInstallTest {
	private static final long T0 = 1_800_000_000_000L;
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final String RUNNING = "2.0.0+26.2";
	private static final String OLD = "k8bas_skyblock_utility-2.0.0+26.2.jar";
	private static final String NEW = "k8bas_skyblock_utility-2.0.1+26.2.jar";

	@TempDir
	Path dir;

	private Path mods;
	private Path runningJar;
	private Path root;
	private final List<List<String>> started = new CopyOnWriteArrayList<>();
	private final List<Thread> hooks = new ArrayList<>();
	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private UpdateHooks.EligibilityCheck eligibility;
	private long now = T0;

	@BeforeEach
	void setUp() throws IOException {
		mods = Files.createDirectories(dir.resolve("mods"));
		runningJar = Files.write(mods.resolve(OLD), TestJars.mod(RUNNING));
		root = dir.resolve(".k8bas_update");
		eligibility = new InstallEligibility(false, new InstallEligibility.Origin(true, List.of(runningJar)), RUNNING, dir, path -> true);
	}

	private UpdateStateStore store() {
		return new UpdateStateStore(dir.resolve("config").resolve(UpdateStateStore.FILE_NAME), warnings::add);
	}

	private UpdateService service(MockGitHub github) {
		int port = github.at("/").getPort();
		SecureDownloader downloader = new SecureDownloader(SharedHttpClient.get(), "test",
				uri -> "127.0.0.1".equals(uri.getHost()) && uri.getPort() == port, Duration.ofSeconds(5));
		GitHubReleaseSource source = new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), RUNNING, Duration.ofSeconds(5));
		return new UpdateService(source, store(), () -> now, () -> true, RUNNING, "26.2", () -> null, line -> { }, warnings::add,
				new UpdateHooks(new ReleaseHash(downloader, warnings::add), eligibility), new JarStager(root, downloader, "26.2", "0.19.5"),
				new StagedInstall(root, () -> runningJar, (command, directory) -> started.add(command), hooks::add, warnings::add));
	}

	private static String list(MockGitHub github, String version, byte[] jar) {
		String name = "k8bas_skyblock_utility-" + version + "+26.2.jar";
		return "{\"tag_name\":\"v" + version + "\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v" + version + "\",\"assets\":["
				+ "{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":" + jar.length + ",\"digest\":\"sha256:" + TestJars.sha256(jar)
				+ "\",\"browser_download_url\":\"" + github.at("/download/v" + version + "/" + name) + "\"}]}";
	}

	/** REQ-UPD-17: the checked jar waits in the staging folder, the update is armed and recorded; mods/ is untouched. */
	@Test
	void aConfirmedDownloadIsArmedAndRecorded() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			UpdateService service = service(github);
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			DownloadOutcome outcome = service.download(Consent.typed("2.0.1"));
			assertEquals(List.of("Update ready, restart Minecraft to apply"), outcome.lines(UTC));
			assertEquals(List.of(OLD), List.of(mods.toFile().list()), "mods/ untouched until the game exits");
			assertEquals(1, hooks.size());
			PendingInstall pending = store().load().pendingInstall;
			assertEquals(new PendingInstall("2.0.1", NEW, TestJars.sha256(jar), T0, RUNNING, runningJar.toString(), 0), pending);
		}
	}

	/**
	 * R53 (T4.5): as the game wires them, the notices read the service's pending install, so the staged version gets no
	 * toast; cancelled, it is on offer again.
	 */
	@Test
	void theNoticesKnowTheStagedVersion() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			UpdateService service = service(github);
			List<com.k8bas.skyblockutility.ui.notice.Notice> toasts = new CopyOnWriteArrayList<>();
			Updates.use(service, new UpdateNotifier(RUNNING, org.slf4j.LoggerFactory.getLogger("test"), toasts::add));
			service.checkManual();
			assertInstanceOf(DownloadOutcome.Staged.class, service.download(Consent.typed("2.0.1")));
			Updates.notifier().offer(service.offer());
			assertEquals(List.of(), toasts);
			assertEquals(UpdateService.Cancel.CANCELLED, service.cancelPending());
			Updates.notifier().offer(service.offer());
			assertEquals(1, toasts.size());
			// G4 review: skipped after its toast, the join's line no longer offers it.
			assertTrue(service.skip("2.0.1"));
			List<String> lines = new ArrayList<>();
			Updates.notifier().showJoinLine(component -> lines.add(component.getString()));
			assertEquals(List.of(), lines);
		}
	}

	/**
	 * G4 review: the card reads the last check and the pending install every frame; neither waits for the state lock, which
	 * the launch check holds while it reads the mods folder. The values are still the saved ones.
	 */
	@Test
	void theCardReadsNeverWaitForTheStateLock() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			UpdateService service = service(github);
			service.checkManual();
			service.download(Consent.typed("2.0.1"));
			java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
			Thread holder = Thread.ofPlatform().start(() -> {
				synchronized (service.state()) {
					held.countDown();
					try {
						release.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});
			try {
				held.await();
				PendingInstall pending = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), service::pendingInstall);
				long lastSuccess = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), service::lastSuccessMs);
				assertEquals("2.0.1", pending.version());
				assertEquals(T0, lastSuccess);
			} finally {
				release.countDown();
				holder.join();
			}
		}
	}

	/** AC-UPD-17: a development run is notify-only and writes nothing anywhere near its mods folder. */
	@Test
	void aDevelopmentRunDownloadsNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			eligibility = new InstallEligibility(true, new InstallEligibility.Origin(true, List.of(runningJar)), RUNNING, dir, path -> true);
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"));
			UpdateService service = service(github);
			CheckOutcome.Found found = assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertEquals(List.of("v2.0.1 is available", InstallEligibility.DEVELOPMENT), found.lines(UTC));
			assertEquals(List.of(InstallEligibility.DEVELOPMENT), service.download(Consent.typed("2.0.1")).lines(UTC));
			assertEquals(1, github.requests.size(), "only the release list");
			assertEquals(List.of(OLD), List.of(mods.toFile().list()));
			assertFalse(Files.exists(root), "no staging folder either");
		}
	}

	/** REQ-UPD-12, AC-UPD-13 (cancel): disarmed, the staged files deleted, the record cleared; the running jar untouched. */
	@Test
	void cancellingDisarmsAndDeletesTheStagedFiles() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			UpdateService service = service(github);
			service.checkManual();
			assertInstanceOf(DownloadOutcome.Staged.class, service.download(Consent.typed("2.0.1")));
			assertEquals(UpdateService.Cancel.CANCELLED, service.cancelPending());
			try (var staged = Files.list(root.resolve("staged"))) {
				assertEquals(0, staged.count(), "the staging folder is empty");
			}
			assertNull(store().load().pendingInstall);
			hooks.getFirst().run();
			assertTrue(started.isEmpty(), "no helper starts at exit");
			assertEquals(List.of(OLD), List.of(mods.toFile().list()));
			assertEquals(UpdateService.Cancel.NOTHING_PENDING, service.cancelPending(), "nothing left to cancel");
			assertNull(service.state().skippedVersion, "a cancel is not a skip");
		}
	}

	/** A cancel while a download runs changes nothing and says so; it never runs beside one. */
	@Test
	void aCancelDuringADownloadSaysSo() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar).gated(gate));
			UpdateService service = service(github);
			service.checkManual();
			java.util.concurrent.CompletableFuture<DownloadOutcome> download =
					java.util.concurrent.CompletableFuture.supplyAsync(() -> service.download(Consent.typed("2.0.1")));
			long until = System.nanoTime() + 5_000_000_000L;
			while (github.requests.size() < 2 && System.nanoTime() < until) {
				Thread.sleep(10);
			}
			assertEquals(UpdateService.Cancel.DOWNLOADING, service.cancelPending());
			assertEquals(List.of("An update is downloading, try again when it is done"), new UpdateActions(service, UTC).cancel());
			gate.countDown();
			assertInstanceOf(DownloadOutcome.Staged.class, download.get(10, java.util.concurrent.TimeUnit.SECONDS));
			assertEquals("2.0.1", store().load().pendingInstall.version(), "the busy cancel changed nothing");
		}
	}

	/** G4 review: a cancel while a check (or the launch check) holds the lock changes nothing and says the updater is busy. */
	@Test
	void aCancelDuringACheckSaysBusy() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
			github.enqueue(MockGitHub.Answer.json(200, "[]").gated(gate));
			UpdateService service = service(github);
			java.util.concurrent.CompletableFuture<CheckOutcome> check = java.util.concurrent.CompletableFuture.supplyAsync(service::checkManual);
			long until = System.nanoTime() + 5_000_000_000L;
			while (github.requests.isEmpty() && System.nanoTime() < until) {
				Thread.sleep(10);
			}
			assertEquals(UpdateService.Cancel.BUSY, service.cancelPending());
			assertEquals(List.of("The updater is busy, try again in a moment"), new UpdateActions(service, UTC).cancel());
			gate.countDown();
			check.get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
	}

	/** T4.3b through the service: the launch after an applied update finishes it, saves the state, and says so once. */
	@Test
	void theLaunchAfterAnAppliedUpdateFinishesIt() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			Path old = Files.write(mods.resolve("k8bas_skyblock_utility-1.9.0+26.2.jar"), TestJars.mod("1.9.0+26.2"));
			UpdateState state = new UpdateState();
			state.pendingInstall = new PendingInstall("2.0.0", OLD, "ab".repeat(32), T0, "1.9.0+26.2", old.toString(), 0);
			// The helper ran at the last exit: its marker is there (REQ-UPD-18).
			Files.writeString(Files.createDirectories(root).resolve(StagedInstall.ATTEMPT_MARKER), "1");
			UpdateStateStore before = store();
			before.save(state);
			before.flush();
			UpdateService service = service(github);
			LaunchReconciler.Report report = service.reconcileAtLaunch();
			assertEquals("Updated to v2.0.0", report.getToast());
			assertNull(store().load().pendingInstall, "saved");
			assertFalse(Files.exists(old));
			assertEquals(0, github.requests.size(), "no network");
		}
	}

	/** The launch's reconciliation waits for a running download rather than skip the launch. */
	@Test
	void theLaunchCheckWaitsForARunningDownload() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar).gated(gate));
			UpdateService service = service(github);
			service.checkManual();
			java.util.concurrent.CompletableFuture<DownloadOutcome> download =
					java.util.concurrent.CompletableFuture.supplyAsync(() -> service.download(Consent.typed("2.0.1")));
			long until = System.nanoTime() + 5_000_000_000L;
			while (github.requests.size() < 2 && System.nanoTime() < until) {
				Thread.sleep(10);
			}
			java.util.concurrent.CompletableFuture<LaunchReconciler.Report> launch =
					java.util.concurrent.CompletableFuture.supplyAsync(service::reconcileAtLaunch);
			Thread.sleep(300);
			assertFalse(launch.isDone(), "waiting for the download");
			gate.countDown();
			download.get(10, java.util.concurrent.TimeUnit.SECONDS);
			assertEquals("Update ready, restart Minecraft to apply", launch.get(10, java.util.concurrent.TimeUnit.SECONDS).getToast(),
					"then it ran, and found the update it had just armed");
		}
	}

	/** EC-UPD-04: 2.0.2 found after 2.0.1 was confirmed: 2.0.1 is applied at exit, 2.0.2 is only offered. */
	@Test
	void aNewerReleaseAfterConfirmingDoesNotReplaceTheConfirmedOne() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			byte[] newer = TestJars.mod("2.0.2+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar),
					MockGitHub.Answer.json(200, "[" + list(github, "2.0.2", newer) + "," + list(github, "2.0.1", jar) + "]"));
			UpdateService service = service(github);
			service.checkManual();
			service.download(Consent.typed("2.0.1"));
			now += CheckPolicy.HOUR;
			CheckOutcome.Found found = assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertEquals("2.0.2", found.candidate().version().toString());
			hooks.getFirst().run();
			List<String> command = started.getFirst();
			assertEquals(root.resolve("staged").resolve(NEW).toString(), command.get(command.indexOf("--staged") + 1));
			assertEquals("2.0.1", store().load().pendingInstall.version());
			assertEquals(3, github.requests.size(), "2.0.2 was not downloaded");
		}
	}

	/** A place the new jar may not go: nothing is armed, the checked jar is deleted again. */
	@Test
	void aRefusedPlaceArmsNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + list(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			runningJar = mods.resolve(NEW.replace("k8bas", "K8BAS"));
			UpdateService service = service(github);
			service.checkManual();
			DownloadOutcome.Failed failed = assertInstanceOf(DownloadOutcome.Failed.class, service.download(Consent.typed("2.0.1")));
			assertEquals("the new jar would replace the running jar", failed.reason());
			assertTrue(hooks.isEmpty());
			assertNull(store().load().pendingInstall);
			try (var staged = Files.list(root.resolve("staged"))) {
				assertEquals(0, staged.count());
			}
		}
	}
}

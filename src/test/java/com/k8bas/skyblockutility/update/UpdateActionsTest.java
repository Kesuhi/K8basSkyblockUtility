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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.4 (REQ-UPD-10, REQ-UPD-11, REQ-UPD-12, AC-UPD-12, AC-UPD-13 [A]): what `/ksu update check | install | skip | cancel`
 * do and say. A stale install is refused with the current candidate named and fetches nothing; skip needs a second step
 * and silences only that version; cancel disarms.
 */
class UpdateActionsTest {
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final String RUNNING = "2.0.0+26.2";

	@TempDir
	Path dir;

	private Path runningJar;
	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private final List<Thread> hooks = new ArrayList<>();
	private long now = 1_800_000_000_000L;

	@BeforeEach
	void setUp() throws IOException {
		runningJar = Files.write(Files.createDirectories(dir.resolve("mods")).resolve("k8bas_skyblock_utility-2.0.0+26.2.jar"), TestJars.mod(RUNNING));
	}

	private UpdateService service(MockGitHub github) {
		int port = github.at("/").getPort();
		SecureDownloader downloader = new SecureDownloader(SharedHttpClient.get(), "test",
				uri -> "127.0.0.1".equals(uri.getHost()) && uri.getPort() == port, Duration.ofSeconds(5));
		Path root = dir.resolve(".k8bas_update");
		return new UpdateService(new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), RUNNING, Duration.ofSeconds(5)),
				new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add), () -> now, () -> true, RUNNING, "26.2", () -> null,
				line -> { }, warnings::add, new UpdateHooks(new ReleaseHash(downloader, warnings::add),
				new InstallEligibility(false, new InstallEligibility.Origin(true, List.of(runningJar)), RUNNING, dir, path -> true)),
				new JarStager(root, downloader, "26.2", "0.19.5"), new StagedInstall(root, () -> runningJar, (command, directory) -> { }, hooks::add, warnings::add));
	}

	private static String release(MockGitHub github, String version, byte[] jar) {
		String name = "k8bas_skyblock_utility-" + version + "+26.2.jar";
		return "{\"tag_name\":\"v" + version + "\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v" + version + "\",\"assets\":["
				+ "{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":" + jar.length + ",\"digest\":\"sha256:" + TestJars.sha256(jar)
				+ "\",\"browser_download_url\":\"" + github.at("/download/" + name) + "\"}]}";
	}

	private static long jarRequests(MockGitHub github) {
		return github.requests.stream().filter(request -> request.pathAndQuery().endsWith(".jar")).count();
	}

	/**
	 * AC-UPD-12: candidate 2.0.1; `/ksu update install 2.0.0` is refused with 2.0.1 named, and the download host sees
	 * nothing. `install 2.0.1` downloads nothing either: it only opens the confirmation screen, whose click with the
	 * checksum it showed is the consent (a chat click from a server could run the command, review of T4.4).
	 */
	@Test
	void aTypedInstallNeverDownloadsAndAStaleOneIsRefused() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + release(github, "2.0.1", jar) + "]"));
			UpdateActions actions = new UpdateActions(service(github), UTC);
			assertEquals(List.of("v2.0.1 is available"), actions.check());
			assertEquals(List.of("v2.0.0 is not the update on offer; that is v2.0.1"), actions.refusalToInstall("2.0.0"));
			assertEquals(List.of("vfoo is not the update on offer; that is v2.0.1"), actions.refusalToInstall("foo"));
			assertNull(actions.refusalToInstall("v2.0.1"), "the candidate: the screen opens");
			assertEquals(0, jarRequests(github));
			github.enqueue(MockGitHub.Answer.bytes(200, jar));
			assertEquals(List.of("Update ready, restart Minecraft to apply"), actions.install(new Consent("2.0.1", TestJars.sha256(jar))));
			assertEquals(1, jarRequests(github));
		}
	}

	/** EC-UPD-11: the screen's click from an earlier offer (2.0.1) after 2.0.2 came out is refused with 2.0.2 named. */
	@Test
	void aClickFromAnEarlierOfferIsRefused() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] old = TestJars.mod("2.0.1+26.2");
			byte[] newer = TestJars.mod("2.0.2+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + release(github, "2.0.2", newer) + "," + release(github, "2.0.1", old) + "]"));
			UpdateActions actions = new UpdateActions(service(github), UTC);
			actions.check();
			assertEquals(List.of("v2.0.1 is not the update on offer; that is v2.0.2"), actions.install(new Consent("2.0.1", TestJars.sha256(old))));
			assertEquals(0, jarRequests(github));
		}
	}

	/** REQ-UPD-12, AC-UPD-13: skip needs its second step, then stores the version; the manual check still shows it, marked; a newer one is not skipped. */
	@Test
	void skipNeedsASecondStepAndSilencesOnlyThatVersion() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + release(github, "2.0.1", jar) + "]"));
			UpdateService service = service(github);
			UpdateActions actions = new UpdateActions(service, UTC);
			actions.check();
			assertEquals(List.of("Skip v2.0.1? Its notices stay silent until a newer version is out"), actions.skip("2.0.1", false));
			assertNull(service.state().skippedVersion, "not yet");
			assertEquals(List.of("Skipped v2.0.1; a newer version notifies again"), actions.skip("v2.0.1", true));
			assertEquals("2.0.1", service.state().skippedVersion);
			CheckOutcome.Found found = assertInstanceOf(CheckOutcome.Found.class, service.offer());
			assertTrue(found.skipped(), "the card and the manual check still show it, marked");
			assertEquals(List.of("foo is not a version"), actions.skip("foo", true));
			// A skip link from another offer changes nothing (EC-UPD-11).
			assertEquals(List.of("v99.0.0 is not the update on offer; that is v2.0.1"), actions.skip("99.0.0", true));
			assertEquals(List.of("v2.0.0 is not the update on offer; that is v2.0.1"), actions.skip("2.0.0", true));
			assertEquals("2.0.1", service.state().skippedVersion);
			// AC-UPD-13: a higher version than the skipped one is offered unmarked.
			service.state().releases = new java.util.ArrayList<>(GitHubReleaseSource.parseReleases(com.google.gson.JsonParser.parseString(
					"[" + release(github, "2.0.2", TestJars.mod("2.0.2+26.2")) + "," + release(github, "2.0.1", jar) + "]").getAsJsonArray()));
			CheckOutcome.Found newer = assertInstanceOf(CheckOutcome.Found.class, service.offer());
			assertEquals("2.0.2", newer.candidate().version().toString());
			assertTrue(!newer.skipped(), "2.0.2 notifies again");
		}
	}

	/** REQ-UPD-12: cancel says what it did. */
	@Test
	void cancelSaysWhatItDid() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.json(200, "[" + release(github, "2.0.1", jar) + "]"), MockGitHub.Answer.bytes(200, jar));
			UpdateActions actions = new UpdateActions(service(github), UTC);
			assertEquals(List.of("No update is pending"), actions.cancel());
			actions.check();
			actions.install(new Consent("2.0.1", TestJars.sha256(jar)));
			assertEquals(List.of("The pending update is cancelled; nothing is installed when you quit"), actions.cancel());
		}
	}

	/** `/ksu update show` with nothing on offer says so rather than open an empty screen. */
	@Test
	void showWithNothingOnOffer() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdateActions actions = new UpdateActions(service(github), UTC);
			assertNull(actions.showable());
			assertEquals(List.of("No update is known yet; /ksu update check looks for one"), actions.nothingToShow());
		}
	}
}

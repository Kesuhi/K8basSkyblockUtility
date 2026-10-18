package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.2 (REQ-UPD-11, REQ-UPD-13, REQ-UPD-14, REQ-UPD-15, REQ-UPD-20): a confirmed download. AC-UPD-12 (no jar byte before
 * consent, a stale version refused with the current one named; the sidecar may be read at the check, R49), AC-UPD-14
 * (each broken transfer leaves no `.part` and the game folder byte-identical), AC-UPD-15 (the hash sources), AC-UPD-16
 * through the service, EC-UPD-03 and the download-host part of AC-UPD-21. Hosts are local mocks; the GitHub host rule
 * itself is {@link SecureDownloaderTest}'s.
 */
class UpdateDownloadTest {
	private static final long T0 = 1_800_000_000_000L;
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final String RUNNING = "2.0.0+26.2";
	private static final String JAR = "k8bas_skyblock_utility-2.0.1+26.2.jar";
	private static final String STAGED = ".k8bas_update" + java.io.File.separator + "staged" + java.io.File.separator + JAR;

	@TempDir
	Path dir;

	private Path staging;
	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private final List<String> info = new CopyOnWriteArrayList<>();
	private final Set<Integer> allowedPorts = new java.util.concurrent.CopyOnWriteArraySet<>();
	private UpdateHooks.Eligibility eligibility = UpdateHooks.Eligibility.ELIGIBLE;

	@BeforeEach
	void setUp() throws IOException {
		Path mods = Files.createDirectories(dir.resolve("mods"));
		Files.write(mods.resolve("k8bas_skyblock_utility-2.0.0+26.2.jar"), TestJars.mod(RUNNING));
		Files.writeString(mods.resolve("other-mod.jar"), "other");
		Files.writeString(Files.createDirectories(dir.resolve("config")).resolve("k8bas.json"), "{}");
		staging = dir.resolve(".k8bas_update");
	}

	private UpdateService service(MockGitHub github, List<Release> releases) {
		allowedPorts.add(github.at("/").getPort());
		SecureDownloader downloader = new SecureDownloader(SharedHttpClient.get(), "k8bas_skyblock_utility/" + RUNNING + " (+https://github.com/Kesuhi/K8basSkyblockUtility)",
				uri -> "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && allowedPorts.contains(uri.getPort()), Duration.ofSeconds(5));
		UpdateHooks hooks = new UpdateHooks(new ReleaseHash(downloader, warnings::add), () -> eligibility);
		UpdateStateStore store = new UpdateStateStore(dir.resolve("config").resolve(UpdateStateStore.FILE_NAME), warnings::add);
		GitHubReleaseSource source = new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), RUNNING, Duration.ofSeconds(5));
		UpdateService service = new UpdateService(source, store, () -> T0, () -> true, RUNNING, "26.2", () -> null, info::add, warnings::add,
				hooks, new JarStager(staging, downloader, "26.2", "0.19.5"),
				new StagedInstall(staging, () -> dir.resolve("mods").resolve("k8bas_skyblock_utility-2.0.0+26.2.jar"), (command, directory) -> { }, thread -> { }, warnings::add));
		service.state().releases = new ArrayList<>(releases);
		return service;
	}

	/** v2.0.1 with its jar at {@code jarUrl}; {@code digest} as GitHub would give it, and a sidecar at the mock if not null. */
	private static List<Release> release(URI jarUrl, MockGitHub github, long size, String digest, byte[] sidecar) {
		List<Release.Asset> assets = new ArrayList<>();
		assets.add(new Release.Asset(JAR, "uploaded", jarUrl.toString(), digest, size));
		if (sidecar != null) {
			assets.add(new Release.Asset(JAR + ".sha256", "uploaded", github.at("/download/v2.0.1/" + JAR + ".sha256").toString(), null, sidecar.length));
		}
		return List.of(new Release("v2.0.1", "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v2.0.1", false, false, assets));
	}

	private static List<Release> release(MockGitHub github, byte[] jar, String digest, byte[] sidecar) {
		return release(github.at("/download/v2.0.1/" + JAR), github, jar.length, digest, sidecar);
	}

	private static byte[] sidecar(String hex) {
		return (hex + "  " + JAR + "\n").getBytes(StandardCharsets.UTF_8);
	}

	/** Every file in the game folder with its SHA-256, the staged jar left out. */
	private Map<String, String> gameFolder() throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				String name = dir.relativize(file).toString();
				if (!name.equals(STAGED)) {
					files.put(name, TestJars.sha256(Files.readAllBytes(file)));
				}
			}
		}
		return files;
	}

	private List<String> stagedFiles() throws IOException {
		Path folder = staging.resolve("staged");
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> list = Files.list(folder)) {
			return list.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}

	private static List<String> paths(MockGitHub github) {
		return github.requests.stream().map(MockGitHub.Request::pathAndQuery).toList();
	}

	/** AC-UPD-15 first row: the digest matches, the jar is checked and staged under its asset name; nothing else changes. */
	@Test
	void aMatchingDigestStagesTheJar() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(TestJars.sha256(jar))), MockGitHub.Answer.bytes(200, jar));
			Map<String, String> before = gameFolder();
			DownloadOutcome outcome = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), sidecar(TestJars.sha256(jar))))
					.download(new Consent("2.0.1", TestJars.sha256(jar).toUpperCase()));
			DownloadOutcome.Staged staged = assertInstanceOf(DownloadOutcome.Staged.class, outcome);
			assertEquals(staging.resolve("staged").resolve(JAR), staged.file());
			assertEquals(TestJars.sha256(jar), staged.sha256());
			assertArrayEquals(jar, Files.readAllBytes(staged.file()));
			assertEquals(List.of(JAR), stagedFiles(), "no .part left");
			Map<String, String> after = gameFolder();
			Set<String> added = new java.util.TreeSet<>(after.keySet());
			added.removeAll(before.keySet());
			assertEquals(Set.of(".k8bas_update" + java.io.File.separator + "helper" + java.io.File.separator
							+ "com/k8bas/skyblockutility/update/helper/UpdateHelper.class".replace("/", java.io.File.separator),
					"config" + java.io.File.separator + UpdateStateStore.FILE_NAME), added, "only the helper and the update state are new");
			before.forEach((name, sha) -> assertEquals(sha, after.get(name), name + " is unchanged"));
			assertEquals(List.of("Update ready, restart Minecraft to apply"), outcome.lines(UTC));
			assertEquals(List.of("/download/v2.0.1/" + JAR + ".sha256", "/download/v2.0.1/" + JAR), paths(github));
		}
	}

	/**
	 * AC-UPD-12: a check finds the candidate and reads at most its sidecar, never the jar (R49); a stale version is refused
	 * with the current one named, before anything is fetched.
	 */
	@Test
	void noJarByteWithoutConsentForTheCurrentCandidate() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			String list = "[{\"tag_name\":\"v2.0.1\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v2.0.1\",\"assets\":["
					+ "{\"name\":\"" + JAR + "\",\"state\":\"uploaded\",\"size\":" + jar.length + ",\"digest\":\"sha256:" + TestJars.sha256(jar)
					+ "\",\"browser_download_url\":\"" + github.at("/download/v2.0.1/" + JAR) + "\"},"
					+ "{\"name\":\"" + JAR + ".sha256\",\"state\":\"uploaded\",\"size\":" + sidecar(TestJars.sha256(jar)).length
					+ ",\"browser_download_url\":\"" + github.at("/download/v2.0.1/" + JAR + ".sha256") + "\"}]}]";
			github.enqueue(MockGitHub.Answer.json(200, list), MockGitHub.Answer.bytes(200, sidecar(TestJars.sha256(jar))));
			UpdateService service = service(github, List.of());
			CheckOutcome.Found found = assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertTrue(found.installOffered());
			assertFalse(paths(github).stream().anyMatch(path -> path.endsWith(".jar")), "the jar was not asked for: " + paths(github));
			int afterCheck = github.requests.size();
			DownloadOutcome stale = service.download(Consent.typed("2.0.0"));
			assertInstanceOf(DownloadOutcome.Stale.class, stale);
			assertEquals(List.of("v2.0.0 is not the update on offer; that is v2.0.1"), stale.lines(UTC));
			assertEquals(List.of("v2.0.2 is not the update on offer; that is v2.0.1"), service.download(Consent.typed("v2.0.2")).lines(UTC));
			assertEquals(List.of("vfoo is not the update on offer; that is v2.0.1"), service.download(Consent.typed("foo")).lines(UTC));
			service.state().releases = new ArrayList<>();
			assertEquals(List.of("v2.0.1 is not the update on offer; there is none"), service.download(Consent.typed("2.0.1")).lines(UTC));
			assertEquals(afterCheck, github.requests.size(), "a refused click sends nothing");
			assertEquals(List.of(), stagedFiles());
		}
	}

	/** Review (T4.2): the screen's click carries the SHA-256 it showed; a file changed since is refused, not fetched. */
	@Test
	void aConfirmationForOtherBytesIsRefused() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			DownloadOutcome changed = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), null))
					.download(new Consent("2.0.1", "ab".repeat(32)));
			assertInstanceOf(DownloadOutcome.Changed.class, changed);
			assertEquals(List.of("v2.0.1 changed on GitHub since you saw it; open it again to confirm"), changed.lines(UTC));
			assertEquals(0, github.requests.size());
		}
	}

	/** AC-UPD-15: a digest that does not match the bytes: the `.part` is deleted, nothing else changes. */
	@Test
	void aDigestMismatchChangesNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			github.enqueue(MockGitHub.Answer.bytes(200, jar));
			Map<String, String> before = gameFolder();
			DownloadOutcome outcome = service(github, release(github, jar, "sha256:" + "ab".repeat(32), null)).download(Consent.typed("2.0.1"));
			assertInstanceOf(DownloadOutcome.ChecksumMismatch.class, outcome);
			assertEquals(List.of("Checksum mismatch, nothing changed"), outcome.lines(UTC));
			assertEquals(List.of(), stagedFiles());
			assertEquals(before, gameFolder());
		}
	}

	/** AC-UPD-15: no digest but a sidecar with a BOM, and a sha512 digest with a sidecar, both stage. */
	@Test
	void theSidecarStagesWhenTheDigestCannot() throws Exception {
		byte[] jar = TestJars.mod("2.0.1+26.2");
		byte[] bomSidecar = new byte[sidecar(TestJars.sha256(jar)).length + 3];
		bomSidecar[0] = (byte) 0xEF;
		bomSidecar[1] = (byte) 0xBB;
		bomSidecar[2] = (byte) 0xBF;
		System.arraycopy(sidecar(TestJars.sha256(jar)), 0, bomSidecar, 3, bomSidecar.length - 3);
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, bomSidecar), MockGitHub.Answer.bytes(200, jar));
			assertInstanceOf(DownloadOutcome.Staged.class, service(github, release(github, jar, null, bomSidecar)).download(Consent.typed("2.0.1")));
			assertTrue(paths(github).getFirst().endsWith(".sha256"), paths(github).toString());
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(TestJars.sha256(jar))), MockGitHub.Answer.bytes(200, jar));
			assertInstanceOf(DownloadOutcome.Staged.class,
					service(github, release(github, jar, "sha512:" + "ef".repeat(64), sidecar(TestJars.sha256(jar)))).download(Consent.typed("2.0.1")));
			assertTrue(paths(github).getFirst().endsWith(".sha256"), paths(github).toString());
		}
	}

	/** AC-UPD-15: no SHA-256 at all, two that disagree, or a sidecar unreadable now: no install, and no jar byte fetched. */
	@Test
	void noUsableHashMeansNoDownload() throws Exception {
		byte[] jar = TestJars.mod("2.0.1+26.2");
		try (MockGitHub github = new MockGitHub()) {
			DownloadOutcome missing = service(github, release(github, jar, null, null)).download(Consent.typed("2.0.1"));
			assertInstanceOf(DownloadOutcome.NotOffered.class, missing);
			assertEquals(List.of("It has no SHA-256 checksum, so install it by hand from the release page"), missing.lines(UTC));
			assertEquals(0, github.requests.size());
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar("cd".repeat(32))));
			DownloadOutcome conflict = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), sidecar("cd".repeat(32))))
					.download(Consent.typed("2.0.1"));
			assertEquals(List.of("Release checksum conflict, install it by hand from the release page"), conflict.lines(UTC));
			assertEquals(List.of("/download/v2.0.1/" + JAR + ".sha256"), paths(github), "only the sidecar");
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(503, new byte[0]));
			DownloadOutcome unreadable = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), sidecar(TestJars.sha256(jar))))
					.download(Consent.typed("2.0.1"));
			assertEquals(List.of("Couldn't read its checksum file from GitHub, try again later"), unreadable.lines(UTC));
			assertEquals(List.of("/download/v2.0.1/" + JAR + ".sha256"), paths(github), "only the sidecar");
		}
	}

	/** Not install-eligible (T4.3 decides): the reason, and no jar byte. */
	@Test
	void anIneligibleGameDownloadsNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			eligibility = UpdateHooks.NOTIFY_ONLY;
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(TestJars.sha256(jar))));
			DownloadOutcome outcome = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), sidecar(TestJars.sha256(jar))))
					.download(Consent.typed("2.0.1"));
			assertEquals(List.of("Install it by hand from the release page"), outcome.lines(UTC));
			assertFalse(paths(github).stream().anyMatch(path -> path.endsWith(".jar")), paths(github).toString());
		}
	}

	/** AC-UPD-16 through the service: a jar for the wrong game is deleted and reported as "incompatible build". */
	@Test
	void anIncompatibleJarIsDeleted() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.jar(TestJars.modJson("k8bas_skyblock_utility", "2.0.1+26.2", "~26.1", ">=0.19.5", "client"));
			github.enqueue(MockGitHub.Answer.bytes(200, jar));
			Map<String, String> before = gameFolder();
			DownloadOutcome outcome = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), null)).download(Consent.typed("2.0.1"));
			DownloadOutcome.Incompatible incompatible = assertInstanceOf(DownloadOutcome.Incompatible.class, outcome);
			assertEquals("it needs Minecraft ~26.1, not 26.2", incompatible.reason());
			assertEquals(List.of("Incompatible build, nothing changed"), outcome.lines(UTC));
			assertEquals(List.of(), stagedFiles());
			assertEquals(before, gameFolder());
		}
	}

	/** AC-UPD-14: each broken transfer aborts for its own reason, deletes the `.part` and leaves the game folder byte-identical. */
	@Test
	void eachBrokenTransferChangesNothing() throws Exception {
		byte[] jar = TestJars.mod("2.0.1+26.2");
		String digest = "sha256:" + TestJars.sha256(jar);
		record Case(String reason, int requests, MockGitHub.Answer... answers) {
		}
		List<java.util.function.Function<MockGitHub, Case>> cases = List.of(
				github -> new Case("a redirect to http://localhost is not allowed", 1,
						MockGitHub.Answer.redirect(302, "http://localhost:" + github.at("/").getPort() + "/elsewhere.jar")),
				github -> new Case("HTTP 404", 1, MockGitHub.Answer.bytes(404, "{}".getBytes(StandardCharsets.UTF_8))),
				github -> new Case("Content-Length " + (jar.length + 1) + " is not the asset's size " + jar.length, 1,
						MockGitHub.Answer.bytes(200, java.util.Arrays.copyOf(jar, jar.length + 1))),
				github -> new Case("the body is longer than the asset's " + jar.length + " bytes", 1,
						MockGitHub.Answer.bytes(200, java.util.Arrays.copyOf(jar, jar.length + 1)).chunked()),
				github -> {
					List<MockGitHub.Answer> hops = new ArrayList<>();
					for (int i = 1; i <= 6; i++) {
						hops.add(MockGitHub.Answer.redirect(302, github.at("/hop" + i).toString()));
					}
					return new Case("more than 5 redirects", 6, hops.toArray(MockGitHub.Answer[]::new));
				});
		for (java.util.function.Function<MockGitHub, Case> make : cases) {
			try (MockGitHub github = new MockGitHub()) {
				Case broken = make.apply(github);
				github.enqueue(broken.answers());
				Map<String, String> before = gameFolder();
				DownloadOutcome outcome = service(github, release(github, jar, digest, null)).download(Consent.typed("2.0.1"));
				assertEquals(new DownloadOutcome.Failed(broken.reason()), outcome);
				assertEquals(List.of("Download failed, nothing changed"), outcome.lines(UTC));
				assertEquals(broken.requests(), github.requests.size(), broken.reason());
				assertEquals(List.of(), stagedFiles(), broken.reason());
				assertEquals(before, gameFolder(), broken.reason());
			}
		}
		try (MockGitHub github = new MockGitHub()) {
			List<Release> huge = release(github.at("/download/" + JAR), github, SecureDownloader.MAX_JAR_BYTES + 1, digest, null);
			assertEquals(new DownloadOutcome.Failed("the asset's size 33554433 is over the 33554432-byte cap"), service(github, huge).download(Consent.typed("2.0.1")));
			assertEquals(0, github.requests.size(), "over 32 MiB: not asked for");
		}
	}

	/** EC-UPD-03: a connection dropped mid-body leaves nothing, the install can be tried again, and a `.part` left by a closed game goes at the next launch. */
	@Test
	void aDroppedDownloadCanBeRetriedAndLeftoversGoAtLaunch() throws Exception {
		byte[] jar = TestJars.mod("2.0.1+26.2");
		try (MockGitHub github = new MockGitHub(); RawServer cdn = new RawServer()) {
			allowedPorts.add(cdn.port());
			cdn.enqueue(RawServer.Answer.droppedMidBody(jar), RawServer.Answer.full(jar));
			UpdateService service = service(github, release(cdn.at("/" + JAR), github, jar.length, "sha256:" + TestJars.sha256(jar), null));
			Map<String, String> before = gameFolder();
			DownloadOutcome.Failed dropped = assertInstanceOf(DownloadOutcome.Failed.class, service.download(Consent.typed("2.0.1")));
			assertTrue(dropped.reason().startsWith("the body broke off"), dropped.reason());
			assertEquals(List.of(), stagedFiles());
			assertEquals(before, gameFolder());
			assertInstanceOf(DownloadOutcome.Staged.class, service.download(Consent.typed("2.0.1")), "the lock was released");
		}
		Path left = staging.resolve("staged").resolve(JAR + ".part");
		Files.writeString(left, "half");
		new JarStager(staging, null, "26.2", "0.19.5").cleanParts();
		assertFalse(Files.exists(left));
		assertTrue(Files.exists(staging.resolve("staged").resolve(JAR)), "a checked jar is T4.3b's to judge");
	}

	/** REQ-UPD-04: one check or download at a time; each answers what holds the lock. */
	@Test
	void aDownloadAndACheckShareTheLock() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			CountDownLatch gate = new CountDownLatch(1);
			github.enqueue(MockGitHub.Answer.bytes(200, jar).gated(gate));
			UpdateService service = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), null));
			CompletableFuture<DownloadOutcome> first = CompletableFuture.supplyAsync(() -> service.download(Consent.typed("2.0.1")));
			awaitRequests(github, 1);
			assertEquals(List.of("An update is already downloading"), service.download(Consent.typed("2.0.1")).lines(UTC));
			assertEquals(List.of("An update is downloading, try again when it is done"), service.checkManual().lines(UTC));
			service.triggerAutomatic();
			gate.countDown();
			assertInstanceOf(DownloadOutcome.Staged.class, first.get(10, TimeUnit.SECONDS));
			Thread.sleep(200);
			assertEquals(1, github.requests.size(), "nothing else was sent");
		}
		try (MockGitHub github = new MockGitHub()) {
			byte[] jar = TestJars.mod("2.0.1+26.2");
			CountDownLatch gate = new CountDownLatch(1);
			github.enqueue(MockGitHub.Answer.json(200, "[]").gated(gate));
			UpdateService service = service(github, release(github, jar, "sha256:" + TestJars.sha256(jar), null));
			CompletableFuture<CheckOutcome> check = CompletableFuture.supplyAsync(service::checkManual);
			awaitRequests(github, 1);
			assertEquals(List.of("An update check is running, try again when it is done"), service.download(Consent.typed("2.0.1")).lines(UTC));
			gate.countDown();
			check.get(10, TimeUnit.SECONDS);
			assertEquals(1, github.requests.size());
		}
	}

	private static void awaitRequests(MockGitHub github, int count) throws InterruptedException {
		long until = System.nanoTime() + 5_000_000_000L;
		while (github.requests.size() < count && System.nanoTime() < until) {
			Thread.sleep(10);
		}
		assertEquals(count, github.requests.size(), "requests arrived");
	}

	/**
	 * AC-REL-09 (for T7.4's draft check): the updater's chain, selection → digest and sidecar → jar check, runs from a test
	 * on a release object, a local jar and its sidecar, with no network.
	 */
	@Test
	void theSelectionDigestAndJarChecksRunOnALocalJar() throws Exception {
		byte[] bytes = TestJars.mod("2.0.1+26.2");
		Path jar = Files.write(dir.resolve(JAR), bytes);
		Path sidecar = Files.write(dir.resolve(JAR + ".sha256"), sidecar(TestJars.sha256(bytes)));
		List<Release> releases = List.of(new Release("v2.0.1", "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v2.0.1", false, false,
				List.of(new Release.Asset(JAR, "uploaded", "https://github.com/x/" + JAR, "sha256:" + TestJars.sha256(bytes), bytes.length))));
		CandidateFinder.Candidate candidate = assertInstanceOf(CandidateFinder.Candidate.class,
				CandidateFinder.find(releases, RUNNING, "26.2", UpdateChannel.STABLE, line -> { }));
		UpdateHooks.Hash.Verifiable digest = assertInstanceOf(UpdateHooks.Hash.Verifiable.class, UpdateHooks.fromDigest(candidate.jar().digest()));
		assertEquals(TestJars.sha256(Files.readAllBytes(jar)), digest.sha256());
		assertEquals(digest.sha256(), ReleaseHash.parseSidecar(Files.readAllBytes(sidecar)));
		assertEquals("2.0.1+26.2", AssetSelector.declaredVersion(candidate.jar().name()));
		assertNull(JarValidator.problem(jar, AssetSelector.declaredVersion(candidate.jar().name()), "26.2", "0.19.5"));
	}

	/** Defence in depth: only a jar name of this mod is staged, so nothing can be written outside the staging folder. */
	@Test
	void onlyAJarNameOfThisModIsStaged() throws Exception {
		JarStager stager = new JarStager(staging, null, "26.2", "0.19.5");
		Map<String, String> before = gameFolder();
		for (String name : List.of("../k8bas_skyblock_utility-2.0.1+26.2.jar", "..\\k8bas_skyblock_utility-2.0.1+26.2.jar", "/abs.jar",
				"C:\\abs.jar", "sub/k8bas_skyblock_utility-2.0.1+26.2.jar", ".hidden.jar", "", "k8bas_skyblock_utility-2.0.1+26.2.jar\u0000.txt")) {
			Release.Asset asset = new Release.Asset(name, "uploaded", "https://github.com/x", "sha256:" + "ab".repeat(32), 10);
			DownloadOutcome.Failed failed = assertInstanceOf(DownloadOutcome.Failed.class, stager.stage("2.0.1", asset, "ab".repeat(32)), name);
			assertTrue(failed.reason().startsWith("the asset name "), failed.reason());
		}
		assertEquals(before, gameFolder());
	}
}

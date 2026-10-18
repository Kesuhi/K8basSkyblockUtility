package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.1 (REQ-UPD-04, REQ-UPD-08, REQ-UPD-09, REQ-UPD-19): the manual check and its outcomes. AC-UPD-04 manual rows (the 60 s
 * cooldown, one check in flight), AC-UPD-09's manual row (no request inside a rate-limit wait, its HH:mm), AC-UPD-07's
 * manual "no build" text, AC-UPD-10 [A] manual part ("Couldn't reach GitHub" within 25 s) and AC-UPD-20's state-file part.
 */
class UpdateManualCheckTest {
	private static final long T0 = 1_800_000_000_000L;
	private static final ZoneId UTC = ZoneId.of("UTC");

	/** One release, v1.2.0 for 26.2, with a usable SHA-256 digest. */
	static final String LIST = GitHubReleaseSourceTest.LIST.replace("sha256:abc", "sha256:" + "ab".repeat(32));

	@TempDir
	Path dir;

	private final AtomicLong clock = new AtomicLong(T0);
	private final AtomicBoolean toggle = new AtomicBoolean(true);
	private final List<String> info = new CopyOnWriteArrayList<>();
	private final List<String> warnings = new CopyOnWriteArrayList<>();

	private UpdateService service(URI uri, String running) {
		return service(uri, running, Duration.ofSeconds(5));
	}

	private UpdateService service(URI uri, String running, Duration timeout) {
		UpdateStateStore store = new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add);
		GitHubReleaseSource source = new GitHubReleaseSource(uri, SharedHttpClient.get(), running, timeout);
		return new UpdateService(source, store, clock::get, toggle::get, running, "26.2", () -> null, info::add, warnings::add);
	}

	/** AC-UPD-04: two manual checks within 60 s send one request; the second gets the cooldown message. */
	@Test
	void aSecondManualCheckWithin60sIsACooldown() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			clock.addAndGet(59 * CheckPolicy.SECOND);
			CheckOutcome second = service.checkManual();
			assertInstanceOf(CheckOutcome.Cooldown.class, second);
			assertEquals(List.of("Checked a moment ago, try again in 1 s"), second.lines(UTC));
			assertEquals(1, github.requests.size());
			// 60 s after the first, a manual check runs again, the 6 h interval notwithstanding (REQ-UPD-09).
			clock.addAndGet(CheckPolicy.SECOND);
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertEquals(2, github.requests.size());
			assertEquals(0, service.state().automaticRequestsMs.size(), "manual checks do not count against the automatic cap");
		}
	}

	/** AC-UPD-04: a manual check in flight: an automatic one is skipped and another manual one answers "already running". */
	@Test
	void whileAManualCheckIsInFlightNothingElseRuns() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST).delayed(1_000));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			CompletableFuture<CheckOutcome> first = CompletableFuture.supplyAsync(service::checkManual);
			awaitRequests(github, 1);
			service.triggerAutomatic();
			CheckOutcome busy = service.checkManual();
			assertInstanceOf(CheckOutcome.Busy.class, busy);
			assertEquals(List.of("An update check is already running"), busy.lines(UTC));
			assertInstanceOf(CheckOutcome.Found.class, first.get(5, TimeUnit.SECONDS));
			Thread.sleep(300);
			assertEquals(1, github.requests.size());
		}
	}

	/** AC-UPD-04: an automatic check in flight: a manual one answers "already running" and sends nothing. */
	@Test
	void whileAnAutomaticCheckIsInFlightAManualOneIsBusy() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST).delayed(1_000));
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			service.triggerAutomatic();
			awaitRequests(github, 1);
			assertInstanceOf(CheckOutcome.Busy.class, service.checkManual());
			Thread.sleep(1_300);
			assertEquals(1, github.requests.size(), "the busy manual check sent nothing");
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual(), "free again once the automatic check ended");
		}
	}

	/** REQ-UPD-04: with the toggle OFF there is no automatic request, but a manual check still runs. */
	@Test
	void aManualCheckRunsWithTheToggleOff() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			toggle.set(false);
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			assertInstanceOf(CheckOutcome.Found.class, service(github.releases(), "1.1.0+26.2").checkManual());
			assertEquals(1, github.requests.size());
		}
	}

	/** REQ-UPD-09 clock rule: a last manual check stored in the future (a wrong clock) does not hold the next one back. */
	@Test
	void aLastManualCheckInTheFutureIsNoCooldown() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			service.state().lastManualMs = T0 + CheckPolicy.HOUR;
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertEquals(1, github.requests.size());
		}
	}

	/** Review (T4.1): a wait saved before T4.1 has no kind; it counts as a rate limit, so a manual check waits it out. */
	@Test
	void aWaitSavedWithoutAKindHoldsAManualCheckBack() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			service.state().nextAllowedMs = T0 + CheckPolicy.HOUR;
			service.state().waitKind = null;
			assertInstanceOf(CheckOutcome.RateLimited.class, service.checkManual());
			assertEquals(0, github.requests.size());
		}
	}

	private static void awaitRequests(MockGitHub github, int count) throws InterruptedException {
		long until = System.nanoTime() + 5_000_000_000L;
		while (github.requests.size() < count && System.nanoTime() < until) {
			Thread.sleep(10);
		}
		assertEquals(count, github.requests.size(), "requests arrived");
	}

	/** AC-UPD-09: inside a rate-limit wait, a manual check sends nothing and says when to try again, in local time. */
	@Test
	void aManualCheckInsideARateLimitWaitSendsNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(403, "{}", "retry-after", "3600"));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			CheckOutcome limited = service.checkManual();
			assertInstanceOf(CheckOutcome.RateLimited.class, limited);
			assertEquals(1, github.requests.size());
			clock.addAndGet(2 * CheckPolicy.MINUTE);
			CheckOutcome again = service.checkManual();
			assertInstanceOf(CheckOutcome.RateLimited.class, again);
			assertEquals(1, github.requests.size(), "no request inside the wait");
			// T0 is 2027-01-15T08:00:00Z; the wait ends an hour later.
			assertEquals(List.of("GitHub rate limit reached, try again after 09:00"), again.lines(UTC));
			assertEquals(List.of("GitHub rate limit reached, try again after 10:00"), again.lines(ZoneId.of("Europe/Berlin")));
		}
	}

	/** REQ-UPD-08: other errors give their own texts, and a back-off after them does not hold a manual check back. */
	@Test
	void errorsHaveTheirTexts() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(500, "{}"));
			github.enqueue(MockGitHub.Answer.json(500, "{}"));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			CheckOutcome failed = service.checkManual();
			assertEquals(List.of("Couldn't reach GitHub"), failed.lines(UTC));
			assertEquals(1, warnings.size(), "one WARN line: " + warnings);
			clock.addAndGet(CheckPolicy.MINUTE);
			service.checkManual();
			// The second 5xx set a 2 min wait; 1 min into it a manual check still goes out.
			clock.addAndGet(CheckPolicy.MINUTE);
			assertTrue(CheckPolicy.waiting(service.state(), clock.get()), "inside the 5xx back-off");
			github.enqueue(MockGitHub.Answer.json(404, "{}"));
			assertEquals(List.of("Release source unavailable"), service.checkManual().lines(UTC));
			assertEquals(3, github.requests.size(), "the 5xx back-off does not hold a manual check back");
		}
	}

	/** AC-UPD-10 [A] manual part: with the connection refused, "Couldn't reach GitHub" at once, without a stack trace. */
	@Test
	void aRefusedConnectionCannotBeReached() throws Exception {
		int port;
		try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = closed.getLocalPort();
		}
		UpdateService service = service(URI.create("http://127.0.0.1:" + port + "/repos/x/y/releases"), "1.1.0+26.2", Duration.ofSeconds(20));
		long start = System.nanoTime();
		CheckOutcome outcome = service.checkManual();
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertEquals(List.of("Couldn't reach GitHub"), outcome.lines(UTC));
		assertTrue(ms < 25_000, "within 25 s: " + ms + " ms");
		assertEquals(1, warnings.size());
		assertFalse(warnings.getFirst().contains("\tat "), "no stack trace: " + warnings.getFirst());
	}

	/**
	 * AC-UPD-10 [A] manual part: a host that takes the connection and never answers ends at the request timeout. The
	 * game's 20 s ({@link GitHubReleaseSource#REQUEST_TIMEOUT}, under the AC's 25 s, wired in by
	 * {@code UpdateScheduleScanTest}) is shortened to 1 s here, so the suite does not wait 20 s on every build.
	 */
	@Test
	void aBlackHoledHostEndsAtTheRequestTimeout() throws Exception {
		assertTrue(GitHubReleaseSource.REQUEST_TIMEOUT.compareTo(Duration.ofSeconds(25)) < 0, "the game's timeout is under 25 s");
		try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			UpdateService service = service(URI.create("http://127.0.0.1:" + silent.getLocalPort() + "/repos/x/y/releases"), "1.1.0+26.2",
					Duration.ofSeconds(1));
			long start = System.nanoTime();
			CheckOutcome outcome = service.checkManual();
			long ms = (System.nanoTime() - start) / 1_000_000;
			assertEquals(List.of("Couldn't reach GitHub"), outcome.lines(UTC));
			assertTrue(ms >= 900 && ms < 5_000, "the 1 s timeout ended it: " + ms + " ms");
			assertEquals(1, warnings.size(), warnings.toString());
			assertFalse(warnings.getFirst().contains("\tat "), "no stack trace: " + warnings.getFirst());
		}
	}

	/** AC-UPD-07 second bullet: running 2.0.0 on 26.2, only v2.1.0 with a +26.3 jar: the manual text. */
	@Test
	void aNewerReleaseWithoutABuildForThisMinecraft() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, """
					[{"tag_name": "v2.1.0", "html_url": "https://github.com/x/releases/tag/v2.1.0", "draft": false, "prerelease": false,
					  "assets": [{"name": "k8bas_skyblock_utility-2.1.0+26.3.jar", "state": "uploaded",
					              "browser_download_url": "https://github.com/x/k8bas_skyblock_utility-2.1.0%2B26.3.jar",
					              "digest": "sha256:abc", "size": 1}]}]
					"""));
			CheckOutcome outcome = service(github.releases(), "2.0.0+26.2").checkManual();
			CheckOutcome.NoBuild noBuild = assertInstanceOf(CheckOutcome.NoBuild.class, outcome);
			assertEquals(List.of("v2.1.0 is out but has no build for Minecraft 26.2"), outcome.lines(UTC));
			assertEquals("https://github.com/x/releases/tag/v2.1.0", noBuild.releasePage(), "for Open release page");
		}
	}

	/** AC-UPD-07 first bullet, manual: v2.1.0 has only a +26.3 jar, v2.0.1 a +26.2 one: 2.0.1 is found, with the note. */
	@Test
	void anOlderReleaseIsOfferedWithTheNote() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, """
					[{"tag_name": "v2.1.0", "html_url": "https://github.com/x/releases/tag/v2.1.0", "draft": false, "prerelease": false,
					  "assets": [{"name": "k8bas_skyblock_utility-2.1.0+26.3.jar", "state": "uploaded",
					              "browser_download_url": "https://github.com/x/k8bas_skyblock_utility-2.1.0%2B26.3.jar",
					              "digest": "sha256:abc", "size": 1}]},
					 {"tag_name": "v2.0.1", "html_url": "https://github.com/x/releases/tag/v2.0.1", "draft": false, "prerelease": false,
					  "assets": [{"name": "k8bas_skyblock_utility-2.0.1+26.2.jar", "state": "uploaded",
					              "browser_download_url": "https://github.com/x/k8bas_skyblock_utility-2.0.1%2B26.2.jar",
					              "digest": "sha256:dededededededededededededededededededededededededededededededede", "size": 1}]}]
					"""));
			CheckOutcome outcome = service(github.releases(), "2.0.0+26.2").checkManual();
			assertEquals(List.of("v2.0.1 is available", "v2.1.0 has no build for Minecraft 26.2", UpdateHooks.NOTIFY_ONLY.reason()), outcome.lines(UTC));
			assertEquals(1, info.stream().filter(line -> line.contains("v2.1.0")).count(), "exactly one log line names v2.1.0: " + info);
		}
	}

	/** REQ-UPD-08: up to date. */
	@Test
	void upToDate() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			assertEquals(List.of("You're up to date (v1.2.0)"), service(github.releases(), "1.2.0+26.2").checkManual().lines(UTC));
		}
	}

	/**
	 * REQ-UPD-08 with T4.1's hooks: until T4.2 and T4.3 land, the found release is offered for a manual install only, with
	 * the reason; with a verifiable hash and an eligible install, it is offered; a skipped version is marked.
	 */
	@Test
	void aFoundReleaseIsNotifyOnlyUntilTheHooksAllowAnInstall() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			github.enqueue(MockGitHub.Answer.json(200, LIST));
			UpdateService service = service(github.releases(), "1.1.0+26.2");
			CheckOutcome.Found found = assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			assertFalse(found.installOffered());
			assertEquals(List.of("v1.2.0 is available", UpdateHooks.NOTIFY_ONLY.reason()), found.lines(UTC));

			UpdateStateStore store = new UpdateStateStore(dir.resolve("other-" + UpdateStateStore.FILE_NAME), warnings::add);
			GitHubReleaseSource source = new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.1.0+26.2", Duration.ofSeconds(5));
			UpdateService allowed = new UpdateService(source, store, clock::get, toggle::get, "1.1.0+26.2", "26.2", () -> null, info::add, warnings::add,
					new UpdateHooks((release, jar) -> new UpdateHooks.Hash.Verifiable("abc"), () -> UpdateHooks.Eligibility.ELIGIBLE));
			allowed.state().skippedVersion = "v1.2.0";
			CheckOutcome.Found offered = assertInstanceOf(CheckOutcome.Found.class, allowed.checkManual());
			assertTrue(offered.installOffered());
			assertEquals(List.of("v1.2.0 is available (skipped)"), offered.lines(UTC));
		}
	}

	/** REQ-UPD-08: without a usable SHA-256 the found release is notify-only, with that reason; a digest conflict says so. */
	@Test
	void theHashVerdictsHaveTheirReasons() {
		CandidateFinder.Candidate candidate = (CandidateFinder.Candidate) CandidateFinder.find(
				GitHubReleaseSource.parseReleases(com.google.gson.JsonParser.parseString(LIST).getAsJsonArray()), "1.1.0+26.2", "26.2", null, line -> { });
		CheckOutcome.Found missing = new CheckOutcome.Found(candidate, UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false, false,
				"26.2");
		assertEquals(List.of("v1.2.0 is available", "It has no SHA-256 checksum, so install it by hand from the release page"), missing.lines(UTC));
		CheckOutcome.Found conflict = new CheckOutcome.Found(candidate, UpdateHooks.Hash.Conflict.INSTANCE, UpdateHooks.Eligibility.ELIGIBLE, false,
				false, "26.2");
		assertEquals(List.of("v1.2.0 is available", "Release checksum conflict, install it by hand from the release page"), conflict.lines(UTC));
		CheckOutcome.Found beta = new CheckOutcome.Found(candidate, new UpdateHooks.Hash.Verifiable("abc"), UpdateHooks.Eligibility.ELIGIBLE, false, true,
				"26.2");
		assertEquals(List.of("v1.2.0 is available (beta)"), beta.lines(UTC));
	}

	/**
	 * The default hash hook: a "sha256:" digest of 64 hex digits makes the jar verifiable, lowercased; none, a short or
	 * non-hex one, or another kind does not.
	 */
	@Test
	void theDefaultHashHookReadsTheDigest() {
		Release release = GitHubReleaseSource.parseReleases(com.google.gson.JsonParser.parseString(LIST).getAsJsonArray()).getFirst();
		Release.Asset jar = release.assets().getFirst();
		assertEquals(new UpdateHooks.Hash.Verifiable("ab".repeat(32)), UpdateHooks.DEFAULT.getHash().check(release, jar));
		assertEquals(new UpdateHooks.Hash.Verifiable("ab".repeat(32)), UpdateHooks.fromDigest("sha256:" + "AB".repeat(32)));
		assertEquals(UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.fromDigest("sha256:abc"));
		assertEquals(UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.fromDigest("sha256:" + "zz".repeat(32)));
		Release.Asset none = new Release.Asset(jar.name(), jar.state(), jar.downloadUrl(), null, jar.size());
		assertEquals(UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.DEFAULT.getHash().check(release, none));
		Release.Asset md5 = new Release.Asset(jar.name(), jar.state(), jar.downloadUrl(), "md5:abc", jar.size());
		assertEquals(UpdateHooks.Hash.Missing.INSTANCE, UpdateHooks.DEFAULT.getHash().check(release, md5));
	}

	/**
	 * AC-UPD-20 state-file part (REQ-UPD-19, REQ-CFG-12): the new fields round-trip, a file without them (1.x) loads with
	 * empty ones at the same stateVersion, and saving the settings leaves the state file byte-identical.
	 */
	@Test
	void theStateFileKeepsItsNewFieldsAndSettingsSavesLeaveItAlone() throws Exception {
		Path file = dir.resolve(UpdateStateStore.FILE_NAME);
		UpdateStateStore store = new UpdateStateStore(file, warnings::add);
		UpdateState state = store.load();
		assertNull(state.skippedVersion);
		assertNull(state.pendingInstall);
		assertTrue(state.warnedFiles.isEmpty());
		state.skippedVersion = "1.4.0";
		state.pendingInstall = new PendingInstall("1.4.0", "k8bas_skyblock_utility-1.4.0+26.2.jar", "abc", T0);
		state.warnedFiles.add("k8bas_skyblock_utility-1.0.1.jar");
		state.lastManualMs = T0;
		store.save(state);
		store.flush();
		UpdateState read = new UpdateStateStore(file, warnings::add).load();
		assertEquals("1.4.0", read.skippedVersion);
		assertEquals(state.pendingInstall, read.pendingInstall);
		assertEquals(List.of("k8bas_skyblock_utility-1.0.1.jar"), read.warnedFiles);
		assertEquals(T0, read.lastManualMs);
		assertEquals(0, read.stateVersion, "additive fields: no migration step");

		Path old = dir.resolve("old.json");
		Files.writeString(old, "{\"stateVersion\": 0, \"etag\": \"e1\", \"releases\": []}");
		UpdateState legacy = new UpdateStateStore(old, warnings::add).load();
		assertEquals("e1", legacy.etag);
		assertTrue(legacy.warnedFiles.isEmpty());
		assertNull(legacy.pendingInstall);

		byte[] before = Files.readAllBytes(file);
		Path config = dir.resolve("k8bas_skyblock_utility.json");
		ConfigManager.load(config);
		ConfigManager.save();
		ConfigManager.flush();
		assertTrue(Files.exists(config));
		assertArrayEquals(before, Files.readAllBytes(file), "saving the settings leaves the state file byte-identical");
		assertTrue(warnings.isEmpty(), warnings.toString());
	}
}

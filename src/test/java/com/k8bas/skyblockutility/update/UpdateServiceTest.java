package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UPD-04 automatic rows, AC-UPD-10 [A] automatic part, AC-UPD-20 state-file rows (T1.4a). */
class UpdateServiceTest {
	private static final long T0 = 1_800_000_000_000L;

	@TempDir
	Path dir;

	private final AtomicLong clock = new AtomicLong(T0);
	private final AtomicBoolean toggle = new AtomicBoolean(true);
	private final List<String> info = new CopyOnWriteArrayList<>();
	private final List<String> warnings = new CopyOnWriteArrayList<>();

	private UpdateService service(URI uri) {
		UpdateStateStore store = new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add);
		GitHubReleaseSource source = new GitHubReleaseSource(uri, SharedHttpClient.get(), "1.1.0+26.2", Duration.ofSeconds(5));
		return new UpdateService(source, store, clock::get, toggle::get, "1.1.0+26.2", "26.2", () -> null, info::add, warnings::add);
	}

	@Test
	void twoStartsAnHourApartSendOneRequestAndBothSeeTheCandidate() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, GitHubReleaseSourceTest.LIST, "ETag", "\"e1\""));
			AtomicReference<CheckOutcome> first = new AtomicReference<>();
			UpdateService start1 = service(github.releases());
			start1.setOnOutcome(first::set);
			assertTrue(start1.checkAutomatic());
			start1.flush();

			clock.addAndGet(CheckPolicy.HOUR);
			AtomicReference<CheckOutcome> second = new AtomicReference<>();
			UpdateService start2 = service(github.releases());
			start2.setOnOutcome(second::set);
			assertFalse(start2.checkAutomatic());

			assertEquals(1, github.requests.size());
			assertInstanceOf(CheckOutcome.Found.class, first.get());
			assertInstanceOf(CheckOutcome.Found.class, second.get());
			assertTrue(warnings.isEmpty(), warnings.toString());
		}
	}

	@Test
	void theToggleOffSendsNothing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			toggle.set(false);
			UpdateService service = service(github.releases());
			assertFalse(service.checkAutomatic());
			service.triggerAutomatic();
			Thread.sleep(300);
			assertEquals(0, github.requests.size());
		}
	}

	@Test
	void onlyOneCheckIsInFlight() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, GitHubReleaseSourceTest.LIST).delayed(500));
			UpdateService service = service(github.releases());
			CopyOnWriteArrayList<String> threads = new CopyOnWriteArrayList<>();
			service.setOnOutcome(outcome -> threads.add(Thread.currentThread().getName()));
			service.triggerAutomatic();
			service.triggerAutomatic();
			service.triggerAutomatic();
			for (int i = 0; i < 50 && threads.isEmpty(); i++) {
				Thread.sleep(100);
			}
			assertEquals(1, github.requests.size());
			// The check ran on its own background thread (AC-UPD-10).
			assertEquals(List.of("k8bas-update-check"), threads);
		}
	}

	@Test
	void noNetworkGivesOneWarningPerAttemptWithoutAStackTrace() throws Exception {
		URI closed;
		try (MockGitHub github = new MockGitHub()) {
			closed = github.releases();
		}
		UpdateService service = service(closed);
		assertTrue(service.checkAutomatic());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.getFirst().startsWith("Update check failed: "), warnings.getFirst());
		assertFalse(warnings.getFirst().contains("\n\tat "));
		// The back-off holds the next attempt.
		assertFalse(service.checkAutomatic());
		clock.addAndGet(CheckPolicy.MINUTE);
		assertTrue(service.checkAutomatic());
		assertEquals(2, warnings.size());
	}

	/** AC-UPD-20: a corrupt state file is reset with one warning; the state survives a restart. */
	@Test
	void aCorruptStateFileIsResetAndAGoodOneIsKept() throws Exception {
		Files.writeString(dir.resolve(UpdateStateStore.FILE_NAME), "{ not json");
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, GitHubReleaseSourceTest.LIST, "ETag", "\"e1\""));
			UpdateService service = service(github.releases());
			assertEquals(1, warnings.size());
			assertTrue(warnings.getFirst().contains("it was reset"), warnings.getFirst());
			assertTrue(service.checkAutomatic());
			service.flush();
		}
		UpdateState reloaded = new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add).load();
		assertEquals("\"e1\"", reloaded.etag);
		assertEquals(T0, reloaded.lastSuccessMs);
		assertEquals(List.of(T0), reloaded.automaticRequestsMs);
		assertEquals("k8bas_skyblock_utility-1.2.0+26.2.jar", reloaded.releases.getFirst().assets().getFirst().name());
		assertEquals(1, warnings.size());
		assertTrue(Files.readString(dir.resolve(UpdateStateStore.FILE_NAME)).contains("\"stateVersion\": 0"));
	}

	/**
	 * G4 review: an offer that read the state first and ends last (its hash check read a sidecar) never replaces a newer
	 * one; here it would undo the skip made meanwhile, and the card would show the version unmarked again.
	 */
	@Test
	void aSlowOfferNeverUndoesANewerOne() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, GitHubReleaseSourceTest.LIST));
			AtomicBoolean holdNext = new AtomicBoolean();
			java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
			UpdateHooks hooks = new UpdateHooks((candidate, jar) -> {
				if (holdNext.compareAndSet(true, false)) {
					entered.countDown();
					try {
						release.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				return new UpdateHooks.Hash.Verifiable("ab".repeat(32));
			}, () -> UpdateHooks.Eligibility.ELIGIBLE);
			UpdateService service = new UpdateService(
					new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.1.0+26.2", Duration.ofSeconds(5)),
					new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add), clock::get, toggle::get, "1.1.0+26.2", "26.2",
					() -> null, info::add, warnings::add, hooks);
			assertInstanceOf(CheckOutcome.Found.class, service.checkManual());
			holdNext.set(true);
			java.util.concurrent.CompletableFuture<CheckOutcome> slow = java.util.concurrent.CompletableFuture.supplyAsync(service::offer);
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			assertTrue(service.skip("1.2.0"));
			release.countDown();
			// It read the state before the skip; it answers with the newer view, so no caller toasts the skipped version.
			assertTrue(assertInstanceOf(CheckOutcome.Found.class, slow.get(5, java.util.concurrent.TimeUnit.SECONDS)).skipped(), "the slow offer's answer");
			assertTrue(assertInstanceOf(CheckOutcome.Found.class, service.lastOffer()).skipped(), "the card keeps the skip");
		}
	}

	/** G4 review: of two offers, the one that read the state later stands, though the other one ends last. */
	@Test
	void aSlowOfferNeverReplacesANewerOne() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, "[" + release("1.3.0-beta.1", true) + "," + release("1.2.0", false) + "]"));
			AtomicBoolean holdNext = new AtomicBoolean();
			java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
			UpdateHooks hooks = new UpdateHooks((candidate, jar) -> {
				if (holdNext.compareAndSet(true, false)) {
					entered.countDown();
					try {
						release.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				return new UpdateHooks.Hash.Verifiable("ab".repeat(32));
			}, () -> UpdateHooks.Eligibility.ELIGIBLE);
			AtomicReference<UpdateChannel> channel = new AtomicReference<>(UpdateChannel.STABLE);
			UpdateService service = new UpdateService(
					new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.1.0+26.2", Duration.ofSeconds(5)),
					new UpdateStateStore(dir.resolve(UpdateStateStore.FILE_NAME), warnings::add), clock::get, toggle::get, "1.1.0+26.2", "26.2",
					channel::get, info::add, warnings::add, hooks);
			assertEquals("1.2.0", assertInstanceOf(CheckOutcome.Found.class, service.checkManual()).candidate().version().toString());
			holdNext.set(true);
			java.util.concurrent.CompletableFuture<CheckOutcome> slow = java.util.concurrent.CompletableFuture.supplyAsync(service::offer);
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			channel.set(UpdateChannel.BETA);
			assertEquals("1.3.0-beta.1", assertInstanceOf(CheckOutcome.Found.class, service.offer()).candidate().version().toString());
			release.countDown();
			assertEquals("1.3.0-beta.1", assertInstanceOf(CheckOutcome.Found.class, slow.get(5, java.util.concurrent.TimeUnit.SECONDS)).candidate().version().toString(),
					"the slow offer answers with the newer view");
			assertEquals("1.3.0-beta.1", assertInstanceOf(CheckOutcome.Found.class, service.lastOffer()).candidate().version().toString(),
					"the card keeps the newer view");
		}
	}

	private static String release(String version, boolean prerelease) {
		String name = "k8bas_skyblock_utility-" + version + "+26.2.jar";
		return "{\"tag_name\":\"v" + version + "\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v" + version
				+ "\",\"draft\":false,\"prerelease\":" + prerelease + ",\"assets\":[{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":1,"
				+ "\"browser_download_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/download/v" + version + "/" + name + "\"}]}";
	}
}

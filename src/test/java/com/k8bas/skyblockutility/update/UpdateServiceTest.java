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
			AtomicReference<CandidateFinder.Outcome> first = new AtomicReference<>();
			UpdateService start1 = service(github.releases());
			start1.setOnOutcome(first::set);
			assertTrue(start1.checkAutomatic());
			start1.flush();

			clock.addAndGet(CheckPolicy.HOUR);
			AtomicReference<CandidateFinder.Outcome> second = new AtomicReference<>();
			UpdateService start2 = service(github.releases());
			start2.setOnOutcome(second::set);
			assertFalse(start2.checkAutomatic());

			assertEquals(1, github.requests.size());
			assertInstanceOf(CandidateFinder.Candidate.class, first.get());
			assertInstanceOf(CandidateFinder.Candidate.class, second.get());
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
}

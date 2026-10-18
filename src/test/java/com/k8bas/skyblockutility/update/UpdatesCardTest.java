package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T4.4b (REQ-UPD-08, REQ-UPD-10, REQ-UPD-19): what the General › Updates card shows and does: the running version, the
 * latest known one (a skipped one marked "skipped", at once), the last check time, a status (the pending install first,
 * then the card's last answer, then the last check's text: each T4.1 text), and its four buttons.
 */
class UpdatesCardTest {
	private static final long NOW = java.time.LocalDateTime.of(2026, 10, 18, 15, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final String HEX = "ab".repeat(32);

	@TempDir
	Path dir;

	private final List<String> warnings = new CopyOnWriteArrayList<>();
	private final List<String> calls = new ArrayList<>();
	private boolean failing;
	private int services;

	private UpdateService service(MockGitHub github, String running) throws Exception {
		return service(github, running, saved -> { });
	}

	/** A service whose state file already holds what {@code seed} sets, as a launch with that file reads it. */
	private UpdateService service(MockGitHub github, String running, java.util.function.Consumer<UpdateState> seed) throws Exception {
		Path state = Files.createDirectories(dir.resolve("s" + services++)).resolve(UpdateStateStore.FILE_NAME);
		UpdateStateStore seeded = new UpdateStateStore(state, warnings::add);
		UpdateState saved = seeded.load();
		seed.accept(saved);
		seeded.save(saved);
		seeded.flush();
		return new UpdateService(new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), running, Duration.ofSeconds(5)),
				new UpdateStateStore(state, warnings::add), () -> NOW, () -> true, running, "26.2", () -> null,
				line -> { }, warnings::add, new UpdateHooks(UpdateHooks.DEFAULT.getHash(), () -> UpdateHooks.Eligibility.ELIGIBLE));
	}

	private UpdatesCard card(UpdateService service) {
		return new UpdatesCard(service, UTC, () -> NOW, new UpdatesCard.Actions() {
			@Override
			public CompletableFuture<List<String>> run(Function<UpdateActions, List<String>> action) {
				calls.add("run");
				if (failing) {
					return CompletableFuture.failedFuture(new IllegalStateException("boom"));
				}
				return CompletableFuture.completedFuture(action.apply(new UpdateActions(service, UTC)));
			}

			@Override
			public void showScreen() {
				calls.add("screen");
			}

			@Override
			public void openLink(URI page) {
				calls.add("link " + page);
			}
		});
	}

	private static String release(String version, boolean prerelease, String digest, String mc) {
		String name = "k8bas_skyblock_utility-" + version + "+" + mc + ".jar";
		return "{\"tag_name\":\"v" + version + "\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v" + version
				+ "\",\"prerelease\":" + prerelease + ",\"assets\":[{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":10,\"digest\":"
				+ (digest == null ? "null" : "\"" + digest + "\"") + ",\"browser_download_url\":\"https://github.com/x/" + name + "\"}]}";
	}

	private static String list(String version, boolean prerelease) {
		return "[" + release(version, prerelease, "sha256:" + HEX, "26.2") + "]";
	}

	/** Before any check: the running version, nothing known yet this session. */
	@Test
	void beforeAnyCheck() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			assertEquals("v1.3.1", card.version());
			assertEquals("Not checked this session", card.latest());
			assertEquals("Never", card.lastCheck());
			assertEquals("Not checked this session", card.status());
		}
	}

	/** "Check now" runs a manual check and shows its text; the latest version and the time follow; the cooldown's own text. */
	@Test
	void checkNowShowsTheManualCheckText() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, list("1.4.0", false)));
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			card.checkNow();
			assertEquals("v1.4.0 is available", card.status());
			assertEquals("v1.4.0", card.latest());
			assertEquals("Today, 15:30", card.lastCheck());
			card.checkNow();
			assertEquals("Checked a moment ago, try again in 60 s", card.status(), "the cooldown's text");
			assertEquals(List.of("run", "run"), calls);
		}
	}

	/** REQ-UPD-08 through the card: each manual-check text, and the latest version it leaves. */
	@Test
	void eachManualCheckText() throws Exception {
		record Case(MockGitHub.Answer answer, String running, String status, String latest) {
		}
		List<Case> cases = List.of(
				new Case(MockGitHub.Answer.json(403, "{}", "retry-after", "7200"), "1.3.1+26.2", "GitHub rate limit reached, try again after 17:30",
						"Not checked this session"),
				new Case(MockGitHub.Answer.json(500, "{}"), "1.3.1+26.2", "Couldn't reach GitHub", "Not checked this session"),
				new Case(MockGitHub.Answer.json(404, "{}"), "1.3.1+26.2", "Release source unavailable", "Not checked this session"),
				new Case(MockGitHub.Answer.json(200, "[" + release("1.4.0", false, "sha256:" + HEX, "26.3") + "]"), "1.3.1+26.2",
						"v1.4.0 is out but has no build for Minecraft 26.2", "v1.4.0, no build for Minecraft 26.2"),
				new Case(MockGitHub.Answer.json(200, list("1.4.0", false)), "dev", "This build (dev) is not a release, so no update is offered; "
						+ "The newest release is v1.4.0", "v1.4.0"),
				new Case(MockGitHub.Answer.json(200, list("1.3.1", false)), "1.3.1+26.2", "You're up to date (v1.3.1)", "None newer"),
				new Case(MockGitHub.Answer.json(200, "[" + release("1.4.1", false, "sha256:" + HEX, "26.3") + "," + release("1.4.0", false, null, "26.2") + "]"),
						"1.3.1+26.2", "v1.4.0 is available; v1.4.1 has no build for Minecraft 26.2; It has no SHA-256 checksum, so install it by hand "
						+ "from the release page", "v1.4.0"));
		for (Case each : cases) {
			try (MockGitHub github = new MockGitHub()) {
				github.enqueue(each.answer());
				UpdatesCard card = card(service(github, each.running()));
				card.checkNow();
				assertEquals(each.status(), card.status(), each.status());
				assertEquals(each.latest(), card.latest(), each.status());
			}
		}
	}

	/** AC-UPD-13 [C]: a version skipped (from the chat or the screen) is marked at once; a beta is labelled. */
	@Test
	void aSkippedOrBetaVersionIsMarked() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, list("1.4.0", false)));
			UpdateService service = service(github, "1.3.1+26.2");
			UpdatesCard card = card(service);
			card.checkNow();
			assertEquals("v1.4.0", card.latest());
			new UpdateActions(service, UTC).skip("1.4.0", true);
			assertEquals("v1.4.0 (skipped)", card.latest(), "without another check");
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, list("1.4.0-beta.1", true)));
			UpdatesCard card = card(service(github, "1.4.0-alpha.1+26.2"));
			card.checkNow();
			assertEquals("v1.4.0-beta.1 (beta)", card.latest());
		}
	}

	/** REQ-UPD-07: the default follows the build; a picked channel is applied to what is offered at once. */
	@Test
	void theChannelsDefaultAndAPick() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			assertEquals(UpdateChannel.STABLE, card(service(github, "1.3.1+26.2")).defaultChannel());
			assertEquals(UpdateChannel.BETA, card(service(github, "1.4.0-alpha.1+26.2")).defaultChannel());
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, "[" + release("1.5.0-beta.1", true, "sha256:" + HEX, "26.2") + "," + release("1.4.0", false,
					"sha256:" + HEX, "26.2") + "]"));
			UpdateChannel[] channel = {null};
			Path state = Files.createDirectories(dir.resolve("channel")).resolve(UpdateStateStore.FILE_NAME);
			UpdateService service = new UpdateService(new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.3.1+26.2", Duration.ofSeconds(5)),
					new UpdateStateStore(state, warnings::add), () -> NOW, () -> true, "1.3.1+26.2", "26.2", () -> channel[0],
					line -> { }, warnings::add, new UpdateHooks(UpdateHooks.DEFAULT.getHash(), () -> UpdateHooks.Eligibility.ELIGIBLE));
			UpdatesCard card = card(service);
			card.checkNow();
			assertEquals("v1.4.0", card.latest());
			channel[0] = UpdateChannel.BETA;
			card.channelChanged();
			assertEquals("v1.5.0-beta.1 (beta)", card.latest(), "the card and the Install screen agree");
			assertEquals("v1.5.0-beta.1 is available (beta)", card.status(), "the last answer gives way to the new offer");
		}
	}

	/** T4.3b's pending state comes first; "Cancel pending update" cancels it and says so. */
	@Test
	void aPendingInstallComesFirstAndCanBeCancelled() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdateService service = service(github, "1.3.1+26.2",
					saved -> saved.pendingInstall = new PendingInstall("1.4.0", "k8bas_skyblock_utility-1.4.0+26.2.jar", HEX, NOW, "1.3.1+26.2", "x.jar", 0));
			UpdatesCard card = card(service);
			assertEquals("Update v1.4.0 ready, restart Minecraft to apply", card.status());
			card.cancelPending();
			assertEquals("The pending update is cancelled; nothing is installed when you quit", card.status());
		}
	}

	/** Install opens the confirmation screen only when an update is on offer; otherwise the status says why. */
	@Test
	void installOpensTheScreenOnlyForAnUpdate() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			card.install();
			assertEquals("Not checked this session, press Check now", card.status());
			github.enqueue(MockGitHub.Answer.json(200, list("1.3.1", false)));
			card.checkNow();
			calls.clear();
			card.install();
			assertEquals("You're up to date (v1.3.1)", card.status());
			assertEquals(List.of(), calls, "no screen");
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, list("1.4.0", false)));
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			card.checkNow();
			calls.clear();
			card.install();
			assertEquals(List.of("screen"), calls);
		}
	}

	/** Open release page: the checked page of the update, or of the version without a build, else the release list. */
	@Test
	void theReleasePage() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			card.openReleasePage();
			github.enqueue(MockGitHub.Answer.json(200, list("1.4.0", false)));
			card.checkNow();
			card.openReleasePage();
			assertEquals(List.of("link https://github.com/Kesuhi/K8basSkyblockUtility/releases", "run",
					"link https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.4.0"), calls);
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, "[" + release("1.4.0", false, "sha256:" + HEX, "26.3") + "]"));
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			card.checkNow();
			calls.clear();
			card.openReleasePage();
			assertEquals(List.of("link https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.4.0"), calls, "the page of the version without a build");
		}
	}

	/** A button whose action fails says so instead of leaving the old text. */
	@Test
	void aFailedActionSaysSo() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdatesCard card = card(service(github, "1.3.1+26.2"));
			failing = true;
			card.checkNow();
			assertEquals("The update action failed, see the log", card.status());
		}
	}

	/** The last check reads the local day: today with the time, another day with the date. */
	@Test
	void theLastCheckReadsTheDay() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			UpdateService service = service(github, "1.3.1+26.2", saved -> saved.lastSuccessMs = NOW - 26L * 3600 * 1000);
			UpdatesCard card = card(service);
			assertEquals("2026-10-17 13:30", card.lastCheck());
			// A check after the card was built shows at once (the service's saved copy, read live).
			github.enqueue(MockGitHub.Answer.json(200, "[]"));
			service.checkManual();
			assertEquals("Today, 15:30", card.lastCheck());
		}
	}
}

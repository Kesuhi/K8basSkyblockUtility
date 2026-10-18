package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import com.k8bas.skyblockutility.settings.GeneralOptions;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.4b, AC-UPD-20 end to end (REQ-UPD-19): from a 1.0.1-shaped config, the opt-out stays an opt-out and the download
 * flag is gone; with the toggle OFF no automatic request goes out, a manual check from the card still works, and nothing
 * is downloaded without the confirmation's click; changing a setting through the card and saving leaves the state file
 * byte-identical; a corrupt state file is reset with one WARN and the updater starts.
 */
class UpdateSettingsEndToEndTest {
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final long NOW = 1_800_000_000_000L;

	@TempDir
	Path dir;

	private final List<String> warnings = new CopyOnWriteArrayList<>();

	private UpdateService service(MockGitHub github, Path stateFile) {
		return new UpdateService(new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.3.1+26.2", Duration.ofSeconds(5)),
				new UpdateStateStore(stateFile, warnings::add), () -> NOW, () -> ConfigManager.general().autoUpdateCheckEnabled, "1.3.1+26.2", "26.2",
				() -> ConfigManager.general().updateChannel, line -> { }, warnings::add,
				new UpdateHooks(UpdateHooks.DEFAULT.getHash(), () -> UpdateHooks.Eligibility.ELIGIBLE));
	}

	@Test
	void aConfigFrom101KeepsItsOptOutAndNothingDownloadsWithoutAClick() throws Exception {
		Path config = dir.resolve("k8bas_skyblock_utility.json");
		String old = new String(getClass().getResourceAsStream("/fixtures/config-1.0.1-shaped.json").readAllBytes(), StandardCharsets.UTF_8)
				.replace("\"autoUpdateCheckEnabled\": true", "\"autoUpdateCheckEnabled\": false");
		Files.writeString(config, old);
		ConfigManager.load(config);
		assertFalse(ConfigManager.general().autoUpdateCheckEnabled, "an opt-out stays an opt-out");
		ConfigManager.save();
		ConfigManager.flush();
		assertFalse(Files.readString(config).contains("autoUpdateDownloadEnabled"), "the download flag is gone");
		assertFalse(Files.readString(config).contains("updateChannel"), "no channel until one is picked (REQ-UPD-07)");

		try (MockGitHub github = new MockGitHub()) {
			Path stateFile = dir.resolve(UpdateStateStore.FILE_NAME);
			UpdateService service = service(github, stateFile);
			assertFalse(service.triggerAutomatic(), "toggle OFF: no automatic check starts");
			assertEquals(0, github.requests.size(), "toggle OFF: no automatic request");

			String name = "k8bas_skyblock_utility-1.4.0+26.2.jar";
			github.enqueue(MockGitHub.Answer.json(200, "[{\"tag_name\":\"v1.4.0\",\"html_url\":\"https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.4.0\","
					+ "\"assets\":[{\"name\":\"" + name + "\",\"state\":\"uploaded\",\"size\":10,\"digest\":\"sha256:" + "ab".repeat(32)
					+ "\",\"browser_download_url\":\"" + github.at("/download/" + name) + "\"}]}]"));
			UpdatesCard panel = new UpdatesCard(service, UTC, () -> NOW, new UpdatesCard.Actions() {
				@Override
				public CompletableFuture<List<String>> run(java.util.function.Function<UpdateActions, List<String>> action) {
					return CompletableFuture.completedFuture(action.apply(new UpdateActions(service, UTC)));
				}

				@Override
				public void showScreen() {
				}

				@Override
				public void openLink(URI page) {
				}
			});
			Card card = GeneralOptions.cards(ConfigManager.general(), () -> { }, panel).stream().filter(c -> c.id().equals("updates")).findFirst().orElseThrow();
			action(card, "updates.check_now");
			assertEquals("v1.4.0 is available", panel.status(), "a manual check works with the toggle OFF");
			action(card, "updates.install");
			assertEquals(List.of("/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30"), github.requests.stream().map(MockGitHub.Request::pathAndQuery).toList(),
					"nothing downloaded without the confirmation's click");

			service.flush();
			byte[] before = Files.readAllBytes(stateFile);
			// A fixed old time: a rewrite with the same bytes would still move it.
			java.nio.file.attribute.FileTime stamp = java.nio.file.attribute.FileTime.fromMillis(1_000_000_000_000L);
			Files.setLastModifiedTime(stateFile, stamp);
			@SuppressWarnings("unchecked")
			Choice<UpdateChannel> channel = (Choice<UpdateChannel>) option(card, "updates.channel");
			channel.binding().set(UpdateChannel.BETA);
			ConfigManager.save();
			ConfigManager.flush();
			assertEquals(UpdateChannel.BETA, ConfigManager.general().updateChannel);
			assertArrayEquals(before, Files.readAllBytes(stateFile), "saving the settings leaves the state file byte-identical");
			assertEquals(stamp, Files.getLastModifiedTime(stateFile), "and does not even rewrite it");
			assertTrue(Files.readString(config).contains("\"updateChannel\": \"BETA\""), "the picked channel is saved");
		}
	}

	/** AC-UPD-20: a corrupt state file is reset with one WARN, and the updater starts. */
	@Test
	void aCorruptStateFileIsResetWithOneWarning() throws Exception {
		Path stateFile = Files.writeString(dir.resolve(UpdateStateStore.FILE_NAME), "{ this is not json");
		ConfigManager.load(dir.resolve("k8bas_skyblock_utility.json"));
		try (MockGitHub github = new MockGitHub()) {
			UpdateService service = service(github, stateFile);
			assertEquals(1, warnings.size(), warnings.toString());
			assertNull(service.state().pendingInstall);
			assertInstanceOf(CheckOutcome.UpToDate.class, service.offer(), "a fresh state: nothing known");
			// The reset state works: a check is answered and saved again.
			github.enqueue(MockGitHub.Answer.json(200, "[]"));
			assertInstanceOf(CheckOutcome.UpToDate.class, service.checkManual());
			service.flush();
			assertEquals(1, warnings.size(), "still the one WARN: " + warnings);
			assertTrue(new UpdateStateStore(stateFile, warnings::add).load().lastManualMs > 0, "the new state file reads back");
		}
	}

	private static Option option(Card card, String id) {
		return card.options().stream().filter(option -> option.id().equals(id)).findFirst().orElseThrow();
	}

	private static void action(Card card, String id) {
		((com.k8bas.skyblockutility.ui.option.ActionOption) option(card, id)).action().run();
	}
}
